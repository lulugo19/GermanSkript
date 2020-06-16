import kotlin.Exception

enum class Geschlecht {
    MÄNNLICH,
    WEIBLICH,
    NEUTRAL,
}

enum class Operator {
    PLUS,
    MINUS,
    MAL,
    GETEILT,
    GLEICH,
    HOCH,
    UND,
    ODER,
    KLEINER,
    GRÖßER,
    KLEINER_GLEICH,
    GRÖßER_GLEICH,
    NEGATION,
    UNGLEICH,
}

enum class Anzahl {
    SINGULAR,
    PLURAL,
    BEIDES,
}

data class Token(val typ: TokenTyp, val wert: String, val anfang: Pair<Int, Int>, val ende: Pair<Int, Int>)

sealed class TokenTyp() {
    override fun toString(): String = javaClass.simpleName

    // Schlüsselwörter
    object MIT: TokenTyp()
    object DEFINIERE:  TokenTyp()
    object RÜCKGABE: TokenTyp()
    object ZURÜCK: TokenTyp()
    object WENN: TokenTyp()
    object DANN: TokenTyp()
    object SONST: TokenTyp()
    object FÜR: TokenTyp()
    object ALS: TokenTyp()
    object VON: TokenTyp()
    object BIS: TokenTyp()
    object DEN: TokenTyp()
    data class JEDE(val geschlecht: Geschlecht): TokenTyp()
    data class GESCHLECHT(val geschlecht: Geschlecht): TokenTyp()
    data class ZUWEISUNG(val anzahl: Anzahl): TokenTyp()
    data class ARTIKEL(val bestimmt: Boolean, val geschlecht: Geschlecht, val anderesGeschlecht: Geschlecht? = null): TokenTyp() // ein, eine, der, die, das

    //Symbole
    object OFFENE_KLAMMER: TokenTyp()
    object GESCHLOSSENE_KLAMMER: TokenTyp()
    object KOMMA: TokenTyp()
    object PUNKT: TokenTyp()
    object DOPPELPUNKT: TokenTyp()
    object TRENNER: TokenTyp() // Semikolon
    object NEUE_ZEILE: TokenTyp()
    object PIPE: TokenTyp()
    object EOF: TokenTyp()
    data class OPERATOR(val operator: Operator): TokenTyp()

    // Identifier
    data class VERB(val name: String): TokenTyp()
    data class NOMEN(val name: String): TokenTyp()

    // Literale
    data class BOOLEAN(val boolean: Boolean): TokenTyp() // richtig | falsch
    data class ZAHL(val zahl: Double): TokenTyp()
    data class ZEICHENFOLGE(val zeichenfolge: String): TokenTyp()

    object UNDEFINIERT: TokenTyp()
}


private val ZEICHEN_MAPPING = mapOf<Char, TokenTyp>(
        '(' to TokenTyp.OFFENE_KLAMMER,
        ')' to TokenTyp.GESCHLOSSENE_KLAMMER,
        ',' to TokenTyp.KOMMA,
        '.' to TokenTyp.PUNKT,
        ':' to TokenTyp.DOPPELPUNKT,
        '!' to TokenTyp.OPERATOR(Operator.NEGATION),
        ';' to TokenTyp.TRENNER,
        '+' to TokenTyp.OPERATOR(Operator.PLUS),
        '-' to TokenTyp.OPERATOR(Operator.MINUS),
        '*' to TokenTyp.OPERATOR(Operator.MAL),
        '/' to TokenTyp.OPERATOR(Operator.GETEILT),
        '^' to TokenTyp.OPERATOR(Operator.HOCH),
        '=' to TokenTyp.ZUWEISUNG(Anzahl.BEIDES),
        '>' to TokenTyp.OPERATOR(Operator.GRÖßER),
        '<' to TokenTyp.OPERATOR(Operator.KLEINER),
        '&' to TokenTyp.UNDEFINIERT,
        '|' to TokenTyp.UNDEFINIERT
)

