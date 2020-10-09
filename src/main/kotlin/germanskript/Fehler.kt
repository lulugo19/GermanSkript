package germanskript

import germanskript.intern.Wert

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

    class AnzahlDerParameterFehler(token: Token): SyntaxFehler(token){
      override val nachricht: String
        get() = "Die Anzahl der Parameter und Argumente der Funktion '${token.wert}' stimmen nicht überein."
    }
  }

  sealed class RückgabeFehler(token: Token): GermanSkriptFehler("Rückgabefehler", token) {
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

  class KlasseErwartet(token: Token): GermanSkriptFehler("Klasse erwartet", token) {
    override val nachricht: String get() {
      return "Es wird eine Klasse erwartet. Eine Schnittstelle oder ein primitiver Typ sind hier nicht erlaubt."
    }
  }

  class SchnittstelleErwartet(token: Token): GermanSkriptFehler("Schnittstelle erwartet", token) {
    override val nachricht: String
      get() = "Es wird eine Schnittstelle erwartet. Eine Klasse oder ein primitiver Typ sind hier nicht erlaubt."
  }

  sealed class ClosureFehler(token: Token): GermanSkriptFehler("Closure-Fehler", token) {
    class UngültigeClosureSchnittstelle(token: Token, schnittstelle: AST.Definition.Typdefinition.Schnittstelle):
        ClosureFehler(token) {
      override val nachricht: String
         = "Die Schnittstelle '${schnittstelle.namensToken.wert}' in ${schnittstelle.namensToken.position} kann nicht für ein Closure verwendet werden,\n" +
            "da sie genau eine Methode definieren muss."
    }

    class ZuVieleBinder(token: Token,  maxAnzahlBinder: Int): ClosureFehler(token) {
      override val nachricht = "Das Closure bindet zu viele Namen. Es dürfen maximal $maxAnzahlBinder Namen gebunden werden."
    }

    class FalscheRückgabe(token: Token, rückgabeTyp: Typ, erwarteterRückgabeTyp: Typ): ClosureFehler(token) {
      override val nachricht = "Es wird erwartet, dass das Closure einen Wert des Typs '$erwarteterRückgabeTyp' zurückgibt.\n" +
          "Es gibt jedoch einen Wert des Typs '$rückgabeTyp' zurück."
    }
  }

  class WennAusdruckBrauchtSonst(token: Token): GermanSkriptFehler("Wenn-Ausdruck-Fehler", token) {
    override val nachricht: String = "Der Wenn-Ausdruck braucht einen Sonst-Fall, ansonsten kann er nicht als Ausdruck verwendet werden."
  }

  class UnimplementierteSchnittstelle(
      token: Token,
      private val klasse: AST.Definition.Typdefinition.Klasse,
      private val implBereich: AST.Definition.ImplementierungsBereich,
      private val typ: Typ.Compound.Schnittstelle): GermanSkriptFehler("Unimplementierte Schnittstelle", token) {
    override val nachricht: String get() {
      val uninplementierteMethoden = typ.definition.methodenSignaturen.filter { signatur ->
        implBereich.methoden.find { it.signatur.vollerName == signatur.vollerName } == null
      }.map {it.vollerName }
      return "Die Klasse '${klasse.name.nominativ}' implementiert die Schnittstelle '${typ}' nicht.\n" +
          "Folgende Methoden müssen implementiert werden:\n" + uninplementierteMethoden.joinToString("\n\t")
    }
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

  class TypArgumentFehler(token: Token, argAnzahl: Int, erwarteteAnzahl: Int): GermanSkriptFehler("Typargumentfehler", token) {
    override val nachricht = "Es wurden $argAnzahl Typargumente angegeben. Erwartet werden jedoch $erwarteteAnzahl Typargument(e)."
  }

  sealed class GrammatikFehler(token: Token): GermanSkriptFehler("Grammatikfehler",token) {

    sealed class FormFehler(token: Token, protected val kasus: Kasus, protected val nomen: AST.WortArt.Nomen): GrammatikFehler(token) {
      val form get() = "($kasus, ${nomen.genus}, ${nomen.numerus})"

      class FalschesVornomen(token: Token, kasus: Kasus, nomen: AST.WortArt.Nomen, private val richtigesVornomen: String) : FormFehler(token, kasus, nomen) {
        override val nachricht: String
          get() = "Falsches Vornomen (${token.typ}) '${token.wert} ${nomen.bezeichner.wert}'.\n" +
              "Das richtige Vornomen $form ist '$richtigesVornomen ${nomen.bezeichner.wert}'."
      }

      class FalschesNomen(token: Token, kasus: Kasus, nomen: AST.WortArt.Nomen, richtigeForm: String) : FormFehler(token, kasus, nomen) {
        override val nachricht = "Falsche Form des Nomens '${token.wert}'. Die richtige Form $form ist '$richtigeForm'."
      }
    }

    class FalschesReflexivPronomen(
        token: Token,
        reflexivPronomen: TokenTyp.REFLEXIV_PRONOMEN,
        erwartet: TokenTyp.REFLEXIV_PRONOMEN
    ): GrammatikFehler(token) {
      override val nachricht = "Falsches Reflexivpronomen '${reflexivPronomen.pronomen}'.\n" +
          "Es wird das Reflexivpronomen '${erwartet.pronomen}' " +
          "(${reflexivPronomen.kasus.joinToString(" | ") {it.anzeigeName}}, ${erwartet.numerus}) erwartet."
    }

    class FalscheAdjektivEndung(token: Token, richtigeEndung: String): GrammatikFehler(token) {
      override val nachricht = "Das Adjektiv '${token.wert}' steht in der falschen Form. Es muss mit '-$richtigeEndung' enden."
    }

    class FalscheZuweisung(token: Token, numerus: Numerus) : GrammatikFehler(token) {
      override val nachricht = "Falsche Zuweisung '${token.wert}'. Die richtige Form der Zuweisung im $numerus ist '${numerus.zuweisung}'."
    }

    class FalscherNumerus(token: Token, numerus: Numerus, erwartetesNomen: String): GrammatikFehler(token) {
      override val nachricht = "Falscher Numerus. Das Nomen muss im $numerus '$erwartetesNomen' stehen."
    }

    class PluralErwartet(token: Token): GrammatikFehler(token) {
      override val nachricht = "Es wird ein Ausdruck im Plural erwartet. Doch der Ausdruck '${token.wert}' ist im Singular."
    }
  }

  sealed class DoppelteDefinition(token: Token): GermanSkriptFehler("Definitionsfehler", token) {
    class Funktion(token: Token, private val definition: AST.Definition.Funktion): DoppelteDefinition(token) {
      override val nachricht: String
        get() = "Die Funktion '${definition.signatur.vollerName}' ist schon in ${definition.signatur.name.position} definiert."
    }

    class Methode(token: Token, methode: AST.Definition.Funktion, klasse: AST.Definition.Typdefinition.Klasse): DoppelteDefinition(token) {
      override val nachricht =
        "Die Methode '${methode.signatur.vollerName}' für die Klasse '${klasse.name.nominativ}' " +
            "ist schon in ${methode.signatur.name.position} definiert."
    }

    class Typ(token: Token, private val vorhandeneDefinition: Token): DoppelteDefinition(token) {
      override val nachricht: String
        get() = "Der Typ '${token.wert}' ist schon in ${vorhandeneDefinition.position} definiert."
    }

    class Konvertierung(token: Token, private val konvertierung: AST.Definition.Konvertierung): DoppelteDefinition(token) {
      override val nachricht: String
        get() = "Die Konvertierung von '${konvertierung.typ.name.nominativ}' zu '${konvertierung.typ.name.nominativ}'\n" +
            "ist schon in ${konvertierung.typ.name.bezeichnerToken.position} definiert."
    }

    class Eigenschaft(token: Token, eigenschaft: AST.Definition.Eigenschaft): DoppelteDefinition(token) {
      override val nachricht = "Die berechnete Eigenschaft '${eigenschaft.name.nominativ}' ist schon in\n" +
          "${eigenschaft.name.bezeichner.position} definiert."
    }

    class Konstante(token: Token, konstante: AST.Definition.Konstante): DoppelteDefinition(token) {
      override val nachricht =" Die Konstante '${konstante.wert}' ist schon in\n" +
          "${konstante.name.position} definiert."
    }
  }

  class DoppelteEigenschaft(token: Token, klasse: AST.Definition.Typdefinition.Klasse): GermanSkriptFehler("Doppelte Eigenschaft", token) {
    override val nachricht = "Die Klasse '${klasse.namensToken.wert}' hat bereits eine Eigenschaft mit dem Namen ${token.wert}."
  }

  class Variablenfehler(token: Token, deklaration: AST.WortArt.Nomen): GermanSkriptFehler("Variablen Fehler", token) {
    override val nachricht = "Die Variable '${token.wert}' ist schon in ${deklaration.bezeichner.position} deklariert und kann nicht erneut deklariert werden."
  }

  class AliasFehler(token: Token, alias: AST.Definition.Typdefinition.Alias): GermanSkriptFehler("Alias Fehler", token) {
    override val nachricht = "Ein Alias kann nicht auf ein weiteres Alias ('${alias.name.nominativ}' in ${alias.name.bezeichner.position}) verweisen."
  }

  sealed class Undefiniert(token: Token): GermanSkriptFehler("Undefiniert Fehler", token) {
    class Funktion(token: Token, private val funktionsAufruf: AST.Satz.Ausdruck.FunktionsAufruf): Undefiniert(token) {
      override val nachricht: String
        get() = "Die Funktion '${funktionsAufruf.vollständigerName}' ist nicht definiert."
    }

    class Methode(token: Token, private val methodenAufruf: AST.Satz.Ausdruck.FunktionsAufruf, private val klassenName: String): Undefiniert(token) {
      override val nachricht: String
        get() = "Die Methode 'für $klassenName: ${methodenAufruf.vollerName!!}' ist nicht definiert."
    }

    class Variable(token: Token, private val name: String = token.wert): Undefiniert(token) {
      override val nachricht: String
        get() = "Die Variable '$name' ist nicht definiert.'"
    }

    class Typ(token: Token, typ: AST.TypKnoten): Undefiniert(token) {
      override val nachricht: String
          = "Der Typ '${typ.vollständigerName}' ist nicht definiert."
    }

    class Konstante(token: Token, konstante: AST.Satz.Ausdruck.Konstante): Undefiniert(token) {
      override val nachricht: String
        = "Die Konstante '${konstante.vollständigerName}' ist nicht definiert."
    }

    class Operator(token: Token, private val typ: String): Undefiniert(token){
      override val nachricht: String
        get() = "Der Operator '${token.wert}' ist für den Typen '$typ' nicht definiert."
    }

    class Eigenschaft(token: Token, eigenschaftsName: String, klasse: String): Undefiniert(token) {
      override val nachricht = "Die Eigenschaft '$eigenschaftsName' ist für die Klasse '$klasse' nicht definiert."
    }

    class Modul(token: Token, modulPfad: List<TypedToken<TokenTyp.BEZEICHNER_GROSS>>): Undefiniert(token) {
      override val nachricht = "Das Modul '${modulPfad.joinToString("::") { it.wert }}' ist nicht definiert."
    }
  }

  sealed class Mehrdeutigkeit(token: Token): GermanSkriptFehler("Mehrdeutigkeit", token) {
    class Typ(token: Token, typA: AST.Definition.Typdefinition, typB: AST.Definition.Typdefinition):
      Mehrdeutigkeit(token) {
      override val nachricht: String
          = "Es ist unklar welche Klasse gemeint ist.\n" +
            "Entweder die Klasse '${typA.namensToken.wert}' in ${typA.namensToken.position}\n" +
            "oder die Klasse '${typB.namensToken.wert}' in ${typB.namensToken.position}."

    }

    class Funktion(
        token: Token,
        funktionA: AST.Definition.Funktion,
        funktionB: AST.Definition.Funktion): Mehrdeutigkeit(token) {

      override val nachricht: String = "Es ist unklar welche Funktion gemeint ist.\n" +
            "Entweder die Funktion in ${funktionA.signatur.name.position} oder die Funktion in " +
            "${funktionB.signatur.name.position}."
    }

    class Konstante(
        token: Token, konstanteA: AST.Definition.Konstante, konstanteB: AST.Definition.Konstante
    ): Mehrdeutigkeit(token) {
      override val nachricht: String = "Es ist unklar welche Konstante gemeint ist.\n" +
          "Entweder die Konstante in ${konstanteA.name.position} oder die Konstante in " +
          "${konstanteB.name.position}."
    }
  }

  sealed class TypFehler(token: Token): GermanSkriptFehler("Typfehler", token) {
    class FalscherTyp(token: Token, falscherTyp: Typ, erwarteterTyp: String): TypFehler(token) {
      override val nachricht = "Falscher Typ '${falscherTyp.name}'. Erwartet wird der Typ '$erwarteterTyp'."
    }

    class TypenUnvereinbar(token: Token, typA: Typ, typB: Typ): TypFehler(token) {
      override val nachricht = "Die Typen '$typA' und '$typB' können nicht in einen gemeinsamen Typen vereint werden."
    }

    class ObjektErwartet(token: Token): TypFehler(token) {
      override val nachricht = "Es wird ein Objekt erwartet und kein primitiver Typ (Zahl, Zeichenfolge, Boolean)."
    }

    class FalscherSchnittstellenTyp(
        token: Token,
        schnittstelle: Typ.Compound.Schnittstelle,
        methodenName: String,
        falscherTyp: Typ,
        erwarteterTyp: Typ
    ): TypFehler(token) {
      override val nachricht = "Um die Methode '${methodenName}' für die Schnittstelle '${schnittstelle}' zu implementieren,\n" +
          "muss der Typ '${erwarteterTyp}' und nicht '${falscherTyp}' sein."
    }

    class RückgabeObjektErwartet(
        token: Token,
        schnittstelle: Typ.Compound.Schnittstelle,
        methodenName: String,
        rückgabeObjektWirdErwartet: Boolean,
        objekt: AST.Definition.Parameter
    ): TypFehler(token) {
      override val nachricht = "Um die Methode '${methodenName}' für die Schnittstelle '${schnittstelle}' zu implementieren,\n" +
          if (rückgabeObjektWirdErwartet) "muss das Objekt ein Rückgabe-Objekt sein, also mit Klammern '(${objekt.anzeigeName})' geschrieben werden."
          else  "darf das Objekt kein Rückgabe-Objekt sein, muss also ohne Klammern geschrieben werden."
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

  class KonstantenFehler(token: Token): GermanSkriptFehler("Konstantenfehler", token) {
    override val nachricht = "Einer Konstanten können nur feste Werte (Zeichenfolge, Zahl, Bool) zugewiesen werden."
  }

  open class LaufzeitFehler(token: Token, val aufrufStapelString: String, val fehlerMeldung: String): GermanSkriptFehler("Laufzeitfehler", token) {
    override val nachricht: String
      get() = "$fehlerMeldung\n$aufrufStapelString"
  }

  class UnbehandelterFehler(token: Token, aufrufStapelString: String, fehlerMeldung: String, val fehlerObjekt: Wert):
      LaufzeitFehler(token, aufrufStapelString, "Unbehandelter Fehler. $fehlerMeldung")
}