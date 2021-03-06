package germanskript

import germanskript.util.Peekable
import kotlinx.coroutines.yield
import java.io.File
import java.lang.Integer.max
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.*

enum class Assoziativität {
    LINKS,
    RECHTS,
}

enum class OperatorKlasse(val kasus: Kasus) {
    ARITHMETISCH(Kasus.AKKUSATIV),
    VERGLEICH_ALS(Kasus.NOMINATIV),
    VERGLEICH_GLEICH(Kasus.DATIV),
    LOGISCH(Kasus.NOMINATIV),
}

enum class Operator(val bindungsKraft: Int, val assoziativität: Assoziativität, val klasse: OperatorKlasse, val methodenName: String? = null) {
    ODER(1, Assoziativität.LINKS, OperatorKlasse.LOGISCH),
    UND(2, Assoziativität.LINKS, OperatorKlasse.LOGISCH),
    GLEICH(3, Assoziativität.LINKS, OperatorKlasse.VERGLEICH_GLEICH, "gleicht dem Objekt"),
    UNGLEICH(3, Assoziativität.LINKS, OperatorKlasse.VERGLEICH_GLEICH, "gleicht dem Objekt"),
    GRÖßER(3, Assoziativität.LINKS, OperatorKlasse.VERGLEICH_ALS, "vergleiche mich mit dem Typ"),
    KLEINER(3, Assoziativität.LINKS, OperatorKlasse.VERGLEICH_ALS, "vergleiche mich mit dem Typ"),
    GRÖSSER_GLEICH(3, Assoziativität.LINKS, OperatorKlasse.VERGLEICH_GLEICH, "vergleiche mich mit dem Typ"),
    KLEINER_GLEICH(3, Assoziativität.LINKS, OperatorKlasse.VERGLEICH_GLEICH, "vergleiche mich mit dem Typ"),
    PLUS(4, Assoziativität.LINKS, OperatorKlasse.ARITHMETISCH, "addiere mich mit dem Operanden"),
    MINUS(4, Assoziativität.LINKS, OperatorKlasse.ARITHMETISCH, "subtrahiere mich mit dem Operanden"),
    MAL(5, Assoziativität.LINKS, OperatorKlasse.ARITHMETISCH, "multipliziere mich mit dem Operanden"),
    GETEILT(5, Assoziativität.LINKS, OperatorKlasse.ARITHMETISCH, "dividiere mich mit dem Operanden"),
    MODULO(5, Assoziativität.LINKS, OperatorKlasse.ARITHMETISCH, "moduliere mich mit dem Operanden"),
    HOCH(6, Assoziativität.RECHTS, OperatorKlasse.ARITHMETISCH, "potenziere mich mit dem Operanden"),
    NEGATION(3, Assoziativität.RECHTS, OperatorKlasse.ARITHMETISCH, "negiere mich"),
}

// Genus (Geschlecht)
enum class Genus(val anzeigeName: String) {
    MASKULINUM("Maskulinum"),
    FEMININUM("Femininum"),
    NEUTRUM("Neutrum");

    override fun toString(): String {
        return anzeigeName
    }
}

// Kasus (Fall)
enum class Kasus(val anzeigeName: String) {
    NOMINATIV("Nominativ"),
    GENITIV("Genitiv"),
    DATIV("Dativ"),
    AKKUSATIV("Akkusativ");

    override fun toString(): String {
        return anzeigeName
    }
}

// Numerus (Anzahl)
enum class Numerus(val anzeigeName: String, val zuweisung: String) {
    SINGULAR("Singular", "ist"),
    PLURAL("Plural", "sind");

    override fun toString(): String {
        return anzeigeName
    }

    companion object  {
        val BEIDE: EnumSet<Numerus> get() = EnumSet.of(SINGULAR, PLURAL)
    }
}

data class Token(val typ: TokenTyp, var wert: String, val dateiPfad: String, val anfang: Position) {
    // um die Ausgabe zu vereinfachen
    // override fun toString(): String = typ.toString()

