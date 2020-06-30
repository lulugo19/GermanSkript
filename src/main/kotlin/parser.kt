import java.util.*

enum class ASTKnotenID {
  PROGRAMM,
  AUSDRUCK,
  VARIABLEN_DEKLARATION,
  FUNKTIONS_AUFRUF,
  FUNKTIONS_DEFINITION,
  INTERN,
  DEKLINATION
}

class Parser(dateiPfad: String): PipelineComponent(dateiPfad) {
  fun parse(): AST.Programm {
    val tokens = Peekable(Lexer(dateiPfad).tokeniziere().iterator())
    val ast = SubParser.Programm().parse(tokens, Stack())
    if (tokens.peek()!!.typ !is TokenTyp.EOF) {
      throw GermanScriptFehler.SyntaxFehler.ParseFehler(tokens.next()!!, "EOF")
    }
    return ast
  }
}


private sealed class SubParser<T: AST>() {
  private var stack: Stack<ASTKnotenID>? = null
  private var tokens: Peekable<Token>? = null
  protected var currentToken: Token? = null

  fun parse(tokens: Peekable<Token>, stack: Stack<ASTKnotenID>): T {
    this.tokens = tokens
    this.stack = stack
    bewacheKnoten()
    stack.push(id)
    val result = parseImpl()
    stack.pop()
    return result
  }

  protected abstract val id: ASTKnotenID
  protected abstract fun parseImpl(): T
  protected open fun bewacheKnoten() {
    // do nothing
  }

  protected val isRoot: Boolean get() = stack!!.empty()
  protected val depth: Int get() = stack!!.size
  protected val parentNodeId: ASTKnotenID get() = stack!!.peek()

  // region Hilfsmethoden
  protected fun next(): Token = tokens!!.next()!!.also { currentToken = it }
  protected fun peek(): Token = tokens!!.peek()!!
  protected fun peekType(): TokenTyp = tokens!!.peek()!!.typ
  protected fun peekDouble(): Token = tokens!!.peekDouble()!!
  protected fun peekDoubleType(): TokenTyp = tokens!!.peekDouble()!!.typ
  protected fun hierarchyContainsNode(knotenId: ASTKnotenID) = stack!!.contains(knotenId)
  protected fun hierarchyContainsAnyNode(knotenIds: Array<ASTKnotenID>) = stack!!.any {knotenIds.contains(it)}


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

  protected inline fun <ElementT, reified StartTokenT> parseKommaListe(elementParser: () -> ElementT, canBeEmpty: Boolean): List<ElementT> {
    if (canBeEmpty && peekType() !is StartTokenT) {
      return emptyList()
    }
    val liste = mutableListOf(elementParser())
    while (peekType() is TokenTyp.KOMMA) {
      next()
      liste.add(elementParser())
    }
    return liste
  }

  protected inline fun <reified T: TokenTyp> parseKommaListe(erwartet: String, canBeEmpty: Boolean): List<TypedToken<T>> {
    if (canBeEmpty && peekType() !is T) {
      return emptyList()
    }
    val liste = mutableListOf<TypedToken<T>>()
    while (peekType() is TokenTyp.KOMMA) {
      next()
      liste.add(expect(erwartet))
    }
    return liste
  }

  protected inline fun <ElementT, ParserT, reified SeperatorT: TokenTyp> parseSeperatorFirstCombineList(
      expectedSeperator: String,
      canBeEmpty: Boolean,
      parser: () -> ParserT,
      combiner: (TypedToken<SeperatorT>, ParserT) -> ElementT
  ): List<ElementT> {
    if (canBeEmpty && peekType() !is SeperatorT) {
      return emptyList()
    }
    val list = mutableListOf<ElementT>()
    while (peekType() is SeperatorT) {
      list += combiner(expect(expectedSeperator), parser())
    }
    return list
  }

  protected inline fun <ElementT, reified TokenT> parseOptional(parser: () -> ElementT): ElementT? {
    return if (peekType() is TokenT) {
      parser()
    } else {
      null
    }
  }

  protected inline fun<reified T: TokenTyp> parseOptional(): TypedToken<T>? {
    return if (peekType() is T) {
      expect<T>("nicht wichtig")
    } else {
      null
    }
  }

  protected fun<T: AST> parseBereich(parser: () -> T): T {
    expect<TokenTyp.DOPPELPUNKT>(":")
    val result = parser()
    expect<TokenTyp.PUNKT>(".")
    return result
  }
  // endregion

  class Programm(): SubParser<AST.Programm>() {
    override val id: ASTKnotenID
      get() = ASTKnotenID.PROGRAMM

