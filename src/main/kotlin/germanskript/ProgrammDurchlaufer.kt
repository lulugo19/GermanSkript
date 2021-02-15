package germanskript

import java.io.File
import java.lang.Exception

/**
 * Eine abstrakte Klasse, die dafür gedacht war, eine gleiche Programmstruktur für
 * den Typprüfer, sowie den Interpreter zu erhalten. Beide durchlaufen nämlich in ähnlicher Art und Weise
 * den AST.
 *
 * Zuerst hat es sich als gute Idee rausgestellt, aber dann wurden Typprüfer und Interpreter
 * zu unterschiedlich, sodass für Typprüfer und Interpreter unnötiger Code ausgeführt werden musste
 * um die gleiche Struktur sicherzuestellen.
 */
abstract  class ProgrammDurchlaufer<T>(startDatei: File): PipelineKomponente(startDatei ) {
  abstract val definierer: Definierer

  protected abstract val nichts: T
  protected abstract val niemals: T
  protected abstract val umgebung: Umgebung<T>
  protected var inSuperBereich = false
    private set

  // region Sätze
  protected fun durchlaufeBereich(bereich: AST.Satz.Bereich, neuerBereich: Boolean): T  {
    if (neuerBereich) {
      umgebung.pushBereich()
    }
    starteBereich(bereich)
    var rückgabe = nichts
    for (satz in bereich.sätze) {
      if (sollteStackAufrollen()) {
        return niemals
      }
      if (sollteAbbrechen()) {
        return nichts
      }
      @Suppress("IMPLICIT_CAST_TO_ANY")
      rückgabe = when (satz) {
        is AST.Satz.VariablenDeklaration -> durchlaufeVariablenDeklaration(satz)
        is AST.Satz.IndexZuweisung -> durchlaufeIndexZuweisung(satz)
        is AST.Satz.Ausdruck.FunktionsAufruf -> durchlaufeFunktionsAufruf(satz, false)
        is AST.Satz.Bereich -> durchlaufeBereich(satz, true)
        is AST.Satz.Zurückgabe -> durchlaufeZurückgabe(satz)
        is AST.Satz.Ausdruck.Bedingung -> durchlaufeBedingungsSatz(satz, false)
        is AST.Satz.SolangeSchleife -> durchlaufeSolangeSchleife(satz)
        is AST.Satz.FürJedeSchleife -> durchlaufeFürJedeSchleife(satz)
        is AST.Satz.Ausdruck.VersucheFange -> durchlaufeVersucheFange(satz)
        is AST.Satz.Ausdruck.Werfe -> durchlaufeWerfe(satz)
        is AST.Satz.SchleifenKontrolle.Abbrechen -> durchlaufeAbbrechen()
        is AST.Satz.SchleifenKontrolle.Fortfahren -> durchlaufeFortfahren()
        is AST.Satz.Intern -> durchlaufeIntern(satz)
        is AST.Satz.Ausdruck -> evaluiereAusdruck(satz)
      }.let {
        @Suppress("UNCHECKED_CAST")
        if (it is Unit) nichts else it as T
      }
    }
    beendeBereich(bereich)
    if (neuerBereich) {
      umgebung.popBereich()
    }
    return rückgabe
  }

  private fun durchlaufeNachrichtenBereich(nachrichtenBereich: AST.Satz.Ausdruck.NachrichtenBereich): T {
    val wert = evaluiereAusdruck(nachrichtenBereich.objekt)
    bevorDurchlaufeNachrichtenBereich(nachrichtenBereich, wert)
    umgebung.pushBereich(wert)
    return durchlaufeBereich(nachrichtenBereich.bereich, false).also { umgebung.popBereich() }
  }

