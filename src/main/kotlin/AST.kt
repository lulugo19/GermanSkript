import java.util.*

fun <T: AST> List<T>.visit(onVisit: (AST) -> Boolean): Boolean {
  for (element in this) {
    element.visit(onVisit)
  }
  return true
}

sealed class AST {
  // visit implementation for all the leaf nodes
  open fun visit(onVisit: (AST) -> Boolean) {
    onVisit(this)
  }


  // Wurzelknoten
  data class Programm(val definitionen: List<Definition>, val sätze: List<Satz>) : AST() {
    override fun visit(onVisit: (AST) -> Boolean) {
      if (onVisit(this)) {
        definitionen.visit(onVisit)
        sätze.visit(onVisit)
      }
    }
  }

  data class Nomen(
      val bezeichner: TypedToken<TokenTyp.BEZEICHNER_GROSS>
  ) {
    var nominativ: String? = null
    var artikel: String? = null
    var genus: Genus? = null
    var numerus: Numerus? = null
    var fälle: EnumSet<Kasus> = EnumSet.noneOf(Kasus::class.java)
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

    data class Parameter(
        val artikel: TypedToken<TokenTyp.ARTIKEL>,
        val typKnoten: TypKnoten,
        val name: Nomen?
    ) {
      val paramName: Nomen get() = name ?: typKnoten.name
    }

    data class PräpositionsParameter(
        val präposition: Präposition,
        val parameter: List<Parameter>
    )

    data class Funktion(
        val rückgabeTyp: TypKnoten?,
        val name: TypedToken<TokenTyp.BEZEICHNER_KLEIN>,
        val objekt: Parameter?,
        val präpositionsParameter: List<PräpositionsParameter>,
        val suffix: TypedToken<TokenTyp.BEZEICHNER_KLEIN>?,
        val sätze: List<Satz>,
        var vollerName: String? = null
    ) : Definition() {
        val parameter: List<Parameter> get() {
          val typen = mutableListOf<Parameter>()
          if (objekt != null) {
            typen.add(objekt)
          }
          for (präposition in präpositionsParameter) {
            for (param in präposition.parameter) {
              typen.add(param)
            }
          }
          return typen
        }

        override fun visit(onVisit: (AST) -> Boolean) {
          if (onVisit(this)) {
            sätze.visit(onVisit)
          }
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
        val artikel: TypedToken<TokenTyp.ARTIKEL>,
        val name: Nomen,
        val zuweisungsOperator: TypedToken<TokenTyp.ZUWEISUNG>,
        val ausdruck: Ausdruck
    ): Satz() {
      override fun visit(onVisit: (AST) -> Boolean) {
        if (onVisit(this)) {
          ausdruck.visit(onVisit)
        }
      }
    }

    data class BedingungsTerm(
        val bedingung: Ausdruck,
        val sätze: List<Satz>
    ): Satz() {
      override fun visit(onVisit: (AST) -> Boolean) {
        if (onVisit(this)) {
          bedingung.visit(onVisit)
          sätze.visit(onVisit)
        }
      }
    }

    data class Bedingung(
        val bedingungen: List<BedingungsTerm>,
        val sonst: List<Satz>?
    ): Satz() {
      override fun visit(onVisit: (AST) -> Boolean) {
        if (onVisit(this)) {
          bedingungen.visit(onVisit)
          sonst?.visit(onVisit)
        }
      }
    }

    data class SolangeSchleife(
        val bedingung: Ausdruck,
        val sätze: List<Satz>
    ) : Satz() {
      override fun visit(onVisit: (AST) -> Boolean) {
        if (onVisit(this)) {
          bedingung.visit(onVisit)
          sätze.visit(onVisit)
        }
      }
    }

    data class FunktionsAufruf(val aufruf: AST.FunktionsAufruf): Satz() {
      override fun visit(onVisit: (AST) -> Boolean) {
        aufruf.visit(onVisit)
      }
    }

    data class Zurückgabe(val ausdruck: Ausdruck): Satz() {
      override fun visit(onVisit: (AST) -> Boolean) {
        if (onVisit(this)) {
          ausdruck.visit(onVisit)
        }
      }
    }
  }

  data class Argument(
      val artikel: TypedToken<TokenTyp.ARTIKEL>,
      val name: Nomen,
      val wert: Ausdruck?
  ): AST() {
    override fun visit(onVisit: (AST) -> Boolean) {
      if (onVisit(this) && wert != null) {
        wert.visit(onVisit)
      }
    }
  }

  data class PräpositionsArgumente(val präposition: Präposition, val argumente: List<Argument>): AST() {
    override fun visit(onVisit: (AST) -> Boolean) {
      if (onVisit(this)) {
        argumente.visit(onVisit)
      }
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
        for (argument in präposition.argumente) {
          _argumente.add(argument)
        }
      }
    }

    override fun visit(onVisit: (AST) -> Boolean) {
      if (onVisit(this)) {
        objekt?.visit(onVisit)
        präpositionsArgumente.visit(onVisit)
      }
    }
  }

  sealed class Ausdruck : AST() {
    data class Zeichenfolge(val zeichenfolge: TypedToken<TokenTyp.ZEICHENFOLGE>) : Ausdruck()

    data class Zahl(val zahl: TypedToken<TokenTyp.ZAHL>) : Ausdruck()

    data class Boolean(val boolean: TypedToken<TokenTyp.BOOLEAN>) : Ausdruck()

    data class Variable(val artikel: TypedToken<TokenTyp.ARTIKEL>?, val name: Nomen) : Ausdruck()

    data class FunktionsAufruf(val aufruf: AST.FunktionsAufruf): Ausdruck() {
      override fun visit(onVisit: (AST) -> kotlin.Boolean){
        aufruf.visit(onVisit)
      }
    }

    data class BinärerAusdruck(val operator: TypedToken<TokenTyp.OPERATOR>, val links: Ausdruck, val rechts: Ausdruck, val istAnfang: kotlin.Boolean) : Ausdruck() {
      override fun visit(onVisit: (AST) -> kotlin.Boolean) {
        if (onVisit(this)) {
          links.visit(onVisit)
          rechts.visit(onVisit)
        }
      }
    }

    data class Minus(val ausdruck: Ausdruck) : Ausdruck() {
      override fun visit(onVisit: (AST) -> kotlin.Boolean) {
        if (onVisit(this)) {
          ausdruck.visit(onVisit)
        }
      }
    }
  }
}