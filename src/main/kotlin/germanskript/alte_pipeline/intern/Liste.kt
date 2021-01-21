package germanskript.alte_pipeline.intern

import germanskript.*
import germanskript.alte_pipeline.InterpretInjection

class Liste(typ: Typ.Compound.Klasse, val elemente: MutableList<Objekt>): Objekt(typ) {
  operator fun plus(liste: Liste) = Liste(klasse, (this.elemente + liste.elemente).toMutableList())

  override fun toString(): String {
    return "[${elemente.joinToString(", ")}]"
  }

  override fun holeEigenschaft(eigenschaftsName: String): Objekt {
    if (eigenschaftsName == "AnZahl") {
      return Zahl(elemente.size.toDouble())
    }
    throw Exception("Die Eigenschaft ${eigenschaftsName} ist für den Typen Liste nicht definiert.")
  }

  override fun setzeEigenschaft(eigenschaftsName: String, wert: Objekt) {
    // vielleicht kommt hier später mal was, sieht aber nicht danach aus
  }

  override fun rufeMethodeAuf(aufruf: AST.IAufruf, injection: InterpretInjection): Objekt {
    return when (aufruf.vollerName!!) {
      "addiere mich mit dem Operanden",
      "addiere mich mit der Liste" -> addiereMichMitDemOperanden(injection)
      "enthalten das Element" -> enthaltenDenTyp(injection)
      "füge das Element hinzu" -> fügeDasElementHinzu(injection)
      "hole den Typ mit dem Index",
      "hole das Element mit dem Index" -> holeDasElementMitDemIndex(aufruf, injection)
      "setze den Index auf den Typ",
      "setze den Index auf das Element" -> setzeDenIndexAufDenTyp(aufruf, injection)
      "entferne an dem Index" -> entferneAnDemIndex(injection)
      "sortiere mich mit dem Vergleichenden" -> sortiereMichMitDemVergleichbaren(injection)
      else -> super.rufeMethodeAuf(aufruf, injection)
    }
  }

  private fun addiereMichMitDemOperanden(injection: InterpretInjection): Objekt {
    val andereListe = injection.umgebung.leseVariable("Operand")!!.wert as Liste
    return this + andereListe
  }

  private fun enthaltenDenTyp(injection: InterpretInjection): Objekt {
    val element = injection.umgebung.leseVariable("Element")!!.wert
    return Boolean(elemente.contains(element))
  }

  private fun fügeDasElementHinzu(injection: InterpretInjection): Objekt {
    val element = injection.umgebung.leseVariable("Element")!!.wert
    elemente.add(element)
    return Nichts
  }

  private fun holeDasElementMitDemIndex(aufruf: AST.IAufruf, injection: InterpretInjection): Objekt {
    val index = (injection.umgebung.leseVariable("Index")!!.wert as Zahl).toInt()

    return prüfeIndex(index, aufruf, injection) ?:
        this.elemente[index]
  }

  private fun setzeDenIndexAufDenTyp(aufruf: AST.IAufruf, injection: InterpretInjection): Objekt {
    val index = (injection.umgebung.leseVariable("Index")!!.wert as Zahl).toInt()
    return prüfeIndex(index, aufruf, injection) ?: {
      val wert = injection.umgebung.leseVariable("Element")!!.wert
      this.elemente[index] = wert
      Nichts
    }()
  }

  private fun prüfeIndex(index: Int, aufruf: AST.IAufruf, injection: InterpretInjection): Objekt? {
    return if (index >= elemente.size) {
      injection.werfeFehler(
          "Index außerhalb des Bereichs. Der Index ist $index, doch die Länge der Liste ist ${elemente.size}.\n", "IndexFehler", aufruf.token)
    } else {
      null
    }
  }

  private fun entferneAnDemIndex(injection: InterpretInjection): Objekt {
    val index = injection.umgebung.leseVariable("Index")!!.wert as Zahl
    elemente.removeAt(index.toInt())
    return Nichts
  }

  private fun sortiereMichMitDemVergleichbaren(injection: InterpretInjection): Objekt {
    val typArg = klasse.typArgumente[0].name.nominativ
    val vergleichbar = injection.umgebung.leseVariable("Vergleichbare")!!.wert
    elemente.sortWith(kotlin.Comparator { a, b ->
      (injection.schnittstellenAufruf(vergleichbar, "vergleiche den ${typArg}A mit dem ${typArg}B", arrayOf(a, b))
          as Zahl).zahl.toInt()
    })
    return Nichts
  }
}