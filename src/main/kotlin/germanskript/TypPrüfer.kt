package germanskript

import java.io.File
import germanskript.util.SimpleLogger

class TypPrüfer(startDatei: File): PipelineKomponente(startDatei) {
  val typisierer = Typisierer(startDatei)
  val definierer = typisierer.definierer
  var umgebung = Umgebung<Typ>()
  val logger = SimpleLogger()
  val ast: AST.Programm get() = typisierer.ast

  private val globaleVariablen = Bereich<Typ>(null)

  // ganz viele veränderliche Variablen :(
  // Da wir von der abstrakten Klasse 'ProgrammDurchlaufer' erben und dieser die Funktionen vorgibt, brauchen wir sie leider.
  // Alternativ könnte man sich überlegen, sich von dem ProgrammDurchlaufer zu trennen
  // und Funktionsparameter für den Kontext zu verwenden.
  private var zuÜberprüfendeKlasse: Typ.Compound.Klasse? = null
  private lateinit var rückgabeTyp: Typ
  private var funktionsTypParams: List<AST.Definition.TypParam>? = null
  private var klassenTypParams: List<AST.Definition.TypParam>? = null
  private var letzterFunktionsAufruf: AST.Satz.Ausdruck.FunktionsAufruf? = null
  private var rückgabeErreicht = false
  private var rückgabeErreichbar = true
  private var bedingteRückgabeErreicht = false
  private var inBedingterRückgabe = false
  private var evaluiereKonstante = false
  private var erwarteterTyp: Typ? = null
  private var methodenObjekt: Typ.Compound? = null
  private var inSuperBlock = false

  fun prüfe() {
    typisierer.typisiere()
    definierer.konstanten.forEach(::prüfeKonstante)
    // Die Objekt-Klasse ist die Wurzel der Typ-Hierarchie in Germanskript und muss zuerst definiert werden
    prüfeKlasse(BuildIn.Klassen.objekt.definition)
    val klassenDefinitionen = definierer.holeDefinitionen<AST.Definition.Typdefinition.Klasse>()
    // Hier werden nur die Konstruktoren ausgeführt, sodass die privaten Klasseneigenschaften noch hinzugefügt werden können
    klassenDefinitionen.forEach(::prüfeKlasse)
    // Hier werden die Implementierungen der Klassen ausgeführt
    klassenDefinitionen.forEach(::prüfeKlassenImplementierungen)
    // neue germanskript.Umgebung
    durchlaufeAufruf(ast.programmStart!!, ast.programm, Umgebung(), true, BuildIn.Klassen.nichts, true)
    definierer.funktionsDefinitionen.forEach {funktion ->
      prüfeFunktion(funktion, Umgebung())
    }
  }

