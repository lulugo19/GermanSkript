package germanskript

import germanskript.util.SimpleLogger
import java.io.File


sealed class Typ(val name: String) {
  override fun toString(): String = name
  val logger = SimpleLogger()

  abstract val definierteOperatoren: Map<Operator, Typ>
  abstract fun kannNachTypKonvertiertWerden(typ: Typ): kotlin.Boolean

  object Zahl : Typ("Zahl") {
    override val definierteOperatoren: Map<Operator, Typ> = mapOf(
          Operator.PLUS to Zahl,
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

  object Generic : Typ("Generic") {
    override val definierteOperatoren: Map<Operator, Typ>
      get() = mapOf()

    override fun kannNachTypKonvertiertWerden(typ: Typ): kotlin.Boolean = false
  }

  data class Schnittstelle(val definition: AST.Definition.Typdefinition.Schnittstelle): Typ(definition.name.wert.capitalize()) {
    override val definierteOperatoren: Map<Operator, Typ> = mapOf(
        Operator.GLEICH to Boolean
    )

    override fun kannNachTypKonvertiertWerden(typ: Typ): kotlin.Boolean {
      return typ.name == this.name || typ == Zeichenfolge
    }
  }

  sealed class KlassenTyp(name: String): Typ(name) {
    abstract val klassenDefinition: AST.Definition.Typdefinition.Klasse

    data class Klasse(override val klassenDefinition: AST.Definition.Typdefinition.Klasse):
        KlassenTyp(klassenDefinition.typ.name.hauptWort(Kasus.NOMINATIV, Numerus.SINGULAR)) {
        override val definierteOperatoren: Map<Operator, Typ> = mapOf(
            Operator.GLEICH to Boolean
        )

        override fun kannNachTypKonvertiertWerden(typ: Typ): kotlin.Boolean {
          return typ.name == this.name || typ == Zeichenfolge || klassenDefinition.konvertierungen.containsKey(typ.name)
        }
    }

    data class Liste(override val klassenDefinition: AST.Definition.Typdefinition.Klasse, val elementTyp: Typ) : KlassenTyp("Liste($elementTyp)") {
      // Das hier muss umbedingt ein Getter sein, sonst gibt es Probleme mit StackOverflow
      override val definierteOperatoren: Map<Operator, Typ> get() = mapOf(
          Operator.PLUS to Liste(klassenDefinition, elementTyp),
          Operator.GLEICH to Boolean
      )
      override fun kannNachTypKonvertiertWerden(typ: Typ) = typ.name == this.name || typ == Zeichenfolge
    }
  }
}

class Typisierer(startDatei: File): PipelineKomponente(startDatei) {
  val definierer = Definierer(startDatei)
  val ast = definierer.ast
  private var _listenKlassenDefinition: AST.Definition.Typdefinition.Klasse? = null
  val listenKlassenDefinition get() = _listenKlassenDefinition!!

  fun typisiere() {
    definierer.definiere()
    _listenKlassenDefinition = definierer.holeTypDefinition("Liste") as AST.Definition.Typdefinition.Klasse
    definierer.funktionsDefinitionen.forEach { typisiereFunktionsSignatur(it.signatur)}
    definierer.typDefinitionen.forEach {typDefinition ->
      when (typDefinition) {
        is AST.Definition.Typdefinition.Schnittstelle ->
          typDefinition.methodenSignaturen.forEach(::typisiereFunktionsSignatur)
        is AST.Definition.Typdefinition.Alias -> bestimmeTypen(typDefinition.typ, false)
      }
    }
  }

  fun bestimmeTypen(nomen: AST.Nomen): Typ {
    val typKnoten = AST.TypKnoten(nomen, emptyList())
    // setze den Parent hier manuell vom Nomen
    typKnoten.setParentNode(nomen.parent!!)
    return bestimmeTypen(typKnoten, true)!!
  }

  private fun holeTypDefinition(typKnoten: AST.TypKnoten, aliasErlaubt: Boolean): Typ =
    when(typKnoten.name.hauptWort(Kasus.NOMINATIV, Numerus.SINGULAR)) {
      "Zahl" -> Typ.Zahl
      "Zeichenfolge" -> Typ.Zeichenfolge
      "Boolean" -> Typ.Boolean
      "Typ" -> Typ.Generic
      "Liste" -> Typ.KlassenTyp.Liste(listenKlassenDefinition, Typ.Generic)
      else -> when (val typDef = definierer.holeTypDefinition(typKnoten)) {
        is AST.Definition.Typdefinition.Klasse -> Typ.KlassenTyp.Klasse(typDef)
        is AST.Definition.Typdefinition.Schnittstelle -> Typ.Schnittstelle(typDef)
        is AST.Definition.Typdefinition.Alias -> {
          if (!aliasErlaubt) {
            throw GermanSkriptFehler.AliasFehler(typKnoten.name.bezeichner.toUntyped(), typDef)
          }
          holeTypDefinition(typDef.typ, false)
        }
      }
    }


  fun bestimmeTypen(typKnoten: AST.TypKnoten?, istAliasErlaubt: Boolean): Typ? {
    if (typKnoten == null) {
      return null
    }
    val singularTyp = holeTypDefinition(typKnoten, istAliasErlaubt)
    typKnoten.typ = if (typKnoten.name.numerus == Numerus.SINGULAR) {
      singularTyp
    } else {
      Typ.KlassenTyp.Liste(listenKlassenDefinition, singularTyp)
    }
    return typKnoten.typ
   }

  private fun typisiereFunktionsSignatur(signatur: AST.Definition.FunktionsSignatur) {
    bestimmeTypen(signatur.rückgabeTyp, true)
    bestimmeTypen(signatur.objekt?.typKnoten, true)
    for (parameter in signatur.parameter) {
      bestimmeTypen(parameter.typKnoten, true)
    }
  }

  fun typisiereKlasse(klasse: AST.Definition.Typdefinition.Klasse) {
    bestimmeTypen(klasse.typ, true)
    for (eigenschaft in klasse.eigenschaften) {
      bestimmeTypen(eigenschaft.typKnoten, true)
    }
    klasse.methoden.values.forEach { methode ->
      methode.klasse.typ = Typ.KlassenTyp.Klasse(klasse)
      typisiereFunktionsSignatur(methode.funktion.signatur)
    }
    klasse.konvertierungen.values.forEach { konvertierung ->
      bestimmeTypen(konvertierung.typ, true)
    }
    klasse.berechneteEigenschaften.values.forEach {eigenschaft ->
      bestimmeTypen(eigenschaft.rückgabeTyp, true)
    }
  }
}

fun main() {
  val typisierer = Typisierer(File("./iterationen/iter_2/code.gms"))
  typisierer.typisiere()
}