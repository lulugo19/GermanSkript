package germanskript

import java.io.File
import java.util.*

/**
 * Constant Folding: https: //en.wikipedia.org/wiki/Constant_folding
 * Dead Code Elimination: https://en.wikipedia.org/wiki/Dead_code_elimination
 *
 * Zurückgabe-Statements in Bedingungen und Schleifen
 */
class KonstantenFalter(startDatei: File): ProgrammDurchlaufer<Wert?>(startDatei) {
  val typPrüfer = TypPrüfer(startDatei)
  val ast = typPrüfer.ast
  override val definierer: Definierer
    get() = typPrüfer.definierer

  override var umgebung = Umgebung<Wert?>()

  fun falteKonstanten() {
    typPrüfer.prüfe()
    definierer.funktionsDefinitionen.forEach(::falteFunktion)
    definierer.holeDefinitionen<AST.Definition.Typdefinition.Klasse>().forEach(::falteKlasse)
    durchlaufeAufruf(ast.programm, Umgebung(), true)
  }

  private fun durchlaufeAufruf(bereich: AST.Satz.Bereich, umgebung: Umgebung<Wert?>, neuerBereich: Boolean) {
    this.umgebung = umgebung
    durchlaufeBereich(bereich, neuerBereich)
  }

  private fun falteFunktion(funktion: AST.Definition.Funktion) {
    if (funktion.signatur.rückgabeTyp == null) {
      return
    }
    val funktionsUmgebung = Umgebung<Wert?>()
    funktionsUmgebung.pushBereich()
    for (parameter in funktion.signatur.parameter) {
      funktionsUmgebung.schreibeVariable(parameter.name, null, false)
    }
    durchlaufeAufruf(funktion.körper, funktionsUmgebung, false)
  }

  private fun falteKlasse(klasse: AST.Definition.Typdefinition.Klasse) {
    durchlaufeAufruf(klasse.konstruktor, Umgebung(), true)
    klasse.methoden.values.forEach {methode -> falteFunktion(methode)}
    klasse.konvertierungen.values.forEach {konvertierung ->
      durchlaufeAufruf(konvertierung.definition, Umgebung(), true)
    }
    klasse.berechneteEigenschaften.values.forEach {eigenschaft ->
      durchlaufeAufruf(eigenschaft.definition, Umgebung(), true)
    }
  }

  private fun falteKonstante(originalerAusdruck: AST.Ausdruck): AST.Ausdruck {
    return when (val konstanterWert = evaluiereAusdruck(originalerAusdruck)) {
      is Wert.Primitiv.Zahl -> AST.Ausdruck.Zahl(TypedToken(TokenTyp.ZAHL(konstanterWert), "", "", Token.Position.Ende, Token.Position.Ende))
      is Wert.Primitiv.Zeichenfolge -> AST.Ausdruck.Zeichenfolge(TypedToken(TokenTyp.ZEICHENFOLGE(konstanterWert), "", "", Token.Position.Ende, Token.Position.Ende))
      is Wert.Primitiv.Boolean -> AST.Ausdruck.Boolean(TypedToken(TokenTyp.BOOLEAN(konstanterWert), "", "", Token.Position.Ende, Token.Position.Ende))
      else -> originalerAusdruck
    }
  }

  override fun bevorDurchlaufeMethodenBlock(methodenBlock: AST.Satz.MethodenBlock, blockObjekt: Wert?) {
    // hier muss nichts gemacht werden
  }

  override fun sollSätzeAbbrechen(): Boolean = false

  override fun durchlaufeFunktionsAufruf(funktionsAufruf: AST.FunktionsAufruf, istAusdruck: Boolean): Wert? {
    funktionsAufruf.argumente.forEach {arg -> arg.ausdruck = falteKonstante(arg.ausdruck)}
    return  null
  }

  class GeleseneVariable(val deklaration: AST.Satz.VariablenDeklaration, var wurdeGelesen: Boolean)

  val geleseneVariablen = Stack<MutableMap<String, GeleseneVariable>>()

  override fun durchlaufeVariablenDeklaration(deklaration: AST.Satz.VariablenDeklaration) {
    deklaration.wert = falteKonstante(deklaration.wert)
    if (deklaration.istEigenschaftsNeuZuweisung || deklaration.istEigenschaft) {
      return
    }
    else {
      val geleseneVariablen = geleseneVariablen.peek()!!
      val wert = evaluiereAusdruck(deklaration.wert)
      if (deklaration.name.unveränderlich || deklaration.neu != null) {
        umgebung.schreibeVariable(deklaration.name, wert, false)
      } else {
        umgebung.überschreibeVariable(deklaration.name, wert)
        if (!geleseneVariablen[deklaration.name.nominativ]!!.wurdeGelesen) {
          val bereich = deklaration.parent as AST.Satz.Bereich
          bereich.sätze.remove(geleseneVariablen[deklaration.name.nominativ]!!.deklaration)
        }
      }
      geleseneVariablen[deklaration.name.nominativ] = GeleseneVariable(deklaration, false)
    }
  }

  override fun starteBereich(bereich: AST.Satz.Bereich) {
    geleseneVariablen.push(mutableMapOf())
  }

  override fun beendeBereich(bereich: AST.Satz.Bereich) {
    // eliminiere alle Variablendeklarationen die nicht gelesen wurden
    val unnötigeVariablendeklarationen = geleseneVariablen.peek().values.filter { it.wurdeGelesen }.map { it.deklaration }
    bereich.sätze.removeAll(unnötigeVariablendeklarationen)
    geleseneVariablen.pop()
  }

  override fun evaluiereVariable(name: AST.WortArt.Nomen): Wert? {
    val geleseneVariablen = geleseneVariablen.peek()
    if (geleseneVariablen.containsKey(name.nominativ)) {
      geleseneVariablen.getValue(name.nominativ).wurdeGelesen = true
    }

    return super.evaluiereVariable(name)
  }

  override fun evaluiereKonstante(konstante: AST.Ausdruck.Konstante): Wert? = evaluiereAusdruck(konstante.wert!!)

  override fun durchlaufeZurückgabe(zurückgabe: AST.Satz.Zurückgabe) {
    if (zurückgabe.ausdruck != null) {
      zurückgabe.ausdruck = falteKonstante(zurückgabe.ausdruck!!)
    }
  }

  override fun durchlaufeBedingungsSatz(bedingungsSatz: AST.Satz.Bedingung) {
    // dead code elimination
    val eliminierteBedingungen = mutableListOf<AST.Satz.BedingungsTerm>()
    for (index in bedingungsSatz.bedingungen.indices) {
      val bedingung = bedingungsSatz.bedingungen[index]
      bedingung.bedingung = falteKonstante(bedingung.bedingung)
      if (bedingung.bedingung is AST.Ausdruck.Boolean) {
        if ((bedingung.bedingung as AST.Ausdruck.Boolean).boolean.typ.boolean.boolean) {
          eliminierteBedingungen.addAll(
              bedingungsSatz.bedingungen.takeLast(bedingungsSatz.bedingungen.size-1-index))
          bedingungsSatz.sonst = null
          durchlaufeBereich(bedingung.bereich, true)
          break
        } else {
          eliminierteBedingungen.add(bedingung)
        }
      } else {
        durchlaufeBereich(bedingung.bereich, true)
      }
    }
    bedingungsSatz.bedingungen.removeAll(eliminierteBedingungen)
    val ersteBedingung = bedingungsSatz.bedingungen[0]
    if (ersteBedingung.bedingung is AST.Ausdruck.Boolean &&
        (ersteBedingung.bedingung as AST.Ausdruck.Boolean).boolean.typ.boolean.boolean) {
      val bereich = bedingungsSatz.parent as AST.Satz.Bereich
      bereich.sätze[bereich.sätze.indexOf(bedingungsSatz)] = AST.Satz.Bereich(ersteBedingung.bereich.sätze)
    }
  }

  override fun durchlaufeAbbrechen() {
    // hier muss nichts gemacht werden
  }

  override fun durchlaufeFortfahren() {
    // hier muss nichts gemacht werden
  }

  override fun durchlaufeSolangeSchleife(schleife: AST.Satz.SolangeSchleife) {
    schleife.bedingung.bedingung = falteKonstante(schleife.bedingung.bedingung)
    durchlaufeBereich(schleife.bedingung.bereich, true)
  }

  override fun durchlaufeFürJedeSchleife(schleife: AST.Satz.FürJedeSchleife) {
    umgebung.pushBereich()
    umgebung.schreibeVariable(schleife.binder, null, true)
    durchlaufeBereich(schleife.bereich, false)
    umgebung.popBereich()
  }

  override fun durchlaufeVersucheFange(versucheFange: AST.Satz.VersucheFange) {
    durchlaufeBereich(versucheFange.versuche, true)
    for (fange in versucheFange.fange) {
      umgebung.pushBereich()
      umgebung.schreibeVariable(fange.param.name, null ,true)
      durchlaufeBereich(fange.bereich, false)
      umgebung.popBereich()
    }
  }

  override fun durchlaufeWerfe(werfe: AST.Satz.Werfe) {
    werfe.ausdruck = falteKonstante(werfe.ausdruck)
  }

  override fun durchlaufeIntern(intern: AST.Satz.Intern) {
    // hier muss nichts gemacht werden
  }

  override fun evaluiereZeichenfolge(ausdruck: AST.Ausdruck.Zeichenfolge): Wert? = ausdruck.zeichenfolge.typ.zeichenfolge

  override fun evaluiereZahl(ausdruck: AST.Ausdruck.Zahl): Wert? = ausdruck.zahl.typ.zahl

  override fun evaluiereBoolean(ausdruck: AST.Ausdruck.Boolean): Wert? = ausdruck.boolean.typ.boolean

  override fun evaluiereListe(ausdruck: AST.Ausdruck.Liste): Wert? {
    ausdruck.elemente = ausdruck.elemente.map(::falteKonstante)
    return null
  }

  override fun evaluiereListenElement(listenElement: AST.Ausdruck.ListenElement): Wert? {
    listenElement.index = falteKonstante(listenElement.index)
    return null
  }

  override fun evaluiereBinärenAusdruck(ausdruck: AST.Ausdruck.BinärerAusdruck): Wert? {
    val links = evaluiereAusdruck(ausdruck.links)
    val rechts = evaluiereAusdruck(ausdruck.rechts)
    val operator = ausdruck.operator.typ.operator
    return when {
      links is Wert.Primitiv.Zeichenfolge && rechts is Wert.Primitiv.Zeichenfolge -> Interpretierer.zeichenFolgenOperation(operator, links, rechts)
      links is Wert.Primitiv.Zahl && rechts is Wert.Primitiv.Zahl -> Interpretierer.zahlOperation(operator, links, rechts)
      links is Wert.Primitiv.Boolean && rechts is Wert.Primitiv.Boolean -> Interpretierer.booleanOperation(operator, links, rechts)
      else -> null
    }
  }

  override fun evaluiereMinus(minus: AST.Ausdruck.Minus): Wert? {
    val zahl = evaluiereAusdruck(minus.ausdruck) as Wert.Primitiv.Zahl?
    return if (zahl != null) {
      -zahl
    } else {
      null
    }
  }

  override fun evaluiereKonvertierung(konvertierung: AST.Ausdruck.Konvertierung): Wert? {
    konvertierung.ausdruck = falteKonstante(konvertierung.ausdruck)
    return null
  }

  override fun evaluiereObjektInstanziierung(instanziierung: AST.Ausdruck.ObjektInstanziierung): Wert? {
    instanziierung.eigenschaftsZuweisungen.forEach {zuweisung -> zuweisung.ausdruck = falteKonstante(zuweisung.ausdruck)}
    return null
  }

  override fun evaluiereClosure(closure: AST.Ausdruck.Closure): Wert? {
    val signatur = (closure.schnittstelle.typ as Typ.Compound.Schnittstelle).definition.methodenSignaturen[0]
    umgebung.pushBereich()
    for (param in signatur.parameter) {
      umgebung.schreibeVariable(param.name, null, true)
    }
    durchlaufeBereich(closure.körper, false)
    umgebung.popBereich()
    return null
  }

  override fun evaluiereEigenschaftsZugriff(eigenschaftsZugriff: AST.Ausdruck.EigenschaftsZugriff): Wert? = null

  override fun evaluiereSelbstEigenschaftsZugriff(eigenschaftsZugriff: AST.Ausdruck.SelbstEigenschaftsZugriff): Wert? = null

  override fun evaluiereMethodenBlockEigenschaftsZugriff(eigenschaftsZugriff: AST.Ausdruck.MethodenBlockEigenschaftsZugriff): Wert? = null

  override fun evaluiereSelbstReferenz(): Wert? = null
}

fun main() {
  val source = """
    die ZahlA ist 5 * 10 - 8
    die ZahlB ist die ZahlA durch 7
    wenn die ZahlB gleich 6 ist:
      schreibe die Zeile "Die Zahl ist gleich " + "sechs!"
    .
    sonst wenn die ZahlB kleiner 6 ist:
      schreibe die Zeile "Die Zahl ist kleiner sechs!"
    .
    sonst: 
      schreibe die Zeile "Die Zahl ist größer sechs!"
    .
  """.trimIndent()

  val file = createTempFile("constant_folding", ".gm")
  file.writeText(source)
  val falter = KonstantenFalter(file)
  falter.falteKonstanten()
  falter.ast.programm.sätze.forEach(::println)
  file.delete()
}