import util.Peekable
import java.util.*

enum class ASTKnotenID {
  PROGRAMM,
  AUSDRUCK,
  VARIABLEN_DEKLARATION,
  FUNKTIONS_AUFRUF,
  FUNKTIONS_DEFINITION,
  METHODEN_DEFINITION,
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
  FELD_ZUGRIFF
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
      return AST.Definition.FunktionOderMethode.Methode(subParse(Definition.Funktion(ASTKnotenID.METHODEN_DEFINITION, rückgabetyp)), AST.TypKnoten(klasse))
    }
    return subParse(Definition.Funktion(ASTKnotenID.FUNKTIONS_DEFINITION, rückgabetyp))
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
    überspringeLeereZeilen()
    val liste = mutableListOf<TypedToken<T>>()
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
            "des", "der" -> subParse(NomenAusdruck.Feldzugriff(nomen, inBinärenAusdruck))
            else -> AST.Ausdruck.Variable(nomen)
          }
        }
        else -> AST.Ausdruck.Variable(nomen)
      }
      is TokenTyp.VORNOMEN.ARTIKEL.UNBESTIMMT ->  {
        when (nächstesToken.typ) {
          is TokenTyp.OFFENE_ECKIGE_KLAMMER -> subParse(NomenAusdruck.Liste(nomen))
          else -> subParse(NomenAusdruck.ObjektInstanziierung(nomen))
        }
      }
      is TokenTyp.VORNOMEN.JEDE -> throw GermanScriptFehler.SyntaxFehler.ParseFehler(nomen.vornomen.toUntyped())
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

  protected fun<T: AST> parseBereich(parser: () -> T): T {
    expect<TokenTyp.DOPPELPUNKT>(":")
    val result = parser()
    expect<TokenTyp.PUNKT>(".")
    return result
  }

  protected fun parseSätze(): List<AST.Satz> =  parseBereich { subParse(Programm) }.sätze
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
        is TokenTyp.BEZEICHNER_GROSS -> AST.Ausdruck.Variable(parseNomen<TokenTyp.VORNOMEN>(false, "Vornomen"))
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
        val feldZuweisungen = when (peekType()) {
          is TokenTyp.BEZEICHNER_KLEIN -> {
            parseKleinesSchlüsselwort("mit")
            parseListeMitStart<AST.Argument, TokenTyp.KOMMA, TokenTyp.VORNOMEN>(false, ::parseArgument)
          }
          else -> emptyList()
        }

        return AST.Ausdruck.ObjektInstanziierung(AST.TypKnoten(nomen), feldZuweisungen)
      }
    }

    class Feldzugriff(nomen: AST.Nomen, private val inBinärenAusdruck: Boolean): NomenAusdruck<AST.Ausdruck.Feldzugriff>(nomen) {
      override val id: ASTKnotenID
        get() = ASTKnotenID.FELD_ZUGRIFF

      override fun parseImpl(): AST.Ausdruck.Feldzugriff {
        val objekt = parseNomenAusdruck(parseNomen<TokenTyp.VORNOMEN.ARTIKEL>(true, "Artikel"), inBinärenAusdruck)
        return AST.Ausdruck.Feldzugriff(nomen, objekt)
      }
    }
  }

  object FunktionsAufruf: SubParser<AST.FunktionsAufruf>() {
    override val id: ASTKnotenID
      get() = ASTKnotenID.FUNKTIONS_AUFRUF

    override fun parseImpl(): AST.FunktionsAufruf {
      val verb = expect<TokenTyp.BEZEICHNER_KLEIN>("bezeichner")
      val objekt = parseOptional<AST.Argument, TokenTyp.VORNOMEN>(::parseArgument)
      val präpositionen = parsePräpositionsArgumente()
      val suffix = parseOptional<TokenTyp.BEZEICHNER_KLEIN>()
      return AST.FunktionsAufruf(verb, objekt, präpositionen, suffix)
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

    class Funktion(override val id: ASTKnotenID, private val rückgabeTyp: TypedToken<TokenTyp.BEZEICHNER_GROSS>?): Definition<AST.Definition.FunktionOderMethode.Funktion>() {

      override fun parseImpl(): AST.Definition.FunktionOderMethode.Funktion {
        val name = expect<TokenTyp.BEZEICHNER_KLEIN>("bezeichner")
        val objekt = parseOptional<AST.Definition.TypUndName, TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT> {
          parseTypUndName<TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT>("bestimmter Artikel")
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
        val felder = when(peekType()) {
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
        return AST.Definition.Klasse(name, elternKlasse, felder, konstruktor)
      }

    }
  }
}

fun main() {
  val ast = Parser("./iterationen/iter_2/code.gms").parse()
  println(ast)
}