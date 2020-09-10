package germanskript

import java.io.File
import germanskript.util.SimpleLogger

class TypPrüfer(startDatei: File): ProgrammDurchlaufer<Typ>(startDatei) {
  val typisierer = Typisierer(startDatei)
  override val definierer = typisierer.definierer
  override val nichts = Typ.Nichts
  override var umgebung = Umgebung<Typ>()
  val logger = SimpleLogger()
  val ast: AST.Programm get() = typisierer.ast

  // ganz viele veränderliche Variablen :(
  private var zuÜberprüfendeKlasse: Typ.Compound.KlassenTyp? = null
  private var rückgabeTyp: Typ = Typ.Nichts
  private var funktionsTypParams: List<AST.WortArt.Nomen>? = null
  private var letzterFunktionsAufruf: AST.FunktionsAufruf? = null
  private var rückgabeErreicht = false
  private var evaluiereKonstante = false
  private var erwarteterTyp: Typ? = null

  fun prüfe() {
    typisierer.typisiere()
    definierer.konstanten.forEach(::prüfeKonstante)
    // zuerst die Klassendefinitionen, damit noch private Eigenschaften definiert werden können
    definierer.holeDefinitionen<AST.Definition.Typdefinition.Klasse>().forEach(::prüfeKlasse)

    definierer.funktionsDefinitionen.forEach(::prüfeFunktion)

    // neue germanskript.Umgebung
    durchlaufeAufruf(ast.programmStart!!, ast.programm, Umgebung(), true,Typ.Nichts)
  }

  private fun ausdruckMussTypSein(
      ausdruck: AST.Ausdruck,
      erwarteterTyp: Typ,
      funktionsTypArgs: List<AST.TypKnoten>? = null,
      typTypArgs: List<AST.TypKnoten>? = null
  ): Typ {
    // verwende den erwarteten Typen um für Objektinitialisierung und Closures Typargumente zu inferieren
    this.erwarteterTyp = erwarteterTyp
    val ausdruckTyp = evaluiereAusdruck(ausdruck)
    this.erwarteterTyp = null
    @Suppress("NAME_SHADOWING")
    var erwarteterTyp = erwarteterTyp
    if (erwarteterTyp is Typ.Generic) {
      erwarteterTyp = when (erwarteterTyp.kontext) {
        TypParamKontext.Funktion -> funktionsTypArgs!![erwarteterTyp.index].typ!!
        TypParamKontext.Typ -> typTypArgs!![erwarteterTyp.index].typ!!
      }
    }

    if (!typIstTyp(ausdruckTyp, erwarteterTyp)) {
      throw GermanSkriptFehler.TypFehler.FalscherTyp(
          holeErstesTokenVonAusdruck(ausdruck), ausdruckTyp, erwarteterTyp.name
      )
    }
    return ausdruckTyp
  }

   fun typIstTyp(typ: Typ, sollTyp: Typ): Boolean {
    fun überprüfeSchnittstelle(klasse: Typ.Compound.KlassenTyp, schnittstelle: Typ.Compound.Schnittstelle): Boolean =
      klasse.definition.implementierteSchnittstellen.contains(schnittstelle)

    fun überprüfeKlassenHierarchie(klasse: Typ.Compound.KlassenTyp, elternKlasse: Typ.Compound.KlassenTyp): Boolean {
      var laufTyp: Typ.Compound.KlassenTyp? = klasse
      while (laufTyp != null) {
        if (laufTyp == elternKlasse) {
          return true
        }
        laufTyp = laufTyp.definition.elternKlasse?.typ as Typ.Compound.KlassenTyp?
      }
      return false
    }

    return when {
      typ is Typ.Compound.KlassenTyp && sollTyp is Typ.Compound.Schnittstelle -> überprüfeSchnittstelle(typ, sollTyp)
      typ is Typ.Compound.KlassenTyp && sollTyp is Typ.Compound.KlassenTyp -> überprüfeKlassenHierarchie(typ, sollTyp)
      else -> typ == sollTyp
    }
  }

  /**
   * inferiert Typargumente basierend auf den erwarteten Typen
   */
  private fun inferiereTypArgumente(typ: Typ.Compound) {
    if (erwarteterTyp is Typ.Compound &&
        typ.typArgumente.isEmpty() &&
        typ.definition === (erwarteterTyp as Typ.Compound).definition) {
      typ.typArgumente = (erwarteterTyp as Typ.Compound).typArgumente.map {arg ->
        when (val argTyp = arg.typ) {
          // TODO: Gucke dir das nochmal genauer an. Braucht man diese Ersetzung wirklich? Welche Probleme können enstehen?
          is Typ.Generic -> when (argTyp.kontext) {
            TypParamKontext.Typ -> (umgebung.holeMethodenBlockObjekt()!! as Typ.Compound).typArgumente[argTyp.index]
            TypParamKontext.Funktion -> letzterFunktionsAufruf!!.typArgumente[argTyp.index]
          }
          else -> arg
        }
      }
    }
  }

