package germanskript

import java.io.File
import kotlin.collections.HashMap

class CodeGenerator(startDatei: File): PipelineKomponente(startDatei) {
  val typPrüfer = TypPrüfer(startDatei)
  val ast = typPrüfer.ast

  val klassen = mutableMapOf<AST.Definition.Typdefinition.Klasse, IM_AST.Definition.Klasse>()
  val funktionen = mutableMapOf<AST.Definition.Funktion, IM_AST.Definition.Funktion>()

  companion object {
    const val SELBST_VAR_NAME = "_Selbst"
    const val METHODEN_BEREICH_VAR_NAME = "_MBObjekt"
    const val ITERATOR_VAR_NAME= "_Iterator"
    const val INIT_OBJEKT_VAR_NAME = "_Init_Objekt"
    const val LISTE_VAR_NAME = "_Liste"
  }

  fun generiere(): IM_AST.Satz.Ausdruck.FunktionsAufruf {
    typPrüfer.prüfe()

    val definierer = typPrüfer.definierer
    definierer.funktionsDefinitionen.forEach {funktionsDef ->
      funktionen[funktionsDef] = generiereFunktion(funktionsDef)
    }
    definierer.holeDefinitionen<AST.Definition.Typdefinition.Klasse>().forEach { klassenDef ->
      klassen[klassenDef] = generiereKlasse(klassenDef, false)
    }

    // TODO: erstelle nur die Körper, die in dem Programm auch wirklich benutzt werden
    // generiere den Funktionskörper für jede Funktion
    for ((funktionsDef, funktion) in funktionen) {
      funktion.körper = generiereBereich(funktionsDef.körper)
    }

    for ((klassenDef, klasse) in klassen) {
      for ((name, methode) in klassenDef.methoden)
        klasse.methoden.getValue(name).körper = generiereBereich(methode.körper)

      for((name, konvertierung) in klassenDef.konvertierungen) {
        klasse.methoden.getValue("als $name").körper = generiereBereich(konvertierung.definition)
      }

      for ((name, eigenschaft) in klassenDef.berechneteEigenschaften) {
        klasse.methoden.getValue("$name von ${klassenDef.namensToken.wert}").körper = generiereBereich(eigenschaft.definition)
      }

      klasse.methoden.getValue("erstelle ${klasse.name}").körper = generiereBereich(klassenDef.konstruktor)
    }

    holeBuildInTypDefinitionen()

    // TODO: Füge Kommandozeilen-Argumente hinzu
    val funktionsDefinition = IM_AST.Definition.Funktion(emptyList())
    funktionsDefinition.körper = generiereBereich(ast.programm)
    return IM_AST.Satz.Ausdruck.FunktionsAufruf("starte das Programm", ast.token, emptyList(), funktionsDefinition)
  }

  private fun holeBuildInTypDefinitionen() {
    BuildIn.IMMKlassen.objekt = klassen.getValue(BuildIn.Klassen.objekt.definition)
    BuildIn.IMMKlassen.nichts = klassen.getValue(BuildIn.Klassen.nichts.definition)
    BuildIn.IMMKlassen.niemals = klassen.getValue(BuildIn.Klassen.niemals.definition)
    BuildIn.IMMKlassen.zahl = klassen.getValue(BuildIn.Klassen.zahl.definition)
    BuildIn.IMMKlassen.zeichenfolge = klassen.getValue(BuildIn.Klassen.zeichenfolge.definition)
    BuildIn.IMMKlassen.boolean = klassen.getValue(BuildIn.Klassen.boolean.definition)
    BuildIn.IMMKlassen.datei = klassen.getValue(BuildIn.Klassen.datei.definition)
    BuildIn.IMMKlassen.hashMap = klassen.getValue(BuildIn.Klassen.hashMap)
    BuildIn.IMMKlassen.hashSet = klassen.getValue(BuildIn.Klassen.hashSet)
    BuildIn.IMMKlassen.schreiber = klassen.getValue(BuildIn.Klassen.schreiber.definition)
    BuildIn.IMMKlassen.liste = klassen.getValue(BuildIn.Klassen.liste)
    BuildIn.IMMKlassen.paar = klassen.getValue(BuildIn.Klassen.paar)
  }