  protected abstract fun bevorDurchlaufeNachrichtenBereich(nachrichtenBereich: AST.Satz.Ausdruck.NachrichtenBereich, blockObjekt: T?)
  protected abstract fun sollteAbbrechen(): Boolean
  protected abstract fun sollteStackAufrollen(): Boolean
  protected abstract fun durchlaufeFunktionsAufruf(funktionsAufruf: AST.Satz.Ausdruck.FunktionsAufruf, istAusdruck: Boolean): T
  protected abstract fun durchlaufeVariablenDeklaration(deklaration: AST.Satz.VariablenDeklaration)
  protected abstract fun durchlaufeIndexZuweisung(zuweisung: AST.Satz.IndexZuweisung)
  protected abstract fun durchlaufeZurückgabe(zurückgabe: AST.Satz.Zurückgabe): T
  protected abstract fun durchlaufeBedingungsSatz(bedingungsSatz: AST.Satz.Ausdruck.Bedingung, istAusdruck: Boolean): T
  protected abstract fun durchlaufeAbbrechen()
  protected abstract fun durchlaufeFortfahren()
  protected abstract fun durchlaufeSolangeSchleife(schleife: AST.Satz.SolangeSchleife)
  protected abstract fun durchlaufeFürJedeSchleife(schleife: AST.Satz.FürJedeSchleife)
  protected abstract fun durchlaufeVersucheFange(versucheFange: AST.Satz.Ausdruck.VersucheFange): T
  abstract fun durchlaufeWerfe(werfe: AST.Satz.Ausdruck.Werfe): T
  protected abstract fun durchlaufeIntern(intern: AST.Satz.Intern): T
  protected abstract fun starteBereich(bereich: AST.Satz.Bereich)
  protected abstract fun beendeBereich(bereich: AST.Satz.Bereich)

  // endregion
  protected fun evaluiereAusdruck(ausdruck: AST.Satz.Ausdruck): T {
    if (sollteStackAufrollen()) {
      return niemals
    }
    return when (ausdruck) {
      is AST.Satz.Ausdruck.Zeichenfolge -> evaluiereZeichenfolge(ausdruck)
      is AST.Satz.Ausdruck.Zahl -> evaluiereZahl(ausdruck)
      is AST.Satz.Ausdruck.Boolean -> evaluiereBoolean(ausdruck)
      is AST.Satz.Ausdruck.Variable -> evaluiereVariable(ausdruck)
      is AST.Satz.Ausdruck.Liste -> evaluiereListe(ausdruck)
      is AST.Satz.Ausdruck.IndexZugriff -> evaluiereIndexZugriff(ausdruck)
      is AST.Satz.Ausdruck.FunktionsAufruf -> durchlaufeFunktionsAufruf(ausdruck, true)!!
      is AST.Satz.Ausdruck.BinärerAusdruck -> evaluiereBinärenAusdruck(ausdruck)
      is AST.Satz.Ausdruck.Minus -> evaluiereMinus(ausdruck)
      is AST.Satz.Ausdruck.Nicht -> evaluiereNicht(ausdruck)
      is AST.Satz.Ausdruck.Konvertierung -> evaluiereKonvertierung(ausdruck)
      is AST.Satz.Ausdruck.ObjektInstanziierung -> evaluiereObjektInstanziierung(ausdruck)
      is AST.Satz.Ausdruck.EigenschaftsZugriff -> evaluiereEigenschaftsZugriff(ausdruck)
      is AST.Satz.Ausdruck.SelbstEigenschaftsZugriff -> evaluiereSelbstEigenschaftsZugriff(ausdruck)
      is AST.Satz.Ausdruck.NachrichtenObjektEigenschaftsZugriff -> evaluiereNachrichtenObjektEigenschaftsZugriff(ausdruck)
      is AST.Satz.Ausdruck.SelbstReferenz -> evaluiereSelbstReferenz()
      is AST.Satz.Ausdruck.NachrichtenObjektReferenz -> evaluiereNachrichtenObjektReferenz()
      is AST.Satz.Ausdruck.Lambda -> evaluiereLambda(ausdruck)
      is AST.Satz.Ausdruck.AnonymeKlasse -> evaluiereAnonymeKlasse(ausdruck)
      is AST.Satz.Ausdruck.Konstante -> evaluiereKonstante(ausdruck)
      is AST.Satz.Ausdruck.TypÜberprüfung -> evaluiereTypÜberprüfung(ausdruck)
      is AST.Satz.Ausdruck.NachrichtenBereich -> durchlaufeNachrichtenBereich(ausdruck)
      is AST.Satz.Ausdruck.SuperBereich -> {
        val prevInSuperBlock = inSuperBereich
        inSuperBereich = true
        durchlaufeBereich(ausdruck.bereich, true).also {
          inSuperBereich = prevInSuperBlock
        }
      }
      is AST.Satz.Ausdruck.Bedingung -> durchlaufeBedingungsSatz(ausdruck, true)
      is AST.Satz.Ausdruck.Nichts -> nichts
      is AST.Satz.Ausdruck.VersucheFange -> durchlaufeVersucheFange(ausdruck)
      is AST.Satz.Ausdruck.Werfe -> durchlaufeWerfe(ausdruck)
    }
  }

