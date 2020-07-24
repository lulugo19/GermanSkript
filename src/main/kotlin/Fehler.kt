sealed class GermanSkriptFehler(private val fehlerName: String, val token: Token): Exception() {
  abstract val nachricht: String

  override val message: String?
    get() {
      val vorspann = "$fehlerName in ${token.position}: "
      return vorspann + "\n" + nachricht.lines().joinToString("\n") {
        line -> "\t" + line
      }
    }

  sealed class SyntaxFehler(token: Token): GermanSkriptFehler("Syntaxfehler", token) {
    class LexerFehler(token: Token, val details: String? = null): SyntaxFehler(token) {
      override val nachricht: String
        get() = "Ungültige Zeichenfolge '${token.wert}'. ${details?: ""}"
    }


    class UngültigeEscapeSequenz(token: Token): SyntaxFehler(token) {
      override val nachricht: String = "Ungültige Escape Sequenz '${token.wert}' in Zeichenfolge."
    }

    class ParseFehler(token: Token, private val erwartet: String? = null, private val details: String? = null): SyntaxFehler(token) {
      override val nachricht: String
        get() {
          var msg = "Unerwartetes Token '${token.wert}'."
          if (erwartet != null) {
            msg += " $erwartet Erwartet."
          }
          if (details != null) {
            msg += " $details"
          }
          return msg
        }
    }

    class UngültigerBereich(token: Token, override val nachricht: String): SyntaxFehler(token)

    class FunktionAlsAusdruckFehler(token: Token): SyntaxFehler(token){
      override val nachricht: String
        get() = "Die Funktion '${token.wert}' kann nicht als Ausdruck verwendet werden, da sie keinen Rückgabetyp besitzt."
    }

    class AnzahlDerParameterFehler(token: Token): SyntaxFehler(token){
      override val nachricht: String
        get() = "Die Anzahl der Parameter und Argumente der Funktion '${token.wert}' stimmen nicht überein."
    }
  }

  sealed class RückgabeFehler(token: Token): GermanSkriptFehler("Rückgabefehler", token) {
    class UngültigeRückgabe(token: Token): RückgabeFehler(token) {
      override val nachricht: String
        get() = "Ungültige Rückgabe. Der Aufruf gibt nichts zurück und eine Rückgabe ist hier nicht erlaubt."
    }

    class RückgabeVergessen(token: Token, private val rückgabeTyp: Typ): RückgabeFehler(token) {
      override val nachricht: String
        get() = "Es wird eine Rückgabe vom Typ '${rückgabeTyp.name}' erwartet."
    }
  }

  sealed class DudenFehler(token: Token, protected val wort: String): GermanSkriptFehler("Dudenfehler", token) {
    class WortNichtGefundenFehler(token: Token, wort: String) : DudenFehler(token, wort) {
      override val nachricht: String
        get() = "Das Wort '$wort' konnte im Duden nicht gefunden werden."
    }

    class Verbindungsfehler(token: Token, wort: String) : DudenFehler(token, wort) {
      override val nachricht: String
        get() = "Das Wort '$wort' konnte nicht im Duden nachgeschaut werden, da keine Netzwerk-Verbindung zum Online-Duden besteht."
    }
  }

  class UnbekanntesWort(token: Token, private val hauptWort: String): GermanSkriptFehler("Unbekanntes Wort", token) {
    override val nachricht: String
      get() = "Das Wort '$hauptWort' ist unbekannt. Füge eine Deklinationsanweisung für das Wort '$hauptWort' hinzu!"
  }

  sealed class ImportFehler(token: Token, protected val dateiPfad: String): GermanSkriptFehler("Importfehler", token) {
    class DateiNichtGefunden(token: Token, dateiPfad: String): ImportFehler(token, dateiPfad) {
      override val nachricht: String
        get() = "Die Datei '$dateiPfad' konnte nicht gefunden werden."
    }

    class ZyklischeImports(token: Token, dateiPfad: String): ImportFehler(token, dateiPfad) {
      override val nachricht: String
        get() = "Zyklischer Import! Die Datei '$dateiPfad' wurde bereits vorher schon importiert."
    }
  }



  sealed class GrammatikFehler(token: Token): GermanSkriptFehler("Grammatikfehler",token) {

    sealed class FormFehler(token: Token, protected val kasus: Kasus, protected val nomen: AST.Nomen): GrammatikFehler(token) {
      val form get() = "(${kasus.anzeigeName}, ${nomen.genus.anzeigeName}, ${nomen.numerus!!.anzeigeName})"

      class FalschesVornomen(token: Token, kasus: Kasus, nomen: AST.Nomen, private val richtigesVornomen: String) : FormFehler(token, kasus, nomen) {
        override val nachricht: String
          get() = "Falsches Vornomen (${token.typ.anzeigeName}) '${token.wert} ${nomen.bezeichner.wert}'.\n" +
              "Das richtige Vornomen $form ist '$richtigesVornomen ${nomen.bezeichner.wert}'."
      }

      class FalschesNomen(token: Token, kasus: Kasus, nomen: AST.Nomen, private val richtigeForm: String) : FormFehler(token, kasus, nomen) {
        override val nachricht: String
          get() = "Falsche Form des Nomens '${token.wert}'. Die richtige Form $form ist '$richtigeForm'."
      }
    }

    class FalscheZuweisung(token: Token, private val numerus: Numerus) : GrammatikFehler(token) {
      override val nachricht: String
        get() = "Falsche Zuweisung '${token.wert}'. Die richtige Form der Zuweisung im ${numerus.anzeigeName} ist '${numerus.zuweisung}'." +
            "Statt dem Wort kann auch das Symbol '=' verwendet werden."
    }

    class FalscherNumerus(token: Token, private  val numerus: Numerus, private val erwartetesNomen: String): GrammatikFehler(token) {
      override val nachricht: String
        get() = "Falscher Numerus. Das Nomen muss im ${numerus.anzeigeName} '$erwartetesNomen' stehen."
    }

    class PluralErwartet(token: Token): GrammatikFehler(token) {
      override val nachricht: String
        get() = "Es wird ein Ausdruck im Plural erwartet. Doch der Ausdruck '${token.wert}' ist im Singular."
    }
  }

  sealed class DoppelteDefinition(token: Token): GermanSkriptFehler("Definitionsfehler", token) {
    class Funktion(token: Token, private val definition: AST.Definition.FunktionOderMethode.Funktion): DoppelteDefinition(token) {
      override val nachricht: String
        get() = "Die Funktion '${definition.vollerName}' ist schon in ${definition.name.position} definiert."
    }

    class Methode(token: Token, private val definition: AST.Definition.FunktionOderMethode.Methode): DoppelteDefinition(token) {
      override val nachricht: String
        get() = "Die Methode '${definition.funktion.vollerName}' für die Klasse '${definition.klasse.name.nominativ}' ist schon in ${definition.funktion.name.position} definiert."
    }

    class Klasse(token: Token, private val definition: AST.Definition.Klasse): DoppelteDefinition(token) {
      override val nachricht: String
        get() = "Die Klasse '${token.wert}' ist schon in ${definition.typ.name.bezeichner.position} definiert."
    }

    class Konvertierung(token: Token, private val konvertierung: AST.Definition.Konvertierung): DoppelteDefinition(token) {
      override val nachricht: String
        get() = "Die Konvertierung von '${konvertierung.klasse.nominativ}' zu '${konvertierung.typ.name.nominativ}'\n" +
            "ist schon in ${konvertierung.klasse.bezeichner.position} definiert."
    }
  }

  class Variablenfehler(token: Token, deklaration: AST.Nomen): GermanSkriptFehler("VariablenFehler", token) {
    override val nachricht = "Die Variable '${token.wert}' ist schon in ${deklaration.bezeichner.position} deklariert und kann nicht erneut deklariert werden."
  }

  class ReservierterTypName(token: Token): GermanSkriptFehler("Reservierter Typname", token) {
    override val nachricht: String
      get() = "Der Name '${token.wert}' kann nicht als Klassenname verwendet werden, da dieser ein reservierter Typname ist."
  }


  sealed class Undefiniert(token: Token): GermanSkriptFehler("Undefiniert Fehler", token) {
    class Funktion(token: Token, private val funktionsAufruf: AST.Funktion): Undefiniert(token) {
      override val nachricht: String
        get() = "Die Funktion '${funktionsAufruf.vollerName!!}' ist nicht definiert."
    }

    class Methode(token: Token, private val methodenAufruf: AST.Funktion, private val klassenName: String): Undefiniert(token) {
      override val nachricht: String
        get() = "Die Methode 'für $klassenName: ${methodenAufruf.vollerName!!}' ist nicht definiert."
    }

    class Variable(token: Token, private val name: String = token.wert): Undefiniert(token) {
      override val nachricht: String
        get() = "Die Variable '$name' ist nicht definiert.'"
    }

    class Typ(token: Token): Undefiniert(token) {
      override val nachricht: String
        get() = "Der Typ '${token.wert}' ist nicht definiert."
    }

    class Operator(token: Token, private val typ: String): Undefiniert(token){
      override val nachricht: String
        get() = "Der Operator '${token.wert}' ist für den Typen '$typ' nicht definiert."
    }

    class Eigenschaft(token: Token, private val klasse: String): Undefiniert(token) {
      override val nachricht: String
        get() = "Die Eigenschaft '${token.wert}' ist für die Klasse '$klasse' nicht definiert."
    }
  }

  sealed class TypFehler(token: Token): GermanSkriptFehler("Typfehler", token) {
    class FalscherTyp(token: Token, private val falscherTyp: Typ, private val erwarteterTyp: String): TypFehler(token) {
      override val nachricht: String
        get() = "Falscher Typ '${falscherTyp.name}'. Erwartet wird der Typ '$erwarteterTyp'."
    }

    class ObjektErwartet(token: Token): TypFehler(token) {
      override val nachricht: String
        get() = "Es wird ein Objekt erwartet und kein primitiver Wert (Zahl, Zeichenfolge, Boolean)."
    }
  }

  sealed class EigenschaftsFehler(token: Token): GermanSkriptFehler("Eigenschaftsfehler", token) {
    class UnerwarteterEigenschaftsName(token: Token, private val erwarteteEigenschaft: String): EigenschaftsFehler(token) {
      override val nachricht: String
        get() = "Unerwartete Eigenschaft '${token.wert}'. Es wird die Eigenschaft '$erwarteteEigenschaft' erwartet."

    }

    class EigenschaftsVergessen(token: Token, private val erwarteteEigenschaft: String): EigenschaftsFehler(token) {
      override val nachricht: String
        get() = "Es wird die Eigenschaft '$erwarteteEigenschaft' erwartet. Doch sie wurde vergessen."

    }

    class UnerwarteteEigenschaft(token: Token): EigenschaftsFehler(token) {
      override val nachricht: String
        get() = "Unerwartetes Eigenschaft '${token.wert}'. Es wird keine Eigenschaft erwartet."
    }

    class EigenschaftUnveränderlich(token: Token): EigenschaftsFehler(token) {
      override val nachricht = "Die Eigenschaft '${token.wert}' ist unveränderlich und kann nicht erneut zugewiesen werden."
    }

    class EigenschaftPrivat(token: Token): EigenschaftsFehler(token) {
      override val nachricht: String
        get() = "Die Eigenschaft '${token.wert}' ist privat und es kann von außen nicht auf sie zugegriffen werden."
    }
  }

  class KonvertierungsFehler(token: Token, private val von: Typ, private val zu: Typ): GermanSkriptFehler("Konvertierungsfehler", token) {
    override val nachricht get() = "Die Konvertierung von ${von.name} zu ${zu.name} ist nicht möglich."
  }

  class LaufzeitFehler(token: Token, val aufrufStapelString: String, val fehlerMeldung: String): GermanSkriptFehler("Laufzeitfehler", token) {
    override val nachricht: String
      get() = "$fehlerMeldung\n$aufrufStapelString"
  }
}