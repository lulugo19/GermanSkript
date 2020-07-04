import util.SimpleLogger
import kotlin.Error

sealed class Typ(val name: String) {
  abstract val definierteOperatoren: Map<Operator, Typ>


  object Zahl : Typ("Zahl") {
    override fun toString(): String {
      return "Zahl"
    }
    override val definierteOperatoren: Map<Operator, Typ>
      get() = mapOf(
          Operator.PLUS to  Zahl,
          Operator.MINUS to Zahl,
          Operator.MAL to Zahl,
          Operator.GETEILT to Zahl,
          Operator.MODULO to Zahl,
          Operator.HOCH to Zahl,
          Operator.GRÖßER to Boolean,
          Operator.KLEINER to Boolean,
          Operator.GRÖSSER_GLEICH to Boolean,
          Operator.KLEINER_GLEICH to Boolean,
          Operator.UNGLEICH to Boolean,
          Operator.GLEICH to Boolean
      )
  }

  object Zeichenfolge : Typ("Zeichenfolge") {
    override fun toString(): String {
      return "Zeichenfolge"
    }
    override val definierteOperatoren: Map<Operator, Typ>
      get() = mapOf(
          Operator.PLUS to Zeichenfolge,
          Operator.GLEICH to Boolean,
          Operator.UNGLEICH to Boolean,
          Operator.GRÖßER to Boolean,
          Operator.KLEINER to Boolean,
          Operator.GRÖSSER_GLEICH to Boolean,
          Operator.KLEINER_GLEICH to Boolean
      )
  }

  object Boolean : Typ("Boolean") {
    override fun toString(): String {
      return "Boolean"
    }
    override val definierteOperatoren: Map<Operator, Typ>
      get() = mapOf(
          Operator.UND to Boolean,
          Operator.ODER to Boolean,
          Operator.GLEICH to Boolean,
          Operator.UNGLEICH to Boolean
      )
  }
}

class TypPrüfer(dateiPfad: String): PipelineComponent(dateiPfad) {

  val definierer = Definierer(dateiPfad)
  val ast = definierer.ast
  val logger = SimpleLogger()

  fun prüfe() {
    definierer.definiere()

    prüfeSätze(ast.sätze, HashMap(), null)

    for (definition in ast.definitionen) {
      if (definition is AST.Definition.Funktion) {
        prüfeFunktion(definition)
      }
    }
  }

  private fun prüfeFunktion(funktion: AST.Definition.Funktion) {
    val variablen = HashMap<String, Typ>()
    print("Funktionsdefinition(${funktion.name.wert})[")
    for (parameter in funktion.parameter) {
      variablen[parameter.paramName.nominativ!!] = parameter.typKnoten.typ!!
      print("${parameter.paramName.nominativ!!} :${parameter.typKnoten.typ!!}")
    }
    print("] -> ")
    prüfeSätze(funktion.sätze, variablen, funktion.rückgabeTyp?.typ)
    println()
  }

  private fun prüfeSätze(sätze: List<AST.Satz>, variablen: HashMap<String, Typ>, rückgabeTyp: Typ?) {
    for (satz in sätze) {
      prüfeSatz(satz, rückgabeTyp, variablen)
    }
  }

  private fun prüfeSatz(satz: AST.Satz, rückgabeTyp: Typ?, variablen: HashMap<String, Typ>) {
    when (satz) {
      AST.Satz.Intern -> Unit // ignorieren
      is AST.Satz.VariablenDeklaration -> prüfeVariablenDeklaration(satz, variablen)
      is AST.Satz.FunktionsAufruf -> prüfeFunktionsAufruf(satz.aufruf, false, variablen)
      is AST.Satz.Zurückgabe -> prüfeZurückgabe(rückgabeTyp, satz, variablen)
    }
  }

  private fun prüfeZurückgabe(rückgabeTyp: Typ?, satz: AST.Satz.Zurückgabe, variablen: HashMap<String, Typ>) {
    if (rückgabeTyp == null) {
      throw Error("Die Funktions kann nichts zurückgeben, wenn sie keinen Rückgabetypen hat.")//TODO(RückgabeFehler)
    }
    print("RückgabeTyp = $rückgabeTyp")
    val ausdruckTyp = typVonAusdruck(satz.ausdruck, variablen)
    if (rückgabeTyp != ausdruckTyp) {
      throw GermanScriptFehler.TypFehler(holeErstesTokenVonAusdruck(satz.ausdruck), rückgabeTyp)
    }
  }

  private fun prüfeVariablenDeklaration(deklaration: AST.Satz.VariablenDeklaration, variablen: HashMap<String, Typ>): Typ {
    // der Typ der Variable in die Variablen-Map packen
    // unveränderbare Variablen dürfen nicht überschrieben werden
    val nominativ = deklaration.name.nominativ!!
    print("Variable($nominativ): ")
    val unveränderbar = deklaration.artikel.typ is TokenTyp.ARTIKEL.BESTIMMT
    if (unveränderbar && variablen.containsKey(nominativ)) {
      throw Error("Unveränderbare Variablen können nicht neu zugewiesen werden!")//TODO(Zuweisungsfehler)
    }
    val ausdruckTyp = typVonAusdruck(deklaration.ausdruck, variablen)
    variablen[nominativ] = ausdruckTyp
    println(" -> $ausdruckTyp")
    println()
    return ausdruckTyp
  }

