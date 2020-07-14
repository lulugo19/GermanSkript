import util.SimpleLogger

class TypPrüfer(dateiPfad: String): ProgrammDurchlaufer<Typ>(dateiPfad) {
  val typisierer = Typisierer(dateiPfad)
  override val definierer = typisierer.definierer
  override var umgebung = Umgebung<Typ>()
  val logger = SimpleLogger()
  val ast: AST.Aufruf.Programm get() = typisierer.ast

  private var zuÜberprüfendeKlasse: AST.Definition.Klasse? = null
  private var zuÜberprüfendeFunktion: AST.Definition.FunktionOderMethode.Funktion? = null

  fun prüfe() {
    typisierer.typisiere()
    durchlaufeSätze(ast.sätze, true)
    definierer.funktionsDefinitionen.forEach(::prüfeFunktion)
    definierer.klassenDefinitionen.forEach { klasse ->
      zuÜberprüfendeKlasse = klasse
      durchlaufeSätze(klasse.konstruktor.sätze, true)
      klasse.methoden.values.forEach {methode -> prüfeFunktion(methode.funktion)}
    }
  }

  private fun ausdruckMussTypSein(ausdruck: AST.Ausdruck, erwarteterTyp: Typ): Typ {
    if (evaluiereAusdruck(ausdruck) != erwarteterTyp) {
      throw GermanScriptFehler.TypFehler.FalscherTyp(holeErstesTokenVonAusdruck(ausdruck), erwarteterTyp.name)
    }
    return erwarteterTyp
  }

  // breche niemals Sätze ab
  override fun sollSätzeAbbrechen(): Boolean = false

  private fun prüfeFunktion(funktion: AST.Definition.FunktionOderMethode.Funktion) {
    val funktionsUmgebung = Umgebung<Typ>()
    funktionsUmgebung.pushBereich()
    for (parameter in funktion.parameter) {
      funktionsUmgebung.schreibeVariable(parameter.name, parameter.typKnoten.typ!!, false)
    }
    umgebung = funktionsUmgebung
    zuÜberprüfendeFunktion = funktion
    durchlaufeSätze(funktion.sätze, true)
  }

  override fun durchlaufeVariablenDeklaration(deklaration: AST.Satz.VariablenDeklaration) {
    if (deklaration.name.unveränderlich || deklaration.neu != null) {
      val wert = evaluiereAusdruck(deklaration.ausdruck)
      umgebung.schreibeVariable(deklaration.name, wert, !deklaration.name.unveränderlich)
    } else {
      // hier müssen wir überprüfen ob der Typ der Variable, die überschrieben werden sollen gleich
      // dem neuen Wert ist
      val vorherigerTyp = umgebung.leseVariable(deklaration.name.nominativ)
      val wert = if (vorherigerTyp != null) {
        if (vorherigerTyp.name.unveränderlich) {
          throw GermanScriptFehler.Variablenfehler(deklaration.name.bezeichner.toUntyped(), vorherigerTyp.name)
        }
        ausdruckMussTypSein(deklaration.ausdruck, vorherigerTyp.wert)
      } else {
        evaluiereAusdruck(deklaration.ausdruck)
      }
      umgebung.überschreibeVariable(deklaration.name, wert)
    }
  }