    override fun parseImpl(): AST.Programm {
      val definitionen = mutableListOf<AST.Definition>()
      val sätze = mutableListOf<AST.Satz>()

      loop@ while (true) {
        überspringeLeereZeilen()
        when {
          parseSatz()?.also { sätze += it } != null -> Unit
          parseDefinition()?.also { definitionen += it } != null -> Unit
          else -> when (peekType()) {
            is TokenTyp.EOF, TokenTyp.PUNKT -> break@loop
            else -> throw GermanScriptFehler.SyntaxFehler.ParseFehler(next())
          }
        }
      }
      return AST.Programm(definitionen, sätze)
    }

    fun parseSatz(): AST.Satz? {
      return when (peekType()) {
        is TokenTyp.INTERN -> subParse(Satz.Intern())
        is TokenTyp.ARTIKEL -> subParse(Satz.VariablenDeklaration())
        is TokenTyp.BEZEICHNER_KLEIN -> subParse(Satz.FunktionsAufruf())
        else -> null
      }
    }

    fun parseDefinition(): AST.Definition? {
      return when(peekType()) {
        is TokenTyp.VERB -> subParse(Definition.Funktion())
        is TokenTyp.DEKLINATION -> subParse(Definition.DeklinationsDefinition())
        else -> null
      }
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
          val name = AST.Nomen(expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner"))
          AST.Ausdruck.Variable(artikel, name)
        }
        is TokenTyp.ZEICHENFOLGE -> AST.Ausdruck.Zeichenfolge(next().toTyped())
        is TokenTyp.ZAHL -> AST.Ausdruck.Zahl(next().toTyped())
        is TokenTyp.BOOLEAN -> AST.Ausdruck.Boolean(next().toTyped())
        is TokenTyp.BEZEICHNER_KLEIN -> AST.Ausdruck.FunktionsAufruf(subParse(FunktionsAufruf()))
        else -> throw GermanScriptFehler.SyntaxFehler.ParseFehler(next())
      }
    }
  }

  class FunktionsAufruf(): SubParser<AST.FunktionsAufruf>() {
    override val id: ASTKnotenID
      get() = ASTKnotenID.FUNKTIONS_AUFRUF

    override fun parseImpl(): AST.FunktionsAufruf {
      val verb = expect<TokenTyp.BEZEICHNER_KLEIN>("bezeichner")
      val objekt = parseOptional<AST.Argument, TokenTyp.ARTIKEL>(::parseArgument)
      val präpositionen = parsePräpositionsArgumente()
      val suffix = parseOptional<TokenTyp.BEZEICHNER_KLEIN>()
      return AST.FunktionsAufruf(verb, objekt, präpositionen, suffix)
    }

    fun parseArgument(): AST.Argument {
      val artikel = expect<TokenTyp.ARTIKEL.BESTIMMT>("bestimmter Artikel")
      val name = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner")
      val wert = when (peekType()) {
        is TokenTyp.NEUE_ZEILE, is TokenTyp.KOMMA, is TokenTyp.BEZEICHNER_KLEIN -> null
        is TokenTyp.BEZEICHNER_GROSS -> AST.Ausdruck.Variable(null, AST.Nomen(next().toTyped()))
        else -> subParse(Ausdruck())
      }
      return AST.Argument(artikel, AST.Nomen(name), wert)
    }

    fun parsePräpositionsArgumente(): List<AST.PräpositionsArgumente> {
      return parseSeperatorFirstCombineList<AST.PräpositionsArgumente, List<AST.Argument>, TokenTyp.BEZEICHNER_KLEIN>(
          "Präposition",
          true,
          {parseKommaListe<AST.Argument, TokenTyp.ARTIKEL.BESTIMMT>(::parseArgument, false)}
      ) {
        präposition, argumente -> AST.PräpositionsArgumente(AST.Präposition(präposition), argumente)
      }
    }
  }

  sealed class Satz<T: AST.Satz>(): SubParser<T>() {
    class Intern: Satz<AST.Satz.Intern>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.INTERN

      override fun parseImpl(): AST.Satz.Intern {
        expect<TokenTyp.INTERN>("intern")
        if (peekType() !is TokenTyp.PUNKT) {
          throw GermanScriptFehler.SyntaxFehler.ParseFehler(next(), ".")
        }
        return AST.Satz.Intern
      }

    }

    class VariablenDeklaration: Satz<AST.Satz.VariablenDeklaration>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.VARIABLEN_DEKLARATION

      override fun parseImpl(): AST.Satz.VariablenDeklaration {
        val artikel = expect<TokenTyp.ARTIKEL>("Artikel")
        val name = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner")
        val zuweisung = expect<TokenTyp.ZUWEISUNG>("Zuweisung")
        val ausdruck = subParse(Ausdruck())
        return AST.Satz.VariablenDeklaration(artikel, AST.Nomen(name), zuweisung, ausdruck)
      }
    }

    class FunktionsAufruf: Satz<AST.Satz.FunktionsAufruf>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.FUNKTIONS_AUFRUF

      override fun parseImpl(): AST.Satz.FunktionsAufruf = AST.Satz.FunktionsAufruf(subParse(SubParser.FunktionsAufruf()))
    }
  }

  sealed class Definition<T: AST.Definition>(): SubParser<T>() {

    override fun bewacheKnoten() {
      // eine Definition kann nur im globalen Programm geschrieben werden
      if (depth != 1) {
        throw GermanScriptFehler.SyntaxFehler.UngültigerBereich(next(), "Definitionen können nur im globalem Bereich geschrieben werden.")
      }
    }

    class DeklinationsDefinition: Definition<AST.Definition.DeklinationsDefinition>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.DEKLINATION

      override fun parseImpl(): AST.Definition.DeklinationsDefinition {
        expect<TokenTyp.DEKLINATION>("Deklination")
        val genus = expect<TokenTyp.GENUS>("Genus").typ.genus
        expect<TokenTyp.SINGULAR>("'Singular'")
        expect<TokenTyp.OFFENE_KLAMMER>("'('")
        val nominativS = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner").wert
        expect<TokenTyp.KOMMA>("','")
        val genitivS = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner").wert
        expect<TokenTyp.KOMMA>("','")
        val dativS = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner").wert
        expect<TokenTyp.KOMMA>("','")
        val akkusativS = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner").wert
        expect<TokenTyp.GESCHLOSSENE_KLAMMER>("')'")
        expect<TokenTyp.PLURAL>("'Plural'")
        expect<TokenTyp.OFFENE_KLAMMER>("'('")
        val nominativP = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner").wert
        expect<TokenTyp.KOMMA>("','")
        val genitivP = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner").wert
        expect<TokenTyp.KOMMA>("','")
        val dativP = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner").wert
        expect<TokenTyp.KOMMA>("','")
        val akkusativP = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner").wert
        expect<TokenTyp.GESCHLOSSENE_KLAMMER>("')'")

        return AST.Definition.DeklinationsDefinition(Deklination(genus,
            arrayOf(nominativS, genitivS, dativS, akkusativS, nominativP, genitivP, dativP, akkusativP)))
      }

    }

    class Funktion: Definition<AST.Definition.Funktion>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.FUNKTIONS_DEFINITION

      override fun parseImpl(): AST.Definition.Funktion {
        expect<TokenTyp.VERB>("Verb")
        val rückgabeTyp = parseOptional<TypedToken<TokenTyp.BEZEICHNER_GROSS>, TokenTyp.OFFENE_KLAMMER>(::parseRückgabeTyp)
        val name = expect<TokenTyp.BEZEICHNER_KLEIN>("bezeichner")
        val objekt = parseOptional<AST.Definition.Parameter, TokenTyp.ARTIKEL.BESTIMMT>(::parseParameter)
        val präpositionsParameter = parsePräpositionsParameter()
        val suffix = parseOptional<TokenTyp.BEZEICHNER_KLEIN>()
        val programm = parseBereich {subParse(Programm())}
        return AST.Definition.Funktion(rückgabeTyp?.let { AST.Nomen(it) }, name, objekt, präpositionsParameter, suffix, programm.sätze)
      }

      fun parseRückgabeTyp(): TypedToken<TokenTyp.BEZEICHNER_GROSS> {
        expect<TokenTyp.OFFENE_KLAMMER>("'('")
        val typ = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner")
        expect<TokenTyp.GESCHLOSSENE_KLAMMER>("')")
        return typ
      }

      fun parseParameter(): AST.Definition.Parameter {
        val artikel = expect<TokenTyp.ARTIKEL.BESTIMMT>("bestimmter Artikel")
        val typ = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner")
        val name = parseOptional<TokenTyp.BEZEICHNER_GROSS>()
        return AST.Definition.Parameter(artikel, AST.Nomen(typ), name?.let { AST.Nomen(it) })
      }

      fun parsePräpositionsParameter(): List<AST.Definition.PräpositionsParameter> {
        return parseSeperatorFirstCombineList<AST.Definition.PräpositionsParameter, List<AST.Definition.Parameter>, TokenTyp.BEZEICHNER_KLEIN>(
            "Präposition",
            true,
            { parseKommaListe<AST.Definition.Parameter, TokenTyp.ARTIKEL.BESTIMMT>(::parseParameter, false) }
        ) {
          präposition, parameter -> AST.Definition.PräpositionsParameter(AST.Präposition(präposition), parameter)
        }
      }

    }
  }
}

fun main() {
  println(Parser("./iterationen/iter0/iter0.gms").parse())
}