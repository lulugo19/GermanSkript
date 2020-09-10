package germanskript

import germanskript.util.Peekable
import java.io.File
import java.util.*

enum class Assoziativität {
    LINKS,
    RECHTS,
}

enum class OperatorKlasse(val kasus: Kasus) {
    ARITHMETISCH(Kasus.AKKUSATIV),
    VERGLEICH(Kasus.AKKUSATIV),
    LOGISCH(Kasus.AKKUSATIV),
}

enum class Operator(val bindungsKraft: Int, val assoziativität: Assoziativität, val klasse: OperatorKlasse) {
    ODER(1, Assoziativität.LINKS, OperatorKlasse.LOGISCH),
    UND(2, Assoziativität.LINKS, OperatorKlasse.LOGISCH),
    GLEICH(3, Assoziativität.LINKS, OperatorKlasse.VERGLEICH),
    UNGLEICH(3, Assoziativität.LINKS, OperatorKlasse.VERGLEICH),
    GRÖßER(3, Assoziativität.LINKS, OperatorKlasse.VERGLEICH),
    KLEINER(3, Assoziativität.LINKS, OperatorKlasse.VERGLEICH),
    GRÖSSER_GLEICH(3, Assoziativität.LINKS, OperatorKlasse.VERGLEICH),
    KLEINER_GLEICH(3, Assoziativität.LINKS, OperatorKlasse.VERGLEICH),
    PLUS(4, Assoziativität.LINKS, OperatorKlasse.ARITHMETISCH),
    MINUS(4, Assoziativität.LINKS, OperatorKlasse.ARITHMETISCH),
    MAL(5, Assoziativität.LINKS, OperatorKlasse.ARITHMETISCH),
    GETEILT(5, Assoziativität.LINKS, OperatorKlasse.ARITHMETISCH),
    MODULO(5, Assoziativität.LINKS, OperatorKlasse.ARITHMETISCH),
    HOCH(6, Assoziativität.RECHTS, OperatorKlasse.ARITHMETISCH),

    NEGATION(3, Assoziativität.RECHTS, OperatorKlasse.ARITHMETISCH),
}

// Genus (Geschlecht)
enum class Genus(val anzeigeName: String) {
    MASKULINUM("Maskulinum"),
    FEMININUM("Femininum"),
    NEUTRUM("Neutrum")
}

// Kasus (Fall)
enum class Kasus(val anzeigeName: String) {
    NOMINATIV("Nominativ"),
    GENITIV("Genitiv"),
    DATIV("Dativ"),
    AKKUSATIV("Akkusativ"),
}

// Numerus (Anzahl)
enum class Numerus(val anzeigeName: String, val zuweisung: String) {
    SINGULAR("Singular", "ist"),
    PLURAL("Plural", "sind");

    companion object  {
        val BEIDE: EnumSet<Numerus> get() = EnumSet.of(SINGULAR, PLURAL)
    }
}

data class Token(val typ: TokenTyp, var wert: String, val dateiPfad: String, val anfang: Position, val ende: Position) {
    // um die Ausgabe zu vereinfachen
    // override fun toString(): String = typ.toString()

    @Suppress("UNCHECKED_CAST")
    fun <T: TokenTyp> toTyped() = TypedToken<T>(typ as T, wert, dateiPfad, anfang, ende)

    open class Position(val zeile: Int, val spalte: Int) {
        object Ende: Position(-1, -1)

        override fun toString(): String {
            return "($zeile, $spalte)"
        }
    }

    val position: String = "'$dateiPfad' $anfang"
}

// für den Parser gedacht
data class TypedToken<out T : TokenTyp>(val typ: T, val wert: String, val dateiPfad: String, val anfang: Token.Position, val ende: Token.Position) {
    fun toUntyped()  = Token(typ, wert, dateiPfad, anfang, ende)
    fun<TChange: TokenTyp> changeType(typ: TChange): TypedToken<TChange> = TypedToken(typ, wert, dateiPfad, anfang, ende)
    val position: String = "'$dateiPfad' $anfang"
}

sealed class TokenTyp(val anzeigeName: String) {
    override fun toString(): String = javaClass.simpleName

