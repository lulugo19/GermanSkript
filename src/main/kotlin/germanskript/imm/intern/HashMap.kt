package germanskript.imm.intern

import germanskript.AST
import germanskript.BuildIn
import germanskript.Typ
import germanskript.imm.IMM_AST
import germanskript.imm.Interpretierer

class HashMap(typ: Typ.Compound.Klasse): Objekt(BuildIn.IMMKlassen.hashMap, typ) {

  private val map = mutableMapOf<Objekt, Objekt>()

  override fun setzeEigenschaft(eigenschaftsName: String, wert: Objekt) {

  }

  override fun holeEigenschaft(eigenschaftsName: String): Objekt {
    return when (eigenschaftsName) {
      "Größe" -> Zahl(map.size.toDouble())
      else -> super.holeEigenschaft(eigenschaftsName)
    }
  }

  override fun rufeMethodeAuf(aufruf: IMM_AST.Satz.Ausdruck.IAufruf, injection: Interpretierer.InterpretInjection): Objekt {
    return when (aufruf.name) {
      "entferne den Schlüssel" -> entferneDenSchlüssel(aufruf, injection)
      "enthält den Schlüssel" -> enthältDenSchlüssel(aufruf, injection)
      "füge den Schlüssel mit dem Wert hinzu" -> fügeDenSchlüsselMitDemWertHinzu(aufruf, injection)
      "hole den Wert mit dem Schlüssel" -> holeDenWertMitDemSchlüssel(aufruf, injection)
      "hole den Wert mit dem Schlüssel, dem Wert" -> holeDenWertMitDemSchlüsselUndDemStandardWert(aufruf, injection)
      "lösche alles" -> löscheAlles(aufruf, injection)
      else -> super.rufeMethodeAuf(aufruf, injection)
    }
  }

  private fun entferneDenSchlüssel(aufruf: IMM_AST.Satz.Ausdruck.IAufruf, injection: Interpretierer.InterpretInjection): Objekt {
    val schlüssel = injection.umgebung.leseVariable("Schlüssel")
    map.remove(schlüssel)
    return Nichts
  }

  private fun enthältDenSchlüssel(aufruf: IMM_AST.Satz.Ausdruck.IAufruf, injection: Interpretierer.InterpretInjection): Objekt {
    val schlüssel = injection.umgebung.leseVariable("Schlüssel")
    return Boolean(map.containsKey(schlüssel))
  }

  private fun fügeDenSchlüsselMitDemWertHinzu(aufruf: IMM_AST.Satz.Ausdruck.IAufruf, injection: Interpretierer.InterpretInjection): Objekt {
    val schlüssel = injection.umgebung.leseVariable("Schlüssel")
    val wert = injection.umgebung.leseVariable("Wert")
    map[schlüssel] = wert
    return Nichts
  }

  private fun holeDenWertMitDemSchlüssel(aufruf: IMM_AST.Satz.Ausdruck.IAufruf, injection: Interpretierer.InterpretInjection): Objekt {
    val schlüssel = injection.umgebung.leseVariable("Schlüssel")
    return map.getOrElse(schlüssel) {
      injection.werfeFehler(
          "Der Schlüssel '$schlüssel' konnte in der HashMap nicht gefunden werden.",
          "SchlüsselNichtGefundenFehler",
          aufruf.token
      )
    }
  }

  private fun holeDenWertMitDemSchlüsselUndDemStandardWert(aufruf: IMM_AST.Satz.Ausdruck.IAufruf, injection: Interpretierer.InterpretInjection): Objekt {
    val schlüssel = injection.umgebung.leseVariable("Schlüssel")
    val standardWert = injection.umgebung.leseVariable("StandardWert")
    return map.getOrDefault(schlüssel, standardWert)
  }

  private fun löscheAlles(aufruf: IMM_AST.Satz.Ausdruck.IAufruf, injection: Interpretierer.InterpretInjection): Objekt {
    map.clear()
    return Nichts
  }
}