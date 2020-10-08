package germanskript
import java.io.File
import java.util.*


sealed class Typ() {
  abstract val name: String
  override fun toString(): String = name

  abstract val definierteOperatoren: Map<Operator, Typ>
  abstract fun kannNachTypKonvertiertWerden(typ: Typ): Boolean
  abstract fun inTypKnoten(): AST.TypKnoten

  // äquivalent zu Kotlins Typen "Nothing"
  object Niemals: Typ() {
    override val name = "Niemals"
    override val definierteOperatoren: Map<Operator, Typ> = mapOf()
    override fun kannNachTypKonvertiertWerden(typ: Typ) = false

    private val typKnoten = AST.TypKnoten(
        emptyList(), AST.WortArt.Nomen(null, TypedToken.imaginäresToken(
        TokenTyp.BEZEICHNER_GROSS(arrayOf("Niemals"), "", null), "Niemals")), emptyList())

    init {
      typKnoten.name.numera.add(Numerus.SINGULAR)
      typKnoten.name.deklination = Deklination(
          Genus.NEUTRUM,
          arrayOf("Niemals", "Niemals", "Niemals", "Niemals"),
          arrayOf("Niemals", "Niemals", "Niemals", "Niemals")
      )
      typKnoten.typ = Niemals
    }

    override fun inTypKnoten(): AST.TypKnoten = typKnoten
  }