  private fun generiereFunktion(funktion: AST.Definition.Funktion): IM_AST.Definition.Funktion {
    return IM_AST.Definition.Funktion(funktion.signatur.parameterNamen.map { it.nominativ })
  }

  private fun generiereKlasse(klasse: AST.Definition.Typdefinition.Klasse, generiereMethodenKörper: Boolean): IM_AST.Definition.Klasse {
    val methoden = HashMap<String, IM_AST.Definition.Funktion>()
      for ((name, methode) in klasse.methoden) {
        val funktion = generiereFunktion(methode)
        if (generiereMethodenKörper) funktion.körper = generiereBereich(methode.körper)
        methoden[name] = funktion
      }

      // füge Konvertierungen als Methode hinzu
      for((name, konvertierung) in klasse.konvertierungen) {
        val funktion = IM_AST.Definition.Funktion(emptyList())
        if (generiereMethodenKörper) funktion.körper = generiereBereich(konvertierung.definition)
        methoden["als $name"] = funktion
      }

      // füge berechnete Eigenschaften als Methode hinzu
      for ((name, eigenschaft) in klasse.berechneteEigenschaften) {
        val funktion = IM_AST.Definition.Funktion(emptyList())
        methoden["$name von ${klasse.namensToken.wert}"] = funktion
        if (generiereMethodenKörper) funktion.körper = generiereBereich(eigenschaft.definition)
      }

      // Füge Konstruktor als Methode hinzu
      val konstruktor = IM_AST.Definition.Funktion(emptyList())
      if (generiereMethodenKörper) konstruktor.körper = generiereBereich(klasse.konstruktor)
      methoden["erstelle " + klasse.name.nominativ] = konstruktor

      return IM_AST.Definition.Klasse(klasse.name.nominativ, methoden)
    }

  // region Sätze
  private fun generiereSatz(satz: AST.Satz): IM_AST.Satz {
    return when (satz) {
      is AST.Satz.Intern -> IM_AST.Satz.Intern
      AST.Satz.SchleifenKontrolle.Fortfahren -> IM_AST.Satz.Fortfahren
      AST.Satz.SchleifenKontrolle.Abbrechen -> IM_AST.Satz.Abbrechen
      is AST.Satz.VariablenDeklaration -> generiereVariablenDeklaration(satz)
      is AST.Satz.IndexZuweisung -> generiereIndexZuweisung(satz)
      is AST.Satz.Bereich -> generiereBereich(satz)
      is AST.Satz.SolangeSchleife -> generiereSolangeSchleife(satz)
      is AST.Satz.FürJedeSchleife -> generiereFürJedeSchleife(satz)
      is AST.Satz.SuperBereich -> generiereBereich(satz.bereich)
      is AST.Satz.Zurückgabe -> generiereZurückgabe(satz)
      is AST.Satz.Ausdruck -> generiereAusdruck(satz)
    }
  }

  private fun generiereBereich(bereich: AST.Satz.Bereich): IM_AST.Satz.Ausdruck.Bereich {
    val sätze = bereich.sätze.map(::generiereSatz)
    return IM_AST.Satz.Ausdruck.Bereich(sätze)
  }

  private fun generiereVariablenDeklaration(deklaration: AST.Satz.VariablenDeklaration): IM_AST.Satz {
    val wert = generiereAusdruck(deklaration.wert)
    return if (deklaration.istEigenschaftsNeuZuweisung || deklaration.istEigenschaft) {
      IM_AST.Satz.SetzeEigenschaft(IM_AST.Satz.Ausdruck.Variable(SELBST_VAR_NAME), deklaration.name.nominativ, wert)
    }
    else {
      val überschreibe = !deklaration.name.unveränderlich && deklaration.neu == null
      IM_AST.Satz.VariablenDeklaration(deklaration.name.nominativ, wert, überschreibe)
    }
  }

