import java.util.*

class Interpreter(dateiPfad: String): ProgrammDurchlaufer<Wert>(dateiPfad) {
  val typPrüfer = TypPrüfer(dateiPfad)
  val definierer = typPrüfer.definierer
  override val ast: AST.Programm = typPrüfer.ast

  private var rückgabeWert: Wert? = null
  private val flags = EnumSet.noneOf(Flag::class.java)

  fun interpretiere() {
    typPrüfer.prüfe()
    durchlaufe(ast.sätze, Umgebung(), true)
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

  override fun durchlaufeFunktionsAufruf(funktionsAufruf: AST.FunktionsAufruf, istAusdruck: Boolean): Wert? {
    val funktionsUmgebung = Umgebung<Wert>()
    funktionsUmgebung.pushBereich()
    for (argument in funktionsAufruf.argumente) {
      val argumentWert = evaluiereAusdruck(argument.wert)
      funktionsUmgebung.schreibeVariable(argument.name, argumentWert)
    }
    stack.push(funktionsUmgebung)
    val funktionsDefinition = definierer.holeFunktionsDefinition(funktionsAufruf)
    if (funktionsDefinition.sätze.isNotEmpty() && funktionsDefinition.sätze.first() is AST.Satz.Intern) {
      interneFunktionen.getValue(funktionsAufruf.vollerName!!)()
    } else {
      durchlaufeSätze(funktionsDefinition.sätze, false)
    }
    stack.pop()
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

  override  fun evaluiereBinärenAusdruck(ausdruck: AST.Ausdruck.BinärerAusdruck): Wert {
    val links = evaluiereAusdruck(ausdruck.links)
    val rechts = evaluiereAusdruck(ausdruck.rechts)
    val operator = ausdruck.operator.typ.operator
    return when (links) {
      is Wert.Zeichenfolge -> zeichenFolgenOperation(operator, links, rechts as Wert.Zeichenfolge)
      is Wert.Zahl -> zahlOperation(operator, links, rechts as Wert.Zahl)
      is Wert.Boolean -> booleanOperation(operator, links, rechts as Wert.Boolean)
      is Wert.Liste -> listenOperation(operator, links, rechts as Wert.Liste)
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
      else -> throw Error("Operator $operator ist für den Typen Zeichenfolge nicht definiert.")
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

  override fun evaluiereMinus(minus: AST.Ausdruck.Minus): Wert.Zahl {
    val ausdruck = evaluiereAusdruck(minus.ausdruck) as Wert.Zahl
    return -ausdruck
  }

  override fun evaluiereListenElement(listenElement: AST.Ausdruck.ListenElement): Wert {
    val liste = evaluiereVariable(listenElement.singular.nominativPlural!!) as Wert.Liste
    val index = evaluiereAusdruck(listenElement.index) as Wert.Zahl
    return liste.elemente[index.toInt()]
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

  val konvertierungsTabelle = mapOf<Typ, (Wert)-> Wert>(
          Typ.Zahl to {
            wert -> when (wert){
            is Wert.Zeichenfolge -> Wert.Zahl(wert.wert)
            is Wert.Boolean -> {
              if (wert.boolean){
                Wert.Zahl("1")
              }else{
                Wert.Zahl("0")
              }
            }
            else -> wert
          }
          },

          Typ.Zeichenfolge to {
            wert -> when(wert){
            is Wert.Zahl -> Wert.Zeichenfolge(wert.toString())
            is Wert.Boolean -> Wert.Zeichenfolge(if (wert.boolean) "wahr" else "falsch")
            else -> wert
          }
          },

          Typ.Boolean to {
            wert -> when(wert){
            is Wert.Zahl -> Wert.Boolean(wert.isZero())
            is Wert.Zeichenfolge -> Wert.Boolean(wert.wert.isNotEmpty())
            else -> wert
          }
          }
  )

  override fun evaluiereKonvertierung(konvertierung: AST.Ausdruck.Konvertierung): Wert {
    val wert = evaluiereAusdruck(konvertierung.ausdruck)
    return konvertierungsTabelle.getValue(konvertierung.typ.typ!!)(wert)
  }
  // endregion
}

fun main() {
  val interpreter = Interpreter("./iterationen/iter_2/code.gms")
  try {
    interpreter.interpretiere()
  } catch (fehler: GermanScriptFehler) {
    System.err.println(fehler.message!!)
  }
}


