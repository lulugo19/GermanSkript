class GrammatikPrüfer(quellCode: String) {
  val bezeichner = Bezeichner(quellCode)
  val ast = bezeichner.ast

  fun prüfe() {
    bezeichner.bezeichne()

    ast.visit { knoten ->
      when (knoten) {
        is AST.Definition.Funktion -> TODO()
        is AST.Satz.Variablendeklaration -> TODO()
        else -> TODO()
      }
    }
  }
}