  class Generic(
      val typParam: AST.Definition.TypParam,
      val index: Int,
      val kontext: TypParamKontext
  ) : Typ() {
    override val name = "Generic"
    override val definierteOperatoren: Map<Operator, Typ>
      get() = mapOf()

    override fun kannNachTypKonvertiertWerden(typ: Typ): Boolean = false


    override fun inTypKnoten(): AST.TypKnoten {
      val typKnoten = AST.TypKnoten(emptyList(), typParam.binder, emptyList())
      typKnoten.typ = this
      return typKnoten
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is Generic) return false
      return this.index == other.index && this.kontext == other.kontext
    }
  }

  sealed class Compound(private val typName: String): Typ() {
    abstract val definition: AST.Definition.Typdefinition
    abstract var typArgumente: List<AST.TypKnoten>
    override val name get() = typName + if (typArgumente.isEmpty()) "" else "<${typArgumente.joinToString(", ") {it.name.nominativ}}>"

    abstract fun copy(typArgumente: List<AST.TypKnoten>): Compound

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is Compound) return false
      // TODO: Ist das wirklich richtig so? Mit den Generics...?
      return this.definition == other.definition && this.typArgumente.size == other.typArgumente.size
          && this.typArgumente.zip(other.typArgumente).all { (a, b) ->
        a.typ is Generic || b.typ is Generic || a.typ == b.typ
      }
    }

    override fun inTypKnoten(): AST.TypKnoten {
      val typKnoten = AST.TypKnoten(emptyList(), definition.name, typArgumente)
      typKnoten.typ = this
      return typKnoten
    }

    sealed class KlassenTyp(name: String): Compound(name) {
      abstract override val definition: AST.Definition.Typdefinition.Klasse

      class Klasse(
          override val definition: AST.Definition.Typdefinition.Klasse,
          override var typArgumente: List<AST.TypKnoten>
      ): KlassenTyp(definition.name.hauptWort(Kasus.NOMINATIV, Numerus.SINGULAR)) {
        override val definierteOperatoren: Map<Operator, Typ> = mapOf(
            Operator.GLEICH to BuildInType.Boolean
        )

        override fun kannNachTypKonvertiertWerden(typ: Typ): kotlin.Boolean {
          return typ.name == this.name || typ == BuildInType.Zeichenfolge || definition.konvertierungen.containsKey(typ.name)
        }

        override fun copy(typArgumente: List<AST.TypKnoten>): Compound {
          return Klasse(definition, typArgumente)
        }
      }

      class Liste(
          override var typArgumente: List<AST.TypKnoten>
      ) : KlassenTyp("Liste") {

        override val definition: AST.Definition.Typdefinition.Klasse
          get() = Liste.definition

        companion object {
          lateinit var definition: AST.Definition.Typdefinition.Klasse
        }

        // Das hier muss umbedingt ein Getter sein, sonst gibt es Probleme mit StackOverflow
        override val definierteOperatoren: Map<Operator, Typ> get() = mapOf(
            Operator.PLUS to Liste(typArgumente),
            Operator.GLEICH to BuildInType.Boolean
        )
        override fun kannNachTypKonvertiertWerden(typ: Typ) = typ.name == this.name || typ == BuildInType.Zeichenfolge

        override fun copy(typArgumente: List<AST.TypKnoten>): Compound {
          return Liste(typArgumente)
        }
      }

      sealed class BuildInType(name: String) : KlassenTyp(name) {

        // äquivalent zu Kotlins Typen "Unit"
        object Nichts: BuildInType("Nichts") {
          override lateinit var definition: AST.Definition.Typdefinition.Klasse

          override var typArgumente: List<AST.TypKnoten> = emptyList()

          override fun copy(typArgumente: List<AST.TypKnoten>): Compound {
            return Nichts
          }

          override val definierteOperatoren: Map<Operator, Typ> = mapOf()
          override fun kannNachTypKonvertiertWerden(typ: Typ) = false
        }

        object Zeichenfolge : BuildInType("Zeichenfolge") {
          override lateinit var definition: AST.Definition.Typdefinition.Klasse

          override var typArgumente: List<AST.TypKnoten> = emptyList()

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

          override fun copy(typArgumente: List<AST.TypKnoten>) = Zeichenfolge
        }

        object Boolean : BuildInType("Boolean") {
          override val definierteOperatoren: Map<Operator, Typ> = mapOf(
                Operator.UND to Boolean,
                Operator.ODER to Boolean,
                Operator.GLEICH to Boolean,
                Operator.UNGLEICH to Boolean
            )

          override fun kannNachTypKonvertiertWerden(typ: Typ) = typ == Boolean || typ == Zeichenfolge || typ == Zahl

          override lateinit var definition: AST.Definition.Typdefinition.Klasse

          override var typArgumente: List<AST.TypKnoten> = emptyList()

          override fun copy(typArgumente: List<AST.TypKnoten>) = Boolean
        }

        object Zahl : BuildInType("Zahl") {
          override lateinit var definition: AST.Definition.Typdefinition.Klasse

          override var typArgumente: List<AST.TypKnoten> = emptyList()

          override fun copy(typArgumente: List<AST.TypKnoten>) = Zahl

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
      }

    }

    class Schnittstelle(
        override val definition: AST.Definition.Typdefinition.Schnittstelle,
        override var typArgumente: List<AST.TypKnoten>
    ): Compound(definition.namensToken.wert.capitalize()) {
      override val definierteOperatoren: Map<Operator, Typ> = mapOf(
          Operator.GLEICH to KlassenTyp.BuildInType.Boolean
      )

      override fun kannNachTypKonvertiertWerden(typ: Typ): Boolean {
        return typ.name == this.name || typ == KlassenTyp.BuildInType.Zeichenfolge
      }

      override fun copy(typArgumente: List<AST.TypKnoten>): Compound {
        return Schnittstelle(definition, typArgumente)
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

  companion object {
    lateinit var schreiberTyp: Typ.Compound.KlassenTyp.Klasse
  }

  fun typisiere() {
    definierer.definiere()
    holeInterneTypDefinitionen()
    definierer.funktionsDefinitionen.forEach { typisiereFunktionsSignatur(it.signatur, null)}
    definierer.typDefinitionen.forEach {typDefinition ->
      // Klassen werden von Typprüfer typisiert, da die Reihenfolge und die Konstruktoren eine wichtige Rolle spielen
      when (typDefinition) {
        is AST.Definition.Typdefinition.Schnittstelle -> typisiereSchnittstelle(typDefinition)
        is AST.Definition.Typdefinition.Alias -> bestimmeTyp(typDefinition.typ, null, null, false)
      }
    }
  }

  private fun holeInterneTypDefinitionen() {
    // hole die Typdefinitionen des Typen Zeichenfolge und Liste
    Typ.Compound.KlassenTyp.Liste.definition = definierer.holeTypDefinition("Liste")
        as AST.Definition.Typdefinition.Klasse
    Typ.Compound.KlassenTyp.BuildInType.Zeichenfolge.definition = definierer.holeTypDefinition("Zeichenfolge")
        as AST.Definition.Typdefinition.Klasse
    Typ.Compound.KlassenTyp.BuildInType.Zahl.definition = definierer.holeTypDefinition("Zahl")
        as AST.Definition.Typdefinition.Klasse
    Typ.Compound.KlassenTyp.BuildInType.Boolean.definition = definierer.holeTypDefinition("Boolean")
      as AST.Definition.Typdefinition.Klasse
    Typ.Compound.KlassenTyp.BuildInType.Nichts.definition = definierer.holeTypDefinition("Nichts")
      as AST.Definition.Typdefinition.Klasse
    schreiberTyp = Typ.Compound.KlassenTyp.Klasse(
        definierer.holeTypDefinition("Schreiber", arrayOf("IO")) as AST.Definition.Typdefinition.Klasse, emptyList()
    )
  }

  fun bestimmeTyp(
      nomen: AST.WortArt.Nomen,
      funktionsTypParams: List<AST.Definition.TypParam>?,
      typTypParameter: List<AST.Definition.TypParam>?
  ): Typ {
    val typKnoten = AST.TypKnoten(emptyList(), nomen, emptyList())
    // setze den Parent hier manuell vom Nomen
    typKnoten.setParentNode(nomen.parent!!)
    return bestimmeTyp(typKnoten, funktionsTypParams, typTypParameter, true)!!
  }

  private fun holeTypDefinition(
      typKnoten: AST.TypKnoten,
      funktionsTypParams: List<AST.Definition.TypParam>?,
      typTypParams: List<AST.Definition.TypParam>?,
      aliasErlaubt: Boolean
  ): Typ {
    val typArgumente = typKnoten.typArgumente
    val typName = typKnoten.name.hauptWort(Kasus.NOMINATIV, Numerus.SINGULAR)
    val typNameNominativ = typKnoten.name.ganzesWort(Kasus.NOMINATIV, Numerus.SINGULAR, false)

    var typ: Typ? = null
    if (funktionsTypParams != null ) {
      val funktionTypParamIndex = funktionsTypParams.indexOfFirst { param -> param.binder.nominativ == typNameNominativ }
      if (funktionTypParamIndex != -1) {
        val typParam = funktionsTypParams[funktionTypParamIndex]
        typ = Typ.Generic(
            typParam,
            funktionTypParamIndex,
            TypParamKontext.Funktion
        )
      }
    }
    if (typ == null && typTypParams != null) {
      val typParamIndex = typTypParams.indexOfFirst { param -> param.binder.nominativ == typNameNominativ }
      if (typParamIndex != -1) {
        val typParam = typTypParams[typParamIndex]
        typ = Typ.Generic(
            typParam,
            typParamIndex,
            TypParamKontext.Typ
        )
      }
    }

    if (typ == null) {
      typ = when (typName) {
        "Zahl" -> Typ.Compound.KlassenTyp.BuildInType.Zahl
        "Zeichenfolge" -> Typ.Compound.KlassenTyp.BuildInType.Zeichenfolge
        "Boolean" -> Typ.Compound.KlassenTyp.BuildInType.Boolean
        "Nichts" -> Typ.Compound.KlassenTyp.BuildInType.Nichts
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
      is Typ.Compound.KlassenTyp.BuildInType.Nichts, is Typ.Niemals, is Typ.Generic -> if (typArgumente.isNotEmpty())
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


  fun bestimmeTyp(
      typKnoten: AST.TypKnoten?,
      funktionsTypParameter: List<AST.Definition.TypParam>?,
      typTypParameter: List<AST.Definition.TypParam>?, istAliasErlaubt: Boolean): Typ? {
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
      nomen.numera = EnumSet.of(Numerus.SINGULAR)
      nomen.deklination = typKnoten.name.deklination
      nomen.fälle = arrayOf(EnumSet.of(Kasus.NOMINATIV))
      val singularTypKnoten = AST.TypKnoten(typKnoten.modulPfad, nomen, emptyList())
      singularTypKnoten.typ = singularTyp
      Typ.Compound.KlassenTyp.Liste(listOf(singularTypKnoten))
    }
    return typKnoten.typ
   }

  private fun typisiereFunktionsSignatur(signatur: AST.Definition.FunktionsSignatur, typTypParameter: List<AST.Definition.TypParam>?) {
    // kombiniere die Typparameter
    bestimmeTyp(signatur.rückgabeTyp, signatur.typParameter, typTypParameter, true)
    for (parameter in signatur.parameter) {
      bestimmeTyp(parameter.typKnoten, signatur.typParameter, typTypParameter, true)
    }
  }

  private fun typisiereTypParameter(typParameter: List<AST.Definition.TypParam>) {
    for (param in typParameter) {
      param.schnittstellen.forEach {
        bestimmeTyp(it, null, listOf(param), true)
      }
      param.elternKlasse?.also { bestimmeTyp(it, null, listOf(param), true) }
    }
  }

  fun typisiereKlasse(klasse: AST.Definition.Typdefinition.Klasse) {
    typisiereTypParameter(klasse.typParameter)
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
      typisiereTypParameter(implementierung.typParameter)
      bestimmeTyp(implementierung.klasse, null, implementierung.typParameter, true)
      implementierung.schnittstellen.forEach { schnittstelle ->
        schnittstelle.typArgumente.forEach {arg -> bestimmeTyp(arg, null, klasse.typParameter, true)}
        klasse.implementierteSchnittstellen += prüfeImplementiertSchnittstelle(klasse, implementierung, schnittstelle)
      }
    }
  }

  private fun prüfeImplementiertSchnittstelle(
      klasse: AST.Definition.Typdefinition.Klasse,
      implementierung: AST.Definition.Implementierung,
      adjektiv: AST.TypKnoten): Typ.Compound.Schnittstelle {

    val schnittstelle = holeTypDefinition(adjektiv, null, implementierung.typParameter, true)
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
      val methode = implementierung.methoden.find { methode ->
        methode.signatur.vollerName == methodenName
      }
          ?: throw GermanSkriptFehler.UnimplementierteSchnittstelle(
              adjektiv.name.bezeichnerToken,
              implementierung,
              schnittstelle
          )

      // Füge den Namen der Signatur wo die Typargumente mit den Typparameternamen ersetzt wurden hinzu
      klasse.methoden[signatur.vollerName!!] = methode

      if (signatur.rückgabeTyp.typ != methode.signatur.rückgabeTyp.typ) {
        throw GermanSkriptFehler.TypFehler.FalscherSchnittstellenTyp(
            methode.signatur.rückgabeTyp.name.bezeichnerToken,
            schnittstelle,
            methodenName,
            methode.signatur.rückgabeTyp.typ!!,
            signatur.rückgabeTyp.typ!!
        )
      }

      if (signatur.hatRückgabeObjekt != methode.signatur.hatRückgabeObjekt) {
        throw GermanSkriptFehler.TypFehler.RückgabeObjektErwartet(
            methode.signatur.objekt!!.name.bezeichnerToken,
            schnittstelle,
            methodenName,
            signatur.hatRückgabeObjekt,
            methode.signatur.objekt
        )
      }

      // überprüfe Parameter
      val schnittstellenParameter = signatur.parameter.toList()
      val methodenParameter = methode.signatur.parameter.toList()
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
    }
    return schnittstelle
  }

  private fun typisiereSchnittstelle(schnittstelle: AST.Definition.Typdefinition.Schnittstelle) {
    typisiereTypParameter(schnittstelle.typParameter)
    schnittstelle.methodenSignaturen.forEach { signatur ->
      typisiereFunktionsSignatur(signatur, schnittstelle.typParameter)
    }
  }
}

fun main() {
  val typisierer = Typisierer(File("./iterationen/iter_2/code.gms"))
  typisierer.typisiere()
}