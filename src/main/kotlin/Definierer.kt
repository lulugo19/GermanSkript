class Definierer(dateiPfad: String): PipelineComponent(dateiPfad) {
  val grammatikPrüfer = GrammatikPrüfer(dateiPfad)
  val ast = grammatikPrüfer.ast
  private val funktionsDefinitionen = hashMapOf<String, AST.Definition.Funktion>()

  fun definiere() {
    grammatikPrüfer.prüfe()
    funktionsDefinitionen.clear()
    ast.definitionen.visit { knoten ->
      when (knoten) {
        is AST.Definition.Funktion -> definiereFunktion(knoten)
      }
      return@visit false
    }
  }

  fun holeFunktionsDefinition(funktionsAufruf: AST.FunktionsAufruf): AST.Definition.Funktion {
    if (funktionsAufruf.vollerName == null) {
      funktionsAufruf.vollerName = getVollerNameVonFunktionsAufruf(funktionsAufruf)
    }
    return funktionsDefinitionen.getOrElse(funktionsAufruf.vollerName!!) {
      throw GermanScriptFehler.Undefiniert.Funktion(funktionsAufruf.verb.toUntyped(), funktionsAufruf)
    }
  }

  fun gebeFunktionsDefinitionenAus() {
    funktionsDefinitionen.forEach {
      vollerName, definition ->

      println("$vollerName: $definition")
    }

  }

  private fun definiereFunktion(funktionsDefinition: AST.Definition.Funktion) {
    val vollerName = getVollerNameVonDefinition(funktionsDefinition)
    if (funktionsDefinitionen.containsKey(vollerName)) {
      throw GermanScriptFehler.DoppelteDefinition.Funktion(
          funktionsDefinition.name.toUntyped(),
          funktionsDefinitionen.getValue(vollerName)
      )
    }
    if (funktionsDefinition.rückgabeTyp != null) {
      bestimmeTypKnotenTyp(funktionsDefinition.rückgabeTyp)
    }
    weiseFunktionsParameternTypenZu(funktionsDefinition)
    funktionsDefinition.vollerName = vollerName
    funktionsDefinitionen[vollerName] = funktionsDefinition
  }

  private fun getVollerNameVonDefinition(funktionsDefinition: AST.Definition.Funktion): String {
    var vollerName = funktionsDefinition.name.wert
    if (funktionsDefinition.objekt != null) {
      val objekt = funktionsDefinition.objekt
      vollerName += " " + objekt.paramName.artikel!! + " " + objekt.paramName.bezeichner.wert
    }
    for (präposition in funktionsDefinition.präpositionsParameter) {
      vollerName += " " + präposition.präposition.präposition.wert
      for (parameterIndex in präposition.parameter.indices) {
        val parameter = präposition.parameter[parameterIndex]
        vollerName += " " + parameter.paramName.artikel!! + " " + parameter.paramName.bezeichner.wert
        if (parameterIndex != präposition.parameter.size-1) {
          vollerName += ","
        }
      }
    }
    if (funktionsDefinition.suffix != null) {
      vollerName += " " + funktionsDefinition.suffix.wert
    }
    return vollerName
  }

  private fun weiseFunktionsParameternTypenZu(funktionsDefinition: AST.Definition.Funktion) {
    if (funktionsDefinition.objekt != null) {
      bestimmeTypKnotenTyp(funktionsDefinition.objekt.typKnoten)
    }
    for (präposition in funktionsDefinition.präpositionsParameter) {
      for (parameter in präposition.parameter) {
        bestimmeTypKnotenTyp(parameter.typKnoten)
      }
    }
  }

  private fun bestimmeTypKnotenTyp(typKnoten: AST.TypKnoten) {
    val typ = when(typKnoten.name.nominativ!!) {
      "Zahl" -> Typ.Zahl
      "Zeichenfolge"  -> Typ.Zeichenfolge
      "Boolean" -> Typ.Boolean
      else -> throw GermanScriptFehler.Undefiniert.Typ(typKnoten.name.bezeichner.toUntyped())
    }
    typKnoten.typ = typ
  }

  private fun getVollerNameVonFunktionsAufruf(funktionsAufruf: AST.FunktionsAufruf): String {
    // erkläre die Zeichenfolge mit der Zahl über die Zeile der Mond nach die Welt
    var vollerName = funktionsAufruf.verb.wert
    if (funktionsAufruf.objekt != null) {
      val objekt = funktionsAufruf.objekt
      vollerName += " " + objekt.name.artikel!! + " " + objekt.name.bezeichner.wert
    }
    for (präposition in funktionsAufruf.präpositionsArgumente) {
      vollerName += " " + präposition.präposition.präposition.wert
      for (argumentIndex in präposition.argumente.indices) {
        val argument = präposition.argumente[argumentIndex]
        vollerName += " " + argument.name.artikel!! + " " + argument.name.bezeichner.wert
        if (argumentIndex != präposition.argumente.size-1) {
          vollerName += ","
        }
      }
    }
    if (funktionsAufruf.suffix != null) {
      vollerName += " " + funktionsAufruf.suffix.wert
    }
    return vollerName
  }
}

fun main() {
  val definierer = Definierer("./iterationen/iter_1/code.gms")
  definierer.definiere()
  definierer.gebeFunktionsDefinitionenAus()
}