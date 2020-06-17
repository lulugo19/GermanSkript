typealias BedingteSätze = Pair<Ausdruck, List<Satz>>

data class Programm(val definitionen: List<Definition>, val sätze: List<Satz>)

// ein Statement
sealed class Satz {
  data class Variablendeklaration(
    val geschlecht: Token,
    val name: Token,
    val zuweisung: Token,
    val ausdruck: Ausdruck
  ): Satz()

  data class Variablenzuweisung(
          val name: Token,
          val zuweisung: Token,
          val ausdruck: Ausdruck
  ): Satz()

  data class FunktionsaufrufSatz(val funktionsaufruf: Funktionsaufruf): Satz()
  data class MethodenaufrufSatz(val methodenaufruf: Methodenaufruf): Satz()


  data class Bedingung(val wennBedingung: BedingteSätze, val wennSonstBedingungen: List<BedingteSätze>?, val sonstBedingung: Satz?)
  data class SolangeSchleife(val bedingteSätze: BedingteSätze)
  data class FürJedeSchleife(val jede: Token, val name: Token, val ausdruck: Ausdruck, val bedingteSätze: BedingteSätze)

  // nur in einer Schleife erlaubt
  data class Forfahren(val wort: Token): Satz()
  data class Abbrechen(val wort: Token): Satz()
}

data class Funktionsaufruf(
        val funktionsName: Token,
        val argumentListe: List<Argument>
) : Satz()

data class Methodenaufruf(
        val objekt: Token,
        val methodenNamen: Token,
        val argumentListe: List<Argument>
) : Satz()

sealed class Argument {
  data class StellenArgument(val index: Int, val ausdruck: Ausdruck): Argument()
  data class NamenArgument(val name: String, val ausdruck: Ausdruck): Argument()
}

sealed class Ausdruck {
  data class Literal(val literal: Token): Ausdruck()
  data class Variable(val name: Token): Ausdruck()
  data class FunktionsaufrufAusdruck(val funktionsaufruf: Funktionsaufruf): Ausdruck()
  data class MethodenaufrufAusdruck(val methodenaufruf: Methodenaufruf): Ausdruck()
  data class WennDannSonst(val bedingung : Ausdruck, val dannAusdruck: Ausdruck, val sonstAusdruck: Ausdruck): Ausdruck()
  data class BinärerAusdruck(val operator: Token, val links: Ausdruck, val rechts: Ausdruck): Ausdruck()
  data class UnärerAusdruck(val operator: Token, val ausdruck: Ausdruck): Ausdruck()
}

data class NameUndTyp(val name: Token, val typ: Token)

sealed class Definition {
  data class Funktion(
    val name: Token,
    val rückgabe: Token?,
    val parameter: List<NameUndTyp>,
    val körper: List<Satz>,
    val rückgabeWert: Ausdruck?
  ): Definition()

  data class Methode(
    val name: Token,
    val typ: Token,
    val rückgabe: Token?,
    val parameter: List<NameUndTyp>,
    val körper: List<Satz>,
    val rückgabeWert: Ausdruck?
  ): Definition()

  data class Typ(
    val geschlecht: Geschlecht,
    val name: Token,
    val elternTyp: Token?,
    val plural: Token,
    val felder: List<NameUndTyp>
  ): Definition()
}