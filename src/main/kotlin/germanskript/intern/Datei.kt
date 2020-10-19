package germanskript.intern

import germanskript.*
import java.io.BufferedWriter
import java.io.File

class Datei(typ: Typ.Compound.KlassenTyp, val eigenschaften: MutableMap<String, Wert>): Wert.Objekt(typ) {

  lateinit var file: File

  override fun rufeMethodeAuf(
      aufruf: AST.IAufruf,
      aufrufStapel: Interpretierer.AufrufStapel,
      umgebung: Umgebung<Wert>,
      aufrufCallback: AufrufCallback): Wert {

    return when (aufruf.vollerName!!) {
      "erstelle die Datei" -> konstruktor()
      "lese die Zeilen" -> leseZeilen()
      "hole den Schreiber" -> holeSchreiber()
      else -> throw Exception("Die Methode '${aufruf.vollerName!!}' ist nicht definiert!")
    }
  }

  override fun holeEigenschaft(eigenschaftsName: String): Wert {
    return eigenschaften.getValue(eigenschaftsName)
  }

  override fun setzeEigenschaft(eigenschaftsName: String, wert: Wert) {

  }

  private fun konstruktor(): Nichts {
    file = File((eigenschaften.getValue("DateiName") as Zeichenfolge).zeichenfolge)
    return Nichts
  }

  private fun leseZeilen(): Liste {
    val zeilen = file.readLines().map<String, Wert> { zeile -> Zeichenfolge(zeile) }.toMutableList()
    return Liste(Typ.Compound.KlassenTyp.Liste(listOf(zeichenFolgenTypArgument)), zeilen)
  }

  private fun holeSchreiber() : Schreiber {
    return Schreiber(Typisierer.schreiberTyp, file.bufferedWriter())
  }
}

class Schreiber(typ: Typ.Compound.KlassenTyp, private val writer: BufferedWriter): Wert.Objekt(typ) {
  override fun rufeMethodeAuf(aufruf: AST.IAufruf, aufrufStapel: Interpretierer.AufrufStapel, umgebung: Umgebung<Wert>, aufrufCallback: AufrufCallback): Wert {
    return when(aufruf.vollerName!!) {
      "schreibe die Zeile" -> schreibeDieZeile(umgebung)
      "schreibe die Zeichenfolge" -> schreibeDieZeichenfolge(umgebung)
      "füge die Zeile hinzu" -> fügeDieZeileHinzu(umgebung)
      "füge die Zeichenfolge hinzu" -> fügeDieZeichenfolgeHinzu(umgebung)
      "schließe mich" -> schließe()
      else -> super.rufeMethodeAuf(aufruf, aufrufStapel, umgebung, aufrufCallback)
    }
  }

  override fun holeEigenschaft(eigenschaftsName: String): Wert {
    throw Exception("Die Klasse Schreiber hat keine Eigenschaften!")
  }

  override fun setzeEigenschaft(eigenschaftsName: String, wert: Wert) {
    TODO("Not yet implemented")
  }

  private fun schreibeDieZeichenfolge(umgebung: Umgebung<Wert>): Nichts {
    val zeile = umgebung.leseVariable("Zeichenfolge")!!.wert as Zeichenfolge
    writer.write(zeile.zeichenfolge)
    return Nichts
  }

  private fun schreibeDieZeile(umgebung: Umgebung<Wert>): Nichts {
    val zeile = umgebung.leseVariable("Zeile")!!.wert as Zeichenfolge
    writer.write(zeile.zeichenfolge)
    writer.newLine()
    return Nichts
  }

  private fun fügeDieZeichenfolgeHinzu(umgebung: Umgebung<Wert>): Nichts {
    val zeile = umgebung.leseVariable("Zeichenfolge")!!.wert as Zeichenfolge
    writer.append(zeile.zeichenfolge)
    return Nichts
  }

  private fun fügeDieZeileHinzu(umgebung: Umgebung<Wert>): Nichts {
    val zeile = umgebung.leseVariable("Zeile")!!.wert as Zeichenfolge
    writer.appendln(zeile.zeichenfolge)
    return Nichts
  }

  private fun schließe(): Nichts {
    writer.close()
    return Nichts
  }
}