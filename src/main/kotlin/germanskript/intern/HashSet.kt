package germanskript.intern

import germanskript.*

class HashSet(typ: Typ.Compound.Klasse): Objekt(BuildIn.IMMKlassen.hashSet, typ) {

  private val set = mutableSetOf<Objekt>()

  override fun setzeEigenschaft(eigenschaftsName: String, wert: Objekt) {

  }

  override fun holeEigenschaft(eigenschaftsName: String): Objekt {
    return when (eigenschaftsName) {
      "Größe" -> Zahl(set.size.toDouble())
      else -> super.holeEigenschaft(eigenschaftsName)
    }
  }

  override fun rufeMethodeAuf(aufruf: IMM_AST.Satz.Ausdruck.IAufruf, injection: Interpretierer.InterpretInjection): Objekt {
    return when (aufruf.name) {
      "entferne den Wert" -> entferneDenWert(injection)
      "enthält den Wert" -> enthältDenWert(injection)
      "füge den Wert hinzu" -> fügeDenWertHinzu(injection)
      "lösche alles" -> löscheAlles()
      "Werte von Set" -> werteEigenschaft()
      else -> super.rufeMethodeAuf(aufruf, injection)
    }
  }

  private fun entferneDenWert( injection: Interpretierer.InterpretInjection): Objekt {
    val wert = injection.umgebung.leseVariable("Wert")
    return Boolean(set.remove(wert))
  }

  private fun enthältDenWert(injection: Interpretierer.InterpretInjection): Objekt {
    val wert = injection.umgebung.leseVariable("Wert")
    return Boolean(set.contains(wert))
  }

  private fun fügeDenWertHinzu(injection: Interpretierer.InterpretInjection): Objekt {
    val wert = injection.umgebung.leseVariable("Wert")
    set.add(wert)
    return Nichts
  }

  private fun löscheAlles(): Objekt {
    set.clear()
    return Nichts
  }

  private fun werteEigenschaft(): Objekt {
    val typ = Typ.Compound.Klasse(BuildIn.Klassen.liste, emptyList())
    return Liste(typ, set.toMutableList())
  }
}