    @Suppress("UNCHECKED_CAST")
    fun <T: TokenTyp> toTyped() = TypedToken<T>(typ as T, wert, dateiPfad, anfang)

    open class Position(val zeile: Int, val spalte: Int) {
        object Ende: Position(-1, -1)

        override fun toString(): String {
            return "($zeile, $spalte)"
        }
    }

    val position: String = "'$dateiPfad' $anfang"

    val ende: Position = Position(anfang.zeile, anfang.spalte + wert.length)
}

// für den Parser gedacht
data class TypedToken<out T : TokenTyp>(val typ: T, val wert: String, val dateiPfad: String, val anfang: Token.Position) {
    fun toUntyped()  = Token(typ, wert, dateiPfad, anfang)
    fun<TChange: TokenTyp> changeType(typ: TChange): TypedToken<TChange> = TypedToken(typ, wert, dateiPfad, anfang)
    val position: String = "'$dateiPfad' $anfang"
    val ende: Token.Position = Token.Position(anfang.zeile, anfang.spalte + wert.length)

    companion object {
        fun<T: TokenTyp> imaginäresToken(typ: T, wert: String)
            = TypedToken(typ, wert, "", Token.Position.Ende)
    }
}

sealed class TokenTyp(val anzeigeName: String) {
    override fun toString(): String = anzeigeName

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
    object ZURÜCK: TokenTyp("'zurück'")
    object VERSUCHE: TokenTyp("'versuche'")
    object FANGE: TokenTyp("'fange'")
    object SCHLUSSENDLICH: TokenTyp("'schlussendlich'")
    object WERFE: TokenTyp("'werfe'")
    object BIS: TokenTyp("'bis'")
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
            object DIESE: DEMONSTRATIV_PRONOMEN("diese")
            object JENE: DEMONSTRATIV_PRONOMEN("jene")
        }

        object ETWAS: VORNOMEN("'etwas'")

        object JEDE: VORNOMEN("'jeder' oder 'jede' oder 'jedes'")
    }

    sealed class REFLEXIV_PRONOMEN(val pronomen: String, val kasus: EnumSet<Kasus>, val numerus: Numerus): TokenTyp("Reflexivpronomen ('$pronomen')") {
        sealed class ERSTE_FORM(pronomen: String, kasus: EnumSet<Kasus>, numerus: Numerus): REFLEXIV_PRONOMEN(pronomen, kasus, numerus) {
            object MIR: ERSTE_FORM("mir", EnumSet.of(Kasus.DATIV), Numerus.SINGULAR)
            object MICH: ERSTE_FORM("mich", EnumSet.of(Kasus.AKKUSATIV), Numerus.SINGULAR)
            object UNS: ERSTE_FORM("uns", EnumSet.of(Kasus.AKKUSATIV, Kasus.DATIV), Numerus.PLURAL)
        }

        sealed class ZWEITE_FORM(pronomen: String, kasus: EnumSet<Kasus>, numerus: Numerus): REFLEXIV_PRONOMEN(pronomen, kasus, numerus) {
            object DIR: ZWEITE_FORM("dir", EnumSet.of(Kasus.DATIV), Numerus.SINGULAR)
            object DICH: ZWEITE_FORM("dich", EnumSet.of(Kasus.AKKUSATIV), Numerus.SINGULAR)
            object EUCH: ZWEITE_FORM("euch", EnumSet.of(Kasus.AKKUSATIV, Kasus.DATIV), Numerus.PLURAL)
        }
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
    // Logisches nicht
    object NICHT: TokenTyp("'nicht'")
    // Identifier
    data class BEZEICHNER_KLEIN(val name: String): TokenTyp("bezeichner")
    data class BEZEICHNER_GROSS(val teilWörter: Array<String>, val symbol: String = "", val adjektiv: TypedToken<BEZEICHNER_KLEIN>? = null): TokenTyp("Bezeichner") {
        val istSymbol get() = teilWörter.isEmpty()
        val hatSymbol get() = symbol.isNotEmpty()
        val hatAdjektiv get() = adjektiv != null
        private val istUnsichtbarerBezeichner get() = teilWörter[0][0] == '_' || (istSymbol && symbol[0] == '_')
        val hauptWort: String? get() = if (teilWörter.isNotEmpty()) teilWörter[teilWörter.size-1].removePrefix("_") else null

        fun ersetzeHauptWort(wort: String, mitSymbol: Boolean, maxAnzahlTeilWörter: Int = Int.MAX_VALUE): String {
            return (if (istUnsichtbarerBezeichner) "_" else "") +
            if (istSymbol) {
                return symbol
            } else {
                val dropAnzahl = max(0, teilWörter.size - maxAnzahlTeilWörter)
                teilWörter.drop(dropAnzahl).dropLast(1).joinToString("") + wort + (if (mitSymbol) symbol else "")
            }
        }
    }

    // Literale
    object NICHTS: TokenTyp("'nichts'")
    data class BOOLEAN(val boolean: Boolean): TokenTyp("'richtig' oder 'falsch'")
    data class ZAHL(val zahl: Double): TokenTyp("Zahl")
    data class ZEICHENFOLGE(val zeichenfolge: String): TokenTyp("Zeichenfolge")

    object UNDEFINIERT: TokenTyp("undefiniert")
}

