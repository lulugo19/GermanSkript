package germanskript
import java.io.File
import java.util.*


sealed class Typ() {
  // Der Name enthält auch die Typargument usw. des Typs
  abstract val name: String
  // Der einfache Name enthält nur den Klassennamen
  abstract val wort: AST.WortArt
  override fun toString(): String = name

  abstract fun inTypKnoten(): AST.TypKnoten

  class Generic(
      val typParam: AST.Definition.TypParam,
      val index: Int,
      val kontext: TypParamKontext
  ) : Typ() {
    override val wort = typParam.binder
    override val name = "Generic<${wort.nominativ}>"

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

  sealed class Compound(override val wort: AST.WortArt): Typ() {
    abstract val definition: AST.Definition.Typdefinition
    abstract var typArgumente: List<AST.TypKnoten>
    override val name get() = wort.nominativ + if (typArgumente.isEmpty()) "" else "<${typArgumente.joinToString(", ") {it.vollständigerName}}>"

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

    class Klasse(
        override val definition: AST.Definition.Typdefinition.Klasse,
        override var typArgumente: List<AST.TypKnoten>
    ): Compound(definition.name) {

      override fun copy(typArgumente: List<AST.TypKnoten>): Compound {
        return Klasse(definition, typArgumente)
      }

    }

    class Schnittstelle(
        override val definition: AST.Definition.Typdefinition.Schnittstelle,
        override var typArgumente: List<AST.TypKnoten>
    ): Compound(definition.name) {

      override fun copy(typArgumente: List<AST.TypKnoten>): Compound {
        return Schnittstelle(definition, typArgumente)
      }
    }
  }
}

enum class TypParamKontext {
  Klasse,
  Funktion,
}

class Typisierer(startDatei: File): PipelineKomponente(startDatei) {
  val definierer = Definierer(startDatei)
  val ast = definierer.ast

  fun typisiere() {
    definierer.definiere()
    holeBuildInTypDefinitionen()
    definierer.funktionsDefinitionen.forEach { typisiereFunktionsSignatur(it.signatur, null)}
    definierer.typDefinitionen.forEach {typDefinition ->
      // Klassen werden von Typprüfer typisiert, da die Reihenfolge und die Konstruktoren eine wichtige Rolle spielen
      when (typDefinition) {
        is AST.Definition.Typdefinition.Schnittstelle -> typisiereSchnittstelle(typDefinition)
        is AST.Definition.Typdefinition.Alias -> bestimmeTyp(
            typDefinition.typ, null, null, istAliasErlaubt = false, erlaubeLeereTypArgumente = false)
      }
    }
    definierer.typDefinitionen.forEach { typDefinition ->
      if (typDefinition is AST.Definition.Typdefinition.Klasse) {
        typisiereKlasse(typDefinition)
      }
    }
  }

