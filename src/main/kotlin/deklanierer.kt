class Deklanierer(quellCode: String) {
  val ast = Parser(quellCode).parse()

  // gibt Wörterbuch zurück
  fun deklaniere() {
    ast.visit {
      if (it is AST.Definition.Deklination) {
        TODO("deklaniere hier")
      }
    }
  }
}