import util.SimpleLogger

class TypPrüfer(dateiPfad: String): ProgrammDurchlaufer<Typ>(dateiPfad) {
  val typisierer = Typisierer(dateiPfad)
  val definierer = typisierer.definierer
  val logger = SimpleLogger()

  override val ast: AST.Programm get() = typisierer.ast

  fun prüfe() {
    typisierer.typisiere()
    durchlaufe(ast.sätze, Umgebung(), true)
    definierer.funktionsDefinitionen.forEach(::prüfeFunktion)
  }

  private fun ausdruckMussTypSein(ausdruck: AST.Ausdruck, erwarteterTyp: Typ): Typ {
    if (evaluiereAusdruck(ausdruck) != erwarteterTyp) {
      throw GermanScriptFehler.TypFehler(holeErstesTokenVonAusdruck(ausdruck), erwarteterTyp)
    }
    return erwarteterTyp
  }

  // breche niemals Sätze ab
  override fun sollSätzeAbbrechen(): Boolean = false

  private fun prüfeFunktion(funktion: AST.Definition.Funktion) {
    logger.addLine("")
    logger.addLine("_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-")
    val funktionsUmgebung = Umgebung<Typ>()
    funktionsUmgebung.pushBereich()
    var variablenString = ""
    for (parameter in funktion.parameter) {
      funktionsUmgebung.schreibeVariable(parameter.name, parameter.typKnoten.typ!!)
      variablenString += "${parameter.name.nominativ!!} :${parameter.typKnoten.typ!!}"
    }
    logger.addLine("Funktionsdefinition(${funktion.name.wert})[$variablenString]")
    logger.addLine("Sätze:")
    durchlaufe(funktion.sätze, funktionsUmgebung, true)
    logger.addLine("____________________________________________________________________")
  }

  override fun durchlaufeFunktionsAufruf(funktionsAufruf: AST.FunktionsAufruf, istAusdruck: Boolean): Typ? {
    logger.addLine("")
    val funktionsDefinition = definierer.holeFunktionsDefinition(funktionsAufruf)
    if (istAusdruck && funktionsDefinition.rückgabeTyp == null) {
      throw GermanScriptFehler.SyntaxFehler.FunktionAlsAusdruckFehler(funktionsDefinition.name.toUntyped())
    }

    val parameter = funktionsDefinition.parameter
    val argumente = funktionsAufruf.argumente
    if (argumente.size != parameter.size) {
      throw GermanScriptFehler.SyntaxFehler.AnzahlDerParameterFehler(funktionsDefinition.name.toUntyped())
    }
    var argumenteString = ""
    for (i in argumente.indices) {
      ausdruckMussTypSein(argumente[i].wert, parameter[i].typKnoten.typ!!)
      argumenteString += "${argumente[i].name.nominativ} :${parameter[i].typKnoten.typ!!}"
    }
    logger.addLine("Funktionsaufruf(${funktionsAufruf.vollerName})[$argumenteString]")
//    logger.addLine("____________________________________________________________________")

    return funktionsDefinition.rückgabeTyp?.typ
  }

  override fun durchlaufeZurückgabe(zurückgabe: AST.Satz.Zurückgabe) {
    val funktionsDefinition = zurückgabe.findNodeInParents<AST.Definition.Funktion>()!!
    val rückgabeTyp = funktionsDefinition.rückgabeTyp
    if (funktionsDefinition.rückgabeTyp == null) {
      throw GermanScriptFehler.SyntaxFehler.RückgabeTypFehler(holeErstesTokenVonAusdruck(zurückgabe.ausdruck))
    }
    logger.addLine("-> $rückgabeTyp")
    ausdruckMussTypSein(zurückgabe.ausdruck, rückgabeTyp!!.typ!!)
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
      (evaluiereListe(schleife.liste) as Typ.Liste).elementTyp
    } else {
      evaluiereListenSingular(schleife.singular!!)
    }
    stack.peek().pushBereich()
    stack.peek().schreibeVariable(schleife.binder, elementTyp)
    durchlaufeSätze(schleife.sätze, false)
    stack.peek().popBereich()
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
    return typisierer.bestimmeTypen(ausdruck.pluralTyp)
  }

  override fun evaluiereListenElement(listenElement: AST.Ausdruck.ListenElement): Typ {
    ausdruckMussTypSein(listenElement.index, Typ.Zahl)
    return evaluiereListenSingular(listenElement.singular)
  }

  private fun evaluiereListenSingular(singular: AST.Nomen): Typ {
    val liste = evaluiereVariable(singular.nominativPlural!!)?:
    throw GermanScriptFehler.Undefiniert.Variable(singular.bezeichner.toUntyped(), singular.nominativPlural!!)

    return (liste as Typ.Liste).elementTyp
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
    if (!ausdruck.istKonvertierbar(konvertierung.typ.typ!!)){
      throw GermanScriptFehler.KonvertierungsFehler(konvertierung.typ.name.bezeichner.toUntyped(),
          ausdruck, konvertierung.typ.typ!!)
    }

//    if (!ausdruck.definierteKonvertierungen.contains(typ)){
//      throw Error("Keine Konvertierung möglich!!")
//    }

    return konvertierung.typ.typ!!
  }
}

fun main() {
  val typPrüfer = TypPrüfer("./iterationen/iter_1/code.gms")
  typPrüfer.prüfe()
  typPrüfer.logger.print()
}