  override fun durchlaufeFunktionsAufruf(funktionsAufruf: AST.Aufruf.Funktion, istAusdruck: Boolean): Typ? {
    if (funktionsAufruf.vollerName == null) {
      funktionsAufruf.vollerName = definierer.holeVollenNamenVonFunktionsAufruf(funktionsAufruf, false)
    }
    logger.addLine("prüfe Funktionsaufruf in ${funktionsAufruf.verb.position}: ${funktionsAufruf.vollerName!!}")
    val methodenBlockObjekt = umgebung.holeMethodenBlockObjekt()

    // ist Methodenaufruf von Block-Variable
    if (methodenBlockObjekt is Typ.Klasse) {
      if (methodenBlockObjekt.klassenDefinition.methoden.containsKey(funktionsAufruf.vollerName!!)) {
        val methodenDefinition = methodenBlockObjekt.klassenDefinition.methoden.getValue(funktionsAufruf.vollerName!!).funktion
        funktionsAufruf.funktionsDefinition = methodenDefinition
        funktionsAufruf.aufrufTyp = FunktionsAufrufTyp.METHODEN_BLOCK_AUFRUF
      }
      else if (funktionsAufruf.reflexivPronomen != null && funktionsAufruf.reflexivPronomen.typ == TokenTyp.REFLEXIV_PRONOMEN.DICH) {
        throw GermanScriptFehler.Undefiniert.Methode(funktionsAufruf.verb.toUntyped(),
            funktionsAufruf,
            methodenBlockObjekt.klassenDefinition.name.nominativ)
      }
    }

    // ist Methoden-Selbst-Aufruf
    if (funktionsAufruf.funktionsDefinition == null) {
      if (zuÜberprüfendeKlasse != null) {
        val klasse = zuÜberprüfendeKlasse!!
        if (klasse.methoden.containsKey(funktionsAufruf.vollerName!!)) {
          funktionsAufruf.funktionsDefinition = klasse.methoden.getValue(funktionsAufruf.vollerName!!).funktion
          funktionsAufruf.aufrufTyp = FunktionsAufrufTyp.METHODEN_SELBST_AUFRUF
        } else if (funktionsAufruf.reflexivPronomen != null && funktionsAufruf.reflexivPronomen.typ == TokenTyp.REFLEXIV_PRONOMEN.MICH) {
          throw  GermanScriptFehler.Undefiniert.Methode(funktionsAufruf.verb.toUntyped(), funktionsAufruf, klasse.name.nominativ)
        }
      }
    }

    // ist normale Funktion
    val undefiniertFehler = try {
      if (funktionsAufruf.funktionsDefinition == null) {
        funktionsAufruf.funktionsDefinition = definierer.holeFunktionsDefinition(funktionsAufruf)
        null
      } else {
        null
      }
    } catch (fehler: GermanScriptFehler.Undefiniert.Funktion) {
      fehler
    }

    // ist Methoden-Objekt-Aufruf als letzte Möglichkeit
    // das bedeutet Funktionsnamen gehen vor Methoden-Objekt-Aufrufen
    if (funktionsAufruf.funktionsDefinition == null && funktionsAufruf.objekt != null) {
      try {
        val klasse = typisierer.bestimmeTypen(funktionsAufruf.objekt.name)
        if (klasse is Typ.Klasse) {
          val methodenName = definierer.holeVollenNamenVonFunktionsAufruf(funktionsAufruf, true)
          val klassenDefinition = klasse.klassenDefinition
          if (klassenDefinition.methoden.containsKey(methodenName)) {
            funktionsAufruf.vollerName = methodenName
            funktionsAufruf.funktionsDefinition = klassenDefinition.methoden.getValue(methodenName).funktion
            funktionsAufruf.aufrufTyp = FunktionsAufrufTyp.METHODEN_OBJEKT_AUFRUF
          }
        }
      } catch (fehler: GermanScriptFehler.Undefiniert.Typ) {
        // fange einfach nur den Fehler auf
        throw undefiniertFehler!!
      }
    }

    if (funktionsAufruf.funktionsDefinition == null) {
      throw GermanScriptFehler.Undefiniert.Funktion(funktionsAufruf.verb.toUntyped(), funktionsAufruf)
    }
    val funktionsDefinition = funktionsAufruf.funktionsDefinition!!

    if (istAusdruck && funktionsDefinition.rückgabeTyp == null) {
      throw GermanScriptFehler.SyntaxFehler.FunktionAlsAusdruckFehler(funktionsDefinition.name.toUntyped())
    }

    val parameter = funktionsDefinition.parameter
    val argumente = funktionsAufruf.argumente
    val anzahlArgumente = argumente.size - if (funktionsAufruf.aufrufTyp == FunktionsAufrufTyp.METHODEN_OBJEKT_AUFRUF) 1 else 0
    if (anzahlArgumente != parameter.size) {
      throw GermanScriptFehler.SyntaxFehler.AnzahlDerParameterFehler(funktionsDefinition.name.toUntyped())
    }

    // stimmen die Argument Typen mit den Parameter Typen überein?
    for (i in argumente.indices) {
      if (funktionsAufruf.aufrufTyp == FunktionsAufrufTyp.METHODEN_OBJEKT_AUFRUF && i == 0) {
        continue
      }
      ausdruckMussTypSein(argumente[i].wert, parameter[i].typKnoten.typ!!)
    }

    return funktionsDefinition.rückgabeTyp?.typ
  }

  override fun durchlaufeZurückgabe(zurückgabe: AST.Satz.Zurückgabe) {
    val rückgabeTyp = zuÜberprüfendeFunktion?.rückgabeTyp
        ?: throw GermanScriptFehler.SyntaxFehler.RückgabeTypFehler(holeErstesTokenVonAusdruck(zurückgabe.ausdruck))
    ausdruckMussTypSein(zurückgabe.ausdruck, rückgabeTyp.typ!!)
  }