    object FEHLER: TokenTyp("'Fehler'")
    object HAUPT_PROGRAMM_START: TokenTyp("Programmstart")
    object HAUPT_PROGRAMM_ENDE: TokenTyp("Programmende")

    // Schlüsselwörter
    object DEKLINATION: TokenTyp("'Deklination'")
    data class GENUS(val genus: Genus): TokenTyp("'Genus'")
    object PLURAL: TokenTyp("'Plural'")
    object SINGULAR: TokenTyp("'Singular'")
    object DUDEN: TokenTyp("'Duden'")
    object WENN: TokenTyp("'wenn'")
    object DANN: TokenTyp("'dann'")
    object SONST: TokenTyp("'sonst'")
    object SOLANGE: TokenTyp("'solange'")
    object ALS_GROß: TokenTyp("'Als'")
    object ALS_KLEIN: TokenTyp("'als'")
    object FORTFAHREN: TokenTyp("'fortfahren'")
    object ABBRECHEN: TokenTyp("'abbrechen'")
    object VERB: TokenTyp("'Verb'")
    object NOMEN: TokenTyp("'Nomen'")
    object ADJEKTIV: TokenTyp("'Adjektiv'")
    object ALIAS: TokenTyp("'Alias'")
    object MODUL: TokenTyp("'Modul'")
    object EIGENSCHAFT: TokenTyp("'Eigenschaft'")
    object KONSTANTE: TokenTyp("'Konstante'")
    object IMPLEMENTIERE : TokenTyp("'Implementiere'")
    object AUFZÄHLUNG: TokenTyp("'Aufzählung'")
    object INTERN: TokenTyp("'intern'")
    object SUPER: TokenTyp("'Super'")

    // Artikel und Präpositionen
    data class ZUWEISUNG(val numerus: Numerus): TokenTyp("'ist' oder 'sind'")

    sealed class VORNOMEN(anzeigeName: String): TokenTyp(anzeigeName) {
        sealed class ARTIKEL(anzeigeName: String): VORNOMEN(anzeigeName) {
            object BESTIMMT: ARTIKEL("bestimmter Artikel")
            object UNBESTIMMT: ARTIKEL("unbestimmter Artikel")
        }
        sealed class POSSESSIV_PRONOMEN(pronomen: String): VORNOMEN("Possesivpronomen ('$pronomen')") {
            object MEIN: POSSESSIV_PRONOMEN("Ich")
            object DEIN: POSSESSIV_PRONOMEN("Du")
        }

        sealed class DEMONSTRATIV_PRONOMEN(pronomen: String): VORNOMEN("Demonstrativpronomen ('$pronomen')") {
            object DIESE: DEMONSTRATIV_PRONOMEN("Diese")
            object JENE: DEMONSTRATIV_PRONOMEN("Jene")
        }

        object ETWAS: VORNOMEN("'etwas'")
    }

    sealed class REFLEXIV_PRONOMEN(pronomen: String): TokenTyp("Reflexivpronomen ('$pronomen')") {
        object MICH: REFLEXIV_PRONOMEN("'Ich'")
        object DICH: REFLEXIV_PRONOMEN("'Du'")
    }

    sealed class REFERENZ(anzeigeName: String): TokenTyp(anzeigeName) {
        object ICH: REFERENZ("Ich")
        object DU: REFERENZ("Du")
    }

    data class NEU(val genus: Genus): TokenTyp("'neuer', 'neue' oder 'neues'") {
        companion object {
            fun holeForm(genus: Genus) = when (genus) {
                Genus.MASKULINUM -> "neuer"
                Genus.FEMININUM -> "neue"
                Genus.NEUTRUM -> "neues"
            }
        }
    }

    data class JEDE(val genus: Genus): TokenTyp("'jeder' oder 'jede' oder 'jedes'") {
        companion object {
            fun holeForm(genus: Genus) = when(genus) {
                Genus.MASKULINUM -> "jeder"
                Genus.FEMININUM -> "jede"
                Genus.NEUTRUM -> "jedes"
            }
        }
    }

