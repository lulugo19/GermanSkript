package germanskript.alte_pipeline.intern

import germanskript.BuildIn
import kotlin.Boolean

class Boolean(val boolean: Boolean): Objekt(BuildIn.Klassen.boolean) {
  override fun toString(): String = if (this.boolean) "wahr" else "falsch"

  override fun equals(other: Any?): Boolean {
    if (other !is germanskript.alte_pipeline.intern.Boolean) return false
    return boolean == other.boolean
  }

  override fun hashCode() = boolean.hashCode()
}