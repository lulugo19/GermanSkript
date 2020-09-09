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
    // Benenne die Methodensignaturen der Schnittstellen
    definitionen.definierteTypen.values.forEach { schnittstelle ->
      if (schnittstelle is AST.Definition.Typdefinition.Schnittstelle) {
        schnittstelle.methodenSignaturen.forEach { holeVollenNamenVonFunktionsSignatur(it) }
      }
    }

    definitionen.funktionsListe.forEach(::definiereFunktion)
    definitionen.implementierungen.forEach(::definiereImplementierung)
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

  fun holeFunktionsDefinition(funktionsAufruf: AST.FunktionsAufruf): AST.Definition.Funktion {
    if (funktionsAufruf.vollerName == null) {
      funktionsAufruf.vollerName = holeVollenNamenVonFunktionsAufruf(funktionsAufruf, null)
    }
    if (funktionsAufruf.modulPfad.isEmpty()) {
      var funktionsDefinition: AST.Definition.Funktion? = null
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
    val teilWörter = when (typ.name) {
      is AST.WortArt.Nomen -> typ.name.bezeichner.typ.teilWörter
      is AST.WortArt.Adjektiv -> arrayOf(typ.name.normalisierung)
    }
    val hauptWort = when (typ.name) {
      is AST.WortArt.Nomen -> {
        // ein kleiner Hack damit Listenerstellungen mit Schnittstellen funktionieren
        if (typ.name.deklination!!.istNominalisiertesAdjektiv && typ.name.numerus == Numerus.PLURAL)
          typ.name.hauptWort(Kasus.NOMINATIV, Numerus.PLURAL)
        else typ.name.hauptWort(Kasus.NOMINATIV, Numerus.SINGULAR)
      }
      is AST.WortArt.Adjektiv -> typ.name.normalisierung
    }
    if (typ.modulPfad.isEmpty()) {
      var typDefinition: AST.Definition.Typdefinition? = null
      for (definitionen in durchlaufeDefinitionsContainer(typ)) {
        for (i in teilWörter.indices) {
          val typName = teilWörter.dropLast(1).drop(i).joinToString("") + hauptWort
          if (definitionen.definierteTypen.containsKey(typName)) {
            typDefinition = definitionen.definierteTypen.getValue(typName)
          }
          if (definitionen.verwendeteTypen.containsKey(typName)) {
            val gefundeneDefinition = definitionen.verwendeteTypen.getValue(typName)
            if (typDefinition != null && typDefinition != gefundeneDefinition) {
              throw GermanSkriptFehler.Mehrdeutigkeit.Typ(typ.name.bezeichnerToken, typDefinition,
                gefundeneDefinition)
            }
            typDefinition = gefundeneDefinition
          }
          for (verwendetesModul in definitionen.verwendeteModule) {
            if (verwendetesModul.definierteTypen.containsKey(typName)) {
              val gefundeneDefinition = verwendetesModul.definierteTypen.getValue(typName)
              if (typDefinition != null && typDefinition != gefundeneDefinition) {
                throw GermanSkriptFehler.Mehrdeutigkeit.Typ(typ.name.bezeichnerToken, typDefinition,
                verwendetesModul.definierteTypen.getValue(typName))
              }
              typDefinition = gefundeneDefinition
            }
          }
          if (typDefinition != null) {
            break
          }
        }
      }
      return typDefinition ?:
        throw GermanSkriptFehler.Undefiniert.Typ(typ.name.bezeichnerToken, typ)
    } else {
      val modul = modulAuflöser.löseModulPfadAuf(typ, typ.modulPfad)
      for (i in teilWörter.indices) {
        val typName = teilWörter.dropLast(1).drop(i).joinToString("") + hauptWort
        if (modul.definitionen.definierteTypen.containsKey(typName)) {
          return modul.definitionen.definierteTypen.getValue(typName)
        }
      }
      throw GermanSkriptFehler.Undefiniert.Typ(typ.name.bezeichnerToken, typ)
    }
  }

  fun holeKonstante(konstante: AST.Ausdruck.Konstante): AST.Definition.Konstante {
    val konstName = konstante.name.wert
    if (konstante.modulPfad.isEmpty()) {
      var konstantenDef: AST.Definition.Konstante? = null
      for (definitionen in durchlaufeDefinitionsContainer(konstante)) {
        if (definitionen.konstanten.containsKey(konstName)) {
          konstantenDef = definitionen.konstanten.getValue(konstName)
        }
        if (definitionen.verwendeteKonstanten.containsKey(konstName)) {
          val gefundeneDefinition = definitionen.verwendeteKonstanten.getValue(konstName)
          if (konstantenDef != null && konstantenDef != gefundeneDefinition) {
            throw GermanSkriptFehler.Mehrdeutigkeit.Konstante(konstante.name.toUntyped(), konstantenDef,
                gefundeneDefinition)
          }
          konstantenDef = gefundeneDefinition

        for (verwendetesModul in definitionen.verwendeteModule) {
          if (verwendetesModul.konstanten.containsKey(konstName)) {
            val gefundeneDefinition = verwendetesModul.konstanten.getValue(konstName)
            if (konstantenDef != null && konstantenDef != gefundeneDefinition) {
              throw GermanSkriptFehler.Mehrdeutigkeit.Konstante(konstante.name.toUntyped(), konstantenDef,
                  verwendetesModul.konstanten.getValue(konstName))
            }
            konstantenDef = gefundeneDefinition
          }
        }
        }
      }
      return konstantenDef ?:
      throw GermanSkriptFehler.Undefiniert.Konstante(konstante.name.toUntyped(), konstante)
    } else {
      val modul = modulAuflöser.löseModulPfadAuf(konstante, konstante.modulPfad)
      if (modul.definitionen.konstanten.containsKey(konstName)) {
        return modul.definitionen.konstanten.getValue(konstName)
      }
      throw GermanSkriptFehler.Undefiniert.Konstante(konstante.name.toUntyped(), konstante)
    }
  }

  private fun holeVollenNamenVonFunktionsSignatur(
      funktionsSignatur: AST.Definition.FunktionsSignatur,
      holeParamName: (AST.Definition.Parameter) -> AST.WortArt = {it.name}
  ): String {
    var vollerName = funktionsSignatur.name.wert
    if (funktionsSignatur.objekt != null) {
      val objekt = funktionsSignatur.objekt
      vollerName += holeParamString(objekt, holeParamName)
    }
    else if (funktionsSignatur.reflexivPronomen != null) {
      vollerName += " ${funktionsSignatur.reflexivPronomen.wert}"
    }
    for (präposition in funktionsSignatur.präpositionsParameter) {
      vollerName += " " + präposition.präposition.präposition.wert
      for (parameterIndex in präposition.parameter.indices) {
        val parameter = präposition.parameter[parameterIndex]
        vollerName += holeParamString(parameter, holeParamName)
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

  private fun holeParamString(
      param: AST.Definition.Parameter,
      holeParamName: (AST.Definition.Parameter) -> AST.WortArt = {it.name}
  ): String {
    val paramName = holeParamName(param)
    val artikel = GrammatikPrüfer.holeVornomen(
        TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT,
        param.typKnoten.name.fälle.first(),
        paramName.genus,
        param.name.numerus!!
    )
    return " " + artikel + " " + paramName.hauptWort(param.name.fälle.first(), param.name.numerus!!)
  }

  fun holeVollenNamenVonFunktionsSignatur(
      funktionsSignatur: AST.Definition.FunktionsSignatur,
      typTypArgs: List<AST.TypKnoten>): String =
      holeVollenNamenVonFunktionsSignatur(funktionsSignatur) { param ->
        holeParamName(param, typTypArgs)
      }

  private fun holeParamName(
      parameter: AST.Definition.Parameter,
      typTypArgs: List<AST.TypKnoten>): AST.WortArt {
    val typ = parameter.typKnoten.typ!!
    return if (parameter.typIstName && typ is Typ.Generic) {
      typTypArgs[typ.index].name
    } else {
      parameter.name
    }
  }

  fun holeVollenNamenVonFunktionsAufruf(
      funktionsAufruf: AST.FunktionsAufruf,
      ersetzeObjekt: String?,
      holeArgName: (AST.Argument) -> AST.WortArt.Nomen = {it.name}
  ): String {
    var vollerName = funktionsAufruf.verb.wert
    if (funktionsAufruf.objekt != null) {
      val objekt = funktionsAufruf.objekt
      if (ersetzeObjekt != null) {
        vollerName += " $ersetzeObjekt"
      } else {
        vollerName += holeArgumentString(objekt, holeArgName)
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
        vollerName += holeArgumentString(argument, holeArgName)
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

  fun holeVollenNamenVonFunktionsAufruf(
      funktionsAufruf: AST.FunktionsAufruf,
      typTypParams: List<AST.WortArt.Nomen>,
      typTypArgs: List<AST.TypKnoten>
  ): String {
    return holeVollenNamenVonFunktionsAufruf(funktionsAufruf, null) { argument ->
      holeArgName(argument, typTypParams, typTypArgs)
    }
  }

  private fun holeArgumentString(argument: AST.Argument, holeArgName: (AST.Argument) -> AST.WortArt.Nomen): String {
    val argName = holeArgName(argument)
    return if (argument.adjektiv != null) {
      val artikel = GrammatikPrüfer.holeVornomen(
          TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT, argument.name.kasus, Genus.NEUTRUM, argument.name.numerus!!)
      " " + artikel + " " + argument.adjektiv.ganzesWort(argument.name.kasus, argument.name.numerus!!)
    } else {
      val artikel = GrammatikPrüfer.holeVornomen(
          TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT,
          argument.name.kasus,
          argName.genus,
          argument.name.numerus!!
      )

      " " + artikel + " " + argName.hauptWort(argument.name.kasus, argument.name.numerus!!)
    }
  }

  private fun holeArgName(
      argument: AST.Argument,
      typTypParams: List<AST.WortArt.Nomen>,
      typTypArgs: List<AST.TypKnoten>
  ): AST.WortArt.Nomen {
    val ersetzeArgIndex = typTypArgs.indexOfFirst { arg -> arg.name.nominativ == argument.name.nominativ }
    if (ersetzeArgIndex != -1) {
      return typTypParams[ersetzeArgIndex]
    }
    return argument.name
  }

  /**
   * Gibt alle Funktionsdefinitionen zurück.
   */
  val funktionsDefinitionen get(): Sequence<AST.Definition.Funktion> = sequence {
    // Der Rückgabetyp der Funktionen muss explizit dranstehen. Ansonsten gibt es einen internen Fehler
    fun holeFunktionsDefinitionen(container: AST.DefinitionsContainer):
        Sequence<AST.Definition.Funktion> = sequence {
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
    fun holeTypDefinition(container: AST.DefinitionsContainer):
        Sequence<AST.Definition.Typdefinition> = sequence {
      yieldAll(container.definierteTypen.values)
      for (modul in container.module.values) {
        yieldAll(holeTypDefinition(modul.definitionen))
      }
    }

    yieldAll(holeTypDefinition(ast.definitionen))
  }

  val konstanten get(): Sequence<AST.Definition.Konstante> = sequence {
    fun holeKonstante(container: AST.DefinitionsContainer):
      Sequence<AST.Definition.Konstante> = sequence {
      yieldAll(container.konstanten.values)
      for (modul in container.module.values) {
        yieldAll(holeKonstante(modul.definitionen))
      }
    }

    yieldAll(holeKonstante(ast.definitionen))
  }

  inline fun<reified T: AST.Definition.Typdefinition> holeDefinitionen() = typDefinitionen.filter { it is T }.map {it as T}

  /** holt eine globale Klasse über den Namen der Klasse */
  fun holeTypDefinition(klassenName: String): AST.Definition.Typdefinition {
    return ast.definitionen.definierteTypen.getValue(klassenName)
  }

  fun gebeKlassenDefinitionenAus() {
    for (klasse in holeDefinitionen<AST.Definition.Typdefinition.Klasse>())  {
      println("${klasse.name.bezeichner.wert}: $klasse")
    }
  }

  fun gebeFunktionsDefinitionenAus() {
    for (funktion in funktionsDefinitionen) {
      println("${funktion.signatur.vollerName!!}: $funktion")
    }
  }

  private fun definiereFunktion(funktionsDefinition: AST.Definition.Funktion) {
    val vollerName = holeVollenNamenVonFunktionsSignatur(funktionsDefinition.signatur)
    val definitionsContainer = funktionsDefinition.findNodeInParents<AST.DefinitionsContainer>()!!
    if (definitionsContainer.funktionen.containsKey(vollerName)) {
      throw GermanSkriptFehler.DoppelteDefinition.Funktion(
          funktionsDefinition.signatur.name.toUntyped(),
          definitionsContainer.funktionen.getValue(vollerName)
      )
    }
    definitionsContainer.funktionen[vollerName] = funktionsDefinition
  }

  private fun definiereImplementierung(implementierung: AST.Definition.Implementierung) {
    val klasse = holeTypDefinition(implementierung.klasse)
    if (klasse !is AST.Definition.Typdefinition.Klasse) {
      throw GermanSkriptFehler.KlasseErwartet(implementierung.klasse.name.bezeichnerToken)
    }
    klasse.implementierungen += implementierung
    implementierung.methoden.forEach { methode -> definiereMethode(methode, klasse) }
    implementierung.eigenschaften.forEach { eigenschaft -> definiereEigenschaft(eigenschaft, klasse) }
    implementierung.konvertierungen.forEach { konvertierung -> definiereKonvertierung(konvertierung, klasse) }
  }

  private fun definiereKonvertierung(konvertierung: AST.Definition.Konvertierung, klasse: AST.Definition.Typdefinition.Klasse) {
    val typName = konvertierung.typ.name.nominativ
    if (klasse.konvertierungen.containsKey(typName)) {
      throw GermanSkriptFehler.DoppelteDefinition.Konvertierung(
          konvertierung.typ.name.bezeichnerToken,
          klasse.konvertierungen.getValue(typName)
      )
    }
    klasse.konvertierungen[typName] = konvertierung
  }

  private fun definiereMethode(methode: AST.Definition.Funktion, klasse: AST.Definition.Typdefinition.Klasse) {
    val vollerName = holeVollenNamenVonFunktionsSignatur(methode.signatur)
    if (klasse.methoden.containsKey(vollerName)) {
      throw GermanSkriptFehler.DoppelteDefinition.Methode(
          methode.signatur.name.toUntyped(),
          klasse.methoden.getValue(vollerName),
          klasse
      )
    }
    klasse.methoden[vollerName] = methode
  }

  private fun definiereEigenschaft(eigenschaft: AST.Definition.Eigenschaft, klasse: AST.Definition.Typdefinition.Klasse) {
    val eigenschaftsName = eigenschaft.name.nominativ
    if (klasse.berechneteEigenschaften.containsKey(eigenschaftsName)) {
      throw GermanSkriptFehler.DoppelteDefinition.Eigenschaft(
          eigenschaft.name.bezeichner.toUntyped(),
          klasse.berechneteEigenschaften.getValue(eigenschaftsName)
      )
    }
    if (klasse.eigenschaften.any { eigenschaft -> eigenschaft.name.nominativ == eigenschaftsName }) {
      throw GermanSkriptFehler.DoppelteEigenschaft(eigenschaft.name.bezeichner.toUntyped(), klasse)
    }
    klasse.berechneteEigenschaften[eigenschaftsName] = eigenschaft
  }
}

fun main() {
  val definierer = Definierer(File("./iterationen/iter_2/code.gms"))
  definierer.definiere()
  definierer.gebeFunktionsDefinitionenAus()
  definierer.gebeKlassenDefinitionenAus()
}