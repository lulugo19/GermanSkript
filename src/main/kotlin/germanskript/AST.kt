package germanskript

import java.util.*
import kotlin.collections.HashMap

fun <T: AST> List<T>.visit(onVisit: (AST) -> Boolean): Boolean {
  for (element in this) {
    element.visit(onVisit)
  }
  return true
}

enum class FunktionsAufrufTyp {
  FUNKTIONS_AUFRUF,
  METHODEN_SELBST_AUFRUF,
  METHODEN_BLOCK_AUFRUF,
  METHODEN_OBJEKT_AUFRUF,
  METHODEN_SUBJEKT_AUFRUF,
}

sealed class AST {
  open val children = emptySequence<AST>()
  var parent: AST? = null
    protected set
  var tiefe: Int = 0
    protected set

  inline fun<reified T> findNodeInParents(): T? {
    var par = this.parent
    while (par != null) {
      if (par is T) {
        return par
      }
      par = par.parent
    }
    return null
  }

  fun setParentNode(parent: AST) {
    this.parent = parent
  }

  protected fun setParentForChildren(tiefe: Int) {
    this.tiefe = tiefe
    for (child in children) {
      child.parent = this
      child.setParentForChildren(tiefe)
    }
  }

  // germanskript.visit implementation for all the leaf nodes
  fun visit(onVisit: (AST) -> Boolean) {
    if (onVisit(this)) {
      for (child in children) {
        child.visit(onVisit)
      }
    }
  }

  sealed class WortArt: AST() {
    abstract val bezeichnerToken: Token
    abstract var deklination: Deklination?
    abstract var numera: EnumSet<Numerus>
    abstract var fälle: Array<EnumSet<Kasus>>
    abstract var vornomen: TypedToken<TokenTyp.VORNOMEN>?
    abstract val hauptWort: String

    val numerus: Numerus get() = numera.first()
    val geprüft get() = deklination != null
    open val genus get() = deklination!!.genus
    val nominativ: String get() = ganzesWort(Kasus.NOMINATIV, numerus)
    val kasus: Kasus get() = fälle[numera.first().ordinal].first()

    val unveränderlich get() = vornomen == null || vornomen!!.typ ==
        TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT || vornomen!!.typ == TokenTyp.VORNOMEN.DEMONSTRATIV_PRONOMEN.DIESE

    open fun hauptWort(kasus: Kasus, numerus: Numerus): String = deklination!!.holeForm(kasus, numerus)
    abstract fun ganzesWort(kasus: Kasus, numerus: Numerus): String

    data class Nomen(
        override var vornomen: TypedToken<TokenTyp.VORNOMEN>?,
        val bezeichner: TypedToken<TokenTyp.BEZEICHNER_GROSS>
    ): WortArt() {
      override val bezeichnerToken = bezeichner.toUntyped()
      override var deklination: Deklination? = null
      override var numera : EnumSet<Numerus> = EnumSet.noneOf(Numerus::class.java)
      override var fälle: Array<EnumSet<Kasus>> = arrayOf(EnumSet.noneOf(Kasus::class.java), EnumSet.noneOf(Kasus::class.java))

      var vornomenString: String? = null
      val istSymbol get() = bezeichner.typ.istSymbol
      override val genus get() = if (istSymbol) Genus.NEUTRUM else deklination!!.genus

      override val hauptWort = if (istSymbol) bezeichner.typ.symbol else bezeichner.typ.hauptWort!!

      override fun ganzesWort(kasus: Kasus, numerus: Numerus): String {
        if (istSymbol) {
          return bezeichnerToken.wert
        }
        return bezeichner.typ.ersetzeHauptWort(deklination!!.holeForm(kasus, numerus))
      }

      override fun hauptWort(kasus: Kasus, numerus: Numerus): String {
        if (istSymbol) {
          return bezeichner.typ.symbol
        }
        return super.hauptWort(kasus, numerus)
      }

      /**
       * Gibt ein neues Nomen basierend auf dem alten Nomen zurück, bei dem das Hauptwort ausgetauscht wurde.
       */
      fun tauscheHauptWortAus(deklination: Deklination): Nomen {
        val neuesNomen = this.copy()
        neuesNomen.numera = this.numera
        neuesNomen.deklination = deklination
        return neuesNomen
      }
    }

