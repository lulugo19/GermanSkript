import java.text.*
import java.math.*
import java.lang.ArithmeticException
import kotlin.math.roundToInt


sealed class Wert {
  data class Zeichenfolge(val wert: String): Wert(), Comparable<Zeichenfolge> {
    override fun toString(): String = wert
    operator fun plus(zeichenfolge: Zeichenfolge) = this.wert + zeichenfolge.wert
    override fun compareTo(other: Zeichenfolge): Int = this.wert.compareTo(other.wert)
  }

  class Zahl(val wert: Double): Wert(), Comparable<Zahl> {
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
    override fun toString(): String = Static.format.format(wert)

    operator fun unaryMinus() = Zahl(-this.wert)
    operator fun plus(zahl: Zahl) = Zahl(this.wert + zahl.wert)
    operator fun minus(zahl: Zahl) = Zahl(this.wert - zahl.wert)
    operator fun times(zahl: Zahl) = Zahl(this.wert * zahl.wert)

    operator fun div(zahl: Zahl) = Zahl(this.wert / zahl.wert)

    override fun equals(other: Any?): kotlin.Boolean {
      return if (other is Zahl) {
        this.wert.compareTo(other.wert) == 0
      } else {
        false
      }
    }

    override fun hashCode(): Int = this.wert.hashCode()

    operator fun rem(zahl: Zahl) = Zahl(this.wert % zahl.wert)
    fun pow(zahl: Zahl): Zahl   = Zahl(Math.pow(this.wert, zahl.wert))
    fun toInt() = this.wert.toInt()
    fun toDouble() = this.wert
    fun round() = wert.roundToInt()
    fun floor() = kotlin.math.floor(wert)
    fun ceil() = kotlin.math.ceil(wert)

    override fun compareTo(other: Zahl): Int = this.wert.compareTo(other.wert)

    fun isZero() = this.wert == 0.0
  }

  class Boolean(val boolean: kotlin.Boolean): Wert() {
    override fun toString(): String = boolean.toString()
  }

  class Liste(val elemente: List<Wert>): Wert() {
    operator fun plus(liste: Liste) = Liste(this.elemente + liste.elemente)
  }
}
