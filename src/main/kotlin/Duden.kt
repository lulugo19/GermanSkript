import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import kotlinx.coroutines.*
import util.SimpleLogger
import java.lang.Exception
import kotlin.math.absoluteValue
import kotlin.system.measureTimeMillis

object Duden {
  private const val DUDEN_TIMEOUT = 3000
  private const val USER_AGENT = "GermanSkript/1.0 (+https://github.com/lulugo19/GermanSkript)"
  private const val DUDEN_RECHTSCHREIBUNGS_URL = "https://www.duden.de/rechtschreibung"
  private const val DUDEN_SUCH_URL = "https://www.duden.de/suchen/dudenonline"

  val logger = SimpleLogger()
  sealed class DudenFehler(message: String): Exception(message) {
     class NotFoundFehler(url: String): DudenFehler("Die Seite '$url' wurde nicht gefunden.")
     class ParseFehler(): DudenFehler("Die Grammatik konnte nicht geparst werden.")
     class KeinInternetFehler(): DudenFehler("Es besteht keine Internet-Verbindung.")
     class TimeoutFehler(): DudenFehler("Der Online-Duden ist momentan unerreichbar.")
     class DudenServerFehler(): DudenFehler("Der Duden-Server hat Probleme.")
  }

  private val regexTableForm = Regex("""<tbody class="wrap-table__flexion"><tr><th class="wrap-table__flexion-head">Nominativ</th>
                  <td>([a-z]{3}).*?([A-ZÖÄÜ][a-zöäüß]*).*?</td>
                  <td>.*?([A-ZÖÄÜ][a-zöäüß]*).*?</td>
                </tr><tr><th class="wrap-table__flexion-head">Genitiv</th>
                  <td>.*?([A-ZÖÄÜ][a-zöäüß]*).*?</td>
                  <td>.*?([A-ZÖÄÜ][a-zöäüß]*).*?</td>
                </tr><tr><th class="wrap-table__flexion-head">Dativ</th>
                  <td>.*?([A-ZÖÄÜ][a-zöäüß]*).*?</td>
                  <td>.*?([A-ZÖÄÜ][a-zöäüß]*).*?</td>
                </tr><tr><th class="wrap-table__flexion-head">Akkusativ</th>
                  <td>.*?([A-ZÖÄÜ][a-zöäüß]*).*?</td>
                  <td>.*?([A-ZÖÄÜ][a-zöäüß]*).*?</td>
                </tr></tbody>""", EnumSet.of(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL))

  private val regexShortForm = Regex(
      """<div class="division "  id="grammatik">.*?<p>([a-z]{3}).*?([A-ZÖÄÜ][a-zöäüß]*); Genitiv:.*?([A-ZÖÄÜ][a-zöäüß\[\]]*), (Plural: )?.*?([A-ZÖÄÜ][a-zöäüß]*)</p>.*?</div>""",
      EnumSet.of(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL))

  private fun frageAn(url: String): HttpURLConnection {
    try {
      val url = URL(url)
      val httpCon = url.openConnection() as HttpURLConnection
      httpCon.requestMethod = "GET"
      httpCon.connectTimeout = DUDEN_TIMEOUT
      httpCon.setRequestProperty("User-Agent", USER_AGENT)
      logger.addLine("\nSent 'GET' request to URL : $url; Response Code : ${httpCon.responseCode}")

      // ein 500er Antwort Code signalisiert irgendeinen Fehler auf der Seite des Servers
      if (httpCon.responseCode.absoluteValue % 100 == 5) {
        throw DudenFehler.DudenServerFehler()
      }
      return httpCon
    } catch(error: java.net.UnknownHostException) {
      throw DudenFehler.KeinInternetFehler()
    } catch (error: java.net.SocketTimeoutException) {
      throw DudenFehler.TimeoutFehler()
    }
  }

