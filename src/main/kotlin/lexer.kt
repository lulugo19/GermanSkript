import java.util.*
enum class Assoziativität {
    LINKS,
    RECHTS,
}

enum class Operator(val bindungsKraft: Int, val assoziativität: Assoziativität) {
    PLUS(4, Assoziativität.LINKS),
    MINUS(4, Assoziativität.LINKS),
    MAL(5, Assoziativität.LINKS),
    GETEILT(5, Assoziativität.LINKS),
    MODULO(5, Assoziativität.LINKS),
    HOCH(6, Assoziativität.RECHTS),
    GLEICH(3, Assoziativität.LINKS),
    UND(2, Assoziativität.LINKS),
    ODER(1, Assoziativität.LINKS),
    KLEINER(3, Assoziativität.LINKS),
    GRÖßER(3, Assoziativität.LINKS),
    KLEINER_GLEICH(3, Assoziativität.LINKS),
    GRÖSSER_GLEICH(3, Assoziativität.LINKS),
    NEGATION(3, Assoziativität.RECHTS),
    UNGLEICH(3, Assoziativität.LINKS),
}

// Genus (Geschlecht)
enum class Genus {
    MASKULINUM,
    FEMININUM,
    NEUTRUM,
}

// Kasus (Fall)
enum class Kasus {
    Nominativ,
    Genitiv,
    Dativ,
    Akkusativ,
}

// Numerus (Anzahl)
enum class Numerus {
    SINGULAR,
    PLURAL,
    BEIDE,
}

data class Form(val bestimmt: Boolean, val genus: Genus?, val kasus: Kasus, val numerus: Numerus)

data class Token(val typ: TokenTyp, val wert: String, val anfang: Position, val ende: Position) {
    // um die Ausgabe zu vereinfachen
    // override fun toString(): String = typ.toString()

    fun <T: TokenTyp> toTyped() = TypedToken<T>(typ as T, wert, anfang, ende)

    open class Position(val zeile: Int, val spalte: Int) {
        object Ende: Token.Position(-1, -1)

        override fun toString(): String {
            return "($zeile, $spalte)"
        }
    }
}

// für den Parser gedacht
data class TypedToken<out T : TokenTyp>(val typ: T, val wert: String, val anfang: Token.Position, val ende: Token.Position) {
    fun toUntyped()  = Token(typ, wert, anfang, ende)
}

sealed class TokenTyp(val anzeigeName: String) {
    override fun toString(): String = javaClass.simpleName

    object FEHLER: TokenTyp("'Fehler'")

    // Schlüsselwörter
    object DEKLINATION: TokenTyp("'Deklination'")
    object GEBE: TokenTyp("'gebe'")
    object ZURÜCK: TokenTyp("'zurück'")
    object WENN: TokenTyp("'wenn'")
    object DANN: TokenTyp("'dann'")
    object SONST: TokenTyp("'sonst'")
    object SOLANGE: TokenTyp("'solange")
    object ALS: TokenTyp("'Als'")
    object PLURAL: TokenTyp("'Plural'")
    object SINGULAR: TokenTyp("'Singular'")
    object FORTFAHREN: TokenTyp("'fortfahren'")
    object ABBRECHEN: TokenTyp("'abbrechen'")
    object VERB: TokenTyp("'Verb'")
    object NOMEN: TokenTyp("'Nomen'")
    object ADJEKTIV: TokenTyp("'Adjektiv'")
    object ALIAS: TokenTyp("'Alias'")
    object MODUL: TokenTyp("'Modul'")

    // Artikel und Präpositionen
    data class JEDE(val genus: Genus): TokenTyp("'jeder' oder 'jede' oder 'jedes'")
    data class ZUWEISUNG(val anzahl: Numerus): TokenTyp("'ist' oder 'sind' oder '='")
    data class ARTIKEL(val formen: List<Form>): TokenTyp("Artikel") {} // ein, eine, eines, der, die, das, den
    data class PRÄPOSITION(val fälle: EnumSet<Kasus>): TokenTyp("Präposition")

    //Symbole
    object OFFENE_KLAMMER: TokenTyp("'('")
    object GESCHLOSSENE_KLAMMER: TokenTyp("')'")
    object KOMMA: TokenTyp("','")
    object PUNKT: TokenTyp("'.'")
    object DOPPELPUNKT: TokenTyp("':'")
    object DOPPEL_DOPPELPUNKT: TokenTyp("'::'")
    object SEMIKOLON: TokenTyp("';'") // Semikolon
    object NEUE_ZEILE: TokenTyp("neue Zeile")
    object EOF: TokenTyp("'EOF'")
    data class OPERATOR(val operator: Operator): TokenTyp("Operator")

