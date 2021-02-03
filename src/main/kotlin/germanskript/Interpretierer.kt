package germanskript
import germanskript.intern.*
import java.io.File
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.*
import kotlin.random.Random

typealias WerfeFehler = (String, String, Token) -> Objekt

class Interpretierer(startDatei: File): PipelineKomponente(startDatei), IInterpretierer {

  private val codeGenerator = CodeGenerator(startDatei)
  private val typPrüfer = codeGenerator.typPrüfer

  private var aufrufStapel = AufrufStapel()
  private val flags = EnumSet.noneOf(Flag::class.java)
  private var geworfenerFehler: Objekt? = null
  private var geworfenerFehlerToken: Token? = null
  private lateinit var rückgabeWert: Objekt
  private val klassenDefinitionen = HashMap<String, Pair<AST.Definition.Typdefinition.Klasse, IM_AST.Definition.Klasse>>()

  private val umgebung: Umgebung get() = aufrufStapel.top().umgebung
  private lateinit var interpretInjection: InterpretInjection

  companion object {
    val preloadedKlassenDefinitionen = arrayOf("Fehler", "KonvertierungsFehler", "SchlüsselNichtGefundenFehler", "IndexFehler")
  }

  object Konstanten {
    const val CALL_STACK_OUTPUT_LIMIT = 50
  }

  // region Klassen
  private enum class Flag {
    SCHLEIFE_ABBRECHEN,
    SCHLEIFE_FORTFAHREN,
    ZURÜCK,
    FEHLER_GEWORFEN,
  }

  private class Bereich {
    val variablen: HashMap<String, Objekt> = HashMap()
  }

  class Umgebung() {
    private val bereiche = Stack<Bereich>()

    fun leseVariable(varName: String): Objekt {
      return bereiche.findLast { bereich -> bereich.variablen.containsKey(varName) }!!.variablen[varName]!!
    }

    fun schreibeVariable(varName: String, wert: Objekt, überschreibe: Boolean) {
      val bereich = if (überschreibe) {
        bereiche.findLast { it.variablen.containsKey(varName) } ?: bereiche.peek()!!
      } else bereiche.peek()!!
      bereich.variablen[varName] = wert
    }

    fun pushBereich() {
      bereiche.push(Bereich())
    }

    fun popBereich() {
      bereiche.pop()
    }

    override fun toString(): String {
      return bereiche.map { it.variablen.entries }.flatten().joinToString("\n") {
        "${it.key} -> ${it.value}"
      }
    }
  }

  class AufrufStapelElement(val funktionsAufruf: IM_AST.Satz.Ausdruck.IAufruf, val objekt: Objekt?, val umgebung: Umgebung)

  class AufrufStapel {
    val stapel = Stack<AufrufStapelElement>()

    fun top(): AufrufStapelElement = stapel.peek()
    fun push(funktionsAufruf: IM_AST.Satz.Ausdruck.IAufruf, neueUmgebung: Umgebung, aufrufObjekt: Objekt? = null) {
      stapel.push(AufrufStapelElement(funktionsAufruf, aufrufObjekt, neueUmgebung))
    }

    fun pop(): AufrufStapelElement = stapel.pop()

    override fun toString(): String {
      if (stapel.isEmpty()) {
        return ""
      }
      return "Aufrufstapel:\n"+ stapel.drop(1).reversed().joinToString(
          "\n",
          "\t",
          "",
          Konstanten.CALL_STACK_OUTPUT_LIMIT,
          "...",
          ::aufrufStapelElementToString
      )
    }

    private fun aufrufStapelElementToString(element: AufrufStapelElement): String {
      val aufruf = element.funktionsAufruf
      var zeichenfolge = "'${aufruf.name}' in ${aufruf.token.position}"
      if (element.objekt is germanskript.alte_pipeline.intern.Objekt) {
        val klassenName = element.objekt.typ.definition.name.hauptWort
        zeichenfolge = "'für $klassenName: ${aufruf.name}' in ${aufruf.token.position}"
      }

      return zeichenfolge
    }
  }


  class InterpretInjection(
      val aufrufStapel: AufrufStapel,
      val werfeFehler: WerfeFehler
  ) {
    val umgebung: Umgebung get() = aufrufStapel.stapel.peek()!!.umgebung
  }
  // endregion

