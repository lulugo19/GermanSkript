package germanskript.alte_pipeline

import germanskript.*
import germanskript.alte_pipeline.intern.*
import java.io.File
import java.util.*
import kotlin.NoSuchElementException
import kotlin.collections.HashMap
import kotlin.math.*
import kotlin.random.Random

typealias SchnittstellenAufruf = (Objekt, String, argumente: Array<Objekt>) -> Objekt?

// Fehlermeldung, KlassenName, Token
typealias WerfeFehler = (String, String, Token) -> Objekt

class InterpretInjection(
    val aufrufStapel: Interpretierer.AufrufStapel,
    val schnittstellenAufruf: SchnittstellenAufruf,
    val werfeFehler: WerfeFehler
) {
  val umgebung: Umgebung<Objekt> get() = aufrufStapel.top().umgebung
}

class Interpretierer(startDatei: File): PipelineKomponente(startDatei), IInterpretierer {
  val entsüßer = Entsüßer(startDatei)
  val typPrüfer = entsüßer.typPrüfer

  val ast: AST.Programm = entsüßer.ast

  private val flags = EnumSet.noneOf(Flag::class.java)
  private val aufrufStapel = AufrufStapel()
  private lateinit var rückgabeWert: Objekt

  private var geworfenerFehler: Objekt? = null
  private var geworfenerFehlerToken: Token? = null

  private val umgebung: Umgebung<Objekt> get() = aufrufStapel.top().umgebung

  private val klassenDefinitionen = HashMap<String, AST.Definition.Typdefinition.Klasse>()

  private lateinit var interpretInjection: InterpretInjection

  override fun interpretiere() {
    entsüßer.entsüße()
    initKlassenDefinitionen()
    try {
      aufrufStapel.push(ast, Umgebung())
      interpretInjection = InterpretInjection(aufrufStapel, ::durchlaufeInternenSchnittstellenAufruf, ::werfeFehler)
      rückgabeWert = Nichts
      durchlaufeBereich(ast.programm, true)
      if (flags.contains(Flag.FEHLER_GEWORFEN)) {
        werfeLaufZeitFehler()
      }
    } catch (fehler: Throwable) {
      when (fehler) {
        is StackOverflowError -> throw GermanSkriptFehler.LaufzeitFehler(
            aufrufStapel.top().aufruf.token,
            aufrufStapel.toString(),
            "Stack Overflow")
        else -> {
          System.err.println(aufrufStapel.toString())
          throw fehler
        }
      }
    }
  }

  private fun werfeLaufZeitFehler() {
    flags.remove(Flag.FEHLER_GEWORFEN)
    val konvertierungsDefinition = geworfenerFehler!!.klasse.definition.konvertierungen.getValue("Zeichenfolge")

    val aufruf = object : AST.IAufruf {
      override val token: Token = konvertierungsDefinition.typ.name.bezeichnerToken
      override val vollerName = "als Zeichenfolge"
    }

    val zeichenfolge = durchlaufeAufruf(
        aufruf,
        konvertierungsDefinition.definition,
        Umgebung(),
        true, geworfenerFehler!!
    ) as Zeichenfolge

    throw GermanSkriptFehler.UnbehandelterFehler(geworfenerFehlerToken!!, aufrufStapel.toString(), zeichenfolge.zeichenfolge)
  }

  private enum class Flag {
    SCHLEIFE_ABBRECHEN,
    SCHLEIFE_FORTFAHREN,
    ZURÜCK,
    FEHLER_GEWORFEN,
  }

  class AufrufStapelElement(val aufruf: AST.IAufruf, val objekt: Objekt?, val umgebung: Umgebung<Objekt>)

  object Konstanten {
     const val CALL_STACK_OUTPUT_LIMIT = 50
  }

  inner class AufrufStapel {
    val stapel = Stack<AufrufStapelElement>()

    fun top(): AufrufStapelElement = stapel.peek()
    fun push(funktionsAufruf: AST.IAufruf, neueUmgebung: Umgebung<Objekt>, aufrufObjekt: Objekt? = null) {
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
      val aufruf = element.aufruf
      var zeichenfolge = "'${aufruf.vollerName}' in ${aufruf.token.position}"
      if (element.objekt is Objekt) {
        val klassenName = element.objekt.klasse.definition.name.hauptWort
        zeichenfolge = "'für $klassenName: ${aufruf.vollerName}' in ${aufruf.token.position}"
      }

      return zeichenfolge
    }
  }

  private fun initKlassenDefinitionen() {
    for (klassenString in preloadedKlassenDefinitionen) {
      val definition = typPrüfer.typisierer.definierer.holeTypDefinition(klassenString, null)
          as AST.Definition.Typdefinition.Klasse

      klassenDefinitionen[klassenString] = definition
    }
  }