  private fun holeBuildInTypDefinitionen() {
    // Build In Klassen
    BuildIn.Klassen.objekt = Typ.Compound.Klasse(definierer.holeTypDefinition("Objekt", null) as AST.Definition.Typdefinition.Klasse, emptyList())
    BuildIn.Klassen.nichts = Typ.Compound.Klasse(definierer.holeTypDefinition("Nichts", null) as AST.Definition.Typdefinition.Klasse, emptyList())
    BuildIn.Klassen.niemals = Typ.Compound.Klasse(definierer.holeTypDefinition("Niemals", null) as AST.Definition.Typdefinition.Klasse, emptyList())
    BuildIn.Klassen.zahl = Typ.Compound.Klasse(definierer.holeTypDefinition("Zahl", null) as AST.Definition.Typdefinition.Klasse, emptyList())
    BuildIn.Klassen.zeichenfolge = Typ.Compound.Klasse(definierer.holeTypDefinition("Zeichenfolge", null) as AST.Definition.Typdefinition.Klasse, emptyList())
    BuildIn.Klassen.boolean = Typ.Compound.Klasse(definierer.holeTypDefinition("Boolean", null) as AST.Definition.Typdefinition.Klasse, emptyList())
    BuildIn.Klassen.reichweite = Typ.Compound.Klasse(definierer.holeTypDefinition("ReichWeite", null) as AST.Definition.Typdefinition.Klasse, emptyList())
    BuildIn.Klassen.schreiber = Typ.Compound.Klasse(definierer.holeTypDefinition("Schreiber", arrayOf("IO")) as AST.Definition.Typdefinition.Klasse, emptyList())
    BuildIn.Klassen.datei = Typ.Compound.Klasse(definierer.holeTypDefinition("Datei", arrayOf("IO"))  as AST.Definition.Typdefinition.Klasse, emptyList())
    BuildIn.Klassen.liste = definierer.holeTypDefinition("Liste") as AST.Definition.Typdefinition.Klasse
    BuildIn.Klassen.hashMap = definierer.holeTypDefinition("HashMap") as AST.Definition.Typdefinition.Klasse
    BuildIn.Klassen.hashSet = definierer.holeTypDefinition("HashSet") as AST.Definition.Typdefinition.Klasse
    BuildIn.Klassen.paar = definierer.holeTypDefinition("Paar") as AST.Definition.Typdefinition.Klasse

    // Build In Schnittstellen
    BuildIn.Schnittstellen.iterierbar = definierer.holeTypDefinition("Iterierbare") as AST.Definition.Typdefinition.Schnittstelle
    BuildIn.Schnittstellen.addierbar = definierer.holeTypDefinition("Addierbare") as AST.Definition.Typdefinition.Schnittstelle
    BuildIn.Schnittstellen.subtrahierbar = definierer.holeTypDefinition("Subtrahierbare") as AST.Definition.Typdefinition.Schnittstelle
    BuildIn.Schnittstellen.multiplizierbar = definierer.holeTypDefinition("Multiplizierbare") as AST.Definition.Typdefinition.Schnittstelle
    BuildIn.Schnittstellen.dividierbar = definierer.holeTypDefinition("Dividierbare") as AST.Definition.Typdefinition.Schnittstelle
    BuildIn.Schnittstellen.potenzierbar = definierer.holeTypDefinition("Potenzierbare") as AST.Definition.Typdefinition.Schnittstelle
    BuildIn.Schnittstellen.modulobar = definierer.holeTypDefinition("Modulobare") as AST.Definition.Typdefinition.Schnittstelle
    BuildIn.Schnittstellen.negierbar = definierer.holeTypDefinition("Negierbare") as AST.Definition.Typdefinition.Schnittstelle
    BuildIn.Schnittstellen.vergleichbar = definierer.holeTypDefinition("Vergleichbare") as AST.Definition.Typdefinition.Schnittstelle
    BuildIn.Schnittstellen.indiziert = definierer.holeTypDefinition("Indizierte") as AST.Definition.Typdefinition.Schnittstelle
    BuildIn.Schnittstellen.indizierbar = definierer.holeTypDefinition("Indizierbare") as AST.Definition.Typdefinition.Schnittstelle
  }

  fun bestimmeTyp(
      nomen: AST.WortArt.Nomen,
      funktionsTypParams: List<AST.Definition.TypParam>?,
      klassenTypParameter: List<AST.Definition.TypParam>?
  ): Typ {
    val typKnoten = AST.TypKnoten(emptyList(), nomen, emptyList())
    // setze den Parent hier manuell vom Nomen
    typKnoten.setParentNode(nomen.parent!!)
    return bestimmeTyp(typKnoten, funktionsTypParams, klassenTypParameter,
        istAliasErlaubt = true, erlaubeLeereTypArgumente = false)!!
  }

