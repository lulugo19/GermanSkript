package germanskript.imm

import germanskript.*
import java.io.File
import java.util.*
import kotlin.collections.HashMap

class IMMCodeGenerator(startDatei: File): PipelineKomponente(startDatei) {
  val typPrüfer = TypPrüfer(startDatei)
  val ast = typPrüfer.ast

  val klassen = mutableMapOf<AST.Definition.Typdefinition.Klasse, IMM_AST.Definition.Klasse>()
  val funktionen = mutableMapOf<AST.Definition, IMM_AST.Definition.Funktion>()

  private val methodenBereichsObjekte = Stack<IMM_AST.Satz.Ausdruck.Variable>()

  fun generiere(): IMM_AST {
    typPrüfer.prüfe()

    val definierer = typPrüfer.definierer
    definierer.funktionsDefinitionen.forEach(::generiereFunktion)
    definierer.holeDefinitionen<AST.Definition.Typdefinition.Klasse>().forEach(::generiereKlasse)

    return generiereBereich(ast.programm)
  }

  private fun holeFunktionsParameterNamen(signatur: AST.Definition.FunktionsSignatur): List<String> {
    return signatur.parameter.map { it.name.nominativ }.toMutableList()
  }

  private fun generiereFunktion(funktion: AST.Definition.Funktion) {
    val parameter = holeFunktionsParameterNamen(funktion.signatur)
    funktionen[funktion] = IMM_AST.Definition.Funktion(parameter)
  }

  private fun generiereKlasse(klasse: AST.Definition.Typdefinition.Klasse) {
      val methoden = HashMap<String, IMM_AST.Definition.Funktion>()
      for ((name, methode) in klasse.methoden) {
        val parameter = holeFunktionsParameterNamen(methode.signatur)
        val funktion = IMM_AST.Definition.Funktion(parameter)
        methoden[name] = funktion
        funktionen[methode] = funktion
      }

      // füge Konvertierungen als Methode hinzu
      for((name, konvertierung) in klasse.konvertierungen) {
        val funktion = IMM_AST.Definition.Funktion(emptyList())
        methoden["als " + name] = funktion
        funktionen[konvertierung] = IMM_AST.Definition.Funktion(emptyList())
      }

      // füge berechnete Eigenschaften als Methode hinzu
      for ((name, eigenschaft) in klasse.berechneteEigenschaften) {
        val funktion = IMM_AST.Definition.Funktion(emptyList())
        methoden[name] = funktion
        funktionen[eigenschaft] = IMM_AST.Definition.Funktion(emptyList())
      }

      // Füge Konstruktor als Methode hinzu
      val konstruktor = IMM_AST.Definition.Funktion(emptyList())
      methoden["erstelle " + klasse.name.nominativ] = konstruktor
      funktionen[klasse] = konstruktor

      klassen[klasse] = IMM_AST.Definition.Klasse(klasse.name.nominativ, methoden)
    }

  // region Sätze
  private fun generiereSatz(satz: AST.Satz): IMM_AST.Satz {
    return when (satz) {
      is AST.Satz.Intern -> IMM_AST.Satz.Intern
      AST.Satz.SchleifenKontrolle.Fortfahren -> IMM_AST.Satz.Fortfahren
      AST.Satz.SchleifenKontrolle.Abbrechen -> IMM_AST.Satz.Abbrechen
      is AST.Satz.VariablenDeklaration -> generiereVariablenDeklaration(satz)
      is AST.Satz.IndexZuweisung -> generiereIndexZuweisung(satz)
      is AST.Satz.Bereich -> generiereBereich(satz)
      is AST.Satz.SolangeSchleife -> generiereSolangeSchleife(satz)
      is AST.Satz.FürJedeSchleife -> generiereFürJedeSchleife(satz)
      is AST.Satz.SuperBlock -> generiereBereich(satz.bereich)
      is AST.Satz.Zurückgabe -> generiereZurückgabe(satz)
      is AST.Satz.Ausdruck -> generiereAusdruck(satz)
    }
  }

  private fun generiereBereich(bereich: AST.Satz.Bereich): IMM_AST.Satz.Ausdruck.Bereich {
    val sätze = bereich.sätze.map(::generiereSatz)
    return IMM_AST.Satz.Ausdruck.Bereich(sätze)
  }

  private fun generiereVariablenDeklaration(deklaration: AST.Satz.VariablenDeklaration): IMM_AST.Satz {
    val wert = generiereAusdruck(deklaration.wert)
    return if (deklaration.istEigenschaftsNeuZuweisung || deklaration.istEigenschaft) {
      IMM_AST.Satz.SetzeEigenschaft(deklaration.name.nominativ, wert)
    }
    else {
      val überschreibe = !deklaration.name.unveränderlich && deklaration.neu == null
      IMM_AST.Satz.VariablenDeklaration(deklaration.name.nominativ, wert, überschreibe)
    }
  }

  private fun generiereIndexZuweisung(indexZuweisung: AST.Satz.IndexZuweisung): IMM_AST.Satz {
    val objekt = IMM_AST.Satz.Ausdruck.Variable(indexZuweisung.singular.ganzesWort(Kasus.NOMINATIV, indexZuweisung.numerus, true))
    val argumente = listOf(generiereAusdruck(indexZuweisung.index), generiereAusdruck(indexZuweisung.wert))
    val methodenName = indexZuweisung.implementierteSchnittstelle!!.definition.methodenSignaturen[0].vollerName!!
    return IMM_AST.Satz.Ausdruck.MethodenAufruf(
        methodenName,
        indexZuweisung.zuweisung.toUntyped(),
        argumente,
        objekt,
        null // dynamic Dispatching
    )
  }

  private fun generiereZurückgabe(satz: AST.Satz.Zurückgabe): IMM_AST.Satz {
    return IMM_AST.Satz.Zurückgabe(generiereAusdruck(satz.ausdruck))
  }

  private fun generiereFürJedeSchleife(schleife: AST.Satz.FürJedeSchleife): IMM_AST.Satz {

    val iterierbar = when {
      schleife.iterierbares != null -> generiereAusdruck(schleife.iterierbares)
      schleife.reichweite != null -> IMM_AST.Satz.Ausdruck.ObjektInstanziierung(
          klassen.getValue(BuildIn.Klassen.reichweite.definition),
          false
      )
      else -> IMM_AST.Satz.Ausdruck.Variable(schleife.singular.ganzesWort(Kasus.NOMINATIV, Numerus.PLURAL, true))
    }

    val holeIterator = IMM_AST.Satz.Ausdruck.MethodenAufruf(
        "hole den Iterator", schleife.für.toUntyped(), emptyList(), iterierbar, null
    )

    val iteratorDeklaration = IMM_AST.Satz.VariablenDeklaration("_Iterator", holeIterator, false)

    val bedingung = IMM_AST.Satz.Ausdruck.MethodenAufruf(
        "läuft weiter", schleife.für.toUntyped(), emptyList(), IMM_AST.Satz.Ausdruck.Variable("_Iterator"), null
    )

    val holeIteration = IMM_AST.Satz.Ausdruck.MethodenAufruf(
        "hole den Typ", schleife.für.toUntyped(), emptyList(), IMM_AST.Satz.Ausdruck.Variable("_Iterator"), null
    )

    val binderDeklaration = IMM_AST.Satz.VariablenDeklaration(schleife.binder.nominativ, holeIteration, false)

    val sätze = schleife.bereich.sätze.map(::generiereSatz).toMutableList()
    sätze.add(0, binderDeklaration)

    val bereich = IMM_AST.Satz.Ausdruck.Bereich(sätze)

    val solangeSchleife = IMM_AST.Satz.SolangeSchleife(IMM_AST.Satz.BedingungsTerm(bedingung, bereich))

    return IMM_AST.Satz.Ausdruck.Bereich(listOf(
        iteratorDeklaration,
        solangeSchleife
    ))
  }

  private fun generiereBedingungsTerm(bedingungsTerm: AST.Satz.BedingungsTerm): IMM_AST.Satz.BedingungsTerm {
    return IMM_AST.Satz.BedingungsTerm(
        generiereAusdruck(bedingungsTerm.bedingung),
        generiereBereich(bedingungsTerm.bereich)
    )
  }

  private fun generiereSolangeSchleife(schleife: AST.Satz.SolangeSchleife): IMM_AST.Satz {
    return IMM_AST.Satz.SolangeSchleife(generiereBedingungsTerm(schleife.bedingung))
  }
  // endregion

  // region Ausdrücke
  private fun generiereAusdruck(ausdruck: AST.Satz.Ausdruck): IMM_AST.Satz.Ausdruck {
    return when (ausdruck) {
      is AST.Satz.Ausdruck.Nichts -> IMM_AST.Satz.Ausdruck.Konstante.Nichts
      is AST.Satz.Ausdruck.Zeichenfolge -> IMM_AST.Satz.Ausdruck.Konstante.Zeichenfolge(ausdruck.zeichenfolge.typ.zeichenfolge)
      is AST.Satz.Ausdruck.Zahl -> IMM_AST.Satz.Ausdruck.Konstante.Zahl(ausdruck.zahl.typ.zahl)
      is AST.Satz.Ausdruck.Boolean -> IMM_AST.Satz.Ausdruck.Konstante.Boolean(ausdruck.boolean.typ.boolean)
      is AST.Satz.Ausdruck.Variable -> generiereVariable(ausdruck)
      is AST.Satz.Ausdruck.FunktionsAufruf -> generiereFunktionsAufruf(ausdruck)
      is AST.Satz.Ausdruck.MethodenBereich -> generiereMethodenBereich(ausdruck)
      is AST.Satz.Ausdruck.Konstante -> generiereKonstante(ausdruck)
      is AST.Satz.Ausdruck.Liste -> generiereListe(ausdruck)
      is AST.Satz.Ausdruck.IndexZugriff -> generiereIndexZugriff(ausdruck)
      is AST.Satz.Ausdruck.BinärerAusdruck -> generiereBinärenAusdruck(ausdruck)
      is AST.Satz.Ausdruck.Minus -> TODO()
      is AST.Satz.Ausdruck.Konvertierung -> TODO()
      is AST.Satz.Ausdruck.ObjektInstanziierung -> TODO()
      is AST.Satz.Ausdruck.Lambda -> TODO()
      is AST.Satz.Ausdruck.AnonymeKlasse -> TODO()
      is AST.Satz.Ausdruck.Bedingung -> TODO()
      is AST.Satz.Ausdruck.TypÜberprüfung -> TODO()
      is AST.Satz.Ausdruck.EigenschaftsZugriff -> TODO()
      is AST.Satz.Ausdruck.MethodenBereichEigenschaftsZugriff -> TODO()
      is AST.Satz.Ausdruck.SelbstEigenschaftsZugriff -> TODO()
      is AST.Satz.Ausdruck.SelbstReferenz -> TODO()
      is AST.Satz.Ausdruck.MethodenBereichReferenz -> TODO()
      is AST.Satz.Ausdruck.VersucheFange -> TODO()
      is AST.Satz.Ausdruck.Werfe -> TODO()
    }
  }

  private fun generiereVariable(variable: AST.Satz.Ausdruck.Variable): IMM_AST.Satz.Ausdruck {
    return if (variable.konstante != null) {
      generiereAusdruck(variable.konstante!!.wert!!)
    } else {
      IMM_AST.Satz.Ausdruck.Variable(variable.name.nominativ)
    }
  }

  private fun generiereFunktionsAufruf(aufruf: AST.Satz.Ausdruck.FunktionsAufruf): IMM_AST.Satz.Ausdruck {
    val funktionsSignatur = aufruf.funktionsDefinition?.signatur
        ?: aufruf.objektTyp!!.definition.methoden.getValue(aufruf.vollerName!!).signatur

    val argumente = aufruf.argumente.map { generiereAusdruck(it.ausdruck) }.toList()

    return if (aufruf.aufrufTyp == FunktionsAufrufTyp.FUNKTIONS_AUFRUF) {
      IMM_AST.Satz.Ausdruck.FunktionsAufruf(
          aufruf.vollerName!!,
          aufruf.token,
          argumente, funktionen.getValue(aufruf.funktionsDefinition!!)
      )
    } else {
      val objekt = when(aufruf.aufrufTyp) {
        FunktionsAufrufTyp.METHODEN_SELBST_AUFRUF -> IMM_AST.Satz.Ausdruck.Variable("_Selbst")
        FunktionsAufrufTyp.METHODEN_BEREICHS_AUFRUF -> IMM_AST.Satz.Ausdruck.Variable("_MBObjekt")
        FunktionsAufrufTyp.METHODEN_REFLEXIV_AUFRUF -> generiereAusdruck(aufruf.objekt!!.ausdruck)
        FunktionsAufrufTyp.METHODEN_SUBJEKT_AUFRUF -> generiereAusdruck(aufruf.subjekt!!)
        else -> throw Exception("Dieser Fall sollte nie auftreten!")
      }

      val funktionsDefinition = if (aufruf.funktionsDefinition != null)
        funktionen.getValue(aufruf.funktionsDefinition!!) else null

      IMM_AST.Satz.Ausdruck.MethodenAufruf(
          aufruf.vollerName!!,
          aufruf.token,
          argumente,
          objekt,
          funktionsDefinition
      )
    }
  }

  private fun generiereMethodenBereich(bereich: AST.Satz.Ausdruck.MethodenBereich): IMM_AST.Satz.Ausdruck {
    val deklaration = IMM_AST.Satz.VariablenDeklaration("_MBObjekt", generiereAusdruck(bereich.objekt), false)
    val bereich = generiereBereich(bereich.bereich)
    val sätze = bereich.sätze.toMutableList()
    sätze.add(0, deklaration)
    return IMM_AST.Satz.Ausdruck.Bereich(sätze)
  }

  private fun generiereKonstante(konstante: AST.Satz.Ausdruck.Konstante): IMM_AST.Satz.Ausdruck {
    return generiereAusdruck(konstante.wert!!)
  }

  private fun generiereListe(liste: AST.Satz.Ausdruck.Liste): IMM_AST.Satz.Ausdruck {
    val sätze = mutableListOf<IMM_AST.Satz>()
    val erstelleListe = IMM_AST.Satz.Ausdruck.ObjektInstanziierung(klassen.getValue(BuildIn.Klassen.liste), false)
    val varDeklaration = IMM_AST.Satz.VariablenDeklaration("_Liste", erstelleListe, false)
    sätze += varDeklaration
    for (element in liste.elemente) {
      sätze += IMM_AST.Satz.Ausdruck.MethodenAufruf(
          "füge das Element hinzu",
          liste.holeErstesToken(),
          listOf(generiereAusdruck(element)),
          IMM_AST.Satz.Ausdruck.Variable("_Liste"),
          null
      )
    }
    sätze += IMM_AST.Satz.Ausdruck.Variable("_Liste")
    return IMM_AST.Satz.Ausdruck.Bereich(sätze)
  }

  private fun generiereIndexZugriff(indexZugriff: AST.Satz.Ausdruck.IndexZugriff): IMM_AST.Satz.Ausdruck {
      return IMM_AST.Satz.Ausdruck.MethodenAufruf(
          "hole den Typ an dem Index",
          indexZugriff.holeErstesToken(),
          listOf(generiereAusdruck(indexZugriff.index)),
          IMM_AST.Satz.Ausdruck.Variable(indexZugriff.singular.ganzesWort(Kasus.NOMINATIV, indexZugriff.numerus, true)),
          null
      )
  }

  private fun generiereBinärenAusdruck(ausdruck: AST.Satz.Ausdruck.BinärerAusdruck): IMM_AST.Satz.Ausdruck {
    val operation = when(ausdruck.operator.typ.operator) {
      Operator.ODER -> "oder"
      Operator.UND -> "und"
      Operator.GLEICH -> "gleich"
      Operator.UNGLEICH -> "ungleich"
      Operator.GRÖßER -> "größer"
      Operator.KLEINER -> "kleiner"
      Operator.GRÖSSER_GLEICH -> "größer_gleich"
      Operator.KLEINER_GLEICH -> "kleiner_gleich"
      Operator.PLUS -> "plus"
      Operator.MINUS -> "minus"
      Operator.MAL -> "mal"
      Operator.GETEILT -> "durch"
      Operator.MODULO -> "modulo"
      Operator.HOCH -> "hoch"
      Operator.NEGATION -> throw Exception("Dieser Fall sollte nie eintreten!")
    }
    return TODO()
  }

  // endregion
}