    // Identifier
    data class BEZEICHNER_KLEIN(val name: String): TokenTyp("bezeichner")
    data class BEZEICHNER_GROSS(val name: String): TokenTyp("Bezeichner")

    // Literale
    data class BOOLEAN(val boolean: Boolean): TokenTyp("'richtig' oder 'falsch'")
    data class ZAHL(val zahl: Double): TokenTyp("Zahl")
    data class ZEICHENFOLGE(val zeichenfolge: String): TokenTyp("Zeichenfolge")

    object UNDEFINIERT: TokenTyp("undefiniert")
}


private val SYMBOL_MAPPING = mapOf<Char, TokenTyp>(
    '(' to TokenTyp.OFFENE_KLAMMER,
    ')' to TokenTyp.GESCHLOSSENE_KLAMMER,
    ',' to TokenTyp.KOMMA,
    ';' to TokenTyp.SEMIKOLON,
    '.' to TokenTyp.PUNKT,
    ':' to TokenTyp.DOPPELPUNKT,
    '!' to TokenTyp.OPERATOR(Operator.NEGATION),
    ';' to TokenTyp.SEMIKOLON,
    '+' to TokenTyp.OPERATOR(Operator.PLUS),
    '-' to TokenTyp.OPERATOR(Operator.MINUS),
    '*' to TokenTyp.OPERATOR(Operator.MAL),
    '/' to TokenTyp.OPERATOR(Operator.GETEILT),
    '^' to TokenTyp.OPERATOR(Operator.HOCH),
    '%' to TokenTyp.OPERATOR(Operator.MODULO),
    '=' to TokenTyp.ZUWEISUNG(Numerus.BEIDE),
    '>' to TokenTyp.OPERATOR(Operator.GRÖßER),
    '<' to TokenTyp.OPERATOR(Operator.KLEINER),
    '&' to TokenTyp.UNDEFINIERT,
    '|' to TokenTyp.UNDEFINIERT
)

private val DOPPEL_SYMBOL_MAPPING = mapOf<String, TokenTyp>(
    "==" to TokenTyp.OPERATOR(Operator.GLEICH),
    "!=" to TokenTyp.OPERATOR(Operator.UNGLEICH),
    ">=" to TokenTyp.OPERATOR(Operator.GRÖSSER_GLEICH),
    "<=" to TokenTyp.OPERATOR(Operator.KLEINER_GLEICH),
    "&&" to TokenTyp.OPERATOR(Operator.UND),
    "||" to TokenTyp.OPERATOR(Operator.ODER),
    "::" to TokenTyp.DOPPEL_DOPPELPUNKT
)


