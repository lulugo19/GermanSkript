package germanskript.imm.intern

import germanskript.*
import germanskript.imm.IMM_AST
import germanskript.imm.Interpretierer
import java.text.ParseException
import kotlin.Boolean

data class Zeichenfolge(val zeichenfolge: String): Objekt(BuildIn.IMMKlassen.zeichenfolge, BuildIn.Klassen.zeichenfolge), Comparable<Zeichenfolge> {

  override fun rufeMethodeAuf(aufruf: IMM_AST.Satz.Ausdruck.IAufruf, injection: Interpretierer.InterpretInjection): Objekt {
    return when (aufruf.name) {
      "addiere mich mit dem Operanden",
      "addiere mich mit der Zeichenfolge" -> addiereMichMitDemOperanden(injection)
      "als Zahl" -> konvertiereInZahl(aufruf, injection)
      "vergleiche mich mit dem Typ",
      "vergleiche mich mit der Zeichenfolge" -> vergleicheMichMitDerZeichenfolge(injection)
      "hole den Typ mit dem Index",
      "hole die Zeichenfolge mit dem Index" -> holeDenTypMitDemIndex(aufruf, injection)
      "code an dem Index" -> codeAnDemIndex(aufruf, injection)
      "buchstabiere mich groß" -> buchstabiereMichGroß()
      "buchstabiere mich klein" -> buchstabierMichKlein()
      "trenne mich zwischen dem Separator" -> trenneMichZwischenDemSeperator(injection)
      else -> super.rufeMethodeAuf(aufruf, injection)
    }
  }

  override fun toString(): String = zeichenfolge

  override fun hashCode(): Int = zeichenfolge.hashCode()

  override fun equals(other: Any?): Boolean {
    if (other !is Zeichenfolge) return false
    return zeichenfolge == other.zeichenfolge
  }

  operator fun plus(zeichenfolge: Zeichenfolge): Zeichenfolge = Zeichenfolge(this.zeichenfolge + zeichenfolge.zeichenfolge)
  override fun compareTo(other: Zeichenfolge): Int = this.zeichenfolge.compareTo(other.zeichenfolge)

  override fun holeEigenschaft(eigenschaftsName: String): Objekt {
    if (eigenschaftsName == "Länge") {
      return Zahl(zeichenfolge.length.toDouble())
    } else {
      throw Exception("Ungültige Eigenschaft '$eigenschaftsName' der Klasse 'Zeichenfolge'.")
    }
  }

  private fun holeDenTypMitDemIndex(aufruf: IMM_AST.Satz.Ausdruck.IAufruf, injection: Interpretierer.InterpretInjection): Objekt {
    val index = (injection.umgebung.leseVariable("Index") as Zahl).zahl.toInt()
    return if (index >= zeichenfolge.length) {
      injection.werfeFehler(
          "Index außerhalb des Bereichs. Der Index ist $index, doch die Länge der Zeichenfolge ist ${zeichenfolge.length}.\n", "IndexFehler", aufruf.token)
    } else {
      Zeichenfolge(this.zeichenfolge[index].toString())
    }
  }

  private fun addiereMichMitDemOperanden(injection: Interpretierer.InterpretInjection): Objekt {
    val zeichenfolge = injection.umgebung.leseVariable("Zeichenfolge") as Zeichenfolge
    return this + zeichenfolge
  }

  private fun konvertiereInZahl(aufruf: IMM_AST.Satz.Ausdruck.IAufruf, injection: Interpretierer.InterpretInjection): Objekt {
    return try {
      Zahl(zeichenfolge)
    }
    catch (parseFehler: ParseException) {
      injection.werfeFehler("Die Zeichenfolge '${zeichenfolge}' kann nicht in eine Zahl konvertiert werden.", "KonvertierungsFehler", aufruf.token)
    }
  }

  private fun vergleicheMichMitDerZeichenfolge(injection: Interpretierer.InterpretInjection): Zahl {
    val zeichenfolge = injection.umgebung.leseVariable("Zeichenfolge") as Zeichenfolge
    return Zahl(this.zeichenfolge.compareTo(zeichenfolge.zeichenfolge).toDouble())
  }

  private fun codeAnDemIndex(aufruf: IMM_AST.Satz.Ausdruck.IAufruf, injection: Interpretierer.InterpretInjection): Objekt {
    val index = (injection.umgebung.leseVariable("Index") as Zahl).toInt()
    if (index < 0 || index >= zeichenfolge.length)
      throw GermanSkriptFehler.LaufzeitFehler(aufruf.token, injection.aufrufStapel.toString(),
          "Index außerhalb des Bereichs. Der Index ist $index, doch die Länge der Zeichenfolge ist ${zeichenfolge.length}.\n")
    return Zahl(zeichenfolge[index].toDouble())
  }

  private fun buchstabiereMichGroß() = Zeichenfolge(zeichenfolge.toUpperCase())

  private fun buchstabierMichKlein() = Zeichenfolge(zeichenfolge.toLowerCase())

  private fun trenneMichZwischenDemSeperator(injection: Interpretierer.InterpretInjection): Objekt {
    val separator = injection.umgebung.leseVariable("Separator") as Zeichenfolge

    return Liste(Typ.Compound.Klasse(BuildIn.Klassen.liste ,listOf(zeichenFolgenTypArgument)),
        zeichenfolge.split(separator.zeichenfolge).map { Zeichenfolge(it) }.toMutableList())
  }
}