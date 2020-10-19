package germanskript.intern

import germanskript.Typ

class Boolean(val boolean: kotlin.Boolean): Wert.Objekt(Typ.Compound.KlassenTyp.BuildInType.Boolean) {
  override fun toString(): String = if (this.boolean) "wahr" else "falsch"
}