  override fun interpretiere() {
    val programm = codeGenerator.generiere()
    initKlassenDefinitionen()
    interpretInjection = InterpretInjection(aufrufStapel, ::werfeFehler)
    try {
      interpretiereFunktionsAufruf(programm)
      if (flags.contains(Flag.FEHLER_GEWORFEN)) {
        werfeLaufZeitFehler()
      }
    }
    catch (fehler: Throwable) {
      when (fehler) {
        // Ein StackOverflow wird als Laufzeitfehler in Germanskript gehandelt
        is StackOverflowError -> throw GermanSkriptFehler.LaufzeitFehler(
            aufrufStapel.top().funktionsAufruf.token,
            aufrufStapel.toString(),
            "Stack Overflow")
        // andere Fehler sollten nicht auftreten
        else -> {
          System.err.println(aufrufStapel.toString())
          throw fehler
        }
      }
    }
  }

  private fun initKlassenDefinitionen() {
    for (klassenString in preloadedKlassenDefinitionen) {
      val definition = typPrüfer.typisierer.definierer.holeTypDefinition(klassenString, null)
          as AST.Definition.Typdefinition.Klasse

      klassenDefinitionen[klassenString] = Pair(definition, codeGenerator.klassen[definition]!!)
    }
  }

  private fun werfeFehler(fehlerMeldung: String, fehlerKlassenName: String, token: Token): Objekt {
    val (astKlassenDefinition, immKlassenDefinition) = klassenDefinitionen.getValue(fehlerKlassenName)
    geworfenerFehler = Objekt.SkriptObjekt(immKlassenDefinition, Typ.Compound.Klasse(astKlassenDefinition, emptyList()))
    geworfenerFehler!!.setzeEigenschaft("FehlerMeldung", Zeichenfolge(fehlerMeldung))
    geworfenerFehlerToken = token
    flags.add(Flag.FEHLER_GEWORFEN)
    return Niemals
  }

  private fun werfeLaufZeitFehler() {
    flags.remove(Flag.FEHLER_GEWORFEN)
    val alsZeichenfolge = geworfenerFehler!!.klasse.methoden.getValue("als Zeichenfolge")

    val aufruf = object : IM_AST.Satz.Ausdruck.IAufruf {
      override val token: Token = geworfenerFehler!!.typ.definition.konvertierungen.getValue("Zeichenfolge").typ.name.bezeichnerToken
      override val name = "als Zeichenfolge"
      override val argumente: List<IM_AST.Satz.Ausdruck> = emptyList()
    }

    val zeichenfolge = interpretiereAufruf(
        Umgebung(),
        aufruf,
        alsZeichenfolge,
        geworfenerFehler
    ) as Zeichenfolge

    throw GermanSkriptFehler.UnbehandelterFehler(geworfenerFehlerToken!!, aufrufStapel.toString(), zeichenfolge.zeichenfolge)
  }

  // region Sätze

  private fun interpretiereVariablenDeklaration(variablenDeklaration: IM_AST.Satz.VariablenDeklaration): Objekt {
    umgebung.schreibeVariable(
        variablenDeklaration.name,
        interpretiereAusdruck(variablenDeklaration.wert), variablenDeklaration.überschreibe
    )
    return if (fehlerGeworfen()) Niemals else Nichts
  }

  private fun interpretiereSetzeEigenschaft(setzeEigenschaft: IM_AST.Satz.SetzeEigenschaft): Objekt {
    val objekt = interpretiereAusdruck(setzeEigenschaft.objekt).also { if (fehlerGeworfen()) return Niemals}
    objekt.setzeEigenschaft(
        setzeEigenschaft.name,
        interpretiereAusdruck(setzeEigenschaft.ausdruck).also { if (fehlerGeworfen()) return Niemals}
    )
    return Nichts
  }

  private fun interpretiereSolangeSchleife(schleife: IM_AST.Satz.SolangeSchleife): Objekt {
    while (!flags.contains(Flag.SCHLEIFE_ABBRECHEN) &&
        !flags.contains(Flag.ZURÜCK) &&
        (interpretiereAusdruck(schleife.bedingungsTerm.bedingung).also { if (fehlerGeworfen()) return Niemals}
            as germanskript.intern.Boolean).boolean
    ) {
      flags.remove(Flag.SCHLEIFE_FORTFAHREN)
      interpretiereBereich(schleife.bedingungsTerm.bereich, true)
    }
    flags.remove(Flag.SCHLEIFE_ABBRECHEN)
    return Nichts
  }

