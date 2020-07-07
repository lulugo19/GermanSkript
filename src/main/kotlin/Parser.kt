import util.Peekable
import java.util.*

enum class ASTKnotenID {
  PROGRAMM,
  AUSDRUCK,
  VARIABLEN_DEKLARATION,
  FUNKTIONS_AUFRUF,
  FUNKTIONS_DEFINITION,
  INTERN,
  DEKLINATION,
  ZURÜCKGABE,
  BEDINGUNG,
  SCHLEIFE,
  SCHLEIFENKONTROLLE,
  LISTE,
  VARIABLE,
  LISTEN_ELEMENT
}

class Parser(dateiPfad: String): PipelineKomponente(dateiPfad) {
  fun parse(): AST.Programm {
    val tokens = Peekable(Lexer(dateiPfad).tokeniziere().iterator())
    val ast = SubParser.Programm.parse(tokens, Stack())
    if (tokens.peek(0)!!.typ !is TokenTyp.EOF) {
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
  protected fun peek(ahead: Int = 0): Token = tokens!!.peek(ahead)!!
  protected fun peekType(ahead: Int = 0): TokenTyp = tokens!!.peek(ahead)!!.typ
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

  protected fun parseKleinesSchlüsselwort(schlüsselwort: String): TypedToken<TokenTyp.BEZEICHNER_KLEIN> {
    val token = expect<TokenTyp.BEZEICHNER_KLEIN>("'$schlüsselwort'")
    if (token.wert != schlüsselwort) {
      throw GermanScriptFehler.SyntaxFehler.ParseFehler(token.toUntyped(), "'$schlüsselwort'")
    }
    return token
  }

  protected inline fun <ElementT, reified StartTokenT> parseKommaListeMitStart(canBeEmpty: Boolean, elementParser: () -> ElementT): List<ElementT> {
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

  protected inline fun <ElementT, reified EndTokenT> parseKommaListeMitEnde(canBeEmpty: Boolean, elementParser: () -> ElementT): List<ElementT> {
    if (canBeEmpty && peekType() is EndTokenT) {
      return emptyList()
    }
    val liste = mutableListOf(elementParser())
    while (peekType() is TokenTyp.KOMMA) {
      next()
      liste.add(elementParser())
    }
    return liste
  }

  protected inline fun <reified T: TokenTyp> parseKommaListe(canBeEmpty: Boolean, erwartet: String): List<TypedToken<T>> {
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

  // zum Parsen der Präpositionslisten bei einer Funktionsdefinition sowie beim Funktionsaufruf
  protected inline fun <ElementT, ParserT> parsePräpositionsListe(
          parser: () -> ParserT,
          combiner: (TypedToken<TokenTyp.BEZEICHNER_KLEIN>, ParserT) -> ElementT
  ): List<ElementT> {
    if (peekType() !is TokenTyp.BEZEICHNER_KLEIN) {
      return emptyList()
    }
    val list = mutableListOf<ElementT>()
    while (peekType() is TokenTyp.BEZEICHNER_KLEIN && peekType(1) is TokenTyp.ARTIKEL) {
      list += combiner(expect("Präposition"), parser())
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

  protected fun parseListenAusdruck(): AST.Ausdruck {
    return when (peekType()) {
      is TokenTyp.ARTIKEL.BESTIMMT -> subParse(Variable)
      is TokenTyp.ARTIKEL.UMBESTIMMT -> subParse(Liste)
      else -> GermanScriptFehler.SyntaxFehler.ParseFehler(next(), "Artikel")
    } as AST.Ausdruck
  }
  // endregion

  object Programm: SubParser<AST.Programm>() {
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
      val nextToken = peek()
      return when (nextToken.typ) {
        is TokenTyp.INTERN -> subParse(Satz.Intern)
        is TokenTyp.ARTIKEL -> subParse(Satz.VariablenDeklaration)
        is TokenTyp.WENN -> subParse(Satz.Bedingung)
        is TokenTyp.SOLANGE -> subParse(Satz.SolangeSchleife)
        is TokenTyp.FORTFAHREN, is TokenTyp.ABBRECHEN -> subParse(Satz.SchleifenKontrolle)
        is TokenTyp.BEZEICHNER_KLEIN ->
          when (nextToken.wert) {
            "gebe" -> subParse(Satz.Zurückgabe)
            "für" -> subParse(Satz.FürJedeSchleife)
            else -> subParse(Satz.FunktionsAufruf)
          }
        else -> null
      }
    }

    fun parseDefinition(): AST.Definition? {
      return when(peekType()) {
        is TokenTyp.VERB -> subParse(Definition.Funktion)
        is TokenTyp.DEKLINATION -> subParse(Definition.DeklinationsDefinition)
        else -> null
      }
    }
  }

  object Ausdruck: SubParser<AST.Ausdruck>() {
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
        is TokenTyp.ZEICHENFOLGE -> AST.Ausdruck.Zeichenfolge(next().toTyped())
        is TokenTyp.ZAHL -> AST.Ausdruck.Zahl(next().toTyped())
        is TokenTyp.BOOLEAN -> AST.Ausdruck.Boolean(next().toTyped())
        is TokenTyp.BEZEICHNER_KLEIN -> AST.Ausdruck.FunktionsAufruf(subParse(FunktionsAufruf))
        is TokenTyp.ARTIKEL.UMBESTIMMT -> subParse(Liste)
        is TokenTyp.ARTIKEL.BESTIMMT -> {
          when (peekType(1)) {
            is TokenTyp.BEZEICHNER_GROSS -> subParse(Variable)
            is TokenTyp.ZAHL -> subParse(ListenElement)
            else -> throw GermanScriptFehler.SyntaxFehler.ParseFehler(peek(1), "Bezeichner oder Index")
          }
        }
        is TokenTyp.OFFENE_KLAMMER -> {
          next()
          parseImpl().also { expect<TokenTyp.GESCHLOSSENE_KLAMMER>("')'") }
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
        else -> throw GermanScriptFehler.SyntaxFehler.ParseFehler(next())
      }
    }
  }

  object Variable: SubParser<AST.Ausdruck.Variable>() {
    override val id: ASTKnotenID = ASTKnotenID.VARIABLE

    override fun parseImpl(): AST.Ausdruck.Variable {
      val artikel = expect<TokenTyp.ARTIKEL.BESTIMMT>("bestimmter Artikel")
      val name = AST.Nomen(expect("Bezeichner"))
      return AST.Ausdruck.Variable(artikel, name)
    }
  }

  object Liste: SubParser<AST.Ausdruck.Liste>() {
    override val id: ASTKnotenID
      get() = ASTKnotenID.LISTE

    override fun parseImpl(): AST.Ausdruck.Liste {
      val artikel = expect<TokenTyp.ARTIKEL.UMBESTIMMT>("unbestimmter Artikel")
      val pluralTyp = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner")
      expect<TokenTyp.OFFENE_ECKIGE_KLAMMER>("'['")
      val elemente = parseKommaListeMitEnde<AST.Ausdruck, TokenTyp.GESCHLOSSENE_ECKIGE_KLAMMER>(true) {subParse(Ausdruck)}
      expect<TokenTyp.GESCHLOSSENE_ECKIGE_KLAMMER>("']'")
      return AST.Ausdruck.Liste(artikel, AST.Nomen(pluralTyp), elemente)
    }
  }

  object ListenElement: SubParser<AST.Ausdruck.ListenElement>() {
    override val id: ASTKnotenID
      get() = ASTKnotenID.LISTEN_ELEMENT

    override fun parseImpl(): AST.Ausdruck.ListenElement {
      val artikel = expect<TokenTyp.ARTIKEL.BESTIMMT>("bestimmter Artikel")
      val index = expect<TokenTyp.ZAHL>("Index")
      if (index.wert.contains(",")) {
        throw GermanScriptFehler.SyntaxFehler.ParseFehler(next(), "ganze Zahl", "Ein Listenindex muss immer eine ganze Zahl sein.")
      }
      val singular = AST.Nomen(expect("Bezeichner"))
      val peekedToken = peek()
      val kasus = if (peekedToken.typ is TokenTyp.BEZEICHNER_KLEIN) {
        parseKleinesSchlüsselwort("von")
        Kasus.DATIV
      } else {
        Kasus.GENITIV
      }

      val listenAusdruck = parseListenAusdruck()
      return AST.Ausdruck.ListenElement(artikel, index, singular, kasus, listenAusdruck)
    }
  }

  object FunktionsAufruf: SubParser<AST.FunktionsAufruf>() {
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
      val artikel = expect<TokenTyp.ARTIKEL>("Artikel")
      return when (artikel.typ) {
        is TokenTyp.ARTIKEL.BESTIMMT -> parseBestimmtesArgument(artikel)
        is TokenTyp.ARTIKEL.UMBESTIMMT -> parseUnbestimmtesArgument(artikel)
      }
    }

    fun parseBestimmtesArgument(artikel: TypedToken<TokenTyp.ARTIKEL>): AST.Argument {
      val name = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner")
      val wert = when (peekType()) {
        is TokenTyp.NEUE_ZEILE, is TokenTyp.PUNKT, is TokenTyp.KOMMA, is TokenTyp.BEZEICHNER_KLEIN -> null
        is TokenTyp.BEZEICHNER_GROSS -> AST.Ausdruck.Variable(null, AST.Nomen(next().toTyped()))
        else -> subParse(Ausdruck)
      }
      return AST.Argument(artikel, AST.Nomen(name), wert)
    }

    fun parseUnbestimmtesArgument(artikel: TypedToken<TokenTyp.ARTIKEL>): AST.Argument {
      // Liste oder Objekt
      if (peekType() !is TokenTyp.BEZEICHNER_GROSS) {
        throw GermanScriptFehler.SyntaxFehler.ParseFehler(next(), "Bezeichner")
      }
      val name = peek().toTyped<TokenTyp.BEZEICHNER_GROSS>()
      val liste = subParse(Liste)
      return AST.Argument(artikel, AST.Nomen(name), liste)
    }

    fun parsePräpositionsArgumente(): List<AST.PräpositionsArgumente> {
      return parsePräpositionsListe(
              {parseKommaListeMitStart<AST.Argument, TokenTyp.ARTIKEL.BESTIMMT>(false, ::parseArgument)}
      ) {
        präposition, argumente -> AST.PräpositionsArgumente(AST.Präposition(präposition), argumente)
      }
    }
  }

  sealed class Satz<T: AST.Satz>(): SubParser<T>() {
    object Intern: Satz<AST.Satz.Intern>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.INTERN

      override fun bewacheKnoten() {
        if (!hierarchyContainsNode(ASTKnotenID.FUNKTIONS_DEFINITION)) {
          throw GermanScriptFehler.SyntaxFehler.ParseFehler(next(), null, "Das Schlüsselwort 'intern' darf nur in einer Funktionsdefinition stehen.")
        }
      }

      override fun parseImpl(): AST.Satz.Intern {
        expect<TokenTyp.INTERN>("intern")
        if (peekType() !is TokenTyp.PUNKT) {
          throw GermanScriptFehler.SyntaxFehler.ParseFehler(next(), ".")
        }
        return AST.Satz.Intern
      }
    }

    object VariablenDeklaration: Satz<AST.Satz.VariablenDeklaration>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.VARIABLEN_DEKLARATION

      override fun parseImpl(): AST.Satz.VariablenDeklaration {
        val artikel = expect<TokenTyp.ARTIKEL>("Artikel")
        val name = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner")
        val zuweisung = expect<TokenTyp.ZUWEISUNG>("Zuweisung")
        val ausdruck = subParse(Ausdruck)
        return AST.Satz.VariablenDeklaration(artikel, AST.Nomen(name), zuweisung, ausdruck)
      }
    }

    object FunktionsAufruf: Satz<AST.Satz.FunktionsAufruf>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.FUNKTIONS_AUFRUF

      override fun parseImpl(): AST.Satz.FunktionsAufruf = AST.Satz.FunktionsAufruf(subParse(SubParser.FunktionsAufruf))
    }

    object Zurückgabe: Satz<AST.Satz.Zurückgabe>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.ZURÜCKGABE

      override fun bewacheKnoten() {
        if (!hierarchyContainsNode(ASTKnotenID.FUNKTIONS_DEFINITION)) {
          throw GermanScriptFehler.SyntaxFehler.ParseFehler(next(), null, "'gebe ... zurück' darf nur in einer Funktionsdefinition mit Rückgabetyp stehen.")
        }
      }

      override fun parseImpl(): AST.Satz.Zurückgabe {
        parseKleinesSchlüsselwort("gebe")
        val ausdruck = subParse(Ausdruck)
        parseKleinesSchlüsselwort("zurück")
        return AST.Satz.Zurückgabe(ausdruck)
      }
    }