    //Symbole
    object OFFENE_KLAMMER: TokenTyp("'('")
    object GESCHLOSSENE_KLAMMER: TokenTyp("')'")
    object OFFENE_ECKIGE_KLAMMER: TokenTyp("']'")
    object GESCHLOSSENE_ECKIGE_KLAMMER: TokenTyp("']'")
    object KOMMA: TokenTyp("','")
    object PUNKT: TokenTyp("'.'")
    object AUSRUFEZEICHEN: TokenTyp("'!'")
    object DOPPELPUNKT: TokenTyp("':'")
    object DOPPEL_DOPPELPUNKT: TokenTyp("'::'")
    object SEMIKOLON: TokenTyp("';'") // Semikolon
    object NEUE_ZEILE: TokenTyp("neue Zeile")
    object EOF: TokenTyp("'EOF'")
    data class OPERATOR(val operator: Operator): TokenTyp("Operator")
    // imaginäres germanskript.Token
    object STRING_INTERPOLATION: TokenTyp("String-Interpolation")

    // Identifier
    data class BEZEICHNER_KLEIN(val name: String): TokenTyp("bezeichner")
    data class BEZEICHNER_GROSS(val teilWörter: Array<String>, val symbol: String): TokenTyp("Bezeichner") {
        val istSymbol get() = teilWörter.isEmpty()
        val hatSymbol get() = symbol.isNotEmpty()
        val hauptWort: String? get() = if (teilWörter.isNotEmpty()) teilWörter[teilWörter.size-1] else null

        fun ersetzeHauptWort(wort: String): String {
            return if (istSymbol) {
                return symbol
            } else {
                teilWörter.dropLast(1).joinToString() + wort + symbol
            }
        }
    }

    // Literale
    object NICHTS: TokenTyp("'nichts'")
    data class BOOLEAN(val boolean: Wert.Primitiv.Boolean): TokenTyp("'richtig' oder 'falsch'")
    data class ZAHL(val zahl: Wert.Primitiv.Zahl): TokenTyp("Zahl")
    data class ZEICHENFOLGE(val zeichenfolge: Wert.Primitiv.Zeichenfolge): TokenTyp("Zeichenfolge")

    object UNDEFINIERT: TokenTyp("undefiniert")
}


private val SYMBOL_MAPPING = mapOf<Char, TokenTyp>(
    '(' to TokenTyp.OFFENE_KLAMMER,
    ')' to TokenTyp.GESCHLOSSENE_KLAMMER,
    '[' to TokenTyp.OFFENE_ECKIGE_KLAMMER,
    ']' to TokenTyp.GESCHLOSSENE_ECKIGE_KLAMMER,
    ',' to TokenTyp.KOMMA,
    ';' to TokenTyp.SEMIKOLON,
    '.' to TokenTyp.PUNKT,
    ':' to TokenTyp.DOPPELPUNKT,
    '!' to TokenTyp.AUSRUFEZEICHEN,
    ';' to TokenTyp.SEMIKOLON,
    '+' to TokenTyp.OPERATOR(Operator.PLUS),
    '-' to TokenTyp.OPERATOR(Operator.MINUS),
    '*' to TokenTyp.OPERATOR(Operator.MAL),
    '/' to TokenTyp.OPERATOR(Operator.GETEILT),
    '^' to TokenTyp.OPERATOR(Operator.HOCH),
    '=' to TokenTyp.OPERATOR(Operator.GLEICH),
    '>' to TokenTyp.OPERATOR(Operator.GRÖßER),
    '<' to TokenTyp.OPERATOR(Operator.KLEINER),
    '&' to TokenTyp.OPERATOR(Operator.UND),
    '|' to TokenTyp.OPERATOR(Operator.ODER)
)

private val DOPPEL_SYMBOL_MAPPING = mapOf<String, TokenTyp>(
    "!=" to TokenTyp.OPERATOR(Operator.UNGLEICH),
    ">=" to TokenTyp.OPERATOR(Operator.GRÖSSER_GLEICH),
    "<=" to TokenTyp.OPERATOR(Operator.KLEINER_GLEICH),
    "::" to TokenTyp.DOPPEL_DOPPELPUNKT
)


