class SyntaxError(token: Token, erwartet: String? = null, nachricht: String? = null) :
        Exception("Unerwartetes ${token.wert} in Zeile ${token.anfang.first} und Spalte ${token.anfang.second}. Erwartet wird $erwartet")


class Parser(val code: String) {
  val tokens = Peekable(tokeniziere(code).iterator())

  fun parse(): Programm {
    val definitionen = mutableListOf<Definition>()
    val sätze = mutableListOf<Satz>()
    loop@ while (true) {
      when (tokens.peek()!!.typ) {
        is TokenTyp.EOF -> break@loop
        is TokenTyp.DEFINIERE -> definitionen += parseDefinition()
        else -> sätze += parseSätze(false)
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
    println("PARSE TYP!!")
    // der Artikel muss ein bestimmter sein: der, die, das, aber statt der muss den genommen werden
    val geschlechtToken = tokens.next()!!
    var geschlecht : Geschlecht = when(val tokenTyp = geschlechtToken.typ) {
      is TokenTyp.ARTIKEL ->
        if (tokenTyp.geschlecht == Geschlecht.MÄNNLICH) throw SyntaxError(tokens.next()!!, "den")
        else tokenTyp.geschlecht
      is TokenTyp.DEN -> Geschlecht.MÄNNLICH
      else -> throw SyntaxError(geschlechtToken, "den/die/das")
    }
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
        var name = expect<TokenTyp.NOMEN>("Nomen")
        expect<TokenTyp.ALS>("als")
        var typ = expect<TokenTyp.NOMEN>("Nomen")
        felder += NameUndTyp(name, typ)
      }
    }
    expect<TokenTyp.PUNKT>(".")
    return Definition.Typ(geschlecht, typName, elternTyp, listenName, felder)
  }

  private fun parseFunktion(): Definition.Funktion {
    TODO("Not yet implemented")
  }

  private fun parseMethode(): Definition.Methode {
    TODO("Not yet implemented")
  }

  private fun parseSätze(inSchleife: Boolean): List<Satz> {
    val sätze = mutableListOf<Satz>()
    while (true) {
      while (tokens.peek()!!.typ is TokenTyp.NEUE_ZEILE) {
        tokens.next()
      }
      val nächsterTokenTyp = tokens.peek()!!.typ
      if (nächsterTokenTyp is TokenTyp.DEFINIERE || nächsterTokenTyp is TokenTyp.EOF) {
        break
      }
      when (tokens.peek()!!.typ) {
        is TokenTyp.WENN -> sätze += parseBedingung()
        is TokenTyp.SOLANGE -> sätze += parseSolangeSchleife()
        is TokenTyp.FÜR -> sätze += parseFürJedeSchleife()
        else -> {
          sätze += parseSatz(inSchleife)
          if (tokens.peek()!!.typ is TokenTyp.TRENNER) {
            tokens.next()
          }
        }
      }
    }
    return sätze
  }

  private fun parseFürJedeSchleife(): Satz {
    TODO("Not yet implemented")
  }

  private fun parseSolangeSchleife(): Satz {
    TODO("Not yet implemented")
  }

  private fun parseBedingung(): Satz {
    TODO("Not yet implemented")
  }

  private fun parseSatz(inSchleife: Boolean): Satz {
    return when (val typ = tokens.peek()!!.typ) {
      is TokenTyp.ARTIKEL -> parseVariablenDeklaration()
      is TokenTyp.NOMEN -> when (tokens.peekDouble()!!.typ) {
        is TokenTyp.VERB -> Satz.MethodenaufrufSatz(parseMethodenAufruf())
        is TokenTyp.ZUWEISUNG -> parseVariablenZuweisung()
        else -> throw SyntaxError(tokens.next()!!)
      }
      is TokenTyp.FORTFAHREN, TokenTyp.ABBRECHEN ->
        if (inSchleife) {
          when (typ) {
            is TokenTyp.FORTFAHREN -> Satz.Forfahren(tokens.next()!!)
            is TokenTyp.ABBRECHEN -> Satz.Abbrechen(tokens.next()!!)
            else -> throw Exception("Dieser Fall wird die ausgeführt")
          }
        } else {
          val token =  tokens.next()!!
          throw Exception("${token.wert} kann nur in einer Schleife verwendet werden")
        }
      else -> throw SyntaxError(tokens.next()!!)
    }
  }

  private fun parseVariablenDeklaration(): Satz.Variablendeklaration {
    TODO()
  }

  private fun parseVariablenZuweisung(): Satz.Variablenzuweisung {
    TODO()
  }

  public fun parseAusdruck() = parseBinärerAusdruck(0.0)

  // pratt parsing: https://matklad.github.io/2020/04/13/simple-but-powerful-pratt-parsing.html
  private fun parseBinärerAusdruck(minBindungskraft: Double) : Ausdruck {
    var leftHandSide = parseEinzelnerAusdruck()

    loop@ while (true) {
      val operator = when (val token = tokens.peek()!!.typ) {
        is TokenTyp.OPERATOR -> token.operator
        else -> break@loop
      }
      val bindungsKraft = operator.bindungsKraft;
      val linkeBindungsKraft = bindungsKraft + if (operator.assoziativität == Assoziativität.RECHTS) 0.1 else 0.0;
      val rechteBindungsKraft = bindungsKraft + if (operator.assoziativität == Assoziativität.LINKS) 0.1 else 0.0;
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
    TODO()
  }

  private fun parseMethodenAufruf(): Methodenaufruf {
    TODO()
  }

  private fun parseWennDannSonstAusdruck(): Ausdruck.WennDannSonst {
    TODO()
  }
}

fun main() {
  val code = "-A * B hoch C minus (-6 durch 56)"

  val typDefinition = "definiere den Student als Person mit Plural Studenten: Vorname als Zeichenfolge, Nachname als Zeichenfolge, Alter als Zahl."

  val parser = Parser(typDefinition)
  println(parser.parse())
}