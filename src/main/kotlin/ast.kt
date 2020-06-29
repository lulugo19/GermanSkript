sealed class AST {

  // visit implementation for all the leaf nodes
  open fun visit(visitor: (AST) -> Unit) {
    visitor(this)
  }

  // Wurzelknoten
  data class Programm(val definitionen: List<Definition>, val sätze: List<Satz>) : AST() {
    override fun visit(visitor: (AST) -> Unit) {
      super.visit(visitor)
      definitionen.forEach { it.visit(visitor) }
      sätze.forEach { it.visit(visitor) }
    }
  }

  // region Blattknoten
  data class Nomen(
      val bezeichner: TypedToken<TokenTyp.BEZEICHNER_GROSS>,
      var nominativ: String? = null,
      var artikel: String? = null,
      var genus: Genus? = null,
      var numerus: Numerus? = null
  ) : AST()

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
        val rückgabeTyp: Nomen,
        val name: TypedToken<TokenTyp.BEZEICHNER_KLEIN>,
        val objekt: Parameter?,
        val präpositionsParameter: List<PräpositionsParameter>,
        val suffix: TypedToken<TokenTyp.BEZEICHNER_KLEIN>?,
        val sätze: List<Satz>
    ) : Definition()

  }

  sealed class Satz : AST() {
    data class Variablendeklaration(
        val artikel: TypedToken<TokenTyp.ARTIKEL>,
        val typ: Nomen,
        val name: Nomen,
        val zuweisungsOperator: TypedToken<TokenTyp.ZUWEISUNG>
    ) : Satz() {
      override fun visit(visitor: (AST) -> Unit) {
        super.visit(visitor)
        typ.visit(visitor)
        name.visit(visitor)
      }
    }

    data class FunktionsAufruf(val aufruf: AST.FunktionsAufruf): Satz() {
      override fun visit(visitor: (AST) -> Unit) {
        super.visit(visitor)
        aufruf.visit(visitor)
      }
    }
  }

  data class Argument(
      val artikel: TypedToken<TokenTyp.ARTIKEL>,
      val name: Nomen,
      val wert: Ausdruck
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
        _argumente.add(objekt.wert)
      }
      for (präposition in präpositionsArgumente) {
        for (argument in präposition.argumente) {
          _argumente.add(argument.wert)
        }
      }
    }
  }

  sealed class Ausdruck : AST() {
    data class Zeichenfolge(val zeichenfolge: TypedToken<TokenTyp.ZEICHENFOLGE>) : Ausdruck()

    data class Zahl(val zahl: TypedToken<TokenTyp.ZAHL>) : Ausdruck()

    data class Boolean(val boolean: TypedToken<TokenTyp.BOOLEAN>) : Ausdruck()

    data class Variable(val artikel: TypedToken<TokenTyp.ARTIKEL>?, val name: TypedToken<TokenTyp.BEZEICHNER_GROSS>) : Ausdruck()

    data class FunktionsAufruf(val aufruf: AST.FunktionsAufruf): Ausdruck() {
      override fun visit(visitor: (AST) -> Unit) {
        super.visit(visitor)
        aufruf.visit(visitor)
      }
    }

    data class BinärerAusdruck(val operator: TypedToken<TokenTyp.OPERATOR>, val links: Ausdruck, val rechts: Ausdruck, val istAnfang: kotlin.Boolean) : Ausdruck() {
      override fun visit(visitor: (AST) -> Unit) {
        super.visit(visitor)
        links.visit(visitor)
        rechts.visit(visitor)
      }
    }

    data class Minus(val ausdruck: Ausdruck) : Ausdruck() {
      override fun visit(visitor: (AST) -> Unit) {
        super.visit(visitor)
        ausdruck.visit(visitor)
      }
    }
  }
}
