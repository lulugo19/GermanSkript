import java.text.DecimalFormat
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.pow

private sealed class Wert {
  data class Zeichenfolge(val zeichenfolge: String): Wert() {
    override fun toString(): String = zeichenfolge
  }

  data class Zahl(val zahl: Double): Wert() {
    object Format {
      val dec = DecimalFormat("#,###.##")
    }
    override fun toString(): String = Format.dec.format(zahl)
  }

  data class Boolean(val boolean: kotlin.Boolean): Wert() {
    override fun toString(): String = boolean.toString()
  }

  data class Liste(val elemente: List<Wert>): Wert()
}

private typealias Bereich = HashMap<String, Wert>

private class Umgebung() {
  private val bereiche = Stack<Bereich>()

  fun leseVariable(varName: String): Wert {
    for (bereich in bereiche) {
      if (bereich.containsKey(varName)) {
        return bereich.getValue(varName)
      }
    }
    throw Error("Variable nicht gefunden. Sollte nie ausgeführt werden, weil der Typchecker diesen Fall schon überprüft!")
  }

  fun schreibeVariable(varName: String, wert: Wert) {
    bereiche.peek()!![varName] = wert
  }

  fun überschreibeVariable(varName: String, wert: Wert) {
    val bereich = bereiche.findLast { it.containsKey(varName) }
    if (bereich != null) {
      bereich[varName] = wert
    } else {
      // Fallback
      schreibeVariable(varName, wert)
    }
  }

  fun schreibeRückgabe(wert: Wert) {
    bereiche.firstElement()["@Rückgabe"] = wert
  }

  fun leseRückgabe(): Wert = bereiche.peek().getValue("@Rückgabe")

  fun pushBereich() {
    bereiche.push(Bereich())
  }

  fun popBereich() {
    bereiche.pop()
  }
}

class Interpreter(dateiPfad: String): PipelineComponent(dateiPfad) {
  val typPrüfer = TypPrüfer(dateiPfad)
  val ast = typPrüfer.ast
  val definierer = typPrüfer.definierer
  private val flags = EnumSet.noneOf(Flag::class.java)

  private val stack = Stack<Umgebung>()

  fun interpretiere() {
    typPrüfer.prüfe()
    stack.push(Umgebung())
    interpretiereSätze(ast.sätze)
  }

  enum class Flag {
    SCHLEIFE_ABBRECHEN,
    SCHLEIFE_FORTFAHREN,
  }


  // region Sätze
  private fun interpretiereSätze(sätze: List<AST.Satz>)  {
    stack.peek().pushBereich()
    for (satz in sätze) {
      if (flags.contains(Flag.SCHLEIFE_FORTFAHREN) || flags.contains(Flag.SCHLEIFE_ABBRECHEN)) {
        return
      }
      when (satz) {
        is AST.Satz.VariablenDeklaration -> interpretiereVariablenDeklaration(satz)
        is AST.Satz.FunktionsAufruf -> interpretiereFunktionsAufruf(satz.aufruf)
        is AST.Satz.Zurückgabe -> interpretiereZurückgabe(satz)
        is AST.Satz.Bedingung -> interpretiereBedingung(satz)
        is AST.Satz.SolangeSchleife -> interpretiereSolangeSchleife(satz)
        is AST.Satz.SchleifenKontrolle.Abbrechen -> {
          flags.add(Flag.SCHLEIFE_ABBRECHEN)
          return
        }
        is AST.Satz.SchleifenKontrolle.Fortfahren -> {
          flags.add(Flag.SCHLEIFE_FORTFAHREN)
          return
        }
      }
    }
    stack.peek().popBereich()
  }