  fun dudenGrammatikAnfrage(wort: String): Deklination {
      logger.addLine("Grammatikanfrage für: $wort")

      val anfrage = frageAn("$DUDEN_RECHTSCHREIBUNGS_URL/$wort")
      if (anfrage.responseCode.absoluteValue == 404) {
        logger.addLine("Grammatikseite nicht gefunden!")
        return dudenSuchAnfrage(wort)
      }

      val htmlDocument = InputStreamReader(anfrage.inputStream).readText()

      var match = regexTableForm.find(htmlDocument)

      val deklination = if (match != null) {
        val values = match.groupValues.map(::normalisiere)
        val genus = when (values[1]) {
          "der" -> Genus.MASKULINUM
          "die" -> Genus.FEMININUM
          else -> Genus.NEUTRUM
        }
        Deklination(genus, arrayOf(values[2], values[4], values[6], values[8]), arrayOf(values[3], values[5], values[7], values[9]))
      } else {
        match = regexShortForm.find(htmlDocument)
        if (match != null) {
          val values = match.groupValues.map(::normalisiere)
          val genus = when (values[1]) {
            "der" -> Genus.MASKULINUM
            "die" -> Genus.FEMININUM
            else -> Genus.NEUTRUM
          }
          val singular = values[2]
          val genitivSingular = values[3]
          val plural = values[5]
          Deklination(genus, arrayOf(singular, genitivSingular, singular, singular), arrayOf(plural, plural, plural, plural))
        } else {
          throw DudenFehler.ParseFehler()
        }
      }
      logger.addLine("Anfrage für '$wort' erfolgreich: " + deklination.toString())
      return deklination
  }

  private val optionalesZeichenRegex = Regex("""\[.?\]""")

  private fun normalisiere(wort: String): String {
    return wort.replace(optionalesZeichenRegex, "")
  }

  private val linkRegex = Regex("""<a class="vignette__link" href="/rechtschreibung/(.*?)">""",
      EnumSet.of(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE))

  private fun dudenSuchAnfrage(wort: String): Deklination {
    logger.addLine("Suchanfrage für: $wort")
    val url = "$DUDEN_SUCH_URL/$wort"
    val anfrage = frageAn(url)


    if (anfrage.responseCode.absoluteValue == 404) {
      logger.addLine("Suchseite nicht gefunden.")
      throw DudenFehler.NotFoundFehler(url)
    }

    val htmlDocument = InputStreamReader(anfrage.inputStream).readText()

    val matches = linkRegex.findAll(htmlDocument)
    logger.addLine("Gehe Suchlinks durch...")
    for (match in matches) {
      try {
        return dudenGrammatikAnfrage(match.groupValues[1])
      }
      catch (error: DudenFehler) {
        logger.addLine("Linkaufruf für '${match.groupValues[1]}' fehlgeschlagen!")
      }
    }
    throw DudenFehler.NotFoundFehler(url)
  }
}

suspend fun asynchroneDudenAnfragen(worte: List<String>) = withContext(Dispatchers.IO) {
  val time = measureTimeMillis {
    val deferreds= worte.map { async { Duden.dudenGrammatikAnfrage(it) } }
    deferreds.map { it.await() }
  }
  Duden.logger.print()
  println("Asynchron completed in $time ms")
}

fun synchroneDudenAnfragen(worte: List<String>) {
  val time = measureTimeMillis {
    for (wort in worte) {
      Duden.dudenGrammatikAnfrage(wort)
    }
  }
  Duden.logger.print()
  println("synchrone Zeit: $time ms")
}


fun main() = runBlocking<Unit> {
  val worte = listOf("Jacke", "Bild", "Flasche", "Telefon", "Computer", "Kamera",
      "Wand", "Ereignis", "Kokosnuss", "Banane", "Apfel", "Erdbeere",
  "Tomate", "Katze", "Pferd", "Wal", "Muschel", "Signal", "Banane",
   "Tisch", "Haus", "Garten", "Steckdose", "Bein", "Arm", "Kopf", "Baum", "Lied",
  "Stuhl", "Mandarine", "Vase", "Gorilla", "Tastatur", "Tasse", "Teller",
  "Liste", "Zahl", "Zeichenfolge", "Zeile", "Zeiger", "Nuss",
  "Aubergine", "Kreis", "Strahl", "Quadrat", "Kandidat", "Eule",
  "Waschbecken", "Zaun", "Fenster", "Rahmen", "Schalter", "Kuchen", "Geschenk",
  "Kind", "Person", "Student", "Glocke", "Schwester", "Bruder", "Schwein", "Schaf", "Wacht", "Mann", "Rechteck")

  asynchroneDudenAnfragen(worte)
  //synchroneDudenAnfragen(worte)
}