private val WORT_MAPPING = mapOf<String, TokenTyp>(
    // Schlüsselwörter
    "Deklination" to TokenTyp.DEKLINATION,
    "Nomen" to TokenTyp.NOMEN,
    "Verb" to TokenTyp.VERB,
    "Adjektiv" to TokenTyp.ADJEKTIV,
    "Alias" to TokenTyp.ALIAS,
    "gebe" to TokenTyp.GEBE,
    "zurück" to TokenTyp.ZURÜCK,
    "wenn" to TokenTyp.WENN,
    "dann" to TokenTyp.DANN,
    "sonst" to TokenTyp.SONST,
    "solange" to TokenTyp.SOLANGE,
    "fortfahren" to TokenTyp.FORTFAHREN,
    "abbrechen" to TokenTyp.ABBRECHEN,
    "als" to TokenTyp.ALS,
    "Singular" to TokenTyp.SINGULAR,
    "Plural" to TokenTyp.PLURAL,
    "Modul" to TokenTyp.MODUL,
    "jede" to TokenTyp.JEDE(Genus.FEMININUM),
    "jeden" to TokenTyp.JEDE(Genus.MASKULINUM),
    "jedes" to TokenTyp.JEDE(Genus.NEUTRUM),

    // Werte
    "wahr" to TokenTyp.BOOLEAN(true),
    "falsch" to TokenTyp.BOOLEAN(false),

    // Operatoren
    "ist" to TokenTyp.ZUWEISUNG(Numerus.SINGULAR),
    "sind" to TokenTyp.ZUWEISUNG(Numerus.PLURAL),
    "gleich" to TokenTyp.OPERATOR(Operator.GLEICH),
    "ungleich" to TokenTyp.OPERATOR(Operator.UNGLEICH),
    "und" to TokenTyp.OPERATOR(Operator.UND),
    "oder" to TokenTyp.OPERATOR(Operator.ODER),
    "kleiner" to TokenTyp.OPERATOR(Operator.KLEINER),
    "größer" to TokenTyp.OPERATOR(Operator.GRÖßER),
    "plus" to TokenTyp.OPERATOR(Operator.PLUS),
    "minus" to TokenTyp.OPERATOR(Operator.MINUS),
    "mal" to TokenTyp.OPERATOR(Operator.MAL),
    "durch" to TokenTyp.OPERATOR(Operator.GETEILT),
    "hoch" to TokenTyp.OPERATOR(Operator.HOCH),
    "modulo" to TokenTyp.OPERATOR(Operator.MODULO),

    // Artikel
    "der" to TokenTyp.ARTIKEL(listOf(
        Form(true, Genus.MASKULINUM, Kasus.Nominativ, Numerus.SINGULAR),
        Form(true, Genus.FEMININUM, Kasus.Genitiv, Numerus.SINGULAR),
        Form(true, Genus.FEMININUM, Kasus.Dativ, Numerus.SINGULAR),
        Form(true,null, Kasus.Genitiv, Numerus.PLURAL)
    )),
    "die" to TokenTyp.ARTIKEL(listOf(
        Form(true, Genus.FEMININUM, Kasus.Nominativ, Numerus.SINGULAR),
        Form(true, Genus.FEMININUM, Kasus.Nominativ, Numerus.PLURAL),
        Form(true, Genus.FEMININUM, Kasus.Akkusativ, Numerus.SINGULAR),
        Form(true, null, Kasus.Akkusativ, Numerus.PLURAL)
    )),
    "das" to TokenTyp.ARTIKEL(listOf(
        Form(true, Genus.NEUTRUM, Kasus.Nominativ, Numerus.SINGULAR),
        Form(true, Genus.NEUTRUM, Kasus.Akkusativ, Numerus.SINGULAR)
    )),
    "den" to TokenTyp.ARTIKEL(listOf(
        Form(true, Genus.MASKULINUM, Kasus.Akkusativ, Numerus.SINGULAR),
        Form(true, null, Kasus.Dativ, Numerus.PLURAL)

    )),
    "ein" to TokenTyp.ARTIKEL(listOf(
        Form(false, Genus.MASKULINUM, Kasus.Nominativ, Numerus.SINGULAR),
        Form(false, Genus.NEUTRUM, Kasus.Nominativ, Numerus.SINGULAR),
        Form(false, Genus.NEUTRUM, Kasus.Akkusativ, Numerus.PLURAL)
    )),
    "eine" to TokenTyp.ARTIKEL(listOf(
        Form(false, Genus.FEMININUM, Kasus.Nominativ, Numerus.SINGULAR),
        Form(false, Genus.FEMININUM, Kasus.Akkusativ, Numerus.SINGULAR)
    ) ),
    "eines" to TokenTyp.ARTIKEL(listOf(
        Form(false, Genus.MASKULINUM, Kasus.Genitiv, Numerus.SINGULAR),
        Form(false, Genus.NEUTRUM, Kasus.Genitiv, Numerus.SINGULAR)
    )),
    "einer" to TokenTyp.ARTIKEL(listOf(
        Form(false, Genus.FEMININUM, Kasus.Genitiv, Numerus.SINGULAR),
        Form(false, Genus.FEMININUM, Kasus.Dativ, Numerus.SINGULAR)
    )),
    "einige" to TokenTyp.ARTIKEL(listOf(
        Form(false, null, Kasus.Nominativ, Numerus.PLURAL),
        Form(false, null, Kasus.Akkusativ, Numerus.PLURAL)
    )),
    "einigen" to TokenTyp.ARTIKEL(listOf(
        Form(false, null, Kasus.Dativ, Numerus.PLURAL)
    )),
    "einiger" to TokenTyp.ARTIKEL(listOf(
        Form(false, null, Kasus.Genitiv, Numerus.PLURAL)
    ))
)

class Lexer(val quellcode: String) {
    private var iterator = Peekable(quellcode.iterator())
    private var zeilenIndex = 0

    private val currentTokenPos: Token.Position get() = Token.Position(zeilenIndex, iterator.index)
    private val nextTokenPos: Token.Position get() = Token.Position(zeilenIndex, iterator.index + 1)
    private val eofToken = Token(TokenTyp.EOF, "EOF", Token.Position.Ende, Token.Position.Ende)

