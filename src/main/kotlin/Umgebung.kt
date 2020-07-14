import java.util.*
import kotlin.collections.HashMap

data class Variable<T>(val name: AST.Nomen, val wert: T)

class Bereich<T>(val methodenBlockObjekt: T?) {
  val variablen: HashMap<String, Variable<T>> = HashMap()
}

class Umgebung<T>() {
  private val bereiche = Stack<Bereich<T>>()

  val istLeer get() = bereiche.empty()

  fun leseVariable(varName: AST.Nomen): Variable<T> {
    return  leseVariable(varName.nominativ)?: throw GermanScriptFehler.Undefiniert.Variable(varName.bezeichner.toUntyped())
  }

  fun leseVariable(varName: String): Variable<T>? {
      return bereiche.findLast { bereich -> bereich.variablen.containsKey(varName) }?.variablen?.get(varName)
  }

  fun schreibeVariable(varName: AST.Nomen, wert: T, überschreibe: Boolean) {
    val variablen = bereiche.peek()!!.variablen
    if (!überschreibe && variablen.containsKey(varName.nominativ)) {
      throw GermanScriptFehler.Variablenfehler(varName.bezeichner.toUntyped(), variablen.getValue(varName.nominativ).name)
    }
    variablen[varName.nominativ] = Variable(varName, wert)
  }

  fun überschreibeVariable(varName: AST.Nomen, wert: T) {
    val bereich = bereiche.findLast {it.variablen.containsKey(varName.nominativ) }
    if (bereich != null) {
      bereich.variablen[varName.nominativ] = Variable(varName, wert)
    } else {
      // Fallback
      schreibeVariable(varName, wert, true)
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