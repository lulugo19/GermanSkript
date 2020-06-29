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
    return if (index < 3) Numerus.SINGULAR else Numerus.PLURAL
  }

  val fallSequenz get() = fälle.asSequence()

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

class Deklanierer(quellCode: String) {
  val ast = Parser(quellCode).parse()
  private val wörterbuch = Wörterbuch()

  fun deklaniere() {
    ast.visit { knoten ->
      if (knoten is AST.Definition.DeklinationsDefinition) {
        wörterbuch.fügeDeklinationHinzu(knoten.deklination)
      }
    }
  }
  
  fun holeDeklination(nomen: AST.Nomen): Deklination {
    try {
      return wörterbuch.holeDeklination(nomen.bezeichner.wert)
    } catch (error: Wörterbuch.WortNichtGefunden) {
      throw GermanScriptFehler.UnbekanntesWort(nomen.bezeichner.toUntyped())
    }
  }
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

fun main() {
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