    fun tokeniziere() : Sequence<Token> = sequence {
        var inMehrZeilenKommentar = false
        for ((zeilenIndex, zeile) in quellcode.lines().map(String::trim).withIndex()) {
            this@Lexer.zeilenIndex = zeilenIndex
            var kannWortLesen = true
            if (zeile == "") {
                continue
            }
            iterator = Peekable(zeile.iterator())
            while (iterator.peek() != null) {
                val zeichen = iterator.peek()!!
                // ignoriere Kommentare
                if (inMehrZeilenKommentar) {
                    if (zeichen == '*' && iterator.peekDouble() == '/') {
                        iterator.next()
                        iterator.next()
                        inMehrZeilenKommentar = false
                    }
                    break
                }
                if (zeichen == '/' && iterator.peekDouble() == '/') {
                    iterator.next()
                    iterator.next()
                    break
                }
                if (zeichen == '/' && iterator.peekDouble() == '*') {
                    iterator.next()
                    iterator.next()
                    inMehrZeilenKommentar = true
                    break
                }
                if (zeichen == ' ') {
                    iterator.next()
                    kannWortLesen = true
                    continue
                }
                yieldAll(when {
                    SYMBOL_MAPPING.containsKey(zeichen) -> symbol().also { kannWortLesen = true }
                    zeichen.isDigit() -> zahl().also { kannWortLesen = false }
                    zeichen == '"' -> zeichenfolge().also { kannWortLesen = false }
                    kannWortLesen -> wort().also { kannWortLesen = false }
                    else -> throw GermanScriptFehler.SyntaxFehler.LexerFehler(
                        Token(
                            TokenTyp.FEHLER,
                            zeichen.toString(),
                            currentTokenPos,
                            nextTokenPos
                        )
                    )
                })
            }

            yield(Token(
                TokenTyp.NEUE_ZEILE,
                "\\n",
                currentTokenPos,
                nextTokenPos
            ))
        }
        while (true) {
            yield(eofToken)
        }
    }

    private fun symbol(): Sequence<Token> = sequence {
        val startPos = currentTokenPos
        var symbolString = iterator.next()!!.toString()
        val potenziellesDoppelSymbol = symbolString + iterator.peek()!!.toString()
        val tokenTyp = if (DOPPEL_SYMBOL_MAPPING.containsKey(potenziellesDoppelSymbol)) {
            iterator.next()
            symbolString = potenziellesDoppelSymbol
            DOPPEL_SYMBOL_MAPPING.getValue(potenziellesDoppelSymbol)
        } else if (SYMBOL_MAPPING.containsKey(symbolString[0])) {
            SYMBOL_MAPPING.getValue(symbolString[0])
        } else {
            TokenTyp.UNDEFINIERT
        }
        val endPos = currentTokenPos
        if (tokenTyp is TokenTyp.UNDEFINIERT) {
            val fehlerToken = Token(TokenTyp.FEHLER, symbolString, startPos, endPos)
            throw GermanScriptFehler.SyntaxFehler.LexerFehler(fehlerToken)
        }
        yield(Token(tokenTyp, symbolString, startPos, endPos))
    }

    private val ZAHLEN_PATTERN = """(0|[1-9]\d?\d?(\.\d{3})+|[1-9]\d*)(\,\d+)?""".toRegex()

    private fun zahl(): Sequence<Token> = sequence {
        val startPos = currentTokenPos
        var zahlenString = ""
        var hinternKomma = false
        while (iterator.peek() != null) {
            val zeichen = iterator.peek()!!
            if (zeichen.isDigit()) {
                zahlenString += iterator.next()
            } else if (!hinternKomma && (zeichen == '.' || zeichen == ',') && iterator.peekDouble()?.isDigit() == true) {
                hinternKomma = zeichen == ','
                zahlenString += iterator.next()
                zahlenString += iterator.next()
            } else {
                break
            }
        }

        val endPos = currentTokenPos
        val zahl = zahlenString.replace(".", "").replace(',', '.').toDouble()
        val token = Token(TokenTyp.ZAHL(zahl), zahlenString, startPos, endPos)

        if (!zahlenString.matches(ZAHLEN_PATTERN)) {
            val fehlerToken = Token(TokenTyp.FEHLER, zahlenString, startPos, endPos)
            throw GermanScriptFehler.SyntaxFehler.LexerFehler(fehlerToken)
        }
        yield(token)
    }

    private fun zeichenfolge(): Sequence<Token> = sequence {
        val startPos = currentTokenPos
        iterator.next() // consume first '"'
        var zeichenfolge = ""
        while (iterator.peek() != null && iterator.peek() != '"') {
            zeichenfolge += iterator.next()
        }
        if (iterator.peek() != '"') {
            throw GermanScriptFehler.SyntaxFehler.LexerFehler(eofToken)
        }
        iterator.next()
        val endPos = currentTokenPos
        val token = Token(TokenTyp.ZEICHENFOLGE(zeichenfolge), '"' + zeichenfolge + '"', startPos, endPos)
        yield(token)
    }