  private fun holeParamName(param: AST.Definition.Parameter, typArgumente: List<AST.TypKnoten>): AST.WortArt.Nomen {
    return if (param.typKnoten.typ is Typ.Generic && param.typIstName) {
      param.name.tauscheHauptWortAus(typArgumente[(param.typKnoten.typ as Typ.Generic).index].name.deklination!!)
    } else {
      return param.name
    }
  }

  private fun holeParamTyp(param: AST.Definition.Parameter, typArgumente: List<AST.TypKnoten>): Typ {
    return if (param.typKnoten.typ is Typ.Generic) {
      val generic = param.typKnoten.typ as Typ.Generic
      when (generic.kontext) {
        TypParamKontext.Typ -> typArgumente[generic.index].typ!!
        TypParamKontext.Funktion -> letzterFunktionsAufruf!!.typArgumente[generic.index].typ!!
      }
    } else param.typKnoten.typ!!
  }

  private fun prüfeKonstante(konstante: AST.Definition.Konstante) {
    evaluiereKonstante = true
    val typ = evaluiereAusdruck(konstante.wert)
    if (typ !is Typ.Primitiv) {
      throw GermanSkriptFehler.KonstantenFehler(holeErstesTokenVonAusdruck(konstante.wert))
    }
    konstante.typ = typ
    evaluiereKonstante = false
  }

  private fun nichtErlaubtInKonstante(ausdruck: AST.Ausdruck) {
    if (evaluiereKonstante) {
      throw GermanSkriptFehler.KonstantenFehler(holeErstesTokenVonAusdruck(ausdruck))
    }
  }

  // breche niemals Sätze ab
  override fun sollSätzeAbbrechen(): Boolean = false

  private fun prüfeFunktion(funktion: AST.Definition.Funktion) {
    val funktionsUmgebung = Umgebung<Typ>()
    funktionsUmgebung.pushBereich()
    for (parameter in funktion.signatur.parameter) {
      funktionsUmgebung.schreibeVariable(parameter.name, parameter.typKnoten.typ!!, false)
    }
    val signatur = funktion.signatur
    funktionsTypParams = signatur.typParameter
    durchlaufeAufruf(signatur.name.toUntyped(), funktion.körper, funktionsUmgebung, false, signatur.rückgabeTyp.typ!!)
    funktionsTypParams = null
  }

  private fun prüfeKlasse(klasse: AST.Definition.Typdefinition.Klasse) {
    if (klasse.geprüft) {
      return
    }
    klasse.geprüft = true
    typisierer.typisiereKlasse(klasse)
    // Da die Kindklasse abhängig von der Elternklasse ist, muss zuerst die Elternklasse geprüft werden
    if (klasse.elternKlasse != null) {
      if (klasse.elternKlasse.typ == null) {
        typisierer.bestimmeTyp(klasse.elternKlasse, null, null, true)
      }
      val elternKlasse = klasse.elternKlasse.typ as Typ.Compound.KlassenTyp
      prüfeKlasse(elternKlasse.definition)
      klasse.eigenschaften.addAll(0, elternKlasse.definition.eigenschaften)
    }
    zuÜberprüfendeKlasse = Typ.Compound.KlassenTyp.Klasse(klasse, klasse.typParameter.mapIndexed { index, param ->
      val typKnoten = AST.TypKnoten(emptyList(), param, emptyList())
      typKnoten.typ = Typ.Generic(index, TypParamKontext.Typ)
      typKnoten
    })
    durchlaufeAufruf(klasse.name.bezeichner.toUntyped(), klasse.konstruktor, Umgebung(), true, Typ.Nichts)
    klasse.methoden.values.forEach(::prüfeFunktion)
    klasse.konvertierungen.values.forEach {konvertierung ->
      durchlaufeAufruf(
          konvertierung.typ.name.bezeichnerToken,
          konvertierung.definition, Umgebung(), true, konvertierung.typ.typ!!)
    }
    klasse.berechneteEigenschaften.values.forEach {eigenschaft ->
      durchlaufeAufruf(eigenschaft.name.bezeichner.toUntyped(),
          eigenschaft.definition, Umgebung(),
          true, eigenschaft.rückgabeTyp.typ!!)
    }

    fun fügeElternKlassenMethodenHinzu(elternKlasse: AST.Definition.Typdefinition.Klasse) {
      for ((methodenName, methode) in elternKlasse.methoden) {
        klasse.methoden.putIfAbsent(methodenName, methode)
      }
      for ((konvertierungsTyp, konvertierung) in elternKlasse.konvertierungen) {
        klasse.konvertierungen.putIfAbsent(konvertierungsTyp, konvertierung)
      }
      if (elternKlasse.elternKlasse != null) {
        fügeElternKlassenMethodenHinzu((elternKlasse.elternKlasse.typ as Typ.Compound.KlassenTyp).definition)
      }
    }

    if (klasse.elternKlasse != null) {
      fügeElternKlassenMethodenHinzu((klasse.elternKlasse.typ as Typ.Compound.KlassenTyp).definition)
    }
  }

