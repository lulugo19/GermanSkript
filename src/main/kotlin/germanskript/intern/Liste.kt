package germanskript.intern

import germanskript.*
import germanskript.IM_AST
import germanskript.Interpretierer

class Liste(typ: Typ.Compound.Klasse, val elemente: MutableList<Objekt>): Objekt(BuildIn.IMMKlassen.liste, typ) {
  operator fun plus(liste: Liste) = Liste(typ, (this.elemente + liste.elemente).toMutableList())

  override fun toString(): String {
    return "[${elemente.joinToString(", ")}]"
  }

  override fun holeEigenschaft(eigenschaftsName: String): Objekt {
    if (eigenschaftsName == "AnZahl") {
      return Zahl(elemente.size.toDouble())
    }
    throw Exception("Die Eigenschaft ${eigenschaftsName} ist für den Typen Liste nicht definiert.")
  }

  override fun rufeMethodeAuf(aufruf: IM_AST.Satz.Ausdruck.IAufruf, injection: Interpretierer.InterpretInjection): Objekt {
    return when (aufruf.name) {
      "addiere mich mit dem Operanden",
      "addiere mich mit der Liste" -> addiereMichMitDemOperanden(injection)
      "enthalten das Element" -> enthaltenDenTyp(injection)
      "füge das Element hinzu" -> fügeDasElementHinzu(injection)
      "hole den Typ mit dem Index",
      "hole das Element mit dem Index" -> holeDasElementMitDemIndex(aufruf, injection)
      "setze den Index auf den Typ",
      "setze den Index auf das Element" -> setzeDenIndexAufDenTyp(aufruf, injection)
      "entferne an dem Index" -> entferneAnDemIndex(injection)
      else -> super.rufeMethodeAuf(aufruf, injection)
    }
  }

  private fun addiereMichMitDemOperanden(injection: Interpretierer.InterpretInjection): Objekt {
    val andereListe = injection.umgebung.leseVariable("Operand") as Liste
    return this + andereListe
  }

  private fun enthaltenDenTyp(injection: Interpretierer.InterpretInjection): Objekt {
    val element = injection.umgebung.leseVariable("Element")
    return Boolean(elemente.contains(element))
  }

  private fun fügeDasElementHinzu(injection: Interpretierer.InterpretInjection): Objekt {
    val element = injection.umgebung.leseVariable("Element")!!
    elemente.add(element)
    return Nichts
  }

  private fun holeDasElementMitDemIndex(aufruf: IM_AST.Satz.Ausdruck.IAufruf, injection: Interpretierer.InterpretInjection): Objekt {
    val index = (injection.umgebung.leseVariable("Index") as Zahl).toInt()

    return prüfeIndex(index, aufruf, injection) ?:
        this.elemente[index]
  }

  private fun setzeDenIndexAufDenTyp(aufruf: IM_AST.Satz.Ausdruck.IAufruf, injection: Interpretierer.InterpretInjection): Objekt {
    val index = (injection.umgebung.leseVariable("Index") as Zahl).toInt()
    return prüfeIndex(index, aufruf, injection) ?: {
      val wert = injection.umgebung.leseVariable("Element")!!
      this.elemente[index] = wert
      Nichts
    }()
  }

  private fun prüfeIndex(index: Int, aufruf: IM_AST.Satz.Ausdruck.IAufruf, injection: Interpretierer.InterpretInjection): Objekt? {
    return if (index >= elemente.size) {
      injection.werfeFehler(
          "Index außerhalb des Bereichs. Der Index ist $index, doch die Länge der Liste ist ${elemente.size}.\n", "IndexFehler", aufruf.token)
    } else {
      null
    }
  }

  private fun entferneAnDemIndex(injection: Interpretierer.InterpretInjection): Objekt {
    val index = injection.umgebung.leseVariable("Index") as Zahl
    elemente.removeAt(index.toInt())
    return Nichts
  }
}