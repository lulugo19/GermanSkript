class SyntaxError(token: Token, erwartet: String? = null, nachricht: String? = null) :
        Exception("Unerwartetes '${token.wert}' in (${token.anfang.first}, ${token.anfang.second}). Erwartet wird $erwartet. $nachricht")

class Parser(code: String) {
  private val tokens = Peekable(tokeniziere(code).iterator())

  private enum class Bereich(val anzeigeName: String) {
    Schleife("Schleife"),
    Bedingung("Bedingung"),
    MethodenDefinition("Methodendefinition"),
    FunktionsDefinition("Funktionsdefinition"),
    MethodenDefinition_R("Methodendefinition"),
    FunktionsDefinition_R("Funktionsdefinition")
  }

  fun parse(): Programm {
    val definitionen = mutableListOf<Definition>()
    val sätze = mutableListOf<Satz>()
    loop@ while (true) {
      when (tokens.peek()!!.typ) {
        is TokenTyp.EOF -> break@loop
        is TokenTyp.DEFINIERE -> definitionen += parseDefinition()
        else -> sätze += parseSätze(emptyList())
      }
    }
    return Programm(definitionen, sätze)
  }

  private inline fun <reified T : TokenTyp>expect(erwartet: String): Token {
    val nextToken = tokens.next()!!
    if (nextToken.typ is T) {
      return nextToken
    } else {
      throw SyntaxError(nextToken, erwartet)
    }
  }

  private fun parseDefinition(): Definition {
    expect<TokenTyp.DEFINIERE>("definiere")
    return when (tokens.peek()!!.typ) {
      is TokenTyp.VERB -> {
        when (tokens.peekDouble()!!.typ) {
          is TokenTyp.FÜR -> parseMethode()
          else -> parseFunktion()
        }
      }
      is TokenTyp.ARTIKEL, TokenTyp.DEN -> parseTyp()
      else -> throw SyntaxError(tokens.next()!!)
    }
  }

  private fun parseTyp(): Definition.Typ {
    // der Artikel muss ein bestimmter sein: der, die, das, aber statt der muss den genommen werden
    val artikel = expect<TokenTyp.ARTIKEL>("Artikel")
    val typName = expect<TokenTyp.NOMEN>("Nomen")
    var elternTyp: Token? = null
    if (tokens.peek()!!.typ is TokenTyp.ALS) {
      tokens.next()
      elternTyp = expect<TokenTyp.NOMEN>("Nomen")
    }
    expect<TokenTyp.MIT>("mit")
    expect<TokenTyp.PLURAL>("Plural")
    val listenName = expect<TokenTyp.NOMEN>("Nomen")

    expect<TokenTyp.DOPPELPUNKT>(":")

    val felder = mutableListOf<NameUndTyp>()
    if (tokens.peek()!!.typ !is TokenTyp.PUNKT) {
      var name = expect<TokenTyp.NOMEN>("Nomen")
      expect<TokenTyp.ALS>("als")
      var typ = expect<TokenTyp.NOMEN>("Nomen")
      felder += NameUndTyp(name, typ)

      while (tokens.peek()!!.typ is TokenTyp.KOMMA) {
        tokens.next()
        name = expect<TokenTyp.NOMEN>("Nomen")
        expect<TokenTyp.ALS>("als")
        typ = expect<TokenTyp.NOMEN>("Nomen")
        felder += NameUndTyp(name, typ)
      }
    }
    expect<TokenTyp.PUNKT>(".")
    return Definition.Typ(artikel, typName, elternTyp, listenName, felder)
  }