    private val NOMEN_PATTERN = """[A-Z]\w*""".toRegex()
    private val VERB_PATTERN = """[a-z]\w*[\?!]?""".toRegex()

    private fun wort(): Sequence<Token> = sequence {
        val firstWordStartPos = currentTokenPos
        val erstesWort = teilWort()
        val firstWordEndPos = currentTokenPos
        var spaceBetweenWords = ""
        when {
            WORT_MAPPING.containsKey(erstesWort) -> when (erstesWort) {
                "größer", "kleiner" -> {
                    while (iterator.peek() == ' ') {
                        spaceBetweenWords += iterator.next()
                    }
                    val nächstesZeichen = iterator.peek()
                    val nächstesIstWort = !(SYMBOL_MAPPING.containsKey(nächstesZeichen) ||
                        nächstesZeichen == '&' ||
                        nächstesZeichen == '|' ||
                        nächstesZeichen == '"' ||
                        nächstesZeichen?.isDigit() == true)
                    if (nächstesIstWort) {
                        val nextWordStartPos = currentTokenPos
                        val nächstesWort = teilWort()
                        val nextWordEndPos = currentTokenPos
                        if (nächstesWort == "gleich") {
                            val tokenTyp = when (erstesWort) {
                                "größer" -> TokenTyp.OPERATOR(Operator.GRÖSSER_GLEICH)
                                "kleiner" -> TokenTyp.OPERATOR(Operator.KLEINER_GLEICH)
                                else -> throw Exception("Diser Fall wird nie ausgeführt")
                            }
                            yield(Token(tokenTyp, erstesWort + spaceBetweenWords + nächstesWort, firstWordStartPos, nextWordEndPos))
                        } else {
                            yield(Token(WORT_MAPPING.getValue(erstesWort), erstesWort, firstWordStartPos, firstWordEndPos))
                            val tokenTyp = (WORT_MAPPING.getOrElse(nächstesWort, {
                                when {
                                    NOMEN_PATTERN.matches(nächstesWort) -> TokenTyp.BEZEICHNER_GROSS(nächstesWort)
                                    VERB_PATTERN.matches(nächstesWort) -> TokenTyp.BEZEICHNER_KLEIN(nächstesWort)
                                    else -> throw GermanScriptFehler.SyntaxFehler.LexerFehler(
                                        Token(TokenTyp.FEHLER, nächstesWort, nextWordStartPos, nextWordEndPos)
                                    )
                                }
                            }))
                            yield(Token(tokenTyp, nächstesWort, nextWordStartPos, nextWordEndPos))
                        }
                    }
                    else {
                        yield(Token(WORT_MAPPING.getValue(erstesWort), erstesWort, firstWordStartPos, firstWordEndPos))
                    }
                }
                else -> yield(Token(WORT_MAPPING.getValue(erstesWort), erstesWort, firstWordStartPos, firstWordEndPos))
            }
            NOMEN_PATTERN.matches(erstesWort) -> yield(Token(TokenTyp.BEZEICHNER_GROSS(erstesWort), erstesWort, firstWordStartPos, firstWordEndPos))
            VERB_PATTERN.matches(erstesWort) -> yield(Token(TokenTyp.BEZEICHNER_KLEIN(erstesWort), erstesWort, firstWordStartPos, firstWordEndPos))
            else -> throw GermanScriptFehler.SyntaxFehler.LexerFehler(Token(TokenTyp.FEHLER, erstesWort, firstWordStartPos, firstWordEndPos))
        }
    }

    private fun teilWort(): String {
        var wort = iterator.next()!!.toString()
        while (iterator.peek() != null) {
            val nächstesZeiches = iterator.peek()!!
            if (nächstesZeiches == ' ' ||
                (nächstesZeiches != '!' && nächstesZeiches != '?' && SYMBOL_MAPPING.containsKey(nächstesZeiches))) {
                break
            }
            wort += iterator.next()!!
        }
        return wort
    }
}



fun main() {

    fun outputTokenTypes(code: String) {
        Lexer(code).tokeniziere().takeWhile { token -> token.typ != TokenTyp.EOF }.forEach { println(it) }
    }

    val test = """
        A größer gleich "String" und B kleiner Hallo oder C ungleich 3 && D || E ungleich "Hallo"
    """.trimIndent()

    outputTokenTypes(test)
}