  private fun ausdruckMussTypSein(
      ausdruck: AST.Satz.Ausdruck,
      erwarteterTyp: Typ
  ): Typ {
    // verwende den erwarteten Typen um für Objektinitialisierung und Lambdas Typargumente zu inferieren
    val prevErwarteterTyp = erwarteterTyp
    this.erwarteterTyp = erwarteterTyp
    val ausdruckTyp = evaluiereAusdruck(ausdruck)
    this.erwarteterTyp = prevErwarteterTyp

    if (!typIstTyp(ausdruckTyp, erwarteterTyp)) {
      throw GermanSkriptFehler.TypFehler.FalscherTyp(
          ausdruck.holeErstesToken(), ausdruckTyp, erwarteterTyp.name
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

  private fun prüfeGenericConstraints(typ: AST.TypKnoten, typParam: AST.Definition.TypParam) {
    typParam.schnittstellen.forEach { typMussTypSein(typ.typ!!, it.typ!!, typ.name.bezeichnerToken) }
    if (typParam.elternKlasse != null) {
      typMussTypSein(typ.typ!!, typParam.elternKlasse.typ!!, typ.name.bezeichnerToken)
    }
  }


  fun typIstTyp(typ: Typ, sollTyp: Typ): Boolean {
    if (typ is Typ.Generic && sollTyp is Typ.Generic) {
      return typ == sollTyp
    }

    // Setze die Typ-Constraints bei Generics ein
    val typ = if (typ is Typ.Generic && typ.kontext == TypParamKontext.Klasse) {
      zuÜberprüfendeKlasse!!.typArgumente[typ.index].typ!!
    } else {
      typ
    }

    fun überprüfeSchnittstelle(klasse: Typ.Compound.Klasse, schnittstelle: Typ.Compound.Schnittstelle): Boolean {
      if (klasse.definition.implementierteSchnittstellen.contains(schnittstelle)) {
        return true
      }
      if (klasse.definition.elternKlasse != null) {
        return überprüfeSchnittstelle(klasse.definition.elternKlasse.klasse.typ!! as Typ.Compound.Klasse, schnittstelle)
      }
      return false
    }

    fun überprüfeKlassenHierarchie(klasse: Typ.Compound.Klasse, elternKlasse: Typ.Compound.Klasse): Boolean {
      var laufTyp: Typ.Compound.Klasse? = klasse
      while (laufTyp != null) {
        if (laufTyp == elternKlasse) {
          return true
        }
        laufTyp = laufTyp.definition.elternKlasse?.klasse?.typ as Typ.Compound.Klasse?
      }
      return false
    }

    if (sollTyp is Typ.Generic) {
      return sollTyp.typParam.schnittstellen.all { typIstTyp(typ, it.typ!!) } &&
          (sollTyp.typParam.elternKlasse == null || typIstTyp(typ, sollTyp.typParam.elternKlasse.typ!!))
    }

    return when (sollTyp) {
      is Typ.Compound.Schnittstelle -> when(typ) {
        is Typ.Generic -> typ.typParam.schnittstellen.find { it.typ!! == sollTyp } != null
        is Typ.Compound.Klasse -> überprüfeSchnittstelle(typ, sollTyp)
        else -> typ == sollTyp
      }
      is Typ.Compound.Klasse -> when(typ) {
        is Typ.Generic -> typ.typParam.elternKlasse?.typ == sollTyp
        is Typ.Compound.Klasse -> sollTyp == BuildIn.Klassen.objekt || überprüfeKlassenHierarchie(typ, sollTyp)
        else -> typ == sollTyp
      }
      else -> typ == sollTyp
    }
  }

  /**
   * Prüft ob ein Typ die Schnittstelle 'iterierbar' implementiert und gibt die Iterierbar-Schnittstelle wenn möglich zurück.
   */
  private fun prüfeImplementiertSchnittstelle(typ: Typ, schnittstelle: AST.Definition.Typdefinition.Schnittstelle): Typ.Compound.Schnittstelle? {
    return when (typ) {
      is Typ.Generic -> typ.typParam.schnittstellen.find {
        (it.typ!! as Typ.Compound.Schnittstelle).definition == schnittstelle }?.typ as Typ.Compound.Schnittstelle?
      is Typ.Compound.Klasse -> typ.definition.implementierteSchnittstellen.find { it.definition == schnittstelle }
      is Typ.Compound.Schnittstelle -> if (typ.definition == schnittstelle) typ else null
    }
  }


  /**
   * inferiert Typargumente basierend auf den erwarteten Typen
   */
  private fun inferiereTypArgumente(typ: Typ.Compound) {
    if (erwarteterTyp is Typ.Compound && typ.definition === (erwarteterTyp as Typ.Compound).definition) {
      if (typ.typArgumente.isEmpty()) {
        typ.typArgumente = (erwarteterTyp as Typ.Compound).typArgumente.map { arg ->
          ersetzeGenerics(arg, letzterFunktionsAufruf?.typArgumente, methodenObjekt?.typArgumente)
        }
      } else {
        typ.typArgumente.forEach { arg ->
          typisierer.bestimmeTyp(arg, funktionsTypParams, klassenTypParams, true, false)
        }
      }
    }
  }

  fun holeParamName(param: AST.Definition.Parameter, typArgumente: List<AST.TypKnoten>): AST.WortArt.Nomen {
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
        TypParamKontext.Klasse -> typArgumente[generic.index].typ!!
        TypParamKontext.Funktion -> letzterFunktionsAufruf!!.typArgumente[generic.index].typ!!
      }
    } else param.typKnoten.typ!!
  }

  private fun vereineTypen(typA: Typ, typB: Typ): Typ {
    return when {
      typIstTyp(typA, typB) -> typB
      typIstTyp(typB, typA) -> typA
      // Wenn die Typen nicht übereinstimmen, dann kommt Objekt raus, Elternklasse von allen Typen
      else -> BuildIn.Klassen.objekt
    }
  }

  private fun vereineAlleTypen(typen: List<Typ>): Typ {
    return typen.reduce() { typA, typB ->
      vereineTypen(typA, typB)
    }
  }


  private fun prüfeKonstante(konstante: AST.Definition.Konstante) {
    evaluiereKonstante = true
    val typ = evaluiereAusdruck(konstante.wert)
    if (typ != BuildIn.Klassen.zahl && typ != BuildIn.Klassen.zeichenfolge && typ != BuildIn.Klassen.boolean) {
      throw GermanSkriptFehler.KonstantenFehler(konstante.wert.holeErstesToken())
    }
    konstante.typ = typ
    evaluiereKonstante = false
  }

  private fun prüfeFunktion(funktion: AST.Definition.Funktion, funktionsUmgebung: Umgebung<Typ>) {
    funktionsUmgebung.pushBereich()
    for (parameter in funktion.signatur.parameter) {
      funktionsUmgebung.schreibeVariable(parameter.name, parameter.typKnoten.typ!!, false)
      funktion.signatur.parameterNamen.add(parameter.name)
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
      typKnoten.typ = Typ.Generic(param, index, TypParamKontext.Klasse)
      typKnoten
    }
  }

  private fun prüfeKlasse(klasse: AST.Definition.Typdefinition.Klasse) {
    if (klasse.geprüft) {
      return
    }
    klasse.geprüft = true

    // Da die Kindklasse abhängig von der Elternklasse ist, muss zuerst die Elternklasse geprüft werden
    if (klasse.elternKlasse != null) {
      val elternKlasse = klasse.elternKlasse.klasse.typ as Typ.Compound.Klasse
      prüfeKlasse(elternKlasse.definition)

      // Überprüfe Elternklassen-Konstruktor
      umgebung.pushBereich()
      for (eigenschaft in klasse.eigenschaften) {
        umgebung.schreibeVariable(eigenschaft.name, eigenschaft.typKnoten.typ!!, false)
      }
      evaluiereObjektInstanziierung(klasse.elternKlasse)
      umgebung.popBereich()
    }

    // überprüfe Konstruktor und Methoden
    zuÜberprüfendeKlasse = Typ.Compound.Klasse(klasse, erstelleGenerischeTypArgumente(klasse.typParameter))
    klassenTypParams = klasse.typParameter
    durchlaufeAufruf(klasse.name.bezeichner.toUntyped(), klasse.konstruktor, Umgebung(), true, BuildIn.Klassen.nichts, false)
    klassenTypParams = null

    zuÜberprüfendeKlasse = null
  }

  private fun prüfeKlassenImplementierungen(klasse: AST.Definition.Typdefinition.Klasse) {
    zuÜberprüfendeKlasse = Typ.Compound.Klasse(klasse, erstelleGenerischeTypArgumente(klasse.typParameter))
    klassenTypParams = klasse.typParameter
    klasse.implementierungen.forEach{implementierung -> prüfeImplementierung(klasse, implementierung)}
    klassenTypParams = null
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
    zuÜberprüfendeKlasse = Typ.Compound.Klasse(klasse, implementierung.klasse.typArgumente)
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

  private fun durchlaufeBereich(bereich: AST.Satz.Bereich, neuerBereich: Boolean): Typ  {
    if (neuerBereich) {
      umgebung.pushBereich()
    }
    var rückgabe: Typ = BuildIn.Klassen.nichts
    for (satz in bereich.sätze) {
      rückgabe = when (satz) {
        is AST.Satz.VariablenDeklaration -> durchlaufeVariablenDeklaration(satz)
        is AST.Satz.IndexZuweisung -> durchlaufeIndexZuweisung(satz)
        is AST.Satz.Bereich -> durchlaufeBereich(satz, true)
        is AST.Satz.Zurückgabe -> durchlaufeZurückgabe(satz)
        is AST.Satz.Ausdruck.Bedingung -> durchlaufeBedingungsSatz(satz, false)
        is AST.Satz.SolangeSchleife -> durchlaufeSolangeSchleife(satz)
        is AST.Satz.FürJedeSchleife -> durchlaufeFürJedeSchleife(satz)
        is AST.Satz.SchleifenKontrolle -> BuildIn.Klassen.nichts
        is AST.Satz.Ausdruck.VersucheFange -> durchlaufeVersucheFange(satz, false)
        is AST.Satz.Intern -> durchlaufeIntern(satz)
        is AST.Satz.Ausdruck -> evaluiereAusdruck(satz)
      }
    }
    if (neuerBereich) {
      umgebung.popBereich()
    }
    return rückgabe
  }

  private fun evaluiereAusdruck(ausdruck: AST.Satz.Ausdruck): Typ {
    return when (ausdruck) {
      is AST.Satz.Ausdruck.Zeichenfolge -> BuildIn.Klassen.zeichenfolge
      is AST.Satz.Ausdruck.Zahl -> BuildIn.Klassen.zahl
      is AST.Satz.Ausdruck.Boolean -> BuildIn.Klassen.boolean
      is AST.Satz.Ausdruck.Variable -> evaluiereVariable(ausdruck)
      is AST.Satz.Ausdruck.Liste -> evaluiereListe(ausdruck)
      is AST.Satz.Ausdruck.IndexZugriff -> evaluiereIndexZugriff(ausdruck)
      is AST.Satz.Ausdruck.FunktionsAufruf -> durchlaufeFunktionsAufruf(ausdruck)
      is AST.Satz.Ausdruck.BinärerAusdruck -> evaluiereBinärenAusdruck(ausdruck)
      is AST.Satz.Ausdruck.Minus -> evaluiereMinus(ausdruck)
      is AST.Satz.Ausdruck.Konvertierung -> evaluiereKonvertierung(ausdruck)
      is AST.Satz.Ausdruck.ObjektInstanziierung -> evaluiereObjektInstanziierung(ausdruck)
      is AST.Satz.Ausdruck.EigenschaftsZugriff -> evaluiereEigenschaftsZugriff(ausdruck)
      is AST.Satz.Ausdruck.SelbstEigenschaftsZugriff -> evaluiereSelbstEigenschaftsZugriff(ausdruck)
      is AST.Satz.Ausdruck.NachrichtenObjektEigenschaftsZugriff -> evaluiereNachrichtenObjektEigenschaftsZugriff(ausdruck)
      is AST.Satz.Ausdruck.SelbstReferenz -> evaluiereSelbstReferenz()
      is AST.Satz.Ausdruck.NachrichtenObjektReferenz -> evaluiereNachrichtenObjektReferenz()
      is AST.Satz.Ausdruck.Lambda -> evaluiereLambda(ausdruck)
      is AST.Satz.Ausdruck.AnonymeKlasse -> evaluiereAnonymeKlasse(ausdruck)
      is AST.Satz.Ausdruck.Konstante -> evaluiereKonstante(ausdruck)
      is AST.Satz.Ausdruck.TypÜberprüfung -> evaluiereTypÜberprüfung(ausdruck)
      is AST.Satz.Ausdruck.NachrichtenBereich -> durchlaufeNachrichtenBereich(ausdruck)
      is AST.Satz.Ausdruck.SuperBereich -> durchlaufeSuperBereich(ausdruck)
      is AST.Satz.Ausdruck.Bedingung -> durchlaufeBedingungsSatz(ausdruck, true)
      is AST.Satz.Ausdruck.Nichts -> BuildIn.Klassen.nichts
      is AST.Satz.Ausdruck.VersucheFange -> durchlaufeVersucheFange(ausdruck, true)
      is AST.Satz.Ausdruck.Werfe -> evaluiereWerfe(ausdruck)
    }
  }

  private fun durchlaufeNachrichtenBereich(nachrichtenBereich: AST.Satz.Ausdruck.NachrichtenBereich): Typ {
    val wert = evaluiereAusdruck(nachrichtenBereich.objekt)
    umgebung.pushBereich(wert)
    return durchlaufeBereich(nachrichtenBereich.bereich, false).also { umgebung.popBereich() }
  }

  private fun durchlaufeSuperBereich(superBereich: AST.Satz.Ausdruck.SuperBereich): Typ {
    val prevInSuperBlock = inSuperBlock
    inSuperBlock = true
    return durchlaufeBereich(superBereich.bereich, true).also {
      inSuperBlock = prevInSuperBlock
    }
  }

  private fun evaluiereVariable(variable: AST.Satz.Ausdruck.Variable): Typ {
    return try {
      if (variable.konstante != null) {
        evaluiereAusdruck(variable.konstante!!.wert!!)
      } else {
        this.evaluiereVariable(variable.name)
      }
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

  private fun evaluiereVariable(name: AST.WortArt.Nomen): Typ {
    return evaluiereVariable(name.nominativ) ?:
      throw GermanSkriptFehler.Undefiniert.Variable(name.bezeichner.toUntyped())
  }

  private fun evaluiereVariable(variable: String): Typ? {
    return umgebung.leseVariable(variable)?.wert ?: globaleVariablen.leseVariable(variable)?.wert
  }

  private fun evaluiereNachrichtenObjektReferenz(): Typ {
    return umgebung.holeNachrichtenBereichObjekt()!!
  }

  private fun durchlaufeAufruf(token: Token, bereich: AST.Satz.Bereich, umgebung: Umgebung<Typ>, neuerBereich: Boolean, rückgabeTyp: Typ, impliziteRückgabe: Boolean): Typ {
    this.rückgabeTyp = rückgabeTyp
    this.umgebung = umgebung
    rückgabeErreichbar = true
    rückgabeErreicht = false
    return durchlaufeBereich(bereich, neuerBereich).also {
      if (!impliziteRückgabe && !rückgabeErreicht && rückgabeTyp != BuildIn.Klassen.nichts) {
        throw GermanSkriptFehler.RückgabeFehler.RückgabeVergessen(token, rückgabeTyp)
      }
    }
  }

  private fun durchlaufeVariablenDeklaration(deklaration: AST.Satz.VariablenDeklaration): Typ {
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
        if (deklaration.istGlobal) {
          globaleVariablen.schreibeVariable(deklaration.name, wert, !deklaration.name.unveränderlich)
        } else {
          umgebung.schreibeVariable(deklaration.name, wert, !deklaration.name.unveränderlich)
        }
      }
      else {
        // hier müssen wir überprüfen ob der Typ der Variable, die überschrieben werden sollen gleich
        // dem neuen germanskript.intern.Wert ist
        val vorherigerTyp = if (deklaration.istGlobal) {
          globaleVariablen.leseVariable(deklaration.name.nominativ)
        } else {
          umgebung.leseVariable(deklaration.name.nominativ)
        }
        val wert = if (vorherigerTyp != null) {
          if (vorherigerTyp.name.unveränderlich) {
            throw GermanSkriptFehler.Variablenfehler(deklaration.name.bezeichner.toUntyped(), vorherigerTyp.name)
          }
          ausdruckMussTypSein(deklaration.wert, vorherigerTyp.wert)
        } else {
          evaluiereAusdruck(deklaration.wert)
        }
        if (deklaration.istGlobal) {
          globaleVariablen.schreibeVariable(deklaration.name, wert, true)
        } else {
          umgebung.überschreibeVariable(deklaration.name, wert)
        }
      }
    }
    return BuildIn.Klassen.nichts
  }

  private fun durchlaufeIndexZuweisung(zuweisung: AST.Satz.IndexZuweisung): Typ {
    val indizierbar = findeIterierbares(zuweisung.singular).let { (typ, numerus) ->
      zuweisung.numerus = numerus
      typ
    } as Typ.Compound
    val indizierbarSchnittstelle = prüfeImplementiertSchnittstelle(indizierbar, BuildIn.Schnittstellen.indizierbar)
        ?: throw GermanSkriptFehler.TypFehler.IndizierbarErwartet(zuweisung.singular.bezeichnerToken, indizierbar)

    ausdruckMussTypSein(zuweisung.index,
       ersetzeGenerics(indizierbarSchnittstelle.typArgumente[0], null, indizierbar.typArgumente).typ!!)
    ausdruckMussTypSein(zuweisung.wert,
        ersetzeGenerics(indizierbarSchnittstelle.typArgumente[1], null, indizierbar.typArgumente).typ!!)
    return BuildIn.Klassen.nichts
  }

  /**
   * Setzt Typargumente in generische Typen ein
   */
  private fun ersetzeGenerics(
      typKnoten: AST.TypKnoten,
      funktionsTypArgumente: List<AST.TypKnoten>?,
      klassenTypArgumente: List<AST.TypKnoten>?): AST.TypKnoten {
    return when (val typ = typKnoten.typ!!) {
      is Typ.Generic -> when (typ.kontext) {
        TypParamKontext.Funktion -> {
          // vor dem Einsetzen des Typarguments muss überprüft werden, ob der eingesetze Typ
          // den Generic-Constraints entspricht
          prüfeGenericConstraints(funktionsTypArgumente!![typ.index], typ.typParam)
          funktionsTypArgumente[typ.index]
        }
        TypParamKontext.Klasse -> {
          // vor dem Einsetzen des Typarguments muss überprüft werden, ob der eingesetze Typ
          // den Generic-Constraints entspricht
          prüfeGenericConstraints(klassenTypArgumente!![typ.index], typ.typParam)
          klassenTypArgumente[typ.index]
        }
      }
      is Typ.Compound -> {
        typKnoten.copy(typArgumente = typ.typArgumente.map {
          ersetzeGenerics(it, funktionsTypArgumente, klassenTypArgumente)
        })
      }
      else -> typKnoten
    }
  }

  /**
   * Inferiert die Typparameter eines Typknotens basierend auf den eingesetzten Wert.
   *
   * @param inferierteGenerics die Generics, die bisher inferiert wurden sind.
   * @param toInfer der Typknoten, der inferiert werden soll
   * @param fehlerToken ein Token um ein GermanSkript-Fehler zu werfen, falls die Inferierung nicht klappt
   * @param fromType der Typ aus dem inferiert werden soll
   * @param inferKontext der Generic-Kontext gibt an welche Generics inferiert werden sollen
   * @param otherKontexTypArgs die Typargumente des anderen Kontexts die nicht inferiert werden
   *
   * @return Der inferierte Typknoten
   */
  fun inferiereGenerics(
      inferierteGenerics: MutableList<AST.TypKnoten?>,
      toInfer: AST.TypKnoten,
      fehlerToken: Token,
      fromType: Typ,
      inferKontext: TypParamKontext,
      otherKontexTypArgs: List<AST.TypKnoten>?
  ): AST.TypKnoten {
    return when (val typ = toInfer.typ!!) {
      is Typ.Generic ->
        if (typ.kontext == inferKontext) {
          inferierteGenerics[typ.index] = if (inferierteGenerics[typ.index] == null) {
            fromType.inTypKnoten()
          } else {
            vereineTypen(inferierteGenerics[typ.index]!!.typ!!, fromType).inTypKnoten()
          }
          inferierteGenerics[typ.index]!!
        } else {
          otherKontexTypArgs!![typ.index]
        }
      is Typ.Compound -> {
        if (fromType !is Typ.Compound || fromType.typArgumente.size != typ.typArgumente.size) {
          throw GermanSkriptFehler.TypFehler.TypenUnvereinbar(fehlerToken, typ, fromType)
        }
        toInfer.copy(typArgumente = typ.typArgumente.mapIndexed { index, arg ->
          inferiereGenerics(inferierteGenerics, arg, fehlerToken, fromType.typArgumente[index].typ!!, inferKontext, otherKontexTypArgs)
        })
      }
      else -> toInfer
    }
  }

  private fun durchlaufeFunktionsAufruf(funktionsAufruf: AST.Satz.Ausdruck.FunktionsAufruf): Typ {
    if (funktionsAufruf.vollerName == null) {
      funktionsAufruf.vollerName = definierer.holeVollenNamenVonFunktionsAufruf(funktionsAufruf, null)
    }
    var funktionsSignatur: AST.Definition.FunktionsSignatur? = null
    val nachrichtenBereichObjekt = umgebung.holeNachrichtenBereichObjekt()

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
        nachrichtenBereichObjekt != null &&
        funktionsAufruf.reflexivPronomen?.typ !is TokenTyp.REFLEXIV_PRONOMEN.ERSTE_FORM
    ) {
      val fund = findeMethode(
          funktionsAufruf,
          nachrichtenBereichObjekt,
          FunktionsAufrufTyp.METHODEN_NACHRICHTEN_OBJEKT_AUFRUF
      )
      funktionsSignatur = fund?.first
      methodenObjekt = fund?.second
    }

    // ist Methoden-Selbst-Aufruf
    if (funktionsSignatur == null && zuÜberprüfendeKlasse != null
        && funktionsAufruf.reflexivPronomen?.typ !is TokenTyp.REFLEXIV_PRONOMEN.ZWEITE_FORM) {

      // Wenn man in einem Super-Block ist, wird die Eltern-Methode aufgerufen
      funktionsSignatur = if (inSuperBlock && zuÜberprüfendeKlasse!!.definition.elternKlasse != null) {
        val elternKlasse = zuÜberprüfendeKlasse!!.definition.elternKlasse!!.klasse.typ!! as Typ.Compound.Klasse
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
        val funktionsDefinition = definierer.holeFunktionsDefinition(funktionsAufruf)

        funktionsSignatur = überprüfePassendeFunktionsSignatur(
            funktionsAufruf,
            funktionsDefinition.signatur,
            FunktionsAufrufTyp.FUNKTIONS_AUFRUF, null
        )
        if (funktionsSignatur != null) {
          funktionsAufruf.funktionsDefinition = funktionsDefinition
        }

        null
      } else {
        null
      }
    } catch (fehler: GermanSkriptFehler.Undefiniert.Funktion) {
      fehler
    }

    // ist Methoden-Reflexiv-Aufruf als letzte Möglichkeit
    // das bedeutet Funktionsnamen gehen vor Methoden-Objekt-Aufrufen
    if (funktionsSignatur == null && funktionsAufruf.objekt != null) {
      val objektTyp = try {
        typisierer.bestimmeTyp(
            funktionsAufruf.objekt.name, funktionsTypParams, klassenTypParams, erlaubeLeereTypArgumente = true
        )
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

        // evaluiere den Objekttypen
        val objektTyp = evaluiereAusdruck(funktionsAufruf.objekt.ausdruck)
        if (objektTyp is Typ.Compound) {
          methodenObjekt = objektTyp
        }
      }
    }

    if (funktionsSignatur == null) {
      throw undefiniertFehler!!
    }

    // hier kommt noch ein nachträglicher Grammatik-Check ob der Numerus des Reflexivpronomens
    // auch mit dem Objekt übereinstimmt
    if (methodenObjekt != null && funktionsAufruf.reflexivPronomen != null) {
      val reflexivPronomen = funktionsAufruf.reflexivPronomen.typ
      if (methodenObjekt!!.definition === BuildIn.Klassen.liste && funktionsAufruf.reflexivPronomen.typ.numerus == Numerus.SINGULAR) {
        val erwartetesPronomen = holeReflexivPronomenForm(true, reflexivPronomen.kasus.first(), Numerus.PLURAL)
        throw GermanSkriptFehler.GrammatikFehler.FalschesReflexivPronomen(
            funktionsAufruf.reflexivPronomen.toUntyped(), reflexivPronomen, erwartetesPronomen)
      } else if (methodenObjekt!!.definition !== BuildIn.Klassen.liste && funktionsAufruf.reflexivPronomen.typ.numerus == Numerus.PLURAL) {
        val erwartetesPronomen = holeReflexivPronomenForm(true, reflexivPronomen.kasus.first(), Numerus.SINGULAR)
        throw GermanSkriptFehler.GrammatikFehler.FalschesReflexivPronomen(
            funktionsAufruf.reflexivPronomen.toUntyped(), reflexivPronomen, erwartetesPronomen)
      }
    }

    // Überprüfe ob die Typargumente des Methodenobjekts mit den Generic-Constraints übereinstimmen
    if (methodenObjekt is Typ.Compound.Klasse && methodenObjekt!!.typArgumente.isNotEmpty()) {
      val implementierung = funktionsSignatur.findNodeInParents<AST.Definition.Implementierung>()
      // anonyme Klassen und Lambdas haben kein Implementierungsblock und dort muss nicht überprüft werden
      if (implementierung != null) {
        val klasse = try{
          ersetzeGenerics(implementierung.klasse, funktionsAufruf.typArgumente, methodenObjekt!!.typArgumente)
        } catch (falscherTyp: GermanSkriptFehler.TypFehler.FalscherTyp) {
          throw GermanSkriptFehler.Undefiniert.Methode(funktionsAufruf.token, funktionsAufruf, methodenObjekt!!.name)
        }
        for (paramIndex in implementierung.klasse.typArgumente.indices) {
          if (!typIstTyp(methodenObjekt!!.typArgumente[paramIndex].typ!!, klasse.typArgumente[paramIndex].typ!!)) {
            throw GermanSkriptFehler.Undefiniert.Methode(funktionsAufruf.token, funktionsAufruf, methodenObjekt!!.name)
          }
        }
      }
    }

    val typKontext = methodenObjekt?.typArgumente

    val parameter = funktionsSignatur.parameter.toList()
    val argumente = funktionsAufruf.argumente.toList()

    if (argumente.size != parameter.size) {
      throw GermanSkriptFehler.SyntaxFehler.AnzahlDerParameterFehler(funktionsSignatur.name.toUntyped())
    }

    val genericsMüssenInferiertWerden = funktionsSignatur.typParameter.isNotEmpty()
        && funktionsAufruf.typArgumente.isEmpty()
    val inferierteGenerics: MutableList<AST.TypKnoten?> = MutableList(funktionsSignatur.typParameter.size) {null}

    if (!genericsMüssenInferiertWerden) {
      if (funktionsAufruf.typArgumente.size != funktionsSignatur.typParameter.size) {
        throw GermanSkriptFehler.TypFehler.TypArgumentFehler(
            funktionsAufruf.token,
            funktionsAufruf.typArgumente.size,
            funktionsSignatur.typParameter.size
        )
      }

      funktionsAufruf.typArgumente.forEach { arg ->
        typisierer.bestimmeTyp(arg, funktionsTypParams, klassenTypParams,
            istAliasErlaubt = true, erlaubeLeereTypArgumente = false)
      }
    }

    // stimmen die Argument Typen mit den Parameter Typen überein?
    letzterFunktionsAufruf = funktionsAufruf
    for (i in parameter.indices) {
      val argAusdruck = argumente[i].ausdruck
      if (genericsMüssenInferiertWerden) {
        val ausdruckTyp = evaluiereAusdruck(argAusdruck)
        val ausdruckToken = argAusdruck.holeErstesToken()
        val paramTyp = inferiereGenerics(
            inferierteGenerics,
            parameter[i].typKnoten,
            ausdruckToken,
            ausdruckTyp,
            TypParamKontext.Funktion,
            typKontext
        )
        typMussTypSein(ausdruckTyp, paramTyp.typ!!, ausdruckToken)
      } else {
        ausdruckMussTypSein(argAusdruck, ersetzeGenerics(parameter[i].typKnoten, funktionsAufruf.typArgumente, typKontext).typ!!)
      }
    }
    if (genericsMüssenInferiertWerden) {
      funktionsAufruf.typArgumente = inferierteGenerics.map {
        it ?: throw GermanSkriptFehler.TypFehler.TypArgumentFehler(
            funktionsAufruf.token,
            funktionsAufruf.typArgumente.size,
            funktionsSignatur.typParameter.size
        )
      }
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
      if (typ is Typ.Compound.Klasse && aufrufTyp == FunktionsAufrufTyp.METHODEN_SELBST_AUFRUF) {
        funktionsAufruf.funktionsDefinition = typ.definition.methoden[funktionsAufruf.vollerName!!]
        funktionsAufruf.objektTyp = typ
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
    val vollerName = if (ersetzeObjekt != null) {
      definierer.holeVollenNamenVonFunktionsAufruf(funktionsAufruf, ersetzeObjekt)
    } else {
      funktionsAufruf.vollerName!!
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
    typ.definition.findeMethode(vollerName)?.also { signatur ->
      überprüfePassendeFunktionsSignatur(funktionsAufruf, signatur, aufrufTyp, typ)?.also {
        funktionsAufruf.vollerName = vollerName
        return Pair(it, typ)
      }
    }

    if (typ.definition.typParameter.isNotEmpty()) {
      // Die Funktionstypparameter werden nicht ersetzt, da sie möglicherweise erst inferiert werden müssen.
      val ersetzeTypArgsName = definierer.holeVollenNamenVonFunktionsAufruf(
          funktionsAufruf, typ.definition.typParameter, typ.typArgumente, ersetzeObjekt)

      typ.definition.findeMethode(ersetzeTypArgsName)?.also { signatur ->
        // TODO: Die Typparameternamen müssen für den Interpreter später hier ersetzt werden
        überprüfePassendeFunktionsSignatur(funktionsAufruf, signatur, aufrufTyp, typ)?.also {
          funktionsAufruf.vollerName = ersetzeTypArgsName
          return Pair(it, typ)
        }
      }
    }

    if (funktionsAufruf.reflexivPronomen != null) {
      throw GermanSkriptFehler.Undefiniert.Methode(funktionsAufruf.verb.toUntyped(),
          funktionsAufruf,
          typ.definition.name.nominativ)
    }
    return null
  }

  private fun durchlaufeZurückgabe(zurückgabe: AST.Satz.Zurückgabe): Typ {
    methodenObjekt = zuÜberprüfendeKlasse
    ausdruckMussTypSein(zurückgabe.ausdruck, rückgabeTyp)
    methodenObjekt = null
    // Die Rückgabe ist nur auf alle Fälle erreichbar, wenn sie in keiner Bedingung und in keiner Schleife ist
    if (rückgabeErreichbar) {
      if (inBedingterRückgabe) bedingteRückgabeErreicht = true
      else rückgabeErreicht = true
    }
    return rückgabeTyp
  }

  private fun prüfeBedingung(bedingung: AST.Satz.BedingungsTerm): Typ {
    ausdruckMussTypSein(bedingung.bedingung, BuildIn.Klassen.boolean)
    return durchlaufeBereich(bedingung.bereich, true)
  }

  private fun durchlaufeBedingungsSatz(bedingungsSatz: AST.Satz.Ausdruck.Bedingung, istAusdruck: Boolean): Typ {
    if (istAusdruck && bedingungsSatz.sonst == null) {
      throw GermanSkriptFehler.WennAusdruckBrauchtSonst(bedingungsSatz.bedingungen[0].token)
    }

    val prevRückgabeErreichbar = rückgabeErreichbar
    val prevInBedingterRückgabe = inBedingterRückgabe
    inBedingterRückgabe = true

    val typen = mutableListOf<Typ>()

    for (bedingung in bedingungsSatz.bedingungen) {
      bedingteRückgabeErreicht = false
      typen.add(prüfeBedingung(bedingung))
      if (!bedingteRückgabeErreicht) {
        rückgabeErreichbar = false
      }
    }

    bedingteRückgabeErreicht = false

    if (bedingungsSatz.sonst != null) {
      bedingteRückgabeErreicht = false
      typen.add(durchlaufeBereich(bedingungsSatz.sonst!!.bereich, true))
      if (!prevInBedingterRückgabe && bedingteRückgabeErreicht)
        rückgabeErreicht = true
    }
    inBedingterRückgabe = prevInBedingterRückgabe
    rückgabeErreichbar = prevRückgabeErreichbar
    return vereineAlleTypen(typen)
  }

  private fun durchlaufeSolangeSchleife(schleife: AST.Satz.SolangeSchleife): Typ {
    val prevRückgabeErreichbar = rückgabeErreichbar
    rückgabeErreichbar = false
    prüfeBedingung(schleife.bedingung)
    rückgabeErreichbar = prevRückgabeErreichbar
    return BuildIn.Klassen.nichts
  }

  private fun durchlaufeFürJedeSchleife(schleife: AST.Satz.FürJedeSchleife): Typ {
    val elementTyp = when {
      schleife.iterierbares != null -> {
        val iterierbarerTyp = evaluiereAusdruck(schleife.iterierbares)
        val iterierbareSchnittstelle = prüfeImplementiertSchnittstelle(iterierbarerTyp, BuildIn.Schnittstellen.iterierbar)
            ?: throw GermanSkriptFehler.TypFehler.IterierbarErwartet(schleife.iterierbares.holeErstesToken(), iterierbarerTyp)
        val typArgumente = if (iterierbarerTyp is Typ.Compound) iterierbarerTyp.typArgumente else null
        ersetzeGenerics(iterierbareSchnittstelle.typArgumente[0], null, typArgumente).typ!!
      }
      schleife.reichweite != null -> {
        val (anfang, ende) = schleife.reichweite
        ausdruckMussTypSein(anfang, BuildIn.Klassen.zahl)
        ausdruckMussTypSein(ende, BuildIn.Klassen.zahl)
        BuildIn.Klassen.zahl
      }
      else -> {
        evaluiereListenSingular(schleife.singular)
      }
    }

    umgebung.pushBereich()
    umgebung.schreibeVariable(schleife.binder, elementTyp, false)
    val prevRückgabeErreichbar = rückgabeErreichbar
    rückgabeErreichbar = false
    durchlaufeBereich(schleife.bereich, true)
    rückgabeErreichbar = prevRückgabeErreichbar
    umgebung.popBereich()
    return BuildIn.Klassen.nichts
  }

  private fun durchlaufeVersucheFange(versucheFange: AST.Satz.Ausdruck.VersucheFange, istAusdruck: Boolean): Typ {
    if (istAusdruck && versucheFange.fange.isEmpty() && versucheFange.schlussendlich == null) {
      throw GermanSkriptFehler.VersucheFangeAusdruckBrauchtFange(versucheFange.versuche.toUntyped())
    }
    val prevRückgabeErreichbar = rückgabeErreichbar
    val prevInBedingterRückgabe = inBedingterRückgabe
    inBedingterRückgabe = true
    val typen = mutableListOf<Typ>()

    bedingteRückgabeErreicht = false
    typen.add(durchlaufeBereich(versucheFange.bereich, true))
    if (!bedingteRückgabeErreicht) {
      rückgabeErreichbar = false
    }

    for (fange in versucheFange.fange) {
      umgebung.pushBereich()
      // TODO: hole aus dem Kontext die Typparameter
      val typ = typisierer.bestimmeTyp(
          fange.param.typKnoten, funktionsTypParams, klassenTypParams, istAliasErlaubt = true, erlaubeLeereTypArgumente = false)!!

      bedingteRückgabeErreicht = false
      umgebung.schreibeVariable(fange.param.name, typ, true)
      typen.add(durchlaufeBereich(fange.bereich, true))
      umgebung.popBereich()
      if (!bedingteRückgabeErreicht) {
        rückgabeErreichbar = false
      }
    }

    if (!prevInBedingterRückgabe && bedingteRückgabeErreicht) {
      rückgabeErreicht = true
    }

    if (versucheFange.schlussendlich != null) {
      bedingteRückgabeErreicht = false
      rückgabeErreichbar = prevRückgabeErreichbar
      durchlaufeBereich(versucheFange.schlussendlich, true)
      if (!prevInBedingterRückgabe && bedingteRückgabeErreicht) {
        rückgabeErreicht = true
      }
    }

    rückgabeErreichbar = prevRückgabeErreichbar
    inBedingterRückgabe = prevInBedingterRückgabe

    return vereineAlleTypen(typen)
  }

  private fun evaluiereWerfe(werfe: AST.Satz.Ausdruck.Werfe): Typ {
    evaluiereAusdruck(werfe.ausdruck)
    return BuildIn.Klassen.niemals
  }

  private fun durchlaufeIntern(intern: AST.Satz.Intern): Typ {
    // Hier muss nicht viel gemacht werden
    // die internen Sachen sind schon von kotlin Typ-gecheckt :)
    rückgabeErreicht = true
    return rückgabeTyp
  }

  private fun evaluiereKonstante(konstante: AST.Satz.Ausdruck.Konstante): Typ {
    val konstanteDefinition = definierer.holeKonstante(konstante)
    // tragen den Wert der Konstanten ein
    konstante.wert = konstanteDefinition.wert
    return evaluiereAusdruck(konstante.wert!!)
  }

  private fun evaluiereListe(ausdruck: AST.Satz.Ausdruck.Liste): Typ {
    val listenTyp = typisierer.bestimmeTyp(
        ausdruck.pluralTyp,
        funktionsTypParams,
        klassenTypParams,
        istAliasErlaubt = true,
        erlaubeLeereTypArgumente = false
    ) as Typ.Compound.Klasse
    ausdruck.elemente.forEach {element -> ausdruckMussTypSein(element, listenTyp.typArgumente[0].typ!!)}
    return listenTyp
  }

  private fun evaluiereIndexZugriff(indexZugriff: AST.Satz.Ausdruck.IndexZugriff): Typ {
    val indizierTyp = findeIterierbares(indexZugriff.singular).let { (typ, numerus) ->
      indexZugriff.numerus = numerus
      typ
    } as Typ.Compound

    val schnittstelle = prüfeImplementiertSchnittstelle(indizierTyp, BuildIn.Schnittstellen.indiziert)
        ?: throw GermanSkriptFehler.TypFehler.IndizierbarErwartet(indexZugriff.singular.bezeichnerToken, indizierTyp)

    ausdruckMussTypSein(indexZugriff.index,
        ersetzeGenerics(schnittstelle.typArgumente[0], null, indizierTyp.typArgumente).typ!!)
    return ersetzeGenerics(schnittstelle.typArgumente[1], null, indizierTyp.typArgumente).typ!!
  }

  private fun findeIterierbares(singular: AST.WortArt.Nomen): Pair<Typ, Numerus> {
    val plural = singular.ganzesWort(Kasus.NOMINATIV, Numerus.PLURAL, true)

    fun holeEigenschaftAusKlasseSingularOderPlural(klasse: Typ.Compound.Klasse): Pair<Typ, Numerus> {
      return try {
        holeNormaleEigenschaftAusKlasse(singular, klasse, Numerus.SINGULAR).typKnoten.typ!! to Numerus.SINGULAR
      } catch (fehler: GermanSkriptFehler.Undefiniert.Eigenschaft) {
        holeNormaleEigenschaftAusKlasse(singular, klasse, Numerus.PLURAL).typKnoten.typ!! to Numerus.PLURAL
      }
    }

    return when(singular.vornomen?.typ) {
      TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT, TokenTyp.VORNOMEN.JEDE, null ->
        evaluiereVariable(plural)?.let{ it to Numerus.PLURAL } ?: evaluiereVariable(singular) to Numerus.SINGULAR
      TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN -> holeEigenschaftAusKlasseSingularOderPlural(zuÜberprüfendeKlasse!!)
      TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.DEIN -> holeEigenschaftAusKlasseSingularOderPlural(
          umgebung.holeNachrichtenBereichObjekt()!! as Typ.Compound.Klasse)
      else -> throw Exception("Dieser Fall sollte nie eintreten.")
    }
  }

  private fun evaluiereListenSingular(singular: AST.WortArt.Nomen): Typ {
    val plural = singular.ganzesWort(Kasus.NOMINATIV, Numerus.PLURAL, true)
    val liste = when (singular.vornomen?.typ) {
      TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT, TokenTyp.VORNOMEN.JEDE, null -> evaluiereVariable(plural)?:
      throw GermanSkriptFehler.Undefiniert.Variable(singular.bezeichner.toUntyped(), plural)
      TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN ->
        holeNormaleEigenschaftAusKlasse(singular, zuÜberprüfendeKlasse!!, Numerus.PLURAL).typKnoten.typ!!
      TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.DEIN -> holeNormaleEigenschaftAusKlasse(
          singular, umgebung.holeNachrichtenBereichObjekt()!! as Typ.Compound.Klasse, Numerus.PLURAL).typKnoten.typ!!
      else -> throw Exception("Dieser Fall sollte nie eintreten.")
    }
    return (liste as Typ.Compound.Klasse).typArgumente[0].typ!!
  }

  private fun evaluiereObjektInstanziierung(instanziierung: AST.Satz.Ausdruck.ObjektInstanziierung): Typ {
    val klasse = typisierer.bestimmeTyp(
        instanziierung.klasse,
        funktionsTypParams,
        klassenTypParams,
        istAliasErlaubt = true,
        erlaubeLeereTypArgumente = true
    )!!

    if (klasse !is Typ.Compound.Klasse) {
      TODO("Werfe Fehler")
    }
    inferiereTypArgumente(klasse)

    val definition = klasse.definition

    val genericsMüssenInferiertWerden = definition.typParameter.isNotEmpty()
        && klasse.typArgumente.isEmpty()

    if (!genericsMüssenInferiertWerden && definition.typParameter.size != klasse.typArgumente.size) {
      throw GermanSkriptFehler.TypFehler.TypArgumentFehler(
          instanziierung.klasse.name.bezeichnerToken,
          klasse.typArgumente.size,
          definition.typParameter.size
      )
    }

    val inferierteGenerics: MutableList<AST.TypKnoten?> = MutableList(definition.typParameter.size) {null}

    // die Eigenschaftszuweisungen müssen mit der Instanzzierung übereinstimmen, Außerdem müssen die Namen übereinstimmen
    var j = 0
    for (i in definition.eigenschaften.indices) {
      val eigenschaft = definition.eigenschaften[i]
      // Wenn es es sich um eine generische Eigenschaft handelt, verwende den Namen des Typarguments
      val eigenschaftsName = try {
        holeParamName(eigenschaft, instanziierung.klasse.typArgumente)
      } catch (e: IndexOutOfBoundsException) {
        throw GermanSkriptFehler.TypFehler.TypArgumentFehler(
            instanziierung.klasse.name.bezeichnerToken,
            klasse.typArgumente.size,
            definition.typParameter.size
        )
      }
      instanziierung.eigenschaftsNamen.add(eigenschaftsName)

      if (eigenschaft.istZusätzlicheEigenschaft) {
        j++
        continue
      }

      val zuweisung = instanziierung.eigenschaftsZuweisungen.getOrNull(i-j)

      // Es wird überprüft, ob das Hauptwort gleich ist, Adjektive können weggelassen werden
      val eigenschaftsVariable = if (zuweisung == null ||
          eigenschaftsName.hauptWort(Kasus.NOMINATIV, eigenschaftsName.numerus) !=
          zuweisung.name.hauptWort(Kasus.NOMINATIV, eigenschaftsName.numerus)) {
        // Gucke ob die Eigenschaft sich in der Umgebung befindet, dann wird die Elterneigenschaft mit dieser überschrieben
        umgebung.leseVariable(eigenschaftsName.nominativ).also {
          instanziierung.eigenschaftsZuweisungen.add(
              i-j, AST.Argument(null, eigenschaftsName, AST.Satz.Ausdruck.Variable(eigenschaftsName)))
        } ?:
        if (zuweisung != null) {
          throw GermanSkriptFehler.EigenschaftsFehler.UnerwarteterEigenschaftsName(
              zuweisung.name.bezeichner.toUntyped(),
              eigenschaft.name.hauptWort(Kasus.DATIV, eigenschaft.name.numerus))
        } else {
          throw GermanSkriptFehler.EigenschaftsFehler.EigenschaftVergessen(instanziierung.klasse.name.bezeichnerToken, eigenschaft.name.hauptWort)
        }
      } else {
        null
      }

      if (genericsMüssenInferiertWerden) {
        val ausdruckTyp = eigenschaftsVariable?.wert ?: evaluiereAusdruck(zuweisung!!.ausdruck)
        val ausdruckToken = eigenschaftsVariable?.name?.bezeichnerToken ?: zuweisung!!.ausdruck.holeErstesToken()
        val paramTyp = inferiereGenerics(
            inferierteGenerics,
            eigenschaft.typKnoten,
            ausdruckToken,
            ausdruckTyp,
            TypParamKontext.Klasse,
            null
        )
        typMussTypSein(ausdruckTyp, paramTyp.typ!!, ausdruckToken)
      } else {
        val erwarteterTyp =  ersetzeGenerics(eigenschaft.typKnoten, null, instanziierung.klasse.typArgumente).typ!!
        if (eigenschaftsVariable != null) {
          typMussTypSein(eigenschaftsVariable.wert, erwarteterTyp, eigenschaftsVariable.name.bezeichnerToken)
        } else {
          ausdruckMussTypSein(zuweisung!!.ausdruck, erwarteterTyp)
        }
      }
    }

    if (genericsMüssenInferiertWerden) {
      klasse.typArgumente = inferierteGenerics.map {
        it ?: throw GermanSkriptFehler.TypFehler.TypArgumentFehler(
            instanziierung.klasse.name.bezeichnerToken,
            klasse.typArgumente.size,
            definition.typParameter.size
        )
      }
    }

    if (instanziierung.eigenschaftsZuweisungen.size > definition.eigenschaften.size-j) {
      throw GermanSkriptFehler.EigenschaftsFehler.UnerwarteteEigenschaft(
          instanziierung.eigenschaftsZuweisungen[definition.eigenschaften.size].name.bezeichner.toUntyped())
    }

    // prüfe die Type-Costraints
    for (index in definition.typParameter.indices) {
      prüfeGenericConstraints(klasse.typArgumente[index], definition.typParameter[index])
    }

    return klasse
  }

  private fun evaluiereEigenschaftsZugriff(eigenschaftsZugriff: AST.Satz.Ausdruck.EigenschaftsZugriff): Typ {
    val klasse = evaluiereAusdruck(eigenschaftsZugriff.objekt)
    return if (klasse is Typ.Compound.Klasse) {
      holeEigenschaftAusKlasse(eigenschaftsZugriff, klasse)
    }
    else {
      TODO("Klasse erwartet")
    }
  }

  private fun evaluiereSelbstEigenschaftsZugriff(eigenschaftsZugriff: AST.Satz.Ausdruck.SelbstEigenschaftsZugriff): Typ {
    return holeEigenschaftAusKlasse(eigenschaftsZugriff, zuÜberprüfendeKlasse!!)
  }


  private fun evaluiereNachrichtenObjektEigenschaftsZugriff(eigenschaftsZugriff: AST.Satz.Ausdruck.NachrichtenObjektEigenschaftsZugriff): Typ {
    val nachrichtenBereichObjekt = umgebung.holeNachrichtenBereichObjekt() as Typ.Compound.Klasse
    return holeEigenschaftAusKlasse(eigenschaftsZugriff, nachrichtenBereichObjekt)
  }

  private fun holeNormaleEigenschaftAusKlasse(
      eigenschaftsName: AST.WortArt.Nomen,
      klasse: Typ.Compound.Klasse,
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
            klasse.definition.elternKlasse.klasse.typ!! as Typ.Compound.Klasse, numerus)
      }
      catch (fehler: GermanSkriptFehler.Undefiniert) {
        // mache nichts hier
      }
    }
    throw GermanSkriptFehler.Undefiniert.Eigenschaft(eigenschaftsName.bezeichner.toUntyped(), eigName, klasse.definition.name.nominativ)
  }

  private fun holeEigenschaftAusKlasse(eigenschaftsZugriff: AST.Satz.Ausdruck.IEigenschaftsZugriff, klasse: Typ.Compound.Klasse): Typ {
    val eigenschaftsName = eigenschaftsZugriff.eigenschaftsName
    val klassenDefinition = klasse.definition
    return try {
      ersetzeGenerics(holeNormaleEigenschaftAusKlasse(eigenschaftsName, klasse).typKnoten, null, klasse.typArgumente).typ!!
    } catch (fehler: GermanSkriptFehler.Undefiniert.Eigenschaft) {
      if (!klassenDefinition.berechneteEigenschaften.containsKey(eigenschaftsName.nominativ)) {
        if (klasse.definition.elternKlasse != null) {
          try {
            return holeEigenschaftAusKlasse(eigenschaftsZugriff,
                klasse.definition.elternKlasse.klasse.typ!! as Typ.Compound.Klasse
            )
          } catch (_: GermanSkriptFehler.Undefiniert.Eigenschaft) {
            throw fehler
          }
        }
        throw fehler
      } else {
        eigenschaftsZugriff.aufrufName = "${eigenschaftsName.nominativ} von ${klassenDefinition.namensToken.wert}"
        ersetzeGenerics(klassenDefinition.berechneteEigenschaften.getValue(eigenschaftsName.nominativ).rückgabeTyp, null, klasse.typArgumente).typ!!
      }
    }
  }

  private fun evaluiereBinärenAusdruck(ausdruck: AST.Satz.Ausdruck.BinärerAusdruck): Typ {
    val operator = ausdruck.operator.typ.operator

    // die Boolschen Operatoren sind nur für Booleans vorhanden
    if (operator.klasse == OperatorKlasse.LOGISCH) {
      ausdruckMussTypSein(ausdruck.links, BuildIn.Klassen.boolean)
      ausdruckMussTypSein(ausdruck.rechts, BuildIn.Klassen.boolean)
      return BuildIn.Klassen.boolean
    }

    if (operator == Operator.GLEICH || operator == Operator.UNGLEICH) {
      evaluiereAusdruck(ausdruck.links)
      evaluiereAusdruck(ausdruck.rechts)
      return BuildIn.Klassen.boolean
    }

    val linkerTyp = evaluiereAusdruck(ausdruck.links)

    val zuImplementierendeSchnittstelle = when(operator) {
      Operator.GRÖßER,
      Operator.KLEINER,
      Operator.GRÖSSER_GLEICH,
      Operator.KLEINER_GLEICH -> {
        BuildIn.Schnittstellen.vergleichbar
      }
      Operator.PLUS -> BuildIn.Schnittstellen.addierbar
      Operator.MINUS -> BuildIn.Schnittstellen.subtrahierbar
      Operator.MAL -> BuildIn.Schnittstellen.multiplizierbar
      Operator.GETEILT -> BuildIn.Schnittstellen.dividierbar
      Operator.MODULO -> BuildIn.Schnittstellen.modulobar
      Operator.HOCH -> BuildIn.Schnittstellen.potenzierbar
      else -> throw Exception("Dieser Fall sollte nie auftreten.")
    }

    val schnittstelle = prüfeImplementiertSchnittstelle(linkerTyp, zuImplementierendeSchnittstelle)
        ?: throw GermanSkriptFehler.TypFehler.SchnittstelleFürOperatorErwartet(
            ausdruck.links.holeErstesToken(), linkerTyp, zuImplementierendeSchnittstelle, operator)

    val rechterTyp = if (linkerTyp is Typ.Compound) {
      ersetzeGenerics(schnittstelle.typArgumente[0], letzterFunktionsAufruf?.typArgumente, linkerTyp.typArgumente).typ!!
    } else {
      schnittstelle.typArgumente[0].typ!!
    }

    ausdruckMussTypSein(ausdruck.rechts, rechterTyp)

    return if (operator.klasse == OperatorKlasse.ARITHMETISCH) {
      schnittstelle.typArgumente[1].typ!!
    }  else {
      BuildIn.Klassen.boolean
    }
  }

  private fun evaluiereMinus(minus: AST.Satz.Ausdruck.Minus): Typ {
    val ausdruck = evaluiereAusdruck(minus.ausdruck)
    val schnittstelle = prüfeImplementiertSchnittstelle(ausdruck, BuildIn.Schnittstellen.negierbar)
        ?: throw GermanSkriptFehler.TypFehler.SchnittstelleFürOperatorErwartet(
            minus.ausdruck.holeErstesToken(), ausdruck, BuildIn.Schnittstellen.negierbar, Operator.NEGATION)

    return schnittstelle.typArgumente[0].typ!!
  }

  private fun evaluiereKonvertierung(konvertierung: AST.Satz.Ausdruck.Konvertierung): Typ{
    val ausdruck = evaluiereAusdruck(konvertierung.ausdruck)
    val konvertierungsTyp = typisierer.bestimmeTyp(konvertierung.typ, funktionsTypParams, klassenTypParams,
        istAliasErlaubt = true, erlaubeLeereTypArgumente = false)!!

    if (!kannNachTypKonvertiertWerden(konvertierung, ausdruck, konvertierungsTyp)) {
      throw GermanSkriptFehler.KonvertierungsFehler(konvertierung.typ.name.bezeichnerToken, ausdruck, konvertierungsTyp)
    }
    return konvertierungsTyp
  }

  private fun kannNachTypKonvertiertWerden(konvertierung: AST.Satz.Ausdruck.Konvertierung, typ: Typ, konvertierungsTyp: Typ): Boolean {
    // Eine Konvertierung ist möglich wenn die Typen miteinander übereinstimmen,
    // oder eine Konvertierung in eine Zeichenfolge erfolgt (diese ist immer möglich)
    // oder eine Konvertierungsdefinition für die Konvertierung vorhanden ist.
    // Bei einem Generic wird übeprüft ob die Elternklasse in den gegebenen Typ konvertiert werden kann.
    if (konvertierungsTyp == BuildIn.Klassen.zeichenfolge) {
      return true
    }

    if (when (typ) {
      is Typ.Generic -> typ.typParam.elternKlasse?.let {
        (it.typ as Typ.Compound.Klasse).definition.konvertierungen.containsKey(konvertierung.typ.vollständigerName)
      } == true
      is Typ.Compound.Klasse -> typ.definition.konvertierungen.containsKey(konvertierung.typ.vollständigerName)
      // TODO: Schnittstellen sollen eventuell auch Konvertierungsdefinitionen
      //  haben und auch in Zeichenfolgen konvertiert werden können
      is Typ.Compound.Schnittstelle -> false
    }) {
      return true
    }
    konvertierung.konvertierungsArt = AST.Satz.Ausdruck.KonvertierungsArt.Cast
    return typIstTyp(typ, konvertierungsTyp) || typIstTyp(konvertierungsTyp, typ)
  }

  private fun evaluiereSelbstReferenz() = zuÜberprüfendeKlasse!!

  private fun evaluiereLambda(lambda: AST.Satz.Ausdruck.Lambda): Typ.Compound.Klasse {
    val schnittstelle = typisierer.bestimmeTyp(
        lambda.schnittstelle,
        funktionsTypParams,
        klassenTypParams,
        istAliasErlaubt = true,
        erlaubeLeereTypArgumente = true
    )
    if (schnittstelle !is Typ.Compound.Schnittstelle) {
      throw GermanSkriptFehler.SchnittstelleErwartet(lambda.schnittstelle.name.bezeichnerToken)
    }
    inferiereTypArgumente(schnittstelle)
    if (schnittstelle.definition.methodenSignaturen.size != 1) {
      throw GermanSkriptFehler.LambdaFehler.UngültigeLambdaSchnittstelle(lambda.schnittstelle.name.bezeichnerToken, schnittstelle.definition)
    }

    if (schnittstelle.typArgumente.size != schnittstelle.definition.typParameter.size) {
      throw GermanSkriptFehler.TypFehler.TypArgumentFehler(
          lambda.schnittstelle.name.bezeichnerToken,
          schnittstelle.typArgumente.size,
          schnittstelle.definition.typParameter.size
      )
    }

    val signatur = schnittstelle.definition.methodenSignaturen[0].copy()
    signatur.parameterNamen.clear()

    val parameter = signatur.parameter.toList()
    if (lambda.bindings.size > signatur.parameter.count()) {
      throw GermanSkriptFehler.LambdaFehler.ZuVieleBinder(
          lambda.bindings[parameter.size].bezeichner.toUntyped(),
          parameter.size
      )
    }
    umgebung.pushBereich()
    for (paramIndex in parameter.indices) {
      val param = parameter[paramIndex]
      val paramName = lambda.bindings.getOrElse(paramIndex) {
        holeParamName(param, schnittstelle.typArgumente)
      }
      signatur.parameterNamen.add(paramName)
      val paramTyp = holeParamTyp(param, schnittstelle.typArgumente)
      umgebung.schreibeVariable(paramName, paramTyp, false)
    }

    val rückgabe = durchlaufeAufruf(
        lambda.schnittstelle.name.bezeichnerToken,
        lambda.körper, umgebung,
        false,
        rückgabeTyp,
        true
    )
    val erwarteterRückgabeTyp = ersetzeGenerics(signatur.rückgabeTyp, null, schnittstelle.typArgumente).typ!!

    if (erwarteterRückgabeTyp != BuildIn.Klassen.nichts && !typIstTyp(rückgabe, erwarteterRückgabeTyp)) {
      throw GermanSkriptFehler.LambdaFehler.FalscheRückgabe(lambda.schnittstelle.name.bezeichnerToken, rückgabe, erwarteterRückgabeTyp)
    }
    umgebung.popBereich()

    val klassenDefinition = AST.Definition.Typdefinition.Klasse(
        emptyList(), lambda.schnittstelle.name as AST.WortArt.Nomen,
        null, mutableListOf(), AST.Satz.Bereich(mutableListOf())
    )
    klassenDefinition.methoden[signatur.vollerName!!] = AST.Definition.Funktion(signatur, lambda.körper)
    klassenDefinition.implementierteSchnittstellen.add(schnittstelle)
    typisierer.fügeElternKlassenMethodenHinzu(klassenDefinition, BuildIn.Klassen.objekt.definition)

    val klasse = Typ.Compound.Klasse(klassenDefinition, schnittstelle.typArgumente)
    lambda.klasse = klasse
    return klasse
  }

  private fun evaluiereAnonymeKlasse(anonymeKlasse: AST.Satz.Ausdruck.AnonymeKlasse): Typ {
    val schnittstelle = typisierer.bestimmeTyp(
        anonymeKlasse.schnittstelle,
        funktionsTypParams,
        klassenTypParams,
        istAliasErlaubt = true,
        erlaubeLeereTypArgumente = true
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
    // Anonyme Objekt erbt auch von der Klasse Objekt
    typisierer.fügeElternKlassenMethodenHinzu(klassenDefinition, BuildIn.Klassen.objekt.definition)

    val typArgumente = if (anonymeKlasse.schnittstelle.typArgumente.isNotEmpty()) {
      anonymeKlasse.schnittstelle.typArgumente
    } else if (erwarteterTyp != null && erwarteterTyp is Typ.Compound) {
      (erwarteterTyp!! as Typ.Compound).typArgumente
    } else {
      emptyList()
    }

    val klasse = Typ.Compound.Klasse(
        klassenDefinition,
        typArgumente
    )

    anonymeKlasse.klasse = klasse

    val vorherigeKlasse = zuÜberprüfendeKlasse
    zuÜberprüfendeKlasse = klasse
    prüfeImplementierungsBereich(anonymeKlasse.bereich, umgebung)
    zuÜberprüfendeKlasse = vorherigeKlasse

    return klasse
  }

  private fun evaluiereTypÜberprüfung(typÜberprüfung: AST.Satz.Ausdruck.TypÜberprüfung): Typ {
    typisierer.bestimmeTyp(typÜberprüfung.typ, funktionsTypParams, klassenTypParams,
        istAliasErlaubt = true, erlaubeLeereTypArgumente = false)
    return BuildIn.Klassen.boolean
  }
}

fun main() {
  val typPrüfer = TypPrüfer(File("./beispiele/HalloWelt.gm"))
  typPrüfer.prüfe()
  typPrüfer.logger.print()
}