private val WORT_MAPPING = mapOf<String, TokenTyp>(
    // Schlüsselwörter
    "Deklination" to TokenTyp.DEKLINATION,
    "Singular" to TokenTyp.SINGULAR,
    "Plural" to TokenTyp.PLURAL,
    "Duden" to TokenTyp.DUDEN,
    "Maskulinum" to TokenTyp.GENUS(Genus.MASKULINUM),
    "Femininum" to TokenTyp.GENUS(Genus.FEMININUM),
    "Neutrum" to TokenTyp.GENUS(Genus.NEUTRUM),
    "Nomen" to TokenTyp.NOMEN,
    "Verb" to TokenTyp.VERB,
    "Eigenschaft" to TokenTyp.EIGENSCHAFT,
    "Konstante" to TokenTyp.KONSTANTE,
    "Implementiere" to TokenTyp.IMPLEMENTIERE,
    "intern" to TokenTyp.INTERN,
    "Adjektiv" to TokenTyp.ADJEKTIV,
    "Alias" to TokenTyp.ALIAS,
    "Als" to TokenTyp.ALS_GROß,
    "als" to TokenTyp.ALS_KLEIN,
    "wenn" to TokenTyp.WENN,
    "dann" to TokenTyp.DANN,
    "sonst" to TokenTyp.SONST,
    "solange" to TokenTyp.SOLANGE,
    "fortfahren" to TokenTyp.FORTFAHREN,
    "abbrechen" to TokenTyp.ABBRECHEN,
    "Modul" to TokenTyp.MODUL,
    "Super" to TokenTyp.SUPER,
    // Werte
    "wahr" to TokenTyp.BOOLEAN(Wert.Primitiv.Boolean(true)),
    "falsch" to TokenTyp.BOOLEAN(Wert.Primitiv.Boolean(false)),
    "nichts" to TokenTyp.NICHTS,

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
    "mod" to TokenTyp.OPERATOR(Operator.MODULO),
    "modulo" to TokenTyp.OPERATOR(Operator.MODULO),

    // Spezielle Referenzen
    "Ich" to TokenTyp.REFERENZ.ICH,
    "Du" to TokenTyp.REFERENZ.DU,

    // Vornomen
    // Artikel
    "der" to TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT,
    "die" to TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT,
    "das" to TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT,
    "den" to TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT,
    "des" to TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT,
    "dem" to TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT,

    "ein" to TokenTyp.VORNOMEN.ARTIKEL.UNBESTIMMT,
    "eine" to TokenTyp.VORNOMEN.ARTIKEL.UNBESTIMMT,
    "eines" to TokenTyp.VORNOMEN.ARTIKEL.UNBESTIMMT,
    "einer" to TokenTyp.VORNOMEN.ARTIKEL.UNBESTIMMT,
    "einen" to TokenTyp.VORNOMEN.ARTIKEL.UNBESTIMMT,
    "einem" to TokenTyp.VORNOMEN.ARTIKEL.UNBESTIMMT,
    "einige" to TokenTyp.VORNOMEN.ARTIKEL.UNBESTIMMT,
    "einigen" to TokenTyp.VORNOMEN.ARTIKEL.UNBESTIMMT,
    "einiger" to TokenTyp.VORNOMEN.ARTIKEL.UNBESTIMMT,

    // Possessivpronomen
    "mein" to TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN,
    "meine" to TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN,
    "meines" to TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN,
    "meiner" to TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN,
    "meinem" to TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN,
    "meinen" to TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN,

    "dein" to TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.DEIN,
    "deine" to TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.DEIN,
    "deines" to TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.DEIN,
    "deiner" to TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.DEIN,
    "deinem" to TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.DEIN,
    "deinen" to TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.DEIN,

    // Demonstrativpronomen
    "dieser" to TokenTyp.VORNOMEN.DEMONSTRATIV_PRONOMEN.DIESE,
    "diese" to TokenTyp.VORNOMEN.DEMONSTRATIV_PRONOMEN.DIESE,
    "dieses" to TokenTyp.VORNOMEN.DEMONSTRATIV_PRONOMEN.DIESE,

    "jener" to TokenTyp.VORNOMEN.DEMONSTRATIV_PRONOMEN.JENE,
    "jene" to TokenTyp.VORNOMEN.DEMONSTRATIV_PRONOMEN.JENE,
    "jenes" to TokenTyp.VORNOMEN.DEMONSTRATIV_PRONOMEN.JENE,

    // Reflexivpronomen
    "mich" to TokenTyp.REFLEXIV_PRONOMEN.MICH,
    "mir" to TokenTyp.REFLEXIV_PRONOMEN.MICH,
    "dich" to TokenTyp.REFLEXIV_PRONOMEN.DICH,
    "dir" to TokenTyp.REFLEXIV_PRONOMEN.DICH,

    "jeden" to TokenTyp.JEDE(Genus.MASKULINUM),
    "jede" to TokenTyp.JEDE(Genus.FEMININUM),
    "jedes" to TokenTyp.JEDE(Genus.NEUTRUM),

    // neu
    "neuer" to TokenTyp.NEU(Genus.MASKULINUM),
    "neue" to TokenTyp.NEU(Genus.FEMININUM),
    "neues" to TokenTyp.NEU(Genus.NEUTRUM),

    "etwas" to TokenTyp.VORNOMEN.ETWAS
)

