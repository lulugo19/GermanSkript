class Interpreter(dateiPfad: String): PipelineComponent(dateiPfad) {
  val typPrüfer = TypPrüfer(dateiPfad)
  val ast = typPrüfer.ast

  fun interpretiere() {
    typPrüfer.prüfe()
    for (satz in ast.sätze) {
      TODO("interpretiere Satz")
    }
  }
}