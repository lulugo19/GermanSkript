sealed class GermanScriptFehler(val token: Token): Error() {
  abstract val nachricht: String?

  override val message: String?
    get() {
      val pos = token.anfang
      return "in (${pos.zeile}, ${pos.spalte}): " + nachricht.orEmpty()
    }

  sealed class SyntaxFehler(token: Token): GermanScriptFehler(token) {

    class LexerFehler(token: Token): SyntaxFehler(token) {
      override val nachricht: String
        get() = "Ungültige Zeichenfolge '${token.wert}'"
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

    class RückgabeTypFehler(token: Token): SyntaxFehler(token){
      override val nachricht: String?
        get() = "Die Funktion kann nichts zurückgeben, da in der definition kein Rückgabetyp angegeben ist."
    }

    class FunktionAlsAusdruckFehler(token: Token): SyntaxFehler(token){
      override val nachricht: String?
        get() = "Die Funktion '${token.wert}' kann nicht als Ausdruck verwendet werden, da sie keinen Rückgabetyp besitzt."
    }

    class AnzahlDerParameterFehler(token: Token): SyntaxFehler(token){
      override val nachricht: String?
        get() = "Die Anzahl der Parameter und Argumente der Funktion '${token.wert}' stimmen nicht überein."
    }

    class OperatorFehler(token: Token, private val linkerTyp: String, private val rechterTyp: String): SyntaxFehler(token){
      override val nachricht: String?
        get() = "Operatoren funktionieren nur für gleiche Typen. $linkerTyp und $rechterTyp sind nicht gleich."
    }
  }

  sealed class DudenFehler(token: Token, protected val wort: String): GermanScriptFehler(token) {
    class WortNichtGefundenFehler(token: Token, wort: String) : DudenFehler(token, wort) {
      override val nachricht: String?
        get() = "Das Wort '$wort' konnte im Duden nicht gefunden werden."
    }

    class Verbindungsfehler(token: Token, wort: String) : DudenFehler(token, wort) {
      override val nachricht: String?
        get() = "Das wort '$wort' konnte nicht im Duden nachgeschaut werden, da keine Netzwerk-Verbindung zum Online-Duden besteht."
    }
  }

  class UnbekanntesWort(token: Token): GermanScriptFehler(token) {
    override val nachricht: String?
      get() = "Das Wort ${token.wert} ist unbekannt. Füge eine Deklinationsanweisung für das Wort hinzu!"
  }

  sealed class GrammatikFehler(token: Token): GermanScriptFehler(token) {

    sealed class KasusFehler(token: Token, protected val kasus: Kasus, protected val nomen: AST.Nomen): GrammatikFehler(token) {
      val form get() = "(${kasus.anzeigeName}, ${nomen.genus!!.anzeigeName}, ${nomen.numerus!!.anzeigeName})"

      class FalscherArtikel(token: Token, kasus: Kasus, nomen: AST.Nomen, private val richtigerArtikel: String) : KasusFehler(token, kasus, nomen) {
        override val nachricht: String?
          get() = "Falscher Artikel '${token.wert} ${nomen.bezeichner.wert}'. " +
              "Der richtige Artikel für '${nomen.bezeichner.wert}' $form ist '$richtigerArtikel ${nomen.bezeichner.wert}'."
      }

      class FalscheForm(token: Token, kasus: Kasus, nomen: AST.Nomen, private val richtigeForm: String) : KasusFehler(token, kasus, nomen) {
        override val nachricht: String?
          get() = "Falsche Form des Nomens '${token.wert}'. Die richtige Form $form ist '$richtigeForm'."
      }
    }

    class FalscheZuweisung(token: Token, private val numerus: Numerus) : GrammatikFehler(token) {
      override val nachricht: String?
        get() = "Falsche Zuweisung '${token.wert}'. Die richtige Form der Zuweisung im ${numerus.anzeigeName} ist '${numerus.zuweisung}'." +
            "Statt dem Wort kann auch das Symbol '=' verwendet werden."
    }

    class FalscherNumerus(token: Token, private  val numerus: Numerus, private val erwartetesNomen: String): GrammatikFehler(token) {
      override val nachricht: String?
        get() = "Falscher Numerus. Das Nomen muss im ${numerus.anzeigeName} '$erwartetesNomen' stehen."
    }

    class FalschesSingular(token: Token, private val plural: String, private val erwartet: String): GrammatikFehler(token) {
      override val nachricht: String?
        get() = "Falsches Singular. Das Singular von '$plural' ist im Akkusativ '$erwartet'."
    }
  }

  sealed class DoppelteDefinition(token: Token): GermanScriptFehler(token) {
    class Funktion(token: Token, private val definition: AST.Definition.Funktion): DoppelteDefinition(token) {
      override val nachricht: String?
        get() = "Die Funktion '${definition.vollerName}' ist schon in Zeile ${definition.name.anfang.zeile} definiert."
    }
    class UnveränderlicheVariable(token: Token): DoppelteDefinition(token){
      override val nachricht: String?
        get() = "Die Variable '${token.wert}' kann nicht erneut zugewiesen werden, da sie unveränderlich ist."
    }
  }

  sealed class Undefiniert(token: Token): GermanScriptFehler(token) {
    class Funktion(token: Token, private val funktionsAufruf: AST.FunktionsAufruf): Undefiniert(token) {
      override val nachricht: String?
        get() = "Die Funktion '${funktionsAufruf.vollerName!!}' ist nicht definiert."
    }

    class Variable(token: Token): Undefiniert(token) {
      override val nachricht: String?
        get() = "Die Variable '${token.wert}' ist nicht definiert.'"
    }

    class Typ(token: Token): Undefiniert(token) {
      override val nachricht: String?
        get() = "Der Typ '${token.wert}' ist nicht definiert."
    }

    class Operator(token: Token, private val typ: String): Undefiniert(token){
      override val nachricht: String?
        get() = "Der Operator '${token.wert}' ist für den Typen '$typ' nicht definiert."
    }

    class Minus(token: Token): Undefiniert(token){
      override val nachricht: String?
        get() = "'$token' ist nur für Zahlen definiert."
    }
  }

  class TypFehler(token: Token, private val erwarteterTyp: Typ): GermanScriptFehler(token) {
    override val nachricht: String?
      get() = "Falscher Typ. Erwartet wird der Typ ${erwarteterTyp.name}"
  }
}