import util.SimpleLogger
import java.util.*

class GrammatikPrüfer(dateiPfad: String): PipelineKomponente(dateiPfad) {
  val deklanierer = Deklanierer(dateiPfad)
  val ast = deklanierer.ast

  val logger = SimpleLogger()

  fun prüfe() {
    deklanierer.deklaniere()

    ast.visit() { knoten ->
      when (knoten) {
        is AST.Definition.Funktion -> prüfeFunktionsDefinition(knoten)
        is AST.Satz.VariablenDeklaration -> prüfeVariablendeklaration(knoten)
        is AST.Satz.FürJedeSchleife -> prüfeFürJedeSchleife(knoten)
        is AST.Satz.FunktionsAufruf -> prüfeFunktionsAufruf(knoten.aufruf)
        is AST.Ausdruck.FunktionsAufruf -> prüfeFunktionsAufruf(knoten.aufruf)
        is AST.Satz.Zurückgabe -> prüfeZurückgabe(knoten)
        is AST.Ausdruck -> when (knoten) {
            is AST.Ausdruck.BinärerAusdruck -> prüfeBinärenAusdruck(knoten)
            is AST.Ausdruck.Minus -> prüfeMinus(knoten)
            else -> return@visit false
        }
      }
      // visit everything
      true
    }
  }

  private fun prüfeZurückgabe(zurückgabe: AST.Satz.Zurückgabe) {
    if (zurückgabe.ausdruck is AST.Ausdruck.Variable) {
      val variable = zurückgabe.ausdruck
      prüfeNomen(variable.name, EnumSet.of(Kasus.AKKUSATIV))
    }
  }

  private fun prüfeNomen(nomen: AST.Nomen, fälle: EnumSet<Kasus>) {
    if (nomen.geprüft) {
      return
    }
    val deklanation = deklanierer.holeDeklination(nomen)
    val numerus = deklanation.getNumerus(nomen.bezeichner.wert)
    nomen.numerus = numerus
    nomen.nominativ = deklanation.getForm(Kasus.NOMINATIV, numerus)
    nomen.nominativSingular = deklanation.getForm(Kasus.NOMINATIV, Numerus.SINGULAR)
    nomen.nominativPlural = deklanation.getForm(Kasus.NOMINATIV, Numerus.PLURAL)
    nomen.genus = deklanation.genus
    for (kasus in fälle) {
      val erwarteteForm = deklanation.getForm(kasus, numerus)
      if (nomen.bezeichner.wert == erwarteteForm) {
        nomen.fälle.add(kasus)
      }
    }
    if (nomen.fälle.isEmpty()) {
      // TODO: berücksichtige auch die möglichen anderen Fälle in der Fehlermeldung
      val kasus = fälle.first()
      val erwarteteForm = deklanation.getForm(kasus, numerus)
      throw GermanScriptFehler.GrammatikFehler.FormFehler.FalschesNomen(nomen.bezeichner.toUntyped(), kasus, nomen, erwarteteForm)
    }
    prüfeVornomen(nomen)
  }

  private fun prüfeVornomen(nomen: AST.Nomen)
  {
    if (nomen.vornomen == null) {
      return
    }
    val vorNomen = nomen.vornomen
    val ersterFall = nomen.fälle.first()
    for (kasus in nomen.fälle) {
      val erwartetesVornomen = holeVornomen(vorNomen.typ, kasus, nomen.genus!!, nomen.numerus!!)
      if (vorNomen.wert == erwartetesVornomen) {
        nomen.vornomenString = erwartetesVornomen
      } else {
        nomen.fälle.remove(kasus)
      }
    }

    if (nomen.vornomenString == null) {
      val erwartetesVornomen = holeVornomen(vorNomen.typ, ersterFall, nomen.genus!!, nomen.numerus!!)
      throw GermanScriptFehler.GrammatikFehler.FormFehler.FalschesVornomen(vorNomen.toUntyped(), ersterFall, nomen, erwartetesVornomen)
    }
  }

  private fun holeVornomen(vorNomen: TokenTyp.VORNOMEN, kasus: Kasus, genus: Genus, numerus: Numerus): String {
    val kasusIndex = kasus.ordinal
    val spaltenIndex = if (numerus == Numerus.SINGULAR) genus.ordinal else 3
    return VORNOMEN_TABELLE.getValue(vorNomen)[kasusIndex][spaltenIndex]
  }

  private fun prüfeNumerus(nomen: AST.Nomen, numerus: Numerus) {
    if (nomen.numerus!! != numerus) {
      val numerusForm = deklanierer.holeDeklination(nomen).getForm(nomen.fälle.first(), numerus)
      throw GermanScriptFehler.GrammatikFehler.FalscherNumerus(nomen.bezeichner.toUntyped(), numerus, numerusForm)
    }
  }

