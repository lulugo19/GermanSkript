import java.util.*

enum class ASTKnotenID {
  PROGRAMM,
  AUSDRUCK,
  VARIABLEN_DEKLARATION
}

class Parser(val quellCode: String) {
  fun parse(): AST.Programm {
    TODO()
  }
}


private sealed class SubParser<T: AST>() {
  private var stack: Stack<ASTKnotenID>? = null
  private var tokens :Peekable<Token>? = null
  protected var currentToken: Token? = null

  fun parse(tokens: Peekable<Token>, stack: Stack<ASTKnotenID>): T {
    this.tokens = tokens
    this.stack = stack
    stack.push(id)
    val result = parseImpl()
    stack.pop()
    return result
  }

  protected abstract val id: ASTKnotenID
  protected abstract fun parseImpl(): T

  // region Hilfsmethoden
  protected fun next(): Token = tokens!!.next()!!.also { currentToken = it }
  protected fun peek(): Token = tokens!!.peek()!!
  protected fun peekType(): TokenTyp = tokens!!.peek()!!.typ
  protected fun peekDouble(): Token = tokens!!.peekDouble()!!
  protected fun peekDoubleType(): TokenTyp = tokens!!.peekDouble()!!.typ

  protected inline fun <reified T : TokenTyp>expect(erwartet: String): TypedToken<T> {
    val nextToken = next()
    if (nextToken.typ is T) {
      return nextToken.toTyped()
    } else {
      throw GermanScriptFehler.SyntaxFehler.ParseFehler(nextToken, erwartet)
    }
  }

  protected fun <T: AST> subParse(subParser: SubParser<T>): T {
    return subParser.parse(tokens!!, stack!!)
  }

  protected fun überspringeLeereZeilen() {
    while (peekType() is TokenTyp.NEUE_ZEILE) {
      next()
    }
  }

  protected fun <T: AST> parseKommaListe(elementParser: () -> T): List<T> {
    val liste = mutableListOf<T>(elementParser())
    while (peekType() is TokenTyp.KOMMA) {
      next()
      liste.add(elementParser())
    }
    return liste
  }
  // endregion

  class Programm(): SubParser<AST.Programm>() {
    override val id: ASTKnotenID
      get() = ASTKnotenID.PROGRAMM

    override fun parseImpl(): AST.Programm {
      val definitionen = mutableListOf<AST.Definition>()
      val sätze = mutableListOf<AST.Satz>()

      when (peekType()) {
        is TokenTyp.ARTIKEL -> sätze += subParse(Satz.Variablendeklaration())
      }

      return AST.Programm(definitionen, sätze)
    }

  }

  class Ausdruck(): SubParser<AST.Ausdruck>() {
    override val id: ASTKnotenID
      get() = ASTKnotenID.AUSDRUCK

    override fun parseImpl(): AST.Ausdruck {
      return parseBinärerAusdruck(0.0)
    }

    // pratt parsing: https://matklad.github.io/2020/04/13/simple-but-powerful-pratt-parsing.html
    private fun parseBinärerAusdruck(minBindungskraft: Double) : AST.Ausdruck {
      var leftHandSide = parseEinzelnerAusdruck()

      loop@ while (true) {
        val operator = when (val tokenTyp = peekType()) {
          is TokenTyp.OPERATOR -> tokenTyp.operator
          else -> break@loop
        }
        val bindungsKraft = operator.bindungsKraft
        val linkeBindungsKraft = bindungsKraft + if (operator.assoziativität == Assoziativität.RECHTS) 0.1 else 0.0
        val rechteBindungsKraft = bindungsKraft + if (operator.assoziativität == Assoziativität.LINKS) 0.1 else 0.0
        if (linkeBindungsKraft < minBindungskraft) {
          break
        }
        val operatorToken = expect<TokenTyp.OPERATOR>("Operator")
        val rightHandSide = parseBinärerAusdruck(rechteBindungsKraft)
        leftHandSide = AST.Ausdruck.BinärerAusdruck(operatorToken, leftHandSide, rightHandSide, minBindungskraft == 0.0)
      }

      return leftHandSide
    }

    private fun parseEinzelnerAusdruck(): AST.Ausdruck {
      return when (val tokenTyp = peekType()) {
        is TokenTyp.OFFENE_KLAMMER -> {
          next()
          parseImpl().also { expect<TokenTyp.GESCHLOSSENE_KLAMMER>("'('") }
        }
        is TokenTyp.OPERATOR -> {
          if (currentToken != null &&
              currentToken!!.typ is TokenTyp.OPERATOR &&
              currentToken!!.toTyped<TokenTyp.OPERATOR>().typ.operator == Operator.MINUS) {
            throw GermanScriptFehler.SyntaxFehler.ParseFehler(next(), null, "Es dürfen keine zwei '-' aufeinander folgen.")
          } else if (tokenTyp.operator != Operator.MINUS) {
            throw GermanScriptFehler.SyntaxFehler.ParseFehler(next(), null)
          } else {
            next()
            AST.Ausdruck.Minus(parseEinzelnerAusdruck())
          }
        }
        is TokenTyp.ARTIKEL -> {
          val artikel = expect<TokenTyp.ARTIKEL.BESTIMMT>("bestimmter Artikel")
          val name = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner")
          AST.Ausdruck.Variable(artikel, name)
        }
        is TokenTyp.ZEICHENFOLGE -> AST.Ausdruck.Zeichenfolge(next().toTyped())
        is TokenTyp.ZAHL -> AST.Ausdruck.Zahl(next().toTyped())
        is TokenTyp.BOOLEAN -> AST.Ausdruck.Boolean(next().toTyped())
        else -> throw GermanScriptFehler.SyntaxFehler.ParseFehler(next())
      }
    }
  }

  sealed class Satz<T: AST.Satz>(): SubParser<T>() {
    class Variablendeklaration: Satz<AST.Satz.Variablendeklaration>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.VARIABLEN_DEKLARATION

      override fun parseImpl(): AST.Satz.Variablendeklaration {
        val artikel = expect<TokenTyp.ARTIKEL>("Artikel")
        val name = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner")
        val zuweisung = expect<TokenTyp.ZUWEISUNG>("Zuweisung")
        val ausdruck = subParse(Ausdruck())
        return AST.Satz.Variablendeklaration(artikel, AST.Nomen(name), zuweisung, ausdruck)
      }

    }
  }

  sealed class Definition<T: AST.Definition>(): SubParser<T>() {

  }
}

fun main() {

}