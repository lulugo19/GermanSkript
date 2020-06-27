class Parser(code: String) {
  private val tokens = Peekable(Lexer(code).tokeniziere().iterator())
  private var currentToken: Token? = null

  private fun next(): Token = tokens.next()!!.also { currentToken = it }
  private fun peek(): Token = tokens.peek()!!
  private fun peekType(): TokenTyp = tokens.peek()!!.typ
  private fun peekDouble(): Token = tokens.peekDouble()!!
  private fun peekDoubleType(): TokenTyp = tokens.peekDouble()!!.typ

  enum class Bereiche {
    Schleife,
    Bedingung,
  }

  private inline fun <reified T : TokenTyp>expect(erwartet: String): TypedToken<T> {
    val nextToken = next()
    if (nextToken.typ is T) {
      return nextToken.toTyped()
    } else {
      throw GermanScriptFehler.SyntaxFehler.ParseFehler(nextToken, erwartet)
    }
  }

  fun parse(): AST.Programm {
    while (true) {
      TODO()
    }
  }

  private fun überspringeLeereZeilen() {
    while (peekType() is TokenTyp.NEUE_ZEILE) {
      next()
    }
  }

  // region Ausdrücke
  private fun parseAusdruck() = parseBinärerAusdruck(0.0)

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
      leftHandSide = AST.Ausdruck.BinärerAusdruck(operatorToken, leftHandSide, rightHandSide)
    }

    return leftHandSide
  }

  private fun parseEinzelnerAusdruck(): AST.Ausdruck {
    return when (val tokenTyp = peekType()) {
      is TokenTyp.OFFENE_KLAMMER -> {
        next()
        parseAusdruck().also { expect<TokenTyp.GESCHLOSSENE_KLAMMER>("'('") }
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
        val artikel = expect<TokenTyp.ARTIKEL>("bestimmter Artikel")
        val name = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner")
        AST.Ausdruck.Variable(artikel, name)
      }
      is TokenTyp.ZEICHENFOLGE -> AST.Ausdruck.Zeichenfolge(next().toTyped())
      is TokenTyp.ZAHL -> AST.Ausdruck.Zahl(next().toTyped())
      is TokenTyp.BOOLEAN -> AST.Ausdruck.Boolean(next().toTyped())
      else -> throw GermanScriptFehler.SyntaxFehler.ParseFehler(next())
    }
  }

  // endregion

  // region Sätze
  private fun parseSätze(): List<AST.Satz> {
    überspringeLeereZeilen()
    while (true) {
      TODO()
    }
  }

  // endregion

  // region Definitionen
  private fun parseFunktion(): AST.Definition.Funktion {
    TODO()
  }
  // endregion
}

fun main() {

}