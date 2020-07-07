import kotlinx.coroutines.*
import java.io.File
import java.lang.Integer.min
import kotlin.math.floor

data class Deklination(
    val genus: Genus,
    private val fälle: Array<String>
) {
  val nominativSingular: String get() = fälle[0]

  fun getForm(kasus: Kasus, numerus: Numerus): String {
    val index = kasus.ordinal + if (numerus == Numerus.SINGULAR) 0 else 4
    return fälle[index]
  }

  fun getNumerus(wort: String): Numerus {
    val index = fälle.indexOf(wort)
    if (index == -1) {
      throw Wörterbuch.WortNichtGefunden(wort)
    }
    return if (index < 4) Numerus.SINGULAR else Numerus.PLURAL
  }

  val fallSequenz get() = fälle.asSequence()

  override fun toString(): String {
    return "Deklination ${genus.anzeigeName} " +
        "Singular(${fälle[0]}, ${fälle[1]}, ${fälle[2]}, ${fälle[3]}) " +
        "Plural(${fälle[4]}, ${fälle[5]}, ${fälle[6]}, ${fälle[7]})"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Deklination

    if (genus != other.genus) return false
    if (!fälle.contentEquals(other.fälle)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = genus.hashCode()
    result = 31 * result + fälle.contentHashCode()
    return result
  }
}

typealias DudenAnfrage = Pair<TypedToken<TokenTyp.BEZEICHNER_GROSS>, Deferred<Deklination>>

class Deklanierer(dateiPfad: String): PipelineKomponente(dateiPfad) {
  val ast = Parser(dateiPfad).parse()
  val wörterbuch = Wörterbuch()

  fun deklaniere() = runBlocking {
    val dudenAnfragen = mutableListOf<DudenAnfrage>()
    ast.definitionen.visit { knoten ->
      if (knoten is AST.Definition.DeklinationsDefinition.Definition) {
        try {
          wörterbuch.fügeDeklinationHinzu(knoten.deklination)
        }
        catch (fehler: Wörterbuch.DoppelteDeklinationFehler) {
          // Doppelte Deklinationen werden einfach ingoriert
          // TODO: Vielleicht sollte eine Warnung ausgegeben werden
        }
      }
      else if (knoten is AST.Definition.DeklinationsDefinition.Duden) {
        dudenAnfragen += (knoten.wort to async(Dispatchers.IO) {
            val wort = knoten.wort
            try {
              return@async Duden.dudenGrammatikAnfrage(wort.wert)
            }
            catch (fehler: Duden.DudenFehler) {
            when (fehler) {
              is Duden.DudenFehler.NotFoundFehler, is Duden.DudenFehler.ParseFehler ->
                throw GermanScriptFehler.DudenFehler.WortNichtGefundenFehler(wort.toUntyped(), wort.wert)
              is Duden.DudenFehler.KeinInternetFehler -> throw GermanScriptFehler.DudenFehler.Verbindungsfehler(wort.toUntyped(), wort.wert)
            }
          }
        })
      }

      // only visit on global level
      return@visit false
    }

    löseDudenAnfragenAuf(dudenAnfragen)
  }

  suspend fun löseDudenAnfragenAuf(dudenAnfragen: List<DudenAnfrage>) {
    // löse Duden-Anfragen auf und überschreibe Duden-Deklination mit manueller Deklination im Source-Code
    val quellCodeDatei = File(dateiPfad)
    val quellCodeZeilen = quellCodeDatei.readLines().toMutableList()
    for ((token, anfrage) in dudenAnfragen) {
      try {
        val deklination = anfrage.await()
        wörterbuch.fügeDeklinationHinzu(deklination)
        // minus 1, da die Zeilen bei 1 anfangen zu zählen
        quellCodeZeilen[token.anfang.zeile-1] = deklination.toString()
      }
      catch (fehler: Wörterbuch.DoppelteDeklinationFehler) {

      }
    }
    quellCodeDatei.writeText(quellCodeZeilen.joinToString("\n"))
  }
  
  fun holeDeklination(nomen: AST.Nomen): Deklination {
    try {
      return wörterbuch.holeDeklination(nomen.bezeichner.wert)
    } catch (error: Wörterbuch.WortNichtGefunden) {
      throw GermanScriptFehler.UnbekanntesWort(nomen.bezeichner.toUntyped())
    }
  }

  fun druckeWörterbuch() = wörterbuch.print()
}

class Wörterbuch {
  
  class WortNichtGefunden(wort: String): Error("Wort '$wort' nicht gefunden!")
  class DoppelteDeklinationFehler(deklination: Deklination): Error("Doppelte Deklination: $deklination!")
  
  private val tabelle = MutableList<Deklination>(0) {Deklination(Genus.NEUTRUM, emptyArray())}

  // gibt Wörterbuch zurück

  fun fügeDeklinationHinzu(deklination: Deklination) {
    if (tabelle.isEmpty()) {
      tabelle.add(deklination)
      return
    }

    val nominativ = deklination.nominativSingular

    var min = 0
    var max = tabelle.size

    while (min != max) {
      val avg = floor((min.toDouble() + max) / 2).toInt()
      when {
        nominativ > tabelle[avg].nominativSingular -> {
          when {
            avg == tabelle.size - 1 -> tabelle.add(deklination).also { return }
            nominativ < tabelle[avg+1].nominativSingular -> tabelle.add(avg, deklination).also { return }
            else -> min = avg
          }
        }
        nominativ < tabelle[avg].nominativSingular -> {
          when {
            avg == 0 -> tabelle.add(0, deklination).also { return }
            nominativ > tabelle[avg-1].nominativSingular -> tabelle.add(avg, deklination).also { return }
            else -> max = avg
          }
        }
        else -> throw DoppelteDeklinationFehler(deklination)
      }
    }
  }

  fun holeDeklination(wort: String): Deklination {
    var min = 0
    var max = tabelle.size
    var lastDiff = 0
    while (min != max) {
      val avg = floor((min.toDouble() + max) / 2).toInt()
      val deklination = tabelle[avg]
      val nominativSingular = deklination.nominativSingular
      val maxLength = min(wort.length, nominativSingular.length)
      if (wortVergleichBerücksichtigeUmlaute(wort.substring(0, maxLength), deklination.nominativSingular) == 0) {
        if (deklination.fallSequenz.contains(wort)) {
          return deklination
        } else {
          max = avg
        }
      }
      else if (wortVergleichBerücksichtigeUmlaute(wort, deklination.nominativSingular) == -1) {
        max = avg
      } else {
        min = avg
      }
      val newDiff = max - min
      if (newDiff == lastDiff) {
        break
      }
      lastDiff = newDiff
    }

    throw WortNichtGefunden(wort)
  }

  fun print() {
    for (row in tabelle) {
      println(row.genus.toString() + ": " + row.fallSequenz.joinToString(" | "))
    }
  }

  private object Static {
    const val umlaute = "öäüÖÄÜ"
    const val vokale = "oauOAU"
  }

  fun wortVergleichBerücksichtigeUmlaute(word1: String, word2: String): Int {
    val minlen = min(word1.length, word2.length)
    for (i in 0 until minlen) {
      val c = zeichenVergleichBerücksichtigeUmlaute(word1[i], word2[i])
      if (c != 0) return c
    }
    return when {
      word1.length < word2.length -> -1
      word1.length > word2.length -> 1
      else -> 0
    }
  }

  private fun zeichenVergleichBerücksichtigeUmlaute(a: Char, b: Char): Int {
    if (a == b) return 0
    for (j in Static.umlaute.indices) {
      when {
        a == Static.umlaute[j] && b == Static.vokale[j] -> return 0
        b == Static.umlaute[j] && a == Static.vokale[j] -> return 0
      }
    }
    return a.compareTo(b)
  }
}

fun wörterBuchTest() {
  val wörterbuch = Wörterbuch()

  // Bäume sollte kleiner Baumhaus sein, dies können wir mit dem normalen Vergleich nicht erreichen, deswegen brauchen wir den Special Vergleich
  println( "Bäume" < "Baumhaus")
  println(wörterbuch.wortVergleichBerücksichtigeUmlaute("Bäume", "Baumhaus"))

  wörterbuch.fügeDeklinationHinzu(Deklination(Genus.MASKULINUM, arrayOf("Keks", " Keks", "Keks", "Keks", "Kekse", "Kekse", "Keksen", "Kekse")))
  wörterbuch.fügeDeklinationHinzu(Deklination(Genus.MASKULINUM, arrayOf("Dose", "Dose", "Dose", "Dose", "Dosen", "Dosen", "Dosen", "Dosen")))
  wörterbuch.fügeDeklinationHinzu(Deklination(Genus.MASKULINUM, arrayOf("Baum", "Baums", "Baum", "Baum", "Bäume", "Bäume", "Bäumen", "Bäumen")))
  wörterbuch.fügeDeklinationHinzu(Deklination(Genus.MASKULINUM, arrayOf("Uhr", "Uhr", "Uhr", "Uhr", "Uhren", "Uhren", "Uhren", "Uhren")))
  wörterbuch.fügeDeklinationHinzu(Deklination(Genus.MASKULINUM, arrayOf("Baumhaus", "Baumhauses", "Baumhaus", "Baumhaus", "Baumhäuser", "Baumhäuser", "Baumhäusern", "Baumhäuser")))

  wörterbuch.print()


  println(wörterbuch.holeDeklination("Bäume"))
  println(wörterbuch.holeDeklination("Baumhaus"))
  println(wörterbuch.holeDeklination("Dose"))
  println(wörterbuch.holeDeklination("Uhr"))
}

fun main() {
  // wörterBuchTest()

  val deklanierer = Deklanierer("./iterationen/iter_1/code.gms")
  deklanierer.deklaniere()
  deklanierer.druckeWörterbuch()
}