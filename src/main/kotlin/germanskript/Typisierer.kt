package germanskript
import java.io.File
import java.util.*


sealed class Typ(val name: String) {
  override fun toString(): String = name

  abstract val definierteOperatoren: Map<Operator, Typ>
  abstract fun kannNachTypKonvertiertWerden(typ: Typ): Boolean

  sealed class Primitiv(name: String): Typ(name) {
    object Zahl : Primitiv("Zahl") {
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

    object Zeichenfolge : Primitiv("Zeichenfolge") {
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

    object Boolean : Primitiv("Boolean") {
      override val definierteOperatoren: Map<Operator, Typ> = mapOf(
            Operator.UND to Boolean,
            Operator.ODER to Boolean,
            Operator.GLEICH to Boolean,
            Operator.UNGLEICH to Boolean
        )

      override fun kannNachTypKonvertiertWerden(typ: Typ) = typ == Boolean || typ == Zeichenfolge || typ == Zahl
    }

  }

  data class Generic(val index: Int, val kontext: TypParamKontext) : Typ("Generic") {
    override val definierteOperatoren: Map<Operator, Typ>
      get() = mapOf()

    override fun kannNachTypKonvertiertWerden(typ: Typ): Boolean = false
  }

  sealed class Compound(name: String): Typ(name) {
    abstract val definition: AST.Definition.Typdefinition
    abstract val typArgumente: List<AST.TypKnoten>

    sealed class KlassenTyp(name: String): Compound(name) {
      abstract override val definition: AST.Definition.Typdefinition.Klasse

      data class Klasse(
          override val definition: AST.Definition.Typdefinition.Klasse,
          override val typArgumente: List<AST.TypKnoten>
      ): KlassenTyp(definition.name.hauptWort(Kasus.NOMINATIV, Numerus.SINGULAR) + "<${typArgumente.joinToString(", ")}>") {
        override val definierteOperatoren: Map<Operator, Typ> = mapOf(
            Operator.GLEICH to Primitiv.Boolean
        )

        override fun kannNachTypKonvertiertWerden(typ: Typ): Boolean {
          return typ.name == this.name || typ == Primitiv.Zeichenfolge || definition.konvertierungen.containsKey(typ.name)
        }
      }

      data class Liste(
          override val definition: AST.Definition.Typdefinition.Klasse,
          override val typArgumente: List<AST.TypKnoten>
      ) : KlassenTyp("Liste<${typArgumente[0]}>") {
        // Das hier muss umbedingt ein Getter sein, sonst gibt es Probleme mit StackOverflow
        override val definierteOperatoren: Map<Operator, Typ> get() = mapOf(
            Operator.PLUS to Liste(definition, typArgumente),
            Operator.GLEICH to Primitiv.Boolean
        )
        override fun kannNachTypKonvertiertWerden(typ: Typ) = typ.name == this.name || typ == Primitiv.Zeichenfolge
      }
    }

    data class Schnittstelle(
        override val definition: AST.Definition.Typdefinition.Schnittstelle,
        override val typArgumente: List<AST.TypKnoten>
    ): Compound(definition.name.wert.capitalize() + "<${typArgumente.joinToString(", ")}>") {
      override val definierteOperatoren: Map<Operator, Typ> = mapOf(
          Operator.GLEICH to Primitiv.Boolean
      )

      override fun kannNachTypKonvertiertWerden(typ: Typ): Boolean {
        return typ.name == this.name || typ == Primitiv.Zeichenfolge
      }
    }
  }
}

enum class TypParamKontext {
  Typ,
  Funktion,
}

class Typisierer(startDatei: File): PipelineKomponente(startDatei) {
  val definierer = Definierer(startDatei)
  val ast = definierer.ast
  private var _listenKlassenDefinition: AST.Definition.Typdefinition.Klasse? = null
  val listenKlassenDefinition get() = _listenKlassenDefinition!!

  fun typisiere() {
    definierer.definiere()
    _listenKlassenDefinition = definierer.holeTypDefinition("Liste") as AST.Definition.Typdefinition.Klasse
    definierer.funktionsDefinitionen.forEach { typisiereFunktionsSignatur(it.signatur, null)}
    definierer.typDefinitionen.forEach {typDefinition ->
      // Klassen werden von Typprüfer typisiert, da die Reihenfolge und die Konstruktoren eine wichtige Rolle spielen
      when (typDefinition) {
        is AST.Definition.Typdefinition.Schnittstelle -> typisiereSchnittstelle(typDefinition)
        is AST.Definition.Typdefinition.Alias -> bestimmeTypen(typDefinition.typ, null, null, false)
      }
    }
  }

  fun bestimmeTypen(nomen: AST.Nomen): Typ {
    val typKnoten = AST.TypKnoten(emptyList(), nomen, emptyList())
    // setze den Parent hier manuell vom Nomen
    typKnoten.setParentNode(nomen.parent!!)
    return bestimmeTypen(typKnoten, null, null, true)!!
  }