fun reflexivPronomenZweiteFromZuErsteForm(pronomen: TokenTyp.REFLEXIV_PRONOMEN.ZWEITE_FORM):
    TokenTyp.REFLEXIV_PRONOMEN.ERSTE_FORM {
    return when (pronomen) {
        TokenTyp.REFLEXIV_PRONOMEN.ZWEITE_FORM.DICH -> TokenTyp.REFLEXIV_PRONOMEN.ERSTE_FORM.MICH
        TokenTyp.REFLEXIV_PRONOMEN.ZWEITE_FORM.DIR -> TokenTyp.REFLEXIV_PRONOMEN.ERSTE_FORM.MIR
        TokenTyp.REFLEXIV_PRONOMEN.ZWEITE_FORM.EUCH -> TokenTyp.REFLEXIV_PRONOMEN.ERSTE_FORM.UNS
    }
}

private val reflexivPronomenFormen = arrayOf(
    arrayOf(TokenTyp.REFLEXIV_PRONOMEN.ERSTE_FORM.MIR, TokenTyp.REFLEXIV_PRONOMEN.ERSTE_FORM.MICH, TokenTyp.REFLEXIV_PRONOMEN.ERSTE_FORM.UNS),
    arrayOf(TokenTyp.REFLEXIV_PRONOMEN.ZWEITE_FORM.DIR, TokenTyp.REFLEXIV_PRONOMEN.ZWEITE_FORM.DICH, TokenTyp.REFLEXIV_PRONOMEN.ZWEITE_FORM.EUCH)
)

fun holeReflexivPronomenForm(zweiteForm: Boolean, kasus: Kasus, numerus: Numerus): TokenTyp.REFLEXIV_PRONOMEN {
    val formIndex = if (zweiteForm) 1 else 0
    val spaltenIndex = if (numerus == Numerus.SINGULAR) kasus.ordinal-2 else 2
    return reflexivPronomenFormen[formIndex][spaltenIndex]
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
    "bis" to TokenTyp.BIS,
    "fortfahren" to TokenTyp.FORTFAHREN,
    "abbrechen" to TokenTyp.ABBRECHEN,
    "zurück" to TokenTyp.ZURÜCK,
    "versuche" to TokenTyp.VERSUCHE,
    "fange" to TokenTyp.FANGE,
    "schlussendlich" to TokenTyp.SCHLUSSENDLICH,
    "werfe" to TokenTyp.WERFE,
    "Modul" to TokenTyp.MODUL,
    "Super" to TokenTyp.SUPER,
    // Werte
    "wahr" to TokenTyp.BOOLEAN(true),
    "falsch" to TokenTyp.BOOLEAN(false),
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
    "nicht" to TokenTyp.NICHT,

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
    "mir" to TokenTyp.REFLEXIV_PRONOMEN.ERSTE_FORM.MIR,
    "mich" to TokenTyp.REFLEXIV_PRONOMEN.ERSTE_FORM.MICH,
    "uns" to TokenTyp.REFLEXIV_PRONOMEN.ERSTE_FORM.UNS,

    "dir" to TokenTyp.REFLEXIV_PRONOMEN.ZWEITE_FORM.DIR,
    "dich" to TokenTyp.REFLEXIV_PRONOMEN.ZWEITE_FORM.DICH,
    "euch" to TokenTyp.REFLEXIV_PRONOMEN.ZWEITE_FORM.EUCH,

    "jeden" to TokenTyp.VORNOMEN.JEDE,
    "jede" to TokenTyp.VORNOMEN.JEDE,
    "jedes" to TokenTyp.VORNOMEN.JEDE,

    // neu
    "neuer" to TokenTyp.NEU(Genus.MASKULINUM),
    "neue" to TokenTyp.NEU(Genus.FEMININUM),
    "neues" to TokenTyp.NEU(Genus.NEUTRUM),

    "etwas" to TokenTyp.VORNOMEN.ETWAS
)

