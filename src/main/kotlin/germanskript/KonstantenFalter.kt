package germanskript

import germanskript.alte_pipeline.Interpretierer
import germanskript.alte_pipeline.intern.Objekt
import germanskript.alte_pipeline.intern.Zahl
import germanskript.alte_pipeline.intern.Zeichenfolge
import java.io.File
import java.util.*

/**
 * Constant Folding: https: //en.wikipedia.org/wiki/Constant_folding
 * Dead Code Elimination: https://en.wikipedia.org/wiki/Dead_code_elimination
 *
 * Zurückgabe-Statements in Bedingungen und Schleifen
 */
class KonstantenFalter(startDatei: File): ProgrammDurchlaufer<Objekt?>(startDatei) {
  val typPrüfer = TypPrüfer(startDatei)
  val ast = typPrüfer.ast

  override val definierer: Definierer
    get() = typPrüfer.definierer

  override val nichts: Objekt? = null
  override val niemals: Objekt? = null

  override var umgebung = Umgebung<Objekt?>()

  fun falteKonstanten() {
    typPrüfer.prüfe()
    definierer.funktionsDefinitionen.forEach(::falteFunktion)
    definierer.holeDefinitionen<AST.Definition.Typdefinition.Klasse>().forEach(::falteKlasse)
    durchlaufeAufruf(ast.programm, Umgebung(), true)
  }

  private fun durchlaufeAufruf(bereich: AST.Satz.Bereich, umgebung: Umgebung<Objekt?>, neuerBereich: Boolean) {
    this.umgebung = umgebung
    durchlaufeBereich(bereich, neuerBereich)
  }

