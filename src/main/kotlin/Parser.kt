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
      val klasse = parseNomen<TokenTyp.VORNOMEN>(false, "")
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

  protected inline fun<reified VornomenT: TokenTyp.VORNOMEN> parseNomen(mitVornomen: Boolean, erwartetesVornomen: String): AST.Nomen {
    val vornomen = if (mitVornomen) expect<VornomenT>(erwartetesVornomen) else null
    if (vornomen != null && vornomen.typ == TokenTyp.VORNOMEN.JEDE) {
      throw GermanScriptFehler.SyntaxFehler.ParseFehler(vornomen.toUntyped(), null,
          "Das Pronomen '${vornomen.wert}' darf nur in Für-Jede-Schleifen vorkommen.")
    }
    if (vornomen != null && vornomen is TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN) {
      // 'mein' darf nur in Methodendefinition vorkommen und 'dein' nur in Methodenblock
      when (vornomen.typ) {
        is TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN -> {
          if (!hierarchyContainsNode(ASTKnotenID.METHODEN_DEFINITION)) {
            throw GermanScriptFehler.SyntaxFehler.ParseFehler(vornomen.toUntyped(), null,
              "Das Possessivpronomen '${vornomen.wert}' darf nur in Methodendefinitionen verwendet werden.")
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
      is TokenTyp.VORNOMEN.JEDE -> throw GermanScriptFehler.SyntaxFehler.ParseFehler(nomen.vornomen.toUntyped())
      TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN -> AST.Ausdruck.SelbstEigenschaftsZugriff(nomen)
      TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.DEIN -> AST.Ausdruck.MethodenBlockEigenschaftsZugriff(nomen)
    }

    return when(peekType()){
      is TokenTyp.ALS -> {
        next()
        val typ = parseNomen<TokenTyp.VORNOMEN>(false, "Vornomen")
        AST.Ausdruck.Konvertierung(ausdruck, AST.TypKnoten(typ))
      }
      is TokenTyp.OPERATOR -> {
         if (inBinärenAusdruck) ausdruck else subParse(Ausdruck(ausdruck))
      }
      else -> ausdruck
    }
  }

  fun parseArgument(): AST.Argument {
    val nomen = parseNomen<TokenTyp.VORNOMEN>(true, "Vornomen")
    var wert = parseNomenAusdruck(nomen, false)
    if (wert is AST.Ausdruck.Variable) {
      wert = when(peekType()) {
        is TokenTyp.NEUE_ZEILE,
        is TokenTyp.PUNKT,
        is TokenTyp.KOMMA,
        is TokenTyp.BEZEICHNER_KLEIN,
        is TokenTyp.GESCHLOSSENE_KLAMMER -> wert

        else -> subParse(Ausdruck())
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

  protected fun parseSätze(endToken: TokenTyp = TokenTyp.PUNKT): List<AST.Satz> =  parseBereich(endToken) { subParse(Programm) }.sätze
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
            is TokenTyp.EOF, TokenTyp.PUNKT, TokenTyp.AUSRUFEZEICHEN -> break@loop
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
        is TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT, is TokenTyp.VORNOMEN.ARTIKEL.UNBESTIMMT -> subParse(Satz.VariablenDeklaration)
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

  class Ausdruck(private val linkerAusdruck: AST.Ausdruck? = null): SubParser<AST.Ausdruck>() {
    override val id: ASTKnotenID
      get() = ASTKnotenID.AUSDRUCK

    override fun parseImpl(): AST.Ausdruck {
      return parseBinärenAusdruck(0.0, linkerAusdruck)
    }

    // pratt parsing: https://matklad.github.io/2020/04/13/simple-but-powerful-pratt-parsing.html
    private fun parseBinärenAusdruck(minBindungskraft: Double, links: AST.Ausdruck? = null) : AST.Ausdruck {
      var links = links?: parseEinzelnerAusdruck()

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
        val rechts = parseBinärenAusdruck(rechteBindungsKraft)
        links = AST.Ausdruck.BinärerAusdruck(operatorToken, links, rechts, minBindungskraft == 0.0)
      }

      return links
    }

    private fun parseEinzelnerAusdruck(): AST.Ausdruck {
      val ausdruck = when (val tokenTyp = peekType()) {
        is TokenTyp.ZEICHENFOLGE -> AST.Ausdruck.Zeichenfolge(next().toTyped())
        is TokenTyp.ZAHL -> AST.Ausdruck.Zahl(next().toTyped())
        is TokenTyp.BOOLEAN -> AST.Ausdruck.Boolean(next().toTyped())
        is TokenTyp.BEZEICHNER_KLEIN -> AST.Ausdruck.FunktionsAufruf(subParse(FunktionsAufruf))
        is TokenTyp.VORNOMEN -> parseNomenAusdruck(parseNomen<TokenTyp.VORNOMEN>(true, "Vornomen"), true)
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
        is TokenTyp.BEZEICHNER_GROSS -> throw GermanScriptFehler.SyntaxFehler.ParseFehler(next(), "Artikel", "Vor einem Nomen muss ein Artikel stehen.")
        else -> throw GermanScriptFehler.SyntaxFehler.ParseFehler(next())
      }
      return when (peekType()){
        is TokenTyp.ALS -> {
          next()
          val typ = parseNomen<TokenTyp.VORNOMEN>(false, "Vornomen")
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
        val elemente = parseListeMitEnde<AST.Ausdruck, TokenTyp.KOMMA, TokenTyp.GESCHLOSSENE_ECKIGE_KLAMMER>(true) {subParse(Ausdruck())}
        expect<TokenTyp.GESCHLOSSENE_ECKIGE_KLAMMER>("']'")
        return AST.Ausdruck.Liste(nomen, elemente)
      }
    }

    class ListenElement(nomen: AST.Nomen): NomenAusdruck<AST.Ausdruck.ListenElement>(nomen) {
      override val id: ASTKnotenID
        get() = ASTKnotenID.LISTEN_ELEMENT

      override fun parseImpl(): AST.Ausdruck.ListenElement {
        expect<TokenTyp.OFFENE_ECKIGE_KLAMMER>("'['")
        val index = subParse(Ausdruck())
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
          val eigenschaft = parseNomen<TokenTyp.VORNOMEN>(false, "")
          val vornomenTyp = nomen.vornomen.typ
          val objekt = if (vornomenTyp is TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN) {
            AST.Ausdruck.SelbstEigenschaftsZugriff(eigenschaft)
          } else {
            AST.Ausdruck.MethodenBlockEigenschaftsZugriff(eigenschaft)
          }
          AST.Ausdruck.EigenschaftsZugriff(nomen, objekt)
        } else {
          val objekt = parseNomenAusdruck(parseNomen<TokenTyp.VORNOMEN.ARTIKEL>(true, "Artikel"), inBinärenAusdruck)
          AST.Ausdruck.EigenschaftsZugriff(nomen, objekt)
        }
      }
    }
  }

  object FunktionsAufruf: SubParser<AST.FunktionsAufruf>() {
    override val id: ASTKnotenID
      get() = ASTKnotenID.FUNKTIONS_AUFRUF

    override fun parseImpl(): AST.FunktionsAufruf {
      val verb = expect<TokenTyp.BEZEICHNER_KLEIN>("bezeichner")
      val objekt = parseOptional<AST.Argument, TokenTyp.VORNOMEN>(::parseArgument)
      val reflexivPronomen = if (objekt == null) parseOptional<TokenTyp.REFLEXIV_PRONOMEN>() else null
      val präpositionen = parsePräpositionsArgumente()
      val suffix = parseOptional<TokenTyp.BEZEICHNER_KLEIN>()
      return AST.FunktionsAufruf(verb, objekt, reflexivPronomen, präpositionen, suffix)
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
        val name = parseNomen<TokenTyp.VORNOMEN>(true, "Vornomen")
        val zuweisung = expect<TokenTyp.ZUWEISUNG>("Zuweisung")
        val ausdruck = subParse(Ausdruck())
        return AST.Satz.VariablenDeklaration(name, zuweisung, ausdruck)
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
        val name = parseNomen<TokenTyp.VORNOMEN>(false, "")
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
        val ausdruck = subParse(Ausdruck())
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
        var bedingung = subParse(Ausdruck())
        var sätze = parseSätze()

        bedingungen += AST.Satz.BedingungsTerm(bedingung, sätze)

        überspringeLeereZeilen()
        while (peekType() is TokenTyp.SONST) {
          expect<TokenTyp.SONST>("'sonst'")
          if (peekType() !is TokenTyp.WENN) {
            sätze = parseBereich {subParse(Programm)}.sätze
            return AST.Satz.Bedingung(bedingungen, sätze)
          }
          expect<TokenTyp.WENN>("'wenn'")
          bedingung = subParse(Ausdruck())
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
        val bedingung = subParse(Ausdruck())
        val sätze = parseSätze()
        return AST.Satz.SolangeSchleife(AST.Satz.BedingungsTerm(bedingung, sätze))
      }
    }

    object FürJedeSchleife: SubParser<AST.Satz.FürJedeSchleife>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.FÜR_JEDE_SCHLEIFE

      override fun parseImpl(): AST.Satz.FürJedeSchleife {
        parseKleinesSchlüsselwort("für")
        val nomen = AST.Nomen(
            expect<TokenTyp.VORNOMEN.JEDE>("'jeder' oder 'jede' oder 'jedes'"),
            expect("Bezeichner")
        )
        val peekToken = peek()
        var liste: AST.Ausdruck.Liste? = null
        var binder = nomen
        when (peekToken.typ) {
          is TokenTyp.DOPPELPUNKT -> null
          is TokenTyp.BEZEICHNER_GROSS -> binder = AST.Nomen(null, next().toTyped())
          is TokenTyp.BEZEICHNER_KLEIN -> {
            parseKleinesSchlüsselwort("in")
            liste = subParse(
                NomenAusdruck.Liste(parseNomen<TokenTyp.VORNOMEN.ARTIKEL.UNBESTIMMT>(
                    true, "unbestimmter Artikel")
                )
            )
          }
          else -> throw GermanScriptFehler.SyntaxFehler.ParseFehler(next(), "':' oder 'in'")
        }
        val sätze = parseSätze()
        return AST.Satz.FürJedeSchleife(binder, if(liste == null) nomen else null, liste, sätze)
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
      val typ = parseNomen<VornomenT>(true, erwartetesVornomen)
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

      // parst einfach einen Bezeichner passt, aber auf dass der Bezeichner mehr als einen Buchstaben hat
      private fun parseWort(): String {
        val wort = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner")
        if (wort.wert.length <= 1) {
          throw GermanScriptFehler.SyntaxFehler.ParseFehler(wort.toUntyped(), "Wort",
              "Ein Nomen in einer Deklinationsanweisungen muss mehr als einen Buchstaben haben.\n" +
                  "Bezeichner die nur einen Buchstaben haben, sind Symbole und können ohne Deklination " +
                  "im Singular überall als Bezeichner verwendet werden.")
        }
        return wort.wert
      }

      private fun parseDeklination(): AST.Definition.DeklinationsDefinition.Definition {
        val genus = expect<TokenTyp.GENUS>("Genus").typ.genus
        expect<TokenTyp.SINGULAR>("'Singular'")
        expect<TokenTyp.OFFENE_KLAMMER>("'('")
        val nominativS = parseWort()
        expect<TokenTyp.KOMMA>("','")
        val genitivS = parseWort()
        expect<TokenTyp.KOMMA>("','")
        val dativS = parseWort()
        expect<TokenTyp.KOMMA>("','")
        val akkusativS = parseWort()
        expect<TokenTyp.GESCHLOSSENE_KLAMMER>("')'")
        expect<TokenTyp.PLURAL>("'Plural'")
        expect<TokenTyp.OFFENE_KLAMMER>("'('")
        val nominativP = parseWort()
        expect<TokenTyp.KOMMA>("','")
        val genitivP = parseWort()
        expect<TokenTyp.KOMMA>("','")
        val dativP = parseWort()
        expect<TokenTyp.KOMMA>("','")
        val akkusativP = parseWort()
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
        val programm = parseBereich {subParse(Programm)}
        return AST.Definition.FunktionOderMethode.Funktion(rückgabeTyp?.let { AST.TypKnoten(AST.Nomen(null, it)) }, name, objekt, präpositionsParameter, suffix, programm.sätze)
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
        val name = AST.Nomen(null, expect("Bezeichner"))
        val elternKlasse = when (peekType()) {
          is TokenTyp.ALS -> next().let { AST.TypKnoten(AST.Nomen(null, expect("Bezeichner"))) }
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

        val konstruktor = parseSätze()
        return AST.Definition.Klasse(name, elternKlasse, eingenschaften, konstruktor)
      }

    }
  }
}

fun main() {
  val ast = Parser("./iterationen/iter_2/code.gms").parse()
  println(ast)
}