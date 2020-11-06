package germanskript.interpreter

open class Objekt {
  open lateinit var klasse: germanskript.imm.IMM_AST.Definition.Klasse
  val eigenschaften = mutableMapOf<String, Objekt>()

  override fun toString(): String {
    return "${klasse.name}@${hashCode()}"
  }

  open fun internerAufruf(name: String): Objekt {
    return when (name) {
      "gleicht dem Objekt" -> TODO()
      "als Zeichenfolge" -> Zeichenfolge(toString())
      "hashe mich" -> Zahl(hashCode().toDouble())
      else -> throw Exception("Dieser Fall sollte nie auftreten!")
    }
  }
}