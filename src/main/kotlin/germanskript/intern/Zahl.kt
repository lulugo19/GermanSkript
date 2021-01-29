package germanskript.intern

import germanskript.*
import germanskript.IM_AST
import germanskript.Interpretierer
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import kotlin.math.pow
import kotlin.math.roundToInt

class Zahl(val zahl: Double): Objekt(BuildIn.IMMKlassen.zahl, BuildIn.Klassen.zahl), Comparable<Zahl> {
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
  fun floor() = kotlin.math.floor(zahl)
  fun ceil() = kotlin.math.ceil(zahl)

  override fun compareTo(other: Zahl): Int = this.zahl.compareTo(other.zahl)

  fun isZero() = this.zahl == 0.0

  override fun rufeMethodeAuf(aufruf: IM_AST.Satz.Ausdruck.IAufruf, injection: Interpretierer.InterpretInjection): Objekt {
    return when(aufruf.name) {
      "addiere mich mit dem Operanden",
      "addiere mich mit der Zahl" -> this + injection.umgebung.leseVariable("Zahl") as Zahl
      "subtrahiere mich mit dem Operanden",
      "subtrahiere mich mit der Zahl" -> this - injection.umgebung.leseVariable("Zahl") as Zahl
      "multipliziere mich mit dem Operanden",
      "multipliziere mich mit der Zahl" -> this * injection.umgebung.leseVariable("Zahl") as Zahl
      "dividiere mich mit dem Operanden",
      "dividiere mich mit der Zahl" -> this / injection.umgebung.leseVariable("Zahl") as Zahl
      "potenziere mich mit dem Operanden",
      "potenziere mich mit der Zahl" -> pow(injection.umgebung.leseVariable("Zahl") as Zahl)
      "moduliere mich mit dem Operanden",
      "moduliere mich mit der Zahl" -> this % injection.umgebung.leseVariable("Zahl") as Zahl
      "negiere mich" -> -this
      else -> super.rufeMethodeAuf(aufruf, injection)
    }
  }
}
