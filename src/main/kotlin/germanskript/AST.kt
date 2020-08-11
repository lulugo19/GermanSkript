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
}

sealed class AST {
  open val children = emptySequence<AST>()
  var parent: AST? = null
    protected set
  var tiefe: Int = 0
    protected set

  inline fun<reified T: AST> findNodeInParents(): T? {
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

  data class Nomen(
      val vornomen: TypedToken<TokenTyp.VORNOMEN>?,
      val bezeichner: TypedToken<TokenTyp.BEZEICHNER_GROSS>
  ): AST() {
    var deklination: Deklination? = null
    var vornomenString: String? = null
    var numerus: Numerus? = null
    var fälle: EnumSet<Kasus> = EnumSet.noneOf(Kasus::class.java)

    val unveränderlich = vornomen == null || vornomen.typ ==
        TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT || vornomen.typ == TokenTyp.VORNOMEN.DEMONSTRATIV_PRONOMEN.DIESE
    val istSymbol get() = bezeichner.typ.istSymbol
    val geprüft get() = deklination != null
    val genus get() = if (istSymbol) Genus.NEUTRUM else deklination!!.genus

    val hauptWort: String get() = bezeichner.typ.hauptWort!!
    val nominativ: String get() = ganzesWort(Kasus.NOMINATIV, numerus!!)

    fun hauptWort(kasus: Kasus, numerus: Numerus): String {
      if (istSymbol) {
        return bezeichner.typ.symbol
      }
      return deklination!!.getForm(kasus, numerus)
    }

    fun ganzesWort(kasus: Kasus, numerus: Numerus): String {
      if (istSymbol) {
        return bezeichner.typ.symbol
      }
      return bezeichner.typ.ersetzeHauptWort(deklination!!.getForm(kasus, numerus))
    }
  }


  data class TypKnoten(
      val name: Nomen,
      val modulPfad: List<TypedToken<TokenTyp.BEZEICHNER_GROSS>>
  ): AST() {
    var typ: Typ? = null
    override val children = sequenceOf(name)
    val vollständigerName: String = modulPfad.joinToString("::") { it.wert } +
        (if (modulPfad.isEmpty()) "" else "::")  + name.bezeichner.wert
  }

  interface IAufruf {
    val token: Token
    val vollerName: String?
  }

  class DefinitionsContainer(): AST() {
    val deklinationen = mutableListOf<Definition.DeklinationsDefinition>()
    val funktionenOderMethoden = mutableListOf<Definition.FunktionOderMethode>()
    val konvertierungen = mutableListOf<Definition.Konvertierung>()
    val definierteTypen: MutableMap<String, Definition.Typdefinition> = mutableMapOf()
    val funktionen: MutableMap<String, Definition.FunktionOderMethode.Funktion> = mutableMapOf()
    val module = mutableMapOf<String, Definition.Modul>()
    val verwende = mutableListOf<Definition.Verwende>()
    val verwendeteModule = mutableListOf<DefinitionsContainer>()
    val verwendeteTypen = mutableMapOf<String, Definition.Typdefinition>()
    val wörterbuch = Wörterbuch()

    override val children = sequence {
      yieldAll(deklinationen)
      yieldAll(funktionenOderMethoden)
      yieldAll(konvertierungen)
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

  data class Funktion(
      val modulPfad: List<TypedToken<TokenTyp.BEZEICHNER_GROSS>>,
      val verb: TypedToken<TokenTyp.BEZEICHNER_KLEIN>,
      val objekt: Argument?,
      val reflexivPronomen: TypedToken<TokenTyp.REFLEXIV_PRONOMEN>?,
      val präpositionsArgumente: List<PräpositionsArgumente>,
      val suffix: TypedToken<TokenTyp.BEZEICHNER_KLEIN>?,
      override var vollerName: String? = null
  ) : AST(), IAufruf {
    override val token = verb.toUntyped()

    private val _argumente: MutableList<Argument> = mutableListOf()
    val argumente: List<Argument> = _argumente
    var funktionsDefinition: Definition.FunktionOderMethode.Funktion? = null
    var aufrufTyp: FunktionsAufrufTyp = FunktionsAufrufTyp.FUNKTIONS_AUFRUF
    val vollständigerName = modulPfad.joinToString("::") { it.wert } +
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

  sealed class Definition : AST() {

    data class TypUndName(
        val typKnoten: TypKnoten,
        val name: Nomen
    ): AST() {
      val istPrivat get() = typKnoten.name.vornomen!!.typ is TokenTyp.VORNOMEN.DEMONSTRATIV_PRONOMEN
      override val children = sequenceOf(typKnoten, name)
    }

    data class PräpositionsParameter(
        val präposition: Präposition,
        val parameter: List<TypUndName>
    ): AST() {
      override val children = sequence { yieldAll(parameter) }
    }


    data class Modul(val name: TypedToken<TokenTyp.BEZEICHNER_GROSS>, val definitionen: DefinitionsContainer):
        Definition() {
      override val children: Sequence<AST> = sequenceOf(definitionen)
    }

    sealed class DeklinationsDefinition: Definition() {
      data class Definition(val deklination: Deklination): DeklinationsDefinition()
      data class Duden(val wort: TypedToken<TokenTyp.BEZEICHNER_GROSS>): DeklinationsDefinition()
    }

    data class FunktionsSignatur(
        val rückgabeTyp: TypKnoten?,
        val name: TypedToken<TokenTyp.BEZEICHNER_KLEIN>,
        val reflexivPronomen: TypedToken<TokenTyp.REFLEXIV_PRONOMEN>?,
        val objekt: TypUndName?,
        val präpositionsParameter: List<PräpositionsParameter>,
        val suffix: TypedToken<TokenTyp.BEZEICHNER_KLEIN>?,
        var vollerName: String? = null
    ): Definition() {
      private val _parameter: MutableList<TypUndName> = mutableListOf()
      val parameter: List<TypUndName> = _parameter

      init {
        if (objekt != null) {
          _parameter.add(objekt)
        }
        for (präposition in präpositionsParameter) {
          _parameter.addAll(präposition.parameter)
        }
      }

      override val children = sequence {
        if (rückgabeTyp != null) {
          yield(rückgabeTyp!!)
        }
        if (objekt != null) {
          yield(objekt!!)
        }
        yieldAll(präpositionsParameter)
      }
    }

    sealed class FunktionOderMethode(): Definition() {
      data class Funktion(
          val signatur: FunktionsSignatur,
          val definition: Satz.Bereich
      ): FunktionOderMethode() {

        override val children = sequenceOf(signatur, definition)
      }

      data class Methode(
          val funktion: Funktion,
          val klasse: TypKnoten
      ): FunktionOderMethode() {
        override val children = sequence {
          yield(klasse)
          yield(funktion)
        }
      }

    }

    sealed class Typdefinition: Definition() {

      abstract val namensToken: Token

      data class Klasse(
          val typ: TypKnoten,
          val elternKlasse: TypKnoten?,
          val eigenschaften: MutableList<TypUndName>,
          val konstruktor: Satz.Bereich
      ): Typdefinition() {
        val methoden: HashMap<String, FunktionOderMethode.Methode> = HashMap()
        val konvertierungen: HashMap<String, Konvertierung> = HashMap()

        override val namensToken = typ.name.bezeichner.toUntyped()

        override val children = sequence {
          yield(typ)
          if (elternKlasse != null) {
            yield(elternKlasse!!)
          }
          yieldAll(eigenschaften)
          yield(konstruktor)
        }
      }

      data class Schnittstelle(
          val name: TypedToken<TokenTyp.BEZEICHNER_KLEIN>,
          val methodenSignaturen: List<FunktionsSignatur>
      ): Typdefinition() {

        override val namensToken = name.toUntyped()

        override val children = sequence {
          yieldAll(methodenSignaturen)
        }
      }
    }

    data class Konvertierung(
        val typ: TypKnoten,
        val klasse: TypKnoten,
        val definition: Satz.Bereich
    ): Definition() {
      override val children: Sequence<AST> = sequence {
        yield(typ)
        yield(klasse)
        yield(definition)
      }
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
    object Intern: Satz()

    sealed class SchleifenKontrolle: Satz() {
      object Fortfahren: SchleifenKontrolle()
      object Abbrechen: SchleifenKontrolle()
    }

    data class VariablenDeklaration(
        val name: Nomen,
        val neu: TypedToken<TokenTyp.NEU>?,
        val zuweisungsOperator: TypedToken<TokenTyp.ZUWEISUNG>,
        var ausdruck: Ausdruck
    ): Satz() {
      override val children = sequenceOf(name, ausdruck)

      val istEigenschaftsNeuZuweisung = name.vornomen!!.typ == TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN
      val istEigenschaft = name.vornomen!!.typ is TokenTyp.VORNOMEN.DEMONSTRATIV_PRONOMEN
    }

    data class BedingungsTerm(
        var bedingung: Ausdruck,
        val bereich: Bereich
    ): AST() {
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
        val singular: Nomen,
        val binder: Nomen,
        val liste: Ausdruck?,
        val bereich: Bereich
    ): Satz() {
      override val children = sequence {
          yield(singular)
          yield(binder)
          if (liste != null) {
            yield(liste!!)
          }
          yield(bereich)
        }
    }

    data class FunktionsAufruf(val aufruf: Funktion): Satz() {
      override val children = sequenceOf(aufruf)
    }

    data class MethodenBlock(val name: Nomen, val bereich: Bereich ): Satz() {
      override val children = sequenceOf(name, bereich)
    }

    data class Zurückgabe(val erstesToken: TypedToken<TokenTyp.BEZEICHNER_KLEIN>, var ausdruck: Ausdruck?): Satz() {
      override val children = sequence {
        if (ausdruck != null) {
          yield(ausdruck!!)
        }
      }
    }
  }

  data class Argument(
      val name: Nomen,
      var wert: Ausdruck
  ): AST() {
    override val children = sequenceOf(name, wert)
  }

  data class PräpositionsArgumente(val präposition: Präposition, val argumente: List<Argument>): AST() {
    override val children = sequence { yieldAll(argumente) }
  }

  sealed class Ausdruck : AST() {
    data class Zeichenfolge(val zeichenfolge: TypedToken<TokenTyp.ZEICHENFOLGE>) : Ausdruck()

    data class Zahl(val zahl: TypedToken<TokenTyp.ZAHL>) : Ausdruck()

    data class Boolean(val boolean: TypedToken<TokenTyp.BOOLEAN>) : Ausdruck()

    data class Variable(val name: Nomen) : Ausdruck() {
      override val children = sequenceOf(name)
    }

    data class Liste(val pluralTyp: Nomen, var elemente: List<Ausdruck>): Ausdruck() {
      override val children = sequence {
        yield(pluralTyp)
        yieldAll( elemente)
      }
    }

    data class ListenElement(
        val singular: Nomen,
        var index: Ausdruck
    ): Ausdruck() {
      override val children = sequenceOf(singular, index)
    }

    data class FunktionsAufruf(val aufruf: Funktion): Ausdruck() {
      override val children = sequenceOf(aufruf)
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
      override val token = typ.name.bezeichner.toUntyped()
      override val vollerName = "als ${typ.name}"

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

      override val token: Token = klasse.name.bezeichner.toUntyped()

      override val vollerName: String get() {
        val artikel = when (klasse.name.genus) {
          Genus.MASKULINUM -> "den"
          Genus.FEMININUM -> "die"
          Genus.NEUTRUM -> "das"
        }
        return "erstelle $artikel ${klasse.name.ganzesWort(Kasus.NOMINATIV, Numerus.SINGULAR)}"
      }
    }

    data class EigenschaftsZugriff(
        val eigenschaftsName: Nomen,
        val objekt: Ausdruck
    ): Ausdruck() {
      override val children = sequenceOf(eigenschaftsName , objekt)
    }

    data class MethodenBlockEigenschaftsZugriff(
        val eigenschaftsName: Nomen
    ): Ausdruck() {
      override val children = sequenceOf(eigenschaftsName)
    }

    data class SelbstEigenschaftsZugriff(
        val eigenschaftsName: Nomen
    ): Ausdruck() {
      override val children = sequenceOf(eigenschaftsName)
    }

    data class SelbstReferenz(val ich: TypedToken<TokenTyp.REFERENZ.ICH>): Ausdruck()
    data class MethodenBlockReferenz(val du: TypedToken<TokenTyp.REFERENZ.DU>): Ausdruck()
  }
}