  private fun parseSignatur(signaturName: Token): Signatur {
    var rückgabeTyp: Token? = null
    val parameterListe = mutableListOf<NameUndTyp>()
    if (tokens.peek()!!.typ is TokenTyp.MIT){
      tokens.next()
      if (tokens.peek()!!.typ is TokenTyp.RÜCKGABE){
        tokens.next()
        rückgabeTyp = expect<TokenTyp.NOMEN>("Nomen")
      }else{
        val typ = expect<TokenTyp.NOMEN>("Nomen")
        var name = typ
        if (tokens.peek()!!.typ is TokenTyp.NOMEN){
          name = tokens.next()!!
        }
        parameterListe += NameUndTyp(name, typ)
      }
      while (tokens.peek()!!.typ is TokenTyp.KOMMA){
        tokens.next()
        val typ = expect<TokenTyp.NOMEN>("Nomen")
        var name = typ
        if (tokens.peek()!!.typ is TokenTyp.NOMEN){
          name = tokens.next()!!
        }
        parameterListe += NameUndTyp(name, typ)
      }
    }
    expect<TokenTyp.DOPPELPUNKT>(":")
    return Signatur(signaturName, rückgabeTyp, parameterListe)
  }

  private fun parseFunktion(): Definition.Funktion {
    val funktionsName = expect<TokenTyp.VERB>("Verb")
    val signatur = parseSignatur(funktionsName)
    val sätze = parseSätze(listOf(if (signatur.rückgabeTyp != null) Bereich.FunktionsDefinition_R else Bereich.FunktionsDefinition))
    if (signatur.rückgabeTyp != null && !sätze.any {it is Satz.Zurück}) {
      throw SyntaxError(tokens.next()!!, "zurück", "Eine Funktion mit einem Rückgabetypen muss mindestens eine zurück-Anweisung haben")
    }
    expect<TokenTyp.PUNKT>(".")
    return Definition.Funktion(signatur, sätze)
  }

  private fun parseMethode(): Definition.Methode {
    val methodenName = expect<TokenTyp.VERB>("Verb")
    expect<TokenTyp.FÜR>("für")
    val typ = expect<TokenTyp.NOMEN>("Nomen")
    val signatur = parseSignatur(methodenName)
    val sätze = parseSätze(listOf(if (signatur.rückgabeTyp != null) Bereich.MethodenDefinition_R else Bereich.MethodenDefinition))
    if (signatur.rückgabeTyp != null && !sätze.any {it is Satz.Zurück}) {
      throw SyntaxError(tokens.next()!!, "zurück", "Eine Methode mit einem Rückgabetypen muss mindestens eine zurück-Anweisung haben.")
    }
    expect<TokenTyp.PUNKT>(".")
    return Definition.Methode(signatur, typ, sätze)
  }

  private fun parseSchnittstelle(): Definition.Schnittstelle {
    TODO("für Finn")
  }

  private fun parseAlias(): Definition.Alias {
    TODO("für Finn")
  }
  
  private fun parseSätze(kontext: List<Bereich>): List<Satz> {
    val sätze = mutableListOf<Satz>()
    while (true) {
      while (tokens.peek()!!.typ is TokenTyp.NEUE_ZEILE) {
        tokens.next()
      }
      val nächsterTokenTyp = tokens.peek()!!.typ
      if (nächsterTokenTyp is TokenTyp.DEFINIERE || nächsterTokenTyp is TokenTyp.EOF || nächsterTokenTyp is TokenTyp.ALIAS) {
        if (kontext.isNotEmpty()) {
          val äußersterBereich = kontext.first()
          val innersterBereich = kontext.last()
          val fehlerNachricht = when(nächsterTokenTyp) {
            is TokenTyp.DEFINIERE -> "Definitionen können nur außerhalb von einer ${äußersterBereich.anzeigeName} gemacht werden."
            is TokenTyp.ALIAS -> "Aliase können nur außerhalb von einer ${äußersterBereich.anzeigeName} gemacht werden."
            is TokenTyp.EOF -> "${innersterBereich.anzeigeName} muss mit einem Punkt geschlossen werden."
            else -> throw Exception("Dieser Fall sollte nie ausgeführt werden")
          }
          throw SyntaxError(tokens.next()!!, null, fehlerNachricht)
        }
        break
      }
      if (nächsterTokenTyp is TokenTyp.ZURÜCK) {
        if (kontext.isEmpty() || kontext[0] != Bereich.MethodenDefinition_R && kontext[0] != Bereich.FunktionsDefinition_R) {
          throw SyntaxError(tokens.next()!!, null, "Das Schlüsselwort 'zurück' kann nur in einer Funktions- oder Methodendefinition verwendet werden.")
        }
      }
      if (nächsterTokenTyp is TokenTyp.PUNKT) {
        if (kontext.isEmpty()) {
          throw SyntaxError(tokens.next()!!, "")
        }
        break
      }
      when (tokens.peek()!!.typ) {
        is TokenTyp.WENN -> sätze += parseBedingung(kontext)
        is TokenTyp.SOLANGE -> sätze += parseSolangeSchleife(kontext)
        is TokenTyp.FÜR -> sätze += parseFürJedeSchleife(kontext)
        else -> {
          sätze += parseSatz(kontext)
          if (tokens.peek()!!.typ is TokenTyp.TRENNER) {
            tokens.next()
          }
        }
      }
    }
    return sätze
  }

