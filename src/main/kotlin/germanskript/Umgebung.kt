package germanskript

import java.util.*
import kotlin.collections.HashMap

data class Variable<T>(val name: AST.WortArt.Nomen, val wert: T)

class Bereich<T>(val nachrichtenBereichObjekt: T?) {
  val variablen: HashMap<String, Variable<T>> = HashMap()

  override fun toString(): String {
    return variablen.entries.joinToString("\n", "[\n", "\n]") {"${it.key}: ${it.value}"}
  }
}

class Umgebung<T>() {
  private val bereiche = Stack<Bereich<T>>()

  val istLeer get() = bereiche.empty()

  fun top() = bereiche.last()

  fun leseVariable(varName: AST.WortArt.Nomen): Variable<T> {
    return  leseVariable(varName.nominativ)?: throw GermanSkriptFehler.Undefiniert.Variable(varName.bezeichner.toUntyped())
  }

  fun leseVariable(varName: String): Variable<T>? {
      return bereiche.findLast { bereich -> bereich.variablen.containsKey(varName) }?.variablen?.get(varName)
  }

  fun schreibeVariable(varName: AST.WortArt.Nomen, wert: T, überschreibe: Boolean) {
    val variablen = bereiche.peek()!!.variablen
    if (!überschreibe && variablen.containsKey(varName.nominativ)) {
      throw GermanSkriptFehler.Variablenfehler(varName.bezeichner.toUntyped(), variablen.getValue(varName.nominativ).name)
    }
    variablen[varName.nominativ] = Variable(varName, wert)
  }

  fun überschreibeVariable(varName: AST.WortArt.Nomen, wert: T) {
    val bereich = bereiche.findLast {it.variablen.containsKey(varName.nominativ) }
    if (bereich != null) {
      bereich.variablen[varName.nominativ] = Variable(varName, wert)
    } else {
      // Fallback
      schreibeVariable(varName, wert, true)
    }
  }

  fun schreibeVariable(varName: String, wert: T) {
    bereiche.peek()!!.variablen[varName] = Variable(
        AST.WortArt.Nomen(null, TypedToken.imaginäresToken(
            TokenTyp.BEZEICHNER_GROSS(arrayOf(varName), "", null), varName)),
        wert)
  }

  fun pushBereich(nachrichtenBereichObjekt: T? = null) {
    bereiche.push(Bereich(nachrichtenBereichObjekt))
  }

  fun popBereich() {
    bereiche.pop()
  }

  fun holeNachrichtenBereichObjekt(): T? = bereiche.findLast { it.nachrichtenBereichObjekt != null }?.nachrichtenBereichObjekt
}