    object Bedingung: Satz<AST.Satz.Bedingung>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.BEDINGUNG

      override fun parseImpl(): AST.Satz.Bedingung {
        val bedingungen = mutableListOf<AST.Satz.BedingungsTerm>()

        expect<TokenTyp.WENN>("'wenn'")
        var bedingung = subParse(Ausdruck)
        var sätze = parseBereich { subParse(Programm) }.sätze

        bedingungen += AST.Satz.BedingungsTerm(bedingung, sätze)

        überspringeLeereZeilen()
        while (peekType() is TokenTyp.SONST) {
          expect<TokenTyp.SONST>("'sonst'")
          if (peekType() !is TokenTyp.WENN) {
            sätze = parseBereich {subParse(Programm)}.sätze
            return AST.Satz.Bedingung(bedingungen, sätze)
          }
          expect<TokenTyp.WENN>("'wenn'")
          bedingung = subParse(Ausdruck)
          sätze = parseBereich { subParse(Programm) }.sätze
          val bedingungsTerm = AST.Satz.BedingungsTerm(bedingung, sätze)
          bedingungen += bedingungsTerm
          überspringeLeereZeilen()
        }
        return AST.Satz.Bedingung(bedingungen, null)
      }
    }

    object SolangeSchleife: SubParser<AST.Satz.SolangeSchleife>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.SCHLEIFE

      override fun parseImpl(): AST.Satz.SolangeSchleife {
        expect<TokenTyp.SOLANGE>("'solange'")
        val bedingung = subParse(Ausdruck)
        val sätze = parseBereich { subParse(Programm) }.sätze
        return AST.Satz.SolangeSchleife(AST.Satz.BedingungsTerm(bedingung, sätze))
      }
    }

    object FürJedeSchleife: SubParser<AST.Satz.FürJedeSchleife>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.SCHLEIFE

      override fun parseImpl(): AST.Satz.FürJedeSchleife {
        parseKleinesSchlüsselwort("für")
        val jede = expect<TokenTyp.JEDE>("'jeder' oder 'jede' oder 'jedes'")
        val binder = AST.Nomen(expect("Bezeichner"))
        parseKleinesSchlüsselwort("in")
        val listenAusdruck = parseListenAusdruck()
        val sätze = parseBereich { subParse(Programm) }.sätze
        return AST.Satz.FürJedeSchleife(jede, binder, listenAusdruck, sätze)
      }
    }

    object SchleifenKontrolle: SubParser<AST.Satz.SchleifenKontrolle>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.SCHLEIFENKONTROLLE

      override fun bewacheKnoten() {
        if (!hierarchyContainsNode(ASTKnotenID.SCHLEIFE)) {
          val schlüsselwort = next()
          throw GermanScriptFehler.SyntaxFehler.ParseFehler(schlüsselwort, null, "Das Schlüsselwort '${schlüsselwort.wert}' darf nur in einer Schleife verwendet werden.")
        }
      }

      override fun parseImpl(): AST.Satz.SchleifenKontrolle {
        return when (peekType()) {
          TokenTyp.FORTFAHREN -> AST.Satz.SchleifenKontrolle.Fortfahren
          TokenTyp.ABBRECHEN -> AST.Satz.SchleifenKontrolle.Abbrechen
          else -> throw Error("Entweder 'fortfahren' oder 'abbrechen'")
        }.also { next() }
      }
    }

  }

  sealed class Definition<T: AST.Definition>(): SubParser<T>() {

    override fun bewacheKnoten() {
      // eine Definition kann nur im globalen Programm geschrieben werden
      if (depth != 1) {
        throw GermanScriptFehler.SyntaxFehler.UngültigerBereich(next(), "Definitionen können nur im globalem Bereich geschrieben werden.")
      }
    }

    object DeklinationsDefinition: Definition<AST.Definition.DeklinationsDefinition>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.DEKLINATION

      override fun parseImpl(): AST.Definition.DeklinationsDefinition {
        expect<TokenTyp.DEKLINATION>("Deklination")
        return when (peekType()) {
          is TokenTyp.DUDEN -> parseDuden()
          else -> parseDeklination()
        }
      }

      private fun parseDeklination(): AST.Definition.DeklinationsDefinition.Definition {
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
        return AST.Definition.DeklinationsDefinition.Definition(Deklination(genus,
                arrayOf(nominativS, genitivS, dativS, akkusativS, nominativP, genitivP, dativP, akkusativP)))
      }

      private fun parseDuden(): AST.Definition.DeklinationsDefinition.Duden {
        expect<TokenTyp.DUDEN>("'Duden'")
        expect<TokenTyp.OFFENE_KLAMMER>("'('")
        val wort = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner")
        expect<TokenTyp.GESCHLOSSENE_KLAMMER>("')'")
        return  AST.Definition.DeklinationsDefinition.Duden(wort)
      }
    }

    object Funktion: Definition<AST.Definition.Funktion>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.FUNKTIONS_DEFINITION

      override fun parseImpl(): AST.Definition.Funktion {
        expect<TokenTyp.VERB>("Verb")
        val rückgabeTyp = parseOptional<TypedToken<TokenTyp.BEZEICHNER_GROSS>, TokenTyp.OFFENE_KLAMMER>(::parseRückgabeTyp)
        val name = expect<TokenTyp.BEZEICHNER_KLEIN>("bezeichner")
        val objekt = parseOptional<AST.Definition.Parameter, TokenTyp.ARTIKEL.BESTIMMT>(::parseParameter)
        val präpositionsParameter = parsePräpositionsParameter()
        val suffix = parseOptional<TokenTyp.BEZEICHNER_KLEIN>()
        val programm = parseBereich {subParse(Programm)}
        return AST.Definition.Funktion(rückgabeTyp?.let { AST.TypKnoten(AST.Nomen(it)) }, name, objekt, präpositionsParameter, suffix, programm.sätze)
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
        return AST.Definition.Parameter(artikel, AST.TypKnoten(AST.Nomen(typ)), name?.let { AST.Nomen(it) })
      }

      fun parsePräpositionsParameter(): List<AST.Definition.PräpositionsParameter> {
        return parsePräpositionsListe(
                { parseKommaListeMitStart<AST.Definition.Parameter, TokenTyp.ARTIKEL.BESTIMMT>(false, ::parseParameter) }
        ) {
          präposition, parameter -> AST.Definition.PräpositionsParameter(AST.Präposition(präposition), parameter)
        }
      }

    }
  }
}

fun main() {
  println(Parser("./iterationen/iter_1/code.gms").parse())
}