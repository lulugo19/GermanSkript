import java.lang.reflect.Parameter
import java.util.*

fun <T: AST> List<T>.visit(onVisit: (AST) -> Boolean): Boolean {
  for (element in this) {
    element.visit(onVisit)
  }
  return true
}

sealed class AST {
  open val children = emptySequence<AST>()
  var parent: AST? = null
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

  protected fun setParentForChildren() {
    for (child in children) {
      child.parent = this
      child.setParentForChildren()
    }
  }

  // visit implementation for all the leaf nodes
  fun visit(onVisit: (AST) -> Boolean) {
    if (onVisit(this)) {
      for (child in children) {
        child.visit(onVisit)
      }
    }
  }

  // Wurzelknoten
  data class Programm(val definitionen: List<Definition>, val sätze: List<Satz>) : AST() {
    override val children: Sequence<AST>
      get() = sequence {
        yieldAll(definitionen)
        yieldAll(sätze)
      }

    init {
      // go through the whole AST and set the parents
      setParentForChildren()
    }
  }

  data class Nomen(
      val vornomen: TypedToken<TokenTyp.VORNOMEN>?,
      val bezeichner: TypedToken<TokenTyp.BEZEICHNER_GROSS>
  ) {
    var nominativ: String? = null
    var nominativSingular: String? = null
    var nominativPlural: String? = null
    var vornomenString: String? = null
    var genus: Genus? = null
    var numerus: Numerus? = null
    var fälle: EnumSet<Kasus> = EnumSet.noneOf(Kasus::class.java)

    val geprüft = nominativ != null
  }


  data class TypKnoten(
      val name: Nomen,
      var typ: Typ? = null
  )

  data class Präposition(val präposition: TypedToken<TokenTyp.BEZEICHNER_KLEIN>) : AST() {
    val fälle = präpositionsFälle
        .getOrElse(präposition.wert) {
          throw GermanScriptFehler.SyntaxFehler.ParseFehler(präposition.toUntyped(), "Präposition")
        }
  }

  sealed class Definition : AST() {

    sealed class DeklinationsDefinition: Definition() {
      data class Definition(val deklination: Deklination): DeklinationsDefinition()
      data class Duden(val wort: TypedToken<TokenTyp.BEZEICHNER_GROSS>): DeklinationsDefinition()
    }

    data class TypUndName(
        val typKnoten: TypKnoten,
        val name: Nomen
    )

    data class PräpositionsParameter(
        val präposition: Präposition,
        val parameter: List<TypUndName>
    )

    data class Funktion(
        val rückgabeTyp: TypKnoten?,
        val name: TypedToken<TokenTyp.BEZEICHNER_KLEIN>,
        val objekt: TypUndName?,
        val präpositionsParameter: List<PräpositionsParameter>,
        val suffix: TypedToken<TokenTyp.BEZEICHNER_KLEIN>?,
        val sätze: List<Satz>,
        var vollerName: String? = null
    ) : Definition() {

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

      override val children: Sequence<AST>
        get() = sequence {
          yieldAll(sätze)
        }
    }

    data class Klasse(
        val name: Nomen,
        val elternKlasse: TypKnoten?,
        val felder: List<TypUndName>,
        val konstruktor: List<Satz>
    ): AST.Definition() {
      override val children: Sequence<AST>
        get() = sequence {
          yieldAll(konstruktor)
        }
    }
  }


  sealed class Satz : AST() {
    object Intern: Satz()

    sealed class SchleifenKontrolle: Satz() {
      object Fortfahren: SchleifenKontrolle()
      object Abbrechen: SchleifenKontrolle()
    }

    data class VariablenDeklaration(
        val name: Nomen,
        val zuweisungsOperator: TypedToken<TokenTyp.ZUWEISUNG>,
        val ausdruck: Ausdruck
    ): Satz() {
      override val children: Sequence<AST> get() = sequenceOf(ausdruck)
    }

    data class BedingungsTerm(
        val bedingung: Ausdruck,
        val sätze: List<Satz>
    ): AST() {
      override val children: Sequence<AST>
        get() = sequence {
          yield(bedingung)
          yieldAll(sätze)
        }
    }

