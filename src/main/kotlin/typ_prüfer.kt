class TypPrüfer(quellCode: String) {

  val grammatikPrüfer = GrammatikPrüfer(quellCode)
  val ast = grammatikPrüfer.ast

  fun prüfe() {
    ast.visit { knoten ->
      when(knoten) {
        is AST.Satz.Variablendeklaration -> TODO()
        else -> TODO()
      }
    }
  }
}