  private fun interpretiereFunktionsAufruf(funktionsAufruf: AST.FunktionsAufruf): Wert? {
    val funktionsUmgebung = Umgebung()
    funktionsUmgebung.pushBereich()
    for (argument in funktionsAufruf.argumente) {
      val argumentWert = evaluiereAusdruck(argument.wert?: AST.Ausdruck.Variable(null, argument.name))
      funktionsUmgebung.schreibeVariable(argument.name.nominativ!!, argumentWert)
    }
    stack.push(funktionsUmgebung)
    val funktionsDefinition = definierer.holeFunktionsDefinition(funktionsAufruf)
    if (funktionsDefinition.sätze.isNotEmpty() && funktionsDefinition.sätze.first() is AST.Satz.Intern) {
      interneFunktionen.getValue(funktionsAufruf.vollerName!!)()
    } else {
      interpretiereSätze(funktionsDefinition.sätze)
    }
    return if (funktionsDefinition.rückgabeTyp != null) {
      stack.pop().leseRückgabe()
    } else {
      stack.pop().let { null }
    }
  }

  private fun interpretiereVariablenDeklaration(deklaration: AST.Satz.VariablenDeklaration) {
    val wert = evaluiereAusdruck(deklaration.ausdruck)
    if (deklaration.artikel.typ is TokenTyp.ARTIKEL.BESTIMMT) {
      stack.peek().schreibeVariable(deklaration.name.nominativ!!, wert)
    } else {
      stack.peek().überschreibeVariable(deklaration.name.nominativ!!, wert)
    }
  }

  private fun interpretiereZurückgabe(zurückgabe: AST.Satz.Zurückgabe) {
    val wert = evaluiereAusdruck(zurückgabe.ausdruck)
    stack.peek().schreibeRückgabe(wert)
  }

  private fun interpretiereBedingung(bedingung: AST.Satz.Bedingung) {
    for (bedingung in bedingung.bedingungen) {
      if ((evaluiereAusdruck(bedingung.bedingung) as Wert.Boolean).boolean) {
        interpretiereSätze(bedingung.sätze)
        return
      }
    }
    if (bedingung.sonst != null) {
      interpretiereSätze(bedingung.sonst)
    }
  }

  private fun interpretiereSolangeSchleife(schleife: AST.Satz.SolangeSchleife) {
    while (!flags.contains(Flag.SCHLEIFE_ABBRECHEN) && (evaluiereAusdruck(schleife.bedingung) as Wert.Boolean).boolean) {
      flags.remove(Flag.SCHLEIFE_FORTFAHREN)
      interpretiereSätze(schleife.sätze)
    }
    flags.remove(Flag.SCHLEIFE_ABBRECHEN)
  }

  // endregion

  // region Ausdrücke
  private fun evaluiereAusdruck(ausdruck: AST.Ausdruck): Wert {
      return when (ausdruck) {
        is AST.Ausdruck.Zeichenfolge -> Wert.Zeichenfolge(ausdruck.zeichenfolge.typ.zeichenfolge)
        is AST.Ausdruck.Zahl -> Wert.Zahl(ausdruck.zahl.typ.zahl)
        is AST.Ausdruck.Boolean -> Wert.Boolean(ausdruck.boolean.typ.boolean)
        is AST.Ausdruck.Liste -> Wert.Liste(ausdruck.elemente.map(::evaluiereAusdruck))
        is AST.Ausdruck.Variable -> evaluiereVariable(ausdruck)
        is AST.Ausdruck.FunktionsAufruf -> evaluiereFunktionsAufruf(ausdruck.aufruf)
        is AST.Ausdruck.BinärerAusdruck -> evaluiereBinärenAusdruck(ausdruck)
        is AST.Ausdruck.Minus -> evaluiereMinus(ausdruck)
      }
  }

  private fun evaluiereVariable(ausdruck: AST.Ausdruck.Variable): Wert {
    return stack.peek()!!.leseVariable(ausdruck.name.nominativ!!)
  }

  private fun evaluiereFunktionsAufruf(aufruf: AST.FunktionsAufruf): Wert = interpretiereFunktionsAufruf(aufruf)!!

  private fun evaluiereBinärenAusdruck(ausdruck: AST.Ausdruck.BinärerAusdruck): Wert {
    val links = evaluiereAusdruck(ausdruck.links)
    val rechts = evaluiereAusdruck(ausdruck.rechts)
    val operator = ausdruck.operator.typ.operator
    return when (links) {
      is Wert.Zeichenfolge -> zeichenFolgenOperation(operator, links, rechts as Wert.Zeichenfolge)
      is Wert.Zahl -> zahlOperation(operator, links, rechts as Wert.Zahl)
      is Wert.Boolean -> booleanOperation(operator, links, rechts as Wert.Boolean)
      is Wert.Liste -> TODO()
    }
  }