    data class Adjektiv(
        override var vornomen: TypedToken<TokenTyp.VORNOMEN>?,
        val bezeichner: TypedToken<TokenTyp.BEZEICHNER_KLEIN>): WortArt() {

      override var numera : EnumSet<Numerus> = EnumSet.noneOf(Numerus::class.java)
      override var deklination: Deklination? = null
      override var fälle: Array<EnumSet<Kasus>> = arrayOf(EnumSet.noneOf(Kasus::class.java), EnumSet.noneOf(Kasus::class.java))
      override val bezeichnerToken = bezeichner.toUntyped()
      var normalisierung: String = ""

      override val hauptWort = bezeichner.wert.capitalize()
      override fun ganzesWort(kasus: Kasus, numerus: Numerus): String = hauptWort(kasus, numerus)
    }
  }

  data class TypKnoten(
      val modulPfad: List<TypedToken<TokenTyp.BEZEICHNER_GROSS>>,
      val name: WortArt,
      val typArgumente: List<TypKnoten>
  ): AST() {
    var typ: Typ? = null
    override val children = sequence {
      yieldAll(typArgumente)
      yield(name)
    }
    val vollständigerName: String get() = modulPfad.joinToString("::") { it.wert } +
        (if (modulPfad.isEmpty()) "" else "::")  + name.nominativ
  }

  interface IAufruf {
    val token: Token
    val vollerName: String?
  }

  class DefinitionsContainer(): AST() {
    val deklinationen = mutableListOf<Definition.DeklinationsDefinition>()
    val funktionsListe = mutableListOf<Definition.Funktion>()
    val konstanten =  mutableMapOf<String, Definition.Konstante>()
    val definierteTypen: MutableMap<String, Definition.Typdefinition> = mutableMapOf()
    val funktionen: MutableMap<String, Definition.Funktion> = mutableMapOf()
    val implementierungen = mutableListOf<Definition.Implementierung>()
    val module = mutableMapOf<String, Definition.Modul>()
    val verwende = mutableListOf<Definition.Verwende>()
    val verwendeteModule = mutableListOf<DefinitionsContainer>()
    val verwendeteTypen = mutableMapOf<String, Definition.Typdefinition>()
    val verwendeteKonstanten = mutableMapOf<String, Definition.Konstante>()
    val wörterbuch = Wörterbuch()

    override val children = sequence {
      yieldAll(deklinationen)
      yieldAll(funktionsListe)
      yieldAll(implementierungen)
      yieldAll(konstanten.values)
      yieldAll(definierteTypen.values)
      yieldAll(module.values)
    }
  }

  // Wurzelknoten
  data class Programm(val programmStart: Token?, val definitionen: DefinitionsContainer, val programm: Satz.Bereich):
      AST(), IAufruf {
    override val vollerName = "starte das Programm"
    override val token get() = programmStart!!

    override val children = sequenceOf(definitionen, programm)

    init {
      // go through the whole AST and set the parents
      setParentForChildren(0)
    }
  }

  data class FunktionsAufruf(
      val typArgumente: List<TypKnoten>,
      val modulPfad: List<TypedToken<TokenTyp.BEZEICHNER_GROSS>>,
      val subjekt: Ausdruck?,
      val verb: TypedToken<TokenTyp.BEZEICHNER_KLEIN>,
      val objekt: Argument?,
      val reflexivPronomen: TypedToken<TokenTyp.REFLEXIV_PRONOMEN>?,
      val präpositionsArgumente: List<PräpositionsArgumente>,
      val suffix: TypedToken<TokenTyp.BEZEICHNER_KLEIN>?
  ) : AST(), IAufruf {
    override val token = verb.toUntyped()

    override var vollerName: String? = null
    private val _argumente: MutableList<Argument> = mutableListOf()
    val argumente: List<Argument> = _argumente
    var funktionsDefinition: Definition.Funktion? = null
    var aufrufTyp: FunktionsAufrufTyp = FunktionsAufrufTyp.FUNKTIONS_AUFRUF
    val vollständigerName: String get() = modulPfad.joinToString("::") { it.wert } +
        (if (modulPfad.isEmpty()) "" else "::") + vollerName

    init {
      if (objekt != null) {
        _argumente.add(objekt)
      }
      for (präposition in präpositionsArgumente) {
        _argumente.addAll(präposition.argumente)
      }
    }

    override val children = sequence {
      if (subjekt != null) {
        yield(subjekt!!)
      }
      yieldAll(typArgumente)
      if (objekt != null) {
        yield(objekt!!)
      }
      yieldAll(präpositionsArgumente)
    }
  }

