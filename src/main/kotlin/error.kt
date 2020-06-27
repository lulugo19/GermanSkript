sealed class GermanScriptFehler(val token: Token): Error() {
  abstract val nachricht: String?

  override val message: String?
    get() {
      val pos = token.anfang
      return "in (${pos.zeile}, ${pos.spalte}) " + nachricht.orEmpty()
    }

  sealed class SyntaxFehler(token: Token): GermanScriptFehler(token) {

    class LexerFehler(token: Token): SyntaxFehler(token) {
      override val nachricht: String
        get() = "Ungültige Zeichenfolge '${token.wert}'"
    }

    class ParseFehler(token: Token, private val erwartet: String? = null, private val details: String? = null): SyntaxFehler(token) {
      override val nachricht: String
        get() = "Unerwartetes Token ${token.typ.anzeigeName}. $erwartet Erwartet. ${details.orEmpty()}"
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

}