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

  sealed class GrammatikFehler(token: Token, protected val kasus: Kasus, protected val numerus: Numerus): GermanScriptFehler(token) {
    class FalscherArtikel(token: Token, kasus: Kasus, numerus: Numerus, private val richtigerArtikel: String): GrammatikFehler(token, kasus, numerus) {
      override val nachricht: String?
        get() = "Falscher Artikel '${token.wert}'. Der richtige Artikel für ${kasus.anzeigeName} ${numerus.anzeigeName} ist '$richtigerArtikel'."
    }

    class FalscheForm(token: Token, kasus: Kasus, numerus: Numerus, private val richtigeForm: String): GrammatikFehler(token, kasus, numerus) {
      override val nachricht: String?
        get() = "Falsche Form des Nomens '${token.wert}'. Die richtige Form des Nomens im ${kasus.anzeigeName} ${numerus.anzeigeName} ist '$richtigeForm'."
    }
  }

  sealed class DoppelteDefinition(token: Token): GermanScriptFehler(token) {
    class Funktion(token: Token, private val definition: FunktionsDefinition): DoppelteDefinition(token) {
      override val nachricht: String?
        get() = "Die Funktion '${definition.vollerName}' ist schon in Zeile ${definition.verbToken.anfang.zeile} definiert."
    }
  }

  sealed class Undefiniert(token: Token): GermanScriptFehler(token) {
    class Funktion(token: Token, private val funktionsAufruf: AST.FunktionsAufruf): Undefiniert(token) {
      override val nachricht: String?
        get() = "Die Funktion '${funktionsAufruf.vollerName!!} ist nicht definiert.'"
    }
  }
}