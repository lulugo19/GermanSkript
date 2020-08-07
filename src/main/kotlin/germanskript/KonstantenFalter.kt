package germanskript

import java.io.File

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
    definierer.klassenDefinitionen.forEach(::falteKlasse)
    durchlaufeAufruf( ast.sätze, Umgebung(), true)
  }

  private fun durchlaufeAufruf(sätze: List<AST.Satz>, umgebung: Umgebung<Wert?>, neuerBereich: Boolean) {
    this.umgebung = umgebung
    durchlaufeSätze(sätze, neuerBereich)
  }

  private fun falteFunktion(funktion: AST.Definition.FunktionOderMethode.Funktion) {
    if (funktion.rückgabeTyp == null) {
      return
    }
    val funktionsUmgebung = Umgebung<Wert?>()
    funktionsUmgebung.pushBereich()
    for (parameter in funktion.parameter) {
      funktionsUmgebung.schreibeVariable(parameter.name, null, false)
    }
    durchlaufeAufruf(funktion.sätze, funktionsUmgebung, false)
  }

  private fun falteKlasse(klasse: AST.Definition.Klasse) {
    durchlaufeAufruf(klasse.sätze, Umgebung(), true)
    klasse.methoden.values.forEach {methode -> falteFunktion(methode.funktion)}
    klasse.konvertierungen.values.forEach {konvertierung ->
      durchlaufeAufruf(konvertierung.sätze, Umgebung(), true)
    }
  }

  private fun falteKonstante(originalerAusdruck: AST.Ausdruck): AST.Ausdruck {
    val konstanterWert = evaluiereAusdruck(originalerAusdruck)
    return when (konstanterWert) {
      is Wert.Zahl -> AST.Ausdruck.Zahl(TypedToken(TokenTyp.ZAHL(konstanterWert), "", "", Token.Position.Ende, Token.Position.Ende))
      is Wert.Zeichenfolge -> AST.Ausdruck.Zeichenfolge(TypedToken(TokenTyp.ZEICHENFOLGE(konstanterWert), "", "", Token.Position.Ende, Token.Position.Ende))
      is Wert.Boolean -> AST.Ausdruck.Boolean(TypedToken(TokenTyp.BOOLEAN(konstanterWert), "", "", Token.Position.Ende, Token.Position.Ende))
      else -> originalerAusdruck
    }
  }

  override fun bevorDurchlaufeMethodenBlock(methodenBlock: AST.Satz.MethodenBlock, blockObjekt: Wert?) {
    // hier muss nichts gemacht werden
  }

  override fun sollSätzeAbbrechen(): Boolean = false

  override fun durchlaufeFunktionsAufruf(funktionsAufruf: AST.Funktion, istAusdruck: Boolean): Wert? {
    funktionsAufruf.argumente.forEach {arg -> arg.wert = falteKonstante(arg.wert)}
    return  null
  }

  override fun durchlaufeVariablenDeklaration(deklaration: AST.Satz.VariablenDeklaration) {
    deklaration.ausdruck = falteKonstante(deklaration.ausdruck)
    if (deklaration.istEigenschaftsNeuZuweisung || deklaration.istEigenschaft) {
      return
    }
    else {
      val wert = evaluiereAusdruck(deklaration.ausdruck)
      if (deklaration.name.unveränderlich || deklaration.neu != null) {
        umgebung.schreibeVariable(deklaration.name, wert, !deklaration.name.unveränderlich)
      } else {
        umgebung.überschreibeVariable(deklaration.name, wert)
      }
    }
  }

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
          durchlaufeSätze(bedingung.sätze, true)
          break
        } else {
          eliminierteBedingungen.add(bedingung)
        }
      } else {
        durchlaufeSätze(bedingung.sätze, true)
      }
    }
    bedingungsSatz.bedingungen.removeAll(eliminierteBedingungen)
    val ersteBedingung = bedingungsSatz.bedingungen[0]
    if (ersteBedingung.bedingung is AST.Ausdruck.Boolean &&
        (ersteBedingung.bedingung as AST.Ausdruck.Boolean).boolean.typ.boolean.boolean) {
      val parent = bedingungsSatz.parent as IBereich
      parent.sätze[parent.sätze.indexOf(bedingungsSatz)] = AST.Satz.Bereich(ersteBedingung.sätze)
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
  }

  override fun durchlaufeFürJedeSchleife(schleife: AST.Satz.FürJedeSchleife) {
    // hier muss nichts gemacht werden
  }

  override fun durchlaufeIntern() {
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
      links is Wert.Zeichenfolge && rechts is Wert.Zeichenfolge -> Interpretierer.zeichenFolgenOperation(operator, links, rechts)
      links is Wert.Zahl && rechts is Wert.Zahl -> Interpretierer.zahlOperation(operator, links, rechts)
      links is Wert.Boolean && rechts is Wert.Boolean -> Interpretierer.booleanOperation(operator, links, rechts)
      else -> null
    }
  }

  override fun evaluiereMinus(minus: AST.Ausdruck.Minus): Wert? {
    val zahl = evaluiereAusdruck(minus.ausdruck) as Wert.Zahl?
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
    instanziierung.eigenschaftsZuweisungen.forEach {zuweisung -> zuweisung.wert = falteKonstante(zuweisung.wert)}
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
  falter.ast.sätze.forEach(::println)
  file.delete()
}