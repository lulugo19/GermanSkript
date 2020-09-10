package germanskript

import germanskript.util.SimpleLogger
import java.io.File
import java.util.*

class GrammatikPrüfer(startDatei: File): PipelineKomponente(startDatei) {
  val deklinierer = Deklinierer(startDatei)
  val ast = deklinierer.ast

  val logger = SimpleLogger()

  companion object {
    fun holeVornomen(vorNomen: TokenTyp.VORNOMEN, kasus: Kasus, genus: Genus, numerus: Numerus): String {
      val kasusIndex = kasus.ordinal
      val spaltenIndex = if (numerus == Numerus.SINGULAR) genus.ordinal else 3
      return VORNOMEN_TABELLE.getValue(vorNomen)[kasusIndex][spaltenIndex]
    }
  }

  fun prüfe() {
    deklinierer.deklaniere()

    ast.visit() { knoten ->
      when (knoten) {
        is AST.Definition.Funktion -> prüfeFunktionsSignatur(knoten.signatur)
        is AST.Definition.Implementierung -> prüfeImplementierung(knoten)
        is AST.Definition.Konvertierung -> prüfeKonvertierungsDefinition(knoten)
        is AST.Definition.Eigenschaft -> prüfeEigenschaftsDefinition(knoten)
        is AST.Definition.Typdefinition.Klasse -> prüfeKlassenDefinition(knoten)
        is AST.Definition.Typdefinition.Schnittstelle -> prüfeSchnittstelle(knoten)
        is AST.Definition.Typdefinition.Alias -> prüfeAlias(knoten)
        is AST.Satz.VariablenDeklaration -> prüfeVariablendeklaration(knoten)
        is AST.Satz.BedingungsTerm -> prüfeKontextbasiertenAusdruck(knoten.bedingung, null, EnumSet.of(Kasus.NOMINATIV), false)
        is AST.Satz.Zurückgabe -> prüfeKontextbasiertenAusdruck(knoten.ausdruck, null, EnumSet.of(Kasus.AKKUSATIV), false)
        is AST.Satz.FürJedeSchleife -> prüfeFürJedeSchleife(knoten)
        is AST.Satz.Fange -> prüfeParameter(knoten.param, EnumSet.of(Kasus.AKKUSATIV))
        is AST.Satz.Werfe -> prüfeWerfe(knoten)
        is AST.Satz.FunktionsAufruf -> prüfeFunktionsAufruf(knoten.aufruf)
        is AST.MethodenBereich -> prüfeNomen(knoten.name, EnumSet.of(Kasus.NOMINATIV), Numerus.BEIDE)
      }
      // germanskript.visit everything
      true
    }
  }

  private fun prüfeNomen(nomen: AST.WortArt.Nomen, fälle: EnumSet<Kasus>, numerus: EnumSet<Numerus>) {
    if (nomen.geprüft) {
      return
    }
    val bezeichner = nomen.bezeichner.typ
    // Bezeichner mit nur Großbuchstaben sind Symbole
    if (bezeichner.istSymbol) {
      nomen.numerus = Numerus.SINGULAR
      nomen.fälle.addAll(fälle)
    } else {
      val deklination = deklinierer.holeDeklination(nomen)
      val deklinationsNumerus = deklination.getNumerus(bezeichner.hauptWort!!)
      deklinationsNumerus.retainAll(numerus)
      if (deklinationsNumerus.isEmpty()) {
        throw GermanSkriptFehler.GrammatikFehler.FalscherNumerus(
            nomen.bezeichner.toUntyped(), numerus.first(),
            deklination.holeForm(fälle.first(), numerus.first())
        )
      }
      nomen.deklination = deklination

      for (numerus in deklinationsNumerus) {
        nomen.numerus = numerus
        for (kasus in fälle) {
          val erwarteteForm = deklination.holeForm(kasus, numerus)
          if (bezeichner.hauptWort!! == erwarteteForm) {
            nomen.fälle.add(kasus)
          }
        }
        if (nomen.fälle.isNotEmpty()) {
          break
        }
      }
      if (nomen.fälle.isEmpty()) {
        // TODO: berücksichtige auch die möglichen anderen Fälle in der Fehlermeldung
        val kasus = fälle.first()
        val erwarteteForm = bezeichner.ersetzeHauptWort(deklination.holeForm(kasus, nomen.numerus!!))
        throw GermanSkriptFehler.GrammatikFehler.FormFehler.FalschesNomen(nomen.bezeichner.toUntyped(), kasus, nomen, erwarteteForm)
      }
    }
    prüfeVornomen(nomen)
  }