  private fun interpretiereIntern(intern: IM_AST.Satz.Intern): Objekt {
    val aufruf = aufrufStapel.top().funktionsAufruf
    return when (val objekt = aufrufStapel.top().objekt) {
      is Objekt -> objekt.rufeMethodeAuf(aufruf, interpretInjection)
      else -> interneFunktionen.getValue(aufruf.name)()
    }.also { rückgabeWert = it }
  }

  private fun interpretiereZurückgabe(zurückgabe: IM_AST.Satz.Zurückgabe): Objekt {
    return interpretiereAusdruck(zurückgabe.ausdruck).also {
      flags.add(Flag.ZURÜCK)
      rückgabeWert = it
    }
  }
  // endregion

  // region Ausdrücke
  private fun interpretiereAusdruck(ausdruck: IM_AST.Satz.Ausdruck): Objekt {
    if (fehlerGeworfen()) {
      return Niemals
    }
    return when (ausdruck) {
      is IM_AST.Satz.Ausdruck.LogischesUnd -> interpretiereLogischesUnd(ausdruck)
      is IM_AST.Satz.Ausdruck.LogischesOder -> interpretiereLogischesOder(ausdruck)
      is IM_AST.Satz.Ausdruck.LogischesNicht -> interpretiereLogischesNicht(ausdruck)
      is IM_AST.Satz.Ausdruck.Vergleich -> interpretiereVergleich(ausdruck)
      is IM_AST.Satz.Ausdruck.Bereich -> interpretiereBereich(ausdruck, true)
      is IM_AST.Satz.Ausdruck.Bedingung -> interpretiereBedingung(ausdruck)
      is IM_AST.Satz.Ausdruck.FunktionsAufruf -> interpretiereFunktionsAufruf(ausdruck)
      is IM_AST.Satz.Ausdruck.MethodenAufruf -> interpretiereMethodenAufruf(ausdruck)
      is IM_AST.Satz.Ausdruck.Variable -> umgebung.leseVariable(ausdruck.name)
      is IM_AST.Satz.Ausdruck.Eigenschaft -> interpretiereEigenschaft(ausdruck)
      is IM_AST.Satz.Ausdruck.ObjektInstanziierung -> interpretiereObjektInstanziierung(ausdruck)
      is IM_AST.Satz.Ausdruck.Konstante.Zahl -> Zahl(ausdruck.zahl)
      is IM_AST.Satz.Ausdruck.Konstante.Zeichenfolge -> Zeichenfolge(ausdruck.zeichenfolge)
      is IM_AST.Satz.Ausdruck.Konstante.Boolean -> Boolean(ausdruck.boolean)
      IM_AST.Satz.Ausdruck.Konstante.Nichts -> Nichts
      is IM_AST.Satz.Ausdruck.VersucheFange -> interpretiereVersucheFange(ausdruck)
      is IM_AST.Satz.Ausdruck.Werfe -> interpretiereWerfe(ausdruck)
      is IM_AST.Satz.Ausdruck.TypÜberprüfung -> interpretiereTypÜberprüfung(ausdruck)
      is IM_AST.Satz.Ausdruck.TypCast -> interpretiereTypCast(ausdruck)
    }.let { if (fehlerGeworfen()) {Niemals} else it }
  }

  private fun interpretiereObjektInstanziierung(instanziierung: IM_AST.Satz.Ausdruck.ObjektInstanziierung): Objekt {
    // TODO: Wie löse ich das Problem, dass hier interne Objekte instanziiert werden können eleganter?
    return when(instanziierung.klasse.name) {
      "Liste" -> Liste(instanziierung.typ, mutableListOf())
      "HashMap" -> germanskript.intern.HashMap(instanziierung.typ)
      "HashSet" -> germanskript.intern.HashSet(instanziierung.typ)
      "Datei" -> Datei()
      else ->
        if (instanziierung.objektArt != IM_AST.Satz.Ausdruck.ObjektArt.Klasse)
          Objekt.ClosureObjekt(instanziierung.klasse, instanziierung.typ, umgebung, instanziierung.objektArt)
        else Objekt.SkriptObjekt(instanziierung.klasse, instanziierung.typ)
    }
  }