class Lexer(startDatei: File): PipelineKomponente(startDatei) {
    companion object {
        const val STANDARD_BIB_PATH = "./stdbib"

        val zahlenFormat = DecimalFormat()
        init {
            with (zahlenFormat) {
                decimalFormatSymbols.groupingSeparator = '.'
                decimalFormatSymbols.decimalSeparator = ','
                roundingMode = RoundingMode.HALF_UP
                maximumFractionDigits = 20
            }
        }
    }

    private var iterator: Peekable<Char>? = null
    private var zeilenIndex = 0
    private var inStringInterpolation = false
    private var kannWortLesen = true

    private var currentFile: String = ""
    private val currentTokenPos: Token.Position get() = Token.Position(zeilenIndex, iterator!!.index)
    private val eofToken = Token(TokenTyp.EOF, "EOF", currentFile, Token.Position.Ende)
    
    private fun next() = iterator!!.next()
    private fun peek(ahead: Int = 0) = iterator!!.peek(ahead)
    private val dateiSchlange = LinkedList<File>()
    private val bearbeiteteDateien = mutableSetOf<String>()

    fun tokeniziere(): Sequence<Token> = sequence {
        yield(Token(
            TokenTyp.HAUPT_PROGRAMM_START,
            "Programmstart", startDatei.absolutePath,
            Token.Position(0, 0)
        ))
        yieldAll(importiereStandardBibliothek())
        yieldAll(tokeniziereDatei(startDatei))
        yield(Token(
            TokenTyp.HAUPT_PROGRAMM_ENDE,
            "Programmende", startDatei.absolutePath,
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
        if (!bearbeiteteDateien.contains(datei.absolutePath)) {
            dateiSchlange.add(datei)
        }
    }

    private fun importiereStandardBibliothek() = sequence {
        for (file in File(STANDARD_BIB_PATH).walkBottomUp().filter { it.isFile }) {
            yieldAll(tokeniziereDatei(file))
        }
    }

    private fun tokeniziereDatei(datei: File) : Sequence<Token> = sequence {
        currentFile = datei.absolutePath
        bearbeiteteDateien.add(currentFile)
        var inMehrZeilenKommentar = false
        for ((zeilenIndex, zeile) in datei.readLines().withIndex()) {
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
                    } else {
                        next()
                    }
                    continue
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
                    continue
                }
                if (zeichen.isWhitespace()) {
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
                    else -> werfeLexerFehlerMitEinemZeichen(zeichen, "Unerwartetes Zeichen '${zeichen}'.")
                })
            }

