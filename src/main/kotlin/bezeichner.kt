class Bezeichner(quellCode: String) {
  val deklanierer = Deklanierer(quellCode)
  val ast = deklanierer.ast

  fun bezeichne() {
    TODO("Hole das Wörterbuch vom Deklanierer und bezeichne")
    ast.visit { knoten ->
      if (knoten is AST.Nomen) {
        knoten.form = TODO()
        knoten.nominativ = TODO()
        knoten.numerus = TODO()
      }
    }
  }
}