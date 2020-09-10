package germanskript

import java.io.File
import java.lang.Exception

abstract  class ProgrammDurchlaufer<T>(startDatei: File): PipelineKomponente(startDatei ) {
  abstract val definierer: Definierer

  protected abstract val nichts: T
  protected abstract val umgebung: Umgebung<T>
  protected var inSuperBlock = false
    private set

  // region Sätze
  protected fun durchlaufeBereich(bereich: AST.Satz.Bereich, neuerBereich: Boolean): T  {
    if (neuerBereich) {
      umgebung.pushBereich()
    }
    starteBereich(bereich)
    var rückgabe = nichts
    for (satz in bereich.sätze) {
      if (sollSätzeAbbrechen()) {
        return nichts
      }
      @Suppress("IMPLICIT_CAST_TO_ANY")
      rückgabe = when (satz) {
        is AST.Satz.VariablenDeklaration -> durchlaufeVariablenDeklaration(satz)
        is AST.Satz.FunktionsAufruf -> durchlaufeFunktionsAufruf(satz.aufruf, false)
        is AST.Satz.Bereich -> durchlaufeBereich(satz, true)
        is AST.Satz.MethodenBereich -> durchlaufeMethodenBereich(satz.methodenBereich)
        is AST.Satz.SuperBlock -> {
          val prevInSuperBlock = inSuperBlock
          inSuperBlock = true
          durchlaufeBereich(satz.bereich, true).also {
            inSuperBlock = prevInSuperBlock
          }
        }
        is AST.Satz.Zurückgabe -> durchlaufeZurückgabe(satz)
        is AST.Satz.Bedingung -> durchlaufeBedingungsSatz(satz)
        is AST.Satz.SolangeSchleife -> durchlaufeSolangeSchleife(satz)
        is AST.Satz.FürJedeSchleife -> durchlaufeFürJedeSchleife(satz)
        is AST.Satz.VersucheFange -> durchlaufeVersucheFange(satz)
        is AST.Satz.Werfe -> durchlaufeWerfe(satz)
        is AST.Satz.SchleifenKontrolle.Abbrechen -> durchlaufeAbbrechen()
        is AST.Satz.SchleifenKontrolle.Fortfahren -> durchlaufeFortfahren()
        is AST.Satz.Intern -> durchlaufeIntern(satz)
        else -> throw Exception("Dieser Fall sollte nie eintreten!")
      }.let { if (it is Unit) nichts else it as T }
    }
    beendeBereich(bereich)
    if (neuerBereich) {
      umgebung.popBereich()
    }
    return rückgabe
  }

  protected fun holeErstesTokenVonAusdruck(ausdruck: AST.Ausdruck): Token {
    return when (ausdruck) {
      is AST.Ausdruck.Zeichenfolge -> ausdruck.zeichenfolge.toUntyped()
      is AST.Ausdruck.Liste -> ausdruck.pluralTyp.name.vornomen!!.toUntyped()
      is AST.Ausdruck.Zahl -> ausdruck.zahl.toUntyped()
      is AST.Ausdruck.Boolean -> ausdruck.boolean.toUntyped()
      is AST.Ausdruck.Variable -> ausdruck.name.bezeichner.toUntyped()
      is AST.Ausdruck.FunktionsAufruf -> ausdruck.aufruf.verb.toUntyped()
      is AST.Ausdruck.ListenElement -> ausdruck.singular.vornomen!!.toUntyped()
      is AST.Ausdruck.BinärerAusdruck -> holeErstesTokenVonAusdruck(ausdruck.links)
      is AST.Ausdruck.Minus -> holeErstesTokenVonAusdruck(ausdruck.ausdruck)
      is AST.Ausdruck.Konvertierung -> holeErstesTokenVonAusdruck(ausdruck.ausdruck)
      is AST.Ausdruck.ObjektInstanziierung -> ausdruck.klasse.name.bezeichnerToken
      is AST.Ausdruck.EigenschaftsZugriff -> ausdruck.eigenschaftsName.bezeichner.toUntyped()
      is AST.Ausdruck.SelbstEigenschaftsZugriff -> ausdruck.eigenschaftsName.bezeichner.toUntyped()
      is AST.Ausdruck.MethodenBereichEigenschaftsZugriff -> ausdruck.eigenschaftsName.bezeichner.toUntyped()
      is AST.Ausdruck.SelbstReferenz -> ausdruck.ich.toUntyped()
      is AST.Ausdruck.MethodenBereichReferenz -> ausdruck.du.toUntyped()
      is AST.Ausdruck.Closure -> ausdruck.schnittstelle.name.bezeichnerToken
      is AST.Ausdruck.Konstante -> ausdruck.name.toUntyped()
      is AST.Ausdruck.MethodenBereich -> ausdruck.methodenBereich.name.bezeichnerToken
      is AST.Ausdruck.Nichts -> ausdruck.nichts.toUntyped()
    }
  }

  private fun durchlaufeMethodenBereich(methodenBereich: AST.MethodenBereich): T{
    val wert = evaluiereVariable(methodenBereich.name)
    bevorDurchlaufeMethodenBereich(methodenBereich, wert)
    umgebung.pushBereich(wert)
    return durchlaufeBereich(methodenBereich.bereich, false).also { umgebung.popBereich() }
  }

