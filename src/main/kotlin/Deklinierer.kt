import kotlinx.coroutines.*
import java.io.File
import java.lang.Integer.min
import java.util.*

data class Deklination(
    val genus: Genus,
    private val singular: Array<String>,
    val plural: Array<String>
) {
  val nominativSingular: String get() = singular[0]

  fun getForm(kasus: Kasus, numerus: Numerus): String {
    val index = kasus.ordinal
    return if (numerus == Numerus.SINGULAR) {
      singular[index]
    } else {
      plural[index]
    }
  }

  fun getNumerus(wort: String): EnumSet<Numerus> {
    val numerus = EnumSet.noneOf(Numerus::class.java)
    if (singular.contains(wort)) {
      numerus.add(Numerus.SINGULAR)
    }
    if (plural.contains(wort)) {
      numerus.add(Numerus.PLURAL)
    }

    if (numerus.isEmpty()) {
      throw Wörterbuch.WortNichtGefunden(wort)
    }

    return numerus
  }

  val fallSequenz get() = singular.asSequence() + plural.asSequence()

  override fun toString(): String {
    val anfang = "Deklination ${genus.anzeigeName}"
    val allesGleichBeiSingular = singular.all { it == singular[0] }
    val sing = if (allesGleichBeiSingular) "Singular(${singular[0]})" else "Singular(${singular.joinToString(", ")})"
    val allesGleichBeiPlural = plural.all { it == plural[0] }
    val plur = when {
      allesGleichBeiPlural -> if (plural[0] == sing) "" else "Plural(${plural[0]})"
      else -> "Plural(${plural.joinToString(", ")})"
    }
    return "$anfang $sing $plur"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Deklination

    if (genus != other.genus) return false
    if (!singular.contentEquals(other.singular)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = genus.hashCode()
    result = 31 * result + singular.contentHashCode()
    return result
  }
}

typealias DudenAnfrage = Pair<TypedToken<TokenTyp.BEZEICHNER_GROSS>, Deferred<Deklination>>

class Deklinierer(startDatei: File): PipelineKomponente(startDatei) {
  val ast = Parser(startDatei).parse()
  val wörterbuch = Wörterbuch()

  fun deklaniere() = runBlocking {
    val dudenAnfragen = mutableMapOf<String, MutableList<DudenAnfrage>>()
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
        dudenAnfragen.computeIfAbsent(knoten.wort.dateiPfad) { mutableListOf() } += (knoten.wort to async(Dispatchers.IO) {
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

  private suspend fun löseDudenAnfragenAuf(anfragenNachDatei: Map<String, List<DudenAnfrage>>) {
    // löse Duden-Anfragen auf und überschreibe Duden-Deklination mit manueller Deklination im Source-Code
    for ((datei, dudenAnfragen) in anfragenNachDatei.entries) {
      val quellCodeDatei = File(datei)
      val quellCodeZeilen = quellCodeDatei.readLines().toMutableList()
      for ((token, anfrage) in dudenAnfragen) {
        try {
          val deklination = anfrage.await()
          wörterbuch.fügeDeklinationHinzu(deklination)
          // minus 1, da die Zeilen bei 1 anfangen zu zählen
          quellCodeZeilen[token.anfang.zeile-1] = deklination.toString()
        }
        catch (fehler: Wörterbuch.DoppelteDeklinationFehler) {
          // TODO: Bei doppelten Deklinationen sollte später vielleicht eine Warnung ausgegeben werden
        }
      }
      quellCodeDatei.writeText(quellCodeZeilen.joinToString("\n"))
    }
  }
  
  fun holeDeklination(nomen: AST.Nomen): Deklination {
    try {
      return wörterbuch.holeDeklination(nomen.hauptWort)
    } catch (error: Wörterbuch.WortNichtGefunden) {
      throw GermanScriptFehler.UnbekanntesWort(nomen.bezeichner.toUntyped(), nomen.hauptWort)
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
      // die minus 1 ist da weil die anderen Formen eines Worts nicht umbedingt immer mit dem
      // Nominativ anfangen müssen
      // TODO: Ich weiß nicht ob diese Lösung stabil ist. Denk dir vielleicht etwas anderes aus.
      val maxLength = min(wort.length, nominativSingular.length) - 1
      if (wortVergleichBerücksichtigeUmlaute(wort.substring(0, maxLength),
              deklination.nominativSingular.substring(0, maxLength)) == 0) {
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

  wörterbuch.fügeDeklinationHinzu(Deklination(Genus.MASKULINUM, arrayOf("Keks", " Keks", "Keks", "Keks"), arrayOf("Kekse", "Kekse", "Keksen", "Kekse")))
  wörterbuch.fügeDeklinationHinzu(Deklination(Genus.MASKULINUM, arrayOf("Dose", "Dose", "Dose", "Dose"), arrayOf( "Dosen", "Dosen", "Dosen", "Dosen")))
  wörterbuch.fügeDeklinationHinzu(Deklination(Genus.MASKULINUM, arrayOf("Baum", "Baums", "Baum", "Baum"), arrayOf("Bäume", "Bäume", "Bäumen", "Bäumen")))
  wörterbuch.fügeDeklinationHinzu(Deklination(Genus.MASKULINUM, arrayOf("Uhr", "Uhr", "Uhr", "Uhr"),arrayOf("Uhren", "Uhren", "Uhren", "Uhren" )))
  wörterbuch.fügeDeklinationHinzu(Deklination(Genus.MASKULINUM, arrayOf("Baumhaus", "Baumhauses", "Baumhaus", "Baumhaus"), arrayOf("Baumhäuser", "Baumhäuser", "Baumhäusern", "Baumhäuser")))

  wörterbuch.print()


  println(wörterbuch.holeDeklination("Bäume"))
  println(wörterbuch.holeDeklination("Baumhaus"))
  println(wörterbuch.holeDeklination("Dose"))
  println(wörterbuch.holeDeklination("Uhr"))
}

fun main() {
  // wörterBuchTest()

  val deklanierer = Deklinierer(File("./iterationen/iter_2/code.gms"))
  deklanierer.deklaniere()
  deklanierer.druckeWörterbuch()
}