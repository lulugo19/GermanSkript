package germanskript

import java.io.File
import germanskript.util.SimpleLogger

class TypPrüfer(startDatei: File): ProgrammDurchlaufer<Typ>(startDatei) {
  val typisierer = Typisierer(startDatei)
  override val definierer = typisierer.definierer
  override val nichts = Typ.Compound.KlassenTyp.BuildInType.Nichts
  override var umgebung = Umgebung<Typ>()
  val logger = SimpleLogger()
  val ast: AST.Programm get() = typisierer.ast

  // ganz viele veränderliche Variablen :(
  // Da wir von der abstrakten Klasse 'ProgrammDurchlaufer' erben und dieser die Funktionen vorgibt, brauchen wir sie leider.
  // Alternativ könnte man sich überlegen, sich von dem ProgrammDurchlaufer zu trennen
  // und Funktionsparameter für den Kontext zu verwenden.
  private var zuÜberprüfendeKlasse: Typ.Compound.KlassenTyp? = null
  private var rückgabeTyp: Typ = Typ.Compound.KlassenTyp.BuildInType.Nichts
  private var funktionsTypParams: List<AST.Definition.TypParam>? = null
  private var klassenTypParams: List<AST.Definition.TypParam>? = null
  private var letzterFunktionsAufruf: AST.Satz.Ausdruck.FunktionsAufruf? = null
  private var rückgabeErreicht = false
  private var evaluiereKonstante = false
  private var erwarteterTyp: Typ? = null
  private var methodenObjekt: Typ.Compound? = null

  fun prüfe() {
    typisierer.typisiere()
    definierer.konstanten.forEach(::prüfeKonstante)
    // zuerst die Klassendefinitionen, damit noch private Eigenschaften definiert werden können
    definierer.holeDefinitionen<AST.Definition.Typdefinition.Klasse>().forEach(::prüfeKlasse)

    definierer.funktionsDefinitionen.forEach {funktion ->
      prüfeFunktion(funktion, Umgebung())
    }

    // neue germanskript.Umgebung
    durchlaufeAufruf(ast.programmStart!!, ast.programm, Umgebung(), true, Typ.Compound.KlassenTyp.BuildInType.Nichts, true)
  }

  private fun ausdruckMussTypSein(
      ausdruck: AST.Satz.Ausdruck,
      erwarteterTyp: Typ
  ): Typ {
    // verwende den erwarteten Typen um für Objektinitialisierung und Closures Typargumente zu inferieren
    this.erwarteterTyp = erwarteterTyp
    val ausdruckTyp = evaluiereAusdruck(ausdruck)
    this.erwarteterTyp = null

    if (!typIstTyp(ausdruckTyp, erwarteterTyp)) {
      throw GermanSkriptFehler.TypFehler.FalscherTyp(
          holeErstesTokenVonAusdruck(ausdruck), ausdruckTyp, erwarteterTyp.name
      )
    }
    return ausdruckTyp
  }

  private fun typMussTypSein(
      typ: Typ,
      erwarteterTyp: Typ,
      token: Token
  ) {
    if (!typIstTyp(typ, erwarteterTyp)) {
      throw GermanSkriptFehler.TypFehler.FalscherTyp(
          token, typ, erwarteterTyp.name
      )
    }
  }

  fun typIstTyp(typ: Typ, sollTyp: Typ): Boolean {
    fun überprüfeSchnittstelle(klasse: Typ.Compound.KlassenTyp, schnittstelle: Typ.Compound.Schnittstelle): Boolean {
      if (klasse.definition.implementierteSchnittstellen.contains(schnittstelle)) {
        return true
      }
      if (klasse.definition.elternKlasse != null) {
        return überprüfeSchnittstelle(klasse.definition.elternKlasse!!.klasse.typ!! as Typ.Compound.KlassenTyp, schnittstelle)
      }
      return false
    }


    fun überprüfeKlassenHierarchie(klasse: Typ.Compound.KlassenTyp, elternKlasse: Typ.Compound.KlassenTyp): Boolean {
      var laufTyp: Typ.Compound.KlassenTyp? = klasse
      while (laufTyp != null) {
        if (laufTyp == elternKlasse) {
          return true
        }
        laufTyp = laufTyp.definition.elternKlasse?.klasse?.typ as Typ.Compound.KlassenTyp?
      }
      return false
    }

    if (sollTyp is Typ.Generic) {
      val schnittstellenKorrekt = sollTyp.typParam.schnittstellen.all { typIstTyp(typ, it.typ!!) }
      return if (sollTyp.typParam.elternKlasse != null) {
        typIstTyp(typ, sollTyp.typParam.elternKlasse.typ!!) && schnittstellenKorrekt
      } else {
        schnittstellenKorrekt
      }
    }

    return when (sollTyp) {
      is Typ.Compound.Schnittstelle -> when(typ) {
        is Typ.Generic -> typ.typParam.schnittstellen.find { it.typ!! == sollTyp } != null
        is Typ.Compound.KlassenTyp -> überprüfeSchnittstelle(typ, sollTyp)
        else -> typ == sollTyp
      }
      is Typ.Compound.KlassenTyp -> when(typ) {
        is Typ.Generic -> typ.typParam.elternKlasse?.typ == sollTyp
        is Typ.Compound.KlassenTyp -> überprüfeKlassenHierarchie(typ, sollTyp)
        else -> typ == sollTyp
      }
      else -> typ == sollTyp
    }
  }