  protected abstract fun bevorDurchlaufeMethodenBereich(methodenBereich: AST.MethodenBereich, blockObjekt: T?)
  protected abstract fun sollSätzeAbbrechen(): Boolean
  protected abstract fun durchlaufeFunktionsAufruf(funktionsAufruf: AST.FunktionsAufruf, istAusdruck: Boolean): T
  protected abstract fun durchlaufeVariablenDeklaration(deklaration: AST.Satz.VariablenDeklaration): T
  protected abstract fun durchlaufeZurückgabe(zurückgabe: AST.Satz.Zurückgabe): T
  protected abstract fun durchlaufeBedingungsSatz(bedingungsSatz: AST.Satz.Bedingung)
  protected abstract fun durchlaufeAbbrechen()
  protected abstract fun durchlaufeFortfahren()
  protected abstract fun durchlaufeSolangeSchleife(schleife: AST.Satz.SolangeSchleife)
  protected abstract fun durchlaufeFürJedeSchleife(schleife: AST.Satz.FürJedeSchleife)
  protected abstract fun durchlaufeVersucheFange(versucheFange: AST.Satz.VersucheFange)
  abstract fun durchlaufeWerfe(werfe: AST.Satz.Werfe): T
  protected abstract fun durchlaufeIntern(intern: AST.Satz.Intern): T
  protected abstract fun starteBereich(bereich: AST.Satz.Bereich)
  protected abstract fun beendeBereich(bereich: AST.Satz.Bereich)

  // endregion
  protected fun evaluiereAusdruck(ausdruck: AST.Ausdruck): T {
    return when (ausdruck) {
      is AST.Ausdruck.Zeichenfolge -> evaluiereZeichenfolge(ausdruck)
      is AST.Ausdruck.Zahl -> evaluiereZahl(ausdruck)
      is AST.Ausdruck.Boolean -> evaluiereBoolean(ausdruck)
      is AST.Ausdruck.Variable -> evaluiereVariable(ausdruck)
      is AST.Ausdruck.Liste -> evaluiereListe(ausdruck)
      is AST.Ausdruck.ListenElement -> evaluiereListenElement(ausdruck)
      is AST.Ausdruck.FunktionsAufruf -> durchlaufeFunktionsAufruf(ausdruck.aufruf, true)!!
      is AST.Ausdruck.BinärerAusdruck -> evaluiereBinärenAusdruck(ausdruck)
      is AST.Ausdruck.Minus -> evaluiereMinus(ausdruck)
      is AST.Ausdruck.Konvertierung -> evaluiereKonvertierung(ausdruck)
      is AST.Ausdruck.ObjektInstanziierung -> evaluiereObjektInstanziierung(ausdruck)
      is AST.Ausdruck.EigenschaftsZugriff -> evaluiereEigenschaftsZugriff(ausdruck)
      is AST.Ausdruck.SelbstEigenschaftsZugriff -> evaluiereSelbstEigenschaftsZugriff(ausdruck)
      is AST.Ausdruck.MethodenBereichEigenschaftsZugriff -> evaluiereMethodenBlockEigenschaftsZugriff(ausdruck)
      is AST.Ausdruck.SelbstReferenz -> evaluiereSelbstReferenz()
      is AST.Ausdruck.MethodenBereichReferenz -> evaluiereMethodenBlockReferenz()
      is AST.Ausdruck.Closure -> evaluiereClosure(ausdruck)
      is AST.Ausdruck.Konstante -> evaluiereKonstante(ausdruck)
      is AST.Ausdruck.MethodenBereich -> durchlaufeMethodenBereich(ausdruck.methodenBereich)
      is AST.Ausdruck.Nichts -> nichts
    }
  }

  protected open fun evaluiereVariable(variable: AST.Ausdruck.Variable): T {
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

  private fun evaluiereMethodenBlockReferenz(): T {
    return umgebung.holeMethodenBlockObjekt()!!
  }

  protected abstract fun evaluiereZeichenfolge(ausdruck: AST.Ausdruck.Zeichenfolge): T
  protected abstract fun evaluiereZahl(ausdruck: AST.Ausdruck.Zahl): T
  protected abstract fun evaluiereBoolean(ausdruck: AST.Ausdruck.Boolean): T
  protected abstract fun evaluiereListe(ausdruck: AST.Ausdruck.Liste): T
  protected abstract fun evaluiereListenElement(listenElement: AST.Ausdruck.ListenElement): T
  protected abstract fun evaluiereBinärenAusdruck(ausdruck: AST.Ausdruck.BinärerAusdruck): T
  protected abstract fun evaluiereMinus(minus: AST.Ausdruck.Minus): T
  protected abstract fun evaluiereKonvertierung(konvertierung: AST.Ausdruck.Konvertierung): T
  protected abstract fun evaluiereObjektInstanziierung(instanziierung: AST.Ausdruck.ObjektInstanziierung): T
  protected abstract fun evaluiereEigenschaftsZugriff(eigenschaftsZugriff: AST.Ausdruck.EigenschaftsZugriff): T
  protected abstract fun evaluiereSelbstEigenschaftsZugriff(eigenschaftsZugriff: AST.Ausdruck.SelbstEigenschaftsZugriff): T
  protected abstract fun evaluiereMethodenBlockEigenschaftsZugriff(eigenschaftsZugriff: AST.Ausdruck.MethodenBereichEigenschaftsZugriff): T
  protected abstract fun evaluiereSelbstReferenz(): T
  protected abstract fun evaluiereClosure(closure: AST.Ausdruck.Closure): T
  protected abstract fun evaluiereKonstante(konstante: AST.Ausdruck.Konstante): T
}