import germanskript.GermanSkriptFehler
import germanskript.Interpretierer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.*

class GermanSkriptTest {

  private fun runGermanSkriptSource(germanSkriptSource: String) {
    // erstelle temporäre Datei mit dem Source-Code
    val tempFile = createTempFile("germanskript_test_temp", "gm")
    tempFile.writeText(germanSkriptSource)

    val interpretierer = Interpretierer(tempFile)
    interpretierer.interpretiere()


    // lösche temporäre Datei
    tempFile.delete()
  }

  private fun testGermanSkriptSource(germanSkriptSource: String, expectedOutput: String) {
    // leite den Standardoutput in einen Byte-Array-Stream um
    val myOut = ByteArrayOutputStream()
    System.setOut(PrintStream(myOut))

    runGermanSkriptSource(germanSkriptSource)

    val actual = myOut.toString()
    assertThat(actual).isEqualToNormalizingNewlines(expectedOutput)

    // set out back to stdout
    System.setOut(PrintStream(FileOutputStream(FileDescriptor.out)))
  }

  @Test
  @DisplayName("Hallo Welt")
  fun halloWelt() {
    val source = """
      schreibe die Zeile "Hallo Welt"
    """.trimIndent()
    val expectedOutput = "Hallo Welt\n"

    testGermanSkriptSource(source, expectedOutput)
  }

  @Test
  @DisplayName("Bedingungen")
  fun bedingungen() {
    val source = """
      Verb teste die Zahl:
          wenn die Zahl gleich 3 ist:
            schreibe die Zeile "Alle guten Dinge sind drei!".
          sonst wenn die Zahl gleich 42 ist:
            schreibe die Zeile "Die Antwort auf alles.".
          sonst: schreibe die Zahl.
      .
      teste die Zahl 11
      teste die Zahl 3
      teste die Zahl 42
      teste die Zahl 12
    """.trimIndent()

    val expectedOutput = """
      11
      Alle guten Dinge sind drei!
      Die Antwort auf alles.
      12
      
    """.trimIndent()

    testGermanSkriptSource(source, expectedOutput)
  }

  @Test
  @DisplayName("Fakultät")
  fun fakultät() {
    val source = """
      Verb(Zahl) fakultät von der Zahl:
        wenn die Zahl gleich 0 ist: gebe 1 zurück.
        sonst: gebe die Zahl * (fakultät von der Zahl - 1) zurück.
      .
      
      schreibe die Zahl (fakultät von der Zahl 3)
      schreibe die Zahl (fakultät von der Zahl 5)
      schreibe die Zahl (fakultät von der Zahl 6)
    """.trimIndent()

    val expectedOutput = """
      6
      120
      720
      
    """.trimIndent()

    testGermanSkriptSource(source, expectedOutput)
  }

  @Test
  @DisplayName("Solange-Schleife")
  fun solangeSchleife() {
    val source = """
      eine Zahl ist -1
      solange wahr:
        eine Zahl ist die Zahl plus 1
        wenn die Zahl > 10 ist: abbrechen.
        wenn die Zahl mod 2 gleich 1 ist: fortfahren.
        schreibe die Zahl
      .
    """.trimIndent()

    val expectedOutput = """
      0
      2
      4
      6
      8
      10
      
    """.trimIndent()

    testGermanSkriptSource(source, expectedOutput)
  }

  @Test
  @DisplayName("Listenindex")
  fun listen() {
    val source = """
      Deklination Maskulinum Singular(Index) Plural(Indizes)
      
      die Zahlen sind einige Zahlen [1, 2, 3]
      schreibe die Zahl[0]
      ein Index ist 1
      solange der Index kleiner als die AnZahl der Zahlen ist:
        schreibe die Zahl[Index]
        ein Index ist der Index plus 1
      .

    """.trimIndent()

    val expectedOutput = """
      1
      2
      3
      
    """.trimIndent()

    testGermanSkriptSource(source, expectedOutput)
  }

  @Test
  @DisplayName("Für-Jede-Schleifen")
  fun fürJedeSchleifen() {
    val source = """
      die Zahlen sind einige Zahlen [1, 2, 3]
      für jede Zahl:
        schreibe die Zahl
      .
      für jedes X in den Zahlen:
        schreibe die Zahl X
      .
      für jede Zahl in einigen Zahlen [11, 12, 13]:
        schreibe die Zahl
      .
    """.trimIndent()

    val expectedOutput = """
      1
      2
      3
      1
      2
      3
      11
      12
      13
      
    """.trimIndent()

    testGermanSkriptSource(source, expectedOutput)
  }

