class Interpreter(quellCode: String) {
  val typPrüfer = TypPrüfer(quellCode)
  val ast = typPrüfer.ast

  fun interpretiere() {
    typPrüfer.prüfe()
    for (satz in ast.sätze) {
      TODO("interpretiere Satz")
    }
  }
}