  private fun prüfeAdjektiv(adjektiv: AST.WortArt.Adjektiv, nomen: AST.WortArt.Nomen?) {
    val endung = if (nomen == null) "" else when(nomen.vornomen?.typ) {
      is TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT -> bestimmterArtikelAdjektivEndung
      else -> unbestimmterArtikelAdjektivEndung
    }[if (nomen.numerus!! == Numerus.SINGULAR) nomen.genus.ordinal else 3][nomen.kasus.ordinal]

    // prüfe die korrekte Endung
    if (!adjektiv.bezeichner.wert.endsWith(endung)) {
      throw GermanSkriptFehler.GrammatikFehler.FalscheAdjektivEndung(adjektiv.bezeichner.toUntyped(), endung)
    }
    adjektiv.normalisierung = adjektiv.bezeichner.wert.capitalize().removeSuffix(endung) + "e"

    adjektiv.deklination = deklinierer.holeDeklination(adjektiv)

    if (nomen == null) {
      adjektiv.numerus = Numerus.SINGULAR
      adjektiv.fälle = EnumSet.of(Kasus.NOMINATIV)
    } else {
      adjektiv.numerus = nomen.numerus
      adjektiv.fälle = nomen.fälle
    }
  }

  private fun prüfeVornomen(nomen: AST.WortArt.Nomen)
  {
    if (nomen.vornomen == null || nomen.vornomen!!.typ == TokenTyp.VORNOMEN.ETWAS) {
      if (nomen.deklination?.istNominalisiertesAdjektiv == true) {
        nomen.deklination = zuBestimmterDeklination(nomen.deklination!!)
      }
      if (nomen.vornomen?.typ == TokenTyp.VORNOMEN.ETWAS) {
        nomen.vornomenString = "etwas"
      }
      return
    }
    val vorNomen = nomen.vornomen!!
    val ersterFall = nomen.kasus
    for (kasus in nomen.fälle) {
      val erwartetesVornomen = holeVornomen(vorNomen.typ, kasus, nomen.genus, nomen.numerus!!)
      if (vorNomen.wert == erwartetesVornomen) {
        nomen.vornomenString = erwartetesVornomen
      } else {
        nomen.fälle.remove(kasus)
      }
    }

    if (nomen.vornomenString == null) {
      val erwartetesVornomen = holeVornomen(vorNomen.typ, ersterFall, nomen.genus, nomen.numerus!!)
      throw GermanSkriptFehler.GrammatikFehler.FormFehler.FalschesVornomen(vorNomen.toUntyped(), ersterFall, nomen, erwartetesVornomen)
    }
  }

  private fun zuBestimmterDeklination(deklination: Deklination): Deklination {
    val singularEndungen = arrayOf("e", "en", "en", "e")
    return Deklination(
        deklination.genus,
        deklination.singular.mapIndexed { i, form -> form.dropLast(2) + singularEndungen[i]}.toTypedArray(),
        // Der Plural ist in diesem Fall übernommen werden, weil er sowieso nie verwendet werden sollte
        deklination.plural
    )
  }

  private fun prüfeNumerus(nomen: AST.WortArt.Nomen, numerus: Numerus) {
    if (nomen.numerus!! != numerus) {
      val numerusForm = deklinierer.holeDeklination(nomen).holeForm(nomen.kasus, numerus)
      throw GermanSkriptFehler.GrammatikFehler.FalscherNumerus(nomen.bezeichner.toUntyped(), numerus, numerusForm)
    }
  }

  val bestimmterArtikelAdjektivEndung = arrayOf(
      arrayOf("e", "", "", "en"),
      arrayOf("e", "en", "en", "e"),
      arrayOf("e", "en", "en", "e"),
      arrayOf("en", "en", "en", "en")
  )