  private fun durchlaufeAufruf(token: Token, bereich: AST.Satz.Bereich, umgebung: Umgebung<Typ>, neuerBereich: Boolean, rückgabeTyp: Typ): Typ {
    this.rückgabeTyp = rückgabeTyp
    this.umgebung = umgebung
    rückgabeErreicht = false
    return durchlaufeBereich(bereich, neuerBereich).also {
      if (rückgabeTyp != Typ.Nichts && !rückgabeErreicht) {
        throw GermanSkriptFehler.RückgabeFehler.RückgabeVergessen(token, rückgabeTyp)
      }
    }
  }

  override fun durchlaufeVariablenDeklaration(deklaration: AST.Satz.VariablenDeklaration): Typ {
    if (deklaration.istEigenschaftsNeuZuweisung) {
      val eigenschaft = holeNormaleEigenschaftAusKlasse(deklaration.name, zuÜberprüfendeKlasse!!)
      if (eigenschaft.typKnoten.name.unveränderlich) {
        throw GermanSkriptFehler.EigenschaftsFehler.EigenschaftUnveränderlich(deklaration.name.bezeichner.toUntyped())
      }
      val eigenschaftTyp = eigenschaft.typKnoten.typ!!
      ausdruckMussTypSein(deklaration.wert, eigenschaftTyp)
    }
    else if (deklaration.istEigenschaft) {
      val klasse = zuÜberprüfendeKlasse!!.definition
      val wert = evaluiereAusdruck(deklaration.wert)
      val typ = AST.TypKnoten(emptyList(), deklaration.name, emptyList())
      typ.typ = wert
      if (klasse.eigenschaften.any { eigenschaft -> eigenschaft.name.nominativ == deklaration.name.nominativ}) {
        throw GermanSkriptFehler.DoppelteEigenschaft(deklaration.name.bezeichner.toUntyped(), klasse)
      }
      klasse.eigenschaften.add(AST.Definition.Parameter(typ, deklaration.name))
    }
    else {
      if (deklaration.name.unveränderlich || deklaration.neu != null) {
        val wert = evaluiereAusdruck(deklaration.wert)
        umgebung.schreibeVariable(deklaration.name, wert, !deklaration.name.unveränderlich)
      }
      else {
        // hier müssen wir überprüfen ob der Typ der Variable, die überschrieben werden sollen gleich
        // dem neuen germanskript.Wert ist
        val vorherigerTyp = umgebung.leseVariable(deklaration.name.nominativ)
        val wert = if (vorherigerTyp != null) {
          if (vorherigerTyp.name.unveränderlich) {
            throw GermanSkriptFehler.Variablenfehler(deklaration.name.bezeichner.toUntyped(), vorherigerTyp.name)
          }
          ausdruckMussTypSein(deklaration.wert, vorherigerTyp.wert)
        } else {
          evaluiereAusdruck(deklaration.wert)
        }
        umgebung.überschreibeVariable(deklaration.name, wert)
      }
    }
    return Typ.Nichts
  }