  private fun sollteAbbrechen(): Boolean {
    return flags.contains(Flag.SCHLEIFE_FORTFAHREN) ||
        flags.contains(Flag.SCHLEIFE_ABBRECHEN) ||
        flags.contains(Flag.ZURÜCK)
  }

  private fun sollteStackAufrollen(): Boolean = flags.contains(Flag.FEHLER_GEWORFEN)

  // region Sätze
  private fun durchlaufeBereich(bereich: AST.Satz.Bereich, neuerBereich: Boolean): Objekt {
    if (neuerBereich) {
      umgebung.pushBereich()
    }
    var rückgabe: Objekt = Nichts
    for (satz in bereich.sätze) {
      if (sollteAbbrechen()) {
        return Nichts
      }
      if (sollteStackAufrollen()) {
        return Niemals
      }
      @Suppress("IMPLICIT_CAST_TO_ANY")
      rückgabe = when (satz) {
        is AST.Satz.VariablenDeklaration -> durchlaufeVariablenDeklaration(satz)
        is AST.Satz.IndexZuweisung -> durchlaufeIndexZuweisung(satz)
        is AST.Satz.Bereich -> durchlaufeBereich(satz, true)
        is AST.Satz.Zurückgabe -> durchlaufeZurückgabe(satz)
        is AST.Satz.SolangeSchleife -> durchlaufeSolangeSchleife(satz)
        is AST.Satz.Ausdruck.VersucheFange -> durchlaufeVersucheFange(satz)
        is AST.Satz.Ausdruck.Werfe -> durchlaufeWerfe(satz)
        is AST.Satz.SchleifenKontrolle.Abbrechen -> flags.add(Flag.SCHLEIFE_ABBRECHEN).let { Nichts }
        is AST.Satz.SchleifenKontrolle.Fortfahren -> flags.add(Flag.SCHLEIFE_FORTFAHREN).let { Nichts }
        is AST.Satz.Intern -> durchlaufeIntern(satz)
        is AST.Satz.Ausdruck -> evaluiereAusdruck(satz)
        else -> throw java.lang.Exception("Dieser Fall sollte nie eintreten!")
      }
    }
    if (neuerBereich) {
      umgebung.popBereich()
    }
    return rückgabe
  }

  private fun durchlaufeMethodenBereich(methodenBereich: AST.Satz.Ausdruck.MethodenBereich): Objekt {
    val wert = evaluiereAusdruck(methodenBereich.objekt)
    umgebung.pushBereich(wert)
    return durchlaufeBereich(methodenBereich.bereich, false).also { umgebung.popBereich() }
  }
  
  private fun durchlaufeBedingung(bedingung: AST.Satz.BedingungsTerm): Objekt? {
      return if (!sollteStackAufrollen() && (evaluiereAusdruck(bedingung.bedingung) as germanskript.alte_pipeline.intern.Boolean).boolean) {
        durchlaufeBereich(bedingung.bereich, true)
      } else {
        null
      }
  }

  private fun durchlaufeVariablenDeklaration(deklaration: AST.Satz.VariablenDeklaration): Objekt {
    if (deklaration.istEigenschaftsNeuZuweisung || deklaration.istEigenschaft) {
      // weise Eigenschaft neu zu
      aufrufStapel.top().objekt!!.setzeEigenschaft(deklaration.name.nominativ, evaluiereAusdruck(deklaration.wert))
    }
    else {
      val wert = evaluiereAusdruck(deklaration.wert)
      // Da der Typprüfer schon überprüft ob Variablen überschrieben werden können
      // werden hier die Variablen immer überschrieben
      if (deklaration.name.unveränderlich || deklaration.neu != null) {
        umgebung.schreibeVariable(deklaration.name, wert, false)
      } else {
        umgebung.überschreibeVariable(deklaration.name, wert)
      }
    }
    return Nichts
  }

  private fun durchlaufeIndexZuweisung(zuweisung: AST.Satz.IndexZuweisung): Objekt {
    val indizierbar = evaluiereIndizierbarSingularOderPlural(zuweisung.singular, zuweisung.numerus)
    val index = evaluiereAusdruck(zuweisung.index)
    val wert = evaluiereAusdruck(zuweisung.wert)

    val methode = indizierbar.klasse.definition.methoden.getValue("setze den Index auf den Typ")
    val parameter = methode.signatur.parameter.toList()
    val aufrufUmgebung = Umgebung<Objekt>()
    aufrufUmgebung.pushBereich()
    aufrufUmgebung.schreibeVariable(parameter[0].name.nominativ, index)
    aufrufUmgebung.schreibeVariable(parameter[1].name.nominativ, wert)

    return durchlaufeAufruf(
        object : AST.IAufruf {
          override val token = zuweisung.singular.bezeichnerToken
          override val vollerName = methode.signatur.vollerName
        },
        methode.körper,
        aufrufUmgebung, false,
        indizierbar
    )
  }

