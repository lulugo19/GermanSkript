import java.util.*
import kotlin.Exception

enum class Assoziativität {
    LINKS,
    RECHTS,
}

enum class Operator(val bindungsKraft: Int, val assoziativität: Assoziativität) {
    PLUS(4, Assoziativität.LINKS),
    MINUS(4, Assoziativität.LINKS),
    MAL(5, Assoziativität.LINKS),
    GETEILT(5, Assoziativität.LINKS),
    HOCH(6, Assoziativität.RECHTS),
    GLEICH(3, Assoziativität.LINKS),
    UND(2, Assoziativität.LINKS),
    ODER(1, Assoziativität.LINKS),
    KLEINER(3, Assoziativität.LINKS),
    GRÖßER(3, Assoziativität.LINKS),
    KLEINER_GLEICH(3, Assoziativität.LINKS),
    GRÖßER_GLEICH(3, Assoziativität.LINKS),
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
    Akkusativ,
    Dativ,
    Genitiv,
}

// Numerus (Anzahl)
enum class Numerus {
    SINGULAR,
    PLURAL,
    BEIDE,
}

data class Form(val bestimmt: Boolean, val genus: Genus?, val kasus: Kasus, val numerus: Numerus)

data class Token(val typ: TokenTyp, val wert: String, val anfang: Pair<Int, Int>, val ende: Pair<Int, Int>) {
    // um die Ausgabe zu vereinfachen
    override fun toString(): String = typ.toString()
}

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
    object SOLANGE: TokenTyp()
    object FÜR: TokenTyp()
    object IN: TokenTyp()
    object ALS: TokenTyp()
    object VON: TokenTyp()
    object BIS: TokenTyp()
    object PLURAL: TokenTyp()
    object DEN: TokenTyp()
    object FORTFAHREN: TokenTyp()
    object ABBRECHEN: TokenTyp()
    object ALIAS: TokenTyp()
    data class JEDE(val genus: Genus): TokenTyp()
    data class GESCHLECHT(val genus: Genus): TokenTyp()
    data class ZUWEISUNG(val anzahl: Numerus): TokenTyp()
    data class ARTIKEL(val formen: List<Form>): TokenTyp() // ein, eine, der, die, das
    //Symbole
    object OFFENE_KLAMMER: TokenTyp()
    object GESCHLOSSENE_KLAMMER: TokenTyp()
    object KOMMA: TokenTyp()
    object PUNKT: TokenTyp()
    object DOPPELPUNKT: TokenTyp()
    object TRENNER: TokenTyp() // Semikolon
    object NEUE_ZEILE: TokenTyp()
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
        ';' to TokenTyp.TRENNER,
        '.' to TokenTyp.PUNKT,
        ':' to TokenTyp.DOPPELPUNKT,
        '!' to TokenTyp.OPERATOR(Operator.NEGATION),
        ';' to TokenTyp.TRENNER,
        '+' to TokenTyp.OPERATOR(Operator.PLUS),
        '-' to TokenTyp.OPERATOR(Operator.MINUS),
        '*' to TokenTyp.OPERATOR(Operator.MAL),
        '/' to TokenTyp.OPERATOR(Operator.GETEILT),
        '^' to TokenTyp.OPERATOR(Operator.HOCH),
        '=' to TokenTyp.ZUWEISUNG(Numerus.BEIDE),
        '>' to TokenTyp.OPERATOR(Operator.GRÖßER),
        '<' to TokenTyp.OPERATOR(Operator.KLEINER),
        '&' to TokenTyp.UNDEFINIERT,
        '|' to TokenTyp.UNDEFINIERT
)

private val WORT_MAPPING = mapOf<String, TokenTyp>(
        "mit" to TokenTyp.MIT,
        "definiere" to TokenTyp.DEFINIERE,
        "alias" to TokenTyp.ALIAS,
        "Rückgabe" to TokenTyp.RÜCKGABE,
        "zurück" to TokenTyp.ZURÜCK,
        "wenn" to TokenTyp.WENN,
        "dann" to TokenTyp.DANN,
        "sonst" to TokenTyp.SONST,
        "solange" to TokenTyp.SOLANGE,
        "fortfahren" to TokenTyp.FORTFAHREN,
        "abbrechen" to TokenTyp.ABBRECHEN,
        "als" to TokenTyp.ALS,
        "ist" to TokenTyp.ZUWEISUNG(Numerus.SINGULAR),
        "sind" to TokenTyp.ZUWEISUNG(Numerus.PLURAL),
        "der" to TokenTyp.ARTIKEL(listOf(
                Form(true, Genus.MASKULINUM, Kasus.Nominativ, Numerus.SINGULAR),
                Form(true, Genus.FEMININUM, Kasus.Genitiv, Numerus.SINGULAR),
                Form(true,null, Kasus.Genitiv, Numerus.PLURAL),
                Form(false, null, Kasus.Genitiv, Numerus.PLURAL)
        )),
        "die" to TokenTyp.ARTIKEL(listOf(
                Form(true, Genus.FEMININUM, Kasus.Nominativ, Numerus.SINGULAR),
                Form(true, Genus.FEMININUM, Kasus.Nominativ, Numerus.PLURAL),
                Form(true, Genus.FEMININUM, Kasus.Akkusativ, Numerus.SINGULAR),
                Form(true, null, Kasus.Genitiv, Numerus.PLURAL)
        )),
        "das" to TokenTyp.ARTIKEL(listOf(
                Form(true, Genus.NEUTRUM, Kasus.Nominativ, Numerus.SINGULAR),
                Form(true, Genus.NEUTRUM, Kasus.Akkusativ, Numerus.SINGULAR)
        )),
        "den" to TokenTyp.ARTIKEL(listOf(
                Form(true, Genus.MASKULINUM, Kasus.Nominativ, Numerus.SINGULAR)
        )),
        "ein" to TokenTyp.ARTIKEL(listOf(
                Form(false, Genus.MASKULINUM, Kasus.Nominativ, Numerus.SINGULAR),
                Form(false, Genus.NEUTRUM, Kasus.Nominativ, Numerus.SINGULAR)
        )),
        "eine" to TokenTyp.ARTIKEL(listOf(
                Form(false, Genus.FEMININUM, Kasus.Nominativ, Numerus.SINGULAR)
        ) ),
        "eines" to TokenTyp.ARTIKEL(listOf(
                Form(false, Genus.MASKULINUM, Kasus.Genitiv, Numerus.SINGULAR),
                Form(false, Genus.NEUTRUM, Kasus.Genitiv, Numerus.SINGULAR)
        )),
        "einer" to TokenTyp.ARTIKEL(listOf(
                Form(false, Genus.FEMININUM, Kasus.Genitiv, Numerus.SINGULAR)
        )),
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
        "wahr" to TokenTyp.BOOLEAN(true),
        "falsch" to TokenTyp.BOOLEAN(false),
        "für" to TokenTyp.FÜR,
        "in" to TokenTyp.IN,
        "von" to TokenTyp.VON,
        "bis" to TokenTyp.BIS,
        "jede" to TokenTyp.JEDE(Genus.FEMININUM),
        "jeden" to TokenTyp.JEDE(Genus.MASKULINUM),
        "jedes" to TokenTyp.JEDE(Genus.NEUTRUM),
        "Plural" to TokenTyp.PLURAL
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
        val nächstesZeiches = iterator.peek()!!
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