  private fun prüfeBedingung(bedingung: AST.Satz.BedingungsTerm) {
    ausdruckMussTypSein(bedingung.bedingung, Typ.Boolean)
    durchlaufeSätze(bedingung.sätze, true)
  }

  override fun durchlaufeBedingungsSatz(bedingungsSatz: AST.Satz.Bedingung) {
    bedingungsSatz.bedingungen.forEach(::prüfeBedingung)
    bedingungsSatz.sonst?.also {durchlaufeSätze(it, true)}
  }

  override fun durchlaufeSolangeSchleife(schleife: AST.Satz.SolangeSchleife) {
    prüfeBedingung(schleife.bedingung)
  }

  override fun durchlaufeFürJedeSchleife(schleife: AST.Satz.FürJedeSchleife) {
    val elementTyp = if (schleife.liste != null) {
      val liste = evaluiereAusdruck(schleife.liste)
      if (liste !is Typ.Liste) {
        throw GermanScriptFehler.TypFehler.FalscherTyp(holeErstesTokenVonAusdruck(schleife.liste), "Liste")
      }
      liste.elementTyp
    } else {
      evaluiereListenSingular(schleife.singular)
    }
    umgebung.pushBereich()
    umgebung.schreibeVariable(schleife.binder, elementTyp, false)
    durchlaufeSätze(schleife.sätze, true)
    umgebung.popBereich()
  }

  override fun durchlaufeIntern() {
    // Hier muss nichts gemacht werden
    // die internen Sachen sind schon von kotlin Typ-gecheckt :)
  }

  override fun bevorDurchlaufeMethodenBlock(methodenBlock: AST.Satz.MethodenBlock, blockObjekt: Typ?) {
    if (blockObjekt !is Typ.Klasse) {
      throw GermanScriptFehler.TypFehler.ObjektErwartet(methodenBlock.name.bezeichner.toUntyped())
    }
  }

  override fun durchlaufeAbbrechen() {
    // mache nichts hier
  }

  override fun durchlaufeFortfahren() {
    // mache nichts hier
  }

  override fun evaluiereZeichenfolge(ausdruck: AST.Ausdruck.Zeichenfolge) = Typ.Zeichenfolge

  override fun evaluiereZahl(ausdruck: AST.Ausdruck.Zahl) = Typ.Zahl

  override fun evaluiereBoolean(ausdruck: AST.Ausdruck.Boolean) = Typ.Boolean

  override fun evaluiereListe(ausdruck: AST.Ausdruck.Liste): Typ {
    val listenTyp = typisierer.bestimmeTypen(ausdruck.pluralTyp) as Typ.Liste
    ausdruck.elemente.forEach {element -> ausdruckMussTypSein(element, listenTyp.elementTyp)}
    return listenTyp
  }

  override fun evaluiereListenElement(listenElement: AST.Ausdruck.ListenElement): Typ {
    ausdruckMussTypSein(listenElement.index, Typ.Zahl)
    return evaluiereListenSingular(listenElement.singular)
  }

  private fun evaluiereListenSingular(singular: AST.Nomen): Typ {
    val plural = singular.ganzesWort(Kasus.NOMINATIV, Numerus.PLURAL)
    val liste = evaluiereVariable(plural)?:
    throw GermanScriptFehler.Undefiniert.Variable(singular.bezeichner.toUntyped(), plural)

    return (liste as Typ.Liste).elementTyp
  }

  override fun evaluiereObjektInstanziierung(instanziierung: AST.Ausdruck.ObjektInstanziierung): Typ {
    typisierer.typisiereTypKnoten(instanziierung.klasse)
    val klasse = instanziierung.klasse.typ!!
    if (klasse !is Typ.Klasse) {
      throw GermanScriptFehler.TypFehler.ObjektErwartet(instanziierung.klasse.name.bezeichner.toUntyped())
    }
    val definition = klasse.klassenDefinition

    // die Eigenschaftszuweisungen müssen mit der Instanzzierung übereinstimmen, Außerdem müssen die Namen übereinstimmen
    for (i in definition.eigenschaften.indices) {
      val eigenschaft = definition.eigenschaften[i]
      if (i >= instanziierung.eigenschaftsZuweisungen.size) {
        throw GermanScriptFehler.EigenschaftsFehler.EigenschaftsVergessen(instanziierung.klasse.name.bezeichner.toUntyped(), eigenschaft.name.nominativ)
      }
      val zuweisung = instanziierung.eigenschaftsZuweisungen[i]

      if (eigenschaft.name.nominativ != zuweisung.name.nominativ) {
        GermanScriptFehler.EigenschaftsFehler.UnerwarteterEigenschaftsName(zuweisung.name.bezeichner.toUntyped(), eigenschaft.name.nominativ)
      }

      // die Typen müssen übereinstimmen
      ausdruckMussTypSein(zuweisung.wert, eigenschaft.typKnoten.typ!!)
    }

    if (instanziierung.eigenschaftsZuweisungen.size > definition.eigenschaften.size) {
      throw GermanScriptFehler.EigenschaftsFehler.UnerwarteteEigenschaft(
          instanziierung.eigenschaftsZuweisungen[definition.eigenschaften.size].name.bezeichner.toUntyped())
    }
    return klasse
  }