  private fun durchlaufeAufruf(
      aufruf: AST.IAufruf,
      bereich: AST.Satz.Bereich,
      umgebung: Umgebung<Objekt>,
      neuerBereich: Boolean,
      objekt: Objekt?
  ): Objekt {
    rückgabeWert = Nichts
    aufrufStapel.push(aufruf, umgebung, objekt)
    return durchlaufeBereich(bereich, neuerBereich).also {
      aufrufStapel.pop()
      val impliziterRückgabeTyp = objekt is Objekt.Lambda
      if (!impliziterRückgabeTyp) {
        flags.remove(Flag.ZURÜCK)
      }
    }
  }

  private fun durchlaufeFunktionsAufruf(funktionsAufruf: AST.Satz.Ausdruck.FunktionsAufruf): Objekt {
    var funktionsUmgebung = Umgebung<Objekt>()
    var objekt: Objekt? = null
    val (körper, parameterNamen) = if (funktionsAufruf.aufrufTyp == FunktionsAufrufTyp.FUNKTIONS_AUFRUF) {
      val definition = funktionsAufruf.funktionsDefinition!!
      Pair(definition.körper, definition.signatur.parameter.map{it.name}.toList())
    } else {
      // dynamisches Binden von Methoden
      objekt = when(funktionsAufruf.aufrufTyp) {
        FunktionsAufrufTyp.METHODEN_SUBJEKT_AUFRUF -> evaluiereAusdruck(funktionsAufruf.subjekt!!)
        FunktionsAufrufTyp.METHODEN_REFLEXIV_AUFRUF -> evaluiereAusdruck(funktionsAufruf.objekt!!.ausdruck)
        FunktionsAufrufTyp.METHODEN_SELBST_AUFRUF -> aufrufStapel.top().objekt!!
        else -> umgebung.holeMethodenBlockObjekt()!!
      }
      if (funktionsAufruf.funktionsDefinition != null) {
        val definition = funktionsAufruf.funktionsDefinition!!
        Pair(definition.körper, definition.signatur.parameterNamen)
      } else {
        val methode = objekt.klasse.definition.methoden.getValue(funktionsAufruf.vollerName!!)
        // funktionsAufruf.vollerName = "für ${objekt.typ.definition.name.nominativ}: ${methode.signatur.vollerName}"
        if (objekt is Objekt.AnonymesSkriptObjekt) {
          funktionsUmgebung = objekt.umgebung
        } else if (objekt is Objekt.Lambda) {
          funktionsUmgebung = objekt.umgebung
        }
        Pair(methode.körper, methode.signatur.parameterNamen)
      }
    }
    funktionsUmgebung.pushBereich()
    val argumente = funktionsAufruf.argumente.toList()
    for (i in parameterNamen.indices) {
      funktionsUmgebung.schreibeVariable(parameterNamen[i], evaluiereAusdruck(argumente[i].ausdruck), false)
    }

    return durchlaufeAufruf(funktionsAufruf, körper, funktionsUmgebung, false, objekt)
        .let { if (objekt is Objekt.Lambda) it else rückgabeWert }
  }

  private fun durchlaufeZurückgabe(zurückgabe: AST.Satz.Zurückgabe): Objekt {
    return evaluiereAusdruck(zurückgabe.ausdruck).also {
      flags.add(Flag.ZURÜCK)
      rückgabeWert = it
    }
  }

  private fun durchlaufeBedingungsSatz(bedingungsSatz: AST.Satz.Ausdruck.Bedingung): Objekt {
    val inBedingung = bedingungsSatz.bedingungen.any { bedingung ->
      durchlaufeBedingung(bedingung)?.also { return it } != null
    }

    return if (!inBedingung && bedingungsSatz.sonst != null ) {
      durchlaufeBereich(bedingungsSatz.sonst!!.bereich, true)
    } else {
      return Nichts
    }
  }

  private fun durchlaufeSolangeSchleife(schleife: AST.Satz.SolangeSchleife): Objekt {
    while (!flags.contains(Flag.SCHLEIFE_ABBRECHEN) && !flags.contains(Flag.ZURÜCK) && (evaluiereAusdruck(schleife.bedingung.bedingung) as germanskript.alte_pipeline.intern.Boolean).boolean) {
      flags.remove(Flag.SCHLEIFE_FORTFAHREN)
      durchlaufeBereich(schleife.bedingung.bereich, true)
    }
    flags.remove(Flag.SCHLEIFE_ABBRECHEN)
    return Nichts
  }