  override fun durchlaufeFunktionsAufruf(funktionsAufruf: AST.FunktionsAufruf, istAusdruck: Boolean): Typ {
    if (funktionsAufruf.vollerName == null) {
      funktionsAufruf.vollerName = definierer.holeVollenNamenVonFunktionsAufruf(funktionsAufruf, null)
    }
    var funktionsSignatur: AST.Definition.FunktionsSignatur? = null
    logger.addLine("prüfe Funktionsaufruf in ${funktionsAufruf.verb.position}: ${funktionsAufruf.vollerName!!}")
    val methodenBlockObjekt = umgebung.holeMethodenBlockObjekt()

    var subjekt: Typ.Compound.KlassenTyp? = null
    if (funktionsAufruf.subjekt != null) {
      subjekt = evaluiereAusdruck(funktionsAufruf.subjekt) as Typ.Compound.KlassenTyp
      funktionsSignatur = findeMethode(
          funktionsAufruf,
          subjekt,
          FunktionsAufrufTyp.METHODEN_SUBJEKT_AUFRUF,
          TokenTyp.REFLEXIV_PRONOMEN.DICH,
          true
      )

      if (funktionsSignatur == null ) {
        throw GermanSkriptFehler.Undefiniert.Funktion(funktionsAufruf.token, funktionsAufruf)
      }
    }

    // ist Methodenaufruf von Bereich-Variable oder Subjekt
    if (funktionsSignatur == null &&
        methodenBlockObjekt is Typ.Compound.KlassenTyp &&
        funktionsAufruf.reflexivPronomen?.typ != TokenTyp.REFLEXIV_PRONOMEN.MICH
    ) {
      funktionsSignatur = findeMethode(
          funktionsAufruf,
          methodenBlockObjekt,
          FunktionsAufrufTyp.METHODEN_BLOCK_AUFRUF,
          TokenTyp.REFLEXIV_PRONOMEN.DICH,
          true
      )
    }

    // Schnittstellen Aufruf
    if (funktionsSignatur == null && methodenBlockObjekt is Typ.Compound.Schnittstelle && funktionsAufruf.reflexivPronomen?.typ != TokenTyp.REFLEXIV_PRONOMEN.MICH) {
      funktionsSignatur = methodenBlockObjekt.definition.methodenSignaturen.find { signatur -> signatur.vollerName!! == funktionsAufruf.vollerName!! }
      if (funktionsSignatur != null) {
        funktionsAufruf.vollerName = funktionsSignatur.vollerName
        funktionsAufruf.aufrufTyp = FunktionsAufrufTyp.METHODEN_BLOCK_AUFRUF
      }
    }

    // ist Methoden-Selbst-Aufruf
    if (funktionsSignatur == null && zuÜberprüfendeKlasse != null && funktionsAufruf.reflexivPronomen?.typ != TokenTyp.REFLEXIV_PRONOMEN.DICH) {
      funktionsSignatur = findeMethode(
          funktionsAufruf,
          zuÜberprüfendeKlasse!!,
          FunktionsAufrufTyp.METHODEN_SELBST_AUFRUF,
          TokenTyp.REFLEXIV_PRONOMEN.MICH,
          true
      )
    }

    // ist normale Funktion
    val undefiniertFehler = try {
      if (funktionsSignatur == null) {
        funktionsAufruf.funktionsDefinition = definierer.holeFunktionsDefinition(funktionsAufruf)
        funktionsSignatur = funktionsAufruf.funktionsDefinition!!.signatur
        null
      } else {
        null
      }
    } catch (fehler: GermanSkriptFehler.Undefiniert.Funktion) {
      fehler
    }

    // ist Methoden-Objekt-Aufruf als letzte Möglichkeit
    // das bedeutet Funktionsnamen gehen vor Methoden-Objekt-Aufrufen
    // TODO: kombiniere dies mit der Ersetzung von Parameternamen mit generische Parameter
    if (funktionsSignatur == null && funktionsAufruf.objekt != null) {
      val klasse = try {
        typisierer.bestimmeTyp(funktionsAufruf.objekt.name)
      }
      catch (fehler: GermanSkriptFehler.Undefiniert.Typ) {
        null
      }
      if (klasse is Typ.Compound.KlassenTyp) {
        val methoden = klasse.definition.methoden
        val klassenTyp = klasse.definition.name
        val reflexivPronomen = when (funktionsAufruf.objekt.name.kasus) {
          Kasus.AKKUSATIV -> "mich"
          Kasus.DATIV -> "mir"
          else -> throw Exception("Dieser Fall sollte nie eintreten, da der Grammatikprüfer dies überprüfen sollte. "
              + "${klassenTyp.bezeichner}")
        }
        val methodenName = definierer.holeVollenNamenVonFunktionsAufruf(funktionsAufruf, reflexivPronomen)
        methoden[methodenName]?.also { methode ->
          funktionsSignatur = methoden.getValue(methodenName).signatur
          funktionsAufruf.vollerName = methode.signatur.vollerName
          funktionsAufruf.aufrufTyp = FunktionsAufrufTyp.METHODEN_OBJEKT_AUFRUF
        }
      }
    }

    if (funktionsSignatur == null) {
      throw undefiniertFehler!!
    }

    val parameter = funktionsSignatur!!.parameter
    val argumente = funktionsAufruf.argumente
    val j = if (funktionsAufruf.aufrufTyp == FunktionsAufrufTyp.METHODEN_OBJEKT_AUFRUF) 1 else 0
    val anzahlArgumente = argumente.size - j
    if (anzahlArgumente != parameter.size) {
      throw GermanSkriptFehler.SyntaxFehler.AnzahlDerParameterFehler(funktionsSignatur!!.name.toUntyped())
    }

    // stimmen die Argument Typen mit den Parameter Typen überein?
    letzterFunktionsAufruf = funktionsAufruf
    for (i in parameter.indices) {
      val typKontext = when (funktionsAufruf.aufrufTyp) {
        FunktionsAufrufTyp.METHODEN_BLOCK_AUFRUF -> (umgebung.holeMethodenBlockObjekt()!! as Typ.Compound).typArgumente
        FunktionsAufrufTyp.METHODEN_SUBJEKT_AUFRUF -> subjekt!!.typArgumente
        else -> null
      }
      ausdruckMussTypSein(argumente[i+j].ausdruck, parameter[i].typKnoten.typ!!, funktionsAufruf.typArgumente, typKontext)
    }
    letzterFunktionsAufruf = null

    return funktionsSignatur!!.rückgabeTyp.typ!!
  }

