fun <T: AST> List<T>.visit(breakOnNotFullyVisited: Boolean, onVisit: (AST) -> Boolean, onVisitEnd: ((AST, Boolean) -> Unit)? = null): Boolean {
  for (element in this) {
    if (!element.visit(breakOnNotFullyVisited, onVisitEnd, onVisit) && breakOnNotFullyVisited) {
      return false
    }
  }
  return true
}

sealed class AST {
  // visit implementation for all the leaf nodes
  fun visit(breakOnNotFullyVisited: Boolean, onVisitEnd: ((AST, Boolean) -> Unit)? = null, onVisit: (AST) -> Boolean): Boolean {
    val fullyVisited = visitImpl(breakOnNotFullyVisited, onVisitEnd, onVisit)
    if (onVisitEnd != null) {
      onVisitEnd(this, fullyVisited)
    }
    return fullyVisited
  }

  protected open fun visitImpl(breakOnNotFullyVisited: Boolean, onVisitEnd: ((AST, Boolean) -> Unit)?, onVisit: (AST) -> Boolean): Boolean {
    return onVisit(this)
  }

  // Wurzelknoten
  data class Programm(val definitionen: List<Definition>, val sätze: List<Satz>) : AST() {
    override fun visitImpl(breakOnNotFullyVisited: Boolean, onVisitEnd: ((AST, Boolean) -> Unit)?, onVisit: (AST) -> Boolean): Boolean {
      return super.visitImpl(breakOnNotFullyVisited, onVisitEnd, onVisit) &&
          definitionen.visit(breakOnNotFullyVisited, onVisit, onVisitEnd) &&
          sätze.visit(breakOnNotFullyVisited, onVisit, onVisitEnd)
    }
  }

  // region Blattknoten
  data class Nomen(
      val bezeichner: TypedToken<TokenTyp.BEZEICHNER_GROSS>,
      var nominativ: String? = null,
      var artikel: String? = null,
      var genus: Genus? = null,
      var numerus: Numerus? = null
  )

  data class Präposition(val präposition: TypedToken<TokenTyp.BEZEICHNER_KLEIN>) : AST() {
    val kasus = präpositionsFälle
        .getOrElse(präposition.wert) {
          throw GermanScriptFehler.SyntaxFehler.ParseFehler(präposition.toUntyped(), "Präposition")
        }
  }
  // endregion

  sealed class Definition : AST() {

    data class DeklinationsDefinition(val deklination: Deklination) : Definition()

    data class Parameter(
        val artikel: TypedToken<TokenTyp.ARTIKEL>,
        val typ: Nomen,
        val name: Nomen?
    ) {
      val paramName: Nomen get() = name ?: typ
    }

    data class PräpositionsParameter(
        val präposition: Präposition,
        val parameter: List<Parameter>
    )

    data class Funktion(
        val rückgabeTyp: Nomen?,
        val name: TypedToken<TokenTyp.BEZEICHNER_KLEIN>,
        val objekt: Parameter?,
        val präpositionsParameter: List<PräpositionsParameter>,
        val suffix: TypedToken<TokenTyp.BEZEICHNER_KLEIN>?,
        val sätze: List<Satz>
    ) : Definition() {
        override fun visitImpl(breakOnNotFullyVisited: Boolean, onVisitEnd: ((AST, Boolean) -> Unit)?, onVisit: (AST) -> Boolean): Boolean {
          return super.visitImpl(breakOnNotFullyVisited, onVisitEnd, onVisit) && sätze.visit(true, onVisit, onVisitEnd)
      }
    }
  }


  sealed class Satz : AST() {
    object Intern: Satz()

    data class VariablenDeklaration(
        val artikel: TypedToken<TokenTyp.ARTIKEL>,
        val name: Nomen,
        val zuweisungsOperator: TypedToken<TokenTyp.ZUWEISUNG>,
        val ausdruck: Ausdruck
    ): Satz() {
      override fun visitImpl(breakOnNotFullyVisited: Boolean, onVisitEnd: ((AST, Boolean) -> Unit)?, onVisit: (AST) -> Boolean): Boolean {
        return super.visitImpl(breakOnNotFullyVisited, onVisitEnd, onVisit) && ausdruck.visit(breakOnNotFullyVisited, onVisitEnd, onVisit)
      }
    }