  private fun durchlaufeVersucheFange(versucheFange: AST.Satz.Ausdruck.VersucheFange): Objekt {
    var rückgabe = durchlaufeBereich(versucheFange.bereich, true)
    if (flags.contains(Flag.FEHLER_GEWORFEN)) {
      val fehlerObjekt = geworfenerFehler!!
      val fehlerKlasse = fehlerObjekt.klasse
      val fange = versucheFange.fange.find { fange ->
        typPrüfer.typIstTyp(fehlerKlasse, fange.param.typKnoten.typ!!)
      }
      if (fange != null) {
        flags.remove(Flag.FEHLER_GEWORFEN)
        umgebung.pushBereich()
        umgebung.schreibeVariable(fange.param.name, fehlerObjekt, false)
        rückgabe = durchlaufeBereich(fange.bereich, false)
        umgebung.popBereich()
      }
    }
    if (versucheFange.schlussendlich != null) {
      val fehlerGeworfen = flags.contains(Flag.FEHLER_GEWORFEN)
      flags.remove(Flag.FEHLER_GEWORFEN)
      rückgabe = durchlaufeBereich(versucheFange.schlussendlich, true)
      if (fehlerGeworfen) {
        flags.add(Flag.FEHLER_GEWORFEN)
      }
    }
    return rückgabe
  }

  private fun durchlaufeWerfe(werfe: AST.Satz.Ausdruck.Werfe): Objekt {
    geworfenerFehler = evaluiereAusdruck(werfe.ausdruck)
    geworfenerFehlerToken = werfe.werfe.toUntyped()
    flags.add(Flag.FEHLER_GEWORFEN)
    return Niemals
  }

  private fun durchlaufeIntern(intern: AST.Satz.Intern): Objekt {
    val aufruf = aufrufStapel.top().aufruf
    return when (val objekt = aufrufStapel.top().objekt) {
      is Objekt -> objekt.rufeMethodeAuf(aufruf, interpretInjection)
      else -> interneFunktionen.getValue(aufruf.vollerName!!)()
    }.also { rückgabeWert = it }
  }

  // endregion

  // region Ausdrücke
  private fun evaluiereAusdruck(ausdruck: AST.Satz.Ausdruck): Objekt {
    if (sollteStackAufrollen()) {
      return Niemals
    }
    return when (ausdruck) {
      is AST.Satz.Ausdruck.Zeichenfolge -> evaluiereZeichenfolge(ausdruck)
      is AST.Satz.Ausdruck.Zahl -> evaluiereZahl(ausdruck)
      is AST.Satz.Ausdruck.Boolean -> evaluiereBoolean(ausdruck)
      is AST.Satz.Ausdruck.Variable -> evaluiereVariable(ausdruck)
      is AST.Satz.Ausdruck.Liste -> evaluiereListe(ausdruck)
      is AST.Satz.Ausdruck.IndexZugriff -> evaluiereIndexZugriff(ausdruck)
      is AST.Satz.Ausdruck.FunktionsAufruf -> durchlaufeFunktionsAufruf(ausdruck)
      is AST.Satz.Ausdruck.BinärerAusdruck -> evaluiereBinärenAusdruck(ausdruck)
      is AST.Satz.Ausdruck.Minus -> evaluiereMinus(ausdruck)
      is AST.Satz.Ausdruck.Konvertierung -> evaluiereKonvertierung(ausdruck)
      is AST.Satz.Ausdruck.ObjektInstanziierung -> evaluiereObjektInstanziierung(ausdruck)
      is AST.Satz.Ausdruck.EigenschaftsZugriff -> evaluiereEigenschaftsZugriff(ausdruck)
      is AST.Satz.Ausdruck.SelbstEigenschaftsZugriff -> evaluiereSelbstEigenschaftsZugriff(ausdruck)
      is AST.Satz.Ausdruck.MethodenBereichEigenschaftsZugriff -> evaluiereMethodenBlockEigenschaftsZugriff(ausdruck)
      is AST.Satz.Ausdruck.SelbstReferenz -> evaluiereSelbstReferenz()
      is AST.Satz.Ausdruck.MethodenBereichReferenz -> evaluiereMethodenBlockReferenz()
      is AST.Satz.Ausdruck.Lambda -> evaluiereLambda(ausdruck)
      is AST.Satz.Ausdruck.AnonymeKlasse -> evaluiereAnonymeKlasse(ausdruck)
      is AST.Satz.Ausdruck.Konstante -> evaluiereKonstante(ausdruck)
      is AST.Satz.Ausdruck.TypÜberprüfung -> evaluiereTypÜberprüfung(ausdruck)
      is AST.Satz.Ausdruck.MethodenBereich -> durchlaufeMethodenBereich(ausdruck)
      is AST.Satz.Ausdruck.SuperBereich -> durchlaufeBereich(ausdruck.bereich, true)
      is AST.Satz.Ausdruck.Bedingung -> durchlaufeBedingungsSatz(ausdruck)
      is AST.Satz.Ausdruck.Nichts -> Nichts
      is AST.Satz.Ausdruck.VersucheFange -> durchlaufeVersucheFange(ausdruck)
      is AST.Satz.Ausdruck.Werfe -> durchlaufeWerfe(ausdruck)
    }
  }