  private fun findeMethode(
      funktionsAufruf: AST.FunktionsAufruf,
      typ: Typ.Compound.KlassenTyp,
      aufrufTyp: FunktionsAufrufTyp,
      reflexivPronomen: TokenTyp.REFLEXIV_PRONOMEN,
      erlaubeHoleElternDefinition: Boolean
  ): AST.Definition.FunktionsSignatur? {

    // versuche Methode in der Elternklasse zu finden
    if (aufrufTyp == FunktionsAufrufTyp.METHODEN_SELBST_AUFRUF &&
        erlaubeHoleElternDefinition && inSuperBlock && typ.definition.elternKlasse != null) {
      val signatur = findeMethode(
          funktionsAufruf, typ.definition.elternKlasse!!.typ!! as Typ.Compound.KlassenTyp,
          aufrufTyp, reflexivPronomen, false
      )
      if (signatur != null) {
        return signatur
      }
    }

    // ist Methodenaufruf von Block-Variable
    typ.definition.methoden[funktionsAufruf.vollerName!!]?.also { methode ->
      if (aufrufTyp == FunktionsAufrufTyp.METHODEN_SELBST_AUFRUF) {
        funktionsAufruf.funktionsDefinition = methode
        funktionsAufruf.vollerName = "für ${typ}: ${funktionsAufruf.vollerName}"
      }
      funktionsAufruf.aufrufTyp = aufrufTyp
      return methode.signatur
    }

    if (typ.definition.typParameter.isNotEmpty()) {
      // TODO: hier müssen nicht nur TypTypParameter sondern auch die FunktionTypParameter berücksichtigt werden
      val ersetzeTypArgsName = definierer.holeVollenNamenVonFunktionsAufruf(
          funktionsAufruf, typ.definition.typParameter, typ.typArgumente)

      typ.definition.methoden[ersetzeTypArgsName]?.also { methode ->
        // TODO: Die Typparameternamen müssen für den Interpreter später hier ersetzt werden
        funktionsAufruf.vollerName = ersetzeTypArgsName
        if (aufrufTyp == FunktionsAufrufTyp.METHODEN_SELBST_AUFRUF) {
          funktionsAufruf.funktionsDefinition = methode
          funktionsAufruf.vollerName = "für ${typ}: ${funktionsAufruf.vollerName}"
        }
        funktionsAufruf.aufrufTyp = aufrufTyp
        return methode.signatur
      }
    }

    if (funktionsAufruf.reflexivPronomen != null &&
        funktionsAufruf.reflexivPronomen.typ == reflexivPronomen &&
        typ.definition.elternKlasse == null) {
      throw GermanSkriptFehler.Undefiniert.Methode(funktionsAufruf.verb.toUntyped(),
          funktionsAufruf,
          typ.definition.name.nominativ)
    }
    return null
  }

  override fun durchlaufeZurückgabe(zurückgabe: AST.Satz.Zurückgabe): Typ {
    ausdruckMussTypSein(zurückgabe.ausdruck, rückgabeTyp)
    // Die Rückgabe ist nur auf alle Fälle erreichbar, wenn sie an keine Bedingung und in keiner Schleife ist
    rückgabeErreicht = zurückgabe.findNodeInParents<AST.Satz.BedingungsTerm>() == null &&
        zurückgabe.findNodeInParents<AST.Satz.FürJedeSchleife>() == null
    return rückgabeTyp
  }

  private fun prüfeBedingung(bedingung: AST.Satz.BedingungsTerm) {
    ausdruckMussTypSein(bedingung.bedingung, Typ.Primitiv.Boolean)
    durchlaufeBereich(bedingung.bereich, true)
  }

  override fun durchlaufeBedingungsSatz(bedingungsSatz: AST.Satz.Bedingung) {
    bedingungsSatz.bedingungen.forEach(::prüfeBedingung)
    bedingungsSatz.sonst?.also {durchlaufeBereich(it, true)}
  }

  override fun durchlaufeSolangeSchleife(schleife: AST.Satz.SolangeSchleife) {
    prüfeBedingung(schleife.bedingung)
  }

  override fun durchlaufeFürJedeSchleife(schleife: AST.Satz.FürJedeSchleife) {
    val elementTyp = when {
      schleife.liste != null -> {
        val liste = evaluiereAusdruck(schleife.liste)
        if (liste !is Typ.Compound.KlassenTyp.Liste) {
          throw GermanSkriptFehler.TypFehler.FalscherTyp(holeErstesTokenVonAusdruck(schleife.liste), liste, "Liste")
        }
        liste.typArgumente[0].typ!!
      }
      schleife.reichweite != null -> {
        val (anfang, ende) = schleife.reichweite
        ausdruckMussTypSein(anfang, Typ.Primitiv.Zahl)
        ausdruckMussTypSein(ende, Typ.Primitiv.Zahl)
        Typ.Primitiv.Zahl
      }
      else -> {
        evaluiereListenSingular(schleife.singular)
      }
    }
    umgebung.pushBereich()
    umgebung.schreibeVariable(schleife.binder, elementTyp, false)
    durchlaufeBereich(schleife.bereich, true)
    umgebung.popBereich()
  }

