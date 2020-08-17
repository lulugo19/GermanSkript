package germanskript
import java.io.File

class Definierer(startDatei: File): PipelineKomponente(startDatei) {
  val grammatikPrüfer = GrammatikPrüfer(startDatei)
  val ast = grammatikPrüfer.ast
  val modulAuflöser = grammatikPrüfer.deklinierer.modulAuflöser

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
    definitionen.definierteTypen.values.forEach { schnittstelle ->
      if (schnittstelle is AST.Definition.Typdefinition.Schnittstelle) {
        schnittstelle.methodenSignaturen.forEach { holeVollenNameVonFunktionsSignatur(it) }
      }
    }
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
      var funktionsDefinition: AST.Definition.FunktionOderMethode.Funktion? = null
      for (definitionen in durchlaufeDefinitionsContainer(funktionsAufruf)) {
        if (definitionen.funktionen.containsKey(funktionsAufruf.vollerName!!)) {
          funktionsDefinition = definitionen.funktionen.getValue(funktionsAufruf.vollerName!!)
        }
        for (verwendetesModul in definitionen.verwendeteModule) {
          if (verwendetesModul.funktionen.containsKey(funktionsAufruf.vollerName!!)) {
            val gefundeneFunktionsDefinition = verwendetesModul.funktionen.getValue(funktionsAufruf.vollerName!!)
            if (funktionsDefinition != null && funktionsDefinition != gefundeneFunktionsDefinition) {
              throw GermanSkriptFehler.Mehrdeutigkeit.Funktion(funktionsAufruf.token,
                funktionsDefinition, gefundeneFunktionsDefinition)
            }
            funktionsDefinition = gefundeneFunktionsDefinition
          }
        }
      }
      return funktionsDefinition ?: throw GermanSkriptFehler.Undefiniert.Funktion(funktionsAufruf.verb.toUntyped(), funktionsAufruf)
    } else {
      val modul = modulAuflöser.löseModulPfadAuf(funktionsAufruf, funktionsAufruf.modulPfad)
      if (!modul.definitionen.funktionen.containsKey(funktionsAufruf.vollerName!!)) {
        throw GermanSkriptFehler.Undefiniert.Funktion(funktionsAufruf.verb.toUntyped(), funktionsAufruf)
      }
      return modul.definitionen.funktionen.getValue(funktionsAufruf.vollerName!!)
    }
  }

  fun holeTypDefinition(typ: AST.TypKnoten): AST.Definition.Typdefinition {
    val teilWörter = typ.name.bezeichner.typ.teilWörter
    if (typ.modulPfad.isEmpty()) {
      var typDefinition: AST.Definition.Typdefinition? = null
      for (definitionen in durchlaufeDefinitionsContainer(typ)) {
        val hauptWort = typ.name.hauptWort(typ.name.fälle.first(), typ.name.numerus!!)
        for (i in teilWörter.indices) {
          val typName = teilWörter.dropLast(1).drop(teilWörter.size - i).joinToString("") + hauptWort
          if (definitionen.definierteTypen.containsKey(typName)) {
            typDefinition = definitionen.definierteTypen.getValue(typName)
          }
          if (definitionen.verwendeteTypen.containsKey(typName)) {
            val gefundeneKlassenDefinition = definitionen.verwendeteTypen.getValue(typName)
            if (typDefinition != null && typDefinition != gefundeneKlassenDefinition) {
              throw GermanSkriptFehler.Mehrdeutigkeit.Typ(typ.name.bezeichner.toUntyped(), typDefinition,
                gefundeneKlassenDefinition)
            }
            typDefinition = gefundeneKlassenDefinition
          }
          for (verwendetesModul in definitionen.verwendeteModule) {
            if (verwendetesModul.definierteTypen.containsKey(typName)) {
              val gefundeneKlassenDefinition = verwendetesModul.definierteTypen.getValue(typName)
              if (typDefinition != null && typDefinition != gefundeneKlassenDefinition) {
                throw GermanSkriptFehler.Mehrdeutigkeit.Typ(typ.name.bezeichner.toUntyped(), typDefinition,
                verwendetesModul.definierteTypen.getValue(typName))
              }
              typDefinition = gefundeneKlassenDefinition
            }
          }
        }
      }
      return typDefinition ?:
        throw GermanSkriptFehler.Undefiniert.Typ(typ.name.bezeichner.toUntyped(), typ)
    } else {
      val modul = modulAuflöser.löseModulPfadAuf(typ, typ.modulPfad)
      for (i in teilWörter.indices) {
        val klassenName = teilWörter.drop(teilWörter.size - 1 - i).joinToString("")
        if (modul.definitionen.definierteTypen.containsKey(klassenName)) {
          return modul.definitionen.definierteTypen.getValue(klassenName)
        }
      }
      throw GermanSkriptFehler.Undefiniert.Typ(typ.name.bezeichner.toUntyped(), typ)
    }
  }

  private fun holeVollenNameVonFunktionsSignatur(
      funktionsSignatur: AST.Definition.FunktionsSignatur): String {
    var vollerName = funktionsSignatur.name.wert
    if (funktionsSignatur.objekt != null) {
      val objekt = funktionsSignatur.objekt
      val typ = objekt.typKnoten.name
      vollerName += " " + objekt.name.vornomenString!! + " " + objekt.name.hauptWort(typ.fälle.first(), typ.numerus!!)
    }
    else if (funktionsSignatur.reflexivPronomen != null) {
      vollerName += " ${funktionsSignatur.reflexivPronomen.wert}"
    }
    for (präposition in funktionsSignatur.präpositionsParameter) {
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
    if (funktionsSignatur.suffix != null) {
      vollerName += " " + funktionsSignatur.suffix.wert
    }
    funktionsSignatur.vollerName = vollerName
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
        vollerName += holeArgumentString(objekt)
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
        vollerName += holeArgumentString(argument)
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

  private fun holeArgumentString(argument: AST.Argument): String {
    return if (argument.adjektiv != null) {
      val artikel = GrammatikPrüfer.holeVornomen(
          TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT, argument.name.fälle.first(), Genus.NEUTRUM, argument.name.numerus!!)
      " " + artikel + " " + argument.adjektiv.normalisierung
    } else {
      val artikel = GrammatikPrüfer.holeVornomen(
          TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT,
          argument.name.fälle.first(),
          argument.name.genus,
          argument.name.numerus!!
      )

      " " + artikel + " " + argument.name.hauptWort(argument.name.fälle.first(), argument.name.numerus!!)
    }
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
  val typDefinitionen get(): Sequence<AST.Definition.Typdefinition> = sequence {
    // Der Rückgabetyp der Funktionen muss explizit dranstehen. Ansonsten gibt es einen internen Fehler
    fun holeKlassenDefinitionen(container: AST.DefinitionsContainer):
        Sequence<AST.Definition.Typdefinition> = sequence {
      yieldAll(container.definierteTypen.values)
      for (modul in container.module.values) {
        yieldAll(holeKlassenDefinitionen(modul.definitionen))
      }
    }

    yieldAll(holeKlassenDefinitionen(ast.definitionen))
  }

  inline fun<reified T: AST.Definition.Typdefinition> holeDefinitionen() = typDefinitionen.filter { it is T }.map {it as T}

  /** holt eine globale Klasse über den Namen der Klasse */
  fun holeTypDefinition(klassenName: String): AST.Definition.Typdefinition {
    return ast.definitionen.definierteTypen.getValue(klassenName)
  }

  fun gebeKlassenDefinitionenAus() {
    for (klasse in holeDefinitionen<AST.Definition.Typdefinition.Klasse>())  {
      println("${klasse.typ.name.bezeichner.wert}: $klasse")
    }
  }

  fun gebeFunktionsDefinitionenAus() {
    for (funktion in funktionsDefinitionen) {
      println("${funktion.signatur.vollerName!!}: $funktion")
    }
  }

  private fun definiereFunktion(funktionsDefinition: AST.Definition.FunktionOderMethode.Funktion) {
    val vollerName = holeVollenNameVonFunktionsSignatur(funktionsDefinition.signatur)
    val definitionsContainer = funktionsDefinition.findNodeInParents<AST.DefinitionsContainer>()!!
    if (definitionsContainer.funktionen.containsKey(vollerName)) {
      throw GermanSkriptFehler.DoppelteDefinition.Funktion(
          funktionsDefinition.signatur.name.toUntyped(),
          definitionsContainer.funktionen.getValue(vollerName)
      )
    }
    definitionsContainer.funktionen[vollerName] = funktionsDefinition
  }

  private fun definiereMethode(methodenDefinition: AST.Definition.FunktionOderMethode.Methode) {
    val vollerName = holeVollenNameVonFunktionsSignatur(methodenDefinition.funktion.signatur)
    val klasse = holeTypDefinition(methodenDefinition.klasse)
    if (klasse !is AST.Definition.Typdefinition.Klasse) {
      throw GermanSkriptFehler.KlasseErwartet(methodenDefinition.klasse.name.bezeichner.toUntyped())
    }
    if (klasse.methoden.containsKey(vollerName)) {
      throw GermanSkriptFehler.DoppelteDefinition.Methode(
          methodenDefinition.funktion.signatur.name.toUntyped(),
          klasse.methoden.getValue(vollerName)
      )
    }
    klasse.methoden[vollerName] = methodenDefinition
  }

  private fun definiereKonvertierung(konvertierung: AST.Definition.Konvertierung) {
    val klasse = holeTypDefinition(konvertierung.klasse)
    if (klasse !is AST.Definition.Typdefinition.Klasse) {
      throw GermanSkriptFehler.KlasseErwartet(konvertierung.klasse.name.bezeichner.toUntyped())
    }
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