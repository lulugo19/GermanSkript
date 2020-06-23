data class Programm(val definitionen: List<Definition>, val sätze: List<Satz>)

data class BedingteSätze(val bedingung: Ausdruck, val sätze: List<Satz>)

// ein Statement
sealed class Satz {
  data class Variablendeklaration(
          val artikel: Token,
          val typ: Token,
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

  data class Bedingung(val wennBedingung: BedingteSätze, val wennSonstBedingungen: List<BedingteSätze>?, val sonstBedingung: List<Satz>?): Satz()
  data class SolangeSchleife(val bedingteSätze: BedingteSätze): Satz()
  data class FürJedeSchleife(val jede: Token, val binder: Token, val ausdruck: Ausdruck, val sätze: List<Satz>): Satz()

  // nur in einer Methoden oder Funktionsdefinition mit Rückgabetyp erlaubt
  data class Zurück(val wert: Ausdruck): Satz()

  // nur in einer Schleife erlaubt
  data class Forfahren(val wort: Token): Satz()
  data class Abbrechen(val wort: Token): Satz()
}

data class Funktionsaufruf(
        val funktionsName: Token,
        val argumentListe: List<Argument>
)

data class Methodenaufruf(
        val objekt: Token,
        val methodenNamen: Token,
        val argumentListe: List<Argument>
)

sealed class Argument {
  data class StellenArgument(val index: Int, val ausdruck: Ausdruck): Argument()
  data class NamenArgument(val name: String, val ausdruck: Ausdruck): Argument()
}

sealed class Ausdruck {
  data class Literal(val literal: Token): Ausdruck()
  data class Zahl(val zahl: Token): Ausdruck()
  data class Zeichenfolge(val zeichenfole: Token): Ausdruck()
  data class Boolean(val boolean: Token): Ausdruck()
  data class Liste(val elemente: List<Ausdruck>)
  data class Lambda(val binder: List<String>, val körper: List<Satz>)
  data class Variable(val name: Token): Ausdruck()
  data class FunktionsaufrufAusdruck(val funktionsaufruf: Funktionsaufruf): Ausdruck()
  data class MethodenaufrufAusdruck(val methodenaufruf: Methodenaufruf): Ausdruck()
  data class WennDannSonst(val bedingung : Ausdruck, val dannAusdruck: Ausdruck, val sonstAusdruck: Ausdruck): Ausdruck()
  data class BinärerAusdruck(val operator: Token, val links: Ausdruck, val rechts: Ausdruck): Ausdruck()
  data class UnärerAusdruck(val operator: Token, val ausdruck: Ausdruck): Ausdruck()
}

sealed class Typ {
  data class Einfach(val name: List<Token>): Typ()
  data class Lambda(val arguments: List<Typ>, val returnType: Typ): Typ()
}

data class NameUndTyp(val name: Token, val typ: Token)

data class Signatur(
  val name: Token,
  val rückgabeTyp: Token?,
  val parameter: List<NameUndTyp>
)

sealed class Definition {

  data class Modul(
          val name: Token,
          val definitionen: List<Definition>
  ) : Definition()

  data class Funktion(
    val signatur: Signatur,
    val körper: List<Satz>
  ): Definition()

  data class Methode(
    val signatur: Signatur,
    val typ: Token,
    val körper: List<Satz>
  ): Definition()

  data class Typ(
          val artikel: Token,
          val name: Token,
          val elternTyp: Token?,
          val plural: Token,
          val genitiv: Token,
          val felder: List<NameUndTyp>
  ): Definition()

  data class Schnittstelle(
    val name: Token,
    val signaturen: List<Signatur>
  ): Definition()

  data class Alias(
          val artikel: Token,
          val aliasTypName: Token,
          val plural: Token,
          val genitiv: Token,
          val typName: Token
  ): Definition()
}