    data class FunktionsAufruf(val aufruf: AST.FunktionsAufruf): Satz() {
      override fun visitImpl(breakOnNotFullyVisited: Boolean, onVisitEnd: ((AST, Boolean) -> Unit)?, onVisit: (AST) -> Boolean): Boolean {
        return super.visitImpl(breakOnNotFullyVisited, onVisitEnd, onVisit) &&
        aufruf.visit(breakOnNotFullyVisited, onVisitEnd, onVisit)
      }
    }
  }

  data class Argument(
      val artikel: TypedToken<TokenTyp.ARTIKEL>,
      val name: Nomen,
      val wert: Ausdruck?
  )

  data class PräpositionsArgumente(val präposition: Präposition, val argumente: List<Argument>)

  data class FunktionsAufruf(
      val verb: TypedToken<TokenTyp.BEZEICHNER_KLEIN>,
      val objekt: Argument?,
      val präpositionsArgumente: List<PräpositionsArgumente>,
      val suffix: TypedToken<TokenTyp.BEZEICHNER_KLEIN>?,
      var vollerName: String? = null
  ): AST() {
    private val _argumente: MutableList<Ausdruck> = mutableListOf()
    val argumente: List<Ausdruck> = _argumente

    init {
      if (objekt != null) {
        _argumente.add(objekt.wert ?: Ausdruck.Variable(null, objekt.name))
      }
      for (präposition in präpositionsArgumente) {
        for (argument in präposition.argumente) {
          _argumente.add(argument.wert ?: Ausdruck.Variable(null, argument.name))
        }
      }
    }
  }

  sealed class Ausdruck : AST() {
    data class Zeichenfolge(val zeichenfolge: TypedToken<TokenTyp.ZEICHENFOLGE>) : Ausdruck()

    data class Zahl(val zahl: TypedToken<TokenTyp.ZAHL>) : Ausdruck()

    data class Boolean(val boolean: TypedToken<TokenTyp.BOOLEAN>) : Ausdruck()

    data class Variable(val artikel: TypedToken<TokenTyp.ARTIKEL>?, val name: Nomen) : Ausdruck()

    data class FunktionsAufruf(val aufruf: AST.FunktionsAufruf): Ausdruck() {
      override fun visitImpl(breakOnNotFullyVisited: kotlin.Boolean, onVisitEnd: ((AST, kotlin.Boolean) -> Unit)?, onVisit: (AST) -> kotlin.Boolean): kotlin.Boolean {
        return super.visitImpl(breakOnNotFullyVisited, onVisitEnd, onVisit) &&
        aufruf.visit(breakOnNotFullyVisited, onVisitEnd, onVisit)
      }
    }

    data class BinärerAusdruck(val operator: TypedToken<TokenTyp.OPERATOR>, val links: Ausdruck, val rechts: Ausdruck, val istAnfang: kotlin.Boolean) : Ausdruck() {
      override fun visitImpl(breakOnNotFullyVisited: kotlin.Boolean, onVisitEnd: ((AST, kotlin.Boolean) -> Unit)?, onVisit: (AST) -> kotlin.Boolean): kotlin.Boolean {
        return super.visitImpl(breakOnNotFullyVisited, onVisitEnd, onVisit) &&
        links.visit(breakOnNotFullyVisited, onVisitEnd, onVisit) &&
        rechts.visit(breakOnNotFullyVisited, onVisitEnd, onVisit)
      }
    }

    data class Minus(val ausdruck: Ausdruck) : Ausdruck() {
      override fun visitImpl(breakOnNotFullyVisited: kotlin.Boolean, onVisitEnd: ((AST, kotlin.Boolean) -> Unit)?, onVisit: (AST) -> kotlin.Boolean): kotlin.Boolean {
        return super.visitImpl(breakOnNotFullyVisited, onVisitEnd, onVisit) &&
        ausdruck.visit(breakOnNotFullyVisited, onVisitEnd, onVisit)
      }
    }
  }
}