  // region kontextbasierte Ausdrücke
  private fun prüfeKontextbasiertenAusdruck(ausdruck: AST.Ausdruck, kontextNomen: AST.Nomen?, fälle: EnumSet<Kasus>) {
    when (ausdruck) {
      is AST.Ausdruck.Variable -> prüfeVariable(ausdruck, kontextNomen, fälle)
      is AST.Ausdruck.Liste ->  prüfeListe(ausdruck, kontextNomen, fälle)
      is AST.Ausdruck.ListenElement -> prüfeListenElement(ausdruck, kontextNomen, fälle)
      is AST.Ausdruck.Konvertierung -> prüfeKonvertierung(ausdruck, kontextNomen, fälle)
      is AST.Ausdruck.BinärerAusdruck -> prüfeBinärenAusdruck(ausdruck)
    }
  }

  private fun prüfeVariable(variable: AST.Ausdruck.Variable, kontextNomen: AST.Nomen?, fälle: EnumSet<Kasus>) {
    prüfeNomen(variable.name, fälle)
    if (kontextNomen != null) {
      prüfeNumerus(variable.name, kontextNomen.numerus!!)
    }
  }

  private fun prüfeListe(liste: AST.Ausdruck.Liste, kontextNomen: AST.Nomen?, fälle: EnumSet<Kasus>) {
    prüfeNomen(liste.pluralTyp, fälle)
    prüfeNumerus(liste.pluralTyp, Numerus.PLURAL)
    if (kontextNomen != null) {
      prüfeNumerus(kontextNomen, Numerus.PLURAL)
    }
  }

  private fun prüfeListenElement(listenElement: AST.Ausdruck.ListenElement, kontextNomen: AST.Nomen?, fälle: EnumSet<Kasus>) {
    prüfeNomen(listenElement.singular, fälle)
    prüfeNumerus(listenElement.singular, Numerus.SINGULAR)
    if (kontextNomen != null) {
      prüfeNumerus(kontextNomen, Numerus.SINGULAR)
    }
    prüfeKontextbasiertenAusdruck(listenElement.index, null, EnumSet.of(Kasus.NOMINATIV))
  }

  private fun prüfeKonvertierung(konvertierung: AST.Ausdruck.Konvertierung, kontextNomen: AST.Nomen?, fälle: EnumSet<Kasus>) {
    prüfeNomen(konvertierung.typ.name, EnumSet.of(Kasus.NOMINATIV))
    prüfeKontextbasiertenAusdruck(konvertierung.ausdruck, kontextNomen, fälle)
  }
  // endregion

  private fun prüfeVariablendeklaration(variablenDeklaration: AST.Satz.VariablenDeklaration) {
    val nomen = variablenDeklaration.name
    prüfeNomen(nomen, EnumSet.of(Kasus.NOMINATIV))
    // prüfe ob Numerus mit 'ist' oder 'sind' übereinstimmt
    if (!variablenDeklaration.zuweisungsOperator.typ.numerus.contains(nomen.numerus!!)) {
      throw GermanScriptFehler.GrammatikFehler.FalscheZuweisung(variablenDeklaration.zuweisungsOperator.toUntyped(), nomen.numerus!!)
    }
    // logger.addLine("geprüft: $variablenDeklaration")
    prüfeKontextbasiertenAusdruck(variablenDeklaration.ausdruck, nomen, EnumSet.of(Kasus.NOMINATIV))
  }

  private fun prüfeFürJedeSchleife(fürJedeSchleife: AST.Satz.FürJedeSchleife) {
    prüfeNomen(fürJedeSchleife.binder, EnumSet.of(Kasus.AKKUSATIV))
    prüfeNumerus(fürJedeSchleife.binder, Numerus.SINGULAR)
    if (fürJedeSchleife.liste != null) {
      prüfeNomen(fürJedeSchleife.liste.pluralTyp, EnumSet.of(Kasus.DATIV))
      prüfeNumerus(fürJedeSchleife.liste.pluralTyp, Numerus.PLURAL)
    } else if (fürJedeSchleife.singular != null)  {
      prüfeNomen(fürJedeSchleife.singular, EnumSet.of(Kasus.AKKUSATIV))
      prüfeNumerus(fürJedeSchleife.singular, Numerus.SINGULAR)
    }
  }


