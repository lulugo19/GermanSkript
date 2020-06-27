import java.util.*

sealed class AST {

  // visit implementation for all the leaf nodes
  open fun visit(visitor: (AST) -> Unit) {
    visitor(this)
  }

  // Wurzelknoten
  data class Programm(val definitionen: List<Definition>, val sätze: List<Satz>): AST() {
    override fun visit(visitor: (AST) -> Unit) {
      super.visit(visitor)
      definitionen.forEach{it.visit(visitor)}
      sätze.forEach{it.visit(visitor)}
    }
  }
  
  // region Blattknoten
  data class Nomen(
      val bezeichner: TypedToken<TokenTyp.BEZEICHNER_GROSS>,
      var form: Form? = null,
      var nominativ: String? = null,
      var numerus: Numerus? = null
  ): AST()

  data class Verb(val bezeichner: TypedToken<TokenTyp.BEZEICHNER_KLEIN>): AST()
  data class Adjektiv(val bezeichner: TypedToken<TokenTyp.BEZEICHNER_KLEIN>): AST()
  data class Präposition(val präposition: TypedToken<TokenTyp.BEZEICHNER_KLEIN>): AST() {
    val kasus = präpositionsFälle
        .getOrElse(präposition.wert) {
          throw GermanScriptFehler.SyntaxFehler.ParseFehler(präposition.toUntyped(), "Präposition")
        }
  }
  // endregion

  sealed class Definition: AST() {

    data class Deklination(
        val genus: Genus,
        val nominativS: TypedToken<TokenTyp.BEZEICHNER_GROSS>,
        val genitivS: TypedToken<TokenTyp.BEZEICHNER_GROSS>,
        val dativS: TypedToken<TokenTyp.BEZEICHNER_GROSS>,
        val akkusativS: TypedToken<TokenTyp.BEZEICHNER_GROSS>,
        val nominativP: TypedToken<TokenTyp.BEZEICHNER_GROSS>,
        val genitivP: TypedToken<TokenTyp.BEZEICHNER_GROSS>,
        val dativP: TypedToken<TokenTyp.BEZEICHNER_GROSS>,
        val akkusativP: TypedToken<TokenTyp.BEZEICHNER_GROSS>
    ) : Definition()

    data class Parameter(
        val artikel: TypedToken<TokenTyp.ARTIKEL>,
        val typ: Nomen,
        val name: Nomen
    ): Definition() {
      override fun visit(visitor: (AST) -> Unit) {
        super.visit(visitor)
        typ.visit(visitor)
        name.visit(visitor)
      }
    }

    data class Präposition(
        val präposition: TypedToken<TokenTyp.PRÄPOSITION>,
        val parameter: List<Parameter>
    ) : Definition() {
      override fun visit(visitor: (AST) -> Unit) {
        super.visit(visitor)
        parameter.forEach{it.visit(visitor)}
      }
    }
    
    data class Funktion(
        val rückgabeTyp: TypedToken<TokenTyp.BEZEICHNER_GROSS>,
        val name: TypedToken<TokenTyp.BEZEICHNER_KLEIN>,
        val objekt: Parameter,
        val präpositionen: List<Präposition>
    ): Definition() {
      override fun visit(visitor: (AST) -> Unit) {
        super.visit(visitor)
        objekt.visit(visitor)
        präpositionen.forEach{it.visit(visitor)}
      }
    }
  }

  sealed class Satz: AST()  {
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
  }

  sealed class Ausdruck: AST() {
    data class Zeichenfolge(val zeichenfolge: TypedToken<TokenTyp.ZEICHENFOLGE>): Ausdruck()

    data class Zahl(val zahl: TypedToken<TokenTyp.ZAHL>): Ausdruck()

    data class Boolean(val boolean: TypedToken<TokenTyp.BOOLEAN>): Ausdruck()

    data class Variable(val artikel: TypedToken<TokenTyp.ARTIKEL>, val name: TypedToken<TokenTyp.BEZEICHNER_GROSS>) : Ausdruck()

    data class BinärerAusdruck(val operator: TypedToken<TokenTyp.OPERATOR>, val links: Ausdruck, val rechts: Ausdruck): Ausdruck() {
      override fun visit(visitor: (AST) -> Unit) {
        super.visit(visitor)
        links.visit(visitor)
        rechts.visit(visitor)
      }
    }

    data class Minus(val ausdruck: Ausdruck): Ausdruck() {
      override fun visit(visitor: (AST) -> Unit) {
        super.visit(visitor)
        ausdruck.visit(visitor)
      }
    }
  }
}