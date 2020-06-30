

data class FunktionsDefinition(
    val verbToken: TypedToken<TokenTyp.BEZEICHNER_KLEIN>,
    val vollerName: String,
    val parameterTypen: List<String>,
    val rückgabe: String? = null,
    val sätze: List<AST.Satz>
)

class Definierer(dateiPfad: String): PipelineComponent(dateiPfad) {
  val grammatikPrüfer = GrammatikPrüfer(dateiPfad)
  val ast = grammatikPrüfer.ast
  private val funktionsDefinitionen = hashMapOf<String, FunktionsDefinition>()

  fun definiere() {
    grammatikPrüfer.prüfe()
    funktionsDefinitionen.clear()
    ast.visit { knoten ->
      if (knoten is AST.Definition) {
        when (knoten) {
          is AST.Definition.Funktion -> definiereFunktion(knoten)
          else -> throw Exception("Unhandled Definition: $knoten")
        }
        true
      } else {
        false
      }
    }
  }

  fun holeFunktionsDefinition(funktionsAufruf: AST.FunktionsAufruf): FunktionsDefinition {
    if (funktionsAufruf.vollerName == null) {
      funktionsAufruf.vollerName = getVollerNameVonFunktionsAufruf(funktionsAufruf)
    }
    return funktionsDefinitionen.getOrElse(funktionsAufruf.vollerName!!) {
      throw GermanScriptFehler.Undefiniert.Funktion(funktionsAufruf.verb.toUntyped(), funktionsAufruf)
    }
  }

  private fun definiereFunktion(funktionsDefinition: AST.Definition.Funktion) {
    val  token = funktionsDefinition.name
    val vollerName = getVollerNameVonDefinition(funktionsDefinition)
    if (funktionsDefinitionen.containsKey(vollerName)) {
      throw GermanScriptFehler.DoppelteDefinition.Funktion(
          funktionsDefinition.name.toUntyped(),
          funktionsDefinitionen.getValue(vollerName)
      )
    }
    val parameterTypen = getParameterTypen(funktionsDefinition)
    val rückgabeTyp = funktionsDefinition.rückgabeTyp?.nominativ!!

    val dieFunktionsDefinition = FunktionsDefinition(token, vollerName, parameterTypen, rückgabeTyp, funktionsDefinition.sätze)
    funktionsDefinitionen[vollerName] = dieFunktionsDefinition
  }

  private fun getVollerNameVonDefinition(funktionsDefinition: AST.Definition.Funktion): String {
    var vollerName = funktionsDefinition.name.wert
    if (funktionsDefinition.objekt != null) {
      val objekt = funktionsDefinition.objekt
      vollerName += " " + objekt.paramName.artikel!! + " " + objekt.paramName.bezeichner.wert
    }
    for (präposition in funktionsDefinition.präpositionsParameter) {
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

  private fun getParameterTypen(funktionsDefinition: AST.Definition.Funktion): List<String> {
    val parameterTypen = mutableListOf<String>()
    if (funktionsDefinition.objekt != null) {
      val objekt = funktionsDefinition.objekt
      parameterTypen.add(objekt.typ.nominativ!!)
    }
    for (präposition in funktionsDefinition.präpositionsParameter) {
      for (parameter in präposition.parameter) {
        parameterTypen.add(parameter.typ.nominativ!!)
      }
    }
    return parameterTypen
  }

  private fun getVollerNameVonFunktionsAufruf(funktionsAufruf: AST.FunktionsAufruf): String {
    var vollerName = funktionsAufruf.verb.wert
    if (funktionsAufruf.objekt != null) {
      val objekt = funktionsAufruf.objekt
      vollerName += " " + objekt.name.artikel!! + " " + objekt.name.bezeichner.wert
    }
    for (präposition in funktionsAufruf.präpositionsArgumente) {
      vollerName += " " + präposition.präposition.präposition.wert
      for (argument in präposition.argumente) {
        vollerName += " " + argument.name.artikel!! + " " + argument.wert
      }
    }
    if (funktionsAufruf.suffix != null) {
      vollerName += " " + funktionsAufruf.suffix
    }
    return vollerName
  }
}