  private fun zeichenFolgenOperation(operator: Operator, links: Wert.Zeichenfolge, rechts: Wert.Zeichenfolge): Wert {
    return when (operator) {
      Operator.GLEICH -> Wert.Boolean(links.zeichenfolge == rechts.zeichenfolge)
      Operator.UNGLEICH -> Wert.Boolean(links.zeichenfolge != rechts.zeichenfolge)
      Operator.GRÖßER -> Wert.Boolean(links.zeichenfolge > rechts.zeichenfolge)
      Operator.KLEINER -> Wert.Boolean(links.zeichenfolge < rechts.zeichenfolge)
      Operator.GRÖSSER_GLEICH -> Wert.Boolean(links.zeichenfolge >= rechts.zeichenfolge)
      Operator.KLEINER_GLEICH -> Wert.Boolean(links.zeichenfolge <= rechts.zeichenfolge)
      Operator.PLUS -> Wert.Zeichenfolge(links.zeichenfolge + rechts.zeichenfolge)
      else -> throw Error("Operator $operator ist für den Typen Zeichenfolge nicht definiert.")
    }
  }

  private fun zahlOperation(operator: Operator, links: Wert.Zahl, rechts: Wert.Zahl): Wert {
    return when(operator) {
      Operator.GLEICH -> Wert.Boolean(links.zahl == rechts.zahl)
      Operator.UNGLEICH -> Wert.Boolean(links.zahl != rechts.zahl)
      Operator.GRÖßER -> Wert.Boolean(links.zahl > rechts.zahl)
      Operator.KLEINER -> Wert.Boolean(links.zahl < rechts.zahl)
      Operator.GRÖSSER_GLEICH -> Wert.Boolean(links.zahl >= rechts.zahl)
      Operator.KLEINER_GLEICH -> Wert.Boolean(links.zahl <= rechts.zahl)
      Operator.PLUS -> Wert.Zahl(links.zahl + rechts.zahl)
      Operator.MINUS -> Wert.Zahl(links.zahl - rechts.zahl)
      Operator.MAL -> Wert.Zahl(links.zahl * rechts.zahl)
      Operator.GETEILT -> Wert.Zahl(links.zahl / rechts.zahl)
      Operator.MODULO -> Wert.Zahl(links.zahl % rechts.zahl)
      Operator.HOCH -> Wert.Zahl(links.zahl.pow(rechts.zahl))
      else -> throw Error("Operator $operator ist für den Typen Zahl nicht definiert.")
    }
  }

  private fun booleanOperation(operator: Operator, links: Wert.Boolean, rechts: Wert.Boolean): Wert {
    return when (operator) {
      Operator.ODER -> Wert.Boolean(links.boolean || rechts.boolean)
      Operator.UND -> Wert.Boolean(links.boolean && rechts.boolean)
      Operator.GLEICH -> Wert.Boolean(links.boolean == rechts.boolean)
      Operator.UNGLEICH -> Wert.Boolean(links.boolean != rechts.boolean)
      else -> throw Error("Operator $operator ist für den Typen Boolean nicht definiert.")
    }
  }

  private fun listenOperation(operator: Operator, links: Wert.Liste, rechts: Wert.Liste): Wert {
    return when (operator) {
      Operator.PLUS -> Wert.Liste(links.elemente + rechts.elemente)
      else -> throw Error("Operator $operator ist für den Typen Liste nicht definiert.")
    }
  }

  private fun evaluiereMinus(ausdruck: AST.Ausdruck.Minus): Wert.Zahl {
    val ausdruck = evaluiereAusdruck(ausdruck.ausdruck) as Wert.Zahl
    return Wert.Zahl(-ausdruck.zahl)
  }
  // endregion

  // region
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
      }
  )
  // endregion
}

fun main() {
  val interpreter = Interpreter("./iterationen/iter_1/code.gms")
  interpreter.interpretiere()
}


