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

class Token(val typ: TokenTyp, wert: String, anfang: Pair<Int, Int>, ende: Pair<Int, Int>)

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
        '<' to TokenTyp.OPERATOR(Operator.KLEINER)
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

fun tokeniziere(quellcode: String) : Sequence<TokenTyp> = sequence {
    var inMehrZeilenKommentar = false
    var amEndeEinesKommentars = false
    var letzterTokenTyp: TokenTyp? = null
    for ((zeilenIndex, zeile) in quellcode.lines().map(String::trim).withIndex()) {
        amEndeEinesKommentars = false
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
                    amEndeEinesKommentars = true
                    inMehrZeilenKommentar = false
                }
                break
            }
            if (zeichen == '/' && iterator.peek() == '/') {
                iterator.next()
                amEndeEinesKommentars = true
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
            when {
                ZEICHEN_MAPPING.containsKey(zeichen) -> symbol(iterator, zeichen).also { kannWortLesen = true }
                zeichen == '|' && iterator.peek() == '|' -> TokenTyp.OPERATOR(Operator.ODER).also { iterator.next() }
                zeichen == '&' && iterator.peek() == '&' -> TokenTyp.OPERATOR(Operator.UND).also { iterator.next() }
                zeichen.isDigit() -> zahl(iterator, zeichen).also { kannWortLesen = false }
                zeichen == '"' -> zeichenfolge(iterator).also { kannWortLesen = false }
                kannWortLesen -> wort(iterator, zeichen).also { kannWortLesen = false }
                else -> throw Exception("Unerwartetes Zeichen $zeichen")
            }.let { yield(it); letzterTokenTyp = it }
        }

        if (!amEndeEinesKommentars && letzterTokenTyp !== TokenTyp.NEUE_ZEILE)
                TokenTyp.NEUE_ZEILE.let { yield(it); letzterTokenTyp = it }
    }
    while (true) {
        yield(TokenTyp.EOF)
    }
}


private fun symbol(iterator: Peekable<Char>, erstesZeichen: Char) : TokenTyp {
    return when (erstesZeichen) {
        '>', '<', '!' -> {
            if (iterator.peek() == '=') {
                iterator.next()
                when (erstesZeichen) {
                    '>' -> TokenTyp.OPERATOR(Operator.GRÖßER_GLEICH)
                    '<' -> TokenTyp.OPERATOR(Operator.KLEINER_GLEICH)
                    '!' -> TokenTyp.OPERATOR(Operator.UNGLEICH)
                    else -> throw Exception("Dieser FAll wird nie ausgeführt")
                }
            } else {
                ZEICHEN_MAPPING.getValue(erstesZeichen)
            }
        }
        else -> ZEICHEN_MAPPING.getValue(erstesZeichen)
    }
}

private val ZAHLEN_PATTERN = """(0|[1-9]\d?\d?(\.\d{3})+|[1-9]\d*)(\,\d+)?""".toRegex()

private fun zahl(iterator: Peekable<Char>, ersteZiffer: Char): TokenTyp {
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
    val zahl = zahlenString.replace(".", "").replace(',', '.').toDouble()
    return TokenTyp.ZAHL(zahl)
}

private fun zeichenfolge(iterator: Peekable<Char>): TokenTyp {
    var zeichenfolge = ""
    while (iterator.peek() != null && iterator.peek() != '"') {
        zeichenfolge += iterator.next()
    }
    if (iterator.next() != '"') {
        throw Exception("Ungeschlossene Zeichenfolge. Erwarte \".")
    }
    return TokenTyp.ZEICHENFOLGE(zeichenfolge)
}

private val NOMEN_PATTERN = """[A-Z]\w*""".toRegex()
private val VERB_PATTERN = """[a-z]\w*[\?!]?""".toRegex()

private fun wort(iterator: Peekable<Char>, zeichen: Char) : TokenTyp {
    return teilWort(iterator, zeichen).let { wort ->
        when {
            WORT_MAPPING.containsKey(wort) -> when (wort) {
                "größer", "kleiner" -> {
                    while (iterator.peek() == ' ') iterator.next()
                    val nächstesZeichen = iterator.peek()
                    val nächstesIstWort = !(ZEICHEN_MAPPING.containsKey(nächstesZeichen) ||
                            nächstesZeichen == '&' ||
                            nächstesZeichen == '|' ||
                            nächstesZeichen == '"' ||
                            nächstesZeichen?.isDigit() == true)
                    if (nächstesIstWort && teilWort(iterator, iterator.next()!!) == "gleich") {
                        when (wort) {
                            "größer" -> TokenTyp.OPERATOR(Operator.GRÖßER_GLEICH)
                            "kleiner" -> TokenTyp.OPERATOR(Operator.KLEINER_GLEICH)
                            else -> throw Exception("Diser Fall wird nie ausgeführt")
                        }
                    } else {
                        WORT_MAPPING.getValue(wort)
                    }
                }
                else -> WORT_MAPPING.getValue(wort)
            }
            NOMEN_PATTERN.matches(wort) -> TokenTyp.NOMEN(wort)
            VERB_PATTERN.matches(wort) -> TokenTyp.VERB(wort)
            else -> throw Exception("Ungültiges Wort $wort")
        }
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

    fun outputTokens(code: String) {
        tokeniziere(code).takeWhile { typ -> typ != TokenTyp.EOF }.forEach(::println)
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
        A = "String" und B kleiner gleich 4 oder C ungleich 3 && D || E ungleich "Hallo"
    """.trimIndent()

    outputTokens(test)
}