  private fun evaluiereVariable(variable: AST.Satz.Ausdruck.Variable): Objekt {
    return if (variable.konstante != null) {
      evaluiereAusdruck(variable.konstante!!.wert!!)
    } else {
      return this.evaluiereVariable(variable.name)
    }
  }

  private fun evaluiereVariable(name: AST.WortArt.Nomen): Objekt {
    return umgebung.leseVariable(name).wert
  }

  private fun evaluiereVariable(variable: String): Objekt? {
    return umgebung.leseVariable(variable)?.wert
  }

  private fun evaluiereMethodenBlockReferenz(): Objekt {
    return umgebung.holeMethodenBlockObjekt()!!
  }
  
  private fun evaluiereZeichenfolge(ausdruck: AST.Satz.Ausdruck.Zeichenfolge): Objekt {
    return Zeichenfolge(ausdruck.zeichenfolge.typ.zeichenfolge)
  }

  private fun evaluiereZahl(ausdruck: AST.Satz.Ausdruck.Zahl): Objekt {
    return Zahl(ausdruck.zahl.typ.zahl)
  }

  private fun evaluiereBoolean(ausdruck: AST.Satz.Ausdruck.Boolean): Objekt {
    return Boolean(ausdruck.boolean.typ.boolean)
  }

  private fun evaluiereKonstante(konstante: AST.Satz.Ausdruck.Konstante): Objekt = evaluiereAusdruck(konstante.wert!!)

  private fun evaluiereListe(ausdruck: AST.Satz.Ausdruck.Liste): Objekt {
    return Liste(
        Typ.Compound.Klasse(BuildIn.Klassen.liste, listOf(ausdruck.pluralTyp)),
        ausdruck.elemente.map(::evaluiereAusdruck).toMutableList()
    )
  }

  private fun evaluiereObjektInstanziierung(instanziierung: AST.Satz.Ausdruck.ObjektInstanziierung): Objekt {
    val klassenTyp = (instanziierung.klasse.typ!! as Typ.Compound.Klasse)
    // TODO: Wie löse ich das Problem, dass hier interne Objekte instanziiert werden können?
    val objekt = when(instanziierung.klasse.name.nominativ) {
      "Datei" -> Datei(klassenTyp)
      "HashMap" -> germanskript.alte_pipeline.intern.HashMap(klassenTyp)
      "HashSet" -> TODO("interne HashSet für alte Pipeline")
      else -> Objekt.SkriptObjekt(klassenTyp)
    }

    fun führeKonstruktorAus(instanziierung: AST.Satz.Ausdruck.ObjektInstanziierung) {
      // Führe zuerst den Konstruktor der Elternklasse aus
      val definition = (instanziierung.klasse.typ!! as Typ.Compound.Klasse).definition
      val eigenschaftsZuweisungen = instanziierung.eigenschaftsZuweisungen
      val evaluierteAusdrücke = eigenschaftsZuweisungen.map { evaluiereAusdruck(it.ausdruck) }
      if (definition.elternKlasse != null) {
        val konstruktorUmgebung = Umgebung<Objekt>()
        konstruktorUmgebung.pushBereich()
        for (index in eigenschaftsZuweisungen.indices) {
          konstruktorUmgebung.schreibeVariable(eigenschaftsZuweisungen[index].name, evaluierteAusdrücke[index], false)
        }
        aufrufStapel.push(definition.elternKlasse, konstruktorUmgebung)
        führeKonstruktorAus(definition.elternKlasse)
        aufrufStapel.pop()
      }

      for (index in eigenschaftsZuweisungen.indices) {
        //val eigenschaftsName = typPrüfer.holeParamName(definition.eigenschaften[index], klassenTyp.typArgumente)
        //eigenschaften[eigenschaftsName.nominativ] = evaluierteAusdrücke[index]
        objekt.eigenschaften[instanziierung.eigenschaftsNamen[index].nominativ] = evaluierteAusdrücke[index]
      }
      durchlaufeAufruf(instanziierung, definition.konstruktor, Umgebung(), true, objekt)
    }

    führeKonstruktorAus(instanziierung)
    return  objekt
  }

  private fun holeEigenschaft(zugriff: AST.Satz.Ausdruck.IEigenschaftsZugriff, objekt: Objekt): Objekt {
    val eigName = zugriff.eigenschaftsName.nominativ
    return try {
      objekt.holeEigenschaft(eigName)
    } catch (nichtGefunden: NoSuchElementException) {
      val berechneteEigenschaft = objekt.klasse.definition.berechneteEigenschaften.getValue(eigName)
      durchlaufeAufruf(zugriff, berechneteEigenschaft.definition, Umgebung(), true, objekt)
    }
  }

  private fun evaluiereEigenschaftsZugriff(eigenschaftsZugriff: AST.Satz.Ausdruck.EigenschaftsZugriff): Objekt {
    return holeEigenschaft(eigenschaftsZugriff, evaluiereAusdruck(eigenschaftsZugriff.objekt))
  }

