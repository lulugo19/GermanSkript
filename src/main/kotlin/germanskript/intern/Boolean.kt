package germanskript.intern

import germanskript.Typ
import kotlin.Boolean

class Boolean(val boolean: Boolean): Wert.Objekt(Typ.Compound.KlassenTyp.BuildInType.Boolean) {
  override fun toString(): String = if (this.boolean) "wahr" else "falsch"

  override fun equals(other: Any?): Boolean {
    if (other !is germanskript.intern.Boolean) return false
    return boolean == other.boolean
  }

  override fun hashCode() = boolean.hashCode()
}