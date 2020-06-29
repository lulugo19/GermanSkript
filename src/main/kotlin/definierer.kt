data class FunktionsDefinition(val vollerName: String, val parameterTypen: List<String>, val rückgabe: String? = null)

class Definierer(quellCode: String) {
  val grammatikPrüfer = GrammatikPrüfer(quellCode)
  val ast = grammatikPrüfer.ast
  val funktionen = hashMapOf<String, FunktionsDefinition>()

  fun definiere() {
    grammatikPrüfer.prüfe()

    for (definition in ast.definitionen) {
      definition.visit { knoten ->
        when (knoten) {
          is AST.Definition.Funktion -> TODO()
        }
      }
    }
  }

  fun definiereFunktion(funktionsDefinition: AST.Definition.Funktion) {

  }

}