private val WORT_MAPPING = mapOf<String, TokenTyp>(
        "mit" to TokenTyp.MIT,
        "definiere" to TokenTyp.DEFINIERE,
        "Rückgabe" to TokenTyp.RÜCKGABE,
        "zurück" to TokenTyp.ZURÜCK,
        "wenn" to TokenTyp.WENN,
        "dann" to TokenTyp.DANN,
        "sonst" to TokenTyp.SONST,
        "als" to TokenTyp.ALS,
        "ist" to TokenTyp.ZUWEISUNG(Anzahl.SINGULAR),
        "sind" to TokenTyp.ZUWEISUNG(Anzahl.PLURAL),
        "der" to TokenTyp.ARTIKEL(true, Geschlecht.MÄNNLICH),
        "die" to TokenTyp.ARTIKEL(true, Geschlecht.WEIBLICH),
        "das" to TokenTyp.ARTIKEL(true, Geschlecht.NEUTRAL),
        "den" to TokenTyp.DEN,
        "ein" to TokenTyp.ARTIKEL(false, Geschlecht.MÄNNLICH, Geschlecht.NEUTRAL),
        "eine" to TokenTyp.ARTIKEL(false, Geschlecht.WEIBLICH),
        "gleich" to TokenTyp.OPERATOR(Operator.GLEICH),
        "ungleich" to TokenTyp.OPERATOR(Operator.UNGLEICH),
        "und" to TokenTyp.OPERATOR(Operator.UND),
        "oder" to TokenTyp.OPERATOR(Operator.ODER),
        "kleiner" to TokenTyp.OPERATOR(Operator.KLEINER),
        "größer" to TokenTyp.OPERATOR(Operator.GRÖßER),
        "plus" to TokenTyp.OPERATOR(Operator.PLUS),
        "minus" to TokenTyp.OPERATOR(Operator.MINUS),
        "mal" to TokenTyp.OPERATOR(Operator.MAL),
        "geteilt" to TokenTyp.OPERATOR(Operator.GETEILT),
        "hoch" to TokenTyp.OPERATOR(Operator.HOCH),
        "wahr" to TokenTyp.BOOLEAN(true),
        "falsch" to TokenTyp.BOOLEAN(false),
        "für" to TokenTyp.FÜR,
        "von" to TokenTyp.VON,
        "bis" to TokenTyp.BIS,
        "jede" to TokenTyp.JEDE(Geschlecht.WEIBLICH),
        "jeden" to TokenTyp.JEDE(Geschlecht.MÄNNLICH),
        "jedes" to TokenTyp.JEDE(Geschlecht.NEUTRAL)
)

fun tokeniziere(quellcode: String) : Sequence<Token> = sequence {
    var inMehrZeilenKommentar = false
    for ((zeilenIndex, zeile) in quellcode.lines().map(String::trim).withIndex()) {
        var kannWortLesen = true
        if (zeile == "") {
            continue
        }
        val iterator = Peekable(zeile.iterator())
        while (iterator.peek() != null) {
            val zeichen = iterator.next()!!
            // ignoriere Kommentare
            if (inMehrZeilenKommentar) {
                if (zeichen == '*' && iterator.peek() == '/') {
                    iterator.next()
                    inMehrZeilenKommentar = false
                }
                break
            }
            if (zeichen == '/' && iterator.peek() == '/') {
                iterator.next()
                break
            }
            if (zeichen == '/' && iterator.peek() == '*') {
                iterator.next()
                inMehrZeilenKommentar = true
                break
            }
            if (zeichen == ' ') {
                kannWortLesen = true
                continue
            }
            yieldAll(when {
                ZEICHEN_MAPPING.containsKey(zeichen) -> symbol(iterator, zeichen, zeilenIndex).also { kannWortLesen = true }
                zeichen.isDigit() -> zahl(iterator, zeichen, zeilenIndex).also { kannWortLesen = false }
                zeichen == '"' -> zeichenfolge(iterator, zeilenIndex).also { kannWortLesen = false }
                kannWortLesen -> wort(iterator, zeichen, zeilenIndex).also { kannWortLesen = false }
                else -> throw Exception("Unerwartetes Zeichen $zeichen")
            })
        }

        yield(Token(TokenTyp.NEUE_ZEILE, "\\n", zeilenIndex to iterator.index, zeilenIndex to iterator.index + 1))
    }
    while (true) {
        yield(Token(TokenTyp.EOF, "EOF", -1 to -1, -1 to -1))
    }
}

