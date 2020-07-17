import java.io.File
import util.SimpleLogger

class TypPrüfer(startDatei: File): ProgrammDurchlaufer<Typ>(startDatei) {
  val typisierer = Typisierer(startDatei)
  override val definierer = typisierer.definierer
  override var umgebung = Umgebung<Typ>()
  val logger = SimpleLogger()
  val ast: AST.Programm get() = typisierer.ast

  private var zuÜberprüfendeKlasse: AST.Definition.Klasse? = null
  private var rückgabeTyp: Typ? = null
  private var rückgabeErreicht = false

  fun prüfe() {
    typisierer.typisiere()
    // zuerst die Klassendefinitionen, damit noch private Eigenschaften definiert werden können
    definierer.klassenDefinitionen.forEach(::prüfeKlasse)
    definierer.funktionsDefinitionen.forEach(::prüfeFunktion)

    // neue Umgebung
    durchlaufeAufruf(ast.programmStart!!, ast.sätze, Umgebung(), true,null)
  }


  private fun ausdruckMussTypSein(ausdruck: AST.Ausdruck, erwarteterTyp: Typ): Typ {
    val ausdruckTyp = evaluiereAusdruck(ausdruck)
    if (ausdruckTyp != erwarteterTyp) {
      throw GermanScriptFehler.TypFehler.FalscherTyp(holeErstesTokenVonAusdruck(ausdruck), ausdruckTyp, erwarteterTyp.name)
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
    durchlaufeAufruf(funktion.name.toUntyped(), funktion.sätze, funktionsUmgebung, false, funktion.rückgabeTyp?.typ)
  }

  private fun prüfeKlasse(klasse: AST.Definition.Klasse) {
    zuÜberprüfendeKlasse = klasse
    durchlaufeAufruf(klasse.typ.name.bezeichner.toUntyped(), klasse.konstruktorSätze, Umgebung(), true,null)
    klasse.methoden.values.forEach {methode -> prüfeFunktion(methode.funktion)}
    klasse.konvertierungen.values.forEach {konvertierung ->
      durchlaufeAufruf(konvertierung.klasse.bezeichner.toUntyped(), konvertierung.sätze, Umgebung(), true, konvertierung.typ.typ!!)
    }
  }

  private fun durchlaufeAufruf(token: Token, sätze: List<AST.Satz>, umgebung: Umgebung<Typ>, neuerBereich: Boolean, rückgabeTyp: Typ?) {
    this.rückgabeTyp = rückgabeTyp
    this.umgebung = umgebung
    rückgabeErreicht = false
    durchlaufeSätze(sätze, neuerBereich)
    if (rückgabeTyp != null && !rückgabeErreicht) {
      throw GermanScriptFehler.RückgabeFehler.RückgabeVergessen(token, rückgabeTyp)
    }
  }

  override fun durchlaufeVariablenDeklaration(deklaration: AST.Satz.VariablenDeklaration) {
    if (deklaration.istEigenschaftsNeuZuweisung) {
      val klasse = zuÜberprüfendeKlasse!!
      val eigenschaft = holeEigenschaftAusKlasse(deklaration.name, klasse)
      if (eigenschaft.name.unveränderlich) {
        throw GermanScriptFehler.EigenschaftsFehler.EigenschaftUnveränderlich(deklaration.name.bezeichner.toUntyped())
      }
      val eigenschaftTyp = eigenschaft.typKnoten.typ!!
      ausdruckMussTypSein(deklaration.ausdruck, eigenschaftTyp)
    }
    else if (deklaration.istEigenschaft) {
      val klasse = zuÜberprüfendeKlasse!!
      val wert = evaluiereAusdruck(deklaration.ausdruck)
      val typ = AST.TypKnoten(deklaration.name, wert)
      klasse.eigenschaften.add(AST.Definition.TypUndName(typ, deklaration.name))
    }
    else {
      if (deklaration.name.unveränderlich || deklaration.neu != null) {
        val wert = evaluiereAusdruck(deklaration.ausdruck)
        umgebung.schreibeVariable(deklaration.name, wert, !deklaration.name.unveränderlich)
      }
      else {
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
  }

  override fun durchlaufeFunktionsAufruf(funktionsAufruf: AST.Funktion, istAusdruck: Boolean): Typ? {
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
            methodenBlockObjekt.klassenDefinition.typ.name.nominativ)
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
          throw  GermanScriptFehler.Undefiniert.Methode(funktionsAufruf.verb.toUntyped(), funktionsAufruf, klasse.typ.name.nominativ)
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
    val j = if (funktionsAufruf.aufrufTyp == FunktionsAufrufTyp.METHODEN_OBJEKT_AUFRUF) 1 else 0
    val anzahlArgumente = argumente.size - j
    if (anzahlArgumente != parameter.size) {
      throw GermanScriptFehler.SyntaxFehler.AnzahlDerParameterFehler(funktionsDefinition.name.toUntyped())
    }

    // stimmen die Argument Typen mit den Parameter Typen überein?
    for (i in parameter.indices) {
      ausdruckMussTypSein(argumente[i+j].wert, parameter[i].typKnoten.typ!!)
    }

    return funktionsDefinition.rückgabeTyp?.typ
  }

  override fun durchlaufeZurückgabe(zurückgabe: AST.Satz.Zurückgabe) {
    if (rückgabeTyp == null && zurückgabe.ausdruck != null) {
      throw GermanScriptFehler.RückgabeFehler.UngültigeRückgabe(zurückgabe.erstesToken.toUntyped())
    }
    if (rückgabeTyp != null) {
      if (zurückgabe.ausdruck == null) {
        throw GermanScriptFehler.RückgabeFehler.RückgabeVergessen(zurückgabe.erstesToken.toUntyped(), rückgabeTyp!!)
      }
      ausdruckMussTypSein(zurückgabe.ausdruck, rückgabeTyp!!)
    }
    rückgabeErreicht = true
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
        throw GermanScriptFehler.TypFehler.FalscherTyp(holeErstesTokenVonAusdruck(schleife.liste), liste, "Liste")
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
    // Hier muss nicht viel gemacht werden
    // die internen Sachen sind schon von kotlin Typ-gecheckt :)
    rückgabeErreicht = true
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
    var j = 0
    for (i in definition.eigenschaften.indices) {
      val eigenschaft = definition.eigenschaften[i]
      if (eigenschaft.istPrivat) {
        j++
        continue
      }
      if (i >= instanziierung.eigenschaftsZuweisungen.size + j) {
        throw GermanScriptFehler.EigenschaftsFehler.EigenschaftsVergessen(instanziierung.klasse.name.bezeichner.toUntyped(), eigenschaft.name.nominativ)
      }
      val zuweisung = instanziierung.eigenschaftsZuweisungen[i-j]

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
    return holeEigenschaftAusKlasse(eigenschaftsZugriff.eigenschaftsName, klasse.klassenDefinition).typKnoten.typ!!
  }

  override fun evaluiereSelbstEigenschaftsZugriff(eigenschaftsZugriff: AST.Ausdruck.SelbstEigenschaftsZugriff): Typ {
    return holeEigenschaftAusKlasse(eigenschaftsZugriff.eigenschaftsName, zuÜberprüfendeKlasse!!).typKnoten.typ!!
  }

  override fun evaluiereMethodenBlockEigenschaftsZugriff(eigenschaftsZugriff: AST.Ausdruck.MethodenBlockEigenschaftsZugriff): Typ {
    val methodenBlockObjekt = umgebung.holeMethodenBlockObjekt() as Typ.Klasse
    return holeEigenschaftAusKlasse(eigenschaftsZugriff.eigenschaftsName, methodenBlockObjekt.klassenDefinition).typKnoten.typ!!
  }

  private fun holeEigenschaftAusKlasse(eigenschaftsName: AST.Nomen, klasse: AST.Definition.Klasse): AST.Definition.TypUndName {
    for (eigenschaft in klasse.eigenschaften) {
      if (eigenschaftsName.nominativ == eigenschaft.name.nominativ) {
        return eigenschaft
      }
    }
    throw GermanScriptFehler.Undefiniert.Eigenschaft(eigenschaftsName.bezeichner.toUntyped(), klasse.typ.name.nominativ)
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
    if (!ausdruck.kannNachTypKonvertiertWerden(konvertierungsTyp)){
      throw GermanScriptFehler.KonvertierungsFehler(konvertierung.typ.name.bezeichner.toUntyped(),
          ausdruck, konvertierungsTyp)
    }
    return konvertierungsTyp
  }

  override fun evaluiereSelbstReferenz() = zuÜberprüfendeKlasse!!.typ.typ!!
}

fun main() {
  val typPrüfer = TypPrüfer(File("./iterationen/iter_2/code.gms"))
  typPrüfer.prüfe()
  typPrüfer.logger.print()
}