  @Test
  @DisplayName("Variablen-Überdeckung")
  fun variablenÜberdeckung() {
    val source = """
      eine Zeichenfolge ist "Erste Variable"
      :
        schreibe die Zeile Zeichenfolge
        eine Zeichenfolge ist "Erste veränderte Variable"
        schreibe die Zeile Zeichenfolge
        eine neue Zeichenfolge ist "Zweite Variable"
        schreibe die Zeile Zeichenfolge
      .
      schreibe die Zeile Zeichenfolge
    """.trimIndent()

    val expectedOutput = """
      Erste Variable
      Erste veränderte Variable
      Zweite Variable
      Erste veränderte Variable
      
    """.trimIndent()

    testGermanSkriptSource(source, expectedOutput)
  }

  @Test
  @DisplayName("Unveränderliche Variable können nicht neu zugewiesen werden")
  fun unverÄnderlicheVariablen() {
    val source = """
      die Zahl ist 6
      :
        die Zahl ist 5
      .
      die Zahl ist 10 // Fehler hier
    """.trimIndent()

    assertThatExceptionOfType(GermanSkriptFehler.Variablenfehler::class.java).isThrownBy {
      runGermanSkriptSource(source)
    }
  }

  @Test
  @DisplayName("Klassendefinition und Objektinstanziierung")
  fun klasseDefinitionUndObjekte() {
    val source = """
      Deklination Maskulinum Singular(Name, Namens, Namen, Namen) Plural(Namen)
      Deklination Neutrum Singular(Alter, Alters, Alter, Alter) Plural(Alter)
      Deklination Femininum Singular(Person) Plural(Personen)


      Nomen Person mit
          der Zeichenfolge VorName,
          der Zeichenfolge NachName,
          einer Zahl Alter:

          dieser Name ist "#{mein VorName} #{mein NachName}"
          schreibe die Zeile "#{mein Name} (#{mein Alter} Jahre alt) wurde erstellt!"
      .
      
      die Person ist eine Person mit dem VorNamen "Max", dem NachNamen "Mustermann", dem Alter 23
      die PersonJANE ist eine Person mit dem VorNamen "Jane", dem NachNamen "Doe", dem Alter 41
    """.trimIndent()

    val expectedOutput = """
      Max Mustermann (23 Jahre alt) wurde erstellt!
      Jane Doe (41 Jahre alt) wurde erstellt!
      
    """.trimIndent()

    testGermanSkriptSource(source, expectedOutput)
  }

  @Test
  @DisplayName("veränderliche Eigenschaften eines Objekt")
  fun veränderlicheEigenschaftenEinesObjekts() {
    val source = """
      Deklination Maskulinum Singular(Zähler, Zählers, Zähler, Zähler) Plural(Zähler)
      
      Nomen Zähler:
        jene Zahl ist 0
        schreibe die Zahl (meine Zahl)
      .
      
      Verb für Zähler erhöhe mich um die Zahl:
        meine Zahl ist meine Zahl + die Zahl
        schreibe die Zahl (meine Zahl)
      .
      
      Verb für Zähler setze mich zurück:
        meine Zahl ist 0
        schreibe die Zahl (meine Zahl)
      .
      
      der Zähler ist ein Zähler
      
      erhöhe den Zähler um die Zahl 5
      erhöhe den Zähler um die Zahl 3
      setze den Zähler zurück
      Zähler:
        erhöhe mich um die Zahl -5
        erhöhe mich um die Zahl 10
        setze mich zurück
      !
    """.trimIndent()

    val expectedOutput = """
      0
      5
      8
      0
      -5
      5
      0
      
    """.trimIndent()

    testGermanSkriptSource(source, expectedOutput)
  }

  @Test
  @DisplayName("Modul")
  fun module() {
    val source = """
      Verb hallo:
        schreibe die Zeile "Hallo Welt"
      .
      
      Modul Foo:
        Verb hallo:
          schreibe die Zeile "Hallo Foo"
        .
        
        Modul Bar:
          Verb hallo:
            schreibe die Zeile "Hallo Bar"
          .
        .
      .
      
      hallo
      Foo::hallo
      Foo::Bar::hallo
    """.trimIndent()

    val expectedOutput = """
      Hallo Welt
      Hallo Foo
      Hallo Bar
      
    """.trimIndent()

    testGermanSkriptSource(source, expectedOutput)
  }


}