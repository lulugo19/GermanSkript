class Definierer(dateiPfad: String): PipelineKomponente(dateiPfad) {
  val grammatikPrüfer = GrammatikPrüfer(dateiPfad)
  val ast = grammatikPrüfer.ast
  private val funktionsDefinitionsMapping = hashMapOf<String, AST.Definition.FunktionOderMethode.Funktion>()
  private val klassenDefinitionsMapping = hashMapOf<String, AST.Definition.Klasse>()

  fun definiere() {
    grammatikPrüfer.prüfe()
    funktionsDefinitionsMapping.clear()
    // definiere Funktionen und Klassen
    ast.definitionen.visit { knoten ->
      when (knoten) {
        is AST.Definition.FunktionOderMethode.Funktion -> definiereFunktion(knoten)
        is AST.Definition.Klasse -> definiereKlasse(knoten)
      }
      return@visit false
    }
    ast.definitionen.visit { knoten ->
      when (knoten) {
        is AST.Definition.FunktionOderMethode.Methode -> definiereMethode(knoten)
        is AST.Definition.Konvertierung -> definiereKonvertierung(knoten)
      }

      return@visit false
    }
  }

  fun holeFunktionsDefinition(funktionsAufruf: AST.Funktion): AST.Definition.FunktionOderMethode.Funktion{
    if (funktionsAufruf.vollerName == null) {
      funktionsAufruf.vollerName = holeVollenNamenVonFunktionsAufruf(funktionsAufruf, false)
    }
    return funktionsDefinitionsMapping.getOrElse(funktionsAufruf.vollerName!!) {
      throw GermanScriptFehler.Undefiniert.Funktion(funktionsAufruf.verb.toUntyped(), funktionsAufruf)
    }
  }

  fun holeKlassenDefinition(nomen: AST.Nomen): AST.Definition.Klasse {
    val teilWörter = nomen.bezeichner.typ.teilWörter
    for (i in teilWörter.indices) {
      val klassenName = teilWörter.drop(teilWörter.size - 1 - i).joinToString("")
      if (klassenDefinitionsMapping.containsKey(klassenName)) {
        return klassenDefinitionsMapping.getValue(klassenName)
      }
    }
    throw GermanScriptFehler.Undefiniert.Typ(nomen.bezeichner.toUntyped())
  }

  val funktionsDefinitionen get(): Sequence<AST.Definition.FunktionOderMethode.Funktion> = funktionsDefinitionsMapping.values.asSequence()

  fun gebeFunktionsDefinitionenAus() {
    funktionsDefinitionsMapping.forEach { (vollerName, definition) ->
      println("$vollerName: $definition")
    }
  }

  val klassenDefinitionen get(): Sequence<AST.Definition.Klasse> = klassenDefinitionsMapping.values.asSequence()

  fun gebeKlassenDefinitionenAus() {
    klassenDefinitionsMapping.forEach {(name, definition) ->
      println("$name: $definition")
    }
  }

  private fun definiereFunktion(funktionsDefinition: AST.Definition.FunktionOderMethode.Funktion) {
    val vollerName = holeVollenNameVonFunktionsDefinition(funktionsDefinition, null)
    if (funktionsDefinitionsMapping.containsKey(vollerName)) {
      throw GermanScriptFehler.DoppelteDefinition.Funktion(
          funktionsDefinition.name.toUntyped(),
          funktionsDefinitionsMapping.getValue(vollerName)
      )
    }
    funktionsDefinition.vollerName = vollerName
    funktionsDefinitionsMapping[vollerName] = funktionsDefinition
  }

  private fun definiereMethode(methodenDefinition: AST.Definition.FunktionOderMethode.Methode) {
    val vollerName = holeVollenNameVonFunktionsDefinition(methodenDefinition.funktion, methodenDefinition.reflexivPronomen)
    val klasse = holeKlassenDefinition(methodenDefinition.klasse.name)
    if (klasse.methoden.containsKey(vollerName)) {
      throw GermanScriptFehler.DoppelteDefinition.Methode(
          methodenDefinition.funktion.name.toUntyped(),
          klasse.methoden.getValue(vollerName)
      )
    }
    methodenDefinition.funktion.vollerName = vollerName
    klasse.methoden[vollerName] = methodenDefinition
  }

  private fun definiereKonvertierung(konvertierung: AST.Definition.Konvertierung) {
    val klasse = holeKlassenDefinition(konvertierung.klasse)
    val typName = konvertierung.typ.name.nominativ
    if (klasse.konvertierungen.containsKey(typName)) {
      throw GermanScriptFehler.DoppelteDefinition.Konvertierung(
          konvertierung.klasse.bezeichner.toUntyped(),
          klasse.konvertierungen.getValue(typName)
      )
    }
    klasse.konvertierungen[typName] = konvertierung
  }

  private fun holeVollenNameVonFunktionsDefinition(
      funktionsDefinition: AST.Definition.FunktionOderMethode.Funktion,
      reflexivPronomen: TypedToken<TokenTyp.REFLEXIV_PRONOMEN>?): String {
    var vollerName = funktionsDefinition.name.wert
    if (funktionsDefinition.objekt != null) {
      val objekt = funktionsDefinition.objekt
      val typ = objekt.typKnoten.name
      vollerName += " " + objekt.name.vornomenString!! + " " + objekt.name.hauptWort(typ.fälle.first(), typ.numerus!!)
    }
    else if (reflexivPronomen != null) {
      vollerName += " ${reflexivPronomen.wert}"
    }
    for (präposition in funktionsDefinition.präpositionsParameter) {
      vollerName += " " + präposition.präposition.präposition.wert
      for (parameterIndex in präposition.parameter.indices) {
        val parameter = präposition.parameter[parameterIndex]
        val typ = parameter.typKnoten.name
        vollerName += " " + parameter.name.vornomenString!! + " " + parameter.name.hauptWort(typ.fälle.first(), typ.numerus!!)
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

  fun holeVollenNamenVonFunktionsAufruf(funktionsAufruf: AST.Funktion, ersetzeObjektMitReflexivPronomen: Boolean): String {
    // erkläre die Zeichenfolge mit der Zahl über die Zeile der Mond nach die Welt
    var vollerName = funktionsAufruf.verb.wert
    if (funktionsAufruf.objekt != null) {
      val objekt = funktionsAufruf.objekt
      if (ersetzeObjektMitReflexivPronomen) {
        val reflexivPronomen = when (objekt.name.fälle.first()) {
          Kasus.AKKUSATIV -> "mich"
          Kasus.DATIV -> "mir"
          else -> throw Exception("Dieser Fall sollte nie eintreten, da der Grammatikprüfer dies überprüfen sollte. ${objekt.name.bezeichner}")
        }
        vollerName += " $reflexivPronomen"
      } else {
        vollerName += " " + objekt.name.vornomenString!! + " " + objekt.name.hauptWort
      }
    } else if (funktionsAufruf.reflexivPronomen != null) {
      val reflexivPronomen = funktionsAufruf.reflexivPronomen
      val pronomen = if(reflexivPronomen.typ == TokenTyp.REFLEXIV_PRONOMEN.MICH) {
        reflexivPronomen.wert
      } else {
        when (reflexivPronomen.wert) {
          "dich" -> "mich"
          "dir" -> "mir"
          else -> throw Exception("Dieser Fall sollte nie auftreten.")
        }
      }
      vollerName += " $pronomen"
    }
    for (präposition in funktionsAufruf.präpositionsArgumente) {
      vollerName += " " + präposition.präposition.präposition.wert
      for (argumentIndex in präposition.argumente.indices) {
        val argument = präposition.argumente[argumentIndex]
        vollerName += " " + argument.name.vornomenString!! + " " + argument.name.hauptWort
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

  private fun definiereKlasse(klasse: AST.Definition.Klasse) {
    val klassenName = klasse.typ.name.hauptWort(Kasus.NOMINATIV, Numerus.SINGULAR)
    val reservierteNamen = arrayOf("Zahl", "Boolean", "Zeichenfolge")
    if (reservierteNamen.contains(klassenName)) {
      throw GermanScriptFehler.ReservierterTypName(klasse.typ.name.bezeichner.toUntyped())
    }
    if (klassenDefinitionsMapping.containsKey(klassenName)) {
      throw GermanScriptFehler.DoppelteDefinition.Klasse(klasse.typ.name.bezeichner.toUntyped(),
          klassenDefinitionsMapping.getValue(klassenName))
    }
    klassenDefinitionsMapping[klassenName] = klasse
  }

}

fun main() {
  val definierer = Definierer("./iterationen/iter_2/code.gms")
  definierer.definiere()
  definierer.gebeFunktionsDefinitionenAus()
  definierer.gebeKlassenDefinitionenAus()
}