class Lexer(startDatei: File): PipelineKomponente(startDatei) {
    private var iterator: Peekable<Char>? = null
    private var zeilenIndex = 0
    private var inStringInterpolation = false
    private var kannWortLesen = true

    private var currentFile: String = ""
    private val currentTokenPos: Token.Position get() = Token.Position(zeilenIndex, iterator!!.index)
    private val nextTokenPos: Token.Position get() = Token.Position(zeilenIndex, iterator!!.index + 1)
    private val eofToken = Token(TokenTyp.EOF, "EOF", currentFile, Token.Position.Ende, Token.Position.Ende)
    
    private fun next() = iterator!!.next()
    private fun peek(ahead: Int = 0) = iterator!!.peek(ahead)
    private val dateiSchlange = LinkedList<File>()
    private val bearbeiteteDateien = mutableSetOf<String>()

    fun tokeniziere(): Sequence<Token> = sequence {
        dateiSchlange.add(File("./stdbib/stdbib.gm"))
        yield(Token(
            TokenTyp.HAUPT_PROGRAMM_START,
            "Programmstart", startDatei.absolutePath,
            Token.Position(0, 0),
            Token.Position(0, 0)
        ))
        yieldAll(tokeniziereDatei(startDatei))
        yield(Token(
            TokenTyp.HAUPT_PROGRAMM_ENDE,
            "Programmende", startDatei.absolutePath,
            Token.Position.Ende,
            Token.Position.Ende
        ))
        while (dateiSchlange.isNotEmpty()) {
            val nächsteDatei = dateiSchlange.remove()
            yieldAll(tokeniziereDatei(nächsteDatei))
        }
        while (true) {
            yield(eofToken)
        }
    }

    fun importiereDatei(import: AST.Definition.Import) {
        val datei = startDatei.parentFile!!.resolve(File(import.pfad))
        if (!datei.exists()) {
            throw GermanSkriptFehler.ImportFehler.DateiNichtGefunden(import.dateiPfad.toUntyped(), import.pfad)
        }
        // füge Datei nur zur Schlange hinzu, wenn sie noch nicht bearbeitet wurden ist
        if (!bearbeiteteDateien.contains(datei.path)) {
            dateiSchlange.add(datei)
        }
    }

