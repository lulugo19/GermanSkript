import java.util.*

abstract  class ProgrammDurchlaufer<T>(dateiPfad: String): PipelineKomponente(dateiPfad ) {
  protected val stack = Stack<Umgebung<T>>()
  abstract val ast: AST.Programm


  protected fun durchlaufe(sätze: List<AST.Satz>, umgebung: Umgebung<T>, clearStack: Boolean) {
    if (clearStack) {
      stack.clear()
    }
    stack.push(umgebung)
    durchlaufeSätze(sätze, umgebung.istLeer)
  }

  // region Sätze
  protected fun durchlaufeSätze(sätze: List<AST.Satz>, neuerBereich: Boolean)  {
    if (neuerBereich) {
      stack.peek().pushBereich()
    }
    for (satz in sätze) {
      if (sollSätzeAbbrechen()) {
        return
      }
      when (satz) {
        is AST.Satz.VariablenDeklaration -> durchlaufeVariablenDeklaration(satz)
        is AST.Satz.FunktionsAufruf -> {
          durchlaufeFunktionsAufruf(satz.aufruf, false)
        }
        is AST.Satz.Zurückgabe -> durchlaufeZurückgabe(satz)
        is AST.Satz.Bedingung -> durchlaufeBedingungsSatz(satz)
        is AST.Satz.SolangeSchleife -> durchlaufeSolangeSchleife(satz)
        is AST.Satz.FürJedeSchleife -> durchlaufeFürJedeSchleife(satz)
        is AST.Satz.SchleifenKontrolle.Abbrechen -> durchlaufeAbbrechen()
        is AST.Satz.SchleifenKontrolle.Fortfahren -> durchlaufeFortfahren()
      }
    }
    if (neuerBereich) {
      stack.peek().popBereich()
    }
  }

  protected fun holeErstesTokenVonAusdruck(ausdruck: AST.Ausdruck): Token {
    return when (ausdruck) {
      is AST.Ausdruck.Zeichenfolge -> ausdruck.zeichenfolge.toUntyped()
      is AST.Ausdruck.Liste -> ausdruck.pluralTyp.vornomen!!.toUntyped()
      is AST.Ausdruck.Zahl -> ausdruck.zahl.toUntyped()
      is AST.Ausdruck.Boolean -> ausdruck.boolean.toUntyped()
      is AST.Ausdruck.Variable -> ausdruck.name.bezeichner.toUntyped()
      is AST.Ausdruck.FunktionsAufruf -> ausdruck.aufruf.verb.toUntyped()
      is AST.Ausdruck.ListenElement -> ausdruck.singular.vornomen!!.toUntyped()
      is AST.Ausdruck.BinärerAusdruck -> holeErstesTokenVonAusdruck(ausdruck.links)
      is AST.Ausdruck.Minus -> holeErstesTokenVonAusdruck(ausdruck.ausdruck)
      is AST.Ausdruck.Konvertierung -> holeErstesTokenVonAusdruck(ausdruck.ausdruck)
    }
  }

  private fun durchlaufeVariablenDeklaration(deklaration: AST.Satz.VariablenDeklaration) {
    val wert = evaluiereAusdruck(deklaration.ausdruck)
    if (deklaration.name.vornomen!!.typ is TokenTyp.VORNOMEN.ARTIKEL_BESTIMMT) {
      stack.peek().schreibeVariable(deklaration.name, wert)
    } else {
      stack.peek().überschreibeVariable(deklaration.name, wert)
    }
  }

  protected abstract fun sollSätzeAbbrechen(): Boolean
  protected abstract fun durchlaufeFunktionsAufruf(funktionsAufruf: AST.FunktionsAufruf, istAusdruck: Boolean): T?
  protected abstract fun durchlaufeZurückgabe(zurückgabe: AST.Satz.Zurückgabe)
  protected abstract fun durchlaufeBedingungsSatz(bedingungsSatz: AST.Satz.Bedingung)
  protected abstract fun durchlaufeAbbrechen()
  protected abstract fun durchlaufeFortfahren()
  protected abstract fun durchlaufeSolangeSchleife(schleife: AST.Satz.SolangeSchleife)
  protected abstract fun durchlaufeFürJedeSchleife(schleife: AST.Satz.FürJedeSchleife)

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
    }
  }

  fun evaluiereVariable(variable: AST.Ausdruck.Variable): T {
    return stack.peek().leseVariable(variable.name)
  }

  fun evaluiereVariable(variable: String): T? {
    return stack.peek().leseVariable(variable)
  }

  protected abstract fun evaluiereZeichenfolge(ausdruck: AST.Ausdruck.Zeichenfolge): T
  protected abstract fun evaluiereZahl(ausdruck: AST.Ausdruck.Zahl): T
  protected abstract fun evaluiereBoolean(ausdruck: AST.Ausdruck.Boolean): T
  protected abstract fun evaluiereListe(ausdruck: AST.Ausdruck.Liste): T
  protected abstract fun evaluiereListenElement(listenElement: AST.Ausdruck.ListenElement): T
  protected abstract fun evaluiereBinärenAusdruck(ausdruck: AST.Ausdruck.BinärerAusdruck): T
  protected abstract fun evaluiereMinus(minus: AST.Ausdruck.Minus): T
  protected abstract fun evaluiereKonvertierung(konvertierung: AST.Ausdruck.Konvertierung): T
}