  private fun evaluiereSelbstEigenschaftsZugriff(eigenschaftsZugriff: AST.Satz.Ausdruck.SelbstEigenschaftsZugriff): Objekt {
    val objekt = aufrufStapel.top().objekt!!
    return holeEigenschaft(eigenschaftsZugriff, objekt)
  }

  private fun evaluiereMethodenBlockEigenschaftsZugriff(eigenschaftsZugriff: AST.Satz.Ausdruck.MethodenBereichEigenschaftsZugriff): Objekt {
    val objekt = umgebung.holeMethodenBlockObjekt()!!
    return holeEigenschaft(eigenschaftsZugriff, objekt)
  }

  private fun evaluiereSelbstReferenz(): Objekt = aufrufStapel.top().objekt!!

  private  fun evaluiereBinärenAusdruck(ausdruck: AST.Satz.Ausdruck.BinärerAusdruck): Objekt {
    val links = evaluiereAusdruck(ausdruck.links)
    val operator = ausdruck.operator.typ.operator

    // implementiere hier Short-circuit evaluation (https://en.wikipedia.org/wiki/Short-circuit_evaluation)
    if (links is germanskript.alte_pipeline.intern.Boolean) {
      if ((operator == Operator.UND && !links.boolean) || (operator == Operator.ODER && links.boolean)) {
        return links
      }
    }

    val rechts = evaluiereAusdruck(ausdruck.rechts)

    return if (operator.klasse == OperatorKlasse.LOGISCH) {
      when (operator) {
        Operator.ODER -> Boolean(
            (links as germanskript.alte_pipeline.intern.Boolean).boolean || (rechts as germanskript.alte_pipeline.intern.Boolean).boolean)
        Operator.UND -> Boolean(
            (links as germanskript.alte_pipeline.intern.Boolean).boolean && (rechts as germanskript.alte_pipeline.intern.Boolean).boolean)
        else -> throw Exception("Dieser Fall sollte nie eintreten!")
      }
    } else {
      val methode = links.klasse.definition.methoden.getValue(operator.methodenName!!)
      val parameterName = methode.signatur.parameter.first().name.nominativ
      val aufrufUmgebung = Umgebung<Objekt>()
      aufrufUmgebung.pushBereich()
      aufrufUmgebung.schreibeVariable(parameterName, rechts)
      val ergebnis = durchlaufeAufruf(
          object : AST.IAufruf {
            override val token = ausdruck.operator.toUntyped()
            override val vollerName = methode.signatur.vollerName
          },
          methode.körper,
          aufrufUmgebung, false,
          links
      )
      return if (operator.klasse == OperatorKlasse.ARITHMETISCH) {
        ergebnis
      } else if (operator == Operator.GLEICH || operator == Operator.UNGLEICH) {
        if (operator == Operator.GLEICH) {
          ergebnis as germanskript.alte_pipeline.intern.Boolean
        } else {
          Boolean(!(ergebnis as germanskript.alte_pipeline.intern.Boolean).boolean)
        }
      } else {
        val vergleich = ergebnis as Zahl

        Boolean(when (operator) {
          Operator.GRÖßER -> vergleich.zahl > 0
          Operator.KLEINER -> vergleich.zahl < 0
          Operator.GRÖSSER_GLEICH -> vergleich.zahl >= 0
          Operator.KLEINER_GLEICH -> vergleich.zahl <= 0
          else -> throw Exception("Dieser Fall sollte nie auftreten.")
        })
      }
    }
  }

  companion object {
  val preloadedKlassenDefinitionen = arrayOf("Fehler", "KonvertierungsFehler", "SchlüsselNichtGefundenFehler", "IndexFehler")

  fun zeichenFolgenOperation(
      operator: Operator,
      links: Zeichenfolge,
      rechts: Zeichenfolge
  ): Objekt {
    return when (operator) {
      Operator.GLEICH -> Boolean(links == rechts)
      Operator.UNGLEICH -> Boolean(links != rechts)
      Operator.GRÖßER -> Boolean(links > rechts)
      Operator.KLEINER -> Boolean(links < rechts)
      Operator.GRÖSSER_GLEICH -> Boolean(links >= rechts)
      Operator.KLEINER_GLEICH -> Boolean(links <= rechts)
      Operator.PLUS -> links + rechts
      else -> throw Exception("Operator $operator ist für den Typen Zeichenfolge nicht definiert.")
    }
  }

  fun zahlOperation(operator: Operator, links: Zahl, rechts: Zahl): Objekt {
    return when (operator) {
      Operator.GLEICH -> Boolean(links == rechts)
      Operator.UNGLEICH -> Boolean(links != rechts)
      Operator.GRÖßER -> Boolean(links > rechts)
      Operator.KLEINER -> Boolean(links < rechts)
      Operator.GRÖSSER_GLEICH -> Boolean(links >= rechts)
      Operator.KLEINER_GLEICH -> Boolean(links <= rechts)
      Operator.PLUS -> links + rechts
      Operator.MINUS -> links - rechts
      Operator.MAL -> links * rechts
      Operator.GETEILT -> links / rechts
      Operator.MODULO -> links % rechts
      Operator.HOCH -> links.pow(rechts)
      else -> throw Exception("Operator $operator ist für den Typen Zahl nicht definiert.")
    }
  }

  fun booleanOperation(operator: Operator, links: germanskript.alte_pipeline.intern.Boolean, rechts: germanskript.alte_pipeline.intern.Boolean): Objekt {
    return when (operator) {
      Operator.ODER -> Boolean(links.boolean || rechts.boolean)
      Operator.UND -> Boolean(links.boolean && rechts.boolean)
      Operator.GLEICH -> Boolean(links.boolean == rechts.boolean)
      Operator.UNGLEICH -> Boolean(links.boolean != rechts.boolean)
      else -> throw Exception("Operator $operator ist für den Typen Boolean nicht definiert.")
    }
  }
}

