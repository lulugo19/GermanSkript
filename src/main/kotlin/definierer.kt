class Definierer(quellCode: String) {
  val grammatikPrüfer = GrammatikPrüfer(quellCode)
  val ast = grammatikPrüfer.ast

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

}