    data class Bedingung(
        val bedingungen: List<BedingungsTerm>,
        val sonst: List<Satz>?
    ): Satz() {
      override val children: Sequence<AST>
        get() = sequence {
          yieldAll(bedingungen)
          if (sonst != null) {
            yieldAll(sonst!!)
          }
        }
    }

    data class SolangeSchleife(
        val bedingung: BedingungsTerm
    ) : Satz() {
      override val children: Sequence<AST>
        get() = sequence {
          yield(bedingung)
        }
    }

    data class FürJedeSchleife(
        val binder: Nomen,
        val singular: Nomen?,
        val liste: Ausdruck.Liste?,
        val sätze: List<Satz>
    ): Satz() {
      override val children: Sequence<AST>
        get() = sequence {
          if (liste != null) {
            yield(liste!!)
          }
          yieldAll(sätze)
        }
    }

    data class FunktionsAufruf(val aufruf: AST.FunktionsAufruf): Satz() {
      override val children: Sequence<AST> get() = sequenceOf(aufruf)
    }

    data class Zurückgabe(val ausdruck: Ausdruck): Satz() {
      override val children: Sequence<AST> get() = sequenceOf(ausdruck)
    }
  }

  data class Argument(
      val name: Nomen,
      val wert: Ausdruck
  ): AST() {
    override val children: Sequence<AST>
      get() = sequenceOf(wert)
  }

  data class PräpositionsArgumente(val präposition: Präposition, val argumente: List<Argument>): AST() {
    override val children: Sequence<AST>
      get() = sequence {
        yieldAll(argumente)
      }
  }

  data class FunktionsAufruf(
      val verb: TypedToken<TokenTyp.BEZEICHNER_KLEIN>,
      val objekt: Argument?,
      val präpositionsArgumente: List<PräpositionsArgumente>,
      val suffix: TypedToken<TokenTyp.BEZEICHNER_KLEIN>?,
      var vollerName: String? = null
  ): AST() {
    private val _argumente: MutableList<Argument> = mutableListOf()
    val argumente: List<Argument> = _argumente

    init {
      if (objekt != null) {
        _argumente.add(objekt)
      }
      for (präposition in präpositionsArgumente) {
        _argumente.addAll(präposition.argumente)
      }
    }

    override val children: Sequence<AST>
      get() = sequence {
        if (objekt != null) {
          yield(objekt!!)
        }
        yieldAll(präpositionsArgumente)
      }
  }

  sealed class Ausdruck : AST() {
    data class Zeichenfolge(val zeichenfolge: TypedToken<TokenTyp.ZEICHENFOLGE>) : Ausdruck()

    data class Zahl(val zahl: TypedToken<TokenTyp.ZAHL>) : Ausdruck()

    data class Boolean(val boolean: TypedToken<TokenTyp.BOOLEAN>) : Ausdruck()

    data class Variable(val name: Nomen) : Ausdruck()

    data class Liste(val pluralTyp: Nomen, val elemente: List<Ausdruck>): Ausdruck() {
      override val children: Sequence<AST>
        get() = sequence {
          yieldAll(elemente)
        }
    }

    data class ListenElement(
        val singular: Nomen,
        val index: Ausdruck
    ): Ausdruck() {
      override val children: Sequence<AST> get() = sequenceOf(index)
    }

    data class FunktionsAufruf(val aufruf: AST.FunktionsAufruf): Ausdruck() {
      override val children: Sequence<AST> get() = sequenceOf(aufruf)
    }

    data class BinärerAusdruck(
        val operator: TypedToken<TokenTyp.OPERATOR>,
        val links: Ausdruck,
        val rechts: Ausdruck,
        val istAnfang: kotlin.Boolean) : Ausdruck() {
      override val children: Sequence<AST> get() = sequenceOf(links, rechts)
    }

    data class Minus(val ausdruck: Ausdruck) : Ausdruck() {
      override val children: Sequence<AST> get() = sequenceOf(ausdruck)
    }

    data class Konvertierung(
        val ausdruck: Ausdruck,
        val typ: TypKnoten
    ):Ausdruck()
  }
}