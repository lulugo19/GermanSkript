package germanskript.interpreter

class Zeichenfolge(val zeichenfolge: String): Objekt() {

  override fun toString(): String = zeichenfolge

  override fun hashCode(): Int = zeichenfolge.hashCode()

  override fun internerAufruf(name: String): Objekt {
    return when (name) {
      "als Zahl" -> TODO()
      "vergleiche mich mit dem Typ" -> TODO()
      "hole das Zeichen an dem Index" -> TODO()
      "code an dem Index" -> TODO()
      "buchstabiere mich groß" -> TODO()
      "buchstabiere mich klein" -> TODO()
      "trenne mich zwischen dem Separator" -> TODO()
      else -> return super.internerAufruf(name)
    }
  }
}