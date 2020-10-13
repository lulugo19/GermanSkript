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
        is AST.Satz.ListenElementZuweisung -> prüfeListenElementZuweisung(knoten)
        is AST.Satz.Zurückgabe -> prüfeKontextbasiertenAusdruck(knoten.ausdruck, null, EnumSet.of(Kasus.AKKUSATIV), false)
        is AST.Satz.Fange -> prüfeParameter(knoten.param, EnumSet.of(Kasus.AKKUSATIV))
        is AST.Satz.Werfe -> prüfeWerfe(knoten)
        is AST.Satz.Ausdruck -> prüfeKontextbasiertenAusdruck(knoten, null, EnumSet.of(Kasus.NOMINATIV), false)
      }
      // germanskript.visit everything
      true
    }
  }

  private fun prüfeNomen(nomen: AST.WortArt.Nomen, fälle: EnumSet<Kasus>, numerus: EnumSet<Numerus>) {
    if (nomen.geprüft) {
      return
    }

    val fälle = nomen.überschriebeneFälle ?: fälle

    val bezeichner = nomen.bezeichner.typ
    // Bezeichner mit nur Großbuchstaben sind Symbole
    if (bezeichner.istSymbol) {
      nomen.numera = EnumSet.of(Numerus.SINGULAR)
      nomen.fälle = arrayOf(fälle, fälle)
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
        for (kasus in fälle) {
          val erwarteteForm = deklination.holeForm(kasus, numerus)
          if (bezeichner.hauptWort!! == erwarteteForm) {
            nomen.fälle[numerus.ordinal].add(kasus)
          }
        }
        if (nomen.fälle[numerus.ordinal].isNotEmpty()) {
          nomen.numera.add(numerus)
        }
      }
      if (nomen.numera.isEmpty()) {
        // TODO: berücksichtige auch die möglichen anderen Fälle in der Fehlermeldung
        val kasus = fälle.first()
        val erwarteteForm = bezeichner.ersetzeHauptWort(deklination.holeForm(kasus, numerus.first()), true)
        throw GermanSkriptFehler.GrammatikFehler.FormFehler.FalschesNomen(nomen.bezeichner.toUntyped(), kasus, nomen, erwarteteForm)
      }

    }

    if (nomen.überschriebenerNumerus == null) {
      prüfeVornomen(nomen)
    }
    if (nomen.adjektiv != null) {
      prüfeAdjektiv(nomen.adjektiv!!, nomen)
    }

    if (nomen.deklination?.istNominalisiertesAdjektiv == true) {
      // Sonderfall beachten: 'einige' bei einem nominalisiertem Adjektiv
      val endung = if (nomen.numerus == Numerus.PLURAL && nomen.vornomen?.typ is TokenTyp.VORNOMEN.ARTIKEL.UNBESTIMMT)
        "e"
        else holeAdjektivEndung(nomen)
      nomen.deklination = holeNormalisierteAdjektivDeklination(nomen.hauptWort.removeSuffix(endung) ,nomen)
    }

    if (nomen.überschriebenerNumerus != null) {
      nomen.fälle[nomen.überschriebenerNumerus!!.second.ordinal].add(nomen.überschriebenerNumerus!!.first)
      nomen.numera = EnumSet.of(nomen.überschriebenerNumerus!!.second)
    }
  }

  private fun holeNormalisierteAdjektivDeklination(adjektivOhneEndung: String, nomen: AST.WortArt.Nomen): Deklination {
    val adjektivEndungenSingular = bestimmterArtikelAdjektivEndung[nomen.genus.ordinal]
    val adjektivEndungenPlural = bestimmterArtikelAdjektivEndung[3]
    val singular = adjektivEndungenSingular.map { adjektivOhneEndung + it }.toTypedArray()
    val plural = adjektivEndungenPlural.map { adjektivOhneEndung + it }.toTypedArray()
    return Deklination(nomen.genus, singular, plural)
  }

  private fun holeAdjektivEndung(nomen: AST.WortArt.Nomen): String {
    return when(nomen.vornomen?.typ) {
      null, TokenTyp.VORNOMEN.ETWAS -> keinArtikelEndungen
      is TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT,
      is TokenTyp.VORNOMEN.JEDE,
      is TokenTyp.VORNOMEN.DEMONSTRATIV_PRONOMEN -> bestimmterArtikelAdjektivEndung
      else -> unbestimmterArtikelAdjektivEndung
    }[if (nomen.numerus == Numerus.SINGULAR) nomen.genus.ordinal else 3][nomen.kasus.ordinal]
  }

  private fun prüfeAdjektiv(adjektiv: AST.WortArt.Adjektiv, nomen: AST.WortArt.Nomen?) {
    val endung = if (nomen == null) "" else holeAdjektivEndung(nomen)

    // prüfe die korrekte Endung
    if (!adjektiv.bezeichner.wert.endsWith(endung)) {
      throw GermanSkriptFehler.GrammatikFehler.FalscheAdjektivEndung(adjektiv.bezeichner.toUntyped(), endung)
    }
    // eine Nomenerweiterung ist daran zu erkennen, dass das Adjektiv ein Teil des Kontextnomens ist
    if (nomen !== null && adjektiv.parent === nomen) {
      // Wenn es sich um eine Nomenerweiterung handelt, dann berechnen wir die Deklination selbst
      adjektiv.deklination = holeNormalisierteAdjektivDeklination(adjektiv.bezeichner.wert.removeSuffix(endung), nomen)
    } else {
      adjektiv.normalisierung = adjektiv.bezeichner.wert.capitalize().removeSuffix(endung) + "e"
      adjektiv.deklination = deklinierer.holeDeklination(adjektiv)
    }

    if (nomen == null) {
      adjektiv.numera = EnumSet.of(Numerus.SINGULAR)
      adjektiv.fälle = arrayOf(EnumSet.of(Kasus.NOMINATIV), EnumSet.noneOf(Kasus::class.java))
    } else {
      adjektiv.numera = nomen.numera
      adjektiv.fälle = nomen.fälle
    }
  }

  private fun prüfeVornomen(nomen: AST.WortArt.Nomen)
  {
    if (nomen.vornomen == null || nomen.vornomen!!.typ == TokenTyp.VORNOMEN.ETWAS) {
      if (nomen.vornomen?.typ == TokenTyp.VORNOMEN.ETWAS) {
        nomen.vornomenString = "etwas"
      }
      return
    }
    val vorNomen = nomen.vornomen!!
    val ersterFall = nomen.kasus
    val ersterNumerus = nomen.numerus
    for (numerus in nomen.numera) {
      for (kasus in nomen.fälle[numerus.ordinal]) {
        val erwartetesVornomen = holeVornomen(vorNomen.typ, kasus, nomen.genus, numerus)
        if (vorNomen.wert == erwartetesVornomen) {
          nomen.vornomenString = erwartetesVornomen
        } else {
          nomen.fälle[numerus.ordinal].remove(kasus)
        }
      }
      if (nomen.fälle[numerus.ordinal].isEmpty()) {
        nomen.numera.remove(numerus)
      }
    }

    if (nomen.vornomenString == null) {
      val erwartetesVornomen = holeVornomen(vorNomen.typ, ersterFall, nomen.genus, ersterNumerus)
      // weise hier den ersten Numerus wieder zu, sodass kein Fehler entsteht
      nomen.numera.add(ersterNumerus)
      throw GermanSkriptFehler.GrammatikFehler.FormFehler.FalschesVornomen(vorNomen.toUntyped(), ersterFall, nomen, erwartetesVornomen)
    }
  }

  private fun prüfeNumerus(nomen: AST.WortArt.Nomen, numerus: Numerus) {
    if (nomen.numerus != numerus) {
      val numerusForm = deklinierer.holeDeklination(nomen).holeForm(nomen.kasus, numerus)
      throw GermanSkriptFehler.GrammatikFehler.FalscherNumerus(nomen.bezeichner.toUntyped(), numerus, numerusForm)
    }
  }

  val bestimmterArtikelAdjektivEndung = arrayOf(
      arrayOf("e", "en", "en", "en"),     // Maskulinum
      arrayOf("e", "en", "en", "e"),  // Femininum
      arrayOf("e", "en", "en", "e"),  // Neutrum
      arrayOf("en", "en", "en", "en") // Plural
  )

  val unbestimmterArtikelAdjektivEndung = arrayOf(
     arrayOf("er", "", "", "en"),
     arrayOf("e", "", "", "e"),
     arrayOf("es", "en", "en", "es"),
     arrayOf("en", "en", "en", "en")
  )

  val keinArtikelEndungen = arrayOf(
      arrayOf("er", "en", "em", "en"),
      arrayOf("e", "er", "er", "e"),
      arrayOf("es", "en", "em", "es"),
      arrayOf("e", "er", "en", "e")
  )

  private fun prüfeTyp(typ: AST.TypKnoten, kasus: EnumSet<Kasus>, numerus: EnumSet<Numerus>, kontextNomen: AST.WortArt.Nomen?) {
    when (typ.name) {
      is AST.WortArt.Nomen -> prüfeNomen(typ.name, kasus, numerus)
      is AST.WortArt.Adjektiv -> prüfeAdjektiv(typ.name, kontextNomen)
    }

    prüfeTypArgumente(typ.typArgumente)
  }

  // region kontextbasierte Ausdrücke
  private fun prüfeKontextbasiertenAusdruck(ausdruck: AST.Satz.Ausdruck, kontextNomen: AST.WortArt.Nomen?, fälle: EnumSet<Kasus>, pluralErwartet: Boolean) {
    when (ausdruck) {
      is AST.Satz.Ausdruck.Variable -> prüfeVariable(ausdruck, kontextNomen, fälle, pluralErwartet)
      is AST.Satz.Ausdruck.Liste ->  prüfeListe(ausdruck, kontextNomen, fälle)
      is AST.Satz.Ausdruck.ListenElement -> prüfeListenElement(ausdruck, kontextNomen, fälle, pluralErwartet)
      is AST.Satz.Ausdruck.ObjektInstanziierung -> prüfeObjektinstanziierung(ausdruck, kontextNomen, fälle, pluralErwartet)
      is AST.Satz.Ausdruck.EigenschaftsZugriff -> prüfeEigenschaftsZugriff(ausdruck, kontextNomen, fälle, pluralErwartet)
      is AST.Satz.Ausdruck.MethodenBereichEigenschaftsZugriff -> prüfeNomenKontextBasiert(ausdruck.eigenschaftsName, kontextNomen, fälle, pluralErwartet)
      is AST.Satz.Ausdruck.SelbstEigenschaftsZugriff -> prüfeNomenKontextBasiert(ausdruck.eigenschaftsName, kontextNomen, fälle, pluralErwartet)
      is AST.Satz.Ausdruck.Konvertierung -> prüfeKonvertierung(ausdruck, kontextNomen, fälle, pluralErwartet)
      is AST.Satz.Ausdruck.BinärerAusdruck -> prüfeBinärenAusdruck(ausdruck, kontextNomen, fälle, pluralErwartet)
      is AST.Satz.Ausdruck.Closure -> prüfeClosure(ausdruck, kontextNomen, fälle, pluralErwartet)
      is AST.Satz.Ausdruck.AnonymeKlasse -> prüfeAnonymeKlasse(ausdruck, kontextNomen, fälle, pluralErwartet)
      is AST.Satz.Ausdruck.Minus -> prüfeMinus(ausdruck)
      is AST.Satz.Ausdruck.FunktionsAufruf -> prüfeFunktionsAufruf(ausdruck)
      is AST.Satz.Ausdruck.TypÜberprüfung -> prüfeTypÜberprüfung(ausdruck)
    }
  }

  private fun prüfeVariable(variable: AST.Satz.Ausdruck.Variable, kontextNomen: AST.WortArt.Nomen?, fälle: EnumSet<Kasus>, pluralErwartet: Boolean) {
    val numerus = when {
      kontextNomen != null -> EnumSet.of(kontextNomen.numerus)
      pluralErwartet -> EnumSet.of(Numerus.PLURAL)
      else -> Numerus.BEIDE
    }
    prüfeNomen(variable.name, fälle, numerus)
  }

  private fun prüfeListe(liste: AST.Satz.Ausdruck.Liste, kontextNomen: AST.WortArt.Nomen?, fälle: EnumSet<Kasus>) {
    prüfeTyp(liste.pluralTyp, fälle, EnumSet.of(Numerus.PLURAL), null)
    if (kontextNomen != null) {
      prüfeNumerus(kontextNomen, Numerus.PLURAL)
    }
    liste.elemente.forEach {element -> prüfeKontextbasiertenAusdruck(element, null, EnumSet.of(Kasus.NOMINATIV), false)}
  }

  private fun prüfeObjektinstanziierung(instanziierung: AST.Satz.Ausdruck.ObjektInstanziierung, kontextNomen: AST.WortArt.Nomen?, fälle: EnumSet<Kasus>, pluralErwartet: Boolean) {
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

  private fun prüfeEigenschaftsZugriff(eigenschaftsZugriff: AST.Satz.Ausdruck.EigenschaftsZugriff, kontextNomen: AST.WortArt.Nomen?, fälle: EnumSet<Kasus>, pluralErwartet: Boolean) {
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
      prüfeNumerus(kontextNomen, nomen.numerus)
    }
  }

  private fun prüfeListenElement(listenElement: AST.Satz.Ausdruck.ListenElement, kontextNomen: AST.WortArt.Nomen?, fälle: EnumSet<Kasus>, pluralErwartet: Boolean) {
    if (pluralErwartet) {
      throw GermanSkriptFehler.GrammatikFehler.PluralErwartet(listenElement.singular.bezeichner.toUntyped())
    }
    prüfeNomen(listenElement.singular, fälle, EnumSet.of(Numerus.SINGULAR))
    if (kontextNomen != null) {
      prüfeNumerus(kontextNomen, Numerus.SINGULAR)
    }
    prüfeKontextbasiertenAusdruck(listenElement.index, null, EnumSet.of(Kasus.NOMINATIV), false)
  }

  private fun prüfeClosure(closure: AST.Satz.Ausdruck.Closure, kontextNomen: AST.WortArt.Nomen?, fälle: EnumSet<Kasus>, pluralErwartet: Boolean) {
    if (pluralErwartet) {
      throw GermanSkriptFehler.GrammatikFehler.PluralErwartet(closure.schnittstelle.name.bezeichnerToken)
    }
    prüfeTyp(closure.schnittstelle, fälle, EnumSet.of(Numerus.SINGULAR), null)
    if (kontextNomen != null) {
      prüfeNumerus(kontextNomen, Numerus.SINGULAR)
    }
    closure.bindings.forEach { binding -> prüfeNomen(binding, EnumSet.of(Kasus.NOMINATIV), Numerus.BEIDE)}
  }

  private fun prüfeAnonymeKlasse(anonymeKlasse: AST.Satz.Ausdruck.AnonymeKlasse, kontextNomen: AST.WortArt.Nomen?, fälle: EnumSet<Kasus>, pluralErwartet: Boolean) {
    if (pluralErwartet) {
      throw GermanSkriptFehler.GrammatikFehler.PluralErwartet(anonymeKlasse.schnittstelle.name.bezeichnerToken)
    }
    prüfeTyp(anonymeKlasse.schnittstelle, fälle, EnumSet.of(Numerus.SINGULAR), null)
    if (kontextNomen != null) {
      prüfeNumerus(kontextNomen, Numerus.SINGULAR)
    }
  }

  private fun prüfeKonvertierung(konvertierung: AST.Satz.Ausdruck.Konvertierung, kontextNomen: AST.WortArt.Nomen?, fälle: EnumSet<Kasus>, pluralErwartet: Boolean) {
    prüfeTyp(konvertierung.typ, EnumSet.of(Kasus.NOMINATIV), EnumSet.of(Numerus.SINGULAR), null)
    prüfeKontextbasiertenAusdruck(konvertierung.ausdruck, null, fälle, pluralErwartet)
  }

  // endregion
  private fun prüfeVariablendeklaration(variablenDeklaration: AST.Satz.VariablenDeklaration) {
    val nomen = variablenDeklaration.name
    prüfeNomen(nomen, EnumSet.of(Kasus.NOMINATIV), EnumSet.of(variablenDeklaration.zuweisung.typ.numerus))
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

  private fun prüfeListenElementZuweisung(elementZuweisung: AST.Satz.ListenElementZuweisung) {
    val nomen = elementZuweisung.singular
    prüfeNomen(elementZuweisung.singular, EnumSet.of(Kasus.NOMINATIV), EnumSet.of(Numerus.SINGULAR))
    if (nomen.numerus != elementZuweisung.zuweisung.typ.numerus) {
      throw GermanSkriptFehler.GrammatikFehler.FalscheZuweisung(elementZuweisung.zuweisung.toUntyped(), nomen.numerus)
    }
    prüfeKontextbasiertenAusdruck(elementZuweisung.index, null, EnumSet.of(Kasus.NOMINATIV), false)
    prüfeKontextbasiertenAusdruck(elementZuweisung.wert, nomen, EnumSet.of(Kasus.NOMINATIV), false)
  }

  private fun prüfeTypÜberprüfung(typÜberprüfung: AST.Satz.Ausdruck.TypÜberprüfung) {
    prüfeTyp(typÜberprüfung.typ, EnumSet.of(Kasus.NOMINATIV), EnumSet.of(typÜberprüfung.ist.typ.numerus), null)
    val pluralErwartet = typÜberprüfung.ist.typ.numerus == Numerus.PLURAL
    when (typÜberprüfung.typ.name) {
      is AST.WortArt.Nomen -> {
        prüfeKontextbasiertenAusdruck(typÜberprüfung.ausdruck, typÜberprüfung.typ.name,
            EnumSet.of(Kasus.NOMINATIV), pluralErwartet)
      }
      is AST.WortArt.Adjektiv -> {
        prüfeKontextbasiertenAusdruck(typÜberprüfung.ausdruck, null,
            EnumSet.of(Kasus.NOMINATIV), pluralErwartet)
      }
    }
  }

  private fun prüfeBinärenAusdruck(binärerAusdruck: AST.Satz.Ausdruck.BinärerAusdruck, kontextNomen: AST.WortArt.Nomen?, fälle: EnumSet<Kasus>, pluralErwartet: Boolean) {
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

  private fun prüfeWerfe(werfe: AST.Satz.Werfe) {
    prüfeKontextbasiertenAusdruck(werfe.ausdruck, null, EnumSet.of(Kasus.AKKUSATIV), false)
  }

  private fun prüfeParameter(parameter: AST.Definition.Parameter, fälle: EnumSet<Kasus>) {
    if (!parameter.typIstName) {
      if (parameter.typKnoten.name is AST.WortArt.Adjektiv) {
        prüfeNomen(parameter.name, fälle, Numerus.BEIDE)
      } else {
        prüfeNomen(parameter.name, EnumSet.of(Kasus.NOMINATIV), Numerus.BEIDE)
      }
    }
    prüfeTyp(parameter.typKnoten, fälle, Numerus.BEIDE, parameter.name)
    if (parameter.name.vornomenString == null) {
      val paramName = parameter.name
      paramName.vornomenString = holeVornomen(TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT, parameter.typKnoten.name.kasus, paramName.genus, paramName.numerus)
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

  private fun prüfeTypParameter(typParameter: List<AST.Definition.TypParam>) {
    for (param in typParameter) {
      prüfeNomen(param.binder, EnumSet.of(Kasus.NOMINATIV), EnumSet.of(Numerus.SINGULAR))
      param.schnittstellen.forEach { schnittstelle ->
        prüfeTyp(schnittstelle, EnumSet.of(Kasus.NOMINATIV), EnumSet.of(Numerus.SINGULAR), param.binder)
      }
      if (param.elternKlasse != null) {
        prüfeTyp(param.elternKlasse, EnumSet.of(Kasus.NOMINATIV), EnumSet.of(Numerus.SINGULAR), null)
      }
    }
  }

  private fun prüfeFunktionsSignatur(signatur: AST.Definition.FunktionsSignatur) {
    prüfeTypParameter(signatur.typParameter)
    if (!signatur.hatRückgabeObjekt) {
      // Ansonsten ist der Rückgabetyp ja das Objekt
      prüfeTyp(signatur.rückgabeTyp, EnumSet.of(Kasus.NOMINATIV), Numerus.BEIDE, null)
    }
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
      prüfeObjektinstanziierung(klasse.elternKlasse, null, EnumSet.of(Kasus.NOMINATIV), false)
    }
  }

  private fun prüfeSchnittstelle(schnittstelle: AST.Definition.Typdefinition.Schnittstelle) {
    prüfeTypParameter(schnittstelle.typParameter)
    prüfeAdjektiv(schnittstelle.name, null)
    schnittstelle.methodenSignaturen.forEach(::prüfeFunktionsSignatur)
  }

  private fun prüfeAlias(alias: AST.Definition.Typdefinition.Alias) {
    prüfeTyp(alias.typ, EnumSet.of(Kasus.NOMINATIV), EnumSet.of(Numerus.SINGULAR), null)
    prüfeNomen(alias.name, EnumSet.of(Kasus.NOMINATIV), EnumSet.of(Numerus.SINGULAR))
  }

  private fun prüfeImplementierung(implementierung: AST.Definition.Implementierung) {
    prüfeTyp(implementierung.klasse, EnumSet.of(Kasus.AKKUSATIV), EnumSet.of(Numerus.SINGULAR), null)
    prüfeTypParameter(implementierung.typParameter)
    implementierung.schnittstellen.forEach { schnittstelle ->
      prüfeTyp(schnittstelle, EnumSet.of(Kasus.NOMINATIV), EnumSet.of(Numerus.SINGULAR) , implementierung.klasse.name as AST.WortArt.Nomen)}
  }

  private fun prüfeGenericDefinition(typParameter: AST.Definition.TypParam) {
    prüfeNomen(typParameter.binder, EnumSet.of(Kasus.NOMINATIV), EnumSet.of(Numerus.SINGULAR))
    typParameter.schnittstellen.forEach { schnittstelle ->
      prüfeTyp(schnittstelle, EnumSet.of(Kasus.NOMINATIV), EnumSet.of(Numerus.SINGULAR), typParameter.binder)
    }
    if (typParameter.elternKlasse != null) {
      prüfeTyp(typParameter.elternKlasse, EnumSet.of(Kasus.NOMINATIV), EnumSet.of(Numerus.SINGULAR), null)
    }
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

  private fun prüfeFunktionsAufruf(funktionsAufruf: AST.Satz.Ausdruck.FunktionsAufruf) {
    if (funktionsAufruf.subjekt != null) {
      prüfeKontextbasiertenAusdruck(funktionsAufruf.subjekt, null, EnumSet.of(Kasus.NOMINATIV), false)
    }
    if (funktionsAufruf.objekt != null) {
      prüfeArgument(funktionsAufruf.objekt, EnumSet.of(Kasus.AKKUSATIV, Kasus.DATIV))
    }
    for (präposition in funktionsAufruf.präpositionsArgumente) {
      prüfePräpositionsArgumente(präposition)
    }
    funktionsAufruf.typArgumente.forEach {arg -> prüfeTyp(arg, EnumSet.of(Kasus.NOMINATIV), Numerus.BEIDE, null)}
  }

  private fun prüfeMinus(knoten: AST.Satz.Ausdruck.Minus) {
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
    ),

    TokenTyp.VORNOMEN.JEDE to arrayOf(
        arrayOf("jeden", "jede", "jedes", "jede"),
        arrayOf("jeden", "jede", "jedes", "jede"),
        arrayOf("jeden", "jede", "jedes", "jede"),
        arrayOf("jeden", "jede", "jedes", "jede")
    )

)

fun main() {
  val grammatikPrüfer = GrammatikPrüfer(File("./iterationen/iter_2/code.gm"))
  grammatikPrüfer.prüfe()
  grammatikPrüfer.logger.print()
}

