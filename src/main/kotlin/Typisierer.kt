import util.SimpleLogger

sealed class Typ(val name: String) {
  override fun toString(): String = name
  val logger = SimpleLogger()

  abstract val definierteOperatoren: Map<Operator, Typ>
  abstract fun kannNachTypKonvertiertWerden(typ: Typ): kotlin.Boolean

  object Zahl : Typ("Zahl") {
    override val definierteOperatoren: Map<Operator, Typ> = mapOf(
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

    override fun kannNachTypKonvertiertWerden(typ: Typ) = typ == Zahl || typ == Zeichenfolge || typ == Boolean
  }

  object Zeichenfolge : Typ("Zeichenfolge") {
    override val definierteOperatoren: Map<Operator, Typ> = mapOf(
          Operator.PLUS to Zeichenfolge,
          Operator.GLEICH to Boolean,
          Operator.UNGLEICH to Boolean,
          Operator.GRÖßER to Boolean,
          Operator.KLEINER to Boolean,
          Operator.GRÖSSER_GLEICH to Boolean,
          Operator.KLEINER_GLEICH to Boolean
      )
    override fun kannNachTypKonvertiertWerden(typ: Typ) = typ == Zeichenfolge || typ == Zahl || typ == Boolean
  }

  object Boolean : Typ("Boolean") {
    override val definierteOperatoren: Map<Operator, Typ> = mapOf(
          Operator.UND to Boolean,
          Operator.ODER to Boolean,
          Operator.GLEICH to Boolean,
          Operator.UNGLEICH to Boolean
      )

    override fun kannNachTypKonvertiertWerden(typ: Typ) = typ == Boolean || typ == Zeichenfolge || typ == Zahl
  }

  data class Liste(val elementTyp: Typ) : Typ("Liste($elementTyp)") {

    // Das hier muss umbedingt ein Getter sein, sonst gibt es Probleme mit StackOverflow
    override val definierteOperatoren: Map<Operator, Typ> get() = mapOf(Operator.PLUS to Liste(elementTyp))
    override fun kannNachTypKonvertiertWerden(typ: Typ) = typ == this
  }

  data class Klasse(val klassenDefinition: AST.Definition.Klasse):
      Typ(klassenDefinition.typ.name.hauptWort(Kasus.NOMINATIV, Numerus.SINGULAR)) {
    override val definierteOperatoren: Map<Operator, Typ> = mapOf()

      override fun kannNachTypKonvertiertWerden(typ: Typ): kotlin.Boolean {
        return typ.name == this.name || typ == Zeichenfolge || klassenDefinition.konvertierungen.containsKey(typ.name)
      }
  }
}

class Typisierer(dateiPfad: String): PipelineKomponente(dateiPfad) {
  val definierer = Definierer(dateiPfad)
  val ast = definierer.ast

  fun typisiere() {
    definierer.definiere()
    definierer.funktionsDefinitionen.forEach(::typisiereFunktion)
    definierer.klassenDefinitionen.forEach(::typisiereKlasse)
  }

  fun bestimmeTypen(nomen: AST.Nomen): Typ {
    val singularTyp = when(nomen.hauptWort(Kasus.NOMINATIV, Numerus.SINGULAR)) {
      "Zahl" -> Typ.Zahl
      "Zeichenfolge" -> Typ.Zeichenfolge
      "Boolean" -> Typ.Boolean
      else -> Typ.Klasse(definierer.holeKlassenDefinition(nomen))
    }
    return if (nomen.numerus == Numerus.SINGULAR) {
      singularTyp
    } else {
      Typ.Liste(singularTyp)
    }
   }

  fun typisiereTypKnoten(typKnoten: AST.TypKnoten?) {
    if (typKnoten != null) {
      typKnoten.typ = bestimmeTypen(typKnoten.name)
    }
  }

  private fun typisiereFunktion(funktion: AST.Definition.FunktionOderMethode.Funktion) {
    typisiereTypKnoten(funktion.rückgabeTyp)
    typisiereTypKnoten(funktion.objekt?.typKnoten)
    for (parameter in funktion.parameter) {
      typisiereTypKnoten(parameter.typKnoten)
    }
  }

  private fun typisiereKlasse(klasse: AST.Definition.Klasse) {
    typisiereTypKnoten(klasse.typ)
    for (eigenschaft in klasse.eigenschaften) {
      typisiereTypKnoten(eigenschaft.typKnoten)
    }
    klasse.methoden.values.forEach{ methode ->
      methode.klasse.typ = Typ.Klasse(klasse)
      typisiereFunktion(methode.funktion)
    }
    klasse.konvertierungen.values.forEach { konvertierung ->
      typisiereTypKnoten(konvertierung.typ)
    }
  }
}

fun main() {
  val typisierer = Typisierer("./iterationen/iter_2/code.gms")
  typisierer.typisiere()
}