  private fun holeTypDefinition(
      typKnoten: AST.TypKnoten,
      funktionsTypParams: List<AST.Definition.TypParam>?,
      typTypParams: List<AST.Definition.TypParam>?,
      aliasErlaubt: Boolean,
      erlaubeLeereTypArgumente: Boolean = false
  ): Typ {
    val typArgumente = typKnoten.typArgumente

    var typ: Typ? = null
    if (funktionsTypParams != null ) {
      val funktionTypParamIndex = funktionsTypParams.indexOfFirst { param ->
        param.binder.nominativ == typKnoten.name.ganzesWort(Kasus.NOMINATIV, Numerus.SINGULAR, false) }
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
      val typParamIndex = typTypParams.indexOfFirst { param ->
        param.binder.nominativ == typKnoten.name.ganzesWort(Kasus.NOMINATIV, Numerus.SINGULAR, false, param.binder.teilwörterAnzahl)
      }
      if (typParamIndex != -1) {
        val typParam = typTypParams[typParamIndex]
        typ = Typ.Generic(
            typParam,
            typParamIndex,
            TypParamKontext.Klasse
        )
      }
    }

    if (typ == null) {
      typ = when (val typDef = definierer.holeTypDefinition(typKnoten)) {
          is AST.Definition.Typdefinition.Klasse -> Typ.Compound.Klasse(typDef, typArgumente)
          is AST.Definition.Typdefinition.Schnittstelle -> Typ.Compound.Schnittstelle(typDef, typArgumente)
          is AST.Definition.Typdefinition.Alias -> {
            if (!aliasErlaubt) {
              throw GermanSkriptFehler.AliasFehler(typKnoten.name.bezeichnerToken, typDef)
            }
            holeTypDefinition(typDef.typ, null, null,
                aliasErlaubt = false, erlaubeLeereTypArgumente = false)
          }
      }
    }
    // Überprüfe hier die Anzahl der Typargumente
    when (typ) {
      is Typ.Generic -> if (typArgumente.isNotEmpty())
        throw GermanSkriptFehler.TypFehler.TypArgumentFehler(typKnoten.name.bezeichnerToken, typArgumente.size, 0)
      is Typ.Compound -> if ((typArgumente.isNotEmpty() || !erlaubeLeereTypArgumente) && typArgumente.size != typ.definition.typParameter.size)
        throw GermanSkriptFehler.TypFehler.TypArgumentFehler(
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
      klassenTypParameter: List<AST.Definition.TypParam>?,
      istAliasErlaubt: Boolean,
      erlaubeLeereTypArgumente: Boolean = false
  ): Typ? {
    if (typKnoten == null) {
      return null
    }
    typKnoten.typArgumente.forEach {typKnoten -> bestimmeTyp(typKnoten, funktionsTypParameter, klassenTypParameter, true, erlaubeLeereTypArgumente)}
    val singularTyp = holeTypDefinition(typKnoten, funktionsTypParameter, klassenTypParameter, istAliasErlaubt, erlaubeLeereTypArgumente)
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
      Typ.Compound.Klasse(BuildIn.Klassen.liste ,listOf(singularTypKnoten))
    }
    return typKnoten.typ
   }

  private fun typisiereFunktionsSignatur(signatur: AST.Definition.FunktionsSignatur, klassenTypParameter: List<AST.Definition.TypParam>?) {
    // kombiniere die Typparameter
    bestimmeTyp(signatur.rückgabeTyp, signatur.typParameter, klassenTypParameter,true)
    for (parameter in signatur.parameter) {
      bestimmeTyp(parameter.typKnoten, signatur.typParameter, klassenTypParameter, true)
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

  private fun typisiereKlasse(klasse: AST.Definition.Typdefinition.Klasse) {
    typisiereTypParameter(klasse.typParameter)
    for (eigenschaft in klasse.eigenschaften) {
      bestimmeTyp(eigenschaft.typKnoten, null, klasse.typParameter, true)
    }

    klasse.implementierungen.forEach { implementierung ->
      typisiereImplementierung(implementierung, klasse)
    }

    fun fügeElternKlassenMethodenHinzu(elternKlasse: AST.Definition.Typdefinition.Klasse) {
      for ((methodenName, methode) in elternKlasse.methoden) {
        klasse.methoden.putIfAbsent(methodenName, methode)
      }
      for ((konvertierungsTyp, konvertierung) in elternKlasse.konvertierungen) {
        klasse.konvertierungen.putIfAbsent(konvertierungsTyp, konvertierung)
      }
      klasse.implementierteSchnittstellen.addAll(elternKlasse.implementierteSchnittstellen)
      if (elternKlasse.elternKlasse != null) {
        fügeElternKlassenMethodenHinzu((elternKlasse.elternKlasse.klasse.typ as Typ.Compound.Klasse).definition)
      } else {
        if (elternKlasse !== BuildIn.Klassen.objekt.definition)
          fügeElternKlassenMethodenHinzu(BuildIn.Klassen.objekt.definition)
      }
    }

    if (klasse.elternKlasse != null) {
      bestimmeTyp(klasse.elternKlasse.klasse, null, klasse.typParameter, true)
      typisiereKlasse((klasse.elternKlasse.klasse.typ!! as Typ.Compound.Klasse).definition)
      fügeElternKlassenMethodenHinzu((klasse.elternKlasse.klasse.typ as Typ.Compound.Klasse).definition)
    } else if (klasse !== BuildIn.Klassen.objekt.definition) {
      // Kopiere die Definitionen der Objekt-Hierarchie-Wurzel runter
      fügeElternKlassenMethodenHinzu(BuildIn.Klassen.objekt.definition)
    }
  }

  private fun typisiereImplementierung(implementierung: AST.Definition.Implementierung, klasse: AST.Definition.Typdefinition.Klasse) {
    typisiereTypParameter(implementierung.typParameter)
    bestimmeTyp(implementierung.klasse, null, implementierung.typParameter, true)
    typisiereImplementierungsBereich(implementierung.bereich, implementierung.typParameter)
    implementierung.schnittstellen.forEach { schnittstelle ->
      schnittstelle.typArgumente.forEach { arg ->
        bestimmeTyp(arg, null, implementierung.typParameter, true)}
      val schnittstellenTyp = holeTypDefinition(
          schnittstelle, null, implementierung.typParameter, true)
      if (schnittstellenTyp !is Typ.Compound.Schnittstelle) {
        throw GermanSkriptFehler.SchnittstelleErwartet(schnittstelle.name.bezeichnerToken)
      }
      prüfeImplementiertSchnittstelle(
          schnittstelle.name.bezeichnerToken,
          klasse,
          schnittstellenTyp,
          implementierung.bereich
      )
    }
  }

  fun typisiereImplementierungsBereich(
      implBereich: AST.Definition.ImplementierungsBereich ,
      klassenTypParameter: List<AST.Definition.TypParam>?) {

    implBereich.methoden.forEach { methode ->
      typisiereFunktionsSignatur(methode.signatur, klassenTypParameter)
    }

    implBereich.konvertierungen.forEach { konvertierung ->
      bestimmeTyp(konvertierung.typ, null, klassenTypParameter, true)
    }

    implBereich.berechneteEigenschaften.forEach { eigenschaft ->
      bestimmeTyp(eigenschaft.rückgabeTyp, null, klassenTypParameter, true)
    }
  }

  fun prüfeImplementiertSchnittstelle(
      token: Token,
      klasse: AST.Definition.Typdefinition.Klasse,
      schnittstelle: Typ.Compound.Schnittstelle,
      implementierung: AST.Definition.ImplementierungsBereich
  ) {

    fun ersetzeGeneric(typ: Typ): Typ {
      return when (typ) {
        is Typ.Generic -> when (typ.kontext) {
          TypParamKontext.Klasse -> schnittstelle.typArgumente[typ.index].typ!!
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
              token,
              klasse,
              implementierung,
              schnittstelle
          )

      // Füge den Namen der Signatur wo die Typargumente mit den Typparameternamen ersetzt wurden hinzu
      // TODO: Untersuche diesen Sachverhalt nochmal gründlich
      klasse.methoden[signatur.vollerName!!] = methode

      val erwarteterTyp = ersetzeGeneric(signatur.rückgabeTyp.typ!!)
      if (methode.signatur.rückgabeTyp.typ != erwarteterTyp) {
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
        val erwarteterTyp = ersetzeGeneric(schnittstellenParameter[pIndex].typKnoten.typ!!)
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
    klasse.implementierteSchnittstellen += schnittstelle
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