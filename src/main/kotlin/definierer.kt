data class FunktionsDefinition(val vollerName: String, val parameterTypen: List<String>, val rückgabe: String? = null, val sätze: List<AST.Satz>)

class Definierer(quellCode: String) {
  val grammatikPrüfer = GrammatikPrüfer(quellCode)
  val ast = grammatikPrüfer.ast
  private val funktionen = hashMapOf<String, FunktionsDefinition>()

  fun definiere() {
    grammatikPrüfer.prüfe()
    funktionen.clear()
    for (definition in ast.definitionen) {
      definition.visit { knoten ->
        when (knoten) {
          is AST.Definition.Funktion -> TODO()
        }
      }
    }
  }

  fun holeFunktionsDefinition(): AST.Definition.Funktion {
    TODO()
  }

  private fun definiereFunktion(funktionsDefinition: AST.Definition.Funktion) {
    val vollerName = getVollerName(funktionsDefinition)
  }

  fun getVollerName(funktionsDefinition: AST.Definition.Funktion): String {
    var vollerName = funktionsDefinition.name.wert
    if (funktionsDefinition.objekt != null) {
      val objekt = funktionsDefinition.objekt
      vollerName += " " + objekt.paramName.artikel!! + " " + objekt.paramName.bezeichner.wert
    }
    for (präposition in funktionsDefinition.präpositionen) {
      vollerName += " " + präposition.präposition.präposition.wert
      for (parameter in präposition.parameter) {
        vollerName += " " + parameter.paramName.artikel!! + " " + parameter.paramName.bezeichner.wert
      }
    }
    return vollerName
  }

}