  val unbestimmterArtikelAdjektivEndung = arrayOf(
     arrayOf("er", "", "", "en"),
     arrayOf("e", "", "", "e"),
     arrayOf("es", "en", "en", "es"),
     arrayOf("en", "en", "en", "en")
  )

  private fun prüfeTyp(typ: AST.TypKnoten, kasus: EnumSet<Kasus>, numerus: EnumSet<Numerus>, kontextNomen: AST.WortArt.Nomen?) {
    when (typ.name) {
      is AST.WortArt.Nomen -> prüfeNomen(typ.name, kasus, numerus)
      is AST.WortArt.Adjektiv -> prüfeAdjektiv(typ.name, kontextNomen)
    }

    prüfeTypArgumente(typ.typArgumente)
  }

  // region kontextbasierte Ausdrücke
  private fun prüfeKontextbasiertenAusdruck(ausdruck: AST.Ausdruck, kontextNomen: AST.WortArt.Nomen?, fälle: EnumSet<Kasus>, pluralErwartet: Boolean) {
    when (ausdruck) {
      is AST.Ausdruck.Variable -> prüfeVariable(ausdruck, kontextNomen, fälle, pluralErwartet)
      is AST.Ausdruck.Liste ->  prüfeListe(ausdruck, kontextNomen, fälle)
      is AST.Ausdruck.ListenElement -> prüfeListenElement(ausdruck, kontextNomen, fälle, pluralErwartet)
      is AST.Ausdruck.ObjektInstanziierung -> prüfeObjektinstanziierung(ausdruck, kontextNomen, fälle, pluralErwartet)
      is AST.Ausdruck.EigenschaftsZugriff -> prüfeEigenschaftsZugriff(ausdruck, kontextNomen, fälle, pluralErwartet)
      is AST.Ausdruck.MethodenBereichEigenschaftsZugriff -> prüfeNomenKontextBasiert(ausdruck.eigenschaftsName, kontextNomen, fälle, pluralErwartet)
      is AST.Ausdruck.SelbstEigenschaftsZugriff -> prüfeNomenKontextBasiert(ausdruck.eigenschaftsName, kontextNomen, fälle, pluralErwartet)
      is AST.Ausdruck.Konvertierung -> prüfeKonvertierung(ausdruck, kontextNomen, fälle, pluralErwartet)
      is AST.Ausdruck.BinärerAusdruck -> prüfeBinärenAusdruck(ausdruck, kontextNomen, fälle, pluralErwartet)
      is AST.Ausdruck.Closure -> prüfeClosure(ausdruck, kontextNomen, fälle, pluralErwartet)
      is AST.Ausdruck.FunktionsAufruf -> prüfeFunktionsAufruf(ausdruck.aufruf)
      is AST.Ausdruck.Minus -> prüfeMinus(ausdruck)
    }
  }

  private fun prüfeVariable(variable: AST.Ausdruck.Variable, kontextNomen: AST.WortArt.Nomen?, fälle: EnumSet<Kasus>, pluralErwartet: Boolean) {
    val numerus = when {
      kontextNomen != null -> EnumSet.of(kontextNomen.numerus)
      pluralErwartet -> EnumSet.of(Numerus.PLURAL)
      else -> Numerus.BEIDE
    }
    prüfeNomen(variable.name, fälle, numerus)
  }

  private fun prüfeListe(liste: AST.Ausdruck.Liste, kontextNomen: AST.WortArt.Nomen?, fälle: EnumSet<Kasus>) {
    prüfeTyp(liste.pluralTyp, fälle, EnumSet.of(Numerus.PLURAL), null)
    if (kontextNomen != null) {
      prüfeNumerus(kontextNomen, Numerus.PLURAL)
    }
    liste.elemente.forEach {element -> prüfeKontextbasiertenAusdruck(element, null, EnumSet.of(Kasus.NOMINATIV), false)}
  }

