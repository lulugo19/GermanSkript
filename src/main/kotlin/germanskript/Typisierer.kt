package germanskript
import java.io.File
import java.util.*


sealed class Typ() {
  abstract val name: String
  override fun toString(): String = name

  abstract val definierteOperatoren: Map<Operator, Typ>
  abstract fun kannNachTypKonvertiertWerden(typ: Typ): Boolean

  sealed class Primitiv(override val name: String): Typ() {
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

  object Nichts: Typ() {
    override val name = "Nichts"
    override val definierteOperatoren: Map<Operator, Typ> = mapOf()
    override fun kannNachTypKonvertiertWerden(typ: Typ) = false
  }

  data class Generic(val index: Int, val kontext: TypParamKontext) : Typ() {
    override val name = "Generic"
    override val definierteOperatoren: Map<Operator, Typ>
      get() = mapOf()

    override fun kannNachTypKonvertiertWerden(typ: Typ): Boolean = false
  }

  sealed class Compound(private val typName: String): Typ() {
    abstract val definition: AST.Definition.Typdefinition
    abstract var typArgumente: List<AST.TypKnoten>
    override val name get() = typName + if (typArgumente.isEmpty()) "" else "<${typArgumente.joinToString(", ") {it.name.nominativ}}>"

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is Compound) return false
      // TODO: Ist das wirklich richtig so? Mit den Generics...?
      return this.definition == other.definition && this.typArgumente.size == other.typArgumente.size
          && this.typArgumente.zip(other.typArgumente).all { (a, b) ->
        a.typ is Generic && b.typ !is Generic ||
        a.typ !is Generic && b.typ is Generic ||
            a.typ == b.typ
      }
    }

    sealed class KlassenTyp(name: String): Compound(name) {
      abstract override val definition: AST.Definition.Typdefinition.Klasse

      class Klasse(
          override val definition: AST.Definition.Typdefinition.Klasse,
          override var typArgumente: List<AST.TypKnoten>
      ): KlassenTyp(definition.name.hauptWort(Kasus.NOMINATIV, Numerus.SINGULAR)) {
        override val definierteOperatoren: Map<Operator, Typ> = mapOf(
            Operator.GLEICH to Primitiv.Boolean
        )

        override fun kannNachTypKonvertiertWerden(typ: Typ): Boolean {
          return typ.name == this.name || typ == Primitiv.Zeichenfolge || definition.konvertierungen.containsKey(typ.name)
        }
      }

      class Liste(
          override val definition: AST.Definition.Typdefinition.Klasse,
          override var typArgumente: List<AST.TypKnoten>
      ) : KlassenTyp("Liste") {
        // Das hier muss umbedingt ein Getter sein, sonst gibt es Probleme mit StackOverflow
        override val definierteOperatoren: Map<Operator, Typ> get() = mapOf(
            Operator.PLUS to Liste(definition, typArgumente),
            Operator.GLEICH to Primitiv.Boolean
        )
        override fun kannNachTypKonvertiertWerden(typ: Typ) = typ.name == this.name || typ == Primitiv.Zeichenfolge
      }
    }

