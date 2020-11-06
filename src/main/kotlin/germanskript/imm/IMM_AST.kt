package germanskript.imm

import germanskript.Operator
import germanskript.Token
import germanskript.TokenTyp
import germanskript.TypedToken

sealed class IMM_AST {
  sealed class Definition {
    class Funktion(val parameter: List<String>) {
      lateinit var bereich: Satz.Ausdruck.Bereich
    }
    class Klasse(val name: String, val methoden: HashMap<String, Funktion>)
  }

  sealed class Satz: IMM_AST() {
    object Intern: Satz()
    object Fortfahren: Satz()
    object Abbrechen: Satz()

    data class Zurückgabe(val ausdruck: Ausdruck): Satz()

    data class VariablenDeklaration(val name: String, val wert: Ausdruck, val überschreibe: kotlin.Boolean): Satz()

    data class SetzeEigenschaft(val name: String, val ausdruck: Ausdruck): Satz()

    data class BedingungsTerm(val bedingung: Ausdruck, val bereich: Ausdruck.Bereich): IMM_AST()

    data class SolangeSchleife(val bedingungsTerm: BedingungsTerm): Satz()

    sealed class Ausdruck: Satz() {

      data class LogischesUnd(val links: Ausdruck, val rechts: Ausdruck)
      data class LogischesOder(val links: Ausdruck, val rechts: Ausdruck)
      data class LogischesNicht(val links: Ausdruck, val rechts: Ausdruck)
      data class VergleichOhneGleich(val links: MethodenAufruf, val rechts: MethodenAufruf)
      data class VergleichMitGleich(val links: MethodenAufruf, val rechts: MethodenAufruf)

      data class Bereich(val sätze: List<Satz>): Ausdruck()

      data class Bedingung(val bedingungen: List<BedingungsTerm>, val sonst: Bereich?): Ausdruck()

      interface IAufruf {
        val name: String
        val token: Token
        val argumente: List<Ausdruck>
      }

      data class FunktionsAufruf(
          override val name: String,
          override val token: Token,
          override val argumente: List<Ausdruck>,
          val funktion: Definition.Funktion
      ): Ausdruck(), IAufruf

      data class MethodenAufruf(
          override val name: String,
          override val token: Token,
          override val argumente: List<Ausdruck>,
          val objekt: Ausdruck,
          // Falls die Funktion explizit angegeben ist, geschieht kein dynamic Dispatching
          val funktion: Definition.Funktion?
      ): Ausdruck(), IAufruf

      data class Variable(val name: String): Ausdruck()

      data class Eigenschaft(val name: String, val objekt: Ausdruck): Ausdruck()

      data class ObjektInstanziierung(val klasse: Definition.Klasse, val istClosure: Boolean): Ausdruck()

      sealed class Konstante(): Ausdruck() {
        data class Zahl(val zahl: Double): Konstante()
        data class Zeichenfolge(val zeichenfolge: String): Konstante()
        data class Boolean(val boolean: kotlin.Boolean): Konstante()
        object Nichts: Konstante()
      }
    }
  }
}