  override fun durchlaufeVersucheFange(versucheFange: AST.Satz.VersucheFange) {
    durchlaufeBereich(versucheFange.versuche, true)
    for (fange in versucheFange.fange) {
      umgebung.pushBereich()
      // TODO: hole aus dem Kontext die Typparameter
      val typ = typisierer.bestimmeTyp(fange.param.typKnoten, null, null, true)!!
      umgebung.schreibeVariable(fange.param.name, typ, true)
      durchlaufeBereich(fange.bereich, true)
      umgebung.popBereich()
      if (versucheFange.schlussendlich != null) {
        durchlaufeBereich(versucheFange.schlussendlich, true)
      }
    }
  }

  override fun durchlaufeWerfe(werfe: AST.Satz.Werfe): Typ {
    evaluiereAusdruck(werfe.ausdruck)
    return Typ.Niemals
  }

  override fun durchlaufeIntern(intern: AST.Satz.Intern): Typ {
    // Hier muss nicht viel gemacht werden
    // die internen Sachen sind schon von kotlin Typ-gecheckt :)
    rückgabeErreicht = true
    return rückgabeTyp
  }

  override fun bevorDurchlaufeMethodenBereich(methodenBereich: AST.MethodenBereich, blockObjekt: Typ?) {
    if (blockObjekt !is Typ.Compound && blockObjekt !is Typ.Compound.Schnittstelle) {
      throw GermanSkriptFehler.TypFehler.ObjektErwartet(methodenBereich.name.bezeichner.toUntyped())
    }
  }

  override fun durchlaufeAbbrechen() {
    // hier muss nichts gemacht werden...
  }

  override fun durchlaufeFortfahren() {
    // hier muss nichts gemacht werden...
  }

  override fun starteBereich(bereich: AST.Satz.Bereich) {
    // hier muss nichts gemacht werden...
  }

  override fun beendeBereich(bereich: AST.Satz.Bereich) {
    // hier muss nichts gemacht werden...
  }

  override fun evaluiereZeichenfolge(ausdruck: AST.Ausdruck.Zeichenfolge) = Typ.Primitiv.Zeichenfolge

  override fun evaluiereZahl(ausdruck: AST.Ausdruck.Zahl) = Typ.Primitiv.Zahl

  override fun evaluiereBoolean(ausdruck: AST.Ausdruck.Boolean) = Typ.Primitiv.Boolean

  override fun evaluiereVariable(variable: AST.Ausdruck.Variable): Typ {
    return try {
      super.evaluiereVariable(variable)
    } catch (undefiniert: GermanSkriptFehler.Undefiniert.Variable) {
      // Wenn die Variable nicht gefunden wurde, wird überprüft, ob es sich vielleicht um eine Konstante handelt
      if (variable.name.vornomen == null) {
        try {
          val konstante = AST.Ausdruck.Konstante(emptyList(), variable.name.bezeichner)
          konstante.setParentNode(variable)
          val konstantenDefinition = definierer.holeKonstante(konstante)
          konstante.wert = konstantenDefinition.wert
          // ersetze die Variable mit ihrer Konstanten
          variable.konstante = konstante
          evaluiereAusdruck(konstantenDefinition.wert)
        } catch (fehler: GermanSkriptFehler.Undefiniert.Konstante) {
          throw  undefiniert
        }
      }
      else {
        throw undefiniert
      }
    }
  }

  override fun evaluiereKonstante(konstante: AST.Ausdruck.Konstante): Typ {
    val konstanteDefinition = definierer.holeKonstante(konstante)
    // tragen den Wert der Konstanten ein
    konstante.wert = konstanteDefinition.wert
    return evaluiereAusdruck(konstante.wert!!)
  }

  override fun evaluiereListe(ausdruck: AST.Ausdruck.Liste): Typ {
    nichtErlaubtInKonstante(ausdruck)
    val listenTyp = typisierer.bestimmeTyp(
        ausdruck.pluralTyp,
        funktionsTypParams,
        zuÜberprüfendeKlasse?.definition?.typParameter,
        true
    ) as Typ.Compound.KlassenTyp.Liste
    ausdruck.elemente.forEach {element -> ausdruckMussTypSein(element, listenTyp.typArgumente[0].typ!!)}
    return listenTyp
  }

  override fun evaluiereListenElement(listenElement: AST.Ausdruck.ListenElement): Typ {
    nichtErlaubtInKonstante(listenElement)
    ausdruckMussTypSein(listenElement.index, Typ.Primitiv.Zahl)
    val zeichenfolge = evaluiereVariable(listenElement.singular.nominativ)
    // Bei einem Zugriff auf einen Listenindex kann es sich auch um eine Zeichenfolge handeln
    if (zeichenfolge != null) {
      if (zeichenfolge !is Typ.Primitiv.Zeichenfolge) {
        throw GermanSkriptFehler.TypFehler.FalscherTyp(
            listenElement.singular.bezeichner.toUntyped(), zeichenfolge, "Zeichenfolge")
      }
      return Typ.Primitiv.Zeichenfolge
    }
    return evaluiereListenSingular(listenElement.singular)
  }