  private fun prüfeObjektinstanziierung(instanziierung: AST.Ausdruck.ObjektInstanziierung, kontextNomen: AST.WortArt.Nomen?, fälle: EnumSet<Kasus>, pluralErwartet: Boolean) {
    if (pluralErwartet) {
      throw GermanSkriptFehler.GrammatikFehler.PluralErwartet(instanziierung.klasse.name.bezeichnerToken)
    }
    prüfeTyp(instanziierung.klasse, fälle, EnumSet.of(Numerus.SINGULAR), null)
    if (kontextNomen != null) {
      prüfeNumerus(kontextNomen, Numerus.SINGULAR)
    }
    for (eigenschaftsZuweisung in instanziierung.eigenschaftsZuweisungen) {
      prüfeNomen(eigenschaftsZuweisung.name, EnumSet.of(Kasus.DATIV), Numerus.BEIDE)
      prüfeKontextbasiertenAusdruck(eigenschaftsZuweisung.ausdruck, eigenschaftsZuweisung.name, EnumSet.of(Kasus.NOMINATIV), false)
    }
  }

  private fun prüfeEigenschaftsZugriff(eigenschaftsZugriff: AST.Ausdruck.EigenschaftsZugriff, kontextNomen: AST.WortArt.Nomen?, fälle: EnumSet<Kasus>, pluralErwartet: Boolean) {
    prüfeNomenKontextBasiert(eigenschaftsZugriff.eigenschaftsName, kontextNomen, fälle, pluralErwartet)
    prüfeKontextbasiertenAusdruck(eigenschaftsZugriff.objekt, null, EnumSet.of(Kasus.GENITIV), false)
  }

  private fun prüfeNomenKontextBasiert(
      nomen: AST.WortArt.Nomen,
      kontextNomen: AST.WortArt.Nomen?,
      fälle: EnumSet<Kasus>,
      pluralErwartet: Boolean)
  {
    val numerus = when {
      kontextNomen != null -> EnumSet.of(kontextNomen.numerus)
      pluralErwartet -> EnumSet.of(Numerus.PLURAL)
      else -> Numerus.BEIDE
    }
    prüfeNomen(nomen, fälle, numerus)
    if (kontextNomen != null) {
      prüfeNumerus(kontextNomen, nomen.numerus!!)
    }
  }

  private fun prüfeListenElement(listenElement: AST.Ausdruck.ListenElement, kontextNomen: AST.WortArt.Nomen?, fälle: EnumSet<Kasus>, pluralErwartet: Boolean) {
    if (pluralErwartet) {
      throw GermanSkriptFehler.GrammatikFehler.PluralErwartet(listenElement.singular.bezeichner.toUntyped())
    }
    prüfeNomen(listenElement.singular, fälle, EnumSet.of(Numerus.SINGULAR))
    if (kontextNomen != null) {
      prüfeNumerus(kontextNomen, Numerus.SINGULAR)
    }
    prüfeKontextbasiertenAusdruck(listenElement.index, null, EnumSet.of(Kasus.NOMINATIV), false)
  }

  private fun prüfeClosure(closure: AST.Ausdruck.Closure, kontextNomen: AST.WortArt.Nomen?, fälle: EnumSet<Kasus>, pluralErwartet: Boolean) {
    if (pluralErwartet) {
      throw GermanSkriptFehler.GrammatikFehler.PluralErwartet(closure.schnittstelle.name.bezeichnerToken)
    }
    prüfeTyp(closure.schnittstelle, fälle, EnumSet.of(Numerus.SINGULAR), null)
    if (kontextNomen != null) {
      prüfeNumerus(kontextNomen, Numerus.SINGULAR)
    }
  }

  private fun prüfeKonvertierung(konvertierung: AST.Ausdruck.Konvertierung, kontextNomen: AST.WortArt.Nomen?, fälle: EnumSet<Kasus>, pluralErwartet: Boolean) {
    prüfeTyp(konvertierung.typ, EnumSet.of(Kasus.NOMINATIV), EnumSet.of(Numerus.SINGULAR), null)
    prüfeKontextbasiertenAusdruck(konvertierung.ausdruck, null, fälle, pluralErwartet)
  }

