import kotlinx.coroutines.*
import java.io.File
import java.util.LinkedList
import java.lang.Integer.min

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

class Deklinierer(dateiPfad: String): PipelineKomponente(dateiPfad) {
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
      return wörterbuch.holeDeklination(nomen.bezeichner.typ.hauptWort!!)
    } catch (error: Wörterbuch.WortNichtGefunden) {
      throw GermanScriptFehler.UnbekanntesWort(nomen.bezeichner.toUntyped())
    }
  }

  fun druckeWörterbuch() = wörterbuch.print()
}

class Wörterbuch {
  
  class WortNichtGefunden(wort: String): Error("Wort '$wort' nicht gefunden!")
  class DoppelteDeklinationFehler(deklination: Deklination): Error("Doppelte Deklination: $deklination!")
  
  private val tabelle = LinkedList<Deklination>()

  // gibt Wörterbuch zurück

  fun fügeDeklinationHinzu(deklination: Deklination) {
    if (tabelle.isEmpty()) {
      tabelle.add(deklination)
      return
    }

    val wort = deklination.nominativSingular

    var lo = 0
    var hi = tabelle.size

    while (lo < hi) {
      val mid = ((lo.toDouble() + hi) / 2).toInt()
      if (tabelle[mid].nominativSingular < wort) {
        lo = mid + 1
      } else {
        hi = mid
      }
    }

    if (lo < tabelle.size && tabelle[lo].nominativSingular == wort) {
      throw DoppelteDeklinationFehler(deklination)
    }
    tabelle.add(lo, deklination)
  }

  fun holeDeklination(wort: String): Deklination {
    var lo = 0
    var hi = tabelle.size
    while (lo < hi) {
      val mid = ((lo.toDouble() + hi) / 2).toInt()
      val deklination = tabelle[mid]
      val nominativSingular = deklination.nominativSingular
      val maxLength = min(wort.length, nominativSingular.length)
      if (wortVergleichBerücksichtigeUmlaute(wort.substring(0, maxLength), deklination.nominativSingular) == 0) {
        if (deklination.fallSequenz.contains(wort)) {
          return deklination
        } else {
          if (nominativSingular < wort) {
            lo = mid + 1
          } else {
            hi = mid
          }
        }
      }
      else if (wortVergleichBerücksichtigeUmlaute(deklination.nominativSingular, wort) == -1) {
        lo = mid + 1
      } else {
        hi = mid
      }
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
    return word1.substring(minlen).compareTo(word2.substring(minlen))
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

  val deklanierer = Deklinierer("./iterationen/iter_2/code.gms")
  deklanierer.deklaniere()
  deklanierer.druckeWörterbuch()
}