  private fun evaluiereListenSingular(singular: AST.WortArt.Nomen): Typ {
    val plural = singular.ganzesWort(Kasus.NOMINATIV, Numerus.PLURAL)
    val liste = evaluiereVariable(plural)?:
    throw GermanSkriptFehler.Undefiniert.Variable(singular.bezeichner.toUntyped(), plural)

    return (liste as Typ.Compound.KlassenTyp.Liste).typArgumente[0].typ!!
  }

  override fun evaluiereObjektInstanziierung(instanziierung: AST.Ausdruck.ObjektInstanziierung): Typ {
    nichtErlaubtInKonstante(instanziierung)
    val klasse = typisierer.bestimmeTyp(
        instanziierung.klasse,
        funktionsTypParams,
        zuÜberprüfendeKlasse?.definition?.typParameter,
        true
    )!!
    if (klasse !is Typ.Compound.KlassenTyp) {
      throw GermanSkriptFehler.TypFehler.ObjektErwartet(instanziierung.klasse.name.bezeichnerToken)
    }
    inferiereTypArgumente(klasse)

    val definition = klasse.definition

    // die Eigenschaftszuweisungen müssen mit der Instanzzierung übereinstimmen, Außerdem müssen die Namen übereinstimmen
    var j = 0
    for (i in definition.eigenschaften.indices) {
      val eigenschaft = definition.eigenschaften[i]
      if (eigenschaft.istPrivat) {
        j++
        continue
      }
      if (i >= instanziierung.eigenschaftsZuweisungen.size + j) {
        throw GermanSkriptFehler.EigenschaftsFehler.EigenschaftsVergessen(instanziierung.klasse.name.bezeichnerToken, eigenschaft.name.nominativ)
      }
      val zuweisung = instanziierung.eigenschaftsZuweisungen[i-j]

      // Wenn es es sich um eine generische Eigenschaft handelt, verwende den Namen des Typarguments
      val eigenschaftsName = holeParamName(eigenschaft, instanziierung.klasse.typArgumente)
      if (eigenschaftsName.nominativ != zuweisung.name.nominativ) {
        GermanSkriptFehler.EigenschaftsFehler.UnerwarteterEigenschaftsName(zuweisung.name.bezeichner.toUntyped(), eigenschaft.name.nominativ)
      }

      // die Typen müssen übereinstimmen
      ausdruckMussTypSein(zuweisung.ausdruck, eigenschaft.typKnoten.typ!!, null, instanziierung.klasse.typArgumente)
    }

    if (instanziierung.eigenschaftsZuweisungen.size > definition.eigenschaften.size) {
      throw GermanSkriptFehler.EigenschaftsFehler.UnerwarteteEigenschaft(
          instanziierung.eigenschaftsZuweisungen[definition.eigenschaften.size].name.bezeichner.toUntyped())
    }
    return klasse
  }

  override fun evaluiereEigenschaftsZugriff(eigenschaftsZugriff: AST.Ausdruck.EigenschaftsZugriff): Typ {
    nichtErlaubtInKonstante(eigenschaftsZugriff)
    val klasse = evaluiereAusdruck(eigenschaftsZugriff.objekt)
    return if (klasse is Typ.Compound.KlassenTyp) {
      holeEigenschaftAusKlasse(eigenschaftsZugriff, klasse)
    } else if (klasse is Typ.Primitiv.Zeichenfolge && eigenschaftsZugriff.eigenschaftsName.nominativ == "Länge") {
      Typ.Primitiv.Zahl
    }
    else {
      throw GermanSkriptFehler.TypFehler.ObjektErwartet(holeErstesTokenVonAusdruck(eigenschaftsZugriff.objekt))
    }
  }

  override fun evaluiereSelbstEigenschaftsZugriff(eigenschaftsZugriff: AST.Ausdruck.SelbstEigenschaftsZugriff): Typ {
    nichtErlaubtInKonstante(eigenschaftsZugriff)
    return holeEigenschaftAusKlasse(eigenschaftsZugriff, zuÜberprüfendeKlasse!!)
  }


  override fun evaluiereMethodenBlockEigenschaftsZugriff(eigenschaftsZugriff: AST.Ausdruck.MethodenBereichEigenschaftsZugriff): Typ {
    nichtErlaubtInKonstante(eigenschaftsZugriff)
    val methodenBlockObjekt = umgebung.holeMethodenBlockObjekt() as Typ.Compound.KlassenTyp
    return holeEigenschaftAusKlasse(eigenschaftsZugriff, methodenBlockObjekt)
  }