  // endregion
  private fun prüfeVariablendeklaration(variablenDeklaration: AST.Satz.VariablenDeklaration) {
    val nomen = variablenDeklaration.name
    prüfeNomen(nomen, EnumSet.of(Kasus.NOMINATIV), Numerus.BEIDE)
    // prüfe ob Numerus mit 'ist' oder 'sind' übereinstimmt
    if (nomen.numerus != variablenDeklaration.zuweisungsOperator.typ.numerus) {
      throw GermanSkriptFehler.GrammatikFehler.FalscheZuweisung(variablenDeklaration.zuweisungsOperator.toUntyped(), nomen.numerus!!)
    }
    if (variablenDeklaration.neu != null) {
      if (nomen.genus != variablenDeklaration.neu.typ.genus) {
        throw GermanSkriptFehler.GrammatikFehler.FormFehler.FalschesVornomen(
            variablenDeklaration.neu.toUntyped(), Kasus.NOMINATIV, nomen, TokenTyp.NEU.holeForm(nomen.genus)
        )
      }
    }
    // logger.addLine("geprüft: $variablenDeklaration")
    prüfeKontextbasiertenAusdruck(variablenDeklaration.wert, nomen, EnumSet.of(Kasus.NOMINATIV), false)
  }

  private fun prüfeBinärenAusdruck(binärerAusdruck: AST.Ausdruck.BinärerAusdruck, kontextNomen: AST.WortArt.Nomen?, fälle: EnumSet<Kasus>, pluralErwartet: Boolean) {
    val rechterKasus = if (binärerAusdruck.inStringInterpolation) {
      EnumSet.of(Kasus.NOMINATIV) // in einer String Interpolation wird wieder der Nominativ verwendet
    } else {
      EnumSet.of(binärerAusdruck.operator.typ.operator.klasse.kasus)
    }
    val linkerKasus = if (binärerAusdruck.istAnfang) fälle else rechterKasus

    // kontextNomen gilt nur für den linken Ausdruck (für den aller ersten Audruck in dem binären Ausdruck)
    prüfeKontextbasiertenAusdruck(binärerAusdruck.links, kontextNomen, linkerKasus, pluralErwartet)
    prüfeKontextbasiertenAusdruck(binärerAusdruck.rechts, null, rechterKasus, false)
    logger.addLine("geprüft: $binärerAusdruck")
  }

  private fun prüfeFürJedeSchleife(fürJedeSchleife: AST.Satz.FürJedeSchleife) {
    prüfeNomen(fürJedeSchleife.singular, EnumSet.of(Kasus.AKKUSATIV), EnumSet.of(Numerus.SINGULAR))
    if (fürJedeSchleife.jede.typ.genus != fürJedeSchleife.singular.genus) {
      throw GermanSkriptFehler.GrammatikFehler.FormFehler.FalschesVornomen(
          fürJedeSchleife.jede.toUntyped(), Kasus.NOMINATIV, fürJedeSchleife.singular,
          TokenTyp.JEDE.holeForm(fürJedeSchleife.singular.genus)
      )
    }

    prüfeNomen(fürJedeSchleife.binder, EnumSet.of(Kasus.NOMINATIV), EnumSet.of(Numerus.SINGULAR))
    prüfeNumerus(fürJedeSchleife.binder, Numerus.SINGULAR)

    if (fürJedeSchleife.liste != null) {
      prüfeKontextbasiertenAusdruck(fürJedeSchleife.liste, null, EnumSet.of(Kasus.DATIV), true)
    }

    if (fürJedeSchleife.reichweite != null) {
      val (anfang, ende) = fürJedeSchleife.reichweite
      prüfeKontextbasiertenAusdruck(anfang, null, EnumSet.of(Kasus.DATIV), false)
      prüfeKontextbasiertenAusdruck(ende, null, EnumSet.of(Kasus.DATIV), false)
    }
  }

  private fun prüfeWerfe(werfe: AST.Satz.Werfe) {
    prüfeKontextbasiertenAusdruck(werfe.ausdruck, null, EnumSet.of(Kasus.AKKUSATIV), false)
  }

