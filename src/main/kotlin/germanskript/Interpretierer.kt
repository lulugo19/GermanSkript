package germanskript

import java.io.File
import java.text.ParseException
import java.util.*
import kotlin.NoSuchElementException
import kotlin.collections.HashMap
import kotlin.math.*
import kotlin.random.Random

class Interpretierer(startDatei: File): ProgrammDurchlaufer<Wert>(startDatei) {
  val typPrüfer = TypPrüfer(startDatei)

  override val definierer = typPrüfer.definierer
  val ast: AST.Programm = typPrüfer.ast

  private var rückgabeWert: Wert? = null
  private val flags = EnumSet.noneOf(Flag::class.java)
  private val aufrufStapel = AufrufStapel()
  private val listenKlassenDefinition get() = typPrüfer.typisierer.listenKlassenDefinition

  override val umgebung: Umgebung<Wert> get() = aufrufStapel.top().umgebung

  private val klassenDefinitionen = HashMap<String, AST.Definition.Typdefinition.Klasse>()

  fun interpretiere() {
    typPrüfer.prüfe()
    initKlassenDefinitionen()
    try {
      aufrufStapel.push(ast, Umgebung())
      durchlaufeBereich(ast.programm, true)
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

  private enum class Flag {
    SCHLEIFE_ABBRECHEN,
    SCHLEIFE_FORTFAHREN,
    ZURÜCK,
  }

  private class AufrufStapelElement(val aufruf: AST.IAufruf, val objekt: Wert?, val umgebung: Umgebung<Wert>)

  object Konstanten {
     const val CALL_STACK_OUTPUT_LIMIT = 50
  }

  private inner class AufrufStapel {
    private val stapel = Stack<AufrufStapelElement>()

    fun top(): AufrufStapelElement = stapel.peek()
    fun push(funktionsAufruf: AST.IAufruf, neueUmgebung: Umgebung<Wert>, aufrufObjekt: Wert.Objekt? = null) {
      val objekt = when (funktionsAufruf) {
        is AST.Programm -> null
        is AST.Funktion -> when (funktionsAufruf.aufrufTyp) {
          FunktionsAufrufTyp.FUNKTIONS_AUFRUF -> null
          FunktionsAufrufTyp.METHODEN_SELBST_AUFRUF -> top().objekt
          FunktionsAufrufTyp.METHODEN_BLOCK_AUFRUF -> top().umgebung.holeMethodenBlockObjekt()
          FunktionsAufrufTyp.METHODEN_OBJEKT_AUFRUF -> evaluiereAusdruck(funktionsAufruf.objekt!!.ausdruck)
        }
        is AST.Ausdruck.ObjektInstanziierung,
        is AST.Ausdruck.Konvertierung,
        is AST.Ausdruck.IEigenschaftsZugriff  -> aufrufObjekt
        else -> throw Exception("Unbehandelter Funktionsaufruftyp")
      }
      stapel.push(AufrufStapelElement(funktionsAufruf, objekt, neueUmgebung))
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
      val zeichenfolge = "'${aufruf.vollerName}' in ${aufruf.token.position}"
      /*
      if (element.objekt is Wert.Objekt) {
        val klassenName = element.objekt.typ.definition.name.hauptWort
        zeichenfolge = "'für $klassenName: ${aufruf.vollerName}' in ${aufruf.token.position}"
      }
       */

      return zeichenfolge
    }
  }

  private fun initKlassenDefinitionen() {
    for (klassenString in preloadedKlassenDefinitionen) {
      val definition = typPrüfer.typisierer.definierer.holeTypDefinition(klassenString)
          as AST.Definition.Typdefinition.Klasse

      klassenDefinitionen[klassenString] = definition
    }
  }

  override fun sollSätzeAbbrechen(): Boolean {
    return flags.contains(Flag.SCHLEIFE_FORTFAHREN) ||
        flags.contains(Flag.SCHLEIFE_ABBRECHEN) ||
        flags.contains(Flag.ZURÜCK)
  }

  // region Sätze
  private fun durchlaufeBedingung(bedingung: AST.Satz.BedingungsTerm): Boolean {
      return if ((evaluiereAusdruck(bedingung.bedingung) as Wert.Primitiv.Boolean).boolean) {
        durchlaufeBereich(bedingung.bereich, true)
        true
      } else {
        false
      }
  }

  override fun starteBereich(bereich: AST.Satz.Bereich) {
    // hier muss nichts gemacht werden...
  }

  override fun beendeBereich(bereich: AST.Satz.Bereich) {
    // hier muss nichts gemacht werden...
  }

  override fun durchlaufeVariablenDeklaration(deklaration: AST.Satz.VariablenDeklaration) {
    if (deklaration.istEigenschaftsNeuZuweisung || deklaration.istEigenschaft) {
      // weise Eigenschaft neu zu
      (aufrufStapel.top().objekt!! as Wert.Objekt).setzeEigenschaft(deklaration.name, evaluiereAusdruck(deklaration.wert))
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
  }

  private fun durchlaufeAufruf(aufruf: AST.IAufruf, bereich: AST.Satz.Bereich, umgebung: Umgebung<Wert>, neuerBereich: Boolean, objekt: Wert.Objekt?): Wert? {
    aufrufStapel.push(aufruf, umgebung, objekt)
    durchlaufeBereich(bereich, neuerBereich)
    flags.remove(Flag.ZURÜCK)
    aufrufStapel.pop()
    return rückgabeWert
  }

  /**
   * Bei Closures werden die Parameternamen bei generischen Parametern mit dem Namen des eingesetzen Typen ersetzt.
   */
  private fun holeParameterNamenFürClosure(objekt: Wert.Closure): List<AST.Nomen> {
    val typArgumente = objekt.schnittstelle.typArgumente
    val signatur = objekt.schnittstelle.definition.methodenSignaturen[0]
    return signatur.parameter.map { param ->
      when (val typ = param.typKnoten.typ!!) {
        is Typ.Generic ->
          if (param.typIstName) param.name.tauscheHauptWortAus(typArgumente[typ.index].name.deklination!!) else param.name
        else -> param.name
      }
    }
  }

  override fun durchlaufeFunktionsAufruf(funktionsAufruf: AST.Funktion, istAusdruck: Boolean): Wert? {
    rückgabeWert = null
    var funktionsUmgebung = Umgebung<Wert>()
    val (körper, parameterNamen) = if (funktionsAufruf.funktionsDefinition != null) {
      val definition = funktionsAufruf.funktionsDefinition!!
      Pair(definition.körper, definition.signatur.parameter.map{it.name})
    } else {
      // dynamisches Binden von Methoden
      val objekt = umgebung.holeMethodenBlockObjekt()
      when (objekt)
      {
        is Wert.Objekt -> {
          val methode = objekt.typ.definition.methoden.getValue(funktionsAufruf.vollerName!!)
          funktionsAufruf.vollerName = "für ${objekt.typ.definition.name.nominativ}: ${methode.funktion.signatur.vollerName}"
          val signatur = methode.funktion.signatur
          Pair(methode.funktion.körper, signatur.parameter.map {it.name})
        }
        is Wert.Closure -> {
          funktionsUmgebung = objekt.umgebung
          Pair(objekt.körper, holeParameterNamenFürClosure(objekt))
        }
        else -> throw Exception("Dieser Fall sollte nie eintreten.")
      }
    }
    funktionsUmgebung.pushBereich()
    val j = if (funktionsAufruf.aufrufTyp == FunktionsAufrufTyp.METHODEN_OBJEKT_AUFRUF) 1 else 0
    val argumente = funktionsAufruf.argumente
    for (i in parameterNamen.indices) {
      funktionsUmgebung.schreibeVariable(parameterNamen[i], evaluiereAusdruck(argumente[i+j].ausdruck), false)
    }

    return durchlaufeAufruf(funktionsAufruf, körper, funktionsUmgebung, false, null)
  }

  override fun durchlaufeZurückgabe(zurückgabe: AST.Satz.Zurückgabe) {
    if (zurückgabe.ausdruck != null) {
      rückgabeWert = evaluiereAusdruck(zurückgabe.ausdruck!!)
    }
    flags.add(Flag.ZURÜCK)
  }

  override fun durchlaufeBedingungsSatz(bedingungsSatz: AST.Satz.Bedingung) {
    val inBedingung = bedingungsSatz.bedingungen.any(::durchlaufeBedingung)

    if (!inBedingung && bedingungsSatz.sonst != null ) {
      durchlaufeBereich(bedingungsSatz.sonst!!, true)
    }
  }

  override fun durchlaufeSolangeSchleife(schleife: AST.Satz.SolangeSchleife) {
    while (!flags.contains(Flag.SCHLEIFE_ABBRECHEN) && (evaluiereAusdruck(schleife.bedingung.bedingung) as Wert.Primitiv.Boolean).boolean) {
      flags.remove(Flag.SCHLEIFE_FORTFAHREN)
      durchlaufeBereich(schleife.bedingung.bereich, true)
    }
    flags.remove(Flag.SCHLEIFE_ABBRECHEN)
  }

  override fun durchlaufeFürJedeSchleife(schleife: AST.Satz.FürJedeSchleife) {
    if (schleife.reichweite != null) {
      val (anfang, ende) = schleife.reichweite
      val anfangsWert = evaluiereAusdruck(anfang) as Wert.Primitiv.Zahl
      val endWert = evaluiereAusdruck(ende) as Wert.Primitiv.Zahl
      val schrittWeite = if (anfangsWert <= endWert) 1 else -1
      var laufWert = anfangsWert
      while (laufWert.compareTo(endWert) == -schrittWeite) {
        if (!iteriereSchleife(schleife, laufWert)) {
          break
        }
        laufWert += Wert.Primitiv.Zahl(schrittWeite.toDouble())
      }
    } else {
      val liste = if (schleife.liste != null) {
        evaluiereAusdruck(schleife.liste) as Wert.Objekt.Liste
      } else {
        evaluiereVariable(schleife.singular.ganzesWort(Kasus.NOMINATIV, Numerus.PLURAL))!! as Wert.Objekt.Liste
      }
      umgebung.pushBereich()
      for (element in liste.elemente) {
        if (!iteriereSchleife(schleife, element)) {
          break
        }
      }
      umgebung.popBereich()
    }
  }

  private fun iteriereSchleife(schleife: AST.Satz.FürJedeSchleife, element: Wert): Boolean {
    flags.remove(Flag.SCHLEIFE_FORTFAHREN)
    umgebung.überschreibeVariable(schleife.binder, element)
    durchlaufeBereich(schleife.bereich, true)
    if (flags.contains(Flag.SCHLEIFE_ABBRECHEN)) {
      flags.remove(Flag.SCHLEIFE_ABBRECHEN)
      return false
    }
    return true
  }

  override fun durchlaufeVersucheFange(versucheFange: AST.Satz.VersucheFange) {
    try {
      durchlaufeBereich(versucheFange.versuche, true)
    } catch (fehler: GermanSkriptFehler.UnbehandelterFehler) {
      val fehlerObjekt = fehler.fehlerObjekt
      val fehlerTyp = typeOf(fehler.fehlerObjekt)
      val fange = versucheFange.fange.find { fange ->
        typPrüfer.typIstTyp(fehlerTyp, fange.typ.typ!!)
      }
      if (fange != null) {
        umgebung.pushBereich()
        umgebung.schreibeVariable(fange.binder, fehlerObjekt, true)
        durchlaufeBereich(fange.bereich, false)
        umgebung.popBereich()
      } else {
        throw fehler
      }
    }
  }

  override fun durchlaufeWerfe(werfe: AST.Satz.Werfe) {
    val wert = evaluiereAusdruck(werfe.ausdruck)
    val fehlerMeldung = konvertiereZuZeichenfolge(wert).zeichenfolge
    throw GermanSkriptFehler.UnbehandelterFehler(werfe.werfe.toUntyped(), aufrufStapel.toString(), fehlerMeldung, wert)
  }

  override fun durchlaufeIntern(intern: AST.Satz.Intern) = interneFunktionen.getValue(aufrufStapel.top().aufruf.vollerName!!)()


  override fun bevorDurchlaufeMethodenBlock(methodenBlock: AST.Satz.MethodenBlock, blockObjekt: Wert?) {
    // mache nichts hier, das ist eigentlich nur für den Typprüfer gedacht
  }

  override fun durchlaufeAbbrechen() {
    flags.add(Flag.SCHLEIFE_ABBRECHEN)
  }

  override fun durchlaufeFortfahren() {
    flags.add(Flag.SCHLEIFE_FORTFAHREN)
  }
  // endregion

  // region Ausdrücke
  override fun evaluiereZeichenfolge(ausdruck: AST.Ausdruck.Zeichenfolge): Wert {
    return ausdruck.zeichenfolge.typ.zeichenfolge
  }

  override fun evaluiereZahl(ausdruck: AST.Ausdruck.Zahl): Wert {
    return ausdruck.zahl.typ.zahl
  }

  override fun evaluiereBoolean(ausdruck: AST.Ausdruck.Boolean): Wert {
    return ausdruck.boolean.typ.boolean
  }

  override fun evaluiereKonstante(konstante: AST.Ausdruck.Konstante): Wert = evaluiereAusdruck(konstante.wert!!)

  override fun evaluiereListe(ausdruck: AST.Ausdruck.Liste): Wert {
    return Wert.Objekt.Liste(
        Typ.Compound.KlassenTyp.Liste(listenKlassenDefinition, listOf(ausdruck.pluralTyp)),
        ausdruck.elemente.map(::evaluiereAusdruck).toMutableList()
    )
  }

  override fun evaluiereObjektInstanziierung(instanziierung: AST.Ausdruck.ObjektInstanziierung): Wert {
    val eigenschaften = hashMapOf<String, Wert>()
    for (zuweisung in instanziierung.eigenschaftsZuweisungen) {
      eigenschaften[zuweisung.name.nominativ] = evaluiereAusdruck(zuweisung.ausdruck)
    }
    val klassenTyp = (instanziierung.klasse.typ!! as Typ.Compound.KlassenTyp)
    val objekt = Wert.Objekt.SkriptObjekt(klassenTyp, eigenschaften)

    fun führeKonstruktorAus(definition: AST.Definition.Typdefinition.Klasse) {
      // Führe zuerst den Konstruktor der Elternklasse aus
      if (definition.elternKlasse != null) {
        führeKonstruktorAus((definition.elternKlasse.typ!! as Typ.Compound.KlassenTyp).definition)
      }
      durchlaufeAufruf(instanziierung, definition.konstruktor, Umgebung(), true, objekt)
    }

    führeKonstruktorAus(klassenTyp.definition)
    return objekt
  }

  private fun holeEigenschaft(zugriff: AST.Ausdruck.IEigenschaftsZugriff, objekt: Wert.Objekt): Wert {
    return try {
      objekt.holeEigenschaft(zugriff.eigenschaftsName)
    } catch (nichtGefunden: NoSuchElementException) {
      val berechneteEigenschaft = objekt.typ.definition.berechneteEigenschaften.getValue(zugriff.eigenschaftsName.nominativ)
      durchlaufeAufruf(zugriff, berechneteEigenschaft.definition, Umgebung(), true, objekt)!!
    }
  }

  override fun evaluiereEigenschaftsZugriff(eigenschaftsZugriff: AST.Ausdruck.EigenschaftsZugriff): Wert {
    return when (val objekt = evaluiereAusdruck(eigenschaftsZugriff.objekt)) {
      is Wert.Objekt -> holeEigenschaft(eigenschaftsZugriff, objekt)
      is Wert.Primitiv.Zeichenfolge -> Wert.Primitiv.Zahl(objekt.zeichenfolge.length.toDouble())
      else -> throw Exception("Dies sollte nie passieren, weil der Typprüfer diesen Fall schon überprüft")
    }
  }

  override fun evaluiereSelbstEigenschaftsZugriff(eigenschaftsZugriff: AST.Ausdruck.SelbstEigenschaftsZugriff): Wert {
    val objekt = aufrufStapel.top().objekt!! as Wert.Objekt
    return holeEigenschaft(eigenschaftsZugriff, objekt)
  }

  override fun evaluiereMethodenBlockEigenschaftsZugriff(eigenschaftsZugriff: AST.Ausdruck.MethodenBlockEigenschaftsZugriff): Wert {
    val objekt = umgebung.holeMethodenBlockObjekt()!! as Wert.Objekt
    return holeEigenschaft(eigenschaftsZugriff, objekt)
  }

  override fun evaluiereSelbstReferenz(): Wert = aufrufStapel.top().objekt!!

  override  fun evaluiereBinärenAusdruck(ausdruck: AST.Ausdruck.BinärerAusdruck): Wert {
    val links = evaluiereAusdruck(ausdruck.links)
    val rechts = evaluiereAusdruck(ausdruck.rechts)
    val operator = ausdruck.operator.typ.operator

    // Referenzvergleich von Klassen
    if (operator == Operator.GLEICH && links is Wert.Objekt && rechts is Wert.Objekt) {
      return Wert.Primitiv.Boolean(links == rechts)
    }
    return when (links) {
      is Wert.Primitiv.Zeichenfolge -> zeichenFolgenOperation(operator, links, rechts as Wert.Primitiv.Zeichenfolge)
      is Wert.Primitiv.Zahl -> {
        if ((rechts as Wert.Primitiv.Zahl).isZero() && operator == Operator.GETEILT) {
          throw GermanSkriptFehler.LaufzeitFehler(holeErstesTokenVonAusdruck(ausdruck.rechts), aufrufStapel.toString(),
              "Division durch 0. Es kann nicht durch 0 dividiert werden.")
        }
        zahlOperation(operator, links, rechts)
      }
      is Wert.Primitiv.Boolean -> booleanOperation(operator, links, rechts as Wert.Primitiv.Boolean)
      is Wert.Objekt.Liste -> listenOperation(operator, links, rechts as Wert.Objekt.Liste)
      else -> throw Exception("Typprüfer sollte disen Fehler verhindern.")
    }
  }

  companion object {
    val preloadedKlassenDefinitionen = arrayOf("Fehler", "KonvertierungsFehler")

    fun zeichenFolgenOperation(operator: Operator, links: Wert.Primitiv.Zeichenfolge, rechts: Wert.Primitiv.Zeichenfolge): Wert {
      return when (operator) {
        Operator.GLEICH -> Wert.Primitiv.Boolean(links == rechts)
        Operator.UNGLEICH -> Wert.Primitiv.Boolean(links != rechts)
        Operator.GRÖßER -> Wert.Primitiv.Boolean(links > rechts)
        Operator.KLEINER -> Wert.Primitiv.Boolean(links < rechts)
        Operator.GRÖSSER_GLEICH -> Wert.Primitiv.Boolean(links >= rechts)
        Operator.KLEINER_GLEICH -> Wert.Primitiv.Boolean(links <= rechts)
        Operator.PLUS -> Wert.Primitiv.Zeichenfolge(links + rechts)
        else -> throw Exception("Operator $operator ist für den Typen Zeichenfolge nicht definiert.")
      }
    }

    fun zahlOperation(operator: Operator, links: Wert.Primitiv.Zahl, rechts: Wert.Primitiv.Zahl): Wert {
      return when (operator) {
        Operator.GLEICH -> Wert.Primitiv.Boolean(links == rechts)
        Operator.UNGLEICH -> Wert.Primitiv.Boolean(links != rechts)
        Operator.GRÖßER -> Wert.Primitiv.Boolean(links > rechts)
        Operator.KLEINER -> Wert.Primitiv.Boolean(links < rechts)
        Operator.GRÖSSER_GLEICH -> Wert.Primitiv.Boolean(links >= rechts)
        Operator.KLEINER_GLEICH -> Wert.Primitiv.Boolean(links <= rechts)
        Operator.PLUS -> links + rechts
        Operator.MINUS -> links - rechts
        Operator.MAL -> links * rechts
        Operator.GETEILT -> links / rechts
        Operator.MODULO -> links % rechts
        Operator.HOCH -> links.pow(rechts)
        else -> throw Exception("Operator $operator ist für den Typen Zahl nicht definiert.")
      }
    }

    fun booleanOperation(operator: Operator, links: Wert.Primitiv.Boolean, rechts: Wert.Primitiv.Boolean): Wert {
      return when (operator) {
        Operator.ODER -> Wert.Primitiv.Boolean(links.boolean || rechts.boolean)
        Operator.UND -> Wert.Primitiv.Boolean(links.boolean && rechts.boolean)
        Operator.GLEICH -> Wert.Primitiv.Boolean(links.boolean == rechts.boolean)
        Operator.UNGLEICH -> Wert.Primitiv.Boolean(links.boolean != rechts.boolean)
        else -> throw Exception("Operator $operator ist für den Typen Boolean nicht definiert.")
      }
    }
  }

  private fun listenOperation(operator: Operator, links: Wert.Objekt.Liste, rechts: Wert.Objekt.Liste): Wert {
    return when (operator) {
      Operator.PLUS ->links + rechts
      else -> throw Exception("Operator $operator ist für den Typen Liste nicht definiert.")
    }
  }

  override fun evaluiereMinus(minus: AST.Ausdruck.Minus): Wert.Primitiv.Zahl {
    val ausdruck = evaluiereAusdruck(minus.ausdruck) as Wert.Primitiv.Zahl
    return -ausdruck
  }

  override fun evaluiereListenElement(listenElement: AST.Ausdruck.ListenElement): Wert {
    val index = (evaluiereAusdruck(listenElement.index) as Wert.Primitiv.Zahl).toInt()
    val zeichenfolge = evaluiereVariable(listenElement.singular.hauptWort)
    if (zeichenfolge != null && zeichenfolge is Wert.Primitiv.Zeichenfolge) {
      val zeichenfolge = zeichenfolge.zeichenfolge
      if (index >= zeichenfolge.length) {
        throw GermanSkriptFehler.LaufzeitFehler(holeErstesTokenVonAusdruck(listenElement.index),
            aufrufStapel.toString(), "Index außerhalb des Bereichs. Der Index ist $index, doch die Länge der Zeichenfolge ist ${zeichenfolge.length}.\n")
      }
      return Wert.Primitiv.Zeichenfolge(zeichenfolge[index].toString())
    }

    val liste = evaluiereVariable(listenElement.singular.ganzesWort(Kasus.NOMINATIV, Numerus.PLURAL)) as Wert.Objekt.Liste

    if (index >= liste.elemente.size) {
      throw GermanSkriptFehler.LaufzeitFehler(holeErstesTokenVonAusdruck(listenElement.index),
          aufrufStapel.toString(), "Index außerhalb des Bereichs. Der Index ist $index, doch die Länge der Liste ist ${liste.elemente.size}.\n")
    }
    return liste.elemente[index]
  }

  override fun evaluiereClosure(closure: AST.Ausdruck.Closure): Wert {
    return Wert.Closure((closure.schnittstelle.typ as Typ.Compound.Schnittstelle), closure.körper, umgebung)
  }
  // endregion


  private fun durchlaufeInternenSchnittstellenAufruf(wert: Wert, name: String, vararg argumente: Wert): Wert? {
    val (funktionsUmgebung, körper, parameterNamen) = when (wert) {
      is Wert.Objekt -> {
        val methode = wert.typ.definition.methoden.getValue(name)
        Triple(Umgebung<Wert>(), methode.funktion.körper, methode.funktion.signatur.parameter.map { it.name })
      }
      is Wert.Closure -> Triple(wert.umgebung, wert.körper, holeParameterNamenFürClosure(wert))
      else -> throw Exception("Dieser Fall sollte nie auftreten!")
    }

    funktionsUmgebung.pushBereich()
    for (i in parameterNamen.indices) {
      funktionsUmgebung.schreibeVariable(parameterNamen[i], argumente[i], false)
    }
    return durchlaufeAufruf(aufrufStapel.top().aufruf, körper, funktionsUmgebung, false, if (wert is Wert.Objekt) wert else null)
  }

  // region interne Funktionen
  private val interneFunktionen = mapOf<String, () -> (Unit)>(
      "schreibe die Zeichenfolge" to {
        val zeichenfolge = umgebung.leseVariable("Zeichenfolge")!!.wert as Wert.Primitiv.Zeichenfolge
        print(zeichenfolge)
      },

      "schreibe die Zeile" to {
        val zeile = umgebung.leseVariable("Zeile")!!.wert as Wert.Primitiv.Zeichenfolge
        println(zeile)
      },

      "schreibe die Zahl" to {
        val zahl = umgebung.leseVariable("Zahl")!!.wert as Wert.Primitiv.Zahl
        println(zahl)
      },

      "lese" to {
        rückgabeWert = Wert.Primitiv.Zeichenfolge(readLine()!!)
      },

      "runde die Zahl" to {
        val zahl = umgebung.leseVariable("Zahl")!!.wert as Wert.Primitiv.Zahl
        rückgabeWert = Wert.Primitiv.Zahl(round(zahl.zahl))
      },

      "runde die Zahl ab" to {
        val zahl = umgebung.leseVariable("Zahl")!!.wert as Wert.Primitiv.Zahl
        rückgabeWert = Wert.Primitiv.Zahl(floor(zahl.zahl))
      },

      "runde die Zahl auf" to {
        val zahl = umgebung.leseVariable("Zahl")!!.wert as Wert.Primitiv.Zahl
        rückgabeWert = Wert.Primitiv.Zahl(ceil(zahl.zahl))
      },

      "sinus von der Zahl" to {
        val zahl = umgebung.leseVariable("Zahl")!!.wert as Wert.Primitiv.Zahl
        rückgabeWert = Wert.Primitiv.Zahl(sin(zahl.zahl))
      },

      "cosinus von der Zahl" to {
        val zahl = umgebung.leseVariable("Zahl")!!.wert as Wert.Primitiv.Zahl
        rückgabeWert = Wert.Primitiv.Zahl(cos(zahl.zahl))
      },

      "tangens von der Zahl" to {
        val zahl = umgebung.leseVariable("Zahl")!!.wert as Wert.Primitiv.Zahl
        rückgabeWert = Wert.Primitiv.Zahl(tan(zahl.zahl))
      },

      "randomisiere" to {
        rückgabeWert = Wert.Primitiv.Zahl(Random.nextDouble())
      },

      "randomisiere zwischen dem Minimum, dem Maximum" to {
        val min = umgebung.leseVariable("Minimum")!!.wert as Wert.Primitiv.Zahl
        val max = umgebung.leseVariable("Maximum")!!.wert as Wert.Primitiv.Zahl
        rückgabeWert = Wert.Primitiv.Zahl(Random.nextDouble(min.zahl, max.zahl))
      },

      "buchstabiere die Zeichenfolge groß" to {
        val wert = umgebung.leseVariable("Zeichenfolge")!!.wert as Wert.Primitiv.Zeichenfolge
        rückgabeWert = Wert.Primitiv.Zeichenfolge(wert.zeichenfolge.toUpperCase())
      },

      "buchstabiere die Zeichenfolge klein" to {
        val wert = umgebung.leseVariable("Zeichenfolge")!!.wert as Wert.Primitiv.Zeichenfolge
        rückgabeWert = Wert.Primitiv.Zeichenfolge(wert.zeichenfolge.toLowerCase())
      },

      "trenne die Zeichenfolge zwischen dem Separator" to {
        val zeichenfolge = umgebung.leseVariable("Zeichenfolge")!!.wert as Wert.Primitiv.Zeichenfolge
        val separator = umgebung.leseVariable("Separator")!!.wert as Wert.Primitiv.Zeichenfolge
        // TODO: Das hier ist sehr unschön. Dass ich hier einen TypKnoten erstellen muss. Könnte man da vielleicht etwas dran ändern?
        val zeichenfolgeTypParameter = AST.TypKnoten(emptyList(), AST.Nomen(null,
            TypedToken(TokenTyp.BEZEICHNER_GROSS(arrayOf("Zeichenfolge"), ""), "Zeichenfolge", "", Token.Position.Ende, Token.Position.Ende)),
            emptyList()
        )
        zeichenfolgeTypParameter.typ = Typ.Primitiv.Zeichenfolge
        rückgabeWert = Wert.Objekt.Liste(Typ.Compound.KlassenTyp.Liste(listenKlassenDefinition, listOf(zeichenfolgeTypParameter)),
            zeichenfolge.zeichenfolge.split(separator.zeichenfolge).map { Wert.Primitiv.Zeichenfolge(it) }.toMutableList())
      },

      "für Liste: enthält den Typ" to  {
        val liste = aufrufStapel.top().objekt!! as Wert.Objekt.Liste
        val element = umgebung.leseVariable("Typ")!!.wert
        rückgabeWert = Wert.Primitiv.Boolean(liste.elemente.contains(element))
      },

      "für Liste: füge den Typ hinzu" to {
        val liste = aufrufStapel.top().objekt!! as Wert.Objekt.Liste
        val element = umgebung.leseVariable("Typ")!!.wert
        liste.elemente.add(element)
        // Unit weil sonst gemeckert wird, dass keine Unit zurückgegeben wird
        Unit
      },

      "für Liste: entferne an dem Index" to {
        val liste = aufrufStapel.top().objekt!! as Wert.Objekt.Liste
        val index = umgebung.leseVariable("Index")!!.wert as Wert.Primitiv.Zahl
        liste.elemente.removeAt(index.toInt())
        // Unit weil sonst gemeckert wird, dass keine Unit zurückgegeben wird
        Unit
      },

      "für Liste: sortiere mich mit dem Vergleichbaren" to {
        val liste = aufrufStapel.top().objekt!! as Wert.Objekt.Liste
        val typArg = liste.typ.typArgumente[0].name.nominativ
        val vergleichbar = umgebung.leseVariable("Vergleichbare")!!.wert
        liste.elemente.sortWith(kotlin.Comparator { a, b ->
          (durchlaufeInternenSchnittstellenAufruf(vergleichbar, "vergleiche den ${typArg}A mit dem ${typArg}B", a, b)
              as Wert.Primitiv.Zahl).zahl.toInt()
        })
      }
  )

  override fun evaluiereKonvertierung(konvertierung: AST.Ausdruck.Konvertierung): Wert {
    val wert = evaluiereAusdruck(konvertierung.ausdruck)
    if (wert is Wert.Objekt && wert.typ.definition.konvertierungen.containsKey(konvertierung.typ.name.nominativ)) {
      val konvertierungsDefinition = wert.typ.definition.konvertierungen.getValue(konvertierung.typ.name.nominativ)
      return durchlaufeAufruf(konvertierung, konvertierungsDefinition.definition, Umgebung(), true, wert)!!
    }
    return when (konvertierung.typ.typ!!) {
      is Typ.Primitiv.Zahl -> konvertiereZuZahl(konvertierung, wert)
      is Typ.Primitiv.Boolean -> konvertiereZuBoolean(wert)
      is Typ.Primitiv.Zeichenfolge -> konvertiereZuZeichenfolge(wert)
      else -> throw Exception("Typprüfer sollte diesen Fall schon überprüfen!")
    }
  }

  private fun konvertiereZuZahl(konvertierung: AST.Ausdruck.Konvertierung, wert: Wert): Wert.Primitiv.Zahl {
    return when (wert) {
      is Wert.Primitiv.Zahl -> wert
      is Wert.Primitiv.Zeichenfolge -> {
        try {
          Wert.Primitiv.Zahl(wert.zeichenfolge)
        }
        catch (parseFehler: ParseException) {
          val fehlerMeldung = "Die Zeichenfolge '${wert.zeichenfolge}' kann nicht in eine Zahl konvertiert werden."
          val fehlerObjekt = Wert.Objekt.SkriptObjekt(Typ.Compound.KlassenTyp.Klasse(klassenDefinitionen.getValue("KonvertierungsFehler"), emptyList()),
            mutableMapOf(
                "FehlerMeldung" to Wert.Primitiv.Zeichenfolge(fehlerMeldung)
            ))
          throw GermanSkriptFehler.UnbehandelterFehler(konvertierung.token, aufrufStapel.toString(), fehlerMeldung, fehlerObjekt)
        }
      }
      is Wert.Primitiv.Boolean -> Wert.Primitiv.Zahl(if (wert.boolean) 1.0 else 0.0)
      else -> throw Exception("Typ-Prüfer sollte dies schon überprüfen!")
    }
  }

  private fun konvertiereZuZeichenfolge(wert: Wert): Wert.Primitiv.Zeichenfolge {
    return when (wert) {
      is Wert.Primitiv.Zeichenfolge -> wert
      is Wert.Primitiv.Boolean -> Wert.Primitiv.Zeichenfolge(if (wert.boolean) "wahr" else "falsch")
      else -> Wert.Primitiv.Zeichenfolge(wert.toString())
    }
  }

  private fun konvertiereZuBoolean(wert: Wert): Wert.Primitiv.Boolean {
    return when (wert) {
      is Wert.Primitiv.Boolean -> wert
      is Wert.Primitiv.Zeichenfolge -> Wert.Primitiv.Boolean(wert.zeichenfolge.isNotEmpty())
      is Wert.Primitiv.Zahl -> Wert.Primitiv.Boolean(!wert.isZero())
      else -> throw Exception("Typ-Prüfer sollte dies schon überprüfen!")
    }
  }
  // endregion

  private fun typeOf(wert: Wert): Typ {
    return when (wert) {
      is Wert.Primitiv.Zeichenfolge -> Typ.Primitiv.Zeichenfolge
      is Wert.Primitiv.Zahl -> Typ.Primitiv.Zahl
      is Wert.Primitiv.Boolean -> Typ.Primitiv.Boolean
      is Wert.Objekt -> wert.typ
      is Wert.Closure -> wert.schnittstelle
    }
  }
}

fun main() {
  val interpreter = Interpretierer(File("./iterationen/iter_2/code.gm"))
  interpreter.interpretiere()
}