private fun symbol(iterator: Peekable<Char>, erstesZeichen: Char, zeilenIndex: Int) : Sequence<Token> = sequence {
    val startPos = zeilenIndex to iterator.index - 1
    var symbolString = erstesZeichen.toString()
    val tokenTyp = (when (erstesZeichen) {
        '>', '<', '!' -> {
            if (iterator.peek() == '=') {
                symbolString += iterator.next()
                when (erstesZeichen) {
                    '>' -> TokenTyp.OPERATOR(Operator.GRÖßER_GLEICH)
                    '<' -> TokenTyp.OPERATOR(Operator.KLEINER_GLEICH)
                    '!' -> TokenTyp.OPERATOR(Operator.UNGLEICH)
                    else -> throw Exception("Dieser Fall wird nie ausgeführt")
                }
            } else {
                when (val token = ZEICHEN_MAPPING.getValue(erstesZeichen)) {
                    TokenTyp.UNDEFINIERT -> throw Exception("Ungültiges Zeichen $erstesZeichen")
                    else -> token
                }
            }
        }
        else -> when {
            erstesZeichen == '&' && iterator.peek() == '&' -> TokenTyp.OPERATOR(Operator.UND).also { symbolString += iterator.next()}
            erstesZeichen == '|' && iterator.peek() == '|' -> TokenTyp.OPERATOR(Operator.ODER).also { symbolString += iterator.next() }
            else -> when (val token = ZEICHEN_MAPPING.getValue(erstesZeichen)) {
                TokenTyp.UNDEFINIERT -> throw Exception("Ungültiges Zeichen $erstesZeichen")
                else -> token
            }
        }
    })
    val endPos = zeilenIndex to iterator.index
    yield(Token(tokenTyp, symbolString, startPos, endPos))
}

private val ZAHLEN_PATTERN = """(0|[1-9]\d?\d?(\.\d{3})+|[1-9]\d*)(\,\d+)?""".toRegex()

private fun zahl(iterator: Peekable<Char>, ersteZiffer: Char, zeilenIndex: Int): Sequence<Token> = sequence {
    val startPos = zeilenIndex to iterator.index - 1
    var zahlenString = ersteZiffer.toString()
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
    if (!zahlenString.matches(ZAHLEN_PATTERN)) {
        throw Exception("Ungültige Zahl")
    }
    val endPos = zeilenIndex to iterator.index
    val zahl = zahlenString.replace(".", "").replace(',', '.').toDouble()
    yield(Token(TokenTyp.ZAHL(zahl), zahlenString, startPos, endPos))
}

private fun zeichenfolge(iterator: Peekable<Char>, zeilenIndex: Int): Sequence<Token> = sequence {
    val startPos = zeilenIndex to iterator.index - 1
    var zeichenfolge = ""
    while (iterator.peek() != null && iterator.peek() != '"') {
        zeichenfolge += iterator.next()
    }
    if (iterator.next() != '"') {
        throw Exception("Ungeschlossene Zeichenfolge. Erwarte \".")
    }
    val endPos = zeilenIndex to iterator.index
    yield(Token(TokenTyp.ZEICHENFOLGE(zeichenfolge), '"' + zeichenfolge + '"', startPos, endPos))
}

private val NOMEN_PATTERN = """[A-Z]\w*""".toRegex()
private val VERB_PATTERN = """[a-z]\w*[\?!]?""".toRegex()