    private fun tokeniziereDatei(datei: File) : Sequence<Token> = sequence {
        currentFile = datei.absolutePath
        bearbeiteteDateien.add(currentFile)
        var inMehrZeilenKommentar = false
        for ((zeilenIndex, zeile) in datei.readLines().map(String::trim).withIndex()) {
            this@Lexer.zeilenIndex = zeilenIndex + 1
            kannWortLesen = true
            if (zeile == "") {
                continue
            }
            iterator = Peekable(zeile.iterator())
            while (peek() != null) {
                val zeichen = peek()!!
                // ignoriere Kommentare
                if (inMehrZeilenKommentar) {
                    if (zeichen == '*' && peek(1) == '/') {
                        next()
                        next()
                        inMehrZeilenKommentar = false
                    }
                    break
                }
                if (zeichen == '/' && peek(1) == '/') {
                    next()
                    next()
                    break
                }
                if (zeichen == '/' && peek(1) == '*') {
                    next()
                    next()
                    inMehrZeilenKommentar = true
                    break
                }
                if (zeichen == ' ') {
                    next()
                    kannWortLesen = true
                    continue
                }
                yieldAll(when {
                    zeichen.isDigit() || zeichen == '-' && peek(1)?.isDigit() == true -> zahl().also { kannWortLesen = false }
                    SYMBOL_MAPPING.containsKey(zeichen) -> symbol().also { kannWortLesen = true }
                    zeichen == '"' -> zeichenfolge(false).also { kannWortLesen = false }
                    kannWortLesen && zeichen.isLetter() -> wort().also { kannWortLesen = false }
                    zeichen == '}'&& inStringInterpolation -> beendeStringInterpolation()
                    else -> throw GermanSkriptFehler.SyntaxFehler.LexerFehler(
                        Token(
                            TokenTyp.FEHLER,
                            zeichen.toString(),
                            currentFile,
                            currentTokenPos,
                            nextTokenPos
                        ),
                        null
                    )
                })
            }

            yield(Token(
                TokenTyp.NEUE_ZEILE,
                "\\n",
                currentFile,
                currentTokenPos,
                nextTokenPos
            ))
        }
    }

    private fun symbol(): Sequence<Token> = sequence {
        val startPos = currentTokenPos
        var symbolString = next()!!.toString()
        val potenziellesDoppelSymbol = symbolString + (peek()?: '\n')
        val tokenTyp = when {
          DOPPEL_SYMBOL_MAPPING.containsKey(potenziellesDoppelSymbol) -> {
              next()
              symbolString = potenziellesDoppelSymbol
              DOPPEL_SYMBOL_MAPPING.getValue(potenziellesDoppelSymbol)
          }
          SYMBOL_MAPPING.containsKey(symbolString[0]) -> {
              SYMBOL_MAPPING.getValue(symbolString[0])
          }
          else -> {
            TokenTyp.UNDEFINIERT
          }
        }
        val endPos = currentTokenPos
        if (tokenTyp is TokenTyp.UNDEFINIERT) {
            val fehlerToken = Token(TokenTyp.FEHLER, symbolString, currentFile, startPos, endPos)
            throw GermanSkriptFehler.SyntaxFehler.LexerFehler(fehlerToken, null)
        }
        yield(Token(tokenTyp, symbolString, currentFile, startPos, endPos))
    }

    private fun zahl(): Sequence<Token> = sequence {
        val startPos = currentTokenPos
        var zahlenString = ""
        if (peek() == '-') {
            zahlenString += next()
        }
        var hinternKomma = false
        while (peek() != null) {
            val zeichen = peek()!!
            if (zeichen.isDigit()) {
                zahlenString += next()
            } else if (!hinternKomma && (zeichen == '.' || zeichen == ',') && peek(1)?.isDigit() == true) {
                hinternKomma = zeichen == ','
                zahlenString += next()
                zahlenString += next()
            } else {
                break
            }
        }

        val endPos = currentTokenPos
        try {
            yield(Token(TokenTyp.ZAHL(Wert.Primitiv.Zahl(zahlenString)), zahlenString, currentFile, startPos, endPos))
        } catch (error: Exception) {
            val fehlerToken = Token(TokenTyp.FEHLER, zahlenString, currentFile, startPos, endPos)
            throw GermanSkriptFehler.SyntaxFehler.LexerFehler(fehlerToken, null)
        }
    }

    private fun zeichenfolge(überspringeAnführungszeichen: Boolean): Sequence<Token> = sequence {
        val startPos = currentTokenPos
        if (!überspringeAnführungszeichen) {
            next() // consume first '"'
        }
        var zeichenfolge = ""
        while (peek() != null && peek() != '"') {
            zeichenfolge += when (peek()) {
                '\\' -> {
                    escapeZeichen()
                }
                '#' -> {
                    if (peek(1) == '{') {
                        yieldAll(starteStringInterpolation(zeichenfolge, startPos))
                        return@sequence
                    } else {
                        next()
                    }
                }
                else -> next()
            }
        }
        if (peek() != '"') {
            throw GermanSkriptFehler.SyntaxFehler.LexerFehler(eofToken, null)
        }
        next()
        val endPos = currentTokenPos
        val token = Token(TokenTyp.ZEICHENFOLGE(Wert.Primitiv.Zeichenfolge(zeichenfolge)),
            '"' + zeichenfolge + '"', currentFile, startPos, endPos)
        yield(token)
    }