  private fun generiereIndexZuweisung(indexZuweisung: AST.Satz.IndexZuweisung): IM_AST.Satz {
    val objekt = IM_AST.Satz.Ausdruck.Variable(indexZuweisung.singular.ganzesWort(Kasus.NOMINATIV, indexZuweisung.numerus, true))
    val argumente = listOf(generiereAusdruck(indexZuweisung.index), generiereAusdruck(indexZuweisung.wert))
    return IM_AST.Satz.Ausdruck.MethodenAufruf(
        "setze den Index auf den Typ",
        indexZuweisung.zuweisung.toUntyped(),
        argumente,
        objekt,
        null // dynamic Dispatching
    )
  }

  private fun generiereZurückgabe(satz: AST.Satz.Zurückgabe): IM_AST.Satz {
    return IM_AST.Satz.Zurückgabe(generiereAusdruck(satz.ausdruck))
  }

  private fun generiereFürJedeSchleife(schleife: AST.Satz.FürJedeSchleife): IM_AST.Satz {

    val iterierbar = when {
      schleife.iterierbares != null -> generiereAusdruck(schleife.iterierbares)
      schleife.reichweite != null -> IM_AST.Satz.Ausdruck.Bereich(
          listOf(
              IM_AST.Satz.VariablenDeklaration(INIT_OBJEKT_VAR_NAME, IM_AST.Satz.Ausdruck.ObjektInstanziierung(
                  BuildIn.Klassen.reichweite,
                  klassen.getValue(BuildIn.Klassen.reichweite.definition),
                  IM_AST.Satz.Ausdruck.ObjektArt.Klasse), false),
              IM_AST.Satz.SetzeEigenschaft(IM_AST.Satz.Ausdruck.Variable(INIT_OBJEKT_VAR_NAME), "Start", generiereAusdruck(schleife.reichweite.anfang)),
              IM_AST.Satz.SetzeEigenschaft(IM_AST.Satz.Ausdruck.Variable(INIT_OBJEKT_VAR_NAME), "Ende", generiereAusdruck(schleife.reichweite.ende)),
              IM_AST.Satz.Ausdruck.Variable(INIT_OBJEKT_VAR_NAME)
          )
      )
      else -> IM_AST.Satz.Ausdruck.Variable(schleife.singular.ganzesWort(Kasus.NOMINATIV, Numerus.PLURAL, true))
    }

    val holeIterator = IM_AST.Satz.Ausdruck.MethodenAufruf(
        "hole den Iterator", schleife.für.toUntyped(), emptyList(), iterierbar, null
    )

    val iteratorDeklaration = IM_AST.Satz.VariablenDeklaration(ITERATOR_VAR_NAME, holeIterator, false)

    val bedingung = IM_AST.Satz.Ausdruck.MethodenAufruf(
        "läuft weiter", schleife.für.toUntyped(), emptyList(), IM_AST.Satz.Ausdruck.Variable(ITERATOR_VAR_NAME), null
    )

    val holeIteration = IM_AST.Satz.Ausdruck.MethodenAufruf(
        "hole den Typ", schleife.für.toUntyped(), emptyList(), IM_AST.Satz.Ausdruck.Variable(ITERATOR_VAR_NAME), null
    )

    val binderDeklaration = IM_AST.Satz.VariablenDeklaration(schleife.binder.nominativ, holeIteration, false)

    val sätze = schleife.bereich.sätze.map(::generiereSatz).toMutableList()
    sätze.add(0, binderDeklaration)

    val bereich = IM_AST.Satz.Ausdruck.Bereich(sätze)

    val solangeSchleife = IM_AST.Satz.SolangeSchleife(IM_AST.Satz.BedingungsTerm(bedingung, bereich))

    return IM_AST.Satz.Ausdruck.Bereich(listOf(
        iteratorDeklaration,
        solangeSchleife
    ))
  }

