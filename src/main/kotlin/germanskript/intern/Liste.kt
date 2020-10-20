package germanskript.intern

import germanskript.*

class Liste(typ: Typ.Compound.KlassenTyp, val elemente: MutableList<Wert>): Wert.Objekt(typ) {
  operator fun plus(liste: Liste) = Liste(typ, (this.elemente + liste.elemente).toMutableList())

  override fun toString(): String {
    return "[${elemente.joinToString(", ")}]"
  }

  override fun holeEigenschaft(eigenschaftsName: String): Wert {
    if (eigenschaftsName == "AnZahl") {
      return Zahl(elemente.size.toDouble())
    }
    throw Exception("Die Eigenschaft ${eigenschaftsName} ist für den Typen Liste nicht definiert.")
  }

  override fun setzeEigenschaft(eigenschaftsName: String, wert: Wert) {
    // vielleicht kommt hier später mal was, sieht aber nicht danach aus
  }

  override fun rufeMethodeAuf(aufruf: AST.IAufruf, injection: InterpretInjection): Wert {
    return when (aufruf.vollerName!!) {
      "enthalten das Element" -> enthaltenDenTyp(injection)
      "füge das Element hinzu" -> fügeDenTypHinzu(injection)
      "entferne an dem Index" -> entferneAnDemIndex(injection)
      "sortiere mich mit dem Vergleichenden" -> sortiereMichMitDemVergleichbaren(injection)
      else -> super.rufeMethodeAuf(aufruf, injection)
    }
  }

  private fun enthaltenDenTyp(injection: InterpretInjection): Wert {
    val element = injection.umgebung.leseVariable("Element")!!.wert
    return Boolean(elemente.contains(element))
  }

  private fun fügeDenTypHinzu(injection: InterpretInjection): Wert {
    val element = injection.umgebung.leseVariable("Element")!!.wert
    elemente.add(element)
    return Nichts
  }

  private fun entferneAnDemIndex(injection: InterpretInjection): Wert {
    val index = injection.umgebung.leseVariable("Index")!!.wert as Zahl
    elemente.removeAt(index.toInt())
    return Nichts
  }

  private fun sortiereMichMitDemVergleichbaren(injection: InterpretInjection): Wert {
    val typArg = typ.typArgumente[0].name.nominativ
    val vergleichbar = injection.umgebung.leseVariable("Vergleichbare")!!.wert
    elemente.sortWith(kotlin.Comparator { a, b ->
      (injection.schnittstellenAufruf(vergleichbar, "vergleiche den ${typArg}A mit dem ${typArg}B", arrayOf(a, b))
          as Zahl).zahl.toInt()
    })
    return Nichts
  }
}