    class Schnittstelle(
        override val definition: AST.Definition.Typdefinition.Schnittstelle,
        override var typArgumente: List<AST.TypKnoten>
    ): Compound(definition.name.wert.capitalize()) {
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
        is AST.Definition.Typdefinition.Alias -> bestimmeTyp(typDefinition.typ, null, null, false)
      }
    }
  }

  fun bestimmeTyp(nomen: AST.WortArt.Nomen): Typ {
    val typKnoten = AST.TypKnoten(emptyList(), nomen, emptyList())
    // setze den Parent hier manuell vom Nomen
    typKnoten.setParentNode(nomen.parent!!)
    return bestimmeTyp(typKnoten, null, null, true)!!
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
              throw GermanSkriptFehler.AliasFehler(typKnoten.name.bezeichnerToken, typDef)
            }
            holeTypDefinition(typDef.typ, null, null, false)
          }
        }
      }
    }
    // Überprüfe hier die Anzahl der Typargumente
    when (typ) {
      is Typ.Primitiv, is Typ.Generic -> if (typArgumente.isNotEmpty())
        throw GermanSkriptFehler.TypArgumentFehler(typKnoten.name.bezeichnerToken, typArgumente.size, 0)
      is Typ.Compound -> if (typArgumente.isNotEmpty() && typArgumente.size != typ.definition.typParameter.size)
        throw GermanSkriptFehler.TypArgumentFehler(
            typKnoten.name.bezeichnerToken,
            typArgumente.size,
            typ.definition.typParameter.size
        )
    }
    return typ
  }


  fun bestimmeTyp(typKnoten: AST.TypKnoten?, funktionsTypParameter: TypParameter?, typTypParameter: TypParameter?, istAliasErlaubt: Boolean): Typ? {
    if (typKnoten == null) {
      return null
    }
    typKnoten.typArgumente.forEach {typKnoten -> bestimmeTyp(typKnoten, funktionsTypParameter, typTypParameter, true)}
    val singularTyp = holeTypDefinition(typKnoten, funktionsTypParameter, typTypParameter, istAliasErlaubt)
    typKnoten.typ = if (typKnoten.name.numerus == Numerus.SINGULAR) {
      singularTyp
    } else {
      // für das Typargument der Liste brauchen wir einen Typknoten, der der Singular von der Liste ist
      val nomen = when (typKnoten.name) {
        is AST.WortArt.Nomen -> typKnoten.name.copy()
        is AST.WortArt.Adjektiv -> typKnoten.name.copy()
      }
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
    bestimmeTyp(signatur.rückgabeTyp, signatur.typParameter, typTypParameter, true)
    bestimmeTyp(signatur.objekt?.typKnoten, signatur.typParameter, typTypParameter, true)
    for (parameter in signatur.parameter) {
      bestimmeTyp(parameter.typKnoten, signatur.typParameter, typTypParameter, true)
    }
  }

  fun typisiereKlasse(klasse: AST.Definition.Typdefinition.Klasse) {
    for (eigenschaft in klasse.eigenschaften) {
      bestimmeTyp(eigenschaft.typKnoten, null, klasse.typParameter, true)
    }
    klasse.methoden.values.forEach { methode ->
      //methode.klasse.typ = Typ.KlassenTyp.Klasse(klasse)
      typisiereFunktionsSignatur(methode.signatur, klasse.typParameter)
    }
    klasse.konvertierungen.values.forEach { konvertierung ->
      bestimmeTyp(konvertierung.typ, null, null, true)
    }
    klasse.berechneteEigenschaften.values.forEach {eigenschaft ->
      bestimmeTyp(eigenschaft.rückgabeTyp, klasse.typParameter, null, true)
    }
    klasse.implementierungen.forEach { implementierung ->
      implementierung.schnittstellen.forEach { schnittstelle ->
        schnittstelle.typArgumente.forEach {arg -> bestimmeTyp(arg, null, klasse.typParameter, true)}
        prüfeImplementiertSchnittstelle(klasse, schnittstelle)
      }
    }
  }

  private fun prüfeImplementiertSchnittstelle(
      klasse: AST.Definition.Typdefinition.Klasse,
      adjektiv: AST.TypKnoten) {

    val schnittstelle = holeTypDefinition(adjektiv, null, klasse.typParameter, true)
    if (schnittstelle !is Typ.Compound.Schnittstelle) {
      throw GermanSkriptFehler.SchnittstelleErwartet(adjektiv.name.bezeichnerToken)
    }

    fun holeErwartetenTypen(schnittstellenParam: AST.Definition.Parameter): Typ {
      return when (val typ = schnittstellenParam.typKnoten.typ!!) {
        is Typ.Generic -> when (typ.kontext) {
          TypParamKontext.Typ -> schnittstelle.typArgumente[typ.index].typ!!
          TypParamKontext.Funktion -> typ
        }
        else -> typ
      }
    }

    for (signatur in schnittstelle.definition.methodenSignaturen) {
      val methodenName = definierer.holeVollenNamenVonFunktionsSignatur(signatur, schnittstelle.typArgumente)
      val methode = klasse.methoden.getOrElse(methodenName, {
        throw GermanSkriptFehler.UnimplementierteSchnittstelle(
            adjektiv.name.bezeichnerToken,
            klasse,
            schnittstelle
        )
      })
      // überprüfe Rückgabetypen
      if (signatur.rückgabeTyp == null && methode.signatur.rückgabeTyp != null) {
        throw GermanSkriptFehler.TypFehler.FalscherSchnittstellenTyp(
            methode.signatur.rückgabeTyp.name.bezeichnerToken,
            schnittstelle,
            methodenName,
            methode.signatur.rückgabeTyp.typ!!,
            Typ.Nichts
        )
      }

      if (signatur.rückgabeTyp != null && methode.signatur.rückgabeTyp == null) {
        throw GermanSkriptFehler.TypFehler.FalscherSchnittstellenTyp(
            methode.signatur.name.toUntyped(),
            schnittstelle,
            methodenName,
            Typ.Nichts,
            signatur.rückgabeTyp.typ!!
        )
      }

      if (signatur.rückgabeTyp != methode.signatur.rückgabeTyp) {
        throw GermanSkriptFehler.TypFehler.FalscherSchnittstellenTyp(
            methode.signatur.rückgabeTyp!!.name.bezeichnerToken,
            schnittstelle,
            methodenName,
            methode.signatur.rückgabeTyp.typ!!,
            signatur.rückgabeTyp!!.typ!!
        )
      }

      // überprüfe Parameter
      val schnittstellenParameter = signatur.parameter
      val methodenParameter = methode.signatur.parameter
      for (pIndex in methodenParameter.indices) {
        val erwarteterTyp = holeErwartetenTypen(schnittstellenParameter[pIndex])
        if (methodenParameter[pIndex].typKnoten.typ != erwarteterTyp) {
          throw GermanSkriptFehler.TypFehler.FalscherSchnittstellenTyp(
              methodenParameter[pIndex].typKnoten.name.bezeichnerToken,
              schnittstelle,
              methodenName,
              methodenParameter[pIndex].typKnoten.typ!!,
              erwarteterTyp
          )
        }
      }
      // füge die Schnittstelle als implementierte Schnittstelle für die Klasse hinzu
      klasse.implementierteSchnittstellen += schnittstelle
    }
  }

  private fun typisiereSchnittstelle(schnittstelle: AST.Definition.Typdefinition.Schnittstelle) {
    schnittstelle.methodenSignaturen.forEach { signatur ->
      typisiereFunktionsSignatur(signatur, schnittstelle.typParameter)
    }
  }
}

fun main() {
  val typisierer = Typisierer(File("./iterationen/iter_2/code.gms"))
  typisierer.typisiere()
}