package germanskript.intern

import germanskript.*

class HashMap(typ: Typ.Compound.Klasse): Objekt(BuildIn.IMMKlassen.hashMap, typ) {

  private val map = mutableMapOf<Objekt, Objekt>()

  override fun holeEigenschaft(eigenschaftsName: String): Objekt {
    return when (eigenschaftsName) {
      "Größe" -> Zahl(map.size.toDouble())
      else -> super.holeEigenschaft(eigenschaftsName)
    }
  }

  override fun rufeMethodeAuf(aufruf: IM_AST.Satz.Ausdruck.IAufruf, injection: Interpretierer.InterpretInjection): Objekt {
    return when (aufruf.name) {
      "entferne den Schlüssel" -> entferneDenSchlüssel(injection)
      "enthält den Schlüssel" -> enthältDenSchlüssel(injection)
      "füge den Schlüssel mit dem Wert hinzu" -> fügeDenSchlüsselMitDemWertHinzu(injection)
      "hole den Wert mit dem Schlüssel" -> holeDenWertMitDemSchlüssel(aufruf, injection)
      "hole den Wert mit dem Schlüssel, dem Wert" -> holeDenWertMitDemSchlüsselUndDemStandardWert(injection)
      "lösche alles" -> löscheAlles()
      "SchlüsselWertePaare von HashMap" -> schlüsselWertePaarEigenschaft()
      else -> super.rufeMethodeAuf(aufruf, injection)
    }
  }

  private fun entferneDenSchlüssel(injection: Interpretierer.InterpretInjection): Objekt {
    val schlüssel = injection.umgebung.leseVariable("Schlüssel")
    return Boolean(map.remove(schlüssel) != null)
  }

  private fun enthältDenSchlüssel(injection: Interpretierer.InterpretInjection): Objekt {
    val schlüssel = injection.umgebung.leseVariable("Schlüssel")
    return Boolean(map.containsKey(schlüssel))
  }

  private fun fügeDenSchlüsselMitDemWertHinzu(injection: Interpretierer.InterpretInjection): Objekt {
    val schlüssel = injection.umgebung.leseVariable("Schlüssel")
    val wert = injection.umgebung.leseVariable("Wert")
    map[schlüssel] = wert
    return Nichts
  }

  private fun holeDenWertMitDemSchlüssel(aufruf: IM_AST.Satz.Ausdruck.IAufruf, injection: Interpretierer.InterpretInjection): Objekt {
    val schlüssel = injection.umgebung.leseVariable("Schlüssel")
    return map.getOrElse(schlüssel) {
      injection.werfeFehler(
          "Der Schlüssel '$schlüssel' konnte in der HashMap nicht gefunden werden.",
          "SchlüsselNichtGefundenFehler",
          aufruf.token
      )
    }
  }

  private fun holeDenWertMitDemSchlüsselUndDemStandardWert(injection: Interpretierer.InterpretInjection): Objekt {
    val schlüssel = injection.umgebung.leseVariable("Schlüssel")
    val standardWert = injection.umgebung.leseVariable("StandardWert")
    return map.getOrDefault(schlüssel, standardWert)
  }

  private fun löscheAlles(): Objekt {
    map.clear()
    return Nichts
  }

  private fun schlüsselWertePaarEigenschaft(): Objekt {
    val typ = Typ.Compound.Klasse(BuildIn.Klassen.liste, emptyList())
    return Liste(typ, map.entries.map { entry ->
      SkriptObjekt(BuildIn.IMMKlassen.paar, Typ.Compound.Klasse(BuildIn.Klassen.paar, typ.typArgumente)).also {
        it.setzeEigenschaft("ersteWert", entry.key)
        it.setzeEigenschaft("zweiteWert", entry.value)
      }
    }.toMutableList())
  }
}