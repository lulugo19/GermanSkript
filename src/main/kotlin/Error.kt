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

  sealed class GrammatikFehler(token: Token, protected val kasus: Kasus, protected val nomen: AST.Nomen): GermanScriptFehler(token) {

    val form get() = "(${kasus.anzeigeName}, ${nomen.genus!!.anzeigeName}, ${nomen.numerus!!.anzeigeName})"

    class FalscherArtikel(token: Token, kasus: Kasus, nomen: AST.Nomen, private val richtigerArtikel: String): GrammatikFehler(token, kasus, nomen) {
      override val nachricht: String?
        get() = "Falscher Artikel '${token.wert} ${nomen.bezeichner.wert}'. " +
            "Der richtige Artikel für '${nomen.bezeichner.wert}' $form ist '${nomen.artikel} ${nomen.bezeichner.wert}'."
    }

    class FalscheForm(token: Token, kasus: Kasus, nomen: AST.Nomen, private val richtigeForm: String): GrammatikFehler(token, kasus, nomen) {
      override val nachricht: String?
        get() = "Falsche Form des Nomens '${token.wert}'. Die richtige Form $form ist '$richtigeForm'."
    }
  }

  sealed class DoppelteDefinition(token: Token): GermanScriptFehler(token) {
    class Funktion(token: Token, private val definition: AST.Definition.Funktion): DoppelteDefinition(token) {
      override val nachricht: String?
        get() = "Die Funktion '${definition.vollerName}' ist schon in Zeile ${definition.name.anfang.zeile} definiert."
    }
  }

  sealed class Undefiniert(token: Token): GermanScriptFehler(token) {
    class Funktion(token: Token, private val funktionsAufruf: AST.FunktionsAufruf): Undefiniert(token) {
      override val nachricht: String?
        get() = "Die Funktion '${funktionsAufruf.vollerName!!} ist nicht definiert.'"
    }

    class Variable(token: Token): Undefiniert(token) {
      override val nachricht: String?
        get() = "Die Variable '${token.wert}' ist nicht definiert.'"
    }

    class Typ(token: Token): Undefiniert(token) {
      override val nachricht: String?
        get() = "Der Typ '${token.wert}' ist nicht definiert."
    }
  }

  class TypFehler(token: Token, private val erwarteterTyp: Typ): GermanScriptFehler(token) {
    override val nachricht: String?
      get() = "Falscher Typ. Erwartet wird der Typ ${erwarteterTyp.name}"
  }
}