    private fun escapeZeichen(): Char {
        val escapeSequenzPos = currentTokenPos
        next()
        return when (val escapeChar = next()) {
            'n' -> '\n'
            'b' -> '\b'
            't' -> '\t'
            'r' -> '\r'
            '"' -> '"'
            '\\' -> '\\'
            else -> {
                val fehlerToken = Token(TokenTyp.FEHLER, "\\$escapeChar", currentFile, escapeSequenzPos, currentTokenPos)
                throw GermanSkriptFehler.SyntaxFehler.UngültigeEscapeSequenz(fehlerToken)
            }
        }
    }

    private fun starteStringInterpolation(zeichenfolge: String, startPosition: Token.Position) = sequence<Token> {
        // String Interpolation
        yield(Token(
            TokenTyp.ZEICHENFOLGE(Wert.Primitiv.Zeichenfolge(zeichenfolge)),
            '"' + zeichenfolge + '"', currentFile, startPosition, currentTokenPos))
        next() // #
        next() // {
        yield(Token(TokenTyp.OPERATOR(Operator.PLUS), "+", currentFile, currentTokenPos, currentTokenPos))
        yield(Token(TokenTyp.STRING_INTERPOLATION, "String-Interpolation", currentFile, currentTokenPos, currentTokenPos))
        yield(Token(TokenTyp.OFFENE_KLAMMER, "(", currentFile, currentTokenPos, currentTokenPos))
        inStringInterpolation = true
        kannWortLesen = true
    }

    private fun beendeStringInterpolation() = sequence<Token> {
        next() // }
        yield(Token(TokenTyp.GESCHLOSSENE_KLAMMER, ")", currentFile, currentTokenPos, currentTokenPos))
        yield(Token(TokenTyp.ALS_KLEIN, "als", currentFile, currentTokenPos, currentTokenPos))
        yield(Token(
            TokenTyp.BEZEICHNER_GROSS(arrayOf("Zeichenfolge"), ""),
            "Zeichenfolge", currentFile, currentTokenPos, currentTokenPos)
        )
        yield(Token(TokenTyp.OPERATOR(Operator.PLUS), "+", currentFile, currentTokenPos, currentTokenPos))
        inStringInterpolation = false
        kannWortLesen = false
        yieldAll(zeichenfolge(true))
    }

    private fun wort(): Sequence<Token> = sequence {
        val firstWordStartPos = currentTokenPos
        val erstesWort = teilWort()
        val firstWordEndPos = currentTokenPos
        var spaceBetweenWords = ""
        when {
            WORT_MAPPING.containsKey(erstesWort) -> when (erstesWort) {
                "größer", "kleiner" -> {
                    while (peek() == ' ') {
                        spaceBetweenWords += next()
                    }
                    val nächstesZeichen = peek()
                    if (nächstesZeichen?.isLetter() == true) {
                        val nextWordStartPos = currentTokenPos
                        val nächstesWort = teilWort()
                        val nextWordEndPos = currentTokenPos
                        if (nächstesWort == "gleich") {
                            val tokenTyp = when (erstesWort) {
                                "größer" -> TokenTyp.OPERATOR(Operator.GRÖSSER_GLEICH)
                                "kleiner" -> TokenTyp.OPERATOR(Operator.KLEINER_GLEICH)
                                else -> throw Exception("Diser Fall wird nie ausgeführt")
                            }
                            yield(Token(tokenTyp, erstesWort + spaceBetweenWords + nächstesWort, currentFile, firstWordStartPos, nextWordEndPos))
                        } else {
                            yield(Token(WORT_MAPPING.getValue(erstesWort), erstesWort, currentFile, firstWordStartPos, firstWordEndPos))
                            val tokenTyp = (WORT_MAPPING.getOrElse(nächstesWort, {
                                when {
                                    nächstesWort.all { zeichen -> zeichen.isLowerCase() } -> TokenTyp.BEZEICHNER_KLEIN(nächstesWort)
                                    else -> großerBezeichner(nächstesWort, nextWordStartPos, nextWordEndPos)
                                }
                            }))
                            val token = Token(tokenTyp, nächstesWort, currentFile, nextWordStartPos, nextWordEndPos)
                            yieldAll(präpositionArtikelVerschmelzung(token))
                        }
                    }
                    else {
                        yield(Token(WORT_MAPPING.getValue(erstesWort), erstesWort, currentFile, firstWordStartPos, firstWordEndPos))
                    }
                }
                else -> yield(Token(WORT_MAPPING.getValue(erstesWort), erstesWort, currentFile, firstWordStartPos, firstWordEndPos))
            }
            erstesWort.all { zeichen -> zeichen.isLowerCase() } -> {
                val token = Token(TokenTyp.BEZEICHNER_KLEIN(erstesWort), erstesWort, currentFile, firstWordStartPos, firstWordEndPos)
                yieldAll(präpositionArtikelVerschmelzung(token))
            }

            else -> yield(
                Token(großerBezeichner(erstesWort, firstWordStartPos, firstWordEndPos),
                    erstesWort, currentFile, firstWordStartPos, firstWordEndPos))
        }
    }

