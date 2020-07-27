import germanskript.Interpretierer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

class GermanSkriptTest {


  private fun testGermanSkriptSource(germanSkriptSource: String, expectedOutput: String) {
    // leite den Standardoutput in einen Byte-Array-Stream um
    val myOut = ByteArrayOutputStream()
    System.setOut(PrintStream(myOut))

    // erstelle temporäre Datei mit dem Source-Code
    val tempFile = createTempFile("germanskript_test_temp", "gm")
    tempFile.writeText(germanSkriptSource)

    val interpretierer = Interpretierer(tempFile)
    interpretierer.interpretiere()

    val actual = myOut.toString()
    assertThat(actual).isEqualToNormalizingNewlines(expectedOutput)

    // lösche temporäre Datei
    tempFile.delete()
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
}