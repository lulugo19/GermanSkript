import util.Peekable
import java.util.*

enum class ASTKnotenID {
  PROGRAMM,
  AUSDRUCK,
  VARIABLEN_DEKLARATION,
  FUNKTIONS_AUFRUF,
  FUNKTIONS_DEFINITION,
  METHODEN_DEFINITION,
  METHODEN_BLOCK,
  INTERN,
  DEKLINATION,
  ZURÜCKGABE,
  BEDINGUNG,
  SCHLEIFE,
  FÜR_JEDE_SCHLEIFE,
  SCHLEIFENKONTROLLE,
  LISTE,
  LISTEN_ELEMENT,
  KLASSEN_DEFINITION,
  OBJEKT_INSTANZIIERUNG,
  EIGENSCHAFTS_ZUGRIFF
}

class Parser(dateiPfad: String): PipelineKomponente(dateiPfad) {
  fun parse(): AST.Aufruf.Programm {
    val tokens = Peekable(Lexer(dateiPfad).tokeniziere().iterator())
    val ast = SubParser.Programm(true).parse(tokens, Stack())
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
  protected fun hierarchyContainsAnyNode(vararg knotenIds: ASTKnotenID) = stack!!.any {knotenIds.contains(it)}


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

  protected fun parseFunktionOderMethode(): AST.Definition.FunktionOderMethode{
    expect<TokenTyp.VERB>("Verb")
    var rückgabetyp:TypedToken<TokenTyp.BEZEICHNER_GROSS>? = null
    if (peekType() is TokenTyp.OFFENE_KLAMMER){
      next()
      rückgabetyp = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner")
      expect<TokenTyp.GESCHLOSSENE_KLAMMER>(")")
    }

    val nächstesToken = peek()
    if (nächstesToken.wert == "für"){
      next()
      val klasse = AST.Nomen(null, parseWort())
      val reflexivPronomen = peek(1).let { token ->
        if (token.typ is TokenTyp.REFLEXIV_PRONOMEN.MICH) token.toTyped<TokenTyp.REFLEXIV_PRONOMEN.MICH>() else null
      }
      return AST.Definition.FunktionOderMethode.Methode(
          subParse(Definition.Funktion(ASTKnotenID.METHODEN_DEFINITION, rückgabetyp, reflexivPronomen != null)),
          AST.TypKnoten(klasse), reflexivPronomen)
    }
    return subParse(Definition.Funktion(ASTKnotenID.FUNKTIONS_DEFINITION, rückgabetyp, false))
  }

  protected fun parseKleinesSchlüsselwort(schlüsselwort: String): TypedToken<TokenTyp.BEZEICHNER_KLEIN> {
    val token = expect<TokenTyp.BEZEICHNER_KLEIN>("'$schlüsselwort'")
    if (token.wert != schlüsselwort) {
      throw GermanScriptFehler.SyntaxFehler.ParseFehler(token.toUntyped(), "'$schlüsselwort'")
    }
    return token
  }

  protected inline fun<reified VornomenT: TokenTyp.VORNOMEN> parseNomen(mitVornomen: Boolean, erwartetesVornomen: String, einzelnesSymbolErlaubt: Boolean): AST.Nomen {
    val vornomen = if (mitVornomen) expect<VornomenT>(erwartetesVornomen) else null
    if (vornomen != null && vornomen is TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN) {
      // 'mein' darf nur in Methodendefinition vorkommen und 'dein' nur in Methodenblock
      when (vornomen.typ) {
        is TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN -> {
          if (!hierarchyContainsAnyNode(ASTKnotenID.METHODEN_DEFINITION, ASTKnotenID.KLASSEN_DEFINITION)) {
            throw GermanScriptFehler.SyntaxFehler.ParseFehler(vornomen.toUntyped(), null,
              "Das Possessivpronomen '${vornomen.wert}' darf nur in Methodendefinitionen oder in Konstruktoren verwendet werden.")
          }
        }
        is TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.DEIN -> {
          if (!hierarchyContainsNode(ASTKnotenID.METHODEN_BLOCK)) {
            throw GermanScriptFehler.SyntaxFehler.ParseFehler(vornomen.toUntyped(), null,
              "Das Possessivpronomen '${vornomen.wert}' darf nur in Methodenblöcken verwendet werden.")
          }
        }
      }
    }
    val bezeichner = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner")
    if (!einzelnesSymbolErlaubt && bezeichner.typ.istSymbol) {
      throw GermanScriptFehler.SyntaxFehler.ParseFehler(bezeichner.toUntyped(), "Bezeichner",
          "Der Bezeichner darf nicht nur aus einem Symbol bestehen. Er muss auf mindestens einem Wort bestehen")
    }
    return AST.Nomen(vornomen, bezeichner)
  }

  protected inline fun <ElementT, reified TrennerT: TokenTyp, reified StartTokenT: TokenTyp> parseListeMitStart(canBeEmpty: Boolean, elementParser: () -> ElementT): List<ElementT> {
    if (canBeEmpty && peekType() !is StartTokenT) {
      return emptyList()
    }
    überspringeLeereZeilen()
    val liste = mutableListOf(elementParser())
    while (peekType() is TrennerT) {
      next()
      überspringeLeereZeilen()
      liste.add(elementParser())
    }
    return liste
  }

  protected inline fun <ElementT, reified TrennerT: TokenTyp, reified EndTokenT: TokenTyp> parseListeMitEnde(canBeEmpty: Boolean, elementParser: () -> ElementT): List<ElementT> {
    if (canBeEmpty && peekType() is EndTokenT) {
      return emptyList()
    }
    überspringeLeereZeilen()
    val liste = mutableListOf(elementParser())
    while (peekType() is TrennerT) {
      next()
      überspringeLeereZeilen()
      liste.add(elementParser())
    }
    return liste
  }

  protected inline fun <reified T: TokenTyp, reified TrennerT: TokenTyp> parseListe(canBeEmpty: Boolean, erwartet: String): List<TypedToken<T>> {
    if (canBeEmpty && peekType() !is T) {
      return emptyList()
    }
    val liste = mutableListOf<TypedToken<T>>(expect(erwartet))
    überspringeLeereZeilen()
    while (peekType() is TrennerT) {
      next()
      überspringeLeereZeilen()
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
    while (peekType() is TokenTyp.BEZEICHNER_KLEIN && peekType(1) is TokenTyp.VORNOMEN) {
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

  // ein Wort ist ein Bezeichner ohne Symbole und mit nur einem Wort
  protected fun parseWort(): TypedToken<TokenTyp.BEZEICHNER_GROSS> {
    val bezeichner = expect<TokenTyp.BEZEICHNER_GROSS>("Wort")
    if (bezeichner.typ.istSymbol || bezeichner.typ.teilWörter.size > 1) {
      throw GermanScriptFehler.SyntaxFehler.ParseFehler(bezeichner.toUntyped(), "Wort", "Ein Wort darf nicht aus " +
          "Teilwörtern und Symbolen bestehen.")
    }
    return bezeichner
  }

  protected fun parseNomenAusdruck(nomen: AST.Nomen, inBinärenAusdruck: Boolean): AST.Ausdruck {
    val nächstesToken = peek()
    val ausdruck = when (nomen.vornomen!!.typ) {
      is TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT -> when (nächstesToken.typ) {
        is TokenTyp.OFFENE_ECKIGE_KLAMMER -> subParse(NomenAusdruck.ListenElement(nomen))
        is TokenTyp.VORNOMEN.ARTIKEL -> {
          when (nächstesToken.wert) {
            "des", "der", "meiner", "meines", "deiner", "deines" -> subParse(NomenAusdruck.EigenschaftsZugriff(nomen, inBinärenAusdruck))
            else -> AST.Ausdruck.Variable(nomen)
          }
        }
        else -> when(nomen.vornomen.typ) {
          TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT -> AST.Ausdruck.Variable(nomen)
          TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN -> AST.Ausdruck.SelbstEigenschaftsZugriff(nomen)
          TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.DEIN -> AST.Ausdruck.MethodenBlockEigenschaftsZugriff(nomen)
          else -> throw GermanScriptFehler.SyntaxFehler.ParseFehler(nomen.vornomen.toUntyped(), "bestimmter Artikel oder Possessivpronomen")
        }
      }
      is TokenTyp.VORNOMEN.ARTIKEL.UNBESTIMMT ->  {
        when (nächstesToken.typ) {
          is TokenTyp.OFFENE_ECKIGE_KLAMMER -> subParse(NomenAusdruck.Liste(nomen))
          else -> subParse(NomenAusdruck.ObjektInstanziierung(nomen))
        }
      }
      TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN -> AST.Ausdruck.SelbstEigenschaftsZugriff(nomen)
      TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.DEIN -> AST.Ausdruck.MethodenBlockEigenschaftsZugriff(nomen)
    }

    return when(peekType()){
      is TokenTyp.ALS -> {
        next()
        val typ = AST.Nomen(null, parseWort())
        AST.Ausdruck.Konvertierung(ausdruck, AST.TypKnoten(typ))
      }
      is TokenTyp.OPERATOR -> {
         if (inBinärenAusdruck) ausdruck else subParse(Ausdruck(mitVornomen = true, optionalesIstNachVergleich = false, linkerAusdruck = ausdruck))
      }
      else -> ausdruck
    }
  }

  fun parseArgument(): AST.Argument {
    val nomen = parseNomen<TokenTyp.VORNOMEN>(true, "Vornomen", true)
    var wert = parseNomenAusdruck(nomen, false)
    if (wert is AST.Ausdruck.Variable) {
      wert = when(peekType()) {
        is TokenTyp.NEUE_ZEILE,
        is TokenTyp.PUNKT,
        is TokenTyp.KOMMA,
        is TokenTyp.BEZEICHNER_KLEIN,
        is TokenTyp.GESCHLOSSENE_KLAMMER -> wert

        else -> subParse(Ausdruck(mitVornomen = false, optionalesIstNachVergleich = false))
      }
    }
    return AST.Argument(nomen, wert)
  }

  protected fun<T: AST> parseBereich(erwartetesEndToken: TokenTyp = TokenTyp.PUNKT, parser: () -> T): T {
    expect<TokenTyp.DOPPELPUNKT>(":")
    val result = parser()
    überspringeLeereZeilen()
    val endToken = next()
    if (endToken.typ != erwartetesEndToken){
      throw GermanScriptFehler.SyntaxFehler.ParseFehler(endToken, erwartetesEndToken.anzeigeName)
    }
    return result
  }

  protected fun parseSätze(endToken: TokenTyp = TokenTyp.PUNKT): List<AST.Satz> =  parseBereich(endToken) { subParse(Programm(false)) }.sätze
  // endregion

  class Programm(private val istProgrammStart: Boolean): SubParser<AST.Aufruf.Programm>() {
    override val id: ASTKnotenID
      get() = ASTKnotenID.PROGRAMM

    override fun parseImpl(): AST.Aufruf.Programm {
      val programmStart = if (istProgrammStart) next() else null
      val definitionen = mutableListOf<AST.Definition>()
      val sätze = mutableListOf<AST.Satz>()

      loop@ while (true) {
        überspringeLeereZeilen()
        when {
          parseSatz()?.also { sätze += it } != null -> Unit
          parseDefinition()?.also { definitionen += it } != null -> Unit
          else -> when (peekType()) {
            is TokenTyp.EOF, TokenTyp.PUNKT, TokenTyp.AUSRUFEZEICHEN -> break@loop
            else -> throw GermanScriptFehler.SyntaxFehler.ParseFehler(next())
          }
        }
      }
      return AST.Aufruf.Programm(programmStart, definitionen, sätze)
    }

    fun parseSatz(): AST.Satz? {
      val nextToken = peek()
      return when (nextToken.typ) {
        is TokenTyp.INTERN -> subParse(Satz.Intern)
        is TokenTyp.VORNOMEN -> subParse(Satz.VariablenDeklaration)
        is TokenTyp.WENN -> subParse(Satz.Bedingung)
        is TokenTyp.SOLANGE -> subParse(Satz.SolangeSchleife)
        is TokenTyp.FORTFAHREN, is TokenTyp.ABBRECHEN -> subParse(Satz.SchleifenKontrolle)
        is TokenTyp.BEZEICHNER_KLEIN ->
          when (nextToken.wert) {
            "gebe" -> subParse(Satz.Zurückgabe)
            "für" -> subParse(Satz.FürJedeSchleife)
            else -> subParse(Satz.FunktionsAufruf)
          }
        is TokenTyp.BEZEICHNER_GROSS -> subParse(Satz.MethodenBlock)
        else -> null
      }
    }

    fun parseDefinition(): AST.Definition? {
      return when(peekType()) {
        is TokenTyp.DEKLINATION -> subParse(Definition.DeklinationsDefinition)
        is TokenTyp.NOMEN -> subParse(Definition.Klasse)
        is TokenTyp.VERB -> parseFunktionOderMethode()
        else -> null
      }
    }
  }

  class Ausdruck(private val mitVornomen: Boolean, private val optionalesIstNachVergleich: Boolean, private val linkerAusdruck: AST.Ausdruck? = null): SubParser<AST.Ausdruck>() {
    override val id: ASTKnotenID
      get() = ASTKnotenID.AUSDRUCK

    override fun parseImpl(): AST.Ausdruck {
      return parseBinärenAusdruck(0.0, mitVornomen, linkerAusdruck)
    }

    // pratt parsing: https://matklad.github.io/2020/04/13/simple-but-powerful-pratt-parsing.html
    private fun parseBinärenAusdruck(minBindungskraft: Double, mitArtikel: Boolean, links: AST.Ausdruck? = null) : AST.Ausdruck {
      var links = links?: parseEinzelnerAusdruck(mitArtikel)

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
        val rechts = parseBinärenAusdruck(rechteBindungsKraft, mitArtikel)
        if (optionalesIstNachVergleich && operator.klasse == OperatorKlasse.VERGLEICH) {
          val ist = parseOptional<TokenTyp.ZUWEISUNG>()
          if (ist != null && ist.typ.numerus != Numerus.SINGULAR) {
            throw GermanScriptFehler.SyntaxFehler.ParseFehler(ist.toUntyped())
          }
        }
        links = AST.Ausdruck.BinärerAusdruck(operatorToken, links, rechts, minBindungskraft == 0.0)
      }

      return links
    }

    private fun parseEinzelnerAusdruck(mitArtikel: Boolean): AST.Ausdruck {
      val ausdruck = when (val tokenTyp = peekType()) {
        is TokenTyp.ZEICHENFOLGE -> AST.Ausdruck.Zeichenfolge(next().toTyped())
        is TokenTyp.ZAHL -> AST.Ausdruck.Zahl(next().toTyped())
        is TokenTyp.BOOLEAN -> AST.Ausdruck.Boolean(next().toTyped())
        is TokenTyp.BEZEICHNER_KLEIN -> AST.Ausdruck.FunktionsAufruf(subParse(FunktionsAufruf))
        is TokenTyp.VORNOMEN -> parseNomenAusdruck(parseNomen<TokenTyp.VORNOMEN>(true, "Vornomen", true), true)
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
            AST.Ausdruck.Minus(parseEinzelnerAusdruck(false))
          }
        }
        is TokenTyp.BEZEICHNER_GROSS -> if (mitArtikel)
            throw GermanScriptFehler.SyntaxFehler.ParseFehler(next(), "Artikel", "Vor einem Nomen muss ein Artikel stehen.")
          else
            AST.Ausdruck.Variable(parseNomen<TokenTyp.VORNOMEN>(false, "", true))
        else -> throw GermanScriptFehler.SyntaxFehler.ParseFehler(next())
      }
      return when (peekType()){
        is TokenTyp.ALS -> {
          next()
          val typ = AST.Nomen(null, parseWort())
          AST.Ausdruck.Konvertierung(ausdruck,AST.TypKnoten(typ))
        }
        else -> ausdruck
      }
    }
  }

  sealed class NomenAusdruck<T: AST.Ausdruck>(protected val nomen: AST.Nomen): SubParser<T>() {
    class Liste(nomen: AST.Nomen): NomenAusdruck<AST.Ausdruck.Liste>(nomen) {
      override val id: ASTKnotenID
        get() = ASTKnotenID.LISTE

      override fun parseImpl(): AST.Ausdruck.Liste {
        expect<TokenTyp.OFFENE_ECKIGE_KLAMMER>("'['")
        val elemente = parseListeMitEnde<AST.Ausdruck, TokenTyp.KOMMA, TokenTyp.GESCHLOSSENE_ECKIGE_KLAMMER>(true)
          {subParse(Ausdruck(mitVornomen = true, optionalesIstNachVergleich = false))}
        expect<TokenTyp.GESCHLOSSENE_ECKIGE_KLAMMER>("']'")
        return AST.Ausdruck.Liste(nomen, elemente)
      }
    }

    class ListenElement(nomen: AST.Nomen): NomenAusdruck<AST.Ausdruck.ListenElement>(nomen) {
      override val id: ASTKnotenID
        get() = ASTKnotenID.LISTEN_ELEMENT

      override fun parseImpl(): AST.Ausdruck.ListenElement {
        expect<TokenTyp.OFFENE_ECKIGE_KLAMMER>("'['")
        val index = subParse(Ausdruck(mitVornomen = false, optionalesIstNachVergleich = false))
        expect<TokenTyp.GESCHLOSSENE_ECKIGE_KLAMMER>("']'")
        return AST.Ausdruck.ListenElement(nomen, index)
      }
    }

    class ObjektInstanziierung(nomen: AST.Nomen): NomenAusdruck<AST.Ausdruck.ObjektInstanziierung>(nomen) {
      override val id: ASTKnotenID
        get() = ASTKnotenID.OBJEKT_INSTANZIIERUNG

      override fun parseImpl(): AST.Ausdruck.ObjektInstanziierung {
        val eigenschaftsZuweisungen = when (peekType()) {
          is TokenTyp.BEZEICHNER_KLEIN -> {
            parseKleinesSchlüsselwort("mit")
            parseListeMitStart<AST.Argument, TokenTyp.KOMMA, TokenTyp.VORNOMEN>(false, ::parseArgument)
          }
          else -> emptyList()
        }

        return AST.Ausdruck.ObjektInstanziierung(AST.TypKnoten(nomen), eigenschaftsZuweisungen)
      }
    }

    class EigenschaftsZugriff(nomen: AST.Nomen, private val inBinärenAusdruck: Boolean): NomenAusdruck<AST.Ausdruck.EigenschaftsZugriff>(nomen) {
      override val id: ASTKnotenID
        get() = ASTKnotenID.EIGENSCHAFTS_ZUGRIFF

      override fun parseImpl(): AST.Ausdruck.EigenschaftsZugriff {
        return if (nomen.vornomen!!.typ is TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN) {
          val eigenschaft = parseNomen<TokenTyp.VORNOMEN>(false, "", true)
          val vornomenTyp = nomen.vornomen.typ
          val objekt = if (vornomenTyp is TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN) {
            AST.Ausdruck.SelbstEigenschaftsZugriff(eigenschaft)
          } else {
            AST.Ausdruck.MethodenBlockEigenschaftsZugriff(eigenschaft)
          }
          AST.Ausdruck.EigenschaftsZugriff(nomen, objekt)
        } else {
          val objekt = parseNomenAusdruck(parseNomen<TokenTyp.VORNOMEN.ARTIKEL>(true, "Artikel", true), inBinärenAusdruck)
          AST.Ausdruck.EigenschaftsZugriff(nomen, objekt)
        }
      }
    }
  }

  object FunktionsAufruf: SubParser<AST.Aufruf.Funktion>() {
    override val id: ASTKnotenID
      get() = ASTKnotenID.FUNKTIONS_AUFRUF

    override fun parseImpl(): AST.Aufruf.Funktion {
      val verb = expect<TokenTyp.BEZEICHNER_KLEIN>("bezeichner")
      val objekt = parseOptional<AST.Argument, TokenTyp.VORNOMEN>(::parseArgument)
      val reflexivPronomen = if (objekt == null) parseOptional<TokenTyp.REFLEXIV_PRONOMEN>() else null
      val präpositionen = parsePräpositionsArgumente()
      val suffix = parseOptional<TokenTyp.BEZEICHNER_KLEIN>()
      return AST.Aufruf.Funktion(verb, objekt, reflexivPronomen, präpositionen, suffix)
    }

    fun parsePräpositionsArgumente(): List<AST.PräpositionsArgumente> {
      return parsePräpositionsListe(
              {parseListeMitStart<AST.Argument, TokenTyp.KOMMA, TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT>(false, ::parseArgument)}
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
        val artikel = expect<TokenTyp.VORNOMEN>("Artikel")
        // man kann nur eigene Eigenschaften neu zuweisen
        if (artikel.typ == TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.DEIN) {
          throw GermanScriptFehler.SyntaxFehler.ParseFehler(artikel.toUntyped(), null, "Neuzuweisungen mit 'dein' funktionieren nicht.\n" +
              "Man kann die Eigenschaften eines anderen Objekts nur lesen.")
        }
        val neu = if (artikel.typ == TokenTyp.VORNOMEN.ARTIKEL.UNBESTIMMT) {
          parseOptional<TokenTyp.NEU>()
        } else {
          null
        }
        val name = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner")
        val nomen = AST.Nomen(artikel, name)
        val zuweisung = expect<TokenTyp.ZUWEISUNG>("Zuweisung")
        val ausdruck = subParse(Ausdruck(mitVornomen = true, optionalesIstNachVergleich = false))
        return AST.Satz.VariablenDeklaration(nomen, neu, zuweisung, ausdruck)
      }
    }

    object FunktionsAufruf: Satz<AST.Satz.FunktionsAufruf>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.FUNKTIONS_AUFRUF

      override fun parseImpl(): AST.Satz.FunktionsAufruf = AST.Satz.FunktionsAufruf(subParse(SubParser.FunktionsAufruf))
    }

    object MethodenBlock: Satz<AST.Satz.MethodenBlock>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.METHODEN_BLOCK

      override fun parseImpl(): AST.Satz.MethodenBlock {
        val name = parseNomen<TokenTyp.VORNOMEN>(false, "", true)
        val sätze = parseSätze(TokenTyp.AUSRUFEZEICHEN)
        return AST.Satz.MethodenBlock(name, sätze)
      }
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
        val ausdruck = subParse(Ausdruck(mitVornomen = true, optionalesIstNachVergleich = false))
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
        var bedingung = subParse(Ausdruck(mitVornomen = true, optionalesIstNachVergleich = true))
        var sätze = parseSätze()

        bedingungen += AST.Satz.BedingungsTerm(bedingung, sätze)

        überspringeLeereZeilen()
        while (peekType() is TokenTyp.SONST) {
          expect<TokenTyp.SONST>("'sonst'")
          if (peekType() !is TokenTyp.WENN) {
            sätze = parseSätze()
            return AST.Satz.Bedingung(bedingungen, sätze)
          }
          expect<TokenTyp.WENN>("'wenn'")
          bedingung = subParse(Ausdruck(mitVornomen = true, optionalesIstNachVergleich = true))
          sätze = parseSätze()
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
        val bedingung = subParse(Ausdruck(mitVornomen = true, optionalesIstNachVergleich = true))
        val sätze = parseSätze()
        return AST.Satz.SolangeSchleife(AST.Satz.BedingungsTerm(bedingung, sätze))
      }
    }

