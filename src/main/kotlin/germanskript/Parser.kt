package germanskript

import germanskript.util.Peekable
import java.io.File
import java.util.*

enum class ASTKnotenID {
  PROGRAMM,
  MODUL,
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
  EIGENSCHAFTS_ZUGRIFF,
  KONVERTIERUNGS_DEFINITION,
  IMPORT,
  VERWENDE,
  BEREICH,
  SCHNITTSTELLE
}

class Parser(startDatei: File): PipelineKomponente(startDatei) {
  fun parse(): AST.Programm {
    val lexer = Lexer(startDatei)
    val tokens = Peekable(lexer.tokeniziere().iterator())
    val ast = SubParser.Programm(ASTKnotenID.PROGRAMM, lexer).parse(tokens, Stack())
    if (tokens.peek(0)!!.typ !is TokenTyp.EOF) {
      throw GermanSkriptFehler.SyntaxFehler.ParseFehler(tokens.next()!!, "EOF")
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
      throw GermanSkriptFehler.SyntaxFehler.ParseFehler(nextToken, erwartet)
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

  protected fun parseFunktionsAnfang(): AST.TypKnoten? {
    expect<TokenTyp.VERB>("Verb")
    var rückgabetyp: AST.TypKnoten? = null
    if (peekType() is TokenTyp.OFFENE_KLAMMER){
      next()
      rückgabetyp = parseTypOhneArtikel(false)
      expect<TokenTyp.GESCHLOSSENE_KLAMMER>(")")
    }
    return rückgabetyp
  }

  protected fun parseFunktionOderMethode(): AST.Definition.FunktionOderMethode{
    val rückgabetyp = parseFunktionsAnfang()

    val nächstesToken = peek()
    if (nächstesToken.wert == "für"){
      next()
      val klasse = parseTypOhneArtikel(false)
      val definition =  subParse(Definition.Funktion(ASTKnotenID.METHODEN_DEFINITION, rückgabetyp, true))
      return AST.Definition.FunktionOderMethode.Methode(definition, klasse)
    }
    return subParse(Definition.Funktion(ASTKnotenID.FUNKTIONS_DEFINITION, rückgabetyp, false))
  }

  protected fun parseKleinesSchlüsselwort(schlüsselwort: String): TypedToken<TokenTyp.BEZEICHNER_KLEIN> {
    val token = expect<TokenTyp.BEZEICHNER_KLEIN>("'$schlüsselwort'")
    if (token.wert != schlüsselwort) {
      throw GermanSkriptFehler.SyntaxFehler.ParseFehler(token.toUntyped(), "'$schlüsselwort'")
    }
    return token
  }

  protected fun parseGroßenBezeichner(symbolErlaubt: Boolean): TypedToken<TokenTyp.BEZEICHNER_GROSS> {
    val bezeichner = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner")
    if (!symbolErlaubt && bezeichner.typ.hatSymbol) {
      throw GermanSkriptFehler.SyntaxFehler.ParseFehler(bezeichner.toUntyped(), "Bezeichner",
          "Symbole sind in diesem Bezeichner nicht erlaubt.")
    }
    return bezeichner
  }

  protected fun parseNomenOhneVornomen(symbolErlaubt: Boolean) = AST.Nomen(null, parseGroßenBezeichner(symbolErlaubt))

  protected inline fun<reified VornomenT: TokenTyp.VORNOMEN> parseNomenMitVornomen(erwartetesVornomen: String, symbolErlaubt: Boolean): AST.Nomen {
    val vornomen = expect<VornomenT>(erwartetesVornomen)
    if (vornomen is TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN) {
      // 'mein' darf nur in Methodendefinition vorkommen und 'dein' nur in Methodenblock
      when (vornomen.typ) {
        is TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN -> {
          if (!hierarchyContainsAnyNode(ASTKnotenID.METHODEN_DEFINITION, ASTKnotenID.KLASSEN_DEFINITION)) {
            throw GermanSkriptFehler.SyntaxFehler.ParseFehler(vornomen.toUntyped(), null,
                "Das Possessivpronomen '${vornomen.wert}' darf nur in Methodendefinitionen oder in Konstruktoren verwendet werden.")
          }
        }
        is TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.DEIN -> {
          if (!hierarchyContainsNode(ASTKnotenID.METHODEN_BLOCK)) {
            throw GermanSkriptFehler.SyntaxFehler.ParseFehler(vornomen.toUntyped(), null,
                "Das Possessivpronomen '${vornomen.wert}' darf nur in Methodenblöcken verwendet werden.")
          }
        }
      }
    }
    if (vornomen is TokenTyp.VORNOMEN.DEMONSTRATIV_PRONOMEN) {
      // Demonstrativpronomen dürfen nur in Konstruktoren und nur in Variablendeklarationen vorkommen
      if (!hierarchyContainsNode(ASTKnotenID.VARIABLEN_DEKLARATION) && !hierarchyContainsNode(ASTKnotenID.KLASSEN_DEFINITION)) {
        throw GermanSkriptFehler.SyntaxFehler.ParseFehler(vornomen.toUntyped(), null,
            "Die Demonstrativpronomen 'diese' und 'jene' dürfen nur in Konstruktoren (Klassendefinitionen) verwendet werden,\n" +
                "um von außen unzugreifbare Eigenschaften zu erstellen.")
      }
    }
    val bezeichner = parseGroßenBezeichner(symbolErlaubt)
    return AST.Nomen(vornomen, bezeichner)
  }

  protected fun parseModulPfad(): List<TypedToken<TokenTyp.BEZEICHNER_GROSS>> {
    val modulPfad = mutableListOf<TypedToken<TokenTyp.BEZEICHNER_GROSS>>()
    while (peekType(1) == TokenTyp.DOPPEL_DOPPELPUNKT) {
      modulPfad += expect("Bezeichner")
      expect<TokenTyp.DOPPEL_DOPPELPUNKT>("'::'")
    }
    return modulPfad
  }

  protected fun parseTypOhneArtikel(symbolErlaubt: Boolean): AST.TypKnoten {
    val modulPfad = parseModulPfad()
    val nomen = AST.Nomen(null, parseGroßenBezeichner(symbolErlaubt))
    return AST.TypKnoten(nomen, modulPfad)
  }

  protected inline fun <reified T: TokenTyp.VORNOMEN.ARTIKEL> parseTypMitArtikel(erwarteterArtikel: String, symbolErlaubt: Boolean): AST.TypKnoten {
    val artikel = expect<T>(erwarteterArtikel)
    val modulPfad = parseModulPfad()
    val nomen = AST.Nomen(artikel, parseGroßenBezeichner(symbolErlaubt))
    return AST.TypKnoten(nomen, modulPfad)
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

  protected inline fun<reified VornomenT: TokenTyp.VORNOMEN> parseNomenAusdruck(
      erwartetesVornomen: String,
      inBinärenAusdruck: Boolean
  ): Pair<AST.Nomen, AST.Ausdruck> {
    val vornomen = expect<VornomenT>(erwartetesVornomen)
    var modulPfad: List<TypedToken<TokenTyp.BEZEICHNER_GROSS>>? = null
    if (vornomen.typ == TokenTyp.VORNOMEN.ARTIKEL.UNBESTIMMT) {
      modulPfad = parseModulPfad()
    }
    val bezeichner = parseGroßenBezeichner(modulPfad == null)
    val nomen = AST.Nomen(vornomen, bezeichner)
    val nächstesToken = peek()
    val ausdruck = when (vornomen.typ) {
      is TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT -> when (nächstesToken.typ) {
        is TokenTyp.OFFENE_ECKIGE_KLAMMER -> subParse(NomenAusdruck.ListenElement(nomen))
        is TokenTyp.VORNOMEN.ARTIKEL -> {
          when (nächstesToken.wert) {
            "des", "der", "meiner", "meines", "deiner", "deines" -> subParse(NomenAusdruck.EigenschaftsZugriff(nomen, inBinärenAusdruck))
            else -> AST.Ausdruck.Variable(nomen)
          }
        }
        else -> when(vornomen.typ) {
          TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT -> AST.Ausdruck.Variable(nomen)
          TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN -> AST.Ausdruck.SelbstEigenschaftsZugriff(nomen)
          TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.DEIN -> AST.Ausdruck.MethodenBlockEigenschaftsZugriff(nomen)
          else -> throw GermanSkriptFehler.SyntaxFehler.ParseFehler(vornomen.toUntyped(), "bestimmter Artikel oder Possessivpronomen")
        }
      }
      is TokenTyp.VORNOMEN.ARTIKEL.UNBESTIMMT ->  {
        when (nächstesToken.typ) {
          is TokenTyp.OFFENE_ECKIGE_KLAMMER -> subParse(NomenAusdruck.Liste(nomen))
          else -> {
            val klasse = AST.TypKnoten(nomen, modulPfad!!)
            subParse(NomenAusdruck.ObjektInstanziierung(klasse))
          }
        }
      }
      TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN -> AST.Ausdruck.SelbstEigenschaftsZugriff(nomen)
      TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.DEIN -> AST.Ausdruck.MethodenBlockEigenschaftsZugriff(nomen)
      is TokenTyp.VORNOMEN.DEMONSTRATIV_PRONOMEN -> throw GermanSkriptFehler.SyntaxFehler.ParseFehler(vornomen.toUntyped(), null,
          "Die Demonstrativpronomen 'diese' und 'jene' dürfen nicht in Ausdrücken verwendet werden.")
      else -> throw Exception("Dieser Fall sollte nie ausgeführt werden.")
    }

    return when(peekType()){
      is TokenTyp.ALS -> {
        next()
        val typ = parseTypOhneArtikel(false)
        Pair(nomen, AST.Ausdruck.Konvertierung(ausdruck, typ))
      }
      is TokenTyp.OPERATOR -> {
         val operatorAusdruck  =
             if (inBinärenAusdruck) ausdruck
             else subParse(Ausdruck(mitVornomen = true, optionalesIstNachVergleich = false, linkerAusdruck = ausdruck))
        Pair(nomen, operatorAusdruck)
      }
      else -> Pair(nomen, ausdruck)
    }
  }

  fun parseArgument(): AST.Argument {
    var (nomen, wert) = parseNomenAusdruck<TokenTyp.VORNOMEN>("Vornomen", false)
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

  protected fun<T> parseBereich(erwartetesEndToken: TokenTyp = TokenTyp.PUNKT, parser: () -> T): T {
    expect<TokenTyp.DOPPELPUNKT>("':'")
    val result = parser()
    überspringeLeereZeilen()
    val endToken = next()
    if (endToken.typ != erwartetesEndToken){
      throw GermanSkriptFehler.SyntaxFehler.ParseFehler(endToken, erwartetesEndToken.anzeigeName)
    }
    return result
  }

  protected fun parseSätze(endToken: TokenTyp = TokenTyp.PUNKT): AST.Satz.Bereich =
      parseBereich(endToken) { subParse(Programm(id)) }.programm
  // endregion

  class Programm(override val id: ASTKnotenID, private val lexer: Lexer? = null): SubParser<AST.Programm>() {

    val istInGlobalenBereich = lexer != null

    override fun parseImpl(): AST.Programm {
      val programmStart = if (istInGlobalenBereich) next() else null
      val definitionen = AST.DefinitionsContainer()
      val sätze = mutableListOf<AST.Satz>()
      var hauptProgrammEnde = false

      loop@ while (true) {
        überspringeLeereZeilen()
        when {
          parseSatz()?.also { satz ->
            // Sätze werden nur von der Hauptdatei eingelesen
            if (!hauptProgrammEnde) {
              sätze += satz
            }
          } != null -> Unit
          parseDefinition(definitionen)?.also { definition ->
            if (definition is AST.Definition.Import) {
              lexer!!.importiereDatei(definition)
            }
          } != null -> Unit
          else -> when (peekType()) {
            is TokenTyp.HAUPT_PROGRAMM_ENDE -> {
              next()
              hauptProgrammEnde = true
            }
            is TokenTyp.EOF, TokenTyp.PUNKT, TokenTyp.AUSRUFEZEICHEN -> break@loop
            else -> throw GermanSkriptFehler.SyntaxFehler.ParseFehler(next())
          }
        }
      }
      return AST.Programm(programmStart, definitionen, AST.Satz.Bereich(sätze))
    }

    fun parseSatz(): AST.Satz? {
      val nextToken = peek()
      return when (nextToken.typ) {
        is TokenTyp.INTERN -> subParse(Satz.Intern)
        is TokenTyp.VORNOMEN -> subParse(Satz.VariablenDeklaration)
        is TokenTyp.DOPPELPUNKT -> AST.Satz.Bereich(parseSätze(TokenTyp.PUNKT).sätze)
        is TokenTyp.WENN -> subParse(Satz.Bedingung)
        is TokenTyp.SOLANGE -> subParse(Satz.SolangeSchleife)
        is TokenTyp.FORTFAHREN, is TokenTyp.ABBRECHEN -> subParse(Satz.SchleifenKontrolle)
        is TokenTyp.BEZEICHNER_KLEIN ->
          when (nextToken.wert) {
            "gebe", "zurück" -> subParse(Satz.Zurückgabe)
            "für" -> subParse(Satz.FürJedeSchleife)
            "importiere" -> when {
              peekType(1) is TokenTyp.ZEICHENFOLGE -> null
              else -> subParse(Satz.FunktionsAufruf)
            }
            "verwende" -> when {
              peekType(1) is TokenTyp.BEZEICHNER_GROSS -> null
              else -> subParse(Satz.FunktionsAufruf)
            }
            else -> subParse(Satz.FunktionsAufruf)
          }
        is TokenTyp.BEZEICHNER_GROSS -> {
          when (peekType(1)) {
            is TokenTyp.DOPPELPUNKT -> subParse(Satz.MethodenBlock)
            is TokenTyp.DOPPEL_DOPPELPUNKT -> subParse(Satz.FunktionsAufruf)
            else -> null
          }
        }
        else -> null
      }
    }

    companion object {
      val reservierteTypNamen = arrayOf("Zahl", "Boolean", "Zeichenfolge")
    }

    fun überprüfeDoppelteDefinition(container: AST.DefinitionsContainer, typDef: AST.Definition.Typdefinition) {
      val typName = typDef.namensToken.wert.capitalize()
      if (reservierteTypNamen.contains(typName)) {
        throw GermanSkriptFehler.ReservierterTypName(typDef.namensToken)
      }
      if (container.definierteTypen.containsKey(typName)) {
        throw GermanSkriptFehler.DoppelteDefinition.Typ(typDef.namensToken,
            container.definierteTypen.getValue(typName).namensToken)
      }
    }

    fun parseDefinition(container: AST.DefinitionsContainer): AST.Definition? {
      val nextToken = peek()
      return when(nextToken.typ) {
        is TokenTyp.DEKLINATION -> subParse(Definition.DeklinationsDefinition).also { deklination ->
          container.deklinationen += deklination
        }
        is TokenTyp.MODUL -> subParse(Definition.Modul).also { modul ->
          mergeModule(container, modul)
        }
        is TokenTyp.NOMEN -> subParse(Definition.Klasse).also { klasse ->
          überprüfeDoppelteDefinition(container, klasse)
          container.definierteTypen[klasse.namensToken.wert] = klasse
        }
        is TokenTyp.ADJEKTIV -> subParse(Definition.Schnittstelle).also { schnittstelle ->
          überprüfeDoppelteDefinition(container, schnittstelle)
          container.definierteTypen[schnittstelle.namensToken.wert.capitalize()] = schnittstelle
        }
        is TokenTyp.VERB -> parseFunktionOderMethode().also { funktion ->
          container.funktionenOderMethoden += funktion
        }
        is TokenTyp.ALS -> subParse(Definition.Konvertierung).also { konvertierung ->
          container.konvertierungen += konvertierung
        }
        is TokenTyp.BEZEICHNER_KLEIN -> {
          when (nextToken.wert) {
            "importiere" -> subParse(Definition.Import)
            "verwende" -> subParse(Definition.Verwende).also { verwende ->
              container.verwende += verwende
            }
            else -> null
          }
        }
        else -> null
      }
    }

    fun mergeModule(container: AST.DefinitionsContainer, modul: AST.Definition.Modul) {
      val modulName = modul.name.wert
      container.module.merge(modulName, modul) {
        modulVorhanden, moduleNeu ->
        // Funktionen und Methoden werden einfach hinzugefügt, der Definierer übernimmt hier die Aufgabe,
        // doppelte Definitionen zu erkennen
        modulVorhanden.definitionen.funktionenOderMethoden.addAll(moduleNeu.definitionen.funktionenOderMethoden)

        // bei den Klassen müssen wir doppelte Definitionen erkennen
        for ((typName, typ) in moduleNeu.definitionen.definierteTypen) {
          überprüfeDoppelteDefinition(modulVorhanden.definitionen, typ)
          modulVorhanden.definitionen.definierteTypen[typName] = typ
        }

        // Die Module müssen mit den anderen Modulen gemergt werden
        for (anderesModul in moduleNeu.definitionen.module.values) {
          mergeModule(modulVorhanden.definitionen, anderesModul)
        }

        // Deklinationen können einfach hinzugefügt werden. Doppelte Deklinationen sind erlaubt.
        modulVorhanden.definitionen.deklinationen.addAll(moduleNeu.definitionen.deklinationen)

        // Konvertierungen fügen wir auch einfach hinzu. Der Definierer wird auf Doppelte Konvertierungen aufmerksam machen.
        modulVorhanden.definitionen.konvertierungen.addAll(moduleNeu.definitionen.konvertierungen)
        modulVorhanden
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
    private fun parseBinärenAusdruck(minBindungskraft: Double, mitArtikel: Boolean, linkerA: AST.Ausdruck? = null) : AST.Ausdruck {
      var links = linkerA?: parseEinzelnerAusdruck(mitArtikel)

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

        // Hinter den Operatoren 'kleiner' und 'größer' kann optional das Wort 'als' kommen
        if ((operator == Operator.KLEINER || operator == Operator.GRÖßER) && peek().wert == "als") {
          next()
        }

        // Das String-Interpolations germanskript.Token ist nur dafür da, dass innerhalb einer String Interpolation wieder der Nominativ verwendet wird
        val inStringInterpolation = parseOptional<TokenTyp.STRING_INTERPOLATION>() != null
        überspringeLeereZeilen()

        val rechts = parseBinärenAusdruck(rechteBindungsKraft, mitArtikel)
        if (optionalesIstNachVergleich && operator.klasse == OperatorKlasse.VERGLEICH) {
          val ist = parseOptional<TokenTyp.ZUWEISUNG>()
          if (ist != null && ist.typ.numerus != Numerus.SINGULAR) {
            throw GermanSkriptFehler.SyntaxFehler.ParseFehler(ist.toUntyped())
          }
        }

        links = AST.Ausdruck.BinärerAusdruck(operatorToken, links, rechts,
            minBindungskraft == 0.0, inStringInterpolation
        )
      }

      return links
    }

    private fun parseEinzelnerAusdruck(mitArtikel: Boolean): AST.Ausdruck {
      val ausdruck = when (val tokenTyp = peekType()) {
        is TokenTyp.ZEICHENFOLGE -> AST.Ausdruck.Zeichenfolge(next().toTyped())
        is TokenTyp.ZAHL -> AST.Ausdruck.Zahl(next().toTyped())
        is TokenTyp.BOOLEAN -> AST.Ausdruck.Boolean(next().toTyped())
        is TokenTyp.BEZEICHNER_KLEIN -> AST.Ausdruck.FunktionsAufruf(subParse(FunktionsAufruf))
        is TokenTyp.VORNOMEN ->
          parseNomenAusdruck<TokenTyp.VORNOMEN>("Vornomen", true).second
        is TokenTyp.REFERENZ.ICH -> {
          if (!hierarchyContainsNode(ASTKnotenID.METHODEN_DEFINITION)) {
            throw GermanSkriptFehler.SyntaxFehler.ParseFehler(next(), null,
                "Die Selbstreferenz 'Ich' darf nur in einem Konstruktor, Methoden- oder Kovertierungsdefinition vorkommen.")
          }
          AST.Ausdruck.SelbstReferenz(next().toTyped())
        }
        is TokenTyp.REFERENZ.DU -> {
          if (!hierarchyContainsNode(ASTKnotenID.METHODEN_BLOCK)) {
            throw GermanSkriptFehler.SyntaxFehler.ParseFehler(next(), null,
                "Die Methodenblockreferenz 'Du' darf nur in einem Methodenblock vorkommen.")
          }
          AST.Ausdruck.MethodenBlockReferenz(next().toTyped())
        }
        is TokenTyp.OFFENE_KLAMMER -> {
          next()
          parseImpl().also { expect<TokenTyp.GESCHLOSSENE_KLAMMER>("')'") }
        }
        is TokenTyp.OPERATOR -> {
          if (currentToken != null &&
                  currentToken!!.typ is TokenTyp.OPERATOR &&
                  currentToken!!.toTyped<TokenTyp.OPERATOR>().typ.operator == Operator.MINUS) {
            throw GermanSkriptFehler.SyntaxFehler.ParseFehler(next(), null, "Es dürfen keine zwei '-' aufeinander folgen.")
          } else if (tokenTyp.operator != Operator.MINUS) {
            throw GermanSkriptFehler.SyntaxFehler.ParseFehler(next(), null)
          } else {
            next()
            AST.Ausdruck.Minus(parseEinzelnerAusdruck(false))
          }
        }
        is TokenTyp.BEZEICHNER_GROSS -> if (mitArtikel)
            throw GermanSkriptFehler.SyntaxFehler.ParseFehler(next(), "Artikel", "Vor einem Nomen muss ein Artikel stehen.")
          else
            AST.Ausdruck.Variable(parseNomenOhneVornomen(true))
        else -> throw GermanSkriptFehler.SyntaxFehler.ParseFehler(next())
      }
      return when (peekType()){
        is TokenTyp.ALS -> {
          next()
          val typ = parseTypOhneArtikel(false)
          AST.Ausdruck.Konvertierung(ausdruck, typ)
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

    class ObjektInstanziierung(val klasse: AST.TypKnoten): NomenAusdruck<AST.Ausdruck.ObjektInstanziierung>(klasse.name) {
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

        return AST.Ausdruck.ObjektInstanziierung(klasse, eigenschaftsZuweisungen)
      }
    }

    class EigenschaftsZugriff(nomen: AST.Nomen, private val inBinärenAusdruck: Boolean): NomenAusdruck<AST.Ausdruck.EigenschaftsZugriff>(nomen) {
      override val id: ASTKnotenID
        get() = ASTKnotenID.EIGENSCHAFTS_ZUGRIFF

      override fun parseImpl(): AST.Ausdruck.EigenschaftsZugriff {
        return if (nomen.vornomen!!.typ is TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN) {
          val eigenschaft = parseNomenOhneVornomen(false)
          val vornomenTyp = nomen.vornomen.typ
          val objekt = if (vornomenTyp is TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN) {
            AST.Ausdruck.SelbstEigenschaftsZugriff(eigenschaft)
          } else {
            AST.Ausdruck.MethodenBlockEigenschaftsZugriff(eigenschaft)
          }
          AST.Ausdruck.EigenschaftsZugriff(nomen, objekt)
        } else {
          val objekt = parseNomenAusdruck<TokenTyp.VORNOMEN.ARTIKEL>("Artikel", inBinärenAusdruck).second
          AST.Ausdruck.EigenschaftsZugriff(nomen, objekt)
        }
      }
    }
  }

  object FunktionsAufruf: SubParser<AST.Funktion>() {
    override val id: ASTKnotenID
      get() = ASTKnotenID.FUNKTIONS_AUFRUF

    override fun parseImpl(): AST.Funktion {
      val modulPfad = parseModulPfad()
      val verb = expect<TokenTyp.BEZEICHNER_KLEIN>("bezeichner")
      val objekt = parseOptional<AST.Argument, TokenTyp.VORNOMEN>(::parseArgument)
      val reflexivPronomen = if (objekt == null) parseOptional<TokenTyp.REFLEXIV_PRONOMEN>() else null
      val präpositionen = parsePräpositionsArgumente()
      val suffix = parseOptional<TokenTyp.BEZEICHNER_KLEIN>()
      return AST.Funktion(modulPfad, verb, objekt, reflexivPronomen, präpositionen, suffix)
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
    override fun bewacheKnoten() {
      if (parentNodeId == ASTKnotenID.MODUL) {
        throw GermanSkriptFehler.SyntaxFehler.ParseFehler(next(), null, "In einem Modul sind nur weitere Definitioen und keine Sätze erlaubt.")
      }
    }

    object Intern: Satz<AST.Satz.Intern>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.INTERN

      override fun bewacheKnoten() {
        super.bewacheKnoten()
        if (!hierarchyContainsAnyNode(ASTKnotenID.FUNKTIONS_DEFINITION, ASTKnotenID.METHODEN_DEFINITION)) {
          throw GermanSkriptFehler.SyntaxFehler.ParseFehler(next(), null, "Das Schlüsselwort 'intern' darf nur in einer Funktionsdefinition stehen.")
        }
      }

      override fun parseImpl(): AST.Satz.Intern {
        expect<TokenTyp.INTERN>("intern")
        if (peekType() !is TokenTyp.PUNKT) {
          throw GermanSkriptFehler.SyntaxFehler.ParseFehler(next(), ".")
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
          throw GermanSkriptFehler.SyntaxFehler.ParseFehler(artikel.toUntyped(), null, "Neuzuweisungen mit 'dein' funktionieren nicht.\n" +
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
        val name = parseNomenOhneVornomen(true)
        val sätze = parseSätze(TokenTyp.AUSRUFEZEICHEN)
        return AST.Satz.MethodenBlock(name, sätze)
      }
    }

    object Zurückgabe : Satz<AST.Satz.Zurückgabe>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.ZURÜCKGABE

      override fun bewacheKnoten() {
        if (!hierarchyContainsAnyNode(ASTKnotenID.FUNKTIONS_DEFINITION, ASTKnotenID.METHODEN_DEFINITION, ASTKnotenID.KONVERTIERUNGS_DEFINITION)) {
          throw GermanSkriptFehler.SyntaxFehler.ParseFehler(next(), null, "'gebe ... zurück' darf hier nicht stehen.")
        }
      }

      override fun parseImpl(): AST.Satz.Zurückgabe {
        val schlüsselWort = next().toTyped<TokenTyp.BEZEICHNER_KLEIN>()
        return if (schlüsselWort.wert == "zurück") {
          AST.Satz.Zurückgabe(schlüsselWort, null)
        } else {
          val ausdruck = subParse(Ausdruck(mitVornomen = true, optionalesIstNachVergleich = false))
          parseKleinesSchlüsselwort("zurück")
          AST.Satz.Zurückgabe(schlüsselWort, ausdruck)
        }
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
        val singular = parseNomenOhneVornomen(true)
        val binder = when (peekType()) {
          is TokenTyp.BEZEICHNER_GROSS -> parseNomenOhneVornomen(true)
          else -> singular
        }
        val liste = when(peekType()) {
          is TokenTyp.BEZEICHNER_KLEIN -> {
            parseKleinesSchlüsselwort("in")
            subParse(Ausdruck(mitVornomen = true, optionalesIstNachVergleich = false))
          }
          else -> {
            if (singular.istSymbol) {
              throw GermanSkriptFehler.SyntaxFehler.ParseFehler(singular.bezeichner.toUntyped(), "Bezeichner",
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
          throw GermanSkriptFehler.SyntaxFehler.ParseFehler(schlüsselwort, null, "Das Schlüsselwort '${schlüsselwort.wert}' darf nur in einer Schleife verwendet werden.")
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
      if (!(parentNodeId == ASTKnotenID.PROGRAMM || parentNodeId == ASTKnotenID.MODUL)) {
        throw GermanSkriptFehler.SyntaxFehler.UngültigerBereich(next(),
            "Definitionen können nur im globalem Bereich oder in Modulen geschrieben werden.")
      }
    }

    protected inline fun<reified VornomenT: TokenTyp.VORNOMEN.ARTIKEL> parseTypUndName(erwarteterArtikel: String): AST.Definition.TypUndName {
      val typ = parseTypMitArtikel<VornomenT>(erwarteterArtikel, true)
      val bezeichner = parseOptional<TokenTyp.BEZEICHNER_GROSS>()
      val name = if (bezeichner != null) AST.Nomen(null, bezeichner) else typ.name
      return AST.Definition.TypUndName(typ, name)
    }

    object Modul: Definition<AST.Definition.Modul>() {
      override val id = ASTKnotenID.MODUL

      override fun parseImpl(): AST.Definition.Modul {
        expect<TokenTyp.MODUL>("'Modul'")
        val modulPfad = parseModulPfad()
        val name = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner")
        val definitionen =  parseBereich { subParse(Programm(id)) }.definitionen
        return modulPfad.reversed().fold(AST.Definition.Modul(name, definitionen)) { modul, bezeichner ->
          val container = AST.DefinitionsContainer()
          container.module[modul.name.wert] = AST.Definition.Modul(modul.name, modul.definitionen)
          AST.Definition.Modul(bezeichner, container)
        }
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

      // ein Wort ist ein Bezeichner ohne Symbole und mit nur einem Wort
      private fun parseWort(): TypedToken<TokenTyp.BEZEICHNER_GROSS> {
        val bezeichner = expect<TokenTyp.BEZEICHNER_GROSS>("Wort")
        if (bezeichner.typ.istSymbol || bezeichner.typ.teilWörter.size > 1) {
          throw GermanSkriptFehler.SyntaxFehler.ParseFehler(bezeichner.toUntyped(), "Wort", "Ein Wort darf nicht aus " +
              "Teilwörtern und Symbolen bestehen.")
        }
        return bezeichner
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
          arrayOf(singular[0], singular[0], singular[0], singular[0])
        }

        return AST.Definition.DeklinationsDefinition.Definition(Deklination(genus, singular, plural))
      }

      private fun parseDuden(): AST.Definition.DeklinationsDefinition.Duden {
        expect<TokenTyp.DUDEN>("'Duden'")
        expect<TokenTyp.OFFENE_KLAMMER>("'('")
        val wort = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner")
        expect<TokenTyp.GESCHLOSSENE_KLAMMER>("')'")
        return  AST.Definition.DeklinationsDefinition.Duden(wort)
      }
    }

    class FunktionsSignatur(
        override val id: ASTKnotenID,
        private val rückgabeTyp: AST.TypKnoten?,
        private val istMethode: Boolean
    ): Definition<AST.Definition.FunktionsSignatur>() {

      override fun bewacheKnoten() {
        // hier muss nichts überwacht werden
        // Da eine Funktionssignatur zu Definitionen zählen und Definitionen nur
        // im globalen Bereich oder in Modulen auftreten dürfen, muss bewacheKnoten() hier
        // wieder überschrieben werden, da Funktionssignaturen innerhalb von Funktions-, Methoden- und
        // Schnittstellendefinitionen verwendet werden
      }

      override fun parseImpl(): AST.Definition.FunktionsSignatur {
        val name = expect<TokenTyp.BEZEICHNER_KLEIN>("bezeichner")
        val reflexivPronomen = if (istMethode) parseOptional<TokenTyp.REFLEXIV_PRONOMEN>() else null
        val objekt = if (reflexivPronomen == null) {
          parseOptional<AST.Definition.TypUndName, TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT> {
            parseTypUndName<TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT>("bestimmter Artikel") }
        } else {
         null
        }
        val präpositionsParameter = parsePräpositionsParameter()
        val suffix = parseOptional<TokenTyp.BEZEICHNER_KLEIN>()

        return AST.Definition.FunktionsSignatur(rückgabeTyp, name, reflexivPronomen, objekt, präpositionsParameter, suffix)
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

    class Funktion(
        override val id: ASTKnotenID,
        private val rückgabeTyp: AST.TypKnoten?,
        private val hatReflexivPronomen: Boolean
    ): Definition<AST.Definition.FunktionOderMethode.Funktion>() {

      override fun parseImpl(): AST.Definition.FunktionOderMethode.Funktion {
        val signatur = subParse(FunktionsSignatur(id, rückgabeTyp, hatReflexivPronomen))
        val sätze = parseSätze()
        return AST.Definition.FunktionOderMethode.Funktion(signatur, sätze)
      }
    }

    object Klasse: Definition<AST.Definition.Typdefinition.Klasse>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.KLASSEN_DEFINITION

      override fun parseImpl(): AST.Definition.Typdefinition.Klasse {
        expect<TokenTyp.NOMEN>("'Nomen'")
        val name = AST.TypKnoten(parseNomenOhneVornomen(false), emptyList())

        val elternKlasse = when (peekType()) {
          is TokenTyp.ALS -> next().let { parseTypOhneArtikel(false) }
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
        return AST.Definition.Typdefinition.Klasse(name, elternKlasse, eingenschaften.toMutableList(), konstruktorSätze)
      }
    }

    object Schnittstelle: Definition<AST.Definition.Typdefinition.Schnittstelle>() {
      override val id = ASTKnotenID.SCHNITTSTELLE

      override fun parseImpl(): AST.Definition.Typdefinition.Schnittstelle {
        expect<TokenTyp.ADJEKTIV>("'Adjektiv'")
        val name = expect<TokenTyp.BEZEICHNER_KLEIN>("bezeichner")
        val definitionen = parseBereich {
          überspringeLeereZeilen()
          val signaturen = mutableListOf<AST.Definition.FunktionsSignatur>()
          while (peekType() != TokenTyp.PUNKT) {
            überspringeLeereZeilen()
            val rückgabeTyp = parseFunktionsAnfang()
            signaturen += subParse(FunktionsSignatur(id, rückgabeTyp, true))
          }
          signaturen
        }
        return AST.Definition.Typdefinition.Schnittstelle(name, definitionen)
      }
    }

    object Konvertierung: Definition<AST.Definition.Konvertierung>() {
      override val id = ASTKnotenID.KONVERTIERUNGS_DEFINITION

      override fun parseImpl(): AST.Definition.Konvertierung {
        expect<TokenTyp.ALS>("'als'")
        val typ = parseTypOhneArtikel(false)
        parseKleinesSchlüsselwort("für")
        val klasse = parseTypOhneArtikel(false)
        return AST.Definition.Konvertierung(typ, klasse, parseSätze())
      }
    }

    object Import: Definition<AST.Definition.Import>() {
      override val id = ASTKnotenID.IMPORT

      override fun parseImpl(): AST.Definition.Import {
        parseKleinesSchlüsselwort("importiere")
        val dateiPfad = expect<TokenTyp.ZEICHENFOLGE>("Dateipfad")
        return AST.Definition.Import(dateiPfad)
      }
    }

    object Verwende: Definition<AST.Definition.Verwende>() {
      override val id = ASTKnotenID.VERWENDE

      override fun parseImpl(): AST.Definition.Verwende {
        parseKleinesSchlüsselwort("verwende")
        val modulPfad = parseModulPfad()
        val modulOderKlasse = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner")
        return AST.Definition.Verwende(modulPfad, modulOderKlasse)
      }
    }
  }
}

fun main() {
  val ast = Parser(File("./iterationen/iter_2/code.gm")).parse()
  println(ast)
}