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
  METHODEN_BEREICHS_AUFRUF,
  METHODEN_REFLEXIV_AUFRUF,
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
    abstract val teilWörterAnzahl: Int

    val numerus: Numerus get() = numera.first()
    val geprüft get() = numera.size != 0
    open val genus get() = deklination!!.genus
    val nominativ: String get() = ganzesWort(Kasus.NOMINATIV, numerus, true)
    val kasus: Kasus get() = fälle[numera.first().ordinal].first()

    val unveränderlich
      get() = vornomen == null || vornomen!!.typ ==
          TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT || vornomen!!.typ == TokenTyp.VORNOMEN.DEMONSTRATIV_PRONOMEN.DIESE

    open fun hauptWort(kasus: Kasus, numerus: Numerus): String = deklination!!.holeForm(kasus, numerus)
    abstract fun ganzesWort(kasus: Kasus, numerus: Numerus, mitErweiterung: Boolean, maxAnzahlTeilWörter: Int = Int.MAX_VALUE): String

    data class Nomen(
        override var vornomen: TypedToken<TokenTyp.VORNOMEN>?,
        val bezeichner: TypedToken<TokenTyp.BEZEICHNER_GROSS>
    ) : WortArt() {
      override val bezeichnerToken = bezeichner.toUntyped()
      override var deklination: Deklination? = null
      override var numera: EnumSet<Numerus> = EnumSet.noneOf(Numerus::class.java)
      override var fälle: Array<EnumSet<Kasus>> = arrayOf(EnumSet.noneOf(Kasus::class.java), EnumSet.noneOf(Kasus::class.java))
      override val teilWörterAnzahl = bezeichner.typ.teilWörter.size

      private var _adjektiv: Adjektiv? = null
      var adjektiv: Adjektiv?
        get() {
          if (bezeichner.typ.adjektiv == null) {
            return null
          } else if (_adjektiv == null) {
            _adjektiv = bezeichner.typ.adjektiv.let { Adjektiv(vornomen, it) }
            _adjektiv!!.setParentNode(this)
          }
          return _adjektiv
        }
        set(value) {
          _adjektiv = value
        }

      var vornomenString: String? = null
      val istSymbol get() = bezeichner.typ.istSymbol
      override val genus get() = if (istSymbol) Genus.NEUTRUM else deklination!!.genus

      override val hauptWort = if (istSymbol) bezeichner.typ.symbol else bezeichner.typ.hauptWort!!

      override fun ganzesWort(kasus: Kasus, numerus: Numerus, mitErweiterung: Boolean, maxAnzahlTeilWörter: Int): String {
        if (istSymbol) {
          return (adjektiv?.ganzesWort(kasus, numerus, true) ?: "") + bezeichnerToken.wert
        }
        val adjektiv = if (mitErweiterung) (adjektiv?.ganzesWort(kasus, numerus, true) ?: "") else ""
        return adjektiv + bezeichner.typ.ersetzeHauptWort(deklination!!.holeForm(kasus, numerus), mitErweiterung, maxAnzahlTeilWörter)
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
        val bezeichner: TypedToken<TokenTyp.BEZEICHNER_KLEIN>) : WortArt() {

      override var numera: EnumSet<Numerus> = EnumSet.noneOf(Numerus::class.java)
      override var deklination: Deklination? = null
      override var fälle: Array<EnumSet<Kasus>> = arrayOf(EnumSet.noneOf(Kasus::class.java), EnumSet.noneOf(Kasus::class.java))
      override val bezeichnerToken = bezeichner.toUntyped()
      override val teilWörterAnzahl = 1
      var normalisierung: String = ""

      override val hauptWort = bezeichner.wert.capitalize()
      override fun ganzesWort(kasus: Kasus, numerus: Numerus, mitErweiterung: Boolean, maxAnzahlTeilWörter: Int): String
          = hauptWort(kasus, numerus)
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

    private var _vollständigerName: String? = null
    val vollständigerName: String get() {
      if (_vollständigerName != null) {
        return _vollständigerName!!
      }
      _vollständigerName =
          modulPfad.joinToString("::") {it.wert} +
          (if (modulPfad.isEmpty()) "" else "::") +
          name.nominativ +
          (if (typArgumente.isEmpty()) "" else
          typArgumente.joinToString(", ", "<", ">") {it.name.nominativ})

      return _vollständigerName!!
    }

    fun copy(typArgumente: List<TypKnoten>): TypKnoten {
      val kopie = TypKnoten(modulPfad, name, typArgumente)
      kopie.typ = when (val meinTyp = this.typ) {
        is Typ.Compound -> meinTyp.copy(typArgumente)
        else -> meinTyp
      }
      return kopie
    }
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

  data class Präposition(val präposition: TypedToken<TokenTyp.BEZEICHNER_KLEIN>) : AST() {
    val fälle = präpositionsFälle
        .getOrElse(präposition.wert) {
          throw GermanSkriptFehler.SyntaxFehler.ParseFehler(präposition.toUntyped(), "Präposition")
        }
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

      val anzeigeName: String get() {
        var anzeigeName = ""
        if (typKnoten.name.vornomen != null) {
          anzeigeName += typKnoten.name.vornomen!!.wert + " "
        }
        anzeigeName += typKnoten.name.bezeichnerToken.wert
        if (!typIstName) {
          anzeigeName += " " + name.bezeichnerToken.wert
        }
        return anzeigeName
      }
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
        val typParameter: List<TypParam>,
        val rückgabeTyp: TypKnoten,
        val name: TypedToken<TokenTyp.BEZEICHNER_KLEIN>,
        val reflexivPronomen: TypedToken<TokenTyp.REFLEXIV_PRONOMEN>?,
        val objekt: Parameter?,
        val hatRückgabeObjekt: Boolean,
        val präpositionsParameter: List<PräpositionsParameter>,
        val suffix: TypedToken<TokenTyp.BEZEICHNER_KLEIN>?,
        var vollerName: String? = null
    ): Definition() {

      override val children = sequence {
        yieldAll(typParameter)
        yield(rückgabeTyp)
        if (objekt != null) {
          yield(objekt!!)
        }
        yieldAll(präpositionsParameter)
      }

      val parameter get() = sequence {
        if (objekt != null && !hatRückgabeObjekt) {
          yield(objekt!!)
        }
        for (präposition in präpositionsParameter) {
          yieldAll(präposition.parameter)
        }
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
        val typParameter: List<TypParam>,
        val schnittstellen: List<TypKnoten>,
        val bereich: ImplementierungsBereich
    ): Definition() {
      override val children = sequence {
        yield(klasse)
        yieldAll(typParameter)
        yieldAll(schnittstellen)
        yield(bereich)
      }
    }

    data class ImplementierungsBereich(
        val eigenschaften: List<Satz.VariablenDeklaration>,
        val methoden: List<Funktion>,
        val berechneteEigenschaften: List<Eigenschaft>,
        val konvertierungen: List<Konvertierung>
    ): Definition() {
      override val children = sequence {
        yieldAll(eigenschaften)
        yieldAll(methoden)
        yieldAll(berechneteEigenschaften)
        yieldAll(konvertierungen)
      }
    }

    data class TypParam(
        val binder: WortArt.Nomen,
        val schnittstellen: List<TypKnoten>,
        val elternKlasse: TypKnoten?
    ): AST() {
      override val children = sequence {
        yield(binder)
        yieldAll(schnittstellen)
        if (elternKlasse != null) {
          yield(elternKlasse!!)
        }
      }
    }

    sealed class Typdefinition: Definition() {
      abstract val name: WortArt
      val namensToken get() = name.bezeichnerToken
      abstract val typParameter: List<TypParam>
      abstract fun findeMethode(methodenName: String): FunktionsSignatur?

      data class Klasse(
          override val typParameter: List<TypParam>,
          override val name: WortArt.Nomen,
          val elternKlasse: Satz.Ausdruck.ObjektInstanziierung?,
          val eigenschaften: MutableList<Parameter>,
          val konstruktor: Satz.Bereich
      ): Typdefinition() {
        val methoden: HashMap<String, Funktion> = HashMap()
        val berechneteEigenschaften: HashMap<String, Eigenschaft> = HashMap()
        val konvertierungen: HashMap<String, Konvertierung> = HashMap()
        val implementierteSchnittstellen = mutableListOf<Typ.Compound.Schnittstelle>()
        val implementierungen = mutableListOf<Implementierung>()
        var geprüft = false

        override val children = sequence {
          yieldAll(typParameter)
          yield(name)
          if (elternKlasse != null) {
            yield(elternKlasse!!)
          }
          yieldAll(eigenschaften)
          yield(konstruktor)
        }

        override fun findeMethode(methodenName: String): FunktionsSignatur? = methoden[methodenName]?.signatur
      }

      data class Schnittstelle(
          override val typParameter: List<TypParam>,
          override val name: WortArt.Adjektiv,
          val methodenSignaturen: List<FunktionsSignatur>
      ): Typdefinition() {

        override val children = sequence {
          yieldAll(typParameter)
          yield(name)
          yieldAll(methodenSignaturen)
        }

        override fun findeMethode(methodenName: String): FunktionsSignatur? =
          methodenSignaturen.find { signatur -> signatur.vollerName!! == methodenName }
      }

      data class Alias(override val name: WortArt.Nomen, val typ: TypKnoten): Typdefinition() {
        override val typParameter = emptyList<TypParam>()
        override val children = sequenceOf(name, typ)
        override fun findeMethode(methodenName: String): FunktionsSignatur? = null
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
        var wert: Satz.Ausdruck
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

      val istEigenschaftsNeuZuweisung = name.vornomen != null && name.vornomen!!.typ == TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN
      val istEigenschaft = name.vornomen != null && name.vornomen!!.typ is TokenTyp.VORNOMEN.DEMONSTRATIV_PRONOMEN
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
        val token: Token,
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

    data class SolangeSchleife(
        val bedingung: BedingungsTerm
    ) : Satz() {
      override val children = sequence {
          yield(bedingung)
        }
    }

    data class FürJedeSchleife(
        val für: TypedToken<TokenTyp.BEZEICHNER_KLEIN>,
        val singular: WortArt.Nomen,
        val binder: WortArt.Nomen,
        val iterierbares: Ausdruck?,
        val reichweite: Reichweite?,
        val bereich: Bereich
    ): Satz() {
      override val children = sequence {
          yield(singular)
          yield(binder)
          if (iterierbares != null) {
            yield(iterierbares!!)
          }
          if (reichweite != null) {
            yield(reichweite!!)
          }
          yield(bereich)
        }
    }

    data class Reichweite(val anfang: Ausdruck, val ende: Ausdruck) : AST() {
      override val children = sequenceOf(anfang, ende)
    }

    data class SuperBlock(val bereich: Bereich): Satz() {
      override val children = sequenceOf(bereich)
    }

    data class Zurückgabe(val erstesToken: Token, var ausdruck: Ausdruck): Satz() {
      override val children = sequenceOf(ausdruck)
    }

    sealed class Ausdruck : Satz() {

      fun holeErstesToken(): Token {
        return when (this) {
          is Zeichenfolge -> zeichenfolge.toUntyped()
          is Liste -> pluralTyp.name.vornomen!!.toUntyped()
          is Zahl -> zahl.toUntyped()
          is Boolean -> boolean.toUntyped()
          is Variable -> name.bezeichner.toUntyped()
          is FunktionsAufruf -> verb.toUntyped()
          is ListenElement -> singular.vornomen!!.toUntyped()
          is BinärerAusdruck -> links.holeErstesToken()
          is Minus -> ausdruck.holeErstesToken()
          is Konvertierung -> ausdruck.holeErstesToken()
          is ObjektInstanziierung -> klasse.name.bezeichnerToken
          is EigenschaftsZugriff -> eigenschaftsName.bezeichner.toUntyped()
          is SelbstEigenschaftsZugriff -> eigenschaftsName.bezeichner.toUntyped()
          is MethodenBereichEigenschaftsZugriff -> eigenschaftsName.bezeichner.toUntyped()
          is SelbstReferenz -> ich.toUntyped()
          is MethodenBereichReferenz -> du.toUntyped()
          is Closure -> schnittstelle.name.bezeichnerToken
          is AnonymeKlasse -> schnittstelle.name.bezeichnerToken
          is Konstante -> name.toUntyped()
          is MethodenBereich -> objekt.holeErstesToken()
          is Nichts -> nichts.toUntyped()
          is Bedingung -> bedingungen[0].token
          is TypÜberprüfung -> ausdruck.holeErstesToken()
          is VersucheFange -> versuche.toUntyped()
          is Werfe -> werfe.toUntyped()
        }
      }
      
      data class Nichts(val nichts: TypedToken<TokenTyp.NICHTS>): Ausdruck()

      data class Zeichenfolge(val zeichenfolge: TypedToken<TokenTyp.ZEICHENFOLGE>) : Ausdruck()

      data class Zahl(val zahl: TypedToken<TokenTyp.ZAHL>) : Ausdruck()

      data class Boolean(val boolean: TypedToken<TokenTyp.BOOLEAN>) : Ausdruck()

      data class Variable(val name: WortArt.Nomen) : Ausdruck() {
        override val children = sequenceOf(name)
        var konstante: Konstante? = null
      }

      data class FunktionsAufruf(
          var typArgumente: List<TypKnoten>,
          val modulPfad: List<TypedToken<TokenTyp.BEZEICHNER_GROSS>>,
          val subjekt: Ausdruck?,
          val verb: TypedToken<TokenTyp.BEZEICHNER_KLEIN>,
          val objekt: Argument?,
          val reflexivPronomen: TypedToken<TokenTyp.REFLEXIV_PRONOMEN>?,
          val präpositionsArgumente: List<PräpositionsArgumente>,
          val suffix: TypedToken<TokenTyp.BEZEICHNER_KLEIN>?
      ) : Ausdruck(), IAufruf {
        override val token = verb.toUntyped()

        override var vollerName: String? = null
        var funktionsDefinition: Definition.Funktion? = null
        var aufrufTyp: FunktionsAufrufTyp = FunktionsAufrufTyp.FUNKTIONS_AUFRUF
        val vollständigerName: String get() = modulPfad.joinToString("::") { it.wert } +
            (if (modulPfad.isEmpty()) "" else "::") + vollerName

        var hatRückgabeObjekt: kotlin.Boolean = false

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

        val argumente get() = sequence {
          if (objekt != null && !hatRückgabeObjekt) {
            yield(objekt!!)
          }
          for (präposition in präpositionsArgumente) {
            yieldAll(präposition.argumente)
          }
        }

        var rückgabeObjektMöglich: kotlin.Boolean
          private set

        init {
          rückgabeObjektMöglich = if (objekt == null) {
            false
          }
          else when (val ausdruck = objekt.ausdruck) {
            is Variable -> ausdruck.name == objekt.name
            is ObjektInstanziierung -> ausdruck.eigenschaftsZuweisungen.isEmpty() &&
                ausdruck.klasse.modulPfad.isEmpty()
                && ausdruck.klasse.name == objekt.name
            else -> false
          }
        }
      }

      data class MethodenBereich(val objekt: Ausdruck, val bereich: Bereich): Ausdruck() {
        override val children = sequenceOf(objekt, bereich)
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
          return "erstelle $artikel ${klassenName.ganzesWort(Kasus.NOMINATIV, Numerus.SINGULAR, true)}"
        }
      }

      data class Closure(val schnittstelle: TypKnoten, val bindings: List<WortArt.Nomen>, val körper: Bereich): Ausdruck() {
        override val children = sequence {
          yield(schnittstelle)
          yieldAll(bindings)
          yield(körper)
        }
      }

      data class AnonymeKlasse(val schnittstelle: TypKnoten, val bereich: Definition.ImplementierungsBereich): Ausdruck() {
        var typ: Typ.Compound.KlassenTyp.Klasse? = null

        override val children = sequence {
          yield(schnittstelle)
          yield(bereich)
        }
      }

      data class Bedingung(
          val bedingungen: MutableList<BedingungsTerm>,
          var sonst: Sonst?
      ): Ausdruck() {
        override val children = sequence {
          yieldAll(bedingungen)
          if (sonst != null) {
            yield(sonst!!)
          }
        }
      }

      data class Sonst(
          val token: TypedToken<TokenTyp.SONST>,
          val bereich: Bereich
      ): Satz() {
        override val children = sequenceOf(bereich)
      }

      data class TypÜberprüfung(
          val ausdruck: Ausdruck,
          val typ: TypKnoten,
          val ist: TypedToken<TokenTyp.ZUWEISUNG>
      ): Ausdruck() {
        override val children = sequenceOf(ausdruck, typ)
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

      data class VersucheFange(
          val versuche: TypedToken<TokenTyp.VERSUCHE>,
          val bereich: Bereich,
          val fange: List<Fange>,
          val schlussendlich: Bereich?
      ): Ausdruck() {
        override val children = sequence {
          yield(bereich)
          yieldAll(fange)
          if (schlussendlich != null) {
            yield(schlussendlich!!)
          }
        }
      }

      data class Fange(
          val fange: TypedToken<TokenTyp.FANGE>,
          val param: Definition.Parameter,
          val bereich: Bereich
      ): Satz() {
        override val children = sequenceOf(param, bereich)
      }

      data class Werfe(
          val werfe: TypedToken<TokenTyp.WERFE>,
          var ausdruck: Ausdruck
      ): Ausdruck() {
        override val children = sequenceOf(ausdruck)
      }
    }
  }

  data class Argument(
      val adjektiv: WortArt.Adjektiv?,
      val name: WortArt.Nomen,
      var ausdruck: Satz.Ausdruck
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
}