import germanskript.AST
import germanskript.Parser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ParserTest {

  private fun parseGermanSkriptSource(germanSkriptSource: String): AST.Programm {
    // erstelle temporäre Datei mit dem Source-Code
    val tempFile = createTempFile("germanskript_test_temp", ".gm")
    tempFile.writeText(germanSkriptSource)

    val parser = Parser(tempFile)
    val ast = parser.parse()

    // lösche temporäre Datei
    tempFile.delete()
    return ast
  }

  @Test
  @DisplayName("Parse Schnittstelle")
  fun parseSchnittstelle() {
    val source = """
      Adjektiv zeichenbar:
        Verb zeichne mich mit der Zeichenfolge Farbe
        Verb skaliere mich mit der Zahl.
    """.trimIndent()

    val ast = parseGermanSkriptSource(source)
    assertThat(ast.definitionen.definierteTypen.containsKey("Zeichenbare"))
    assertThat(ast.definitionen.definierteTypen.getValue("Zeichenbare"))
        .isInstanceOf(AST.Definition.Typdefinition.Schnittstelle::class.java)
    val schnittstelle = ast.definitionen.definierteTypen.getValue("Zeichenbare") as AST.Definition.Typdefinition.Schnittstelle
    assertThat(schnittstelle.methodenSignaturen.size).isEqualTo(2)
    assertThat(schnittstelle.methodenSignaturen[0].name.wert).isEqualTo("zeichne")
    assertThat(schnittstelle.methodenSignaturen[1].name.wert).isEqualTo("skaliere")
  }
}