  private fun holeTypDefinition(typKnoten: AST.TypKnoten, funktionsTypParams: TypParameter?, typTypParams: TypParameter?, aliasErlaubt: Boolean): Typ {
    val typArgumente = typKnoten.typArgumente
    val typName = typKnoten.name.hauptWort(Kasus.NOMINATIV, Numerus.SINGULAR)

    var typ: Typ? = null
    if (funktionsTypParams != null ) {
      val funktionTypParamIndex = funktionsTypParams.indexOfFirst { param -> param.hauptWort(Kasus.NOMINATIV, Numerus.SINGULAR) == typName }
      if (funktionTypParamIndex != -1) {
        typ = Typ.Generic(funktionTypParamIndex, TypParamKontext.Funktion)
      }
    }
    if (typ == null && typTypParams != null) {
      val typParamIndex = typTypParams.indexOfFirst { param -> param.hauptWort(Kasus.NOMINATIV, Numerus.SINGULAR) == typName }
      if (typParamIndex != -1) {
        typ = Typ.Generic(typParamIndex, TypParamKontext.Typ)
      }
    }

    if (typ == null) {
      typ = when (typName) {
        "Zahl" -> Typ.Primitiv.Zahl
        "Zeichenfolge" -> Typ.Primitiv.Zeichenfolge
        "Boolean" -> Typ.Primitiv.Boolean
        else -> when (val typDef = definierer.holeTypDefinition(typKnoten)) {
          is AST.Definition.Typdefinition.Klasse -> Typ.Compound.KlassenTyp.Klasse(typDef, typArgumente)
          is AST.Definition.Typdefinition.Schnittstelle -> Typ.Compound.Schnittstelle(typDef, typArgumente)
          is AST.Definition.Typdefinition.Alias -> {
            if (!aliasErlaubt) {
              throw GermanSkriptFehler.AliasFehler(typKnoten.name.bezeichner.toUntyped(), typDef)
            }
            holeTypDefinition(typDef.typ, null, null, false)
          }
        }
      }
    }
    // Überprüfe hier die Anzahl der Typargumente
    when (typ) {
      is Typ.Primitiv, is Typ.Generic -> if (typArgumente.isNotEmpty())
        throw GermanSkriptFehler.TypArgumentFehler(typKnoten.name.bezeichner.toUntyped(), typArgumente.size, 0)
      is Typ.Compound.KlassenTyp -> if (typArgumente.size != typ.definition.typParameter.size)
        throw GermanSkriptFehler.TypArgumentFehler(
            typKnoten.name.bezeichner.toUntyped(),
            typArgumente.size,
            typ.definition.typParameter.size
        )
      is Typ.Compound.Schnittstelle -> if (typArgumente.size != typ.definition.typParameter.size)
        throw GermanSkriptFehler.TypArgumentFehler(
            typKnoten.name.bezeichner.toUntyped(),
            typArgumente.size,
            typ.definition.typParameter.size
        )
    }
    return typ
  }


  fun bestimmeTypen(typKnoten: AST.TypKnoten?, funktionsTypParameter: TypParameter?, typTypParameter: TypParameter?, istAliasErlaubt: Boolean): Typ? {
    if (typKnoten == null) {
      return null
    }
    typKnoten.typArgumente.forEach {typKnoten -> bestimmeTypen(typKnoten, funktionsTypParameter, typTypParameter, true)}
    val singularTyp = holeTypDefinition(typKnoten, funktionsTypParameter, typTypParameter, istAliasErlaubt)
    typKnoten.typ = if (typKnoten.name.numerus == Numerus.SINGULAR) {
      singularTyp
    } else {
      val nomen = typKnoten.name.copy()
      nomen.numerus = Numerus.SINGULAR
      nomen.deklination = typKnoten.name.deklination
      nomen.fälle = EnumSet.of(Kasus.NOMINATIV)
      val singularTypKnoten = AST.TypKnoten(typKnoten.modulPfad, nomen, emptyList())
      singularTypKnoten.typ = singularTyp
      Typ.Compound.KlassenTyp.Liste(listenKlassenDefinition, listOf(singularTypKnoten))
    }
    return typKnoten.typ
   }

  private fun typisiereFunktionsSignatur(signatur: AST.Definition.FunktionsSignatur, typTypParameter: TypParameter?) {
    // kombiniere die Typparameter
    bestimmeTypen(signatur.rückgabeTyp, signatur.typParameter, typTypParameter, true)
    bestimmeTypen(signatur.objekt?.typKnoten, signatur.typParameter, typTypParameter, true)
    for (parameter in signatur.parameter) {
      bestimmeTypen(parameter.typKnoten, signatur.typParameter, typTypParameter, true)
    }
  }

  fun typisiereKlasse(klasse: AST.Definition.Typdefinition.Klasse) {
    for (eigenschaft in klasse.eigenschaften) {
      bestimmeTypen(eigenschaft.typKnoten, null, klasse.typParameter, true)
    }
    klasse.methoden.values.forEach { methode ->
      //methode.klasse.typ = Typ.KlassenTyp.Klasse(klasse)
      typisiereFunktionsSignatur(methode.funktion.signatur, klasse.typParameter)
    }
    klasse.konvertierungen.values.forEach { konvertierung ->
      bestimmeTypen(konvertierung.typ, null, null, true)
    }
    klasse.berechneteEigenschaften.values.forEach {eigenschaft ->
      bestimmeTypen(eigenschaft.rückgabeTyp, klasse.typParameter, null, true)
    }
  }

  fun typisiereSchnittstelle(schnittstelle: AST.Definition.Typdefinition.Schnittstelle) {
    schnittstelle.methodenSignaturen.forEach { signatur ->
      typisiereFunktionsSignatur(signatur, schnittstelle.typParameter)
    }
  }
}

fun main() {
  val typisierer = Typisierer(File("./iterationen/iter_2/code.gms"))
  typisierer.typisiere()
}