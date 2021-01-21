package germanskript

sealed class IMM_AST {
  sealed class Definition {
    class Funktion(val parameter: List<String>) {
      var körper: Satz.Ausdruck.Bereich? = null
    }
    class Klasse(val name: String, val methoden: HashMap<String, Funktion>)
  }

  sealed class Satz: IMM_AST() {
    object Intern: Satz()
    object Fortfahren: Satz()
    object Abbrechen: Satz()

    data class Zurückgabe(val ausdruck: Ausdruck): Satz()

    data class VariablenDeklaration(val name: String, val wert: Ausdruck, val überschreibe: Boolean): Satz()

    data class SetzeEigenschaft(val objekt: Ausdruck, val name: String, val ausdruck: Ausdruck): Satz()

    data class BedingungsTerm(val bedingung: Ausdruck, val bereich: Ausdruck.Bereich): IMM_AST()

    data class SolangeSchleife(val bedingungsTerm: BedingungsTerm): Satz()

    sealed class Ausdruck: Satz() {

      data class LogischesUnd(val links: Ausdruck, val rechts: Ausdruck): Ausdruck()
      data class LogischesOder(val links: Ausdruck, val rechts: Ausdruck): Ausdruck()
      data class LogischesNicht(val ausdruck: Ausdruck): Ausdruck()

      enum class VergleichsOperator {
        KLEINER,
        GRÖSSER,
        KLEINER_GLEICH,
        GRÖSSER_GLEICH
      }

      data class Vergleich(val vergleichsMethode: MethodenAufruf, val operator: VergleichsOperator): Ausdruck()

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

      enum class ObjektArt {
        Klasse,
        AnonymeKlasse,
        Lambda
      }

      data class ObjektInstanziierung(val typ: Typ.Compound.Klasse, val klasse: Definition.Klasse, val objektArt: ObjektArt): Ausdruck()

      sealed class Konstante(): Ausdruck() {
        data class Zahl(val zahl: Double): Konstante()
        data class Zeichenfolge(val zeichenfolge: String): Konstante()
        data class Boolean(val boolean: kotlin.Boolean): Konstante()
        object Nichts: Konstante()
      }

      data class VersucheFange(
          val versuchBereich: Bereich,
          val fange: List<Fange>,
          val schlussendlich: Bereich?
      ): Ausdruck()

      data class Fange (
          val param: String,
          val typ: Typ.Compound,
          val bereich: Bereich
      ): IMM_AST()

      data class Werfe (
        val werfe: TypedToken<TokenTyp.WERFE>,
        val ausdruck: Ausdruck
      ): Ausdruck()

      data class TypÜberprüfung(
          val ausdruck: Ausdruck,
          val typ: Typ
      ): Ausdruck()

      data class TypCast(
          val ausdruck: Ausdruck,
          val zielTyp: Typ,
          val token: Token
      ): Ausdruck()
    }
  }
}