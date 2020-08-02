package germanskript
import java.io.File

class Definierer(startDatei: File): PipelineKomponente(startDatei) {
  val grammatikPrüfer = GrammatikPrüfer(startDatei)
  val ast = grammatikPrüfer.ast

  fun definiere() {
    grammatikPrüfer.prüfe()
    // definiere Funktionen
    definiere(ast.definitionen)
  }

  private fun definiere(definitionen: AST.DefinitionsContainer) {
    definitionen.funktionenOderMethoden.forEach { knoten ->
      when (knoten) {
        is AST.Definition.FunktionOderMethode.Funktion -> definiereFunktion(knoten)
        is AST.Definition.FunktionOderMethode.Methode -> definiereMethode(knoten)
      }
    }
    definitionen.konvertierungen.forEach(::definiereKonvertierung)
    definitionen.module.values.forEach {modul -> definiere(modul.definitionen)}
  }

  private fun durchlaufeDefinitionsContainer(knoten: AST): Sequence<AST.DefinitionsContainer> = sequence {
    var node: AST.DefinitionsContainer? = knoten.findNodeInParents() ?:
      knoten.findNodeInParents<AST.Programm>()!!.definitionen
    while (node != null) {
      yield(node!!)
      node = node.findNodeInParents()
    }
  }

  fun holeFunktionsDefinition(funktionsAufruf: AST.Funktion): AST.Definition.FunktionOderMethode.Funktion {
    if (funktionsAufruf.vollerName == null) {
      funktionsAufruf.vollerName = holeVollenNamenVonFunktionsAufruf(funktionsAufruf, null)
    }
    if (funktionsAufruf.modulPfad.isEmpty()) {
      for (definitionen in durchlaufeDefinitionsContainer(funktionsAufruf)) {
        if (definitionen.funktionen.containsKey(funktionsAufruf.vollerName!!)) {
          return definitionen.funktionen.getValue(funktionsAufruf.vollerName!!)
        }
      }
      throw GermanSkriptFehler.Undefiniert.Funktion(funktionsAufruf.verb.toUntyped(), funktionsAufruf)
    } else {
      val modul = löseModulPfadAuf(funktionsAufruf.modulPfad)
      if (!modul.definitionen.funktionen.containsKey(funktionsAufruf.vollerName!!)) {
        throw GermanSkriptFehler.Undefiniert.Funktion(funktionsAufruf.verb.toUntyped(), funktionsAufruf)
      }
      return modul.definitionen.funktionen.getValue(funktionsAufruf.vollerName!!)
    }
  }

  fun holeKlassenDefinition(klasse: AST.TypKnoten): AST.Definition.Klasse {
    val teilWörter = klasse.name.bezeichner.typ.teilWörter
    if (klasse.modulPfad.isEmpty()) {
      for (definitionen in durchlaufeDefinitionsContainer(klasse)) {
        for (i in teilWörter.indices) {
          val klassenName = teilWörter.drop(teilWörter.size - 1 - i).joinToString("")
          if (definitionen.klassen.containsKey(klassenName)) {
            return definitionen.klassen.getValue(klassenName)
          }
        }
      }
      throw GermanSkriptFehler.Undefiniert.Typ(klasse.name.bezeichner.toUntyped(), klasse)
    } else {
      val modul = löseModulPfadAuf(klasse.modulPfad)
      for (i in teilWörter.indices) {
        val klassenName = teilWörter.drop(teilWörter.size - 1 - i).joinToString("")
        if (modul.definitionen.klassen.containsKey(klassenName)) {
          return modul.definitionen.klassen.getValue(klassenName)
        }
      }
      throw GermanSkriptFehler.Undefiniert.Typ(klasse.name.bezeichner.toUntyped(), klasse)
    }
  }

