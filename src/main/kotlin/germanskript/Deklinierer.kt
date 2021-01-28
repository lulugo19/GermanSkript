package germanskript

import germanskript.util.istVokal
import kotlinx.coroutines.*
import java.io.File
import java.lang.Integer.min
import java.util.*

data class Deklination(
    val genus: Genus,
    val singular: Array<String>,
    val plural: Array<String>,
    val istNominalisiertesAdjektiv: Boolean = false
) {
  val nominativSingular: String get() = singular[0]

  fun holeForm(kasus: Kasus, numerus: Numerus): String {
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
    val anfang = "Deklination $genus"
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

data class DudenAnfrage(
    val dudenDeklination: AST.Definition.DeklinationsDefinition.Duden,
    val anfrage: Deferred<Deklination>
)

class Deklinierer(startDatei: File): PipelineKomponente(startDatei) {
  val modulAuflöser = ModulAuflöser(startDatei)
  val ast = modulAuflöser.ast

  fun deklaniere() = runBlocking {
    modulAuflöser.löseModulPfadeAuf()
    val dudenAnfragen = mutableMapOf<String, MutableList<DudenAnfrage>>()
    ast.visit { knoten ->
      when (knoten) {
        is AST.Programm, is AST.Definition.Modul -> return@visit true
        is AST.DefinitionsContainer -> {
          for (deklination in knoten.deklinationen) {
            when (deklination) {
              is AST.Definition.DeklinationsDefinition.Definition ->
              {
                try {
                  knoten.wörterbuch.fügeDeklinationHinzu(deklination.deklination)
                }
                catch (fehler: Wörterbuch.DoppelteDeklinationFehler) {
                  // Doppelte Deklinationen werden einfach ingoriert
                  // TODO: Vielleicht sollte eine Warnung ausgegeben werden
                }
              }
              is AST.Definition.DeklinationsDefinition.Duden -> {
                val anfrage = async(Dispatchers.IO) {
                  val wort = deklination.wort
                  try {
                    return@async Duden.dudenGrammatikAnfrage(wort.wert)
                  }
                  catch (fehler: Duden.DudenFehler) {
                    when (fehler) {
                      is Duden.DudenFehler.NotFoundFehler, is Duden.DudenFehler.ParseFehler ->
                        throw GermanSkriptFehler.DudenFehler.WortNichtGefundenFehler(wort.toUntyped(), wort.wert)
                      is Duden.DudenFehler.KeinInternetFehler, is Duden.DudenFehler.TimeoutFehler, is Duden.DudenFehler.DudenServerFehler ->
                        throw GermanSkriptFehler.DudenFehler.Verbindungsfehler(wort.toUntyped(), wort.wert)
                    }
                  }
                }
                dudenAnfragen.computeIfAbsent(deklination.wort.dateiPfad) { mutableListOf()} += DudenAnfrage(deklination, anfrage)
              }
            }
          }
          for (typDefinition in knoten.definierteTypen.values) {
            if (typDefinition is AST.Definition.Typdefinition.Schnittstelle) {
              knoten.wörterbuch.fügeDeklinationHinzu(deklaniereAdjektiv(typDefinition))
            }
          }
          return@visit true
        }
        else -> return@visit false
      }
    }
    löseDudenAnfragenAuf(dudenAnfragen)
  }

  /**
   * Das Adjektiv wird einfach groß als Nomen für die Deklination gespeichert. Auf die richtigen Endungen
   * wird erst bei 'holeDeklination' geachtet.
   */
  private fun deklaniereAdjektiv(schnittstelle: AST.Definition.Typdefinition.Schnittstelle): Deklination {
    val adjektiv = schnittstelle.namensToken.wert.capitalize()

    // Placeholder Formen
    val formen = arrayOf(adjektiv, adjektiv, adjektiv, adjektiv)
    return Deklination(Genus.NEUTRUM, formen, formen, true)
  }

  private suspend fun löseDudenAnfragenAuf(anfragenNachDatei: Map<String, List<DudenAnfrage>>) {
    // löse Duden-Anfragen auf und überschreibe Duden-Deklination mit manueller Deklination im Source-Code
    for ((datei, dudenAnfragen) in anfragenNachDatei.entries) {
      val quellCodeDatei = File(datei)
      val quellCodeZeilen = quellCodeDatei.readLines().toMutableList()
      for ((dudenDekl, anfrage) in dudenAnfragen) {
        try {
          val deklination = anfrage.await()
          (dudenDekl.parent as AST.DefinitionsContainer).wörterbuch.fügeDeklinationHinzu(deklination)
          // minus 1, da die Zeilen bei 1 anfangen zu zählen
          // füge bei den Dudendeklinationen Tabs hinzu
          quellCodeZeilen[dudenDekl.wort.anfang.zeile-1] = "\t".repeat(dudenDekl.tiefe) + deklination.toString()
        }
        catch (fehler: Wörterbuch.DoppelteDeklinationFehler) {
          // TODO: Bei doppelten Deklinationen sollte später vielleicht eine Warnung ausgegeben werden
        }
      }
      quellCodeDatei.writeText(quellCodeZeilen.joinToString("\n"))
    }
  }

  fun holeDeklination(wort: AST.WortArt): Deklination {
    val container: AST.DefinitionsContainer? = wort.findNodeInParents() ?:
    wort.findNodeInParents<AST.Programm>()!!.definitionen

    val modulPfad = wort.findNodeInParents<AST.Satz.Ausdruck.FunktionsAufruf>()?.modulPfad ?:
      wort.findNodeInParents<AST.Satz.Ausdruck.ObjektInstanziierung>()?.klasse?.modulPfad
    if (modulPfad != null && modulPfad.isNotEmpty()) {
      val modul = modulAuflöser.findeModul(container!!, modulPfad)
      holeDeklination(wort, modul.definitionen)?.let { return it }
    }
    holeDeklination(wort, container!!)?.let { return it }

    for (verwendetesModul in container.verwendeteModule) {
      holeDeklination(wort, verwendetesModul)?.let { return it }
    }
    throw GermanSkriptFehler.UnbekanntesWort(wort.bezeichnerToken, wort.hauptWort)
  }

  private fun holeDeklination(wort: AST.WortArt, container: AST.DefinitionsContainer): Deklination? {
    var container: AST.DefinitionsContainer? = container
    while (true) {
      try {
        return container!!.wörterbuch.holeDeklination(wort.hauptWort, wort.vornomen?.typ)
      } catch (fehler: Wörterbuch.WortNichtGefunden) {
        container = container!!.findNodeInParents()
        if (container == null) {
          return null
        }
      }
    }
  }
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

  fun holeDeklination(wort: String, vornomen: TokenTyp.VORNOMEN?): Deklination {
    var lo = 0
    var hi = tabelle.size - 1
    while (lo <= hi) {
      val mid = ((lo.toDouble() + hi) / 2).toInt()
      val deklination = tabelle[mid]
      val nominativSingular = deklination.nominativSingular
      // die minus 1 ist da weil die anderen Formen eines Worts nicht umbedingt immer mit dem
      // Nominativ anfangen müssen
      val maxLength = min(wort.length, nominativSingular.length) - 1
      if (wortVergleichBerücksichtigeUmlaute(wort.substring(0, maxLength),
              deklination.nominativSingular.substring(0, maxLength)) == 0) {
        if (deklination.fallSequenz.contains(wort)) {
          return deklination
        } else {
          if (deklination.istNominalisiertesAdjektiv) {
            val adjektivDeklination = dekliniereAdjektiv(deklination.nominativSingular, vornomen)
            if (adjektivDeklination.fallSequenz.contains(wort)) {
              return adjektivDeklination
            }
          }
          if (nominativSingular < wort) {
            lo = mid + 1
          } else {
            hi = mid - 1
          }
        }
      }
      else if (wortVergleichBerücksichtigeUmlaute(deklination.nominativSingular, wort) == -1) {
        lo = mid + 1
      } else {
        hi = mid - 1
      }
    }

    throw WortNichtGefunden(wort)
  }

  private val bestimmterArtikelAdjektivEndungen = Pair(arrayOf("e", "en", "en", "e"), arrayOf("en", "en", "en", "en"))
  private val unbestimmterArtikelAdjektivEndungen = Pair(arrayOf("es", "en", "en", "es"), arrayOf("en", "en", "en", "en"))
  private val ohneArtikelAdjektivEndungen = Pair(arrayOf("es", "en", "em", "es"), arrayOf("e", "er", "en", "e"))

  private fun holeEndungen(vornomen: TokenTyp.VORNOMEN?) = when (vornomen) {
    null, TokenTyp.VORNOMEN.ETWAS -> ohneArtikelAdjektivEndungen
    TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT,
    is TokenTyp.VORNOMEN.DEMONSTRATIV_PRONOMEN,
    TokenTyp.VORNOMEN.JEDE -> bestimmterArtikelAdjektivEndungen
    // 'einige' ist ein spezieller Fall, wo dann die unbestimmten Adjektivendungen verwendet werden
    TokenTyp.VORNOMEN.ARTIKEL.UNBESTIMMT -> Pair(unbestimmterArtikelAdjektivEndungen.first, ohneArtikelAdjektivEndungen.second)
    TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN,
    TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.DEIN -> unbestimmterArtikelAdjektivEndungen
  }

  fun dekliniereAdjektiv(adjektiv: String, vornomen: TokenTyp.VORNOMEN?): Deklination {
    val (singularEndungen, pluralEndungen) = holeEndungen(vornomen)
    val singular = adjektivMitEndungen(adjektiv, singularEndungen)
    val plural = adjektivMitEndungen(adjektiv, pluralEndungen)
    return Deklination(Genus.NEUTRUM, singular, plural, true)
  }

  private fun adjektivMitEndungen(adjektiv: String, endungen: Array<String>): Array<String> {
    // für Regeln siehe https://deutsch.lingolia.com/de/grammatik/adjektive/deklination
    var adjektiv = adjektiv
    val endetAufE = adjektiv.last() == 'e'
    if (adjektiv.dropLast(2) == "el") {
      adjektiv = adjektiv.removeRange(adjektiv.length-2, adjektiv.length-1)
    }
    else if (adjektiv.dropLast(2) == "er" && adjektiv.getOrNull(adjektiv.length-3)?.istVokal() == true) {
      adjektiv = adjektiv.removeRange(adjektiv.length-2, adjektiv.length-1)
    }
    else if (adjektiv == "hoch") {
      adjektiv = "hoh"
    }
    return endungen.map { endung ->
      adjektiv + (if (endetAufE && endung.getOrNull(0) == 'e') {
        endung.drop(1)
      } else {
        endung
      })
    }.toTypedArray()
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
  wörterbuch.fügeDeklinationHinzu(Deklination(Genus.MASKULINUM, arrayOf("Dose", "Dose", "Dose", "Dose"), arrayOf("Dosen", "Dosen", "Dosen", "Dosen")))
  wörterbuch.fügeDeklinationHinzu(Deklination(Genus.MASKULINUM, arrayOf("Baum", "Baums", "Baum", "Baum"), arrayOf("Bäume", "Bäume", "Bäumen", "Bäumen")))
  wörterbuch.fügeDeklinationHinzu(Deklination(Genus.MASKULINUM, arrayOf("Uhr", "Uhr", "Uhr", "Uhr"), arrayOf("Uhren", "Uhren", "Uhren", "Uhren")))
  wörterbuch.fügeDeklinationHinzu(Deklination(Genus.MASKULINUM, arrayOf("Baumhaus", "Baumhauses", "Baumhaus", "Baumhaus"), arrayOf("Baumhäuser", "Baumhäuser", "Baumhäusern", "Baumhäuser")))

  wörterbuch.print()


  println(wörterbuch.holeDeklination("Bäume", null))
  println(wörterbuch.holeDeklination("Baumhaus", null))
  println(wörterbuch.holeDeklination("Dose", null))
  println(wörterbuch.holeDeklination("Uhr", null))
}

fun main() {
  // germanskript.wörterBuchTest()

  val deklanierer = Deklinierer(File("./iterationen/iter_2/code.gm"))
  deklanierer.deklaniere()
  deklanierer.ast.definitionen.wörterbuch.print()
}