  private fun holeNormaleEigenschaftAusKlasse(eigenschaftsName: AST.WortArt.Nomen, klasse: Typ.Compound.KlassenTyp): AST.Definition.Parameter {
    for (eigenschaft in klasse.definition.eigenschaften) {
      val name = holeParamName(eigenschaft ,klasse.typArgumente)
      if (eigenschaftsName.nominativ == name.nominativ) {
        return eigenschaft
      }
    }
    throw GermanSkriptFehler.Undefiniert.Eigenschaft(eigenschaftsName.bezeichner.toUntyped(), klasse.definition.name.nominativ)
  }

  private fun holeEigenschaftAusKlasse(eigenschaftsZugriff: AST.Ausdruck.IEigenschaftsZugriff, klasse: Typ.Compound.KlassenTyp): Typ {
    val eigenschaftsName = eigenschaftsZugriff.eigenschaftsName
    val klassenDefinition = klasse.definition
    val typ = try {
      holeNormaleEigenschaftAusKlasse(eigenschaftsName, klasse).typKnoten.typ!!
    } catch (fehler: GermanSkriptFehler.Undefiniert.Eigenschaft) {
      if (!klassenDefinition.berechneteEigenschaften.containsKey(eigenschaftsName.nominativ)) {
        throw fehler
      } else {
        eigenschaftsZugriff.aufrufName = "${eigenschaftsName.nominativ} von ${klassenDefinition.namensToken.wert}"
        klassenDefinition.berechneteEigenschaften.getValue(eigenschaftsName.nominativ).rückgabeTyp.typ!!
      }
    }
    return if (typ is Typ.Generic) klasse.typArgumente[typ.index].typ!! else typ
  }

  override fun evaluiereBinärenAusdruck(ausdruck: AST.Ausdruck.BinärerAusdruck): Typ {
    val linkerTyp = evaluiereAusdruck(ausdruck.links)
    val operator = ausdruck.operator.typ.operator
    if (!linkerTyp.definierteOperatoren.containsKey(operator)) {
      throw GermanSkriptFehler.Undefiniert.Operator(ausdruck.operator.toUntyped(), linkerTyp.name)
    }
    // es wird erwartete, dass bei einem binären Ausdruck beide Operanden vom selben Typen sind
    ausdruckMussTypSein(ausdruck.rechts, linkerTyp)
    return linkerTyp.definierteOperatoren.getValue(operator)
  }

  override fun evaluiereMinus(minus: AST.Ausdruck.Minus): Typ {
    return ausdruckMussTypSein(minus.ausdruck, Typ.Primitiv.Zahl)
  }

  override fun evaluiereKonvertierung(konvertierung: AST.Ausdruck.Konvertierung): Typ{
    nichtErlaubtInKonstante(konvertierung)
    val ausdruck = evaluiereAusdruck(konvertierung.ausdruck)
    val konvertierungsTyp = typisierer.bestimmeTyp(konvertierung.typ, null, null, true)!!
    if (!ausdruck.kannNachTypKonvertiertWerden(konvertierungsTyp)){
      throw GermanSkriptFehler.KonvertierungsFehler(konvertierung.typ.name.bezeichnerToken,
          ausdruck, konvertierungsTyp)
    }
    return konvertierungsTyp
  }

  override fun evaluiereSelbstReferenz() = zuÜberprüfendeKlasse!!

  override fun evaluiereClosure(closure: AST.Ausdruck.Closure): Typ.Compound.Schnittstelle {
    nichtErlaubtInKonstante(closure)
    val schnittstelle = typisierer.bestimmeTyp(
        closure.schnittstelle,
        funktionsTypParams,
        zuÜberprüfendeKlasse?.definition?.typParameter,
        true
    )
    if (schnittstelle !is Typ.Compound.Schnittstelle) {
      throw GermanSkriptFehler.SchnittstelleErwartet(closure.schnittstelle.name.bezeichnerToken)
    }
    inferiereTypArgumente(schnittstelle)
    if (schnittstelle.definition.methodenSignaturen.size != 1) {
      throw GermanSkriptFehler.UngültigeClosureSchnittstelle(closure.schnittstelle.name.bezeichnerToken, schnittstelle.definition)
    }
    val prevRückgabeTyp = rückgabeTyp
    val prevRückgabeErreicht = rückgabeErreicht
    val signatur = schnittstelle.definition.methodenSignaturen[0]
    umgebung.pushBereich()
    for (param in signatur.parameter) {
      val paramName = holeParamName(param, schnittstelle.typArgumente)
      val paramTyp = holeParamTyp(param, schnittstelle.typArgumente)
      umgebung.schreibeVariable(paramName, paramTyp, false)
    }
    durchlaufeAufruf(
        closure.schnittstelle.name.bezeichnerToken,
        closure.körper, umgebung,
        false,
        signatur.rückgabeTyp.typ!!
    )
    umgebung.popBereich()
    rückgabeTyp = prevRückgabeTyp
    rückgabeErreicht = prevRückgabeErreicht
    return schnittstelle
  }
}

fun main() {
  val typPrüfer = TypPrüfer(File("./beispiele/HalloWelt.gm"))
  typPrüfer.prüfe()
  typPrüfer.logger.print()
}