  private fun löseModulPfadAuf(modulPfad: List<TypedToken<TokenTyp.BEZEICHNER_GROSS>>): AST.Definition.Modul {
    var definitionen = ast.definitionen
    for (bezeichner in modulPfad) {
      if (!definitionen.module.containsKey(bezeichner.wert)) {
        throw GermanSkriptFehler.Undefiniert.Modul(bezeichner.toUntyped())
      }
      definitionen = definitionen.module.getValue(bezeichner.wert).definitionen
    }
    return definitionen.parent as AST.Definition.Modul
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

  fun holeVollenNamenVonFunktionsAufruf(funktionsAufruf: AST.Funktion, ersetzeObjekt: String?): String {
    // erkläre die Zeichenfolge mit der Zahl über die Zeile der Mond nach die Welt
    var vollerName = funktionsAufruf.verb.wert
    if (funktionsAufruf.objekt != null) {
      val objekt = funktionsAufruf.objekt
      if (ersetzeObjekt != null) {
        vollerName += " $ersetzeObjekt"
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

  /**
   * Gibt alle Funktionsdefinitionen zurück.
   */
  val funktionsDefinitionen get(): Sequence<AST.Definition.FunktionOderMethode.Funktion> = sequence {
    // Der Rückgabetyp der Funktionen muss explizit dranstehen. Ansonsten gibt es einen internen Fehler
    fun holeFunktionsDefinitionen(container: AST.DefinitionsContainer):
        Sequence<AST.Definition.FunktionOderMethode.Funktion> = sequence {
      for (funktion in container.funktionen.values) {
        yield(funktion)
      }
      for (modul in container.module.values) {
        yieldAll(holeFunktionsDefinitionen(modul.definitionen))
      }
    }

    yieldAll(holeFunktionsDefinitionen(ast.definitionen))
  }

  /**
   * Gibt alle Klassendefinitionen zurück.
   */
  val klassenDefinitionen get(): Sequence<AST.Definition.Klasse> = sequence {
    // Der Rückgabetyp der Funktionen muss explizit dranstehen. Ansonsten gibt es einen internen Fehler
    fun holeKlassenDefinitionen(container: AST.DefinitionsContainer):
        Sequence<AST.Definition.Klasse> = sequence {
      for (klasse in container.klassen.values) {
        yield(klasse)
      }
      for (modul in container.module.values) {
        yieldAll(holeKlassenDefinitionen(modul.definitionen))
      }
    }

    yieldAll(holeKlassenDefinitionen(ast.definitionen))
  }

  /** holt eine globale Klasse über den Namen der Klasse */
  fun holeKlassenDefinition(klassenName: String): AST.Definition.Klasse {
    return ast.definitionen.klassen.getValue(klassenName)
  }

  fun gebeKlassenDefinitionenAus() {
    for (klasse in klassenDefinitionen)  {
      println("${klasse.typ.name.bezeichner.wert}: $klasse")
    }
  }

  fun gebeFunktionsDefinitionenAus() {
    for (funktion in funktionsDefinitionen) {
      println("${funktion.vollerName!!}: $funktion")
    }
  }

  private fun definiereFunktion(funktionsDefinition: AST.Definition.FunktionOderMethode.Funktion) {
    val vollerName = holeVollenNameVonFunktionsDefinition(funktionsDefinition, null)
    val definitionsContainer = funktionsDefinition.findNodeInParents<AST.DefinitionsContainer>()!!
    if (definitionsContainer.funktionen.containsKey(vollerName)) {
      throw GermanSkriptFehler.DoppelteDefinition.Funktion(
          funktionsDefinition.name.toUntyped(),
          definitionsContainer.funktionen.getValue(vollerName)
      )
    }
    funktionsDefinition.vollerName = vollerName
    definitionsContainer.funktionen[vollerName] = funktionsDefinition
  }

  private fun definiereMethode(methodenDefinition: AST.Definition.FunktionOderMethode.Methode) {
    val vollerName = holeVollenNameVonFunktionsDefinition(methodenDefinition.funktion, methodenDefinition.reflexivPronomen)
    val klasse = holeKlassenDefinition(methodenDefinition.klasse)
    if (klasse.methoden.containsKey(vollerName)) {
      throw GermanSkriptFehler.DoppelteDefinition.Methode(
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
      throw GermanSkriptFehler.DoppelteDefinition.Konvertierung(
          konvertierung.klasse.name.bezeichner.toUntyped(),
          klasse.konvertierungen.getValue(typName)
      )
    }
    klasse.konvertierungen[typName] = konvertierung
  }
}

fun main() {
  val definierer = Definierer(File("./iterationen/iter_2/code.gms"))
  definierer.definiere()
  definierer.gebeFunktionsDefinitionenAus()
  definierer.gebeKlassenDefinitionenAus()
}