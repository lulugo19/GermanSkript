package germanskript.intern

import germanskript.*

class HashMap(typ: Typ.Compound.Klasse): Objekt(BuildIn.IMMKlassen.hashMap, typ) {

  class HashSchlüssel(
      val objekt: Objekt,
      private val hash: Int,
      private val interpretInjection: Interpretierer.InterpretInjection,
      private val token: Token
  ) {
    override fun hashCode(): Int {
      return hash
    }

    override fun equals(other: Any?): kotlin.Boolean {
      if (other !is HashSchlüssel)
        return false

      return (interpretInjection.interpretiereInjectionMethodenAufruf(
          "gleicht dem Objekt",
          token,
          objekt,
          listOf(other.objekt)
      ) as Boolean).boolean
    }
  }

  private val map = mutableMapOf<HashSchlüssel, Objekt>()

  override fun holeEigenschaft(eigenschaftsName: String): Objekt {
    return when (eigenschaftsName) {
      "Größe" -> Zahl(map.size.toDouble())
      else -> super.holeEigenschaft(eigenschaftsName)
    }
  }

  override fun rufeMethodeAuf(aufruf: IM_AST.Satz.Ausdruck.IAufruf, injection: Interpretierer.InterpretInjection): Objekt {
    return when (aufruf.name) {
      "entferne den Schlüssel" -> entferneDenSchlüssel(aufruf, injection)
      "enthält den Schlüssel" -> enthältDenSchlüssel(aufruf, injection)
      "füge den Schlüssel mit dem Wert hinzu" -> fügeDenSchlüsselMitDemWertHinzu(aufruf, injection)
      "hole den Wert mit dem Schlüssel" -> holeDenWertMitDemSchlüssel(aufruf, injection)
      "hole den Wert mit dem Schlüssel, dem Wert" -> holeDenWertMitDemSchlüsselUndDemStandardWert(aufruf, injection)
      "lösche alles" -> löscheAlles()
      "SchlüsselWertePaare von HashMap" -> schlüsselWertePaarEigenschaft()
      else -> super.rufeMethodeAuf(aufruf, injection)
    }
  }

  private fun holeHashSchlüssel(
      aufruf: IM_AST.Satz.Ausdruck.IAufruf,
      injection: Interpretierer.InterpretInjection,
      objekt: Objekt):
      HashSchlüssel {
    return HashSchlüssel(
        objekt,
        (injection.interpretiereInjectionMethodenAufruf("hashe mich", aufruf.token, objekt, emptyList()) as Zahl).zahl.toInt(),
        injection,
        aufruf.token
    )
  }

  private fun entferneDenSchlüssel(aufruf: IM_AST.Satz.Ausdruck.IAufruf, injection: Interpretierer.InterpretInjection): Objekt {
    val schlüssel = injection.umgebung.leseVariable("Schlüssel")
    val hashSchlüssel = holeHashSchlüssel(aufruf, injection, schlüssel)
    return Boolean(map.remove(hashSchlüssel) != null)
  }

  private fun enthältDenSchlüssel(aufruf: IM_AST.Satz.Ausdruck.IAufruf, injection: Interpretierer.InterpretInjection): Objekt {
    val schlüssel = injection.umgebung.leseVariable("Schlüssel")
    val hashSchlüssel = holeHashSchlüssel(aufruf, injection, schlüssel)
    return Boolean(map.containsKey(hashSchlüssel))
  }

  private fun fügeDenSchlüsselMitDemWertHinzu(aufruf: IM_AST.Satz.Ausdruck.IAufruf,injection: Interpretierer.InterpretInjection): Objekt {
    val schlüssel = injection.umgebung.leseVariable("Schlüssel")
    val hashSchlüssel = holeHashSchlüssel(aufruf, injection, schlüssel)
    val wert = injection.umgebung.leseVariable("Wert")
    map[hashSchlüssel] = wert
    return Nichts
  }

  private fun holeDenWertMitDemSchlüssel(aufruf: IM_AST.Satz.Ausdruck.IAufruf, injection: Interpretierer.InterpretInjection): Objekt {
    val schlüssel = injection.umgebung.leseVariable("Schlüssel")
    val hashSchlüssel = holeHashSchlüssel(aufruf, injection, schlüssel)
    return map.getOrElse(hashSchlüssel) {
      injection.werfeFehler(
          "Der Schlüssel '$schlüssel' konnte in der HashMap nicht gefunden werden.",
          "SchlüsselNichtGefundenFehler",
          aufruf.token
      )
    }
  }

  private fun holeDenWertMitDemSchlüsselUndDemStandardWert(
      aufruf: IM_AST.Satz.Ausdruck.IAufruf,
      injection: Interpretierer.InterpretInjection
  ): Objekt {
    val schlüssel = injection.umgebung.leseVariable("Schlüssel")
    val hashSchlüssel = holeHashSchlüssel(aufruf, injection, schlüssel)
    val standardWert = injection.umgebung.leseVariable("StandardWert")
    return map.getOrDefault(hashSchlüssel, standardWert)
  }

  private fun löscheAlles(): Objekt {
    map.clear()
    return Nichts
  }

  private fun schlüsselWertePaarEigenschaft(): Objekt {
    val typ = Typ.Compound.Klasse(BuildIn.Klassen.liste, emptyList())
    return Liste(typ, map.entries.map { entry ->
      SkriptObjekt(BuildIn.IMMKlassen.paar, Typ.Compound.Klasse(BuildIn.Klassen.paar, typ.typArgumente)).also {
        it.setzeEigenschaft("ersteWert", entry.key.objekt)
        it.setzeEigenschaft("zweiteWert", entry.value)
      }
    }.toMutableList())
  }
}