package germanskript.intern

import germanskript.*
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import kotlin.math.pow
import kotlin.math.roundToInt

class Zahl(val zahl: Double): Wert.Objekt.InternesObjekt(Typ.Compound.KlassenTyp.BuildInType.Zahl), Comparable<Zahl> {
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

  override fun rufeMethodeAuf(aufruf: AST.IAufruf, aufrufStapel: Interpretierer.AufrufStapel, umgebung: Umgebung<Wert>, aufrufCallback: AufrufCallback): Wert {
    TODO("Not yet implemented")
  }

  override fun holeEigenschaft(eigenschaftsName: String): Wert {
    TODO("Not yet implemented")
  }

  override fun setzeEigenschaft(eigenschaftsName: String, wert: Wert) {
    TODO("Not yet implemented")
  }

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
}
