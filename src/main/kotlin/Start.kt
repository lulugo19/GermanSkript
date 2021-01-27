import germanskript.GermanSkriptFehler
import germanskript.Interpretierer

import java.io.File

fun main(args: Array<String>) {
  if (args.isEmpty()) {
    System.err.println("Bitte gebe eine Datei als Argument an!")
    return
  }
  val dateiPfad = args[0]
  val datei = File(dateiPfad)
  if (!datei.exists()) {
    System.err.println("Die Datei '$dateiPfad' existiert nicht. Gebe bitte eine existierende Datei an.")
    return
  }
  try {
    val interpreter = Interpretierer(datei)
    interpreter.interpretiere()
  } catch (fehler: GermanSkriptFehler) {
    System.err.println(fehler.message!!)
  }
}