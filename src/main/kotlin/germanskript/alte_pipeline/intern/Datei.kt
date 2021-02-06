package germanskript.alte_pipeline.intern

import germanskript.*
import germanskript.alte_pipeline.InterpretInjection
import java.io.BufferedWriter
import java.io.File

class Datei(typ: Typ.Compound.Klasse): Objekt(typ) {

  lateinit var file: File

  override fun rufeMethodeAuf(
      aufruf: AST.IAufruf,
      injection: InterpretInjection): Objekt {

    return when (aufruf.vollerName!!) {
      "erstelle die Datei" -> konstruktor(injection)
      "lese die Zeilen" -> leseZeilen()
      "hole den Schreiber" -> holeSchreiber()
      else -> throw Exception("Die Methode '${aufruf.vollerName!!}' ist nicht definiert!")
    }
  }

  private fun konstruktor(injection: InterpretInjection): Nichts {
    // Der Dateiname wird relativ zur Startdatei aufgelöst
    file = injection.startDatei.parentFile
        .resolve(File((eigenschaften.getValue("DateiName") as germanskript.intern.Zeichenfolge).zeichenfolge))
    return Nichts
  }

  private fun leseZeilen(): Liste {
    val zeilen = file.readLines().map<String, Objekt> { zeile -> Zeichenfolge(zeile) }.toMutableList()
    return Liste(Typ.Compound.Klasse(BuildIn.Klassen.liste ,listOf(zeichenFolgenTypArgument)), zeilen)
  }

  private fun holeSchreiber() : Schreiber {
    return Schreiber(BuildIn.Klassen.schreiber, file.bufferedWriter())
  }
}

class Schreiber(typ: Typ.Compound.Klasse, private val writer: BufferedWriter): Objekt(typ) {
  override fun rufeMethodeAuf(aufruf: AST.IAufruf, injection: InterpretInjection): Objekt {
    return when(aufruf.vollerName!!) {
      "schreibe die Zeile" -> schreibeDieZeile(injection)
      "schreibe die Zeichenfolge" -> schreibeDieZeichenfolge(injection)
      "füge die Zeile hinzu" -> fügeDieZeileHinzu(injection)
      "füge die Zeichenfolge hinzu" -> fügeDieZeichenfolgeHinzu(injection)
      "schließe mich" -> schließe()
      else -> super.rufeMethodeAuf(aufruf, injection)
    }
  }

  override fun holeEigenschaft(eigenschaftsName: String): Objekt {
    throw Exception("Die Klasse Schreiber hat keine Eigenschaften!")
  }

  override fun setzeEigenschaft(eigenschaftsName: String, wert: Objekt) {
    TODO("Not yet implemented")
  }

  private fun schreibeDieZeichenfolge(injection: InterpretInjection): Nichts {
    val zeile = injection.umgebung.leseVariable("Zeichenfolge")!!.wert as Zeichenfolge
    writer.write(zeile.zeichenfolge)
    return Nichts
  }

  private fun schreibeDieZeile(injection: InterpretInjection): Nichts {
    val zeile = injection.umgebung.leseVariable("Zeile")!!.wert as Zeichenfolge
    writer.write(zeile.zeichenfolge)
    writer.newLine()
    return Nichts
  }

  private fun fügeDieZeichenfolgeHinzu(injection: InterpretInjection): Nichts {
    val zeile = injection.umgebung.leseVariable("Zeichenfolge")!!.wert as Zeichenfolge
    writer.append(zeile.zeichenfolge)
    return Nichts
  }

  private fun fügeDieZeileHinzu(injection: InterpretInjection): Nichts {
    val zeile = injection.umgebung.leseVariable("Zeile")!!.wert as Zeichenfolge
    writer.appendln(zeile.zeichenfolge)
    return Nichts
  }

  private fun schließe(): Nichts {
    writer.close()
    return Nichts
  }
}