  private fun prüfeParameter(parameter: AST.Definition.Parameter, fälle: EnumSet<Kasus>) {
    if (!parameter.typIstName) {
      prüfeNomen(parameter.name, EnumSet.of(Kasus.NOMINATIV), Numerus.BEIDE)
    }
    prüfeTyp(parameter.typKnoten, fälle, Numerus.BEIDE, parameter.name)
    if (parameter.name.vornomenString == null) {
      val paramName = parameter.name
      paramName.vornomenString = holeVornomen(TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT, parameter.typKnoten.name.kasus, paramName.genus, paramName.numerus!!)
    }
  }

  private fun prüfePräpositionsParameter(präposition: AST.Definition.PräpositionsParameter) {
    for (parameter in präposition.parameter) {
      prüfeParameter(parameter, präposition.präposition.fälle)
    }
  }

  private fun prüfeTypArgumente(typArgumente: List<AST.TypKnoten>) {
    for (arg in typArgumente) {
      prüfeTyp(arg, EnumSet.of(Kasus.NOMINATIV), Numerus.BEIDE, null)
    }
  }

  private fun prüfeTypParameter(typParameter: TypParameter) {
    for (param in typParameter) {
      prüfeNomen(param, EnumSet.of(Kasus.NOMINATIV), EnumSet.of(Numerus.SINGULAR))
    }
  }

  private fun prüfeFunktionsSignatur(signatur: AST.Definition.FunktionsSignatur) {
    prüfeTypParameter(signatur.typParameter)
    prüfeTyp(signatur.rückgabeTyp, EnumSet.of(Kasus.NOMINATIV), Numerus.BEIDE, null)
    if (signatur.objekt != null) {
      prüfeParameter(signatur.objekt, EnumSet.of(Kasus.DATIV, Kasus.AKKUSATIV))
    }
    for (präposition in signatur.präpositionsParameter) {
      prüfePräpositionsParameter(präposition)
    }
  }

  private fun prüfeKlassenDefinition(klasse: AST.Definition.Typdefinition.Klasse) {
    prüfeNomen(klasse.name, EnumSet.of(Kasus.NOMINATIV), EnumSet.of(Numerus.SINGULAR))
    prüfeTypParameter(klasse.typParameter)
    klasse.eigenschaften.forEach {eigenschaft -> prüfeParameter(eigenschaft, EnumSet.of(Kasus.DATIV))}

    if (klasse.elternKlasse != null) {
      prüfeTyp(klasse.elternKlasse, EnumSet.of(Kasus.NOMINATIV), EnumSet.of(Numerus.SINGULAR), null)
    }
  }

  private fun prüfeSchnittstelle(schnittstelle: AST.Definition.Typdefinition.Schnittstelle) {
    prüfeTypParameter(schnittstelle.typParameter)
    schnittstelle.methodenSignaturen.forEach(::prüfeFunktionsSignatur)
  }

  private fun prüfeAlias(alias: AST.Definition.Typdefinition.Alias) {
    prüfeTyp(alias.typ, EnumSet.of(Kasus.NOMINATIV), EnumSet.of(Numerus.SINGULAR), null)
    prüfeNomen(alias.name, EnumSet.of(Kasus.NOMINATIV), EnumSet.of(Numerus.SINGULAR))
  }

  private fun prüfeImplementierung(implementierung: AST.Definition.Implementierung) {
    prüfeTyp(implementierung.klasse, EnumSet.of(Kasus.AKKUSATIV), EnumSet.of(Numerus.SINGULAR), null)
    implementierung.schnittstellen.forEach { schnittstelle ->
      prüfeTyp(schnittstelle, EnumSet.of(Kasus.NOMINATIV), Numerus.BEIDE , implementierung.klasse.name as AST.WortArt.Nomen)}
  }

  private fun prüfeKonvertierungsDefinition(konvertierung: AST.Definition.Konvertierung) {
    prüfeTyp(konvertierung.typ, EnumSet.of(Kasus.NOMINATIV), Numerus.BEIDE, null)
  }

  private fun prüfeEigenschaftsDefinition(eigenschaft: AST.Definition.Eigenschaft) {
    prüfeTyp(eigenschaft.rückgabeTyp, EnumSet.of(Kasus.NOMINATIV), Numerus.BEIDE, null)
    prüfeNomen(eigenschaft.name, EnumSet.of(Kasus.NOMINATIV), Numerus.BEIDE)
  }