private fun wort(iterator: Peekable<Char>, zeichen: Char, zeilenIndex: Int) : Sequence<Token> = sequence {
    val firstWordStartPos = zeilenIndex to iterator.index - 1
    val erstesWort = teilWort(iterator, zeichen)
    val firstWordEndPos = zeilenIndex to iterator.index
    var spaceBetweenWords = ""
    when {
        WORT_MAPPING.containsKey(erstesWort) -> when (erstesWort) {
            "größer", "kleiner" -> {
                while (iterator.peek() == ' ') {
                    spaceBetweenWords += iterator.next()
                }
                val nächstesZeichen = iterator.peek()
                val nächstesIstWort = !(ZEICHEN_MAPPING.containsKey(nächstesZeichen) ||
                        nächstesZeichen == '&' ||
                        nächstesZeichen == '|' ||
                        nächstesZeichen == '"' ||
                        nächstesZeichen?.isDigit() == true)
                if (nächstesIstWort) {
                    val nextWordStartPos = zeilenIndex to iterator.index
                    val nächstesWort = teilWort(iterator, iterator.next()!!)
                    val nextWordEndPos = zeilenIndex to iterator.index
                    if (nächstesWort == "gleich") {
                        val tokenTyp = when (erstesWort) {
                            "größer" -> TokenTyp.OPERATOR(Operator.GRÖßER_GLEICH)
                            "kleiner" -> TokenTyp.OPERATOR(Operator.KLEINER_GLEICH)
                            else -> throw Exception("Diser Fall wird nie ausgeführt")
                        }
                        yield(Token(tokenTyp, erstesWort + spaceBetweenWords + nächstesWort, firstWordStartPos, nextWordEndPos))
                    } else {
                        yield(Token(WORT_MAPPING.getValue(erstesWort), erstesWort, firstWordStartPos, firstWordEndPos))
                        val tokenTyp = (WORT_MAPPING.getOrElse(nächstesWort, {
                            when {
                                NOMEN_PATTERN.matches(nächstesWort) -> TokenTyp.NOMEN(nächstesWort)
                                VERB_PATTERN.matches(nächstesWort) -> TokenTyp.VERB(nächstesWort)
                                else -> throw Exception("Ungültiges Wort $nächstesWort")
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
        NOMEN_PATTERN.matches(erstesWort) -> yield(Token(TokenTyp.NOMEN(erstesWort), erstesWort, firstWordStartPos, firstWordEndPos))
        VERB_PATTERN.matches(erstesWort) -> yield(Token(TokenTyp.VERB(erstesWort), erstesWort, firstWordStartPos, firstWordEndPos))
        else -> throw Exception("Ungültiges Wort $erstesWort")
    }
}

private fun teilWort(iterator: Peekable<Char>, erstesZeichen: Char): String {
    var wort = erstesZeichen.toString()
    while (iterator.peek() != null) {
        var nächstesZeiches = iterator.peek()!!
        if (nächstesZeiches == ' ' ||
            (nächstesZeiches != '!' && nächstesZeiches != '?' && ZEICHEN_MAPPING.containsKey(nächstesZeiches))) {
            break
        }
        wort += iterator.next()!!
    }
    return wort
}



fun main() {

    fun outputTokenTypes(code: String) {
        tokeniziere(code).takeWhile { token -> token.typ != TokenTyp.EOF }.forEach { println(it) }
    }

    val code = """
        // Fizzbuzz in GermanScript

        für jede Zahl von 1 bis 100.000:
            eine Ausgabe ist ""
            
            wenn Zahl rest 3 gleich 0:
                Ausgabe ist Ausgabe + "Fizz"
            sonst wenn Zahl rest 5 gleich 0:
                Ausgabe ist Ausgabe + "Buzz".

            drucke wenn (Ausgabe leer?) dann Zahl sonst Ausgabe.
            

        /*
        Fizzbuzz in Javascript
        for (let i = 1; i <= 100; i++) {
            let output = ""
            if (i % 3 === 0)
                output += "Fizz"
            if (i % 5 === 0)
                output += "Buzz"
            console.log(output || i)
        }
        */
    """.trimIndent()

    val test = """
        A größer gleich "String" und B kleiner Hallo oder C ungleich 3 && D || E ungleich "Hallo"
    """.trimIndent()

    outputTokenTypes(test)
}