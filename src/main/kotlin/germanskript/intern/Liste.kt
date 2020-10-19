package germanskript.intern

import germanskript.AST
import germanskript.Interpretierer
import germanskript.Typ
import germanskript.Umgebung

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

  override fun rufeMethodeAuf(aufruf: AST.IAufruf, aufrufStapel: Interpretierer.AufrufStapel, umgebung: Umgebung<Wert>, aufrufCallback: AufrufCallback): Wert {
    return when (aufruf.vollerName!!) {
      "enthalten das Element" -> enthaltenDenTyp(umgebung)
      "füge das Element hinzu" -> fügeDenTypHinzu(umgebung)
      "entferne an dem Index" -> entferneAnDemIndex(umgebung)
      "sortiere mich mit dem Vergleichenden" -> sortiereMichMitDemVergleichbaren(umgebung, aufrufCallback)
      else -> super.rufeMethodeAuf(aufruf, aufrufStapel, umgebung, aufrufCallback)
    }
  }

  private fun enthaltenDenTyp(umgebung: Umgebung<Wert>): Wert {
    val element = umgebung.leseVariable("Element")!!.wert
    return Boolean(elemente.contains(element))
  }

  private fun fügeDenTypHinzu(umgebung: Umgebung<Wert>): Wert {
    val element = umgebung.leseVariable("Element")!!.wert
    elemente.add(element)
    return Nichts
  }

  private fun entferneAnDemIndex(umgebung: Umgebung<Wert>): Wert {
    val index = umgebung.leseVariable("Index")!!.wert as Zahl
    elemente.removeAt(index.toInt())
    return Nichts
  }

  private fun sortiereMichMitDemVergleichbaren(umgebung: Umgebung<Wert>, aufrufCallback: AufrufCallback): Wert {
    val typArg = typ.typArgumente[0].name.nominativ
    val vergleichbar = umgebung.leseVariable("Vergleichbare")!!.wert
    elemente.sortWith(kotlin.Comparator { a, b ->
      (aufrufCallback(vergleichbar, "vergleiche den ${typArg}A mit dem ${typArg}B", arrayOf(a, b))
          as Zahl).zahl.toInt()
    })
    return Nichts
  }
}