  private fun prüfeArgument(argument: AST.Argument, fälle: EnumSet<Kasus>) {
    prüfeNomen(argument.name, fälle, Numerus.BEIDE)
    if (argument.adjektiv != null) {
      prüfeAdjektiv(argument.adjektiv, argument.name)
    }
    prüfeKontextbasiertenAusdruck(argument.ausdruck, argument.name, EnumSet.of(Kasus.NOMINATIV), false)
  }

  private fun prüfePräpositionsArgumente(präposition: AST.PräpositionsArgumente) {
    for (argument in präposition.argumente) {
      prüfeArgument(argument, präposition.präposition.fälle)
    }
  }

  private fun prüfeFunktionsAufruf(funktionsAufruf: AST.FunktionsAufruf) {
    if (funktionsAufruf.subjekt != null) {
      prüfeKontextbasiertenAusdruck(funktionsAufruf.subjekt, null, EnumSet.of(Kasus.NOMINATIV), false)
    }
    if (funktionsAufruf.objekt != null) {
      prüfeArgument(funktionsAufruf.objekt, EnumSet.of(Kasus.AKKUSATIV, Kasus.DATIV))
    }
    for (präposition in funktionsAufruf.präpositionsArgumente) {
      prüfePräpositionsArgumente(präposition)
    }
    // logger.addLine("geprüft: $funktionsAufruf")
  }

  private fun prüfeMinus(knoten: AST.Ausdruck.Minus) {
    prüfeKontextbasiertenAusdruck(knoten.ausdruck, null, EnumSet.of(Kasus.AKKUSATIV), false)
  }
}



private val VORNOMEN_TABELLE = mapOf<TokenTyp.VORNOMEN, Array<Array<String>>>(
    TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT to arrayOf(
        arrayOf("der", "die", "das", "die"),
        arrayOf("des", "der", "des", "der"),
        arrayOf("dem", "der", "dem", "den"),
        arrayOf("den", "die", "das", "die")
    ),

    TokenTyp.VORNOMEN.ARTIKEL.UNBESTIMMT to arrayOf(
        arrayOf("ein", "eine", "ein", "einige"),
        arrayOf("eines", "einer", "eines", "einiger"),
        arrayOf("einem", "einer", "einem", "einigen"),
        arrayOf("einen", "eine", "ein", "einige")
    ),

    TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN to arrayOf(
        arrayOf("mein", "meine", "mein", "meine"),
        arrayOf("meines", "meiner", "meines", "meiner"),
        arrayOf("meinem", "meiner", "meinem", "meinen"),
        arrayOf("meinen", "meine", "mein", "meine")
    ),

    TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.DEIN to arrayOf(
        arrayOf("dein", "deine", "dein", "deine"),
        arrayOf("deines", "deiner", "deines", "deiner"),
        arrayOf("deinem", "deiner", "deinem", "deinen"),
        arrayOf("deinen", "deine", "dein", "deine")
    ),

    TokenTyp.VORNOMEN.DEMONSTRATIV_PRONOMEN.DIESE to arrayOf(
        arrayOf("dieser", "diese", "dieses", "diese"),
        arrayOf("dieses", "dieser", "dieses", "dieser"),
        arrayOf("diesem", "dieser", "diesem", "diesen"),
        arrayOf("diesen", "diese", "dieses", "diese")
    ),

    TokenTyp.VORNOMEN.DEMONSTRATIV_PRONOMEN.JENE to arrayOf(
        arrayOf("jener", "jene", "jenes", "jene"),
        arrayOf("jenes", "jener", "jenes", "jener"),
        arrayOf("jenem", "jener", "jenem", "jenen"),
        arrayOf("jenen", "jene", "jenes", "jene")
    )

)

fun main() {
  val grammatikPrüfer = GrammatikPrüfer(File("./iterationen/iter_2/code.gm"))
  grammatikPrüfer.prüfe()
  grammatikPrüfer.logger.print()
}

