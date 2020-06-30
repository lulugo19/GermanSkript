class TypPrüfer(dateiPfad: String): PipelineComponent(dateiPfad) {

  val grammatikPrüfer = GrammatikPrüfer(dateiPfad)
  val ast = grammatikPrüfer.ast

  fun prüfe() {
    ast.visit { knoten ->
      when(knoten) {
        is AST.Satz.VariablenDeklaration -> TODO()
        else -> TODO()
      }
      true // visit everything
    }
  }
}