  private fun typVonAusdruck(ausdruck: AST.Ausdruck, variablen: HashMap<String, Typ>): Typ {
    val typ = when (ausdruck) {
      is AST.Ausdruck.Zahl -> Typ.Zahl
      is AST.Ausdruck.Zeichenfolge -> Typ.Zeichenfolge
      is AST.Ausdruck.Boolean -> Typ.Boolean
      is AST.Ausdruck.BinärerAusdruck -> prüfeBinärenAusdruck(ausdruck, variablen)
      is AST.Ausdruck.Minus -> prüfeMinus(ausdruck, variablen)
      is AST.Ausdruck.Variable -> prüfeVariable(ausdruck, variablen)
      is AST.Ausdruck.FunktionsAufruf -> {
        prüfeFunktionsAufruf(ausdruck.aufruf, true, variablen)!!
      }
    }
    return typ
  }

  private fun prüfeFunktionsAufruf(funktionsAufruf: AST.FunktionsAufruf, istAusdruck: Boolean, variablen: HashMap<String, Typ>): Typ? {
    val funktionsDefinition = definierer.holeFunktionsDefinition(funktionsAufruf)
    print("Funktionsaufruf(${funktionsAufruf.vollerName})")
    if (istAusdruck && funktionsDefinition.rückgabeTyp == null) {
      throw Error("Eine Funktion ohne Rückgabetyp kann nicht als Ausdruck verwendet werden.")//TODO(RückgabeFehler die 2.)
    }

    val parameterTypen = funktionsDefinition.parameter.map { it.typKnoten.typ!! }
    val argumente = funktionsAufruf.argumente
    if (argumente.size != parameterTypen.size) {
      throw Error("Zu viele Parameter!")//TODO(ParameterFehler)
    }
    print("[")
    for (i in argumente.indices) {
      val typVonAusdruck = typVonAusdruck(argumente[i].wert?: AST.Ausdruck.Variable(null, argumente[i].name), variablen)
      print("${argumente[i].name.nominativ} :${parameterTypen[i]}")
      if (typVonAusdruck != parameterTypen[i]) {
        throw GermanScriptFehler.TypFehler(holeErstesTokenVonAusdruck(
            argumente[i].wert ?: AST.Ausdruck.Variable(null, argumente[i].name)), parameterTypen[i])
      }
    }
    println("]")
    println()
    return funktionsDefinition.rückgabeTyp?.typ
  }

  private fun holeErstesTokenVonAusdruck(ausdruck: AST.Ausdruck): Token {
    return when (ausdruck) {
      is AST.Ausdruck.Zeichenfolge -> ausdruck.zeichenfolge.toUntyped()
      is AST.Ausdruck.Zahl -> ausdruck.zahl.toUntyped()
      is AST.Ausdruck.Boolean -> ausdruck.boolean.toUntyped()
      is AST.Ausdruck.Variable -> ausdruck.name.bezeichner.toUntyped()
      is AST.Ausdruck.FunktionsAufruf -> ausdruck.aufruf.verb.toUntyped()
      is AST.Ausdruck.BinärerAusdruck -> holeErstesTokenVonAusdruck(ausdruck.links)
      is AST.Ausdruck.Minus -> holeErstesTokenVonAusdruck(ausdruck.ausdruck)
    }
  }

  private fun prüfeVariable(ausdruck: AST.Ausdruck.Variable, variablen: HashMap<String, Typ>): Typ {
    val nominativ = ausdruck.name.nominativ!!
    if (!variablen.containsKey(nominativ)) {
      throw GermanScriptFehler.Undefiniert.Variable(ausdruck.name.bezeichner.toUntyped())
    }
    return variablen.getValue(nominativ)
  }

  private fun prüfeBinärenAusdruck(ausdruck: AST.Ausdruck.BinärerAusdruck, variablen: HashMap<String, Typ>): Typ {
    val linkerTyp = typVonAusdruck(ausdruck.links, variablen)
    val operator = ausdruck.operator.typ.operator
    if (!linkerTyp.definierteOperatoren.containsKey(operator)) {
      throw GermanScriptFehler.Undefiniert.Operator(ausdruck.operator.toUntyped())
    }
    val rechterTyp = typVonAusdruck(ausdruck.rechts, variablen)
    print("($linkerTyp $operator $rechterTyp)")
    if (linkerTyp != rechterTyp) {
      throw Error("Operatoren funktionieren nur für gleiche Typen")//TODO(Operatorfehler)
    }
    return linkerTyp.definierteOperatoren.getValue(operator)
  }

  private fun prüfeMinus(ausdruck: AST.Ausdruck.Minus, variablen: HashMap<String, Typ>): Typ {
    val typ = typVonAusdruck(ausdruck, variablen)
    if (typ != Typ.Zahl) {
      throw Error("Minus nur für Zahlen definiert.")//TODO("Welcher Token?")
    }
    return Typ.Zahl
  }
}

fun main() {
  val typPrüfer = TypPrüfer("./iterationen/iter_0/code.gms")
  typPrüfer.prüfe()
  typPrüfer.logger.print()
}