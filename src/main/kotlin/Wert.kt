import java.text.*
import java.math.*
import kotlin.math.*


sealed class Wert {
  data class Zeichenfolge(val zeichenfolge: String): Wert(), Comparable<Zeichenfolge> {
    override fun toString(): String = zeichenfolge
    operator fun plus(zeichenfolge: Zeichenfolge) = this.zeichenfolge + zeichenfolge.zeichenfolge
    override fun compareTo(other: Zeichenfolge): Int = this.zeichenfolge.compareTo(other.zeichenfolge)
  }

  class Zahl(val zahl: Double): Wert(), Comparable<Zahl> {
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
    fun pow(other: Zahl): Zahl   = Zahl(this.zahl.pow(other.zahl))
    fun toInt() = zahl.toInt()
    fun toDouble() = zahl
    fun round() = zahl.roundToInt()
    fun floor() = floor(zahl)
    fun ceil() = ceil(zahl)

    override fun compareTo(other: Zahl): Int = this.zahl.compareTo(other.zahl)

    fun isZero() = this.zahl == 0.0
  }

  class Boolean(val boolean: kotlin.Boolean): Wert() {
    override fun toString(): String = boolean.toString()
  }

  class Liste(val elemente: List<Wert>): Wert() {
    operator fun plus(liste: Liste) = Liste(this.elemente + liste.elemente)
  }

  class Objekt(override val klassenDefinition: AST.Definition.Klasse, val felder: HashMap<String, Wert>): Wert(), IObjekt
}