    object FürJedeSchleife: SubParser<AST.Satz.FürJedeSchleife>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.FÜR_JEDE_SCHLEIFE

      override fun parseImpl(): AST.Satz.FürJedeSchleife {
        parseKleinesSchlüsselwort("für")
        val jede = expect<TokenTyp.JEDE>("'jeden', 'jede' oder 'jedes'")
        val singular = parseNomen<TokenTyp.VORNOMEN>(false, "", true)
        val binder = when (peekType()) {
          is TokenTyp.BEZEICHNER_GROSS -> AST.Nomen(null, next().toTyped())
          else -> singular
        }
        val liste = when(peekType()) {
          is TokenTyp.BEZEICHNER_KLEIN -> {
            parseKleinesSchlüsselwort("in")
            subParse(Ausdruck(mitVornomen = true, optionalesIstNachVergleich = false))
          }
          else -> {
            if (singular.istSymbol) {
              throw GermanScriptFehler.SyntaxFehler.ParseFehler(singular.bezeichner.toUntyped(), "Bezeichner",
                  "In der Für-Jede-Schleife ohne 'in' ist ein Singular, dass nur aus einem Symbol besteht nicht erlaubt.")
            }
            null
          }
        }
        val sätze = parseSätze()
        return AST.Satz.FürJedeSchleife(jede, singular, binder, liste, sätze)
      }
    }

    object SchleifenKontrolle: SubParser<AST.Satz.SchleifenKontrolle>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.SCHLEIFENKONTROLLE

      override fun bewacheKnoten() {
        if (!hierarchyContainsAnyNode(ASTKnotenID.SCHLEIFE, ASTKnotenID.FÜR_JEDE_SCHLEIFE)) {
          val schlüsselwort = next()
          throw GermanScriptFehler.SyntaxFehler.ParseFehler(schlüsselwort, null, "Das Schlüsselwort '${schlüsselwort.wert}' darf nur in einer Schleife verwendet werden.")
        }
      }

      override fun parseImpl(): AST.Satz.SchleifenKontrolle {
        return when (peekType()) {
          TokenTyp.FORTFAHREN -> AST.Satz.SchleifenKontrolle.Fortfahren
          TokenTyp.ABBRECHEN -> AST.Satz.SchleifenKontrolle.Abbrechen
          else -> throw Exception("Entweder 'fortfahren' oder 'abbrechen'")
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

    protected inline fun<reified VornomenT: TokenTyp.VORNOMEN> parseTypUndName(erwartetesVornomen: String): AST.Definition.TypUndName {
      val typ = parseNomen<VornomenT>(true, erwartetesVornomen, false)
      val bezeichner = parseOptional<TokenTyp.BEZEICHNER_GROSS>()
      val name = if (bezeichner != null) AST.Nomen(null, bezeichner) else typ
      return AST.Definition.TypUndName(AST.TypKnoten(typ), name)
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

      private fun parseFälle(): Array<String> {
        expect<TokenTyp.OFFENE_KLAMMER>("'('")
        val genitiv: String; val dativ: String; val akkusativ: String
        val nominativ = parseWort().wert
        if (peekType() is TokenTyp.GESCHLOSSENE_KLAMMER) {
          genitiv = nominativ
          dativ = nominativ
          akkusativ = nominativ
        } else {
          expect<TokenTyp.KOMMA>("','")
          genitiv = parseWort().wert
          expect<TokenTyp.KOMMA>("','")
          dativ = parseWort().wert
          expect<TokenTyp.KOMMA>("','")
          akkusativ = parseWort().wert
        }

        expect<TokenTyp.GESCHLOSSENE_KLAMMER>("')'")
        return arrayOf(nominativ, genitiv, dativ, akkusativ)
      }

      private fun parseDeklination(): AST.Definition.DeklinationsDefinition.Definition {
        val genus = expect<TokenTyp.GENUS>("Genus").typ.genus
        expect<TokenTyp.SINGULAR>("'Singular'")
        val singular = parseFälle()
        val plural = if (peekType() is TokenTyp.PLURAL) {
          next()
          parseFälle()
        } else {
          singular
        }

        return AST.Definition.DeklinationsDefinition.Definition(Deklination(genus, singular + plural))
      }

      private fun parseDuden(): AST.Definition.DeklinationsDefinition.Duden {
        expect<TokenTyp.DUDEN>("'Duden'")
        expect<TokenTyp.OFFENE_KLAMMER>("'('")
        val wort = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner")
        expect<TokenTyp.GESCHLOSSENE_KLAMMER>("')'")
        return  AST.Definition.DeklinationsDefinition.Duden(wort)
      }
    }

    class Funktion(override val id: ASTKnotenID, private val rückgabeTyp: TypedToken<TokenTyp.BEZEICHNER_GROSS>?, private val hatReflexivPronomen: Boolean): Definition<AST.Definition.FunktionOderMethode.Funktion>() {

      override fun parseImpl(): AST.Definition.FunktionOderMethode.Funktion {
        val name = expect<TokenTyp.BEZEICHNER_KLEIN>("bezeichner")
        val objekt = if (hatReflexivPronomen) {
          next()
          null
        } else {
        parseOptional<AST.Definition.TypUndName, TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT> {
          parseTypUndName<TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT>("bestimmter Artikel") }
        }
        val präpositionsParameter = parsePräpositionsParameter()
        val suffix = parseOptional<TokenTyp.BEZEICHNER_KLEIN>()
        val sätze = parseSätze()
        return AST.Definition.FunktionOderMethode.Funktion(
            rückgabeTyp?.let { AST.TypKnoten(AST.Nomen(null, it)) },
            name, objekt, präpositionsParameter, suffix, sätze)
      }

      fun parseRückgabeTyp(): TypedToken<TokenTyp.BEZEICHNER_GROSS> {
        expect<TokenTyp.OFFENE_KLAMMER>("'('")
        val typ = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner")
        expect<TokenTyp.GESCHLOSSENE_KLAMMER>("')")
        return typ
      }

      fun parsePräpositionsParameter(): List<AST.Definition.PräpositionsParameter> {
        return parsePräpositionsListe({
          parseListeMitStart<AST.Definition.TypUndName, TokenTyp.KOMMA, TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT>(false) {
            parseTypUndName<TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT>("bestimmter Artikel")
          }
        }
        ) {
          präposition, parameter -> AST.Definition.PräpositionsParameter(AST.Präposition(präposition), parameter)
        }
      }
    }

    object Klasse: Definition<AST.Definition.Klasse>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.KLASSEN_DEFINITION

      override fun parseImpl(): AST.Definition.Klasse {
        expect<TokenTyp.NOMEN>("'Nomen'")
        val name = AST.Nomen(null, parseWort())

        val elternKlasse = when (peekType()) {
          is TokenTyp.ALS -> next().let { AST.TypKnoten(AST.Nomen(null, parseWort())) }
          else -> null
        }
        val eingenschaften = when(peekType()) {
          is TokenTyp.BEZEICHNER_KLEIN -> {
            parseKleinesSchlüsselwort("mit")
            überspringeLeereZeilen()
            parseListeMitStart<AST.Definition.TypUndName, TokenTyp.KOMMA, TokenTyp.VORNOMEN.ARTIKEL>(false) {
              parseTypUndName<TokenTyp.VORNOMEN.ARTIKEL>("Artikel")
            }
          }
          else -> emptyList()
        }

        val konstruktorSätze = parseSätze()
        return AST.Definition.Klasse(name, elternKlasse, eingenschaften, AST.Aufruf.Konstruktor(name, konstruktorSätze))
      }

    }
  }
}

fun main() {
  val ast = Parser("./iterationen/iter_2/code.gms").parse()
  println(ast)
}