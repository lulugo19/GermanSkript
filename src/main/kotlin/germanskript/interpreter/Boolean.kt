package germanskript.interpreter

import kotlin.Boolean

class Boolean(val boolean: Boolean): Objekt() {
  override fun toString(): String = if (boolean) "wahr" else "falsch"

  override fun hashCode() = if (boolean) 1 else 0

  override fun equals(other: Any?): Boolean {
    if (other !is germanskript.intern.Boolean) return false
    return boolean == other.boolean
  }
}