  private fun prüfeParameter(parameter: AST.Definition.Parameter, fälle: EnumSet<Kasus>) {
    val nomen = parameter.typKnoten.name
    prüfeNomen(nomen, fälle)
    prüfeNomen(parameter.name, EnumSet.of(Kasus.NOMINATIV))
    if (parameter.name.vornomenString == null) {
      val paramName = parameter.name
      paramName.vornomenString = holeVornomen(TokenTyp.VORNOMEN.ARTIKEL_BESTIMMT, nomen.fälle.first(), paramName.genus!!, paramName.numerus!!)
    }
  }

  private fun prüfeFunktionsDefinition(funktionsDefinition: AST.Definition.Funktion) {
    if (funktionsDefinition.rückgabeTyp != null) {
      prüfeNomen(funktionsDefinition.rückgabeTyp.name, EnumSet.of(Kasus.NOMINATIV))
    }
    if (funktionsDefinition.objekt != null) {
      prüfeParameter(funktionsDefinition.objekt, EnumSet.of(Kasus.DATIV, Kasus.AKKUSATIV))
    }
    for (präposition in funktionsDefinition.präpositionsParameter) {
      prüfePräpositionsParameter(präposition)
    }
    // logger.addLine("geprüft: $funktionsDefinition")
  }

  private fun prüfePräpositionsParameter(präposition: AST.Definition.PräpositionsParameter) {
    for (parameter in präposition.parameter) {
      prüfeParameter(parameter, präposition.präposition.fälle)
    }
  }

  private fun prüfeArgument(argument: AST.Argument, fälle: EnumSet<Kasus>) {
    prüfeNomen(argument.name, fälle)
    prüfeKontextbasiertenAusdruck(argument.wert, argument.name, EnumSet.of(Kasus.NOMINATIV))
  }

  private fun prüfePräpositionsArgumente(präposition: AST.PräpositionsArgumente) {
    for (argument in präposition.argumente) {
      prüfeArgument(argument, präposition.präposition.fälle)
    }
  }

  private fun prüfeFunktionsAufruf(funktionsAufruf: AST.FunktionsAufruf) {
    if (funktionsAufruf.objekt != null) {
      prüfeArgument(funktionsAufruf.objekt, EnumSet.of(Kasus.AKKUSATIV))
    }
    for (präposition in funktionsAufruf.präpositionsArgumente) {
      prüfePräpositionsArgumente(präposition)
    }
    // logger.addLine("geprüft: $funktionsAufruf")
  }

  // TODO: binären Ausdruck sollte auch kontextsensitiv sein
  private fun prüfeBinärenAusdruck(binärerAusdruck: AST.Ausdruck.BinärerAusdruck) {
    val rechterKasus = binärerAusdruck.operator.typ.operator.klasse.kasus
    val linkerKasus = if (binärerAusdruck.istAnfang) Kasus.NOMINATIV else rechterKasus

    prüfeKontextbasiertenAusdruck(binärerAusdruck.links, null, EnumSet.of(linkerKasus))
    prüfeKontextbasiertenAusdruck(binärerAusdruck.rechts, null, EnumSet.of(rechterKasus))
    logger.addLine("geprüft: $binärerAusdruck")
  }

  private fun prüfeMinus(knoten: AST.Ausdruck.Minus) {
    if (knoten.ausdruck is AST.Ausdruck.Variable) {
      val variable = knoten.ausdruck
      prüfeNomen(variable.name, EnumSet.of(Kasus.AKKUSATIV))
    }
  }
}

private val VORNOMEN_TABELLE = mapOf<TokenTyp.VORNOMEN, Array<Array<String>>>(
    TokenTyp.VORNOMEN.ARTIKEL_BESTIMMT to arrayOf(
        arrayOf("der", "die", "das", "die"),
        arrayOf("des", "der", "des", "der"),
        arrayOf("dem", "der", "dem", "den"),
        arrayOf("den", "die", "das", "die")
    ),

    TokenTyp.VORNOMEN.ARTIKEL_UNBESTIMMT to arrayOf(
        arrayOf("ein", "eine", "ein", "einige"),
        arrayOf("eines", "einer", "eines", "einiger"),
        arrayOf("einem", "einer", "einem", "einigen"),
        arrayOf("einen", "eine", "ein", "einige")
    ),

    TokenTyp.VORNOMEN.JEDE to arrayOf(
        arrayOf("jeder", "jede", "jedes", "alle"),
        arrayOf("jedes", "jeder", "jedes", "aller"),
        arrayOf("jedem", "jeder", "jedem", "allen"),
        arrayOf("jeden", "jede", "jedes", "alle")
    )
)

fun main() {
  val grammatikPrüfer = GrammatikPrüfer("./iterationen/iter_2/code.gms")
  grammatikPrüfer.prüfe()
  grammatikPrüfer.logger.print()
}