    private fun großerBezeichner(zeichenfolge: String, tokenStart: Token.Position, tokenEnd: Token.Position): TokenTyp.BEZEICHNER_GROSS {
        val teilWörter = mutableListOf<String>()
        var i = 0
        var teilWort = zeichenfolge[i++].toString()
        var symbol = ""
        while (i < zeichenfolge.length) {
            while (i < zeichenfolge.length && zeichenfolge[i].isLowerCase()) {
                teilWort += zeichenfolge[i++]
            }
            when {
              teilWort.length == 1  -> symbol += teilWort
              symbol.isEmpty() -> teilWörter.add(teilWort)
              else -> {
                  val token = Token(TokenTyp.FEHLER, zeichenfolge, currentFile, tokenStart, tokenEnd)
                  throw GermanSkriptFehler.SyntaxFehler.LexerFehler(token, "Ein großer Bezeichner in GermanSkript darf einzelne Großbuchstaben" +
                      " (Symbole) nur am Ende haben.")
              }
            }
            if (i < zeichenfolge.length) {
                teilWort = zeichenfolge[i++].toString()
            }
        }
        if (teilWort.length == 1) {
            symbol += teilWort
        }
        return TokenTyp.BEZEICHNER_GROSS(teilWörter.toTypedArray(), symbol)
    }

    private fun teilWort(): String {
        var wort = next()!!.toString()
        while (peek()?.isLetter() == true) {
            wort += next()!!
        }
        return wort
    }

    private val präpositionArtikelVerschmelzungsMap = mapOf<String, Pair<String, String>>(
        "zum" to Pair("zu" , "dem"),
        "zur" to Pair("zu" , "der"),
        "im" to Pair("in" , "dem"),
        "ins" to Pair("in" , "das"),
        "durchs" to Pair("durch" , "das"),
        "fürs" to Pair("für" , "das"),
        "unterm" to Pair("unter" , "dem"),
        "überm" to Pair("über" , "dem"),
        "hinterm" to Pair("hinter" , "dem")
    )

    private fun präpositionArtikelVerschmelzung(präpToken: Token) = sequence<Token> {
        if (präpositionArtikelVerschmelzungsMap.containsKey(präpToken.wert)) {
            val (präposition, artikel) = präpositionArtikelVerschmelzungsMap.getValue(präpToken.wert)
            präpToken.wert = präposition
            yield(präpToken)
            val artikelToken = Token(TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT, artikel, currentFile, präpToken.anfang, präpToken.ende)
            yield(artikelToken)
        } else {
            yield(präpToken)
        }
    }
}



fun main() {
    Lexer(File("./iterationen/iter_2/code.gm"))
        .tokeniziere().takeWhile { token -> token.typ != TokenTyp.EOF }.forEach { println(it) }
}