  /**
   * inferiert Typargumente basierend auf den erwarteten Typen
   */
  private fun inferiereTypArgumente(typ: Typ.Compound) {
    if (erwarteterTyp is Typ.Compound && typ.definition === (erwarteterTyp as Typ.Compound).definition) {
      if (typ.typArgumente.isEmpty()) {
        typ.typArgumente = (erwarteterTyp as Typ.Compound).typArgumente.map { arg ->
          when (val argTyp = arg.typ) {
            // TODO: Gucke dir das nochmal genauer an. Braucht man diese Ersetzung wirklich? Welche Probleme können enstehen?
            is Typ.Generic -> when (argTyp.kontext) {
              TypParamKontext.Typ -> methodenObjekt!!.typArgumente[argTyp.index]
              TypParamKontext.Funktion -> letzterFunktionsAufruf!!.typArgumente[argTyp.index]
            }
            else -> arg
          }
        }
      } else {
        typ.typArgumente.forEach {arg ->
          typisierer.bestimmeTyp(arg, funktionsTypParams, klassenTypParams, true)}
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

  private fun vereineTypen(token: Token, typA: Typ, typB: Typ): Typ {
    return when {
      typIstTyp(typA, typB) -> typB
      typIstTyp(typB, typA) -> typA
      else -> throw GermanSkriptFehler.TypFehler.TypenUnvereinbar(token, typA, typB)
    }
  }

  private fun prüfeKonstante(konstante: AST.Definition.Konstante) {
    evaluiereKonstante = true
    val typ = evaluiereAusdruck(konstante.wert)
    if (typ !is Typ.Compound.KlassenTyp.BuildInType) {
      throw GermanSkriptFehler.KonstantenFehler(holeErstesTokenVonAusdruck(konstante.wert))
    }
    konstante.typ = typ
    evaluiereKonstante = false
  }

  private fun nichtErlaubtInKonstante(ausdruck: AST.Satz.Ausdruck) {
    if (evaluiereKonstante) {
      throw GermanSkriptFehler.KonstantenFehler(holeErstesTokenVonAusdruck(ausdruck))
    }
  }

  // breche niemals Sätze ab
  override fun sollSätzeAbbrechen(): Boolean = false

  private fun prüfeFunktion(funktion: AST.Definition.Funktion, funktionsUmgebung: Umgebung<Typ>) {
    funktionsUmgebung.pushBereich()
    for (parameter in funktion.signatur.parameter) {
      funktionsUmgebung.schreibeVariable(parameter.name, parameter.typKnoten.typ!!, false)
    }
    val signatur = funktion.signatur
    funktionsTypParams = signatur.typParameter
    durchlaufeAufruf(signatur.name.toUntyped(), funktion.körper, funktionsUmgebung, false, signatur.rückgabeTyp.typ!!, false)
    funktionsTypParams = null
    funktionsUmgebung.popBereich()
  }

  private fun erstelleGenerischeTypArgumente(typParameter: List<AST.Definition.TypParam>): List<AST.TypKnoten> {
    return typParameter.mapIndexed { index, param ->
      val typKnoten = AST.TypKnoten(emptyList(), param.binder, emptyList())
      typKnoten.typ = Typ.Generic(param, index, TypParamKontext.Typ)
      typKnoten
    }
  }

  private fun prüfeKlasse(klasse: AST.Definition.Typdefinition.Klasse) {
    if (klasse.geprüft) {
      return
    }
    klasse.geprüft = true
    typisierer.typisiereKlasse(klasse)
    // Da die Kindklasse abhängig von der Elternklasse ist, muss zuerst die Elternklasse geprüft werden
    if (klasse.elternKlasse != null) {
      if (klasse.elternKlasse.klasse.typ == null) {
        typisierer.bestimmeTyp(klasse.elternKlasse.klasse, null, null, true)
      }
      val elternKlasse = klasse.elternKlasse.klasse.typ as Typ.Compound.KlassenTyp
      prüfeKlasse(elternKlasse.definition)
    }
    zuÜberprüfendeKlasse = typAusKlassenDefinition(klasse, erstelleGenerischeTypArgumente(klasse.typParameter))
    klassenTypParams = klasse.typParameter
    durchlaufeAufruf(klasse.name.bezeichner.toUntyped(), klasse.konstruktor, Umgebung(), true, Typ.Compound.KlassenTyp.BuildInType.Nichts, false)
    klasse.implementierungen.forEach {implementierung -> prüfeImplementierung(klasse, implementierung)}
    klassenTypParams = null

    fun fügeElternKlassenMethodenHinzu(elternKlasse: AST.Definition.Typdefinition.Klasse) {
      for ((methodenName, methode) in elternKlasse.methoden) {
        klasse.methoden.putIfAbsent(methodenName, methode)
      }
      for ((konvertierungsTyp, konvertierung) in elternKlasse.konvertierungen) {
        klasse.konvertierungen.putIfAbsent(konvertierungsTyp, konvertierung)
      }
      if (elternKlasse.elternKlasse != null) {
        fügeElternKlassenMethodenHinzu((elternKlasse.elternKlasse.klasse.typ as Typ.Compound.KlassenTyp).definition)
      }
    }

    if (klasse.elternKlasse != null) {
      fügeElternKlassenMethodenHinzu((klasse.elternKlasse.klasse.typ as Typ.Compound.KlassenTyp).definition)
    }

    zuÜberprüfendeKlasse = null
  }

  private fun prüfeImplementierung(klasse: AST.Definition.Typdefinition.Klasse, implementierung: AST.Definition.Implementierung) {
    klassenTypParams = implementierung.typParameter
    if (klasse.typParameter.size != implementierung.klasse.typArgumente.size) {
      throw GermanSkriptFehler.TypFehler.TypArgumentFehler(
          implementierung.klasse.name.bezeichnerToken,
          implementierung.klasse.typArgumente.size,
          klasse.typParameter.size
      )
    }
    zuÜberprüfendeKlasse = typAusKlassenDefinition(klasse, implementierung.klasse.typArgumente)
    prüfeImplementierungsBereich(implementierung.bereich, Umgebung())
    zuÜberprüfendeKlasse = null
  }

  private fun prüfeImplementierungsBereich(implBereich: AST.Definition.ImplementierungsBereich, umgebung: Umgebung<Typ>) {
    implBereich.methoden.forEach { methode ->
      prüfeFunktion(methode, umgebung)
    }
    implBereich.konvertierungen.forEach { konvertierung ->
      durchlaufeAufruf(
          konvertierung.typ.name.bezeichnerToken,
          konvertierung.definition, umgebung, true, konvertierung.typ.typ!!, false)
    }
    implBereich.berechneteEigenschaften.forEach { eigenschaft ->
      durchlaufeAufruf(eigenschaft.name.bezeichner.toUntyped(),
          eigenschaft.definition, umgebung,
          true, eigenschaft.rückgabeTyp.typ!!, false)
    }
  }

  private fun typAusKlassenDefinition(klasse: AST.Definition.Typdefinition.Klasse, typArgumente: List<AST.TypKnoten>): Typ.Compound.KlassenTyp {
    return when (klasse) {
      Typ.Compound.KlassenTyp.Liste.definition -> Typ.Compound.KlassenTyp.Liste(typArgumente)
      Typ.Compound.KlassenTyp.BuildInType.Zeichenfolge.definition -> Typ.Compound.KlassenTyp.BuildInType.Zeichenfolge
      Typ.Compound.KlassenTyp.BuildInType.Zahl.definition -> Typ.Compound.KlassenTyp.BuildInType.Zahl
      Typ.Compound.KlassenTyp.BuildInType.Boolean.definition -> Typ.Compound.KlassenTyp.BuildInType.Boolean
      Typ.Compound.KlassenTyp.BuildInType.Nichts.definition -> Typ.Compound.KlassenTyp.BuildInType.Nichts
      else -> Typ.Compound.KlassenTyp.Klasse(klasse, typArgumente)
    }
  }

  private fun durchlaufeAufruf(token: Token, bereich: AST.Satz.Bereich, umgebung: Umgebung<Typ>, neuerBereich: Boolean, rückgabeTyp: Typ, impliziteRückgabe: Boolean): Typ {
    this.rückgabeTyp = rückgabeTyp
    this.umgebung = umgebung
    rückgabeErreicht = false
    return durchlaufeBereich(bereich, neuerBereich).also {
      if (!impliziteRückgabe && !rückgabeErreicht && rückgabeTyp != Typ.Compound.KlassenTyp.BuildInType.Nichts) {
        throw GermanSkriptFehler.RückgabeFehler.RückgabeVergessen(token, rückgabeTyp)
      }
    }
  }

  override fun durchlaufeVariablenDeklaration(deklaration: AST.Satz.VariablenDeklaration) {
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
        // dem neuen germanskript.intern.Wert ist
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
  }

  override fun durchlaufeListenElementZuweisung(zuweisung: AST.Satz.ListenElementZuweisung) {
    ausdruckMussTypSein(zuweisung.index, Typ.Compound.KlassenTyp.BuildInType.Zahl)
    val elementTyp = evaluiereListenSingular(zuweisung.singular)
    ausdruckMussTypSein(zuweisung.wert, elementTyp)
  }

  private fun ersetzeGenerics(
      typKnoten: AST.TypKnoten,
      funktionsTypArgumente: List<AST.TypKnoten>?,
      typTypArgumente: List<AST.TypKnoten>?): AST.TypKnoten {
    return when (val typ = typKnoten.typ!!) {
      is Typ.Generic -> when (typ.kontext) {
        TypParamKontext.Funktion -> funktionsTypArgumente!![typ.index]
        TypParamKontext.Typ -> typTypArgumente!![typ.index]
      }
      is Typ.Compound -> {
        typKnoten.copy(typArgumente = typ.typArgumente.map {
          ersetzeGenerics(it, funktionsTypArgumente, typTypArgumente)
        })
      }
      else -> typKnoten
    }
  }

  override fun durchlaufeFunktionsAufruf(funktionsAufruf: AST.Satz.Ausdruck.FunktionsAufruf, istAusdruck: Boolean): Typ {
    if (funktionsAufruf.vollerName == null) {
      funktionsAufruf.vollerName = definierer.holeVollenNamenVonFunktionsAufruf(funktionsAufruf, null)
    }
    var funktionsSignatur: AST.Definition.FunktionsSignatur? = null
    val methodenBlockObjekt = umgebung.holeMethodenBlockObjekt()

    if (funktionsAufruf.subjekt != null) {
      val subjekt = evaluiereAusdruck(funktionsAufruf.subjekt)
      val fund = findeMethode(
          funktionsAufruf,
          subjekt,
          FunktionsAufrufTyp.METHODEN_SUBJEKT_AUFRUF
      )
      funktionsSignatur = fund?.first
      methodenObjekt = fund?.second
      if (funktionsSignatur == null ) {
        throw GermanSkriptFehler.Undefiniert.Funktion(funktionsAufruf.token, funktionsAufruf)
      }
    }

    // ist Methodenaufruf von Bereich-Variable
    if (funktionsSignatur == null &&
        methodenBlockObjekt != null &&
        funktionsAufruf.reflexivPronomen?.typ !is TokenTyp.REFLEXIV_PRONOMEN.ERSTE_FORM
    ) {
      val fund = findeMethode(
          funktionsAufruf,
          methodenBlockObjekt,
          FunktionsAufrufTyp.METHODEN_BEREICHS_AUFRUF
      )
      funktionsSignatur = fund?.first
      methodenObjekt = fund?.second
    }

    // ist Methoden-Selbst-Aufruf
    if (funktionsSignatur == null && zuÜberprüfendeKlasse != null
        && funktionsAufruf.reflexivPronomen?.typ !is TokenTyp.REFLEXIV_PRONOMEN.ZWEITE_FORM) {

      // Wenn man in einem Super-Block ist, wird die Eltern-Methode aufgerufen
      funktionsSignatur = if (inSuperBlock && zuÜberprüfendeKlasse!!.definition.elternKlasse != null) {
        val elternKlasse = zuÜberprüfendeKlasse!!.definition.elternKlasse!!.klasse.typ!! as Typ.Compound.KlassenTyp
        val fund = findeMethode(
            funktionsAufruf,
            elternKlasse,
            FunktionsAufrufTyp.METHODEN_SELBST_AUFRUF
        )
        methodenObjekt = fund?.second
        fund?.first
      } else {
        val fund = findeMethode(
            funktionsAufruf,
            zuÜberprüfendeKlasse!!,
            FunktionsAufrufTyp.METHODEN_SELBST_AUFRUF
        )
        methodenObjekt = fund?.second
        fund?.first
      }
    }

    // ist normale Funktion
    val undefiniertFehler = try {
      if (funktionsSignatur == null) {
        funktionsAufruf.funktionsDefinition = definierer.holeFunktionsDefinition(funktionsAufruf)
        funktionsSignatur = überprüfePassendeFunktionsSignatur(
            funktionsAufruf,
            funktionsAufruf.funktionsDefinition!!.signatur,
            FunktionsAufrufTyp.FUNKTIONS_AUFRUF, null
        )
        null
      } else {
        null
      }
    } catch (fehler: GermanSkriptFehler.Undefiniert.Funktion) {
      fehler
    }

    // ist Methoden-Objekt-Aufruf als letzte Möglichkeit
    // das bedeutet Funktionsnamen gehen vor Methoden-Objekt-Aufrufen
    if (funktionsSignatur == null && funktionsAufruf.objekt != null) {
      val objektTyp = try {
        typisierer.bestimmeTyp(funktionsAufruf.objekt.name, funktionsTypParams, klassenTypParams)
      }
      catch (fehler: GermanSkriptFehler.Undefiniert.Typ) {
        null
      }
      if (objektTyp != null) {
        val objektName = funktionsAufruf.objekt.name
        val reflexivPronomen = holeReflexivPronomenForm(false, objektName.kasus, objektName.numerus).pronomen
        val fund = findeMethode(
            funktionsAufruf,
            objektTyp,
            FunktionsAufrufTyp.METHODEN_REFLEXIV_AUFRUF,
            reflexivPronomen
        )
        funktionsSignatur = fund?.first
        methodenObjekt = fund?.second
      }

    }

    if (funktionsSignatur == null) {
      throw undefiniertFehler!!
    }

    // hier kommt noch ein nachträglicher Grammatik-Check ob der Numerus des Reflexivpronomens
    // auch mit dem Objekt übereinstimmt
    if (methodenObjekt != null && funktionsAufruf.reflexivPronomen != null) {
      val reflexivPronomen = funktionsAufruf.reflexivPronomen.typ
      if (methodenObjekt is Typ.Compound.KlassenTyp.Liste && funktionsAufruf.reflexivPronomen.typ.numerus == Numerus.SINGULAR) {
        val erwartetesPronomen = holeReflexivPronomenForm(true, reflexivPronomen.kasus.first(), Numerus.PLURAL)
        throw GermanSkriptFehler.GrammatikFehler.FalschesReflexivPronomen(
            funktionsAufruf.reflexivPronomen.toUntyped(), reflexivPronomen, erwartetesPronomen)
      } else if (methodenObjekt !is Typ.Compound.KlassenTyp.Liste && funktionsAufruf.reflexivPronomen.typ.numerus == Numerus.PLURAL) {
        val erwartetesPronomen = holeReflexivPronomenForm(true, reflexivPronomen.kasus.first(), Numerus.SINGULAR)
        throw GermanSkriptFehler.GrammatikFehler.FalschesReflexivPronomen(
            funktionsAufruf.reflexivPronomen.toUntyped(), reflexivPronomen, erwartetesPronomen)
      }
    }

    // TODO: überprüfe ob die Typargumente des Methodenobjekts mit den Generic-Constrains übereinstimmen
    if (methodenObjekt is Typ.Compound.KlassenTyp && methodenObjekt!!.typArgumente.isNotEmpty()) {
      val implementierung = funktionsSignatur.findNodeInParents<AST.Definition.Implementierung>()!!
      for (paramIndex in implementierung.klasse.typArgumente.indices) {
        if (!typIstTyp(methodenObjekt!!.typArgumente[paramIndex].typ!!, implementierung.klasse.typArgumente[paramIndex].typ!!)) {
          throw GermanSkriptFehler.Undefiniert.Methode(funktionsAufruf.token, funktionsAufruf, methodenObjekt!!.name)
        }
      }
    }

    val typKontext = methodenObjekt?.typArgumente

    val parameter = funktionsSignatur.parameter.toList()
    val argumente = funktionsAufruf.argumente.toList()
    val j = if (funktionsAufruf.aufrufTyp == FunktionsAufrufTyp.METHODEN_REFLEXIV_AUFRUF) 1 else 0
    val anzahlArgumente = argumente.size - j
    if (anzahlArgumente != parameter.size) {
      throw GermanSkriptFehler.SyntaxFehler.AnzahlDerParameterFehler(funktionsSignatur.name.toUntyped())
    }

    val genericsMüssenInferiertWerden = funktionsSignatur.typParameter.isNotEmpty()
        && funktionsAufruf.typArgumente.isEmpty()
    val inferierteGenerics: MutableList<AST.TypKnoten?> = MutableList(funktionsSignatur.typParameter.size) {null}

    if (!genericsMüssenInferiertWerden) {
      funktionsAufruf.typArgumente.forEach { arg ->
        typisierer.bestimmeTyp(arg, funktionsTypParams, klassenTypParams, true)
      }
    }

    fun inferiereGenerics(typKnoten: AST.TypKnoten, token: Token, argTyp: Typ): AST.TypKnoten {
      return when (val typ = typKnoten.typ!!) {
        is Typ.Generic -> when (typ.kontext) {
          TypParamKontext.Funktion -> {
            inferierteGenerics[typ.index] = if (inferierteGenerics[typ.index] == null) {
              argTyp.inTypKnoten()
            } else {
              vereineTypen(token, inferierteGenerics[typ.index]!!.typ!!, argTyp).inTypKnoten()
            }
            inferierteGenerics[typ.index]!!
          }
          TypParamKontext.Typ -> typKontext!![typ.index]
        }
        is Typ.Compound -> {
          if (argTyp !is Typ.Compound || argTyp.typArgumente.size != typ.typArgumente.size) {
            throw GermanSkriptFehler.TypFehler.TypenUnvereinbar(token, typ, argTyp)
          }
          typKnoten.copy(typArgumente = typ.typArgumente.mapIndexed { index, arg ->
            inferiereGenerics(arg, token, argTyp.typArgumente[index].typ!!)
          })
        }
        else -> typKnoten
      }
    }

    // stimmen die Argument Typen mit den Parameter Typen überein?
    letzterFunktionsAufruf = funktionsAufruf
    for (i in parameter.indices) {
      val argAusdruck = argumente[i+j].ausdruck
      if (genericsMüssenInferiertWerden) {
        val ausdruckTyp = evaluiereAusdruck(argAusdruck)
        val ausdruckToken = holeErstesTokenVonAusdruck(argAusdruck)
        val paramTyp = inferiereGenerics(parameter[i].typKnoten, ausdruckToken, ausdruckTyp)
        typMussTypSein(ausdruckTyp, paramTyp.typ!!, ausdruckToken)
      } else {
        ausdruckMussTypSein(argAusdruck, ersetzeGenerics(parameter[i].typKnoten, funktionsAufruf.typArgumente, typKontext).typ!!)
      }
    }
    if (genericsMüssenInferiertWerden) {
      funktionsAufruf.typArgumente = inferierteGenerics.map { it!! }
    }

    val rückgabeTyp = ersetzeGenerics(funktionsSignatur.rückgabeTyp, funktionsAufruf.typArgumente, typKontext)
    letzterFunktionsAufruf = null
    methodenObjekt = null
    return rückgabeTyp.typ!!
  }

  /**
   * Hier wird überprüft, ob der Funktionsaufruf wirklich zur Funktionssignatur passt.
   * Wenn die Signatur ein Rückgabeobjekt hat, muss das Objekt des Funktionsaufrufs nicht mehr als Argument,
   * sondern als Name gesehen werden. Ob dies möglich ist wird mit dem Attribut 'rückgabeObjektMöglich' ausgedrückt.
   */
  private fun überprüfePassendeFunktionsSignatur(
      funktionsAufruf: AST.Satz.Ausdruck.FunktionsAufruf,
      signatur: AST.Definition.FunktionsSignatur?,
      aufrufTyp: FunktionsAufrufTyp,
      typ: Typ?
  ): AST.Definition.FunktionsSignatur? {
    return if (signatur != null && (!signatur.hatRückgabeObjekt || funktionsAufruf.rückgabeObjektMöglich)) {
      if (typ is Typ.Compound.KlassenTyp && aufrufTyp == FunktionsAufrufTyp.METHODEN_SELBST_AUFRUF) {
        funktionsAufruf.funktionsDefinition = typ.definition.methoden[funktionsAufruf.vollerName!!]
      }
      funktionsAufruf.hatRückgabeObjekt = signatur.hatRückgabeObjekt
      funktionsAufruf.aufrufTyp = aufrufTyp
      signatur
    } else null
  }

  private fun findeMethode(
      funktionsAufruf: AST.Satz.Ausdruck.FunktionsAufruf,
      typ: Typ,
      aufrufTyp: FunktionsAufrufTyp,
      ersetzeObjekt: String? = null
  ): Pair<AST.Definition.FunktionsSignatur, Typ.Compound>? {
    if (ersetzeObjekt != null) {
      funktionsAufruf.vollerName = definierer.holeVollenNamenVonFunktionsAufruf(funktionsAufruf, ersetzeObjekt)
    }

    if (typ is Typ.Generic) {
      for (schnittstelle in typ.typParam.schnittstellen) {
        findeMethode(funktionsAufruf, schnittstelle.typ!!, aufrufTyp, ersetzeObjekt)?.also { return it }
      }
      typ.typParam.elternKlasse?.also {
        klasse -> findeMethode(funktionsAufruf, klasse.typ!!, aufrufTyp, ersetzeObjekt)?.also { return it }
      }
    }

    if (typ !is Typ.Compound) {
      return null
    }

    // ist Methodenaufruf von Block-Variable
    typ.definition.findeMethode(funktionsAufruf.vollerName!!)?.also { signatur ->
      überprüfePassendeFunktionsSignatur(funktionsAufruf, signatur, aufrufTyp, typ)?.also { return Pair(it, typ) }
    }

    if (typ.definition.typParameter.isNotEmpty()) {
      // Die Funktionstypparameter werden nicht ersetzt, da sie möglicherweise erst inferiert werden müssen.
      val ersetzeTypArgsName = definierer.holeVollenNamenVonFunktionsAufruf(
          funktionsAufruf, typ.definition.typParameter, typ.typArgumente, ersetzeObjekt)

      typ.definition.findeMethode(ersetzeTypArgsName)?.also { signatur ->
        // TODO: Die Typparameternamen müssen für den Interpreter später hier ersetzt werden
        funktionsAufruf.vollerName = ersetzeTypArgsName
        überprüfePassendeFunktionsSignatur(funktionsAufruf, signatur, aufrufTyp, typ)?.also { return Pair(it, typ) }
      }
    }

    if (funktionsAufruf.reflexivPronomen != null) {
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

  private fun prüfeBedingung(bedingung: AST.Satz.BedingungsTerm): Typ {
    ausdruckMussTypSein(bedingung.bedingung, Typ.Compound.KlassenTyp.BuildInType.Boolean)
    return durchlaufeBereich(bedingung.bereich, true)
  }

  override fun durchlaufeBedingungsSatz(bedingungsSatz: AST.Satz.Ausdruck.Bedingung, istAusdruck: Boolean): Typ {
    if (istAusdruck && bedingungsSatz.sonst == null) {
      throw GermanSkriptFehler.WennAusdruckBrauchtSonst(holeErstesTokenVonAusdruck(bedingungsSatz))
    }

    var bedingungsTyp: Typ? = null

    for (bedingung in bedingungsSatz.bedingungen) {
      val typ = prüfeBedingung(bedingung)
      bedingungsTyp = try {
        if (bedingungsTyp == null) {
          typ
        } else {
          vereineTypen(holeErstesTokenVonAusdruck(bedingung.bedingung), bedingungsTyp, typ)
        }
      } catch (fehler: GermanSkriptFehler.TypFehler.TypenUnvereinbar) {
        if (istAusdruck) {
          throw fehler
        }
        Typ.Compound.KlassenTyp.BuildInType.Nichts
      }
    }

    if (bedingungsSatz.sonst != null) {
      bedingungsTyp = try {
        vereineTypen(
            holeErstesTokenVonAusdruck(bedingungsSatz.bedingungen[0].bedingung),
            bedingungsTyp!!,
            durchlaufeBereich(bedingungsSatz.sonst!!, true)
        )
      } catch (fehler: GermanSkriptFehler.TypFehler.TypenUnvereinbar) {
        if (istAusdruck) {
          throw fehler
        }
        Typ.Compound.KlassenTyp.BuildInType.Nichts
      }
    }
    return bedingungsTyp?: Typ.Compound.KlassenTyp.BuildInType.Nichts
  }

  override fun durchlaufeSolangeSchleife(schleife: AST.Satz.SolangeSchleife) {
    prüfeBedingung(schleife.bedingung)
  }

  override fun durchlaufeFürJedeSchleife(schleife: AST.Satz.FürJedeSchleife) {
    val elementTyp = when {
      schleife.iterierbares != null -> {
        val iterierbarerTyp = evaluiereAusdruck(schleife.iterierbares)
        val iterierbareSchnittstelle = typisierer.prüfeIterierbar(iterierbarerTyp)
            ?: throw GermanSkriptFehler.TypFehler.IterierbarErwartet(holeErstesTokenVonAusdruck(schleife.iterierbares))
        val typArgumente = if (iterierbarerTyp is Typ.Compound) iterierbarerTyp.typArgumente else null
        ersetzeGenerics(iterierbareSchnittstelle.typArgumente[0], null, typArgumente).typ!!
      }
      schleife.reichweite != null -> {
        val (anfang, ende) = schleife.reichweite
        ausdruckMussTypSein(anfang, Typ.Compound.KlassenTyp.BuildInType.Zahl)
        ausdruckMussTypSein(ende, Typ.Compound.KlassenTyp.BuildInType.Zahl)
        Typ.Compound.KlassenTyp.BuildInType.Zahl
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
      val typ = typisierer.bestimmeTyp(
          fange.param.typKnoten, funktionsTypParams, klassenTypParams, true)!!
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
    return Typ.Compound.KlassenTyp.BuildInType.Niemals
  }

  override fun durchlaufeIntern(intern: AST.Satz.Intern): Typ {
    // Hier muss nicht viel gemacht werden
    // die internen Sachen sind schon von kotlin Typ-gecheckt :)
    rückgabeErreicht = true
    return rückgabeTyp
  }

  override fun bevorDurchlaufeMethodenBereich(methodenBereich: AST.Satz.Ausdruck.MethodenBereich, blockObjekt: Typ?) {
    if (blockObjekt !is Typ.Compound && blockObjekt !is Typ.Compound.Schnittstelle) {
      throw GermanSkriptFehler.TypFehler.ObjektErwartet(holeErstesTokenVonAusdruck(methodenBereich.objekt))
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

  override fun evaluiereZeichenfolge(ausdruck: AST.Satz.Ausdruck.Zeichenfolge) = Typ.Compound.KlassenTyp.BuildInType.Zeichenfolge

  override fun evaluiereZahl(ausdruck: AST.Satz.Ausdruck.Zahl) = Typ.Compound.KlassenTyp.BuildInType.Zahl

  override fun evaluiereBoolean(ausdruck: AST.Satz.Ausdruck.Boolean) = Typ.Compound.KlassenTyp.BuildInType.Boolean

  override fun evaluiereVariable(variable: AST.Satz.Ausdruck.Variable): Typ {
    return try {
      super.evaluiereVariable(variable)
    } catch (undefiniert: GermanSkriptFehler.Undefiniert.Variable) {
      // Wenn die Variable nicht gefunden wurde, wird überprüft, ob es sich vielleicht um eine Konstante handelt
      if (variable.name.vornomen == null) {
        try {
          val konstante = AST.Satz.Ausdruck.Konstante(emptyList(), variable.name.bezeichner)
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

  override fun evaluiereKonstante(konstante: AST.Satz.Ausdruck.Konstante): Typ {
    val konstanteDefinition = definierer.holeKonstante(konstante)
    // tragen den Wert der Konstanten ein
    konstante.wert = konstanteDefinition.wert
    return evaluiereAusdruck(konstante.wert!!)
  }

  override fun evaluiereListe(ausdruck: AST.Satz.Ausdruck.Liste): Typ {
    nichtErlaubtInKonstante(ausdruck)
    val listenTyp = typisierer.bestimmeTyp(
        ausdruck.pluralTyp,
        funktionsTypParams,
        klassenTypParams,
        true
    ) as Typ.Compound.KlassenTyp.Liste
    ausdruck.elemente.forEach {element -> ausdruckMussTypSein(element, listenTyp.typArgumente[0].typ!!)}
    return listenTyp
  }

  override fun evaluiereListenElement(listenElement: AST.Satz.Ausdruck.ListenElement): Typ {
    nichtErlaubtInKonstante(listenElement)
    ausdruckMussTypSein(listenElement.index, Typ.Compound.KlassenTyp.BuildInType.Zahl)
    val zeichenfolge = evaluiereVariable(listenElement.singular.nominativ)
    // Bei einem Zugriff auf einen Listenindex kann es sich auch um eine Zeichenfolge handeln
    if (zeichenfolge != null && zeichenfolge == Typ.Compound.KlassenTyp.BuildInType.Zeichenfolge) {
      listenElement.istZeichenfolgeZugriff = true
      return Typ.Compound.KlassenTyp.BuildInType.Zeichenfolge
    }
    return evaluiereListenSingular(listenElement.singular)
  }

  private fun evaluiereListenSingular(singular: AST.WortArt.Nomen): Typ {
    val plural = singular.ganzesWort(Kasus.NOMINATIV, Numerus.PLURAL, true)
    val liste = when (singular.vornomen?.typ) {
        TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT, TokenTyp.VORNOMEN.JEDE, null -> evaluiereVariable(plural)?:
          throw GermanSkriptFehler.Undefiniert.Variable(singular.bezeichner.toUntyped(), plural)
        TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN ->
          holeNormaleEigenschaftAusKlasse(singular, zuÜberprüfendeKlasse!!, Numerus.PLURAL).typKnoten.typ!!
        TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.DEIN -> holeNormaleEigenschaftAusKlasse(
            singular, umgebung.holeMethodenBlockObjekt()!! as Typ.Compound.KlassenTyp, Numerus.PLURAL).typKnoten.typ!!
        else -> throw Exception("Dieser Fall sollte nie eintreten.")
    }
    return (liste as Typ.Compound.KlassenTyp.Liste).typArgumente[0].typ!!
  }

  override fun evaluiereObjektInstanziierung(instanziierung: AST.Satz.Ausdruck.ObjektInstanziierung): Typ {
    nichtErlaubtInKonstante(instanziierung)
    val klasse = typisierer.bestimmeTyp(
        instanziierung.klasse,
        funktionsTypParams,
        klassenTypParams,
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

      val erwarteterTyp = when(val typ = eigenschaft.typKnoten.typ!!) {
        is Typ.Generic ->
          if (typ.kontext == TypParamKontext.Typ) instanziierung.klasse.typArgumente[typ.index].typ!!
          else typ
        else -> typ
      }
      // die Typen müssen übereinstimmen
      ausdruckMussTypSein(zuweisung.ausdruck, erwarteterTyp)
    }

    if (instanziierung.eigenschaftsZuweisungen.size > definition.eigenschaften.size) {
      throw GermanSkriptFehler.EigenschaftsFehler.UnerwarteteEigenschaft(
          instanziierung.eigenschaftsZuweisungen[definition.eigenschaften.size].name.bezeichner.toUntyped())
    }
    return klasse
  }

  override fun evaluiereEigenschaftsZugriff(eigenschaftsZugriff: AST.Satz.Ausdruck.EigenschaftsZugriff): Typ {
    nichtErlaubtInKonstante(eigenschaftsZugriff)
    val klasse = evaluiereAusdruck(eigenschaftsZugriff.objekt)
    return if (klasse is Typ.Compound.KlassenTyp) {
      holeEigenschaftAusKlasse(eigenschaftsZugriff, klasse)
    }
    else {
      throw GermanSkriptFehler.TypFehler.ObjektErwartet(holeErstesTokenVonAusdruck(eigenschaftsZugriff.objekt))
    }
  }

  override fun evaluiereSelbstEigenschaftsZugriff(eigenschaftsZugriff: AST.Satz.Ausdruck.SelbstEigenschaftsZugriff): Typ {
    nichtErlaubtInKonstante(eigenschaftsZugriff)
    return holeEigenschaftAusKlasse(eigenschaftsZugriff, zuÜberprüfendeKlasse!!)
  }


  override fun evaluiereMethodenBlockEigenschaftsZugriff(eigenschaftsZugriff: AST.Satz.Ausdruck.MethodenBereichEigenschaftsZugriff): Typ {
    nichtErlaubtInKonstante(eigenschaftsZugriff)
    val methodenBlockObjekt = umgebung.holeMethodenBlockObjekt() as Typ.Compound.KlassenTyp
    return holeEigenschaftAusKlasse(eigenschaftsZugriff, methodenBlockObjekt)
  }

  private fun holeNormaleEigenschaftAusKlasse(
      eigenschaftsName: AST.WortArt.Nomen,
      klasse: Typ.Compound.KlassenTyp,
      numerus: Numerus = eigenschaftsName.numerus
  ): AST.Definition.Parameter {
    val eigName = eigenschaftsName.ganzesWort(Kasus.NOMINATIV, numerus, true)
    for (eigenschaft in klasse.definition.eigenschaften) {
      val name = holeParamName(eigenschaft ,klasse.typArgumente)
      if (name.nominativ == eigName) {
        return eigenschaft
      }
    }
    if (klasse.definition.elternKlasse != null) {
      try {
        return holeNormaleEigenschaftAusKlasse(eigenschaftsName,
          klasse.definition.elternKlasse!!.klasse.typ!! as Typ.Compound.KlassenTyp, numerus)
      }
      catch (fehler: GermanSkriptFehler.Undefiniert) {
        // mache nichts hier
      }
    }
    throw GermanSkriptFehler.Undefiniert.Eigenschaft(eigenschaftsName.bezeichner.toUntyped(), eigName, klasse.definition.name.nominativ)
  }

  private fun holeEigenschaftAusKlasse(eigenschaftsZugriff: AST.Satz.Ausdruck.IEigenschaftsZugriff, klasse: Typ.Compound.KlassenTyp): Typ {
    val eigenschaftsName = eigenschaftsZugriff.eigenschaftsName
    val klassenDefinition = klasse.definition
    val typ = try {
      holeNormaleEigenschaftAusKlasse(eigenschaftsName, klasse).typKnoten.typ!!
    } catch (fehler: GermanSkriptFehler.Undefiniert.Eigenschaft) {
      if (!klassenDefinition.berechneteEigenschaften.containsKey(eigenschaftsName.nominativ)) {
        if (klasse.definition.elternKlasse != null) {
          try {
            return holeEigenschaftAusKlasse(eigenschaftsZugriff,
                klasse.definition.elternKlasse!!.klasse.typ!! as Typ.Compound.KlassenTyp
            )
          } catch (_: GermanSkriptFehler.Undefiniert.Eigenschaft) {
            throw fehler
          }
        }
        throw fehler
      } else {
        eigenschaftsZugriff.aufrufName = "${eigenschaftsName.nominativ} von ${klassenDefinition.namensToken.wert}"
        klassenDefinition.berechneteEigenschaften.getValue(eigenschaftsName.nominativ).rückgabeTyp.typ!!
      }
    }
    return if (typ is Typ.Generic) klasse.typArgumente[typ.index].typ!! else typ
  }

  override fun evaluiereBinärenAusdruck(ausdruck: AST.Satz.Ausdruck.BinärerAusdruck): Typ {
    val linkerTyp = evaluiereAusdruck(ausdruck.links)
    val operator = ausdruck.operator.typ.operator
    if (!linkerTyp.definierteOperatoren.containsKey(operator)) {
      throw GermanSkriptFehler.Undefiniert.Operator(ausdruck.operator.toUntyped(), linkerTyp.name)
    }
    // es wird erwartete, dass bei einem binären Ausdruck beide Operanden vom selben Typen sind
    ausdruckMussTypSein(ausdruck.rechts, linkerTyp)
    return linkerTyp.definierteOperatoren.getValue(operator)
  }

  override fun evaluiereMinus(minus: AST.Satz.Ausdruck.Minus): Typ {
    return ausdruckMussTypSein(minus.ausdruck, Typ.Compound.KlassenTyp.BuildInType.Zahl)
  }

  override fun evaluiereKonvertierung(konvertierung: AST.Satz.Ausdruck.Konvertierung): Typ{
    nichtErlaubtInKonstante(konvertierung)
    val ausdruck = evaluiereAusdruck(konvertierung.ausdruck)
    val konvertierungsTyp = typisierer.bestimmeTyp(konvertierung.typ, null, null, true)!!
    if (!typIstTyp(ausdruck, konvertierungsTyp) && !typIstTyp(konvertierungsTyp, ausdruck) &&
        !ausdruck.kannNachTypKonvertiertWerden(konvertierungsTyp)){
      throw GermanSkriptFehler.KonvertierungsFehler(konvertierung.typ.name.bezeichnerToken,
          ausdruck, konvertierungsTyp)
    }
    return konvertierungsTyp
  }

  override fun evaluiereSelbstReferenz() = zuÜberprüfendeKlasse!!

  override fun evaluiereClosure(closure: AST.Satz.Ausdruck.Closure): Typ.Compound.Schnittstelle {
    nichtErlaubtInKonstante(closure)
    val schnittstelle = typisierer.bestimmeTyp(
        closure.schnittstelle,
        funktionsTypParams,
        klassenTypParams,
        true
    )
    if (schnittstelle !is Typ.Compound.Schnittstelle) {
      throw GermanSkriptFehler.SchnittstelleErwartet(closure.schnittstelle.name.bezeichnerToken)
    }
    inferiereTypArgumente(schnittstelle)
    if (schnittstelle.definition.methodenSignaturen.size != 1) {
      throw GermanSkriptFehler.ClosureFehler.UngültigeClosureSchnittstelle(closure.schnittstelle.name.bezeichnerToken, schnittstelle.definition)
    }
    val signatur = schnittstelle.definition.methodenSignaturen[0]
    val parameter = signatur.parameter.toList()
    if (closure.bindings.size > signatur.parameter.count()) {
      throw GermanSkriptFehler.ClosureFehler.ZuVieleBinder(
          closure.bindings[parameter.size].bezeichner.toUntyped(),
          parameter.size
      )
    }
    umgebung.pushBereich()
    for (paramIndex in parameter.indices) {
      val param = parameter[paramIndex]
      val paramName = closure.bindings.getOrElse(paramIndex) {
        holeParamName(param, schnittstelle.typArgumente)
      }
      val paramTyp = holeParamTyp(param, schnittstelle.typArgumente)
      umgebung.schreibeVariable(paramName, paramTyp, false)
    }

    val rückgabe = durchlaufeAufruf(
        closure.schnittstelle.name.bezeichnerToken,
        closure.körper, umgebung,
        false,
        rückgabeTyp,
        true
    )
    val erwarteterRückgabeTyp = when(val typ = signatur.rückgabeTyp.typ!!) {
      is Typ.Generic ->
        if (typ.kontext == TypParamKontext.Typ) schnittstelle.typArgumente[typ.index].typ!!
        else typ
      else -> typ
    }
    if (erwarteterRückgabeTyp != Typ.Compound.KlassenTyp.BuildInType.Nichts && !typIstTyp(rückgabe, erwarteterRückgabeTyp)) {
      throw GermanSkriptFehler.ClosureFehler.FalscheRückgabe(closure.schnittstelle.name.bezeichnerToken, rückgabe, erwarteterRückgabeTyp)
    }
    umgebung.popBereich()
    return schnittstelle
  }

  override fun evaluiereAnonymeKlasse(anonymeKlasse: AST.Satz.Ausdruck.AnonymeKlasse): Typ {
    nichtErlaubtInKonstante(anonymeKlasse)
    val schnittstelle = typisierer.bestimmeTyp(
        anonymeKlasse.schnittstelle,
        funktionsTypParams,
        klassenTypParams,
        true
    )
    if (schnittstelle !is Typ.Compound.Schnittstelle) {
      throw GermanSkriptFehler.SchnittstelleErwartet(anonymeKlasse.schnittstelle.name.bezeichnerToken)
    }

    inferiereTypArgumente(schnittstelle)

    val klassenDefinition = AST.Definition.Typdefinition.Klasse(
        emptyList(), anonymeKlasse.schnittstelle.name as AST.WortArt.Nomen,
        null, mutableListOf(), AST.Satz.Bereich(mutableListOf())
    )

    for (eigenschaft in anonymeKlasse.bereich.eigenschaften) {
      val wert = evaluiereAusdruck(eigenschaft.wert)
      val typ = AST.TypKnoten(emptyList(), eigenschaft.name, emptyList())
      typ.typ = wert
      if (klassenDefinition.eigenschaften.any {it.name.nominativ == eigenschaft.name.nominativ}) {
        throw GermanSkriptFehler.DoppelteEigenschaft(eigenschaft.name.bezeichner.toUntyped(), klassenDefinition)
      }
      klassenDefinition.eigenschaften.add(AST.Definition.Parameter(typ, eigenschaft.name))
    }

    definierer.definiereImplementierungsKörper(anonymeKlasse.bereich, klassenDefinition)
    typisierer.typisiereImplementierungsBereich(anonymeKlasse.bereich, klassenTypParams)
    typisierer.prüfeImplementiertSchnittstelle(
        anonymeKlasse.schnittstelle.name.bezeichnerToken,
        klassenDefinition,
        schnittstelle,
        anonymeKlasse.bereich
    )

    val klassenTyp = Typ.Compound.KlassenTyp.Klasse(klassenDefinition, emptyList())
    anonymeKlasse.typ = klassenTyp

    val vorherigeKlasse = zuÜberprüfendeKlasse
    zuÜberprüfendeKlasse = klassenTyp
    prüfeImplementierungsBereich(anonymeKlasse.bereich, umgebung)
    zuÜberprüfendeKlasse = vorherigeKlasse

    return klassenTyp
  }

  override fun evaluiereTypÜberprüfung(typÜberprüfung: AST.Satz.Ausdruck.TypÜberprüfung): Typ {
    typisierer.bestimmeTyp(typÜberprüfung.typ, funktionsTypParams, klassenTypParams, true)
    return Typ.Compound.KlassenTyp.BuildInType.Boolean
  }
}

fun main() {
  val typPrüfer = TypPrüfer(File("./beispiele/HalloWelt.gm"))
  typPrüfer.prüfe()
  typPrüfer.logger.print()
}