  private fun parseSatz(kontext: List<Bereich>): Satz {
    return when (val typ = tokens.peek()!!.typ) {
      is TokenTyp.ARTIKEL -> parseVariablenDeklaration()
      is TokenTyp.NOMEN -> when (tokens.peekDouble()!!.typ) {
        is TokenTyp.VERB -> Satz.MethodenaufrufSatz(parseMethodenAufruf())
        is TokenTyp.ZUWEISUNG -> parseVariablenZuweisung()
        else -> throw SyntaxError(tokens.next()!!)
      }
      is TokenTyp.FORTFAHREN, TokenTyp.ABBRECHEN ->
        if (kontext.contains(Bereich.Schleife)) {
          when (typ) {
            is TokenTyp.FORTFAHREN -> Satz.Forfahren(tokens.next()!!)
            is TokenTyp.ABBRECHEN -> Satz.Abbrechen(tokens.next()!!)
            else -> throw Exception("Dieser Fall wird die ausgeführt")
          }
        } else {
          val token =  tokens.next()!!
          throw SyntaxError(token, null,"${token.wert} kann nur in einer Schleife verwendet werden")
        }
      is TokenTyp.ZURÜCK -> {
        if (kontext[0] != Bereich.FunktionsDefinition_R && kontext[0] != Bereich.MethodenDefinition_R) {
          throw SyntaxError(tokens.next()!!, null,
                  "Zurück kann nicht verwendet werden, da diese ${kontext[0].anzeigeName} keinen Rückgabetypen hat")
        } else {
          tokens.next()
          Satz.Zurück(parseAusdruck())
        }
      }
      else -> throw SyntaxError(tokens.next()!!)
    }
  }

  private fun parseFürJedeSchleife(kontext: List<Bereich>): Satz.FürJedeSchleife {
    expect<TokenTyp.FÜR>("für")
    val jede = expect<TokenTyp.JEDE>("jeder/jede/jedes")
    val binder = expect<TokenTyp.NOMEN>("Nomen")
    expect<TokenTyp.IN>("in")
    val ausdruck = parseAusdruck()
    expect<TokenTyp.DOPPELPUNKT>(":")
    var sätze = emptyList<Satz>()
    if (tokens.peek()!!.typ !is TokenTyp.PUNKT) {
      sätze = parseSätze(kontext + Bereich.Schleife)
    }
    expect<TokenTyp.PUNKT>(".")
    return Satz.FürJedeSchleife(jede, binder, ausdruck, sätze)
  }

  private fun parseSolangeSchleife(kontext: List<Bereich>): Satz.SolangeSchleife {
    expect<TokenTyp.SOLANGE>("solange")
    val bedingung = parseAusdruck()
    expect<TokenTyp.DOPPELPUNKT>(":")
    var sätze = emptyList<Satz>()
    if (tokens.peek()!!.typ !is TokenTyp.PUNKT) {
      sätze = parseSätze(kontext + Bereich.Schleife)
    }
    expect<TokenTyp.PUNKT>(".")
    return Satz.SolangeSchleife(BedingteSätze(bedingung, sätze))
  }