  override fun evaluiereEigenschaftsZugriff(eigenschaftsZugriff: AST.Ausdruck.EigenschaftsZugriff): Typ {
    val klasse = evaluiereAusdruck(eigenschaftsZugriff.objekt)
    if (klasse !is Typ.Klasse) {
      throw GermanScriptFehler.TypFehler.ObjektErwartet(holeErstesTokenVonAusdruck(eigenschaftsZugriff.objekt))
    }
    return  überprüfeEigenschaftInKlasse(eigenschaftsZugriff.eigenschaftsName, klasse.klassenDefinition)
  }

  override fun evaluiereSelbstEigenschaftsZugriff(eigenschaftsZugriff: AST.Ausdruck.SelbstEigenschaftsZugriff): Typ {
    return überprüfeEigenschaftInKlasse(eigenschaftsZugriff.eigenschaftsName, zuÜberprüfendeKlasse!!)
  }

  override fun evaluiereMethodenBlockEigenschaftsZugriff(eigenschaftsZugriff: AST.Ausdruck.MethodenBlockEigenschaftsZugriff): Typ {
    val methodenBlockObjekt = umgebung.holeMethodenBlockObjekt() as Typ.Klasse
    return überprüfeEigenschaftInKlasse(eigenschaftsZugriff.eigenschaftsName, methodenBlockObjekt.klassenDefinition)
  }

  private fun überprüfeEigenschaftInKlasse(eigenschaftsName: AST.Nomen, klasse: AST.Definition.Klasse): Typ {
    for (eigenschaft in klasse.eigenschaften) {
      if (eigenschaftsName.nominativ == eigenschaft.name.nominativ) {
        return eigenschaft.typKnoten.typ!!
      }
    }
    throw GermanScriptFehler.Undefiniert.Eigenschaft(eigenschaftsName.bezeichner.toUntyped(), klasse.name.nominativ)
  }

  override fun evaluiereBinärenAusdruck(ausdruck: AST.Ausdruck.BinärerAusdruck): Typ {
    val linkerTyp = evaluiereAusdruck(ausdruck.links)
    val operator = ausdruck.operator.typ.operator
    if (!linkerTyp.definierteOperatoren.containsKey(operator)) {
      throw GermanScriptFehler.Undefiniert.Operator(ausdruck.operator.toUntyped(), linkerTyp.name)
    }
    // es wird erwartete, dass bei einem binären Ausdruck beide Operanden vom selben Typen sind
    ausdruckMussTypSein(ausdruck.rechts, linkerTyp)
    return linkerTyp.definierteOperatoren.getValue(operator)
  }

  override fun evaluiereMinus(minus: AST.Ausdruck.Minus): Typ {
    return ausdruckMussTypSein(minus.ausdruck, Typ.Zahl)
  }

  override fun evaluiereKonvertierung(konvertierung: AST.Ausdruck.Konvertierung): Typ{
    val ausdruck = evaluiereAusdruck(konvertierung.ausdruck)
    typisierer.typisiereTypKnoten(konvertierung.typ)
    val konvertierungsTyp = konvertierung.typ.typ!!
    if (!ausdruck.definierteKonvertierungen.contains(konvertierungsTyp)){
      throw GermanScriptFehler.KonvertierungsFehler(konvertierung.typ.name.bezeichner.toUntyped(),
          ausdruck, konvertierungsTyp)
    }

    return konvertierungsTyp
  }
}

fun main() {
  val typPrüfer = TypPrüfer("./iterationen/iter_2/code.gms")
  typPrüfer.prüfe()
  typPrüfer.logger.print()
}