  private fun listenOperation(operator: Operator, links: Liste, rechts: Liste): Objekt {
    return when (operator) {
      Operator.PLUS ->links + rechts
      else -> throw Exception("Operator $operator ist für den Typen Liste nicht definiert.")
    }
  }

  private fun evaluiereMinus(minus: AST.Satz.Ausdruck.Minus): Objekt {
    val ausdruck = evaluiereAusdruck(minus.ausdruck)

    return durchlaufeAufruf(
        object : AST.IAufruf {
          override val token = minus.holeErstesToken()
          override val vollerName = "negiere mich"
        },
        ausdruck.klasse.definition.methoden["negiere mich"]!!.körper,
        Umgebung(), false,
        ausdruck
    )
  }

  private fun evaluiereIndizierbarSingularOderPlural(singular: AST.WortArt.Nomen, numerus: Numerus): Objekt {
    return when (val vornomenTyp = singular.vornomen?.typ) {
      is TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN -> {
        val objekt = when (vornomenTyp) {
          TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN -> aufrufStapel.top().objekt!!
          TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.DEIN -> umgebung.holeMethodenBlockObjekt()!!
        }
        objekt.holeEigenschaft(singular.ganzesWort(Kasus.NOMINATIV, numerus, true))
      }
      is TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT, null ->
        evaluiereVariable(singular.ganzesWort(Kasus.NOMINATIV, numerus, true))!!
      else -> throw Exception("Dieser Fall sollte nie eintreten!")
    }
  }

  private fun evaluiereIndexZugriff(indexZugriff: AST.Satz.Ausdruck.IndexZugriff): Objekt {
    val indiziert = evaluiereIndizierbarSingularOderPlural(indexZugriff.singular, indexZugriff.numerus)
    val index = evaluiereAusdruck(indexZugriff.index)

    val methode = indiziert.klasse.definition.methoden.getValue("hole den Typ mit dem Index")
    val aufrufUmgebung = Umgebung<Objekt>()
    aufrufUmgebung.pushBereich()
    aufrufUmgebung.schreibeVariable(methode.signatur.parameter.first().name.nominativ, index)

    return durchlaufeAufruf(
        object : AST.IAufruf {
          override val token = indexZugriff.singular.bezeichnerToken
          override val vollerName = methode.signatur.vollerName
        },
        methode.körper,
        aufrufUmgebung, false,
        indiziert
    )
  }

  private fun evaluiereLambda(lambda: AST.Satz.Ausdruck.Lambda): Objekt {
    return Objekt.Lambda(lambda.klasse, umgebung)
  }

  private fun evaluiereAnonymeKlasse(anonymeKlasse: AST.Satz.Ausdruck.AnonymeKlasse): Objekt {
    val objekt = Objekt.AnonymesSkriptObjekt(anonymeKlasse.klasse, umgebung)

    anonymeKlasse.bereich.eigenschaften.forEach {
      objekt.setzeEigenschaft(it.name.nominativ, evaluiereAusdruck(it.wert))
      it.name.nominativ to evaluiereAusdruck(it.wert)
    }

    return objekt
  }

  private fun evaluiereTypÜberprüfung(typÜberprüfung: AST.Satz.Ausdruck.TypÜberprüfung): Objekt {
    val klasse = evaluiereAusdruck(typÜberprüfung.ausdruck).klasse
    val istTyp = typPrüfer.typIstTyp(klasse, typÜberprüfung.typ.typ!!)
    return Boolean(istTyp)
  }

  // endregion