            yield(Token(
                TokenTyp.NEUE_ZEILE,
                "\\n",
                currentFile,
                currentTokenPos
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
        if (tokenTyp is TokenTyp.UNDEFINIERT) {
            val fehlerToken = Token(TokenTyp.FEHLER, symbolString, currentFile, startPos)
            throw GermanSkriptFehler.SyntaxFehler.LexerFehler(fehlerToken, null)
        }
        yield(Token(tokenTyp, symbolString, currentFile, startPos))
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
        try {
            yield(Token(TokenTyp.ZAHL(zahlenFormat.parse(zahlenString).toDouble()), zahlenString, currentFile, startPos))
        } catch (error: Exception) {
            val fehlerToken = Token(TokenTyp.FEHLER, zahlenString, currentFile, startPos)
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
        val token = Token(TokenTyp.ZEICHENFOLGE(zeichenfolge),
            '"' + zeichenfolge + '"', currentFile, startPos)
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
                val fehlerToken = Token(TokenTyp.FEHLER, "\\$escapeChar", currentFile, escapeSequenzPos)
                throw GermanSkriptFehler.SyntaxFehler.UngültigeEscapeSequenz(fehlerToken)
            }
        }
    }

    private fun starteStringInterpolation(zeichenfolge: String, startPosition: Token.Position) = sequence<Token> {
        // String Interpolation
        yield(Token(
            TokenTyp.ZEICHENFOLGE(zeichenfolge),
            '"' + zeichenfolge + '"', currentFile, startPosition))
        next() // #
        next() // {
        yield(Token(TokenTyp.OPERATOR(Operator.PLUS), "+", currentFile, currentTokenPos))
        yield(Token(TokenTyp.STRING_INTERPOLATION, "String-Interpolation", currentFile, currentTokenPos))
        yield(Token(TokenTyp.OFFENE_KLAMMER, "(", currentFile, currentTokenPos))
        inStringInterpolation = true
        kannWortLesen = true
    }

    private fun beendeStringInterpolation() = sequence<Token> {
        next() // }
        yield(Token(TokenTyp.GESCHLOSSENE_KLAMMER, ")", currentFile, currentTokenPos))
        yield(Token(TokenTyp.ALS_KLEIN, "als", currentFile, currentTokenPos))
        yield(Token(
            TokenTyp.BEZEICHNER_GROSS(arrayOf("Zeichenfolge"), "", null),
            "Zeichenfolge", currentFile, currentTokenPos)
        )
        yield(Token(TokenTyp.OPERATOR(Operator.PLUS), "+", currentFile, currentTokenPos))
        inStringInterpolation = false
        kannWortLesen = false
        yieldAll(zeichenfolge(true))
    }

