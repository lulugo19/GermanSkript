import util.SimpleLogger

sealed class Typ(val name: String) {
  override fun toString(): String = name
  val logger = SimpleLogger()

  abstract val definierteOperatoren: Map<Operator, Typ>

  object Zahl : Typ("Zahl") {
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
    override val definierteOperatoren: Map<Operator, Typ>
      get() = mapOf(
          Operator.UND to Boolean,
          Operator.ODER to Boolean,
          Operator.GLEICH to Boolean,
          Operator.UNGLEICH to Boolean
      )
  }

  data class Liste(val elementTyp: Typ) : Typ("Liste($elementTyp)") {
    override val definierteOperatoren: Map<Operator, Typ>
      get() = mapOf(
          Operator.PLUS to Liste(elementTyp)
      )
  }
}

class Typisierer(dateiPfad: String): PipelineKomponente(dateiPfad) {
  val definierer = Definierer(dateiPfad)
  val ast = definierer.ast

  fun typisiere() {
    definierer.definiere()
    definierer.funktionsDefinitionen.forEach(::typisiereFunktion)
  }

  fun bestimmeTypen(nomen: AST.Nomen): Typ {
    val singularTyp = bestimmeTypen(nomen.nominativSingular!!)?: throw GermanScriptFehler.Undefiniert.Typ(nomen.bezeichner.toUntyped())
    return if (nomen.numerus == Numerus.SINGULAR) {
      singularTyp
    } else {
      Typ.Liste(singularTyp)
    }
   }

  private fun bestimmeTypen(typ: String): Typ? {
    return when(typ) {
      "Zahl" -> Typ.Zahl
      "Zeichenfolge"  -> Typ.Zeichenfolge
      "Boolean" -> Typ.Boolean
      "Zahlen" -> Typ.Liste(Typ.Zahl)
      "Zeichenfolgen" -> Typ.Liste(Typ.Zeichenfolge)
      "Booleans" -> Typ.Liste(Typ.Boolean)
      else -> null
    }
  }

  private fun typisiereTypKnoten(typKnoten: AST.TypKnoten?) {
    if (typKnoten != null) {
      typKnoten.typ = bestimmeTypen(typKnoten.name)
    }
  }

  private fun typisiereFunktion(funktion: AST.Definition.Funktion) {
    typisiereTypKnoten(funktion.rückgabeTyp)
    typisiereTypKnoten(funktion.objekt?.typKnoten)
    for (parameter in funktion.parameter) {
      typisiereTypKnoten(parameter.typKnoten)
    }
  }
}

fun main() {
  val typisierer = Typisierer("./iterationen/iter_1/code.gms")
  typisierer.typisiere()
}