  private fun parseBedingung(kontext: List<Bereich>): Satz.Bedingung {
    TODO("für Lukas")
  }

  private fun parseVariablenDeklaration(): Satz.Variablendeklaration {
    TODO("für Finn")
  }

  private fun parseVariablenZuweisung(): Satz.Variablenzuweisung {
    TODO("für Finn")
  }

  private fun parseAusdruck() = parseBinärerAusdruck(0.0)

  // pratt parsing: https://matklad.github.io/2020/04/13/simple-but-powerful-pratt-parsing.html
  private fun parseBinärerAusdruck(minBindungskraft: Double) : Ausdruck {
    var leftHandSide = parseEinzelnerAusdruck()

    loop@ while (true) {
      val operator = when (val token = tokens.peek()!!.typ) {
        is TokenTyp.OPERATOR -> token.operator
        else -> break@loop
      }
      val bindungsKraft = operator.bindungsKraft
      val linkeBindungsKraft = bindungsKraft + if (operator.assoziativität == Assoziativität.RECHTS) 0.1 else 0.0
      val rechteBindungsKraft = bindungsKraft + if (operator.assoziativität == Assoziativität.LINKS) 0.1 else 0.0
      if (linkeBindungsKraft < minBindungskraft) {
        break
      }
      val operatorToken = expect<TokenTyp.OPERATOR>("Operator")
      val rightHandSide = parseBinärerAusdruck(rechteBindungsKraft)
      leftHandSide = Ausdruck.BinärerAusdruck(operatorToken, leftHandSide, rightHandSide)
    }

    return leftHandSide
  }

  private fun parseEinzelnerAusdruck() : Ausdruck {
    return when (val token = tokens.peek()!!.typ) {
      is TokenTyp.OFFENE_KLAMMER -> {
        tokens.next()!!
        parseAusdruck().also { expect<TokenTyp.GESCHLOSSENE_KLAMMER>(")") }
      }
      is TokenTyp.OPERATOR -> when (token.operator) {
        Operator.PLUS, Operator.MINUS, Operator.NEGATION -> Ausdruck.UnärerAusdruck(tokens.next()!!, parseEinzelnerAusdruck())
        else -> throw SyntaxError(tokens.next()!!, "Unärer Operator")
      }
      is TokenTyp.BOOLEAN, is TokenTyp.ZAHL, is TokenTyp.ZEICHENFOLGE -> Ausdruck.Literal(tokens.next()!!)
      is TokenTyp.NOMEN -> when(tokens.peekDouble()!!.typ) {
        is TokenTyp.VERB -> Ausdruck.MethodenaufrufAusdruck(parseMethodenAufruf())
        else -> Ausdruck.Variable(tokens.next()!!)
      }
      is TokenTyp.VERB -> Ausdruck.FunktionsaufrufAusdruck(parseFunktionsAufruf())
      is TokenTyp.WENN -> parseWennDannSonstAusdruck()
      else -> throw SyntaxError(tokens.next()!!)
    }
  }

  private fun parseFunktionsAufruf(): Funktionsaufruf {
    TODO("für Finn")
  }

  private fun parseMethodenAufruf(): Methodenaufruf {
    TODO("für Finn")
  }

  private fun parseWennDannSonstAusdruck(): Ausdruck.WennDannSonst {
    TODO("für Lukas")
  }
}

fun main() {
  val code = "-A * B hoch C minus (-6 durch 56)"

  val typDefinition = "definiere den Student als Person mit Plural Studenten: Vorname als Zeichenfolge, Nachname als Zeichenfolge, Alter als Zahl."

  val funktionDefinition = "definiere addieren mit Rückgabe Zahl , Zahl , Zahl X: zurück Zahl + X."

  val methodenDefinition = """definiere hallo für Student mit Rückgabe Zeichenfolge: zurück "Hallo!"."""

  val fürJedeSchleife = "für jede Zahl in Zahlen:."

  val solangeSchleife = "solange X > 5:."

  val parser = Parser(solangeSchleife)
  println(parser.parse())
}