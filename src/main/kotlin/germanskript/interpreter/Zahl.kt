package germanskript.interpreter

import java.math.RoundingMode
import java.text.DecimalFormat
import kotlin.Boolean

class Zahl(val zahl: Double): Objekt() {
  companion object {
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

  override fun toString(): String = format.format(zahl)

  override fun hashCode(): Int = this.zahl.hashCode()

  override fun equals(other: Any?): Boolean {
    if (other !is Zahl) return false
    return zahl == other.zahl
  }

  override fun internerAufruf(name: String): Objekt {
    return when (name) {
      "plus", "minus", "mal", "durch", "hoch", "mod" -> TODO()
      else -> TODO()
    }
  }
}