    private fun wort(): Sequence<Token> = sequence {
        val firstWordStartPos = currentTokenPos
        val erstesWort = teilWort()
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
                        if (nächstesWort == "gleich") {
                            val tokenTyp = when (erstesWort) {
                                "größer" -> TokenTyp.OPERATOR(Operator.GRÖSSER_GLEICH)
                                "kleiner" -> TokenTyp.OPERATOR(Operator.KLEINER_GLEICH)
                                else -> throw Exception("Diser Fall wird nie ausgeführt")
                            }
                            yield(Token(tokenTyp, erstesWort + spaceBetweenWords + nächstesWort, currentFile, firstWordStartPos))
                        } else {
                            yield(Token(WORT_MAPPING.getValue(erstesWort), erstesWort, currentFile, firstWordStartPos))
                            val tokenTyp = (WORT_MAPPING.getOrElse(nächstesWort, {
                                when {
                                    istGroßerBezeichner(erstesWort) -> großerBezeichner(nächstesWort, nextWordStartPos)
                                    else -> TokenTyp.BEZEICHNER_KLEIN(nächstesWort)
                                }
                            }))
                            val token = Token(tokenTyp, nächstesWort, currentFile, nextWordStartPos)
                            if (tokenTyp is TokenTyp.BEZEICHNER_KLEIN) {
                                yieldAll(präpositionArtikelVerschmelzung(token))
                            } else {
                                yield(token)
                            }
                        }
                    }
                    else {
                        yield(Token(WORT_MAPPING.getValue(erstesWort), erstesWort, currentFile, firstWordStartPos))
                    }
                }
                else -> yield(Token(WORT_MAPPING.getValue(erstesWort), erstesWort, currentFile, firstWordStartPos))
            }
            istGroßerBezeichner(erstesWort) -> yield(
                Token(großerBezeichner(erstesWort, firstWordStartPos),
                    erstesWort, currentFile, firstWordStartPos))
            else -> {
                val token = Token(TokenTyp.BEZEICHNER_KLEIN(erstesWort), erstesWort, currentFile, firstWordStartPos)
                yieldAll(präpositionArtikelVerschmelzung(token))
            }
        }
    }

    private fun werfeLexerFehlerMitEinemZeichen(wert: Char, details: String?): Nothing {
        throw GermanSkriptFehler.SyntaxFehler.LexerFehler(
            Token(
                TokenTyp.FEHLER,
                wert.toString(),
                currentFile,
                currentTokenPos
            ),
            details
        )
    }

    private fun istGroßerBezeichner(wort: String): Boolean {
        return wort.any {it.isUpperCase()}
    }

    private fun großerBezeichner(zeichenfolge: String, tokenPos: Token.Position): TokenTyp.BEZEICHNER_GROSS {
        var i = 0
        var adjektivString = ""
        while (i < zeichenfolge.length && zeichenfolge[i].isLowerCase()) {
            adjektivString += zeichenfolge[i++]
        }
        val adjektiv =
            if (adjektivString.isEmpty()) null
            else TypedToken(TokenTyp.BEZEICHNER_KLEIN(adjektivString), adjektivString, currentFile, tokenPos)
        var teilWort = zeichenfolge[i++].toString()
        val teilWörter = mutableListOf<String>()
        var symbol = ""
        while (i < zeichenfolge.length) {
            while (i < zeichenfolge.length && zeichenfolge[i].isLowerCase()) {
                teilWort += zeichenfolge[i++]
            }
            when {
              teilWort.length == 1  -> symbol += teilWort
              symbol.isEmpty() -> teilWörter.add(teilWort)
              else -> {
                  val token = Token(TokenTyp.FEHLER, zeichenfolge, currentFile, tokenPos)
                  throw GermanSkriptFehler.SyntaxFehler.LexerFehler(token, "Ein großer Bezeichner in GermanSkript darf einzelne Großbuchstaben und Ziffern" +
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
        return TokenTyp.BEZEICHNER_GROSS(teilWörter.toTypedArray(), symbol, adjektiv)
    }

    private fun teilWort(): String {
        var wort = next()!!.toString()
        var istGroßerBezeichner = false
        var ziffernAmEnde = false
        while (true) {
            val nächstesZeichen = peek()
            if (nächstesZeichen?.isLetter() != true && nächstesZeichen?.isDigit() != true && nächstesZeichen != '_') {
                break
            }
            if (nächstesZeichen.isDigit()) {
                ziffernAmEnde = true
            }
            if (nächstesZeichen.isUpperCase()) {
                if (wort.contains('_')) {
                    werfeLexerFehlerMitEinemZeichen(nächstesZeichen, "Ein kleiner Bezeichner mit Unterstrich '_' darf keinen Großbuchstaben haben.")
                }
                istGroßerBezeichner = true
            }
            else if (ziffernAmEnde && nächstesZeichen.isLetter()) {
                werfeLexerFehlerMitEinemZeichen(nächstesZeichen, "Nach Ziffern dürfen bei Bezeichnern keine Buchstaben mehr stehen.")
            }
            if (istGroßerBezeichner && nächstesZeichen == '_') {
                werfeLexerFehlerMitEinemZeichen(nächstesZeichen, "Ein großer Bezeichner darf keine Unterstriche '_' haben.")
            }
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
            val artikelToken = Token(TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT, artikel, currentFile, präpToken.anfang)
            yield(artikelToken)
        } else {
            yield(präpToken)
        }
    }
}

fun main() {
    val datei = createTempFile()
    datei.writeText("\"#{der Name} wird morgen #{das Alter + 1} Jahre alt!\"")

    Lexer(datei)
        .tokeniziere().takeWhile { token -> token.typ != TokenTyp.EOF }.forEach { println(it.typ) }
}