  protected open fun evaluiereVariable(variable: AST.Satz.Ausdruck.Variable): T {
    return if (variable.konstante != null) {
      evaluiereAusdruck(variable.konstante!!.wert!!)
    } else {
      return this.evaluiereVariable(variable.name)
    }
  }

  protected open fun evaluiereVariable(name: AST.WortArt.Nomen): T {
    return umgebung.leseVariable(name).wert
  }

  protected open fun evaluiereVariable(variable: String): T? {
    return umgebung.leseVariable(variable)?.wert
  }

  private fun evaluiereNachrichtenObjektReferenz(): T {
    return umgebung.holeNachrichtenBereichObjekt()!!
  }

  protected abstract fun evaluiereZeichenfolge(ausdruck: AST.Satz.Ausdruck.Zeichenfolge): T
  protected abstract fun evaluiereZahl(ausdruck: AST.Satz.Ausdruck.Zahl): T
  protected abstract fun evaluiereBoolean(ausdruck: AST.Satz.Ausdruck.Boolean): T
  protected abstract fun evaluiereListe(ausdruck: AST.Satz.Ausdruck.Liste): T
  protected abstract fun evaluiereIndexZugriff(indexZugriff: AST.Satz.Ausdruck.IndexZugriff): T
  protected abstract fun evaluiereBinärenAusdruck(ausdruck: AST.Satz.Ausdruck.BinärerAusdruck): T
  protected abstract fun evaluiereMinus(minus: AST.Satz.Ausdruck.Minus): T
  abstract fun evaluiereNicht(nicht: AST.Satz.Ausdruck.Nicht): T
  protected abstract fun evaluiereKonvertierung(konvertierung: AST.Satz.Ausdruck.Konvertierung): T
  protected abstract fun evaluiereObjektInstanziierung(instanziierung: AST.Satz.Ausdruck.ObjektInstanziierung): T
  protected abstract fun evaluiereEigenschaftsZugriff(eigenschaftsZugriff: AST.Satz.Ausdruck.EigenschaftsZugriff): T
  protected abstract fun evaluiereSelbstEigenschaftsZugriff(eigenschaftsZugriff: AST.Satz.Ausdruck.SelbstEigenschaftsZugriff): T
  protected abstract fun evaluiereNachrichtenObjektEigenschaftsZugriff(eigenschaftsZugriff: AST.Satz.Ausdruck.NachrichtenObjektEigenschaftsZugriff): T
  protected abstract fun evaluiereSelbstReferenz(): T
  protected abstract fun evaluiereLambda(lambda: AST.Satz.Ausdruck.Lambda): T
  protected abstract fun evaluiereAnonymeKlasse(anonymeKlasse: AST.Satz.Ausdruck.AnonymeKlasse): T
  protected abstract fun evaluiereKonstante(konstante: AST.Satz.Ausdruck.Konstante): T
  protected abstract fun evaluiereTypÜberprüfung(typÜberprüfung: AST.Satz.Ausdruck.TypÜberprüfung): T
}