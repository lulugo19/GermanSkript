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
        is AST.Definition.FunktionOderMethode.Funktion -> prüfeFunktionsSignatur(knoten.signatur)
        is AST.Definition.FunktionOderMethode.Methode -> prüfeMethodenDefinition(knoten)
        is AST.Definition.Konvertierung -> prüfeKonvertierungsDefinition(knoten)
        is AST.Definition.Typdefinition.Klasse -> prüfeKlassenDefinition(knoten)
        is AST.Definition.Typdefinition.Schnittstelle -> knoten.methodenSignaturen.forEach(::prüfeFunktionsSignatur)
        is AST.Satz.VariablenDeklaration -> prüfeVariablendeklaration(knoten)
        is AST.Satz.BedingungsTerm -> prüfeKontextbasiertenAusdruck(knoten.bedingung, null, EnumSet.of(Kasus.NOMINATIV), false)
        is AST.Satz.Zurückgabe -> if (knoten.ausdruck != null)
            prüfeKontextbasiertenAusdruck(knoten.ausdruck!!, null, EnumSet.of(Kasus.AKKUSATIV), false)
        is AST.Satz.FürJedeSchleife -> prüfeFürJedeSchleife(knoten)
        is AST.Satz.FunktionsAufruf -> prüfeFunktionsAufruf(knoten.aufruf)
        is AST.Satz.MethodenBlock -> prüfeNomen(knoten.name, EnumSet.of(Kasus.NOMINATIV), Numerus.BEIDE)
        is AST.Ausdruck, is AST.Nomen, is AST.Definition.FunktionsSignatur -> return@visit false
      }
      // germanskript.visit everything
      true
    }
  }

  private fun prüfeNomen(nomen: AST.Nomen, fälle: EnumSet<Kasus>, numerus: EnumSet<Numerus>) {
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
            deklination.getForm(fälle.first(), numerus.first())
        )
      }
      nomen.numerus = deklinationsNumerus.first()
      nomen.deklination = deklination
      for (kasus in fälle) {
        val erwarteteForm = deklination.getForm(kasus, nomen.numerus!!)
        if (bezeichner.hauptWort!! == erwarteteForm) {
          nomen.fälle.add(kasus)
        }
      }
      if (nomen.fälle.isEmpty()) {
        // TODO: berücksichtige auch die möglichen anderen Fälle in der Fehlermeldung
        val kasus = fälle.first()
        val erwarteteForm = bezeichner.ersetzeHauptWort(deklination.getForm(kasus, nomen.numerus!!))
        throw GermanSkriptFehler.GrammatikFehler.FormFehler.FalschesNomen(nomen.bezeichner.toUntyped(), kasus, nomen, erwarteteForm)
      }
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

  private fun prüfeNumerus(nomen: AST.Nomen, numerus: Numerus) {
    if (nomen.numerus!! != numerus) {
      val numerusForm = deklinierer.holeDeklination(nomen).getForm(nomen.fälle.first(), numerus)
      throw GermanSkriptFehler.GrammatikFehler.FalscherNumerus(nomen.bezeichner.toUntyped(), numerus, numerusForm)
    }
  }

  val bestimmterArtikelAdjektivEndung = arrayOf(
      arrayOf("e", "", "", "en"),
      arrayOf("e", "en", "en", "e"),
      arrayOf("e", "en", "en", "e"),
      arrayOf("en", "", "", "en")
  )

  val unbestimmterArtikelAdjektivEndung = arrayOf(
     arrayOf("er", "", "", "en"),
     arrayOf("e", "", "", "e"),
     arrayOf("es", "en", "en", "es"),
     arrayOf("en", "", "", "en")
  )

  private fun prüfeAdjektiv(adjektiv: AST.Adjektiv, name: AST.Nomen) {
    val endung = when(name.vornomen?.typ) {
      is TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT -> bestimmterArtikelAdjektivEndung
      else -> unbestimmterArtikelAdjektivEndung
    }[if (name.numerus!! == Numerus.SINGULAR) name.genus.ordinal else 3][name.fälle.first().ordinal]

    // prüfe die korrekte Endung
    if (!adjektiv.bezeichner.wert.endsWith(endung)) {
      throw GermanSkriptFehler.GrammatikFehler.FalscheAdjektivEndung(adjektiv.bezeichner.toUntyped(), endung)
    }
    // wandel das Adejktiv Token, in ein Nomen Token um, sodass es vom Deklinierer gefunden werden kann
    val adjektivToken = adjektiv.bezeichner
    val adjektivGroß = adjektiv.bezeichner.wert.capitalize()
    val nomen = AST.Nomen(
        name.vornomen,
        TypedToken(TokenTyp.BEZEICHNER_GROSS(arrayOf(adjektivGroß), ""),
            adjektivGroß, adjektivToken.dateiPfad, adjektivToken.anfang, adjektivToken.ende)
    )
    nomen.setParentNode(adjektiv.parent!!)
    adjektiv.normalisierung = deklinierer.holeDeklination(nomen).getForm(name.fälle.first(), name.numerus!!)
  }

  // region kontextbasierte Ausdrücke
  private fun prüfeKontextbasiertenAusdruck(ausdruck: AST.Ausdruck, kontextNomen: AST.Nomen?, fälle: EnumSet<Kasus>, pluralErwartet: Boolean) {
    when (ausdruck) {
      is AST.Ausdruck.Variable -> prüfeVariable(ausdruck, kontextNomen, fälle, pluralErwartet)
      is AST.Ausdruck.Liste ->  prüfeListe(ausdruck, kontextNomen, fälle)
      is AST.Ausdruck.ListenElement -> prüfeListenElement(ausdruck, kontextNomen, fälle, pluralErwartet)
      is AST.Ausdruck.ObjektInstanziierung -> prüfeObjektinstanziierung(ausdruck, kontextNomen, fälle, pluralErwartet)
      is AST.Ausdruck.EigenschaftsZugriff -> prüfeEigenschaftsZugriff(ausdruck, kontextNomen, fälle, pluralErwartet)
      is AST.Ausdruck.MethodenBlockEigenschaftsZugriff -> prüfeNomenKontextBasiert(ausdruck.eigenschaftsName, kontextNomen, fälle, pluralErwartet)
      is AST.Ausdruck.SelbstEigenschaftsZugriff -> prüfeNomenKontextBasiert(ausdruck.eigenschaftsName, kontextNomen, fälle, pluralErwartet)
      is AST.Ausdruck.Konvertierung -> prüfeKonvertierung(ausdruck, kontextNomen, fälle, pluralErwartet)
      is AST.Ausdruck.BinärerAusdruck -> prüfeBinärenAusdruck(ausdruck, kontextNomen, fälle, pluralErwartet)
      is AST.Ausdruck.FunktionsAufruf -> prüfeFunktionsAufruf(ausdruck.aufruf)
      is AST.Ausdruck.Minus -> prüfeMinus(ausdruck)
    }
  }

  private fun prüfeVariable(variable: AST.Ausdruck.Variable, kontextNomen: AST.Nomen?, fälle: EnumSet<Kasus>, pluralErwartet: Boolean) {
    val numerus = when {
      kontextNomen != null -> EnumSet.of(kontextNomen.numerus)
      pluralErwartet -> EnumSet.of(Numerus.PLURAL)
      else -> Numerus.BEIDE
    }
    prüfeNomen(variable.name, fälle, numerus)
  }

  private fun prüfeListe(liste: AST.Ausdruck.Liste, kontextNomen: AST.Nomen?, fälle: EnumSet<Kasus>) {
    prüfeNomen(liste.pluralTyp, fälle, EnumSet.of(Numerus.PLURAL))
    if (kontextNomen != null) {
      prüfeNumerus(kontextNomen, Numerus.PLURAL)
    }
    liste.elemente.forEach {element -> prüfeKontextbasiertenAusdruck(element, null, EnumSet.of(Kasus.NOMINATIV), false)}
  }

  private fun prüfeObjektinstanziierung(instanziierung: AST.Ausdruck.ObjektInstanziierung, kontextNomen: AST.Nomen?, fälle: EnumSet<Kasus>, pluralErwartet: Boolean) {
    if (pluralErwartet) {
      throw GermanSkriptFehler.GrammatikFehler.PluralErwartet(instanziierung.klasse.name.bezeichner.toUntyped())
    }
    prüfeNomen(instanziierung.klasse.name, fälle, EnumSet.of(Numerus.SINGULAR))
    if (kontextNomen != null) {
      prüfeNumerus(kontextNomen, Numerus.SINGULAR)
    }
    for (eigenschaftsZuweisung in instanziierung.eigenschaftsZuweisungen) {
      prüfeNomen(eigenschaftsZuweisung.name, EnumSet.of(Kasus.DATIV), Numerus.BEIDE)
      prüfeKontextbasiertenAusdruck(eigenschaftsZuweisung.wert, eigenschaftsZuweisung.name, EnumSet.of(Kasus.NOMINATIV), false)
    }
  }

  private fun prüfeEigenschaftsZugriff(eigenschaftsZugriff: AST.Ausdruck.EigenschaftsZugriff, kontextNomen: AST.Nomen?, fälle: EnumSet<Kasus>, pluralErwartet: Boolean) {
    prüfeNomenKontextBasiert(eigenschaftsZugriff.eigenschaftsName, kontextNomen, fälle, pluralErwartet)
    prüfeKontextbasiertenAusdruck(eigenschaftsZugriff.objekt, null, EnumSet.of(Kasus.GENITIV), false)
  }

  private fun prüfeNomenKontextBasiert(
      nomen: AST.Nomen,
      kontextNomen: AST.Nomen?,
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

  private fun prüfeListenElement(listenElement: AST.Ausdruck.ListenElement, kontextNomen: AST.Nomen?, fälle: EnumSet<Kasus>, pluralErwartet: Boolean) {
    if (pluralErwartet) {
      throw GermanSkriptFehler.GrammatikFehler.PluralErwartet(listenElement.singular.bezeichner.toUntyped())
    }
    prüfeNomen(listenElement.singular, fälle, EnumSet.of(Numerus.SINGULAR))
    if (kontextNomen != null) {
      prüfeNumerus(kontextNomen, Numerus.SINGULAR)
    }
    prüfeKontextbasiertenAusdruck(listenElement.index, null, EnumSet.of(Kasus.NOMINATIV), false)
  }

  private fun prüfeKonvertierung(konvertierung: AST.Ausdruck.Konvertierung, kontextNomen: AST.Nomen?, fälle: EnumSet<Kasus>, pluralErwartet: Boolean) {
    prüfeNomen(konvertierung.typ.name, EnumSet.of(Kasus.NOMINATIV), EnumSet.of(Numerus.SINGULAR))
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
    prüfeKontextbasiertenAusdruck(variablenDeklaration.ausdruck, nomen, EnumSet.of(Kasus.NOMINATIV), false)
  }

  private fun prüfeBinärenAusdruck(binärerAusdruck: AST.Ausdruck.BinärerAusdruck, kontextNomen: AST.Nomen?, fälle: EnumSet<Kasus>, pluralErwartet: Boolean) {
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
  }


  private fun prüfeParameter(parameter: AST.Definition.TypUndName, fälle: EnumSet<Kasus>) {
    val nomen = parameter.typKnoten.name
    prüfeNomen(nomen, fälle, Numerus.BEIDE)
    prüfeNomen(parameter.name, EnumSet.of(Kasus.NOMINATIV), Numerus.BEIDE)
    if (parameter.name.vornomenString == null) {
      val paramName = parameter.name
      paramName.vornomenString = holeVornomen(TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT, nomen.fälle.first(), paramName.genus, paramName.numerus!!)
    }
  }

  private fun prüfePräpositionsParameter(präposition: AST.Definition.PräpositionsParameter) {
    for (parameter in präposition.parameter) {
      prüfeParameter(parameter, präposition.präposition.fälle)
    }
  }

  private fun prüfeFunktionsSignatur(signatur: AST.Definition.FunktionsSignatur) {
    if (signatur.rückgabeTyp != null) {
      prüfeNomen(signatur.rückgabeTyp.name, EnumSet.of(Kasus.NOMINATIV), Numerus.BEIDE)
    }
    if (signatur.objekt != null) {
      prüfeParameter(signatur.objekt, EnumSet.of(Kasus.DATIV, Kasus.AKKUSATIV))
    }
    for (präposition in signatur.präpositionsParameter) {
      prüfePräpositionsParameter(präposition)
    }
  }

  private fun prüfeMethodenDefinition(methodenDefinition: AST.Definition.FunktionOderMethode.Methode){
    prüfeNomen(methodenDefinition.klasse.name, EnumSet.of(Kasus.NOMINATIV), EnumSet.of(Numerus.SINGULAR))
    prüfeFunktionsSignatur(methodenDefinition.funktion.signatur)
  }

  private fun prüfeKlassenDefinition(klasse: AST.Definition.Typdefinition.Klasse) {
    prüfeNomen(klasse.typ.name, EnumSet.of(Kasus.NOMINATIV), EnumSet.of(Numerus.SINGULAR))

    for (eigenschaft in klasse.eigenschaften) {
      prüfeNomen(eigenschaft.typKnoten.name, EnumSet.of(Kasus.DATIV), Numerus.BEIDE)
      prüfeNomen(eigenschaft.name, EnumSet.of(Kasus.NOMINATIV), Numerus.BEIDE)
    }
  }

  private fun prüfeKonvertierungsDefinition(konvertierung: AST.Definition.Konvertierung) {
    prüfeNomen(konvertierung.typ.name, EnumSet.of(Kasus.NOMINATIV), EnumSet.of(Numerus.SINGULAR))
    prüfeNomen(konvertierung.klasse.name, EnumSet.of(Kasus.NOMINATIV), EnumSet.of(Numerus.SINGULAR))
  }


  private fun prüfeArgument(argument: AST.Argument, fälle: EnumSet<Kasus>) {
    prüfeNomen(argument.name, fälle, Numerus.BEIDE)
    if (argument.adjektiv != null) {
      prüfeAdjektiv(argument.adjektiv, argument.name)
    }
    prüfeKontextbasiertenAusdruck(argument.wert, argument.name, EnumSet.of(Kasus.NOMINATIV), false)
  }

  private fun prüfePräpositionsArgumente(präposition: AST.PräpositionsArgumente) {
    for (argument in präposition.argumente) {
      prüfeArgument(argument, präposition.präposition.fälle)
    }
  }

  private fun prüfeFunktionsAufruf(funktionsAufruf: AST.Funktion) {
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

