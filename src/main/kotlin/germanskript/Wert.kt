package germanskript

import java.text.*
import java.math.*
import kotlin.math.*

typealias AufrufCallback = (Wert, String, argumente: Array<Wert>) -> Wert?

sealed class Wert {
  sealed class Primitiv: Wert() {
    data class Zeichenfolge(val zeichenfolge: String): Primitiv(), Comparable<Zeichenfolge> {
      override fun toString(): String = zeichenfolge
      operator fun plus(zeichenfolge: Zeichenfolge) = this.zeichenfolge + zeichenfolge.zeichenfolge
      override fun compareTo(other: Zeichenfolge): Int = this.zeichenfolge.compareTo(other.zeichenfolge)
    }

    class Zahl(val zahl: Double): Primitiv(), Comparable<Zahl> {
      object Static {
        val format = DecimalFormat()
        init {
          with (format) {
            decimalFormatSymbols.groupingSeparator = '.'
            decimalFormatSymbols.decimalSeparator = ','
            roundingMode = RoundingMode.HALF_UP
            maximumFractionDigits = 20
          }
        }
      }

      private object MathContexts {
        val FLOOR = MathContext(0, RoundingMode.FLOOR)
        val CEIL = MathContext(0, RoundingMode.DOWN)
        val ROUND = MathContext(0, RoundingMode.HALF_UP)
      }

      constructor(zahl: String): this(Static.format.parse(zahl).toDouble())
      override fun toString(): String = Static.format.format(zahl)

      operator fun unaryMinus() = Zahl(-this.zahl)
      operator fun plus(other: Zahl) = Zahl(this.zahl + other.zahl)
      operator fun minus(other: Zahl) = Zahl(this.zahl - other.zahl)
      operator fun times(other: Zahl) = Zahl(this.zahl * other.zahl)

      operator fun div(other: Zahl) = Zahl(this.zahl / other.zahl)

      override fun equals(other: Any?): kotlin.Boolean {
        return if (other is Zahl) {
          this.zahl.compareTo(other.zahl) == 0
        } else {
          false
        }
      }

      override fun hashCode(): Int = this.zahl.hashCode()

      operator fun rem(other: Zahl) = Zahl(this.zahl % other.zahl)
      fun pow(other: Zahl): Zahl = Zahl(this.zahl.pow(other.zahl))
      fun toInt() = zahl.toInt()
      fun toDouble() = zahl
      fun round() = zahl.roundToInt()
      fun floor() = floor(zahl)
      fun ceil() = ceil(zahl)

      override fun compareTo(other: Zahl): Int = this.zahl.compareTo(other.zahl)

      fun isZero() = this.zahl == 0.0
    }

    class Boolean(val boolean: kotlin.Boolean): Primitiv() {
      override fun toString(): String = boolean.toString()
    }

  }

  sealed class Objekt(internal val typ: Typ.Compound.KlassenTyp): Wert() {
    // TODO: String sollte eindeutigen Identifier zurückliefern
    override fun toString() = typ.definition.name.nominativ
    abstract fun holeEigenschaft(eigenschaftsName: AST.WortArt.Nomen): Wert
    abstract fun setzeEigenschaft(eigenschaftsName: AST.WortArt.Nomen, wert: Wert)

    class SkriptObjekt(
        typ: Typ.Compound.KlassenTyp,
        val eigenschaften: MutableMap<String, Wert>
    ) : Objekt(typ) {
      override fun holeEigenschaft(eigenschaftsName: AST.WortArt.Nomen) = eigenschaften.getValue(eigenschaftsName.nominativ)
      override fun setzeEigenschaft(eigenschaftsName: AST.WortArt.Nomen, wert: Wert) {
        eigenschaften[eigenschaftsName.nominativ] = wert
      }
    }

    sealed class InternesObjekt(typ: Typ.Compound.KlassenTyp): Objekt(typ) {

      abstract fun rufeMethodeAuf(methodenName: String, umgebung: Umgebung<Wert>, aufrufCallback: AufrufCallback): Wert?

      class Liste(typ: Typ.Compound.KlassenTyp, val elemente: MutableList<Wert>): InternesObjekt(typ) {
        operator fun plus(liste: Liste) = Liste(typ, (this.elemente + liste.elemente).toMutableList())

        override fun toString(): String {
          return "[${elemente.joinToString(", ")}]"
        }

        override fun holeEigenschaft(eigenschaftsName: AST.WortArt.Nomen): Wert {
          if (eigenschaftsName.nominativ == "AnZahl") {
            return Primitiv.Zahl(elemente.size.toDouble())
          }
          throw Exception("Die Eigenschaft ${eigenschaftsName.nominativ} ist für den Typen Liste nicht definiert.")
        }

        override fun setzeEigenschaft(eigenschaftsName: AST.WortArt.Nomen, wert: Wert) {
          // vielleicht kommt hier später mal was, sieht aber nicht danach aus
        }

        override fun rufeMethodeAuf(methodenName: String, umgebung: Umgebung<Wert>, aufrufCallback: AufrufCallback): Wert? {
          return when (methodenName) {
            "enthalten den Typ" -> enthaltenDenTyp(umgebung)
            "füge den Typ hinzu" -> fügeDenTypHinzu(umgebung)
            "entferne an dem Index" -> entferneAnDemIndex(umgebung)
            "sortiere mich mit dem Vergleichbaren" -> sortiereMichMitDemVergleichbaren(umgebung, aufrufCallback)
            else -> throw Exception("Undefinierte Methode für Liste")
          }
        }

        private fun enthaltenDenTyp(umgebung: Umgebung<Wert>): Wert? {
          val element = umgebung.leseVariable("Typ")!!.wert
          return Primitiv.Boolean(elemente.contains(element))
        }

        private fun fügeDenTypHinzu(umgebung: Umgebung<Wert>): Wert? {
          val element = umgebung.leseVariable("Typ")!!.wert
          elemente.add(element)
          return null
        }

        private fun entferneAnDemIndex(umgebung: Umgebung<Wert>): Wert? {
          val index = umgebung.leseVariable("Index")!!.wert as Primitiv.Zahl
          elemente.removeAt(index.toInt())
          return null
        }

        private fun sortiereMichMitDemVergleichbaren(umgebung: Umgebung<Wert>, aufrufCallback: AufrufCallback): Wert? {
          val typArg = typ.typArgumente[0].name.nominativ
          val vergleichbar = umgebung.leseVariable("Vergleichbare")!!.wert
          elemente.sortWith(kotlin.Comparator { a, b ->
            (aufrufCallback(vergleichbar, "vergleiche den ${typArg}A mit dem ${typArg}B", arrayOf(a, b))
                as Primitiv.Zahl).zahl.toInt()
          })
          return null
        }
      }

    }

  }

  class Closure(val schnittstelle: Typ.Compound.Schnittstelle, val körper: AST.Satz.Bereich, val umgebung: Umgebung<Wert>): Wert()
}