  data class Präposition(val präposition: TypedToken<TokenTyp.BEZEICHNER_KLEIN>) : AST() {
    val fälle = präpositionsFälle
        .getOrElse(präposition.wert) {
          throw GermanSkriptFehler.SyntaxFehler.ParseFehler(präposition.toUntyped(), "Präposition")
        }
  }

  data class MethodenBereich(val name: WortArt.Nomen, val bereich: Satz.Bereich): AST() {
    override val children = sequenceOf(name, bereich)
  }

  sealed class Definition : AST() {

    data class Modul(val name: TypedToken<TokenTyp.BEZEICHNER_GROSS>, val definitionen: DefinitionsContainer):
        Definition() {
      override val children: Sequence<AST> = sequenceOf(definitionen)
    }

    data class Parameter(
        val typKnoten: TypKnoten,
        val name: WortArt.Nomen
    ): AST() {
      val istPrivat get() = typKnoten.name.vornomen!!.typ is TokenTyp.VORNOMEN.DEMONSTRATIV_PRONOMEN
      val typIstName get() = typKnoten.name === name
      override val children = sequenceOf(typKnoten, name)
    }

    data class PräpositionsParameter(
        val präposition: Präposition,
        val parameter: List<Parameter>
    ): AST() {
      override val children = sequence { yieldAll(parameter) }
    }

    sealed class DeklinationsDefinition: Definition() {
      data class Definition(val deklination: Deklination): DeklinationsDefinition()
      data class Duden(val wort: TypedToken<TokenTyp.BEZEICHNER_GROSS>): DeklinationsDefinition()
    }

    data class FunktionsSignatur(
        val typParameter: List<WortArt.Nomen>,
        val rückgabeTyp: TypKnoten,
        val name: TypedToken<TokenTyp.BEZEICHNER_KLEIN>,
        val reflexivPronomen: TypedToken<TokenTyp.REFLEXIV_PRONOMEN>?,
        val objekt: Parameter?,
        val präpositionsParameter: List<PräpositionsParameter>,
        val suffix: TypedToken<TokenTyp.BEZEICHNER_KLEIN>?,
        var vollerName: String? = null
    ): Definition() {
      private val _parameter: MutableList<Parameter> = mutableListOf()
      val parameter: List<Parameter> = _parameter

      init {
        if (objekt != null) {
          _parameter.add(objekt)
        }
        for (präposition in präpositionsParameter) {
          _parameter.addAll(präposition.parameter)
        }
      }

      override val children = sequence {
        yieldAll(typParameter)
        yield(rückgabeTyp)
        if (objekt != null) {
          yield(objekt!!)
        }
        yieldAll(präpositionsParameter)
      }
    }

    data class Funktion(
        val signatur: FunktionsSignatur,
        val körper: Satz.Bereich
    ): Definition() {

      override val children = sequenceOf(signatur, körper)
    }

    data class Implementierung(
        val klasse: TypKnoten,
        val schnittstellen: List<TypKnoten>,
        val methoden: List<Funktion>,
        val eigenschaften: List<Eigenschaft>,
        val konvertierungen: List<Konvertierung>
    ): Definition() {
      override val children = sequence {
        yield(klasse)
        yieldAll(schnittstellen)
        yieldAll(methoden)
        yieldAll(eigenschaften)
        yieldAll(konvertierungen)
      }
    }

    sealed class Typdefinition: Definition() {

      abstract val namensToken: Token
      abstract val typParameter: List<WortArt.Nomen>

