package germanskript.intern

import germanskript.*

data class Zeichenfolge(val zeichenfolge: String): Wert.Objekt.InternesObjekt(Typ.Compound.KlassenTyp.BuildInType.Zeichenfolge), Comparable<Zeichenfolge> {

  override fun rufeMethodeAuf(aufruf: AST.IAufruf, aufrufStapel: Interpretierer.AufrufStapel, umgebung: Umgebung<Wert>, aufrufCallback: AufrufCallback): Wert {
    return when (aufruf.vollerName!!) {
      "code an dem Index" -> codeAnDemIndex(aufruf, aufrufStapel, umgebung)
      "buchstabiere mich groß" -> buchstabiereMichGroß()
      "buchstabiere mich klein" -> buchstabierMichKlein()
      "trenne mich zwischen dem Separator" -> trenneMichZwischenDemSeperator(umgebung)
      else -> throw Exception("Ungültige Methode '${aufruf.vollerName!!}' für die Klasse Zeichenfolge!")
    }
  }

  override fun toString(): String = zeichenfolge

  operator fun plus(zeichenfolge: Zeichenfolge) = this.zeichenfolge + zeichenfolge.zeichenfolge
  override fun compareTo(other: Zeichenfolge): Int = this.zeichenfolge.compareTo(other.zeichenfolge)

  override fun holeEigenschaft(eigenschaftsName: String): Wert {
    if (eigenschaftsName == "Länge") {
      return Zahl(zeichenfolge.length.toDouble())
    } else {
      throw Exception("Ungültige Eigenschaft '$eigenschaftsName' der Klasse 'Zeichenfolge'.")
    }
  }

  override fun setzeEigenschaft(eigenschaftsName: String, wert: Wert) {
    // Eine Zeichenfolge ist immutable
    TODO("Not yet implemented")
  }

  private fun codeAnDemIndex(aufruf: AST.IAufruf, aufrufStapel: Interpretierer.AufrufStapel, umgebung: Umgebung<Wert>): Wert {
    val index = (umgebung.leseVariable("Index")!!.wert as Zahl).toInt()
    if (index < 0 || index >= zeichenfolge.length)
      throw GermanSkriptFehler.LaufzeitFehler(aufruf.token, aufrufStapel.toString(),
          "Index außerhalb des Bereichs. Der Index ist $index, doch die Länge der Zeichenfolge ist ${zeichenfolge.length}.\n")
    return Zahl(zeichenfolge[index].toDouble())
  }

  private fun buchstabiereMichGroß() = Zeichenfolge(zeichenfolge.toUpperCase())

  private fun buchstabierMichKlein() = Zeichenfolge(zeichenfolge.toLowerCase())

  private fun trenneMichZwischenDemSeperator(umgebung: Umgebung<Wert>): Wert {
    val separator = umgebung.leseVariable("Separator")!!.wert as Zeichenfolge

    return Liste(Typ.Compound.KlassenTyp.Liste(listOf(zeichenFolgenTypArgument)),
        zeichenfolge.split(separator.zeichenfolge).map { Zeichenfolge(it) }.toMutableList())
  }
}