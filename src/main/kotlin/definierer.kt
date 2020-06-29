data class FunktionsDefinition(val verbToken: TypedToken<TokenTyp.BEZEICHNER_KLEIN>,val vollerName: String, val parameterTypen: List<String>, val rückgabe: String? = null, val sätze: List<AST.Satz>)

class Definierer(quellCode: String) {
  val grammatikPrüfer = GrammatikPrüfer(quellCode)
  val ast = grammatikPrüfer.ast
  private val funktionsDefinitionen = hashMapOf<String, FunktionsDefinition>()

  fun definiere() {
    grammatikPrüfer.prüfe()
    funktionsDefinitionen.clear()
    for (definition in ast.definitionen) {
      definition.visit { knoten ->
        when (knoten) {
          is AST.Definition.Funktion -> definiereFunktion(knoten)
        }
      }
    }
  }

  fun holeFunktionsDefinition(): AST.Definition.Funktion {
    TODO()
  }

  private fun definiereFunktion(funktionsDefinition: AST.Definition.Funktion) {
    val  token = funktionsDefinition.name
    val vollerName = getVollerName(funktionsDefinition)
    if (funktionsDefinitionen.containsKey(vollerName)) {
      throw GermanScriptFehler.DoppelteDefinition.Funktion(
          funktionsDefinition.name.toUntyped(),
          funktionsDefinitionen.getValue(vollerName)
      )
    }
    val parameterTypen = getParameterTypen(funktionsDefinition)
    val rückgabeTyp = funktionsDefinition.rückgabeTyp.nominativ!!

    val dieFunktionsDefinition = FunktionsDefinition(token, vollerName, parameterTypen, rückgabeTyp, funktionsDefinition.sätze)
    funktionsDefinitionen[vollerName] = dieFunktionsDefinition
  }

  fun getVollerName(funktionsDefinition: AST.Definition.Funktion): String {
    var vollerName = funktionsDefinition.name.wert
    if (funktionsDefinition.objekt != null) {
      val objekt = funktionsDefinition.objekt
      vollerName += " " + objekt.paramName.artikel!! + " " + objekt.paramName.bezeichner.wert
    }
    for (präposition in funktionsDefinition.präpositionen) {
      vollerName += " " + präposition.präposition.präposition.wert
      for (parameter in präposition.parameter) {
        vollerName += " " + parameter.paramName.artikel!! + " " + parameter.paramName.bezeichner.wert
      }
    }
    if (funktionsDefinition.suffix != null) {
      vollerName += " " + funktionsDefinition.suffix
    }
    return vollerName
  }

  fun getParameterTypen(funktionsDefinition: AST.Definition.Funktion): List<String> {
    val parameterTypen = mutableListOf<String>()
    if (funktionsDefinition.objekt != null) {
      val objekt = funktionsDefinition.objekt
      parameterTypen.add(objekt.typ.nominativ!!)
    }
    for (präposition in funktionsDefinition.präpositionen) {
      for (parameter in präposition.parameter) {
        parameterTypen.add(parameter.typ.nominativ!!)
      }
    }
    return parameterTypen
  }
}