  private fun durchlaufeInternenSchnittstellenAufruf(wert: Objekt, name: String, argumente: Array<Objekt>): Objekt {
    val funktionsUmgebung = if (wert is Objekt.AnonymesSkriptObjekt) wert.umgebung else Umgebung()
    val methode = wert.klasse.definition.methoden.getValue(name)
    val parameterNamen = methode.signatur.parameter.map { it.name }.toList()

    funktionsUmgebung.pushBereich()
    for (i in parameterNamen.indices) {
      funktionsUmgebung.schreibeVariable(parameterNamen[i], argumente[i], false)
    }

    return durchlaufeAufruf(
        aufrufStapel.top().aufruf,
        methode.körper, funktionsUmgebung, false, wert
    )
  }

  // region interne Funktionen
  private val interneFunktionen = mapOf<String, () -> (Objekt)>(
    "schreibe die Zeichenfolge" to {
      val zeichenfolge = umgebung.leseVariable("Zeichenfolge")!!.wert as Zeichenfolge
      print(zeichenfolge)
      Nichts
    },

    "schreibe die Zeile" to {
      val zeile = umgebung.leseVariable("Zeile")!!.wert as Zeichenfolge
      println(zeile)
      Nichts
    },

    "schreibe die Zahl" to {
      val zahl = umgebung.leseVariable("Zahl")!!.wert as Zahl
      println(zahl)
      Nichts
    },

    "schreibe die Meldung" to {
      val zeichenfolge = umgebung.leseVariable("FehlerMeldung")!!.wert as Zeichenfolge
      System.err.println(zeichenfolge)
      Nichts
    },

    "erstelle aus dem Code" to {
      val zeichenfolge = umgebung.leseVariable("Code")!!.wert as Zahl
      Zeichenfolge(zeichenfolge.toInt().toChar().toString())
    },

    "lese" to {
      Zeichenfolge(readLine()!!)
    },

    "runde die Zahl" to {
      val zahl = umgebung.leseVariable("Zahl")!!.wert as Zahl
      Zahl(round(zahl.zahl))
    },

    "runde die Zahl ab" to {
      val zahl = umgebung.leseVariable("Zahl")!!.wert as Zahl
      Zahl(floor(zahl.zahl))
    },

    "runde die Zahl auf" to {
      val zahl = umgebung.leseVariable("Zahl")!!.wert as Zahl
      Zahl(ceil(zahl.zahl))
    },

    "sinus von der Zahl" to {
      val zahl = umgebung.leseVariable("Zahl")!!.wert as Zahl
      Zahl(sin(zahl.zahl))
    },

    "cosinus von der Zahl" to {
      val zahl = umgebung.leseVariable("Zahl")!!.wert as Zahl
      Zahl(cos(zahl.zahl))
    },

    "tangens von der Zahl" to {
      val zahl = umgebung.leseVariable("Zahl")!!.wert as Zahl
      Zahl(tan(zahl.zahl))
    },

    "randomisiere" to {
      Zahl(Random.nextDouble())
    },

    "randomisiere zwischen dem Minimum, dem Maximum" to {
      val min = umgebung.leseVariable("Minimum")!!.wert as Zahl
      val max = umgebung.leseVariable("Maximum")!!.wert as Zahl
      Zahl(Random.nextDouble(min.zahl, max.zahl))
    }
  )

  private fun evaluiereKonvertierung(konvertierung: AST.Satz.Ausdruck.Konvertierung): Objekt {
    val wert = evaluiereAusdruck(konvertierung.ausdruck)

    return when (konvertierung.konvertierungsArt) {
      AST.Satz.Ausdruck.KonvertierungsArt.Cast -> {
        if (typPrüfer.typIstTyp(wert.klasse, konvertierung.typ.typ!!)) {
          wert
        } else {
          val fehlerMeldung = "Ungültige Konvertierung!\n" +
              "Die Klasse '${wert}' kann nicht nach '${konvertierung.typ.typ!!}' konvertiert werden."
          werfeFehler(fehlerMeldung, "KonvertierungsFehler", konvertierung.token)
        }
      }
      AST.Satz.Ausdruck.KonvertierungsArt.Methode -> wert.klasse.definition.konvertierungen.getValue(konvertierung.typ.typ!!.name).let {
        durchlaufeAufruf(konvertierung, it.definition, Umgebung(), true, wert)
      }
    }
  }

  private fun werfeFehler(fehlerMeldung: String, fehlerKlassenName: String, token: Token): Objekt {
    geworfenerFehler = Objekt.SkriptObjekt(Typ.Compound.Klasse(klassenDefinitionen.getValue(fehlerKlassenName), emptyList()))
    geworfenerFehler!!.eigenschaften["FehlerMeldung"] = Zeichenfolge(fehlerMeldung)
    geworfenerFehlerToken = token
    flags.add(Flag.FEHLER_GEWORFEN)
    return Niemals
  }
  // endregion

}

fun main() {
  val interpreter = Interpretierer(File("./iterationen/iter_2/code.gm"))
  interpreter.interpretiere()
}