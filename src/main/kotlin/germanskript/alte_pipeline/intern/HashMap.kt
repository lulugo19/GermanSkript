package germanskript.alte_pipeline.intern

import germanskript.AST
import germanskript.BuildIn
import germanskript.alte_pipeline.InterpretInjection
import germanskript.Typ
import germanskript.intern.Liste

class HashMap(typ: Typ.Compound.Klasse): Objekt(typ) {

  private val map = mutableMapOf<Objekt, Objekt>()

  override fun holeEigenschaft(eigenschaftsName: String): Objekt {
    return when (eigenschaftsName) {
      "Größe" -> Zahl(map.size.toDouble())
      else -> super.holeEigenschaft(eigenschaftsName)
    }
  }

  override fun rufeMethodeAuf(aufruf: AST.IAufruf, injection: InterpretInjection): Objekt {
    return when (aufruf.vollerName) {
      "entferne den Schlüssel" -> entferneDenSchlüssel(aufruf, injection)
      "enthält den Schlüssel" -> enthältDenSchlüssel(aufruf, injection)
      "füge den Schlüssel mit dem Wert hinzu" -> fügeDenSchlüsselMitDemWertHinzu(aufruf, injection)
      "hole den Wert mit dem Schlüssel" -> holeDenWertMitDemSchlüssel(aufruf, injection)
      "hole den Wert mit dem Schlüssel, dem Wert" -> holeDenWertMitDemSchlüsselUndDemStandardWert(aufruf, injection)
      "lösche alles" -> löscheAlles(aufruf, injection)
      "SchlüsselWertePaare von HashMap" -> schlüsselWertePaarEigenschaft()
      else -> super.rufeMethodeAuf(aufruf, injection)
    }
  }

  private fun entferneDenSchlüssel(aufruf: AST.IAufruf, injection: InterpretInjection): Objekt {
    val schlüssel = injection.umgebung.leseVariable("Schlüssel")!!.wert
    map.remove(schlüssel)
    return Nichts
  }

  private fun enthältDenSchlüssel(aufruf: AST.IAufruf, injection: InterpretInjection): Objekt {
    val schlüssel = injection.umgebung.leseVariable("Schlüssel")!!.wert
    return Boolean(map.containsKey(schlüssel))
  }

  private fun fügeDenSchlüsselMitDemWertHinzu(aufruf: AST.IAufruf, injection: InterpretInjection): Objekt {
    val schlüssel = injection.umgebung.leseVariable("Schlüssel")!!.wert
    val wert = injection.umgebung.leseVariable("Wert")!!.wert
    map[schlüssel] = wert
    return Nichts
  }

  private fun holeDenWertMitDemSchlüssel(aufruf: AST.IAufruf, injection: InterpretInjection): Objekt {
    val schlüssel = injection.umgebung.leseVariable("Schlüssel")!!.wert
    return map.getOrElse(schlüssel) {
      injection.werfeFehler(
          "Der Schlüssel '$schlüssel' konnte in der HashMap nicht gefunden werden.",
          "SchlüsselNichtGefundenFehler",
          aufruf.token
      )
    }
  }

  private fun holeDenWertMitDemSchlüsselUndDemStandardWert(aufruf: AST.IAufruf, injection: InterpretInjection): Objekt {
    val schlüssel = injection.umgebung.leseVariable("Schlüssel")!!.wert
    val standardWert = injection.umgebung.leseVariable("StandardWert")!!.wert
    return map.getOrDefault(schlüssel, standardWert)
  }

  private fun löscheAlles(aufruf: AST.IAufruf, injection: InterpretInjection): Objekt {
    map.clear()
    return Nichts
  }

  private fun schlüsselWertePaarEigenschaft(): Objekt {
    val typ = Typ.Compound.Klasse(BuildIn.Klassen.liste, emptyList())
    return germanskript.alte_pipeline.intern.Liste(typ, map.entries.map { entry ->
      SkriptObjekt(Typ.Compound.Klasse(BuildIn.Klassen.paar, typ.typArgumente)).also {
        it.setzeEigenschaft("ersteWert", entry.key)
        it.setzeEigenschaft("zweiteWert", entry.value)
      }
    }.toMutableList())
  }
}