  private fun interpretiereEigenschaft(eigenschaft: IM_AST.Satz.Ausdruck.Eigenschaft): Objekt {
    val objekt = interpretiereAusdruck(eigenschaft.objekt)
    return objekt.holeEigenschaft(eigenschaft.name)
  }

  private fun interpretiereBedingungsTerm(term: IM_AST.Satz.BedingungsTerm): Objekt? {
    return if (!fehlerGeworfen() && (interpretiereAusdruck(term.bedingung) as germanskript.intern.Boolean).boolean) {
      interpretiereBereich(term.bereich, true)
    } else {
      null
    }
  }

  private fun interpretiereBedingung(bedingungsSatz: IM_AST.Satz.Ausdruck.Bedingung): Objekt {
    val inBedingung = bedingungsSatz.bedingungen.any { bedingung ->
      interpretiereBedingungsTerm(bedingung)?.also { return it } != null
    }

    return if (!inBedingung && bedingungsSatz.sonst != null ) {
      interpretiereBereich(bedingungsSatz.sonst, true)
    } else {
      return Nichts
    }
  }

  private fun interpretiereVersucheFange(versucheFange: IM_AST.Satz.Ausdruck.VersucheFange): Objekt {
    var rückgabe = interpretiereBereich(versucheFange.versuchBereich, true)
    if (flags.contains(Flag.FEHLER_GEWORFEN)) {
      val fehlerObjekt = geworfenerFehler!!
      val fehlerKlasse = fehlerObjekt.typ
      val fange = versucheFange.fange.find { fange ->
        typPrüfer.typIstTyp(fehlerKlasse, fange.typ)
      }
      if (fange != null) {
        flags.remove(Flag.FEHLER_GEWORFEN)
        umgebung.pushBereich()
        umgebung.schreibeVariable(fange.param, fehlerObjekt, false)
        rückgabe = interpretiereBereich(fange.bereich, false)
        umgebung.popBereich()
      }
    }
    if (versucheFange.schlussendlich != null) {
      val fehlerGeworfen = flags.contains(Flag.FEHLER_GEWORFEN)
      flags.remove(Flag.FEHLER_GEWORFEN)
      rückgabe = interpretiereBereich(versucheFange.schlussendlich, true)
      if (fehlerGeworfen) {
        flags.add(Flag.FEHLER_GEWORFEN)
      }
    }
    return rückgabe
  }

  private fun interpretiereWerfe(werfe: IM_AST.Satz.Ausdruck.Werfe): Objekt {
    geworfenerFehler = interpretiereAusdruck(werfe.ausdruck)
    geworfenerFehlerToken = werfe.werfe.toUntyped()
    flags.add(Flag.FEHLER_GEWORFEN)
    return Niemals
  }

  private fun interpretiereTypÜberprüfung(typÜberprüfung: IM_AST.Satz.Ausdruck.TypÜberprüfung): Objekt {
    val typ = interpretiereAusdruck(typÜberprüfung.ausdruck).typ
    val istTyp = typPrüfer.typIstTyp(typ, typÜberprüfung.typ)
    return Boolean(istTyp)
  }

  private fun interpretiereTypCast(typCast: IM_AST.Satz.Ausdruck.TypCast): Objekt {
    val wert = interpretiereAusdruck(typCast.ausdruck)
    return if (typPrüfer.typIstTyp(wert.typ, typCast.zielTyp)) {
      wert
    } else {
      val fehlerMeldung = "Ungültige Konvertierung!\n" +
          "Die Klasse '${wert.klasse.name}' kann nicht nach '${typCast.zielTyp}' konvertiert werden."
      werfeFehler(fehlerMeldung, "KonvertierungsFehler", typCast.token)
    }
  }

  private fun interpretiereLogischesUnd(logischesUnd: IM_AST.Satz.Ausdruck.LogischesUnd): Objekt {
      return Boolean(
          (interpretiereAusdruck(logischesUnd.links) as germanskript.intern.Boolean).boolean &&
              (interpretiereAusdruck(logischesUnd.rechts) as germanskript.intern.Boolean).boolean
      )
  }

