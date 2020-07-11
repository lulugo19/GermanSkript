import java.util.*
import kotlin.collections.HashMap

class Bereich<T>(val methodenBlockObjekt: T?) {
  val variablen = HashMap<String, T>()
}

class Umgebung<T>() {
  private val bereiche = Stack<Bereich<T>>()

  val istLeer get() = bereiche.empty()

  fun leseVariable(varName: AST.Nomen): T {
    return  leseVariable(varName.nominativ!!)?: throw GermanScriptFehler.Undefiniert.Variable(varName.bezeichner.toUntyped())
  }

  fun leseVariable(varName: String): T? {
    for (bereich in bereiche) {
      bereich.variablen[varName]?.also { return it }
    }
    return null
  }

  fun schreibeVariable(varName: AST.Nomen, wert: T) {
    bereiche.peek()!!.variablen[varName.nominativ!!] = wert
  }

  fun überschreibeVariable(varName: AST.Nomen, wert: T) {
    val bereich = bereiche.findLast {it.variablen.containsKey(varName.nominativ!!) }
    if (bereich != null) {
      bereich.variablen[varName.nominativ!!] = wert
    } else {
      // Fallback
      schreibeVariable(varName, wert)
    }
  }

  fun pushBereich(methodenBlockObjekt: T? = null) {
    bereiche.push(Bereich(methodenBlockObjekt))
  }

  fun popBereich() {
    bereiche.pop()
  }

  fun holeMethodenBlockObjekt(): T? = bereiche.findLast { it.methodenBlockObjekt != null }?.methodenBlockObjekt
}