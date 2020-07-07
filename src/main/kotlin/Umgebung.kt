import java.util.*
import kotlin.collections.HashMap

typealias Bereich<T> = HashMap<String, T>

class Umgebung<T>() {
  private val bereiche = Stack<Bereich<T>>()

  val istLeer get() = bereiche.empty()

  fun leseVariable(varName: AST.Nomen): T {
    return  leseVariable(varName.nominativ!!)?: throw GermanScriptFehler.Undefiniert.Variable(varName.bezeichner.toUntyped())
  }

  fun leseVariable(varName: String): T? {
    for (bereich in bereiche) {
      bereich[varName]?.also { return it }
    }
    return null
  }

  fun schreibeVariable(varName: AST.Nomen, wert: T) {
    bereiche.peek()!![varName.nominativ!!] = wert
  }

  fun überschreibeVariable(varName: AST.Nomen, wert: T) {
    val bereich = bereiche.findLast {b -> (b as HashMap<String, T>).containsKey(varName.nominativ!!) }
    if (bereich != null) {
      bereich[varName.nominativ!!] = wert
    } else {
      // Fallback
      schreibeVariable(varName, wert)
    }
  }

  fun pushBereich() {
    bereiche.push(Bereich())
  }

  fun popBereich() {
    bereiche.pop()
  }
}