  private fun interpretiereLogischesOder(logischesOder: IM_AST.Satz.Ausdruck.LogischesOder): Objekt {
    return Boolean(
        (interpretiereAusdruck(logischesOder.links) as germanskript.intern.Boolean).boolean ||
            (interpretiereAusdruck(logischesOder.rechts) as germanskript.intern.Boolean).boolean
    )
  }

  private fun interpretiereLogischesNicht(logischesNicht: IM_AST.Satz.Ausdruck.LogischesNicht): Objekt {
    return Boolean(!(interpretiereAusdruck(logischesNicht.ausdruck) as germanskript.intern.Boolean).boolean)
  }

  private fun interpretiereVergleich(vergleich: IM_AST.Satz.Ausdruck.Vergleich): Objekt {
    val vergleichsWert = (interpretiereMethodenAufruf(vergleich.vergleichsMethode) as Zahl).zahl
    return when (vergleich.operator) {
      IM_AST.Satz.Ausdruck.VergleichsOperator.KLEINER -> Boolean(vergleichsWert < 0)
      IM_AST.Satz.Ausdruck.VergleichsOperator.GRÖSSER -> Boolean(vergleichsWert > 0)
      IM_AST.Satz.Ausdruck.VergleichsOperator.KLEINER_GLEICH -> Boolean(vergleichsWert <= 0)
      IM_AST.Satz.Ausdruck.VergleichsOperator.GRÖSSER_GLEICH -> Boolean(vergleichsWert >= 0)
    }
  }

  private fun sollteAbbrechen(): Boolean {
    return flags.contains(Flag.SCHLEIFE_FORTFAHREN) ||
        flags.contains(Flag.SCHLEIFE_ABBRECHEN) ||
        flags.contains(Flag.ZURÜCK)
  }

  private fun fehlerGeworfen(): Boolean = flags.contains(Flag.FEHLER_GEWORFEN)

  private fun interpretiereBereich(bereich: IM_AST.Satz.Ausdruck.Bereich, neuerBereich: Boolean): Objekt {
    if (neuerBereich) {
      umgebung.pushBereich()
    }
    var rückgabe: Objekt = Nichts
    for (satz in bereich.sätze) {
      if (sollteAbbrechen()) {
        return Nichts
      }
      if (fehlerGeworfen()) {
        return Nichts
      }
      rückgabe = when (satz) {
        is IM_AST.Satz.Intern -> interpretiereIntern(satz)
        IM_AST.Satz.Fortfahren -> flags.add(Flag.SCHLEIFE_FORTFAHREN).let { Nichts }
        IM_AST.Satz.Abbrechen -> flags.add(Flag.SCHLEIFE_ABBRECHEN).let { Nichts }
        is IM_AST.Satz.Zurückgabe -> interpretiereZurückgabe(satz)
        is IM_AST.Satz.VariablenDeklaration -> interpretiereVariablenDeklaration(satz)
        is IM_AST.Satz.SetzeEigenschaft -> interpretiereSetzeEigenschaft(satz)
        is IM_AST.Satz.SolangeSchleife -> interpretiereSolangeSchleife(satz)
        is IM_AST.Satz.Ausdruck -> interpretiereAusdruck(satz)
      }
    }
    if (neuerBereich) {
      umgebung.popBereich()
    }
    return rückgabe
  }

  private fun interpretiereAufruf(
      aufrufUmgebung: Umgebung,
      funktionsAufruf: IM_AST.Satz.Ausdruck.IAufruf,
      funktionsDefinition: IM_AST.Definition.Funktion,
      aufrufObjekt: Objekt?
  ): Objekt {
    aufrufUmgebung.pushBereich()
    val parameter = funktionsDefinition.parameter
    val argumente = funktionsAufruf.argumente
    for (index in parameter.indices) {
      aufrufUmgebung.schreibeVariable(
          parameter[index],
          interpretiereAusdruck(argumente[index]),
          false
      )
      if (fehlerGeworfen()) {
        return Niemals
      }
    }

    if (aufrufObjekt != null) {
      aufrufUmgebung.schreibeVariable(CodeGenerator.SELBST_VAR_NAME, aufrufObjekt, false)
    }

    aufrufStapel.push(funktionsAufruf, aufrufUmgebung, aufrufObjekt)
    rückgabeWert = Nichts
    return interpretiereBereich(funktionsDefinition.körper!!, false).let {
      aufrufStapel.pop()
      if (aufrufObjekt != null && aufrufObjekt is Objekt.ClosureObjekt
          && aufrufObjekt.objektArt == IM_AST.Satz.Ausdruck.ObjektArt.Lambda) it else rückgabeWert.also {
        flags.remove(Flag.ZURÜCK)
      }
    }
  }

