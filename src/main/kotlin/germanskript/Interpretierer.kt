package germanskript

import germanskript.intern.Wert
import java.io.File
import java.util.*
import kotlin.NoSuchElementException
import kotlin.collections.HashMap
import kotlin.math.*
import kotlin.random.Random

typealias SchnittstellenAufruf = (Wert, String, argumente: Array<Wert>) -> Wert?
typealias WerfeFehler = (String, String, Token) -> Wert

class InterpretInjection(
    val aufrufStapel: Interpretierer.AufrufStapel,
    val schnittstellenAufruf: SchnittstellenAufruf,
    val werfeFehler: WerfeFehler
) {
  val umgebung: Umgebung<Wert> get() = aufrufStapel.top().umgebung
}

class Interpretierer(startDatei: File): PipelineKomponente(startDatei) {
  val entsüßer = Entsüßer(startDatei)
  val typPrüfer = entsüßer.typPrüfer

  val ast: AST.Programm = entsüßer.ast

  private val flags = EnumSet.noneOf(Flag::class.java)
  private val aufrufStapel = AufrufStapel()
  private var rückgabeWert: Wert = germanskript.intern.Nichts

  private var geworfenerFehler: Wert.Objekt? = null
  private var geworfenerFehlerToken: Token? = null

  private val umgebung: Umgebung<Wert> get() = aufrufStapel.top().umgebung

  private val klassenDefinitionen = HashMap<String, AST.Definition.Typdefinition.Klasse>()

  private lateinit var interpretInjection: InterpretInjection

  fun interpretiere() {
    entsüßer.entsüße()
    initKlassenDefinitionen()
    try {
      aufrufStapel.push(ast, Umgebung())
      interpretInjection = InterpretInjection(aufrufStapel, ::durchlaufeInternenSchnittstellenAufruf, ::werfeFehler)
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
    val konvertierungsDefinition = geworfenerFehler!!.typ.definition.konvertierungen.getValue("Zeichenfolge")

    val aufruf = object : AST.IAufruf {
      override val token: Token = konvertierungsDefinition.typ.name.bezeichnerToken
      override val vollerName = "als Zeichenfolge"
    }

    val zeichenfolge = durchlaufeAufruf(
        aufruf,
        konvertierungsDefinition.definition,
        Umgebung(),
        true, geworfenerFehler!!, false
    ) as germanskript.intern.Zeichenfolge

    throw GermanSkriptFehler.UnbehandelterFehler(geworfenerFehlerToken!!, aufrufStapel.toString(), zeichenfolge.zeichenfolge, geworfenerFehler!!)
  }

  private enum class Flag {
    SCHLEIFE_ABBRECHEN,
    SCHLEIFE_FORTFAHREN,
    ZURÜCK,
    FEHLER_GEWORFEN,
  }

  class AufrufStapelElement(val aufruf: AST.IAufruf, val objekt: Wert?, val umgebung: Umgebung<Wert>)

  object Konstanten {
     const val CALL_STACK_OUTPUT_LIMIT = 50
  }

  inner class AufrufStapel {
    val stapel = Stack<AufrufStapelElement>()

    fun top(): AufrufStapelElement = stapel.peek()
    fun push(funktionsAufruf: AST.IAufruf, neueUmgebung: Umgebung<Wert>, aufrufObjekt: Wert.Objekt? = null) {
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
      if (element.objekt is Wert.Objekt) {
        val klassenName = element.objekt.typ.definition.name.hauptWort
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
  private fun durchlaufeBereich(bereich: AST.Satz.Bereich, neuerBereich: Boolean): Wert  {
    if (neuerBereich) {
      umgebung.pushBereich()
    }
    var rückgabe: Wert = germanskript.intern.Nichts
    for (satz in bereich.sätze) {
      if (sollteAbbrechen()) {
        return germanskript.intern.Nichts
      }
      if (sollteStackAufrollen()) {
        return germanskript.intern.Niemals
      }
      @Suppress("IMPLICIT_CAST_TO_ANY")
      rückgabe = when (satz) {
        is AST.Satz.VariablenDeklaration -> durchlaufeVariablenDeklaration(satz)
        is AST.Satz.ListenElementZuweisung -> durchlaufeListenElementZuweisung(satz)
        is AST.Satz.Bereich -> durchlaufeBereich(satz, true)
        is AST.Satz.SuperBlock -> durchlaufeBereich(satz.bereich, true)
        is AST.Satz.Zurückgabe -> durchlaufeZurückgabe(satz)
        is AST.Satz.SolangeSchleife -> durchlaufeSolangeSchleife(satz)
        is AST.Satz.Ausdruck.VersucheFange -> durchlaufeVersucheFange(satz)
        is AST.Satz.Ausdruck.Werfe -> durchlaufeWerfe(satz)
        is AST.Satz.SchleifenKontrolle.Abbrechen -> flags.add(Flag.SCHLEIFE_ABBRECHEN).let { germanskript.intern.Nichts }
        is AST.Satz.SchleifenKontrolle.Fortfahren -> flags.add(Flag.SCHLEIFE_FORTFAHREN).let { germanskript.intern.Nichts }
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

  private fun durchlaufeMethodenBereich(methodenBereich: AST.Satz.Ausdruck.MethodenBereich): Wert {
    val wert = evaluiereAusdruck(methodenBereich.objekt)
    umgebung.pushBereich(wert)
    return durchlaufeBereich(methodenBereich.bereich, false).also { umgebung.popBereich() }
  }
  
  private fun durchlaufeBedingung(bedingung: AST.Satz.BedingungsTerm): Wert? {
      return if (!sollteStackAufrollen() && (evaluiereAusdruck(bedingung.bedingung) as germanskript.intern.Boolean).boolean) {
        durchlaufeBereich(bedingung.bereich, true)
      } else {
        null
      }
  }

  private fun durchlaufeVariablenDeklaration(deklaration: AST.Satz.VariablenDeklaration): Wert {
    if (deklaration.istEigenschaftsNeuZuweisung || deklaration.istEigenschaft) {
      // weise Eigenschaft neu zu
      (aufrufStapel.top().objekt!! as Wert.Objekt).setzeEigenschaft(deklaration.name.nominativ, evaluiereAusdruck(deklaration.wert))
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
    return germanskript.intern.Nichts
  }

  private fun durchlaufeListenElementZuweisung(zuweisung: AST.Satz.ListenElementZuweisung): Wert {
    val liste = evaluiereListenSingular(zuweisung.singular)
    val index = (evaluiereAusdruck(zuweisung.index) as germanskript.intern.Zahl).toInt()
    if (index >= liste.elemente.size) {
      throw GermanSkriptFehler.LaufzeitFehler(zuweisung.index.holeErstesToken(),
          aufrufStapel.toString(), "Index außerhalb des Bereichs. Der Index ist $index, doch die Länge der Liste ist ${liste.elemente.size}.\n")
    }
    liste.elemente[index] = evaluiereAusdruck(zuweisung.wert)

    return germanskript.intern.Nichts
  }

  private fun durchlaufeAufruf(
      aufruf: AST.IAufruf,
      bereich: AST.Satz.Bereich,
      umgebung: Umgebung<Wert>,
      neuerBereich: Boolean,
      objekt: Wert.Objekt?,
      impliziterRückgabeTyp: Boolean
  ): Wert {
    rückgabeWert = germanskript.intern.Nichts
    aufrufStapel.push(aufruf, umgebung, objekt)
    return durchlaufeBereich(bereich, neuerBereich).also {
      aufrufStapel.pop()
      if (!impliziterRückgabeTyp) {
        flags.remove(Flag.ZURÜCK)
      }
    }
  }

  /**
   * Bei Closures werden die Parameternamen bei generischen Parametern mit dem Namen des eingesetzen Typen ersetzt.
   */
  private fun holeParameterNamenFürClosure(objekt: Wert.Closure): List<AST.WortArt.Nomen> {
    val typArgumente = objekt.schnittstelle.typArgumente
    val signatur = objekt.schnittstelle.definition.methodenSignaturen[0]
    return signatur.parameter.toList().mapIndexed { index, param ->
      if (index < objekt.ausdruck.bindings.size) objekt.ausdruck.bindings[index]
      else when (val typ = param.typKnoten.typ!!) {
        is Typ.Generic ->
          if (param.typIstName) param.name.tauscheHauptWortAus(typArgumente[typ.index].name.deklination!!) else param.name
        else -> param.name
      }
    }
  }

  private fun durchlaufeFunktionsAufruf(funktionsAufruf: AST.Satz.Ausdruck.FunktionsAufruf): Wert {
    var funktionsUmgebung = Umgebung<Wert>()
    var objekt: Wert.Objekt? = null
    var impliziterRückgabeTyp = false
    val (körper, parameterNamen) = if (funktionsAufruf.aufrufTyp == FunktionsAufrufTyp.FUNKTIONS_AUFRUF) {
      val definition = funktionsAufruf.funktionsDefinition!!
      Pair(definition.körper, definition.signatur.parameter.map{it.name}.toList())
    } else {
      // dynamisches Binden von Methoden
      val methodenObjekt = when(funktionsAufruf.aufrufTyp) {
        FunktionsAufrufTyp.METHODEN_SUBJEKT_AUFRUF -> evaluiereAusdruck(funktionsAufruf.subjekt!!)
        FunktionsAufrufTyp.METHODEN_REFLEXIV_AUFRUF -> evaluiereAusdruck(funktionsAufruf.objekt!!.ausdruck)
        FunktionsAufrufTyp.METHODEN_SELBST_AUFRUF -> aufrufStapel.top().objekt!!
        else -> umgebung.holeMethodenBlockObjekt()
      }
      objekt = if (methodenObjekt is Wert.Objekt) methodenObjekt else null
      if (funktionsAufruf.funktionsDefinition != null) {
        val definition = funktionsAufruf.funktionsDefinition!!
        Pair(definition.körper, definition.signatur.parameter.map{it.name}.toList())
      } else when (methodenObjekt)
      {
        is Wert.Objekt -> {
          val methode = methodenObjekt.typ.definition.methoden.getValue(funktionsAufruf.vollerName!!)
          // funktionsAufruf.vollerName = "für ${objekt.typ.definition.name.nominativ}: ${methode.signatur.vollerName}"
          if (methodenObjekt is Wert.Objekt.AnonymesSkriptObjekt) {
            funktionsUmgebung = methodenObjekt.umgebung
          }
          val signatur = methode.signatur
          Pair(methode.körper, signatur.parameter.map {it.name}.toList())
        }
        is Wert.Closure -> {
          impliziterRückgabeTyp = true
          funktionsUmgebung = methodenObjekt.umgebung
          Pair(methodenObjekt.ausdruck.körper, holeParameterNamenFürClosure(methodenObjekt))
        }
        else -> throw Exception("Dieser Fall sollte nie eintreten.")
      }
    }
    funktionsUmgebung.pushBereich()
    val j = if (funktionsAufruf.aufrufTyp == FunktionsAufrufTyp.METHODEN_REFLEXIV_AUFRUF) 1 else 0
    val argumente = funktionsAufruf.argumente.toList()
    for (i in parameterNamen.indices) {
      funktionsUmgebung.schreibeVariable(parameterNamen[i], evaluiereAusdruck(argumente[i+j].ausdruck), false)
    }

    return durchlaufeAufruf(funktionsAufruf, körper, funktionsUmgebung, false, objekt, impliziterRückgabeTyp)
        .let { if (impliziterRückgabeTyp) it else rückgabeWert }
  }

  private fun durchlaufeZurückgabe(zurückgabe: AST.Satz.Zurückgabe): Wert {
    return evaluiereAusdruck(zurückgabe.ausdruck).also {
      flags.add(Flag.ZURÜCK)
      rückgabeWert = it
    }
  }

  private fun durchlaufeBedingungsSatz(bedingungsSatz: AST.Satz.Ausdruck.Bedingung): Wert {
    val inBedingung = bedingungsSatz.bedingungen.any { bedingung ->
      durchlaufeBedingung(bedingung)?.also { return it } != null
    }

    return if (!inBedingung && bedingungsSatz.sonst != null ) {
      durchlaufeBereich(bedingungsSatz.sonst!!.bereich, true)
    } else {
      return germanskript.intern.Nichts
    }
  }

  private fun durchlaufeSolangeSchleife(schleife: AST.Satz.SolangeSchleife): Wert {
    while (!flags.contains(Flag.SCHLEIFE_ABBRECHEN) && !flags.contains(Flag.ZURÜCK) && (evaluiereAusdruck(schleife.bedingung.bedingung) as germanskript.intern.Boolean).boolean) {
      flags.remove(Flag.SCHLEIFE_FORTFAHREN)
      durchlaufeBereich(schleife.bedingung.bereich, true)
    }
    flags.remove(Flag.SCHLEIFE_ABBRECHEN)
    return germanskript.intern.Nichts
  }

  private fun durchlaufeVersucheFange(versucheFange: AST.Satz.Ausdruck.VersucheFange): Wert {
    var rückgabe = durchlaufeBereich(versucheFange.bereich, true)
    if (flags.contains(Flag.FEHLER_GEWORFEN)) {
      val fehlerObjekt = geworfenerFehler!!
      val fehlerTyp = typeOf(fehlerObjekt)
      val fange = versucheFange.fange.find { fange ->
        typPrüfer.typIstTyp(fehlerTyp, fange.param.typKnoten.typ!!)
      }
      if (fange != null) {
        flags.remove(Flag.FEHLER_GEWORFEN)
        umgebung.pushBereich()
        umgebung.schreibeVariable(fange.param.name, fehlerObjekt, true)
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

  private fun durchlaufeWerfe(werfe: AST.Satz.Ausdruck.Werfe): Wert {
    val wert = evaluiereAusdruck(werfe.ausdruck)
    if (wert !is Wert.Objekt) {
      throw Exception("Der Typprüfer sollte diesen Fall schon überprüfen.")
    }
    geworfenerFehlerToken = werfe.werfe.toUntyped()
    geworfenerFehler = wert
    flags.add(Flag.FEHLER_GEWORFEN)
    return germanskript.intern.Niemals
  }

  private fun durchlaufeIntern(intern: AST.Satz.Intern): Wert {
    val aufruf = aufrufStapel.top().aufruf
    return when (val objekt = aufrufStapel.top().objekt) {
      is Wert.Objekt -> objekt.rufeMethodeAuf(aufruf, interpretInjection)
      else -> interneFunktionen.getValue(aufruf.vollerName!!)()
    }.also { rückgabeWert = it }
  }

  // endregion

  // region Ausdrücke
  private fun evaluiereAusdruck(ausdruck: AST.Satz.Ausdruck): Wert {
    if (sollteStackAufrollen()) {
      return germanskript.intern.Niemals
    }
    return when (ausdruck) {
      is AST.Satz.Ausdruck.Zeichenfolge -> evaluiereZeichenfolge(ausdruck)
      is AST.Satz.Ausdruck.Zahl -> evaluiereZahl(ausdruck)
      is AST.Satz.Ausdruck.Boolean -> evaluiereBoolean(ausdruck)
      is AST.Satz.Ausdruck.Variable -> evaluiereVariable(ausdruck)
      is AST.Satz.Ausdruck.Liste -> evaluiereListe(ausdruck)
      is AST.Satz.Ausdruck.ListenElement -> evaluiereListenElement(ausdruck)
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
      is AST.Satz.Ausdruck.Closure -> evaluiereClosure(ausdruck)
      is AST.Satz.Ausdruck.AnonymeKlasse -> evaluiereAnonymeKlasse(ausdruck)
      is AST.Satz.Ausdruck.Konstante -> evaluiereKonstante(ausdruck)
      is AST.Satz.Ausdruck.TypÜberprüfung -> evaluiereTypÜberprüfung(ausdruck)
      is AST.Satz.Ausdruck.MethodenBereich -> durchlaufeMethodenBereich(ausdruck)
      is AST.Satz.Ausdruck.Bedingung -> durchlaufeBedingungsSatz(ausdruck)
      is AST.Satz.Ausdruck.Nichts -> germanskript.intern.Nichts
      is AST.Satz.Ausdruck.VersucheFange -> durchlaufeVersucheFange(ausdruck)
      is AST.Satz.Ausdruck.Werfe -> durchlaufeWerfe(ausdruck)
    }
  }

  private fun evaluiereVariable(variable: AST.Satz.Ausdruck.Variable): Wert {
    return if (variable.konstante != null) {
      evaluiereAusdruck(variable.konstante!!.wert!!)
    } else {
      return this.evaluiereVariable(variable.name)
    }
  }

  private fun evaluiereVariable(name: AST.WortArt.Nomen): Wert {
    return umgebung.leseVariable(name).wert
  }

  private fun evaluiereVariable(variable: String): Wert? {
    return umgebung.leseVariable(variable)?.wert
  }

  private fun evaluiereMethodenBlockReferenz(): Wert {
    return umgebung.holeMethodenBlockObjekt()!!
  }
  
  private fun evaluiereZeichenfolge(ausdruck: AST.Satz.Ausdruck.Zeichenfolge): Wert {
    return ausdruck.zeichenfolge.typ.zeichenfolge
  }

  private fun evaluiereZahl(ausdruck: AST.Satz.Ausdruck.Zahl): Wert {
    return ausdruck.zahl.typ.zahl
  }

  private fun evaluiereBoolean(ausdruck: AST.Satz.Ausdruck.Boolean): Wert {
    return ausdruck.boolean.typ.boolean
  }

  private fun evaluiereKonstante(konstante: AST.Satz.Ausdruck.Konstante): Wert = evaluiereAusdruck(konstante.wert!!)

  private fun evaluiereListe(ausdruck: AST.Satz.Ausdruck.Liste): Wert {
    return germanskript.intern.Liste(
        Typ.Compound.KlassenTyp.Liste(listOf(ausdruck.pluralTyp)),
        ausdruck.elemente.map(::evaluiereAusdruck).toMutableList()
    )
  }

  private fun evaluiereObjektInstanziierung(instanziierung: AST.Satz.Ausdruck.ObjektInstanziierung): Wert {
    val eigenschaften = hashMapOf<String, Wert>()
    val klassenTyp = (instanziierung.klasse.typ!! as Typ.Compound.KlassenTyp)
    // TODO: Wie löse ich das Problem, dass hier interne Objekte instanziiert werden können?
    val objekt = when(instanziierung.klasse.name.nominativ) {
      "Datei" -> germanskript.intern.Datei(klassenTyp, eigenschaften)
      "HashMap" -> germanskript.intern.HashMap(klassenTyp, eigenschaften)
      else -> Wert.Objekt.SkriptObjekt(klassenTyp, eigenschaften)
    }

    fun führeKonstruktorAus(definition: AST.Definition.Typdefinition.Klasse, eigenschaftsZuweisungen: List<AST.Argument>) {
      // Führe zuerst den Konstruktor der Elternklasse aus
      val evaluierteAusdrücke = eigenschaftsZuweisungen.map { evaluiereAusdruck(it.ausdruck) }
      if (definition.elternKlasse != null) {
        val konstruktorUmgebung = Umgebung<Wert>()
        konstruktorUmgebung.pushBereich()
        for (index in eigenschaftsZuweisungen.indices) {
          konstruktorUmgebung.schreibeVariable(eigenschaftsZuweisungen[index].name, evaluierteAusdrücke[index], false)
        }
        aufrufStapel.push(definition.elternKlasse, konstruktorUmgebung)
        führeKonstruktorAus(
            (definition.elternKlasse.klasse.typ!! as Typ.Compound.KlassenTyp).definition,
            definition.elternKlasse.eigenschaftsZuweisungen
        )
        aufrufStapel.pop()
      }
      for (index in eigenschaftsZuweisungen.indices) {
        val eigenschaftsName = typPrüfer.holeParamName(definition.eigenschaften[index], klassenTyp.typArgumente)
        eigenschaften[eigenschaftsName.nominativ] = evaluierteAusdrücke[index]
      }
      durchlaufeAufruf(instanziierung, definition.konstruktor, Umgebung(), true, objekt, false)
    }

    führeKonstruktorAus(klassenTyp.definition, instanziierung.eigenschaftsZuweisungen)
    return  objekt
  }

  private fun holeEigenschaft(zugriff: AST.Satz.Ausdruck.IEigenschaftsZugriff, objekt: Wert.Objekt): Wert {
    val eigName = zugriff.eigenschaftsName.nominativ
    return try {
      objekt.holeEigenschaft(eigName)
    } catch (nichtGefunden: NoSuchElementException) {
      val berechneteEigenschaft = objekt.typ.definition.berechneteEigenschaften.getValue(eigName)
      durchlaufeAufruf(zugriff, berechneteEigenschaft.definition, Umgebung(), true, objekt, false)
    }
  }

  private fun evaluiereEigenschaftsZugriff(eigenschaftsZugriff: AST.Satz.Ausdruck.EigenschaftsZugriff): Wert {
    return when (val objekt = evaluiereAusdruck(eigenschaftsZugriff.objekt)) {
      is Wert.Objekt -> holeEigenschaft(eigenschaftsZugriff, objekt)
      else -> throw Exception("Dies sollte nie passieren, weil der Typprüfer diesen Fall schon überprüft")
    }
  }

  private fun evaluiereSelbstEigenschaftsZugriff(eigenschaftsZugriff: AST.Satz.Ausdruck.SelbstEigenschaftsZugriff): Wert {
    val objekt = aufrufStapel.top().objekt!! as Wert.Objekt
    return holeEigenschaft(eigenschaftsZugriff, objekt)
  }

  private fun evaluiereMethodenBlockEigenschaftsZugriff(eigenschaftsZugriff: AST.Satz.Ausdruck.MethodenBereichEigenschaftsZugriff): Wert {
    val objekt = umgebung.holeMethodenBlockObjekt()!! as Wert.Objekt
    return holeEigenschaft(eigenschaftsZugriff, objekt)
  }

  private fun evaluiereSelbstReferenz(): Wert = aufrufStapel.top().objekt!!

  private  fun evaluiereBinärenAusdruck(ausdruck: AST.Satz.Ausdruck.BinärerAusdruck): Wert {
    val links = evaluiereAusdruck(ausdruck.links)
    val operator = ausdruck.operator.typ.operator

    // implementiere hier Short-circuit evaluation (https://en.wikipedia.org/wiki/Short-circuit_evaluation)
    if (links is germanskript.intern.Boolean) {
      if ((operator == Operator.UND && !links.boolean) || (operator == Operator.ODER && links.boolean)) {
        return links
      }
    }

    val rechts = evaluiereAusdruck(ausdruck.rechts)

    if (operator == Operator.GLEICH && links is Wert.Objekt && rechts is Wert.Objekt) {
      // TODO: syntaktischer Zucker der zur Laufzeit aufgelöst wird ist schlecht für die Performance
      val aufrufUmgebung = Umgebung<Wert>()
      aufrufUmgebung.pushBereich()
      aufrufUmgebung.schreibeVariable("Objekt", rechts)
      return durchlaufeAufruf(
        object : AST.IAufruf {
          override val token = ausdruck.operator.toUntyped()
          override val vollerName = "gleicht dem Objekt"
        },
        links.typ.definition.methoden["gleicht dem Objekt"]!!.körper,
        aufrufUmgebung, false,
        links,
        false
      )
    }
    return when (links) {
      is germanskript.intern.Zeichenfolge -> zeichenFolgenOperation(operator, links, rechts as germanskript.intern.Zeichenfolge)
      is germanskript.intern.Zahl -> {
        if ((rechts as germanskript.intern.Zahl).isZero() && operator == Operator.GETEILT) {
          throw GermanSkriptFehler.LaufzeitFehler(ausdruck.rechts.holeErstesToken(), aufrufStapel.toString(),
              "Division durch 0. Es kann nicht durch 0 dividiert werden.")
        }
        zahlOperation(operator, links, rechts)
      }
      is germanskript.intern.Boolean -> booleanOperation(operator, links, rechts as germanskript.intern.Boolean)
      is germanskript.intern.Liste -> listenOperation(operator, links, rechts as germanskript.intern.Liste)
      else -> throw Exception("Typprüfer sollte disen Fehler verhindern.")
    }
  }

  companion object {
  val preloadedKlassenDefinitionen = arrayOf("Fehler", "KonvertierungsFehler", "SchlüsselNichtGefundenFehler")

  fun zeichenFolgenOperation(
      operator: Operator,
      links: germanskript.intern.Zeichenfolge,
      rechts: germanskript.intern.Zeichenfolge
  ): Wert {
    return when (operator) {
      Operator.GLEICH -> germanskript.intern.Boolean(links == rechts)
      Operator.UNGLEICH -> germanskript.intern.Boolean(links != rechts)
      Operator.GRÖßER -> germanskript.intern.Boolean(links > rechts)
      Operator.KLEINER -> germanskript.intern.Boolean(links < rechts)
      Operator.GRÖSSER_GLEICH -> germanskript.intern.Boolean(links >= rechts)
      Operator.KLEINER_GLEICH -> germanskript.intern.Boolean(links <= rechts)
      Operator.PLUS -> germanskript.intern.Zeichenfolge(links + rechts)
      else -> throw Exception("Operator $operator ist für den Typen Zeichenfolge nicht definiert.")
    }
  }

  fun zahlOperation(operator: Operator, links: germanskript.intern.Zahl, rechts: germanskript.intern.Zahl): Wert {
    return when (operator) {
      Operator.GLEICH -> germanskript.intern.Boolean(links == rechts)
      Operator.UNGLEICH -> germanskript.intern.Boolean(links != rechts)
      Operator.GRÖßER -> germanskript.intern.Boolean(links > rechts)
      Operator.KLEINER -> germanskript.intern.Boolean(links < rechts)
      Operator.GRÖSSER_GLEICH -> germanskript.intern.Boolean(links >= rechts)
      Operator.KLEINER_GLEICH -> germanskript.intern.Boolean(links <= rechts)
      Operator.PLUS -> links + rechts
      Operator.MINUS -> links - rechts
      Operator.MAL -> links * rechts
      Operator.GETEILT -> links / rechts
      Operator.MODULO -> links % rechts
      Operator.HOCH -> links.pow(rechts)
      else -> throw Exception("Operator $operator ist für den Typen Zahl nicht definiert.")
    }
  }

  fun booleanOperation(operator: Operator, links: germanskript.intern.Boolean, rechts: germanskript.intern.Boolean): Wert {
    return when (operator) {
      Operator.ODER -> germanskript.intern.Boolean(links.boolean || rechts.boolean)
      Operator.UND -> germanskript.intern.Boolean(links.boolean && rechts.boolean)
      Operator.GLEICH -> germanskript.intern.Boolean(links.boolean == rechts.boolean)
      Operator.UNGLEICH -> germanskript.intern.Boolean(links.boolean != rechts.boolean)
      else -> throw Exception("Operator $operator ist für den Typen Boolean nicht definiert.")
    }
  }
}

  private fun listenOperation(operator: Operator, links: germanskript.intern.Liste, rechts: germanskript.intern.Liste): Wert {
    return when (operator) {
      Operator.PLUS ->links + rechts
      else -> throw Exception("Operator $operator ist für den Typen Liste nicht definiert.")
    }
  }

  private fun evaluiereMinus(minus: AST.Satz.Ausdruck.Minus): germanskript.intern.Zahl {
    val ausdruck = evaluiereAusdruck(minus.ausdruck) as germanskript.intern.Zahl
    return -ausdruck
  }

  private fun evaluiereListenSingular(singular: AST.WortArt.Nomen): germanskript.intern.Liste {
    return when (val vornomenTyp = singular.vornomen?.typ) {
      is TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN -> {
        val objekt = when (vornomenTyp) {
          TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN -> aufrufStapel.top().objekt!! as Wert.Objekt
          TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.DEIN -> umgebung.holeMethodenBlockObjekt()!! as Wert.Objekt
        }
        objekt.holeEigenschaft(singular.ganzesWort(Kasus.NOMINATIV, Numerus.PLURAL, true)) as germanskript.intern.Liste
      }
      is TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT, null ->
        evaluiereVariable(singular.ganzesWort(Kasus.NOMINATIV, Numerus.PLURAL, true)) as germanskript.intern.Liste
      else -> throw Exception("Dieser Fall sollte nie eintreten!")
    }
  }

  private fun evaluiereListenElement(listenElement: AST.Satz.Ausdruck.ListenElement): Wert {
    val index = (evaluiereAusdruck(listenElement.index) as germanskript.intern.Zahl).toInt()

    if (listenElement.istZeichenfolgeZugriff) {
      val zeichenfolge = (evaluiereVariable(listenElement.singular.nominativ) as germanskript.intern.Zeichenfolge).zeichenfolge
      if (index >= zeichenfolge.length) {
        throw GermanSkriptFehler.LaufzeitFehler(listenElement.index.holeErstesToken(),
            aufrufStapel.toString(), "Index außerhalb des Bereichs. Der Index ist $index, doch die Länge der Zeichenfolge ist ${zeichenfolge.length}.\n")
      }
      return germanskript.intern.Zeichenfolge(zeichenfolge[index].toString())
    }

    val liste = evaluiereListenSingular(listenElement.singular)

    if (index >= liste.elemente.size) {
      throw GermanSkriptFehler.LaufzeitFehler(listenElement.index.holeErstesToken(),
          aufrufStapel.toString(), "Index außerhalb des Bereichs. Der Index ist $index, doch die Länge der Liste ist ${liste.elemente.size}.\n")
    }
    return liste.elemente[index]
  }

  private fun evaluiereClosure(closure: AST.Satz.Ausdruck.Closure): Wert {
    return Wert.Closure((closure.schnittstelle.typ as Typ.Compound.Schnittstelle), closure, umgebung)
  }

  private fun evaluiereAnonymeKlasse(anonymeKlasse: AST.Satz.Ausdruck.AnonymeKlasse): Wert {
    val eigenschaften = anonymeKlasse.bereich.eigenschaften
        .map { it.name.nominativ to evaluiereAusdruck(it.wert) }
        .toMap().toMutableMap()
    return Wert.Objekt.AnonymesSkriptObjekt(anonymeKlasse.typ!!, eigenschaften, umgebung)
  }

  private fun evaluiereTypÜberprüfung(typÜberprüfung: AST.Satz.Ausdruck.TypÜberprüfung): Wert {
    val ausdruckTyp = typeOf(evaluiereAusdruck(typÜberprüfung.ausdruck))
    val istTyp = typPrüfer.typIstTyp(ausdruckTyp, typÜberprüfung.typ.typ!!)
    return germanskript.intern.Boolean(istTyp)
  }

  // endregion


  private fun durchlaufeInternenSchnittstellenAufruf(wert: Wert, name: String, argumente: Array<Wert>): Wert {
    val (funktionsUmgebung, körper, parameterNamen) = when (wert) {
      is Wert.Objekt -> {
        val methode = wert.typ.definition.methoden.getValue(name)
        Triple(Umgebung<Wert>(), methode.körper, methode.signatur.parameter.map { it.name }.toList())
      }
      is Wert.Closure -> Triple(wert.umgebung, wert.ausdruck.körper, holeParameterNamenFürClosure(wert))
      else -> throw Exception("Dieser Fall sollte nie auftreten!")
    }

    funktionsUmgebung.pushBereich()
    for (i in parameterNamen.indices) {
      funktionsUmgebung.schreibeVariable(parameterNamen[i], argumente[i], false)
    }
    return durchlaufeAufruf(
        aufrufStapel.top().aufruf,
        körper, funktionsUmgebung, false, if (wert is Wert.Objekt) wert else null, wert is Wert.Closure
    )
  }

  // region interne Funktionen
  private val interneFunktionen = mapOf<String, () -> (Wert)>(
    "schreibe die Zeichenfolge" to {
      val zeichenfolge = umgebung.leseVariable("Zeichenfolge")!!.wert as germanskript.intern.Zeichenfolge
      print(zeichenfolge)
      germanskript.intern.Nichts
    },

    "schreibe die Zeile" to {
      val zeile = umgebung.leseVariable("Zeile")!!.wert as germanskript.intern.Zeichenfolge
      println(zeile)
      germanskript.intern.Nichts
    },

    "schreibe die Zahl" to {
      val zahl = umgebung.leseVariable("Zahl")!!.wert as germanskript.intern.Zahl
      println(zahl)
      germanskript.intern.Nichts
    },

    "schreibe die Meldung" to {
      val zeichenfolge = umgebung.leseVariable("FehlerMeldung")!!.wert as germanskript.intern.Zeichenfolge
      System.err.println(zeichenfolge)
      germanskript.intern.Nichts
    },

    "erstelle aus dem Code" to {
      val zeichenfolge = umgebung.leseVariable("Code")!!.wert as germanskript.intern.Zahl
      germanskript.intern.Zeichenfolge(zeichenfolge.toInt().toChar().toString())
    },

    "lese" to {
      germanskript.intern.Zeichenfolge(readLine()!!)
    },

    "runde die Zahl" to {
      val zahl = umgebung.leseVariable("Zahl")!!.wert as germanskript.intern.Zahl
      germanskript.intern.Zahl(round(zahl.zahl))
    },

    "runde die Zahl ab" to {
      val zahl = umgebung.leseVariable("Zahl")!!.wert as germanskript.intern.Zahl
      germanskript.intern.Zahl(floor(zahl.zahl))
    },

    "runde die Zahl auf" to {
      val zahl = umgebung.leseVariable("Zahl")!!.wert as germanskript.intern.Zahl
      germanskript.intern.Zahl(ceil(zahl.zahl))
    },

    "sinus von der Zahl" to {
      val zahl = umgebung.leseVariable("Zahl")!!.wert as germanskript.intern.Zahl
      germanskript.intern.Zahl(sin(zahl.zahl))
    },

    "cosinus von der Zahl" to {
      val zahl = umgebung.leseVariable("Zahl")!!.wert as germanskript.intern.Zahl
      germanskript.intern.Zahl(cos(zahl.zahl))
    },

    "tangens von der Zahl" to {
      val zahl = umgebung.leseVariable("Zahl")!!.wert as germanskript.intern.Zahl
      germanskript.intern.Zahl(tan(zahl.zahl))
    },

    "randomisiere" to {
      germanskript.intern.Zahl(Random.nextDouble())
    },

    "randomisiere zwischen dem Minimum, dem Maximum" to {
      val min = umgebung.leseVariable("Minimum")!!.wert as germanskript.intern.Zahl
      val max = umgebung.leseVariable("Maximum")!!.wert as germanskript.intern.Zahl
      germanskript.intern.Zahl(Random.nextDouble(min.zahl, max.zahl))
    }
  )

  private fun evaluiereKonvertierung(konvertierung: AST.Satz.Ausdruck.Konvertierung): Wert {
    val wert = evaluiereAusdruck(konvertierung.ausdruck)


    if (wert !is Wert.Objekt) {
      throw Exception("Typprüfer sollte diesen Fall schon überprüfen!")
    }

    wert.typ.definition.konvertierungen[konvertierung.typ.typ!!.name]?.also {
      return durchlaufeAufruf(konvertierung, it.definition, Umgebung(), true, wert, false)
    }

    if (typeOf(wert) == konvertierung.typ.typ!!) {
      return wert
    }

    val fehlerMeldung = "Ungültige Konvertierung!\n" +
        "Die Klasse '${wert}' kann nicht nach '${konvertierung.typ.typ!!}' konvertiert werden."
    return werfeFehler(fehlerMeldung, "KonvertierungsFehler", konvertierung.token)
  }

  private fun werfeFehler(fehlerMeldung: String, fehlerKlassenName: String, token: Token): Wert {
    geworfenerFehler = Wert.Objekt.SkriptObjekt(Typ.Compound.KlassenTyp.Klasse(klassenDefinitionen.getValue(fehlerKlassenName), emptyList()),
        mutableMapOf(
            "FehlerMeldung" to germanskript.intern.Zeichenfolge(fehlerMeldung)
        ))
    geworfenerFehlerToken = token
    flags.add(Flag.FEHLER_GEWORFEN)
    return germanskript.intern.Niemals
  }
  // endregion

  private fun typeOf(wert: Wert): Typ {
    return when (wert) {
      is Wert.Objekt -> wert.typ
      is Wert.Closure -> wert.schnittstelle
      else -> throw Exception("Dieser Fall sollte nie auftreten.")
    }
  }
}

fun main() {
  val interpreter = Interpretierer(File("./iterationen/iter_2/code.gm"))
  interpreter.interpretiere()
}