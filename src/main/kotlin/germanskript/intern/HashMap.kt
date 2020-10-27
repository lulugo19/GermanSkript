package germanskript.intern

import germanskript.AST
import germanskript.InterpretInjection
import germanskript.Typ

class HashMap(typ: Typ.Compound.KlassenTyp, val eigenschaften: MutableMap<String, Wert>): Wert.Objekt(typ) {

  private val map = mutableMapOf<Wert, Wert>()

  override fun setzeEigenschaft(eigenschaftsName: String, wert: Wert) {

  }

  override fun holeEigenschaft(eigenschaftsName: String): Wert {
    return when (eigenschaftsName) {
      "Größe" -> Zahl(map.size.toDouble())
      else -> super.holeEigenschaft(eigenschaftsName)
    }
  }

  override fun rufeMethodeAuf(aufruf: AST.IAufruf, injection: InterpretInjection): Wert {
    return when (aufruf.vollerName) {
      "entferne den Schlüssel" -> entferneDenSchlüssel(aufruf, injection)
      "enthält den Schlüssel" -> enthältDenSchlüssel(aufruf, injection)
      "füge den Schlüssel mit dem Wert hinzu" -> fügeDenSchlüsselMitDemWertHinzu(aufruf, injection)
      "hole den Wert mit dem Schlüssel" -> holeDenWertMitDemSchlüssel(aufruf, injection)
      "hole den Wert mit dem Schlüssel, dem Wert" -> holeDenWertMitDemSchlüsselUndDemStandardWert(aufruf, injection)
      "lösche alles" -> löscheAlles(aufruf, injection)
      else -> super.rufeMethodeAuf(aufruf, injection)
    }
  }

  private fun entferneDenSchlüssel(aufruf: AST.IAufruf, injection: InterpretInjection): Wert {
    val schlüssel = injection.umgebung.leseVariable("Schlüssel")!!.wert
    map.remove(schlüssel)
    return Nichts
  }

  private fun enthältDenSchlüssel(aufruf: AST.IAufruf, injection: InterpretInjection): Wert {
    val schlüssel = injection.umgebung.leseVariable("Schlüssel")!!.wert
    return Boolean(map.containsKey(schlüssel))
  }

  private fun fügeDenSchlüsselMitDemWertHinzu(aufruf: AST.IAufruf, injection: InterpretInjection): Wert {
    val schlüssel = injection.umgebung.leseVariable("Schlüssel")!!.wert
    val wert = injection.umgebung.leseVariable("Wert")!!.wert
    map[schlüssel] = wert
    return Nichts
  }

  private fun holeDenWertMitDemSchlüssel(aufruf: AST.IAufruf, injection: InterpretInjection): Wert {
    val schlüssel = injection.umgebung.leseVariable("Schlüssel")!!.wert
    return map.getOrElse(schlüssel) {
      injection.werfeFehler(
          "Der Schlüssel '$schlüssel' konnte in der HashMap nicht gefunden werden.",
          "SchlüsselNichtGefundenFehler",
          aufruf.token
      )
    }
  }

  private fun holeDenWertMitDemSchlüsselUndDemStandardWert(aufruf: AST.IAufruf, injection: InterpretInjection): Wert {
    val schlüssel = injection.umgebung.leseVariable("Schlüssel")!!.wert
    val standardWert = injection.umgebung.leseVariable("StandardWert")!!.wert
    return map.getOrDefault(schlüssel, standardWert)
  }

  private fun löscheAlles(aufruf: AST.IAufruf, injection: InterpretInjection): Wert {
    map.clear()
    return Nichts
  }
}