  private fun interpretiereFunktionsAufruf(funktionsAufruf: IM_AST.Satz.Ausdruck.FunktionsAufruf): Objekt {
    return interpretiereAufruf(Umgebung(), funktionsAufruf, funktionsAufruf.funktion, null)
  }

  private fun interpretiereMethodenAufruf(methodenAufruf: IM_AST.Satz.Ausdruck.MethodenAufruf): Objekt {
    val objekt = interpretiereAusdruck(methodenAufruf.objekt).also { if (fehlerGeworfen()) return Niemals }
    val aufrufUmgebung = if (objekt is Objekt.ClosureObjekt) objekt.umgebung else Umgebung()
    val funktionsDefinition = methodenAufruf.funktion ?: objekt.klasse.methoden.getValue(methodenAufruf.name)
    return interpretiereAufruf(aufrufUmgebung, methodenAufruf, funktionsDefinition, objekt)
  }
  // endregion

  // region interne Funktionen
  private val interneFunktionen = mapOf<String, () -> (Objekt)>(
      "schreibe die Zeichenfolge" to {
        val zeichenfolge = umgebung.leseVariable("Zeichenfolge") as Zeichenfolge
        print(zeichenfolge)
        Nichts
      },

      "schreibe die Zeile" to {
        val zeile = umgebung.leseVariable("Zeile") as Zeichenfolge
        println(zeile)
        Nichts
      },

      "schreibe die Zahl" to {
        val zahl = umgebung.leseVariable("Zahl") as Zahl
        println(zahl)
        Nichts
      },

      "schreibe die Meldung" to {
        val zeichenfolge = umgebung.leseVariable("FehlerMeldung") as Zahl
        System.err.println(zeichenfolge)
        Nichts
      },

      "erstelle aus dem Code" to {
        val zeichenfolge = umgebung.leseVariable("Code") as Zahl
        Zeichenfolge(zeichenfolge.toInt().toChar().toString())
      },

      "lese" to {
        Zeichenfolge(readLine()!!)
      },

      "runde die Zahl" to {
        val zahl = umgebung.leseVariable("Zahl") as Zahl
        Zahl(round(zahl.zahl))
      },

      "runde die Zahl ab" to {
        val zahl = umgebung.leseVariable("Zahl") as Zahl
        Zahl(floor(zahl.zahl))
      },

      "runde die Zahl auf" to {
        val zahl = umgebung.leseVariable("Zahl") as Zahl
        Zahl(ceil(zahl.zahl))
      },

      "sinus von der Zahl" to {
        val zahl = umgebung.leseVariable("Zahl") as Zahl
        Zahl(sin(zahl.zahl))
      },

      "cosinus von der Zahl" to {
        val zahl = umgebung.leseVariable("Zahl") as Zahl
        Zahl(cos(zahl.zahl))
      },

      "tangens von der Zahl" to {
        val zahl = umgebung.leseVariable("Zahl") as Zahl
        Zahl(tan(zahl.zahl))
      },

      "wurzel von der Zahl" to {
        val zahl = umgebung.leseVariable("Zahl") as Zahl
        Zahl(sqrt(zahl.zahl))
      },

      "randomisiere" to {
        Zahl(Random.nextDouble())
      },

      "randomisiere zwischen dem Minimum, dem Maximum" to {
        val min = umgebung.leseVariable("Minimum") as Zahl
        val max = umgebung.leseVariable("Maximum") as Zahl
        Zahl(Random.nextDouble(min.zahl, max.zahl))
      }
  )
  // endregion
}