      data class Klasse(
          override val typParameter: List<WortArt.Nomen>,
          val name: WortArt.Nomen,
          val elternKlasse: TypKnoten?,
          val eigenschaften: MutableList<Parameter>,
          val konstruktor: Satz.Bereich
      ): Typdefinition() {
        val methoden: HashMap<String, Funktion> = HashMap()
        val berechneteEigenschaften: HashMap<String, Eigenschaft> = HashMap()
        val konvertierungen: HashMap<String, Konvertierung> = HashMap()
        val implementierteSchnittstellen = mutableListOf<Typ.Compound.Schnittstelle>()
        val implementierungen = mutableListOf<Implementierung>()
        var geprüft = false

        override val namensToken = name.bezeichner.toUntyped()

        override val children = sequence {
          yieldAll(typParameter)
          yield(name)
          if (elternKlasse != null) {
            yield(elternKlasse!!)
          }
          yieldAll(eigenschaften)
          yield(konstruktor)
        }
      }

      data class Schnittstelle(
          override val typParameter: List<WortArt.Nomen>,
          val name: TypedToken<TokenTyp.BEZEICHNER_KLEIN>,
          val methodenSignaturen: List<FunktionsSignatur>
      ): Typdefinition() {

        override val namensToken = name.toUntyped()

        override val children = sequence {
          yieldAll(typParameter)
          yieldAll(methodenSignaturen)
        }
      }

      data class Alias(val name: WortArt.Nomen, val typ: TypKnoten): Typdefinition() {
        override val namensToken = name.bezeichner.toUntyped()
        override val typParameter = emptyList<WortArt.Nomen>()
        override val children = sequenceOf(name, typ)
      }
    }

    data class Konvertierung(
        val typ: TypKnoten,
        val definition: Satz.Bereich
    ): Definition() {
      override val children = sequenceOf(typ, definition)
    }

    data class Eigenschaft(
        val rückgabeTyp: TypKnoten,
        val name: WortArt.Nomen,
        val definition: Satz.Bereich
    ): Definition() {
      override val children = sequenceOf(rückgabeTyp, name, definition)
    }

    data class Konstante(
        val name: TypedToken<TokenTyp.BEZEICHNER_GROSS>,
        var wert: Ausdruck
    ): Definition() {
      override val children = sequenceOf(wert)
      var typ: Typ? = null
    }

    data class Import(
        val dateiPfad: TypedToken<TokenTyp.ZEICHENFOLGE>
    ): Definition() {
      val pfad = dateiPfad.typ.zeichenfolge.zeichenfolge
    }

    data class Verwende(
        val modulPfad: List<TypedToken<TokenTyp.BEZEICHNER_GROSS>>,
        val modulOderKlasse: TypedToken<TokenTyp.BEZEICHNER_GROSS>
    ): Definition()
  }


  sealed class Satz : AST() {
    data class Intern(val intern: TypedToken<TokenTyp.INTERN>) :Satz(), IAufruf {
      override val token = intern.toUntyped()
      override val vollerName = "intern"
    }

    sealed class SchleifenKontrolle: Satz() {
      object Fortfahren: SchleifenKontrolle()
      object Abbrechen: SchleifenKontrolle()
    }

    data class VariablenDeklaration(
        val name: WortArt.Nomen,
        val neu: TypedToken<TokenTyp.NEU>?,
        val zuweisung: TypedToken<TokenTyp.ZUWEISUNG>,
        var wert: Ausdruck
    ): Satz() {
      override val children = sequenceOf(name, wert)

      val istEigenschaftsNeuZuweisung = name.vornomen!!.typ == TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN
      val istEigenschaft = name.vornomen!!.typ is TokenTyp.VORNOMEN.DEMONSTRATIV_PRONOMEN
    }

    data class ListenElementZuweisung(
        val singular: WortArt.Nomen,
        val index: Ausdruck,
        val zuweisung: TypedToken<TokenTyp.ZUWEISUNG>,
        var wert: Ausdruck
    ): Satz() {
      override val children = sequenceOf(singular, wert)
      val istEigenschaft = singular.vornomen!!.typ is TokenTyp.VORNOMEN.DEMONSTRATIV_PRONOMEN
    }

    data class BedingungsTerm(
        var bedingung: Ausdruck,
        val bereich: Bereich
    ): Satz() {
      override val children = sequence {
          yield(bedingung)
          yield(bereich)
        }
    }

    data class Bereich(
        val sätze: MutableList<Satz>
    ): Satz() {
      override val children = sequence {
        yieldAll(sätze)
      }
    }

    data class Bedingung(
        val bedingungen: MutableList<BedingungsTerm>,
        var sonst: Bereich?
    ): Satz() {
      override val children = sequence {
          yieldAll(bedingungen)
          if (sonst != null) {
            yield(sonst!!)
          }
        }
    }

    data class SolangeSchleife(
        val bedingung: BedingungsTerm
    ) : Satz() {
      override val children = sequence {
          yield(bedingung)
        }
    }

    data class FürJedeSchleife(
        val jede: TypedToken<TokenTyp.JEDE>,
        val singular: WortArt.Nomen,
        val binder: WortArt.Nomen,
        val liste: Ausdruck?,
        val reichweite: Reichweite?,
        val bereich: Bereich
    ): Satz() {
      override val children = sequence {
          yield(singular)
          yield(binder)
          if (liste != null) {
            yield(liste!!)
          }
          if (reichweite != null) {
            yield(reichweite!!)
          }
          yield(bereich)
        }
    }

    data class VersucheFange(
        val versuche: Bereich,
        val fange: List<Fange>,
        val schlussendlich: Bereich?
    ): Satz() {
      override val children = sequence {
        yield(versuche)
        yieldAll(fange)
        if (schlussendlich != null) {
          yield(schlussendlich!!)
        }
      }
    }

    data class Fange(
        val param: Definition.Parameter,
        val bereich: Bereich
    ): Satz() {
      override val children = sequenceOf(param, bereich)
    }

    data class Werfe(
        val werfe: TypedToken<TokenTyp.BEZEICHNER_KLEIN>,
        var ausdruck: Ausdruck
    ): Satz() {
      override val children = sequenceOf(ausdruck)
    }

    data class Reichweite(val anfang: Ausdruck, val ende: Ausdruck) : AST() {
      override val children = sequenceOf(anfang, ende)
    }

    data class FunktionsAufruf(val aufruf: AST.FunktionsAufruf): Satz() {
      override val children = sequenceOf(aufruf)
    }

    data class MethodenBereich(val methodenBereich: AST.MethodenBereich): Satz() {
      override val children = sequenceOf(methodenBereich)
    }

    data class SuperBlock(val bereich: Bereich): Satz() {
      override val children = sequenceOf(bereich)
    }

    data class Zurückgabe(val erstesToken: TypedToken<TokenTyp.BEZEICHNER_KLEIN>, var ausdruck: Ausdruck): Satz() {
      override val children = sequenceOf(ausdruck)
    }
  }

  data class Argument(
      val adjektiv: WortArt.Adjektiv?,
      val name: WortArt.Nomen,
      var ausdruck: Ausdruck
  ): AST() {

    override val children = sequence {
      if (adjektiv != null) {
        yield(adjektiv!!)
      }
      yield(name)
      yield(ausdruck)
    }
  }

  data class PräpositionsArgumente(val präposition: Präposition, val argumente: List<Argument>): AST() {
    override val children = sequence { yieldAll(argumente) }
  }

  sealed class Ausdruck : AST() {
    data class Nichts(val nichts: TypedToken<TokenTyp.NICHTS>): Ausdruck()

    data class Zeichenfolge(val zeichenfolge: TypedToken<TokenTyp.ZEICHENFOLGE>) : Ausdruck()

    data class Zahl(val zahl: TypedToken<TokenTyp.ZAHL>) : Ausdruck()

    data class Boolean(val boolean: TypedToken<TokenTyp.BOOLEAN>) : Ausdruck()

    data class Variable(val name: WortArt.Nomen) : Ausdruck() {
      override val children = sequenceOf(name)
      var konstante: Konstante? = null
    }

    data class Konstante(
        val modulPfad: List<TypedToken<TokenTyp.BEZEICHNER_GROSS>>,
        val name: TypedToken<TokenTyp.BEZEICHNER_GROSS>
    ): Ausdruck() {
      val vollständigerName: String = modulPfad.joinToString("::") { it.wert } +
          (if (modulPfad.isEmpty()) "" else "::")  + name.wert

      var wert: Ausdruck? = null
    }

    data class Liste(val pluralTyp: TypKnoten, var elemente: List<Ausdruck>): Ausdruck() {
      override val children = sequence {
        yield(pluralTyp)
        yieldAll( elemente)
      }
    }

    data class ListenElement(
        val singular: WortArt.Nomen,
        var index: Ausdruck
    ): Ausdruck() {
      var istZeichenfolgeZugriff = false
      override val children = sequenceOf(singular, index)
    }

    data class FunktionsAufruf(val aufruf: AST.FunktionsAufruf): Ausdruck() {
      override val children = sequenceOf(aufruf)
    }

    data class MethodenBereich(val methodenBereich: AST.MethodenBereich): Ausdruck() {
      override val children = sequenceOf(methodenBereich)
    }

    data class BinärerAusdruck(
        val operator: TypedToken<TokenTyp.OPERATOR>,
        val links: Ausdruck,
        val rechts: Ausdruck,
        val istAnfang: kotlin.Boolean,
        val inStringInterpolation: kotlin.Boolean) : Ausdruck() {
      override val children = sequenceOf(links, rechts)
    }

    data class Minus(val ausdruck: Ausdruck) : Ausdruck() {
      override val children = sequenceOf(ausdruck)
    }

    data class Konvertierung(
        var ausdruck: Ausdruck,
        val typ: TypKnoten
    ): Ausdruck(), IAufruf {
      val typName = typ.name as WortArt.Nomen
      override val token = typName.bezeichner.toUntyped()
      override val vollerName get() = "als ${typName.nominativ}"

      override val children = sequenceOf(ausdruck, typ)
    }

    data class ObjektInstanziierung(
        val klasse: TypKnoten,
        val eigenschaftsZuweisungen: List<Argument>
    ): Ausdruck(), IAufruf {
      override val children = sequence {
        yield(klasse)
        yieldAll(eigenschaftsZuweisungen)
      }

      private val klassenName = (klasse.name) as WortArt.Nomen
      override val token: Token = klassenName.bezeichner.toUntyped()

      override val vollerName: String get() {
        val artikel = when (klasse.name.genus) {
          Genus.MASKULINUM -> "den"
          Genus.FEMININUM -> "die"
          Genus.NEUTRUM -> "das"
        }
        return "erstelle $artikel ${klassenName.ganzesWort(Kasus.NOMINATIV, Numerus.SINGULAR)}"
      }
    }

    data class Closure(val schnittstelle: TypKnoten, val körper: Satz.Bereich): Ausdruck() {
      override val children = sequenceOf(schnittstelle, körper)
    }

    interface IEigenschaftsZugriff: IAufruf {
      val eigenschaftsName: WortArt.Nomen
      var aufrufName: String?
    }

    data class EigenschaftsZugriff(
        override val eigenschaftsName: WortArt.Nomen,
        val objekt: Ausdruck
    ): Ausdruck(), IEigenschaftsZugriff {
      override val children = sequenceOf(eigenschaftsName , objekt)
      override val token = eigenschaftsName.bezeichner.toUntyped()
      override var aufrufName: String? = null
      override val vollerName get() = aufrufName
    }

    data class MethodenBereichEigenschaftsZugriff(
        override val eigenschaftsName: WortArt.Nomen
    ): Ausdruck(), IEigenschaftsZugriff {
      override val children = sequenceOf(eigenschaftsName)
      override val token = eigenschaftsName.bezeichner.toUntyped()
      override var aufrufName: String? = null
      override val vollerName get() = aufrufName
    }

    data class SelbstEigenschaftsZugriff(
        override val eigenschaftsName: WortArt.Nomen
    ): Ausdruck(), IEigenschaftsZugriff {
      override val children = sequenceOf(eigenschaftsName)
      override val token = eigenschaftsName.bezeichner.toUntyped()
      override var aufrufName: String? = null
      override val vollerName get() = aufrufName
    }

    data class SelbstReferenz(val ich: TypedToken<TokenTyp.REFERENZ.ICH>): Ausdruck()
    data class MethodenBereichReferenz(val du: TypedToken<TokenTyp.REFERENZ.DU>): Ausdruck()
  }
}