  private fun generiereBedingungsTerm(bedingungsTerm: AST.Satz.BedingungsTerm): IM_AST.Satz.BedingungsTerm {
    return IM_AST.Satz.BedingungsTerm(
        generiereAusdruck(bedingungsTerm.bedingung),
        generiereBereich(bedingungsTerm.bereich)
    )
  }

  private fun generiereSolangeSchleife(schleife: AST.Satz.SolangeSchleife): IM_AST.Satz {
    return IM_AST.Satz.SolangeSchleife(generiereBedingungsTerm(schleife.bedingung))
  }
  // endregion

  // region Ausdrücke
  private fun generiereAusdruck(ausdruck: AST.Satz.Ausdruck): IM_AST.Satz.Ausdruck {
    return when (ausdruck) {
      is AST.Satz.Ausdruck.Nichts -> IM_AST.Satz.Ausdruck.Konstante.Nichts
      is AST.Satz.Ausdruck.Zeichenfolge -> IM_AST.Satz.Ausdruck.Konstante.Zeichenfolge(ausdruck.zeichenfolge.typ.zeichenfolge)
      is AST.Satz.Ausdruck.Zahl -> IM_AST.Satz.Ausdruck.Konstante.Zahl(ausdruck.zahl.typ.zahl)
      is AST.Satz.Ausdruck.Boolean -> IM_AST.Satz.Ausdruck.Konstante.Boolean(ausdruck.boolean.typ.boolean)
      is AST.Satz.Ausdruck.Variable -> generiereVariable(ausdruck)
      is AST.Satz.Ausdruck.FunktionsAufruf -> generiereFunktionsAufruf(ausdruck)
      is AST.Satz.Ausdruck.MethodenBereich -> generiereMethodenBereich(ausdruck)
      is AST.Satz.Ausdruck.Konstante -> generiereKonstante(ausdruck)
      is AST.Satz.Ausdruck.Liste -> generiereListe(ausdruck)
      is AST.Satz.Ausdruck.IndexZugriff -> generiereIndexZugriff(ausdruck)
      is AST.Satz.Ausdruck.BinärerAusdruck -> generiereBinärenAusdruck(ausdruck)
      is AST.Satz.Ausdruck.Minus -> generiereMinus(ausdruck)
      is AST.Satz.Ausdruck.Konvertierung -> generiereKonvertierung(ausdruck)
      is AST.Satz.Ausdruck.ObjektInstanziierung -> generiereObjektInstanziierung(ausdruck)
      is AST.Satz.Ausdruck.Lambda -> generiereLambda(ausdruck)
      is AST.Satz.Ausdruck.AnonymeKlasse -> generiereAnonymeKlasse(ausdruck)
      is AST.Satz.Ausdruck.Bedingung -> generiereBedingung(ausdruck)
      is AST.Satz.Ausdruck.TypÜberprüfung -> generiereTypÜberprüfung(ausdruck)
      is AST.Satz.Ausdruck.EigenschaftsZugriff ->
        generiereEigenschaftsZugriff(ausdruck, generiereAusdruck(ausdruck.objekt))
      is AST.Satz.Ausdruck.MethodenBereichEigenschaftsZugriff ->
        generiereEigenschaftsZugriff(ausdruck, IM_AST.Satz.Ausdruck.Variable(METHODEN_BEREICH_VAR_NAME))
      is AST.Satz.Ausdruck.SelbstEigenschaftsZugriff ->
        generiereEigenschaftsZugriff(ausdruck, IM_AST.Satz.Ausdruck.Variable(SELBST_VAR_NAME))
      is AST.Satz.Ausdruck.SelbstReferenz -> IM_AST.Satz.Ausdruck.Variable(SELBST_VAR_NAME)
      is AST.Satz.Ausdruck.MethodenBereichReferenz -> IM_AST.Satz.Ausdruck.Variable(METHODEN_BEREICH_VAR_NAME)
      is AST.Satz.Ausdruck.VersucheFange -> generiereVersucheFange(ausdruck)
      is AST.Satz.Ausdruck.Werfe -> generiereWerfe(ausdruck)
    }
  }

  private fun generiereVariable(variable: AST.Satz.Ausdruck.Variable): IM_AST.Satz.Ausdruck {
    return if (variable.konstante != null) {
      generiereAusdruck(variable.konstante!!.wert!!)
    } else {
      IM_AST.Satz.Ausdruck.Variable(variable.name.nominativ)
    }
  }

  private fun generiereFunktionsAufruf(aufruf: AST.Satz.Ausdruck.FunktionsAufruf): IM_AST.Satz.Ausdruck {
    val argumente = aufruf.argumente.map { generiereAusdruck(it.ausdruck) }.toList()

    return if (aufruf.aufrufTyp == FunktionsAufrufTyp.FUNKTIONS_AUFRUF) {
      IM_AST.Satz.Ausdruck.FunktionsAufruf(
          aufruf.vollerName!!,
          aufruf.token,
          argumente, funktionen.getValue(aufruf.funktionsDefinition!!)
      )
    } else {
      val objekt = when(aufruf.aufrufTyp) {
        FunktionsAufrufTyp.METHODEN_SELBST_AUFRUF -> IM_AST.Satz.Ausdruck.Variable(SELBST_VAR_NAME)
        FunktionsAufrufTyp.METHODEN_BEREICHS_AUFRUF -> IM_AST.Satz.Ausdruck.Variable(METHODEN_BEREICH_VAR_NAME)
        FunktionsAufrufTyp.METHODEN_REFLEXIV_AUFRUF -> generiereAusdruck(aufruf.objekt!!.ausdruck)
        FunktionsAufrufTyp.METHODEN_SUBJEKT_AUFRUF -> generiereAusdruck(aufruf.subjekt!!)
        else -> throw Exception("Dieser Fall sollte nie auftreten!")
      }

      val funktionsDefinition = if (aufruf.funktionsDefinition != null)
        klassen.getValue(aufruf.objektTyp!!.definition).methoden[aufruf.vollerName!!] else null

      IM_AST.Satz.Ausdruck.MethodenAufruf(
          aufruf.vollerName!!,
          aufruf.token,
          argumente,
          objekt,
          funktionsDefinition
      )
    }
  }

  private fun generiereMethodenBereich(methodenBereich: AST.Satz.Ausdruck.MethodenBereich): IM_AST.Satz.Ausdruck {
    val deklaration = IM_AST.Satz.VariablenDeklaration(METHODEN_BEREICH_VAR_NAME, generiereAusdruck(methodenBereich.objekt), false)
    val bereich = generiereBereich(methodenBereich.bereich)
    val sätze = bereich.sätze.toMutableList()
    sätze.add(0, deklaration)
    return IM_AST.Satz.Ausdruck.Bereich(sätze)
  }

  private fun generiereKonstante(konstante: AST.Satz.Ausdruck.Konstante): IM_AST.Satz.Ausdruck {
    return generiereAusdruck(konstante.wert!!)
  }

  private fun generiereListe(liste: AST.Satz.Ausdruck.Liste): IM_AST.Satz.Ausdruck {
    val sätze = mutableListOf<IM_AST.Satz>()
    val erstelleListe = IM_AST.Satz.Ausdruck.ObjektInstanziierung(
        Typ.Compound.Klasse(BuildIn.Klassen.liste, listOf(liste.pluralTyp)),
        klassen.getValue(BuildIn.Klassen.liste),
        IM_AST.Satz.Ausdruck.ObjektArt.Klasse
    )
    sätze.add(IM_AST.Satz.VariablenDeklaration(LISTE_VAR_NAME, erstelleListe, false))
    for (element in liste.elemente) {
      sätze.add(IM_AST.Satz.Ausdruck.MethodenAufruf(
          "füge das Element hinzu",
          liste.holeErstesToken(),
          listOf(generiereAusdruck(element)),
          IM_AST.Satz.Ausdruck.Variable(LISTE_VAR_NAME),
          null
      ))
    }
    sätze.add(IM_AST.Satz.Ausdruck.Variable(LISTE_VAR_NAME))
    return IM_AST.Satz.Ausdruck.Bereich(sätze)
  }

  private fun generiereIndexZugriff(indexZugriff: AST.Satz.Ausdruck.IndexZugriff): IM_AST.Satz.Ausdruck {
      return IM_AST.Satz.Ausdruck.MethodenAufruf(
          "hole den Typ mit dem Index",
          indexZugriff.holeErstesToken(),
          listOf(generiereAusdruck(indexZugriff.index)),
          IM_AST.Satz.Ausdruck.Variable(indexZugriff.singular.ganzesWort(Kasus.NOMINATIV, indexZugriff.numerus, true)),
          null
      )
  }

  private fun generiereBinärenAusdruck(ausdruck: AST.Satz.Ausdruck.BinärerAusdruck): IM_AST.Satz.Ausdruck {
    val operator = ausdruck.operator.typ.operator
    val links = generiereAusdruck(ausdruck.links)
    val rechts = generiereAusdruck(ausdruck.rechts)

    return if (operator.klasse == OperatorKlasse.LOGISCH) {
      when (operator) {
        Operator.ODER -> IM_AST.Satz.Ausdruck.LogischesOder(links, rechts)
        Operator.UND -> IM_AST.Satz.Ausdruck.LogischesUnd(links, rechts)
        else -> throw Exception("Dieser Fall sollte nie eintreten!")
      }
    } else {
      val methodenAufruf = IM_AST.Satz.Ausdruck.MethodenAufruf(operator.methodenName!!, ausdruck.operator.toUntyped(), listOf(rechts), links, null)
      when (operator) {
        Operator.GLEICH -> methodenAufruf
        Operator.UNGLEICH -> IM_AST.Satz.Ausdruck.LogischesNicht(methodenAufruf)
        Operator.GRÖßER -> IM_AST.Satz.Ausdruck.Vergleich(methodenAufruf, IM_AST.Satz.Ausdruck.VergleichsOperator.GRÖSSER)
        Operator.KLEINER -> IM_AST.Satz.Ausdruck.Vergleich(methodenAufruf, IM_AST.Satz.Ausdruck.VergleichsOperator.KLEINER)
        Operator.GRÖSSER_GLEICH -> IM_AST.Satz.Ausdruck.Vergleich(methodenAufruf, IM_AST.Satz.Ausdruck.VergleichsOperator.GRÖSSER_GLEICH)
        Operator.KLEINER_GLEICH -> IM_AST.Satz.Ausdruck.Vergleich(methodenAufruf, IM_AST.Satz.Ausdruck.VergleichsOperator.KLEINER_GLEICH)
        Operator.PLUS,
        Operator.MINUS,
        Operator.MAL,
        Operator.GETEILT,
        Operator.MODULO,
        Operator.HOCH -> methodenAufruf
        else -> throw Exception("Dieser Fall sollte nie auftreten!")
      }
    }
  }

  private fun generiereMinus(minus: AST.Satz.Ausdruck.Minus): IM_AST.Satz.Ausdruck {
    return IM_AST.Satz.Ausdruck.MethodenAufruf(minus.operator.typ.operator.methodenName!!, minus.operator.toUntyped(), emptyList(),
        generiereAusdruck(minus.ausdruck), null)
  }

  private fun generiereKonvertierung(konvertierung: AST.Satz.Ausdruck.Konvertierung): IM_AST.Satz.Ausdruck {
    return when(konvertierung.konvertierungsArt) {
      AST.Satz.Ausdruck.KonvertierungsArt.Cast ->
        IM_AST.Satz.Ausdruck.TypCast(generiereAusdruck(konvertierung.ausdruck), konvertierung.typ.typ!!, konvertierung.token)
      AST.Satz.Ausdruck.KonvertierungsArt.Methode -> IM_AST.Satz.Ausdruck.MethodenAufruf(
          "als " + konvertierung.typName.nominativ,
          konvertierung.token, emptyList(),
          generiereAusdruck(konvertierung.ausdruck),
          null
      )
    }
  }

  private fun generiereObjektInstanziierung(instanziierung: AST.Satz.Ausdruck.ObjektInstanziierung): IM_AST.Satz.Ausdruck {
    val klassenTyp = instanziierung.klasse.typ!! as Typ.Compound.Klasse
    val klasse = klassen.getValue(klassenTyp.definition)
    val objekt = IM_AST.Satz.Ausdruck.ObjektInstanziierung(
        instanziierung.klasse.typ!! as Typ.Compound.Klasse,
        klasse,
        IM_AST.Satz.Ausdruck.ObjektArt.Klasse
    )
    val sätze = mutableListOf<IM_AST.Satz>()

    fun generiereKonstruktor(instanziierung: AST.Satz.Ausdruck.ObjektInstanziierung): IM_AST.Satz.Ausdruck.Bereich {
      val eigenschaftsZuweisungen = instanziierung.eigenschaftsZuweisungen
      val definition = (instanziierung.klasse.typ!! as Typ.Compound.Klasse).definition
      val sätze: MutableList<IM_AST.Satz> = mutableListOf()
      val generierteEigenschaftsAusdrücke = eigenschaftsZuweisungen.map { generiereAusdruck(it.ausdruck) }
      if (definition.elternKlasse != null) {
        sätze.add(IM_AST.Satz.Ausdruck.Bereich(
            eigenschaftsZuweisungen.withIndex()
                .map { (index, zuweisung) -> IM_AST.Satz.VariablenDeklaration(zuweisung.name.nominativ, generierteEigenschaftsAusdrücke[index], false) } +
                generiereKonstruktor(definition.elternKlasse))
        )
      }

      for (index in eigenschaftsZuweisungen.indices) {
        val eigenschaftsName = typPrüfer.holeParamName(definition.eigenschaften[index], klassenTyp.typArgumente).nominativ
        sätze.add(IM_AST.Satz.SetzeEigenschaft(
            IM_AST.Satz.Ausdruck.Variable(INIT_OBJEKT_VAR_NAME),
            eigenschaftsName,
            generierteEigenschaftsAusdrücke[index])
        )
      }

      val klassenDefinition = (instanziierung.klasse.typ!! as Typ.Compound.Klasse).definition
      val konstruktorName = "erstelle ${klassenDefinition.name.nominativ}"

      sätze.add(IM_AST.Satz.Ausdruck.MethodenAufruf(
          konstruktorName,
          instanziierung.token,
          emptyList(),
          IM_AST.Satz.Ausdruck.Variable(INIT_OBJEKT_VAR_NAME),
          klassen.getValue((instanziierung.klasse.typ!! as Typ.Compound.Klasse).definition).methoden.getValue(konstruktorName)
      ))
      return IM_AST.Satz.Ausdruck.Bereich(sätze)
    }

    sätze.add(IM_AST.Satz.VariablenDeklaration(INIT_OBJEKT_VAR_NAME, objekt, false))
    sätze.add(generiereKonstruktor(instanziierung))
    sätze.add(IM_AST.Satz.Ausdruck.Variable(INIT_OBJEKT_VAR_NAME))
    return IM_AST.Satz.Ausdruck.Bereich(sätze)
  }

  private fun generiereLambda(lambda: AST.Satz.Ausdruck.Lambda): IM_AST.Satz.Ausdruck {
    val klasse = generiereKlasse(lambda.klasse.definition, true)
    return IM_AST.Satz.Ausdruck.ObjektInstanziierung(lambda.klasse, klasse, IM_AST.Satz.Ausdruck.ObjektArt.Lambda)
  }

  private fun generiereAnonymeKlasse(anonymeKlasse: AST.Satz.Ausdruck.AnonymeKlasse): IM_AST.Satz.Ausdruck {
    val klasse = generiereKlasse(anonymeKlasse.klasse.definition, true)
    val objekt = IM_AST.Satz.Ausdruck.ObjektInstanziierung(anonymeKlasse.klasse, klasse, IM_AST.Satz.Ausdruck.ObjektArt.AnonymeKlasse)
    val sätze: MutableList<IM_AST.Satz> = mutableListOf()
    sätze.add(IM_AST.Satz.VariablenDeklaration(INIT_OBJEKT_VAR_NAME, objekt, false))
    sätze.addAll(anonymeKlasse.bereich.eigenschaften.map {
      IM_AST.Satz.SetzeEigenschaft(
          IM_AST.Satz.Ausdruck.Variable(INIT_OBJEKT_VAR_NAME),
          it.name.nominativ,
          generiereAusdruck(it.wert))
    })
    sätze.add(IM_AST.Satz.Ausdruck.Variable(INIT_OBJEKT_VAR_NAME))
    return IM_AST.Satz.Ausdruck.Bereich(sätze)
  }

  private fun generiereBedingung(bedingung: AST.Satz.Ausdruck.Bedingung): IM_AST.Satz.Ausdruck {
    return IM_AST.Satz.Ausdruck.Bedingung(
        bedingung.bedingungen.map(::generiereBedingungsTerm),
        bedingung.sonst?.let { generiereBereich(it.bereich) }
    )
  }

  private fun generiereEigenschaftsZugriff(zugriff: AST.Satz.Ausdruck.IEigenschaftsZugriff, objekt: IM_AST.Satz.Ausdruck): IM_AST.Satz.Ausdruck {
    return if (zugriff.aufrufName == null) {
      IM_AST.Satz.Ausdruck.Eigenschaft(
          zugriff.eigenschaftsName.nominativ,
          objekt
      )
    } else {
      IM_AST.Satz.Ausdruck.MethodenAufruf(
          zugriff.aufrufName!!,
          zugriff.token,
          emptyList(),
          objekt,
          null
      )
    }
  }

  private fun generiereVersucheFange(versucheFange: AST.Satz.Ausdruck.VersucheFange): IM_AST.Satz.Ausdruck {
    return IM_AST.Satz.Ausdruck.VersucheFange(
        generiereBereich(versucheFange.bereich),
        versucheFange.fange.map {
          IM_AST.Satz.Ausdruck.Fange(
              it.param.name.nominativ,
              it.param.typKnoten.typ!! as Typ.Compound,
              generiereBereich(it.bereich)
          )
        },
        versucheFange.schlussendlich?.let { generiereBereich(it) }
    )
  }

  private fun generiereWerfe(werfe: AST.Satz.Ausdruck.Werfe): IM_AST.Satz.Ausdruck.Werfe {
    return IM_AST.Satz.Ausdruck.Werfe(
        werfe.werfe,
        generiereAusdruck(werfe.ausdruck)
    )
  }

  private fun generiereTypÜberprüfung(typÜberprüfung: AST.Satz.Ausdruck.TypÜberprüfung): IM_AST.Satz.Ausdruck {
    return IM_AST.Satz.Ausdruck.TypÜberprüfung(
        generiereAusdruck(typÜberprüfung.ausdruck),
        typÜberprüfung.typ.typ!!
    )
  }
  // endregion
}