  private fun falteFunktion(funktion: AST.Definition.Funktion) {
    val funktionsUmgebung = Umgebung<Objekt?>()
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

  private fun falteKonstante(originalerAusdruck: AST.Satz.Ausdruck): AST.Satz.Ausdruck {
    return when (val konstanterWert = evaluiereAusdruck(originalerAusdruck)) {
      is Zahl -> AST.Satz.Ausdruck.Zahl(TypedToken.imaginäresToken(TokenTyp.ZAHL(konstanterWert.zahl),""))
      is Zeichenfolge -> AST.Satz.Ausdruck.Zeichenfolge(TypedToken.imaginäresToken(TokenTyp.ZEICHENFOLGE(konstanterWert.zeichenfolge), ""))
      is germanskript.alte_pipeline.intern.Boolean -> AST.Satz.Ausdruck.Boolean(TypedToken.imaginäresToken(TokenTyp.BOOLEAN(konstanterWert.boolean), ""))
      else -> originalerAusdruck
    }
  }

  override fun bevorDurchlaufeNachrichtenBereich(nachrichtenBereich: AST.Satz.Ausdruck.NachrichtenBereich, blockObjekt: Objekt?) {
    // hier muss nichts gemacht werden
  }

  override fun sollteAbbrechen(): Boolean = false
  override fun sollteStackAufrollen(): Boolean = false

  override fun durchlaufeFunktionsAufruf(funktionsAufruf: AST.Satz.Ausdruck.FunktionsAufruf, istAusdruck: Boolean): Objekt? {
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

  override fun durchlaufeIndexZuweisung(zuweisung: AST.Satz.IndexZuweisung) {
    falteKonstante(zuweisung.wert)
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

  override fun evaluiereVariable(name: AST.WortArt.Nomen): Objekt? {
    val geleseneVariablen = geleseneVariablen.peek()
    geleseneVariablen[name.nominativ]?.also { variable ->
      variable.wurdeGelesen = true
    }
    return super.evaluiereVariable(name)
  }

  override fun evaluiereKonstante(konstante: AST.Satz.Ausdruck.Konstante): Objekt? = evaluiereAusdruck(konstante.wert!!)

  override fun durchlaufeZurückgabe(zurückgabe: AST.Satz.Zurückgabe): Objekt? {
    zurückgabe.ausdruck = falteKonstante(zurückgabe.ausdruck)
    return null
  }

  override fun durchlaufeBedingungsSatz(bedingungsSatz: AST.Satz.Ausdruck.Bedingung, istAusdruck: Boolean): Objekt? {
    // dead code elimination
    val eliminierteBedingungen = mutableListOf<AST.Satz.BedingungsTerm>()
    for (index in bedingungsSatz.bedingungen.indices) {
      val bedingung = bedingungsSatz.bedingungen[index]
      bedingung.bedingung = falteKonstante(bedingung.bedingung)
      if (bedingung.bedingung is AST.Satz.Ausdruck.Boolean) {
        if ((bedingung.bedingung as AST.Satz.Ausdruck.Boolean).boolean.typ.boolean) {
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
    if (ersteBedingung.bedingung is AST.Satz.Ausdruck.Boolean &&
        (ersteBedingung.bedingung as AST.Satz.Ausdruck.Boolean).boolean.typ.boolean) {
      val bereich = bedingungsSatz.parent as AST.Satz.Bereich
      bereich.sätze[bereich.sätze.indexOf(bedingungsSatz)] = AST.Satz.Bereich(ersteBedingung.bereich.sätze)
      return durchlaufeBereich(ersteBedingung.bereich, true)
    }
    return null
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

  override fun durchlaufeVersucheFange(versucheFange: AST.Satz.Ausdruck.VersucheFange): Objekt? {
    durchlaufeBereich(versucheFange.bereich, true)
    for (fange in versucheFange.fange) {
      umgebung.pushBereich()
      umgebung.schreibeVariable(fange.param.name, null ,true)
      durchlaufeBereich(fange.bereich, false)
      umgebung.popBereich()
    }
    return null
  }

  override fun durchlaufeWerfe(werfe: AST.Satz.Ausdruck.Werfe): Objekt? {
    werfe.ausdruck = falteKonstante(werfe.ausdruck)
    return null
  }

  override fun durchlaufeIntern(intern: AST.Satz.Intern): Objekt? {
    // hier muss nichts gemacht werden
    return null
  }

  override fun evaluiereZeichenfolge(ausdruck: AST.Satz.Ausdruck.Zeichenfolge): Objekt? =
      Zeichenfolge(ausdruck.zeichenfolge.typ.zeichenfolge)

  override fun evaluiereZahl(ausdruck: AST.Satz.Ausdruck.Zahl): Objekt? =
      Zahl(ausdruck.zahl.typ.zahl)

  override fun evaluiereBoolean(ausdruck: AST.Satz.Ausdruck.Boolean): Objekt? =
      germanskript.alte_pipeline.intern.Boolean(ausdruck.boolean.typ.boolean)

  override fun evaluiereListe(ausdruck: AST.Satz.Ausdruck.Liste): Objekt? {
    ausdruck.elemente = ausdruck.elemente.map(::falteKonstante)
    return null
  }

  override fun evaluiereIndexZugriff(indexZugriff: AST.Satz.Ausdruck.IndexZugriff): Objekt? {
    indexZugriff.index = falteKonstante(indexZugriff.index)
    return null
  }

  override fun evaluiereBinärenAusdruck(ausdruck: AST.Satz.Ausdruck.BinärerAusdruck): Objekt? {
    val links = evaluiereAusdruck(ausdruck.links)
    val rechts = evaluiereAusdruck(ausdruck.rechts)
    val operator = ausdruck.operator.typ.operator
    return when {
      links is Zeichenfolge &&
        rechts is Zeichenfolge -> Interpretierer.zeichenFolgenOperation(operator, links, rechts)
      links is Zahl && rechts is Zahl -> Interpretierer.zahlOperation(operator, links, rechts)
      links is germanskript.alte_pipeline.intern.Boolean && rechts is germanskript.alte_pipeline.intern.Boolean -> Interpretierer.booleanOperation(operator, links, rechts)
      else -> null
    }
  }

  override fun evaluiereMinus(minus: AST.Satz.Ausdruck.Minus): Objekt? {
    return (evaluiereAusdruck(minus.ausdruck) as Zahl?)?.let { -it }
  }

  override fun evaluiereNicht(nicht: AST.Satz.Ausdruck.Nicht): Objekt? {
    return (evaluiereAusdruck(nicht.ausdruck) as germanskript.alte_pipeline.intern.Boolean?)
        ?.let { germanskript.alte_pipeline.intern.Boolean(!it.boolean) }
  }

  override fun evaluiereKonvertierung(konvertierung: AST.Satz.Ausdruck.Konvertierung): Objekt? {
    konvertierung.ausdruck = falteKonstante(konvertierung.ausdruck)
    return null
  }

  override fun evaluiereObjektInstanziierung(instanziierung: AST.Satz.Ausdruck.ObjektInstanziierung): Objekt? {
    instanziierung.eigenschaftsZuweisungen.forEach {zuweisung -> zuweisung.ausdruck = falteKonstante(zuweisung.ausdruck)}
    return null
  }

  override fun evaluiereLambda(lambda: AST.Satz.Ausdruck.Lambda): Objekt? {
    val signatur = (lambda.schnittstelle.typ as Typ.Compound.Schnittstelle).definition.methodenSignaturen[0]
    umgebung.pushBereich()
    for (param in signatur.parameter) {
      umgebung.schreibeVariable(param.name, null, true)
    }
    durchlaufeBereich(lambda.körper, false)
    umgebung.popBereich()
    return null
  }

  // TODO: Hier müssten man noch die Methoden, Eigenschaften usw. der anonymen Klasse durchlaufen
  override fun evaluiereAnonymeKlasse(anonymeKlasse: AST.Satz.Ausdruck.AnonymeKlasse): Objekt? = null

  override fun evaluiereEigenschaftsZugriff(eigenschaftsZugriff: AST.Satz.Ausdruck.EigenschaftsZugriff): Objekt? = null

  override fun evaluiereSelbstEigenschaftsZugriff(eigenschaftsZugriff: AST.Satz.Ausdruck.SelbstEigenschaftsZugriff): Objekt? = null

  override fun evaluiereNachrichtenObjektEigenschaftsZugriff(eigenschaftsZugriff: AST.Satz.Ausdruck.NachrichtenObjektEigenschaftsZugriff): Objekt? = null

  override fun evaluiereSelbstReferenz(): Objekt? = null

  override fun evaluiereTypÜberprüfung(typÜberprüfung: AST.Satz.Ausdruck.TypÜberprüfung): Objekt? = null
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