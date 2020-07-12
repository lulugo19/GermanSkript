import java.text.ParseException
import java.util.*

data class AufrufStapelElement(val funktionsAufruf: AST.FunktionsAufruf, val objekt: Wert.Objekt?)

class AufrufStapel {
  private object Static {
    const val CALL_STACK_OUTPUT_LIMIT = 50
  }

  private val stapel = Stack<AufrufStapelElement>()

  fun top(): AufrufStapelElement = stapel.peek()
  fun push(funktionsAufruf: AST.FunktionsAufruf, objekt: Wert.Objekt?): Unit =
      stapel.push(AufrufStapelElement(funktionsAufruf, objekt)).let { Unit }
  fun pop(): AufrufStapelElement = stapel.pop()

  override fun toString(): String {
    if (stapel.isEmpty()) {
      return ""
    }
    return "Aufrufstapel:\n"+ stapel.reversed().joinToString(
        "\n",
        "\t",
        "",
        Static.CALL_STACK_OUTPUT_LIMIT,
        "...",
        ::aufrufStapelElementToSting
    )
  }

  private fun aufrufStapelElementToSting(element: AufrufStapelElement): String {
    val funktionsAufruf = element.funktionsAufruf
    val token = funktionsAufruf.verb
    var zeichenfolge = "${funktionsAufruf.vollerName} in ${token.position}"
    if (element.objekt != null) {
      val klassenName = element.objekt.klassenDefinition.name.nominativ!!
      zeichenfolge = "für $klassenName: $zeichenfolge"
    }

    return zeichenfolge
  }
}

class Interpreter(dateiPfad: String): ProgrammDurchlaufer<Wert, Wert.Objekt>(dateiPfad) {
  val typPrüfer = TypPrüfer(dateiPfad)

  override val definierer = typPrüfer.definierer
  override val ast: AST.Programm = typPrüfer.ast

  private var rückgabeWert: Wert? = null
  private val flags = EnumSet.noneOf(Flag::class.java)
  private val aufrufStapel = AufrufStapel()

  fun interpretiere() {
    typPrüfer.prüfe()
    try {
      durchlaufe(ast.sätze, Umgebung(), true)
    } catch (stackOverflow: StackOverflowError) {
      throw GermanScriptFehler.LaufzeitFehler(
          aufrufStapel.top().funktionsAufruf.verb.toUntyped(),
          aufrufStapel,
          "Stack Overflow")
    }
  }

  enum class Flag {
    SCHLEIFE_ABBRECHEN,
    SCHLEIFE_FORTFAHREN,
  }

  override fun sollSätzeAbbrechen(): Boolean {
    return flags.contains(Flag.SCHLEIFE_FORTFAHREN) || flags.contains(Flag.SCHLEIFE_ABBRECHEN)
  }

  // region Sätze
  private fun durchlaufeBedingung(bedingung: AST.Satz.BedingungsTerm): Boolean {
      return if ((evaluiereAusdruck(bedingung.bedingung) as Wert.Boolean).boolean) {
        durchlaufeSätze(bedingung.sätze, true)
        true
      } else {
        false
      }
  }

  override fun durchlaufeMethodenOderFunktionsAufruf(objekt: Wert?, funktionsAufruf: AST.FunktionsAufruf, funktionsDefinition: AST.Definition.FunktionOderMethode.Funktion, istAusdruck: Boolean): Wert? {
    val funktionsUmgebung = Umgebung<Wert>()
    funktionsUmgebung.pushBereich()
    for (argument in funktionsAufruf.argumente) {
      val argumentWert = evaluiereAusdruck(argument.wert)
      funktionsUmgebung.schreibeVariable(argument.name, argumentWert)
    }
    aufrufStapel.push(funktionsAufruf, objekt?.let { it as Wert.Objekt })
    stack.push(funktionsUmgebung)
    rückgabeWert = null
    if (funktionsDefinition.sätze.isNotEmpty() && funktionsDefinition.sätze.first() is AST.Satz.Intern) {
      interneFunktionen.getValue(funktionsAufruf.vollerName!!)()
    } else {
      durchlaufeSätze(funktionsDefinition.sätze, false)
    }
    stack.pop()
    aufrufStapel.pop()
    return funktionsDefinition.rückgabeTyp?.let { rückgabeWert }
  }

  override fun durchlaufeZurückgabe(zurückgabe: AST.Satz.Zurückgabe) {
    val wert = evaluiereAusdruck(zurückgabe.ausdruck)
    rückgabeWert = wert
  }

  override fun durchlaufeBedingungsSatz(bedingungsSatz: AST.Satz.Bedingung) {
    val inBedingung = bedingungsSatz.bedingungen.any(::durchlaufeBedingung)

    if (!inBedingung && bedingungsSatz.sonst != null ) {
      durchlaufeSätze(bedingungsSatz.sonst, true)
    }
  }

  override fun durchlaufeSolangeSchleife(schleife: AST.Satz.SolangeSchleife) {
    while (!flags.contains(Flag.SCHLEIFE_ABBRECHEN) && (evaluiereAusdruck(schleife.bedingung.bedingung) as Wert.Boolean).boolean) {
      flags.remove(Flag.SCHLEIFE_FORTFAHREN)
      durchlaufeSätze(schleife.bedingung.sätze, true)
    }
    flags.remove(Flag.SCHLEIFE_ABBRECHEN)
  }

  override fun durchlaufeFürJedeSchleife(schleife: AST.Satz.FürJedeSchleife) {
    val liste = if (schleife.liste != null)  {
      evaluiereAusdruck(schleife.liste) as Wert.Liste
    } else {
      evaluiereVariable(schleife.singular!!.nominativPlural!!)!! as Wert.Liste
    }
    stack.peek().pushBereich()
    for (element in liste.elemente) {
      flags.remove(Flag.SCHLEIFE_FORTFAHREN)
      stack.peek().überschreibeVariable(schleife.binder, element)
      durchlaufeSätze(schleife.sätze, false)
      if (flags.contains(Flag.SCHLEIFE_ABBRECHEN)) {
        flags.remove(Flag.SCHLEIFE_ABBRECHEN)
        break
      }
    }
    stack.peek().popBereich()
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

  override fun evaluiereListe(ausdruck: AST.Ausdruck.Liste): Wert {
    return Wert.Liste(ausdruck.elemente.map(::evaluiereAusdruck))
  }

  override fun evaluiereObjektInstanziierung(instanziierung: AST.Ausdruck.ObjektInstanziierung): Wert {
    val eigenschaften = hashMapOf<String, Wert>()
    for (zuweisung in instanziierung.eigenschaftsZuweisungen) {
      eigenschaften[zuweisung.name.nominativ!!] = evaluiereAusdruck(zuweisung.wert)
    }
    val klassenDefinition = (instanziierung.klasse.typ!! as Typ.Klasse).klassenDefinition
    return Wert.Objekt(klassenDefinition, eigenschaften)
  }

  override fun evaluiereEigenschaftsZugriff(eigenschaftsZugriff: AST.Ausdruck.EigenschaftsZugriff): Wert {
    val objekt = evaluiereAusdruck(eigenschaftsZugriff.objekt) as Wert.Objekt
    return objekt.eigenschaften.getValue(eigenschaftsZugriff.eigenschaftsName.nominativ!!)
  }

  override fun evaluiereSelbstEigenschaftsZugriff(eigenschaftsZugriff: AST.Ausdruck.SelbstEigenschaftsZugriff): Wert {
    val objekt = aufrufStapel.top().objekt!!
    return objekt.eigenschaften.getValue(eigenschaftsZugriff.eigenschaftsName.nominativ!!)
  }

  override fun evaluiereMethodenBlockEigenschaftsZugriff(eigenschaftsZugriff: AST.Ausdruck.MethodenBlockEigenschaftsZugriff): Wert {
    val objekt = stack.peek().holeMethodenBlockObjekt()!! as Wert.Objekt
    return objekt.eigenschaften.getValue(eigenschaftsZugriff.eigenschaftsName.nominativ!!)
  }

  override  fun evaluiereBinärenAusdruck(ausdruck: AST.Ausdruck.BinärerAusdruck): Wert {
    val links = evaluiereAusdruck(ausdruck.links)
    val rechts = evaluiereAusdruck(ausdruck.rechts)
    val operator = ausdruck.operator.typ.operator
    return when (links) {
      is Wert.Zeichenfolge -> zeichenFolgenOperation(operator, links, rechts as Wert.Zeichenfolge)
      is Wert.Zahl -> {
        if ((rechts as Wert.Zahl).isZero() && operator == Operator.GETEILT) {
          throw GermanScriptFehler.LaufzeitFehler(holeErstesTokenVonAusdruck(ausdruck.rechts), aufrufStapel,
            "Division durch 0. Es kann nicht durch 0 dividiert werden.")
        }
        zahlOperation(operator, links, rechts)
      }
      is Wert.Boolean -> booleanOperation(operator, links, rechts as Wert.Boolean)
      is Wert.Liste -> listenOperation(operator, links, rechts as Wert.Liste)
      else -> throw Exception("Typprüfer sollte disen Fehler verhindern.")
    }
  }

  private fun zeichenFolgenOperation(operator: Operator, links: Wert.Zeichenfolge, rechts: Wert.Zeichenfolge): Wert {
    return when (operator) {
      Operator.GLEICH -> Wert.Boolean(links == rechts)
      Operator.UNGLEICH -> Wert.Boolean(links != rechts)
      Operator.GRÖßER -> Wert.Boolean(links > rechts)
      Operator.KLEINER -> Wert.Boolean(links < rechts)
      Operator.GRÖSSER_GLEICH -> Wert.Boolean(links >= rechts)
      Operator.KLEINER_GLEICH -> Wert.Boolean(links <= rechts)
      Operator.PLUS -> Wert.Zeichenfolge(links + rechts)
      else -> throw Exception("Operator $operator ist für den Typen Zeichenfolge nicht definiert.")
    }
  }

  private fun zahlOperation(operator: Operator, links: Wert.Zahl, rechts: Wert.Zahl): Wert {
    return when(operator) {
      Operator.GLEICH -> Wert.Boolean(links == rechts)
      Operator.UNGLEICH -> Wert.Boolean(links != rechts)
      Operator.GRÖßER -> Wert.Boolean(links > rechts)
      Operator.KLEINER -> Wert.Boolean(links < rechts)
      Operator.GRÖSSER_GLEICH -> Wert.Boolean(links >= rechts)
      Operator.KLEINER_GLEICH -> Wert.Boolean(links <= rechts)
      Operator.PLUS -> links + rechts
      Operator.MINUS -> links - rechts
      Operator.MAL -> links * rechts
      Operator.GETEILT -> links / rechts
      Operator.MODULO -> links % rechts
      Operator.HOCH -> links.pow(rechts)
      else -> throw Exception("Operator $operator ist für den Typen Zahl nicht definiert.")
    }
  }

  private fun booleanOperation(operator: Operator, links: Wert.Boolean, rechts: Wert.Boolean): Wert {
    return when (operator) {
      Operator.ODER -> Wert.Boolean(links.boolean || rechts.boolean)
      Operator.UND -> Wert.Boolean(links.boolean && rechts.boolean)
      Operator.GLEICH -> Wert.Boolean(links.boolean == rechts.boolean)
      Operator.UNGLEICH -> Wert.Boolean(links.boolean != rechts.boolean)
      else -> throw Exception("Operator $operator ist für den Typen Boolean nicht definiert.")
    }
  }

  private fun listenOperation(operator: Operator, links: Wert.Liste, rechts: Wert.Liste): Wert {
    return when (operator) {
      Operator.PLUS ->links + rechts
      else -> throw Exception("Operator $operator ist für den Typen Liste nicht definiert.")
    }
  }

  override fun evaluiereMinus(minus: AST.Ausdruck.Minus): Wert.Zahl {
    val ausdruck = evaluiereAusdruck(minus.ausdruck) as Wert.Zahl
    return -ausdruck
  }

  override fun evaluiereListenElement(listenElement: AST.Ausdruck.ListenElement): Wert {
    val liste = evaluiereVariable(listenElement.singular.nominativPlural!!) as Wert.Liste
    val index = (evaluiereAusdruck(listenElement.index) as Wert.Zahl).toInt()
    if (index >= liste.elemente.size) {
      throw GermanScriptFehler.LaufzeitFehler(holeErstesTokenVonAusdruck(listenElement.index),
        aufrufStapel,"Index außerhalb des Bereichs. Der Index ist $index, doch die Länge der Liste ist ${liste.elemente.size}.\n")
    }
    return liste.elemente[index]
  }
  // endregion

  // region interne Funktionen
  private val interneFunktionen = mapOf<String, () -> (Unit)>(
      "schreibe die Zeichenfolge" to {
        val zeichenfolge = stack.peek().leseVariable("Zeichenfolge") as Wert.Zeichenfolge
        print(zeichenfolge)
      },

      "schreibe die Zeile" to {
        val zeile = stack.peek().leseVariable("Zeile") as Wert.Zeichenfolge
        println(zeile)
      },

      "schreibe die Zahl" to {
        val zahl = stack.peek().leseVariable("Zahl") as Wert.Zahl
        println(zahl)
      },

      "lese" to{
        rückgabeWert = Wert.Zeichenfolge(readLine()!!)
      }
  )

  override fun evaluiereKonvertierung(konvertierung: AST.Ausdruck.Konvertierung): Wert {
    val wert = evaluiereAusdruck(konvertierung.ausdruck)
    return when (konvertierung.typ.typ!!) {
      is Typ.Zahl -> konvertiereZuZahl(konvertierung, wert)
      is Typ.Boolean -> konvertiereZuBoolean(konvertierung, wert)
      is Typ.Zeichenfolge -> konvertiereZuZeichenfolge(konvertierung, wert)
      else -> throw Exception("Typprüfer sollte diesen Fall schon überprüfen!")
    }
  }

  private fun konvertiereZuZahl(konvertierung: AST.Ausdruck.Konvertierung, wert: Wert): Wert.Zahl {
    return when (wert) {
      is Wert.Zeichenfolge -> {
        try {
          Wert.Zahl(wert.zeichenfolge)
        }
        catch (parseFehler: ParseException) {
          throw GermanScriptFehler.LaufzeitFehler(konvertierung.typ.name.bezeichner.toUntyped(), aufrufStapel,
              "Die Zeichenfolge '${wert.zeichenfolge}' kann nicht in eine Zahl konvertiert werden.")
        }
      }
      is Wert.Boolean -> Wert.Zahl(if (wert.boolean) 1.0 else 0.0)
      else -> throw Exception("Typ-Prüfer sollte dies schon überprüfen!")
    }
  }

  private fun konvertiereZuZeichenfolge(konvertierung: AST.Ausdruck.Konvertierung, wert: Wert): Wert.Zeichenfolge {
    return when (wert) {
      is Wert.Zahl -> Wert.Zeichenfolge(wert.toString())
      is Wert.Boolean -> Wert.Zeichenfolge(if(wert.boolean) "wahr" else "falsch")
      else -> throw Exception("Typ-Prüfer sollte dies schon überprüfen!")
    }
  }

  private fun konvertiereZuBoolean(konvertierung: AST.Ausdruck.Konvertierung, wert: Wert): Wert.Boolean {
    return when (wert) {
      is Wert.Zeichenfolge -> Wert.Boolean(wert.zeichenfolge.isNotEmpty())
      is Wert.Zahl -> Wert.Boolean(!wert.isZero())
      else -> throw Exception("Typ-Prüfer sollte dies schon überprüfen!")
    }
  }
  // endregion
}

fun main() {
  val interpreter = Interpreter("./iterationen/iter_2/code.gms")
  try {
    interpreter.interpretiere()
  } catch (fehler: GermanScriptFehler) {
    // Anstatt zu werfen gebe Fehler später einfach aus
    //System.err.println(fehler.message!!)
    throw fehler
  }
}


