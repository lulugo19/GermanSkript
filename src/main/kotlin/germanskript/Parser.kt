package germanskript

import germanskript.util.Peekable
import java.io.File
import java.util.*

enum class ASTKnotenID {
  PROGRAMM,
  MODUL,
  VARIABLEN_DEKLARATION,
  FUNKTIONS_AUFRUF,
  FUNKTIONS_DEFINITION,
  NACHRICHTEN_BEREICH,
  INTERN,
  DEKLINATION,
  ZURÜCKGABE,
  BEDINGUNG,
  SCHLEIFE,
  FÜR_JEDE_SCHLEIFE,
  SCHLEIFENKONTROLLE,
  LISTE,
  LISTEN_ELEMENT,
  KLASSEN_DEFINITION,
  OBJEKT_INSTANZIIERUNG,
  EIGENSCHAFTS_ZUGRIFF,
  KONVERTIERUNGS_DEFINITION,
  IMPORT,
  VERWENDE,
  SCHNITTSTELLE,
  SUPER_BLOCK,
  Lambda,
  ALIAS,
  EIGENSCHAFTS_DEFINITION,
  KONSTANTE,
  VERSUCHE_FANGE,
  WERFE,
  FUNKTIONS_SIGNATUR,
  IMPLEMENTIERUNG,
  BEDINGUNGS_TERM,
  LISTEN_ELEMENT_ZUWEISUNG,
  TYP_ÜBERPRÜFUNG,
  ANONYME_KLASSE,
  SelbstEigenschaftsZugriff,
  NachrichtenBereichsEigenschaftsZugriff
}

class Parser(startDatei: File): PipelineKomponente(startDatei) {
  fun parse(): AST.Programm {
    val lexer = Lexer(startDatei)
    val tokens = Peekable(lexer.tokeniziere().iterator())
    val ast = SubParser.Programm(ASTKnotenID.PROGRAMM, lexer).parse(tokens, Stack())
    if (tokens.peek(0)!!.typ !is TokenTyp.EOF) {
      throw GermanSkriptFehler.SyntaxFehler.ParseFehler(tokens.next()!!, "EOF")
    }
    return ast
  }
}

private sealed class SubParser<T: AST>() {
  private var stack: Stack<ASTKnotenID>? = null
  private var tokens: Peekable<Token>? = null
  protected var currentToken: Token? = null

  fun parse(tokens: Peekable<Token>, stack: Stack<ASTKnotenID>): T {
    this.tokens = tokens
    this.stack = stack
    bewacheKnoten()
    stack.push(id)
    val result = parseImpl()
    stack.pop()
    return result
  }

  protected abstract val id: ASTKnotenID
  protected abstract fun parseImpl(): T
  protected open fun bewacheKnoten() {
    // do nothing
  }

  protected val isRoot: Boolean get() = stack!!.empty()
  protected val depth: Int get() = stack!!.size
  protected val parentNodeId: ASTKnotenID get() = stack!!.peek()

  // region Hilfsmethoden
  protected fun next(): Token = tokens!!.next()!!.also { currentToken = it }
  protected fun peek(ahead: Int = 0): Token = tokens!!.peek(ahead)!!
  protected fun peekType(ahead: Int = 0): TokenTyp = tokens!!.peek(ahead)!!.typ
  protected fun hierarchyContainsNode(knotenId: ASTKnotenID) = stack!!.contains(knotenId)
  protected fun hierarchyContainsAnyNode(vararg knotenIds: ASTKnotenID) = stack!!.any {knotenIds.contains(it)}

  protected inline fun <reified T : TokenTyp>expect(erwartet: String): TypedToken<T> {
    val nextToken = next()
    if (nextToken.typ is T) {
      return nextToken.toTyped()
    } else {
      throw GermanSkriptFehler.SyntaxFehler.ParseFehler(nextToken, erwartet)
    }
  }

  protected fun <T: AST> subParse(subParser: SubParser<T>): T {
    return subParser.parse(tokens!!, stack!!)
  }

  protected fun überspringeLeereZeilen() {
    while (peekType() is TokenTyp.NEUE_ZEILE) {
      next()
    }
  }

  protected fun parseKleinesSchlüsselwort(schlüsselwort: String): TypedToken<TokenTyp.BEZEICHNER_KLEIN> {
    val token = expect<TokenTyp.BEZEICHNER_KLEIN>("'$schlüsselwort'")
    if (token.wert != schlüsselwort) {
      throw GermanSkriptFehler.SyntaxFehler.ParseFehler(token.toUntyped(), "'$schlüsselwort'")
    }
    return token
  }

  protected fun parseGroßenBezeichner(namensErweiterungErlaubt: Boolean): TypedToken<TokenTyp.BEZEICHNER_GROSS> {
    // mit Namenserweiterung ist ein Symbol oder ein Adjektiv vor dem Nomen gemeint
    val bezeichner = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner")
    if (!namensErweiterungErlaubt && bezeichner.typ.hatSymbol) {
      throw GermanSkriptFehler.SyntaxFehler.ParseFehler(bezeichner.toUntyped(), "Bezeichner ohne Symbol",
          "Ein Symbol ist in diesem Bezeichner nicht erlaubt.")
    }
    if (!namensErweiterungErlaubt && bezeichner.typ.hatAdjektiv) {
      throw GermanSkriptFehler.SyntaxFehler.ParseFehler(bezeichner.toUntyped(), "Bezeichner ohne Adjektiv",
        "Ein Adjektiv ist in diesem Bezeichner nicht erlaubt.")
    }
    return bezeichner
  }

  protected fun parseNomenOhneVornomen(namensErweiterungErlaubt: Boolean): AST.WortArt.Nomen =
      AST.WortArt.Nomen(null, parseGroßenBezeichner(namensErweiterungErlaubt))

  protected inline fun<reified VornomenT: TokenTyp.VORNOMEN> parseNomenMitVornomen(erwartetesVornomen: String, symbolErlaubt: Boolean): AST.WortArt.Nomen {
    val vornomen = expect<VornomenT>(erwartetesVornomen)
    if (vornomen is TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN) {
      // 'mein' darf nur in Methodendefinition vorkommen und 'dein' nur in Nachrichtenbereich
      when (vornomen.typ) {
        is TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN -> {
          if (!hierarchyContainsAnyNode(ASTKnotenID.IMPLEMENTIERUNG, ASTKnotenID.KLASSEN_DEFINITION)) {
            throw GermanSkriptFehler.SyntaxFehler.ParseFehler(vornomen.toUntyped(), null,
                "Das Possessivpronomen '${vornomen.wert}' darf nur innerhalb einer Klassen-Implementierung verwendet werden.")
          }
        }
        is TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.DEIN -> {
          if (!hierarchyContainsNode(ASTKnotenID.NACHRICHTEN_BEREICH)) {
            throw GermanSkriptFehler.SyntaxFehler.ParseFehler(vornomen.toUntyped(), null,
                "Das Possessivpronomen '${vornomen.wert}' darf nur in Nachrichtenbereichen verwendet werden.")
          }
        }
        is TokenTyp.VORNOMEN.JEDE -> {
          if (id != ASTKnotenID.FÜR_JEDE_SCHLEIFE) {
            throw GermanSkriptFehler.SyntaxFehler.ParseFehler(vornomen.toUntyped(), null,
            "Das Vornomen 'jeden', 'jeder', 'jedes' darf nur in einer Für-Jede-Schleife verwendet werden.")
          }
        }
      }
    }
    if (vornomen is TokenTyp.VORNOMEN.DEMONSTRATIV_PRONOMEN) {
      // Demonstrativpronomen dürfen nur in Konstruktoren und nur in Variablendeklarationen vorkommen
      if (!hierarchyContainsNode(ASTKnotenID.VARIABLEN_DEKLARATION) && !hierarchyContainsNode(ASTKnotenID.KLASSEN_DEFINITION)) {
        throw GermanSkriptFehler.SyntaxFehler.ParseFehler(vornomen.toUntyped(), null,
            "Die Demonstrativpronomen 'diese' und 'jene' dürfen nur in Konstruktoren (Klassendefinitionen) verwendet werden,\n" +
                "um weitere Eigenschaften zu erstellen.")
      }
    }
    val nomen = parseNomenOhneVornomen(symbolErlaubt)
    nomen.vornomen = vornomen
    return nomen
  }

  protected fun parseModulPfad(): List<TypedToken<TokenTyp.BEZEICHNER_GROSS>> {
    val modulPfad = mutableListOf<TypedToken<TokenTyp.BEZEICHNER_GROSS>>()
    while (peekType(1) == TokenTyp.DOPPEL_DOPPELPUNKT) {
      modulPfad += expect("Bezeichner")
      expect<TokenTyp.DOPPEL_DOPPELPUNKT>("'::'")
    }
    return modulPfad
  }

  protected inline fun<reified VornomenT: TokenTyp.VORNOMEN.ARTIKEL> parseParameter(erwarteterArtikel: String): AST.Definition.Parameter {
    val typ = parseTypMitArtikel<VornomenT>(erwarteterArtikel,
        symbolErlaubt = true, nomenErlaubt = true, adjektivErlaubt = true)
    val name = when (typ.name) {
      is AST.WortArt.Nomen -> parseOptional<AST.WortArt.Nomen, TokenTyp.BEZEICHNER_GROSS>() {
        parseNomenOhneVornomen(true)
      }
      is AST.WortArt.Adjektiv -> {
        val paramName = parseNomenOhneVornomen(true)
        paramName.vornomen = typ.name.vornomen
        paramName
      }
    } ?: (typ.name as AST.WortArt.Nomen)
    return AST.Definition.Parameter(typ, name)
  }

  protected fun<T> parseTypParameterOderArgumente(parser: () -> T): List<T> {
    var liste = emptyList<T>()
    val kleinerZeichen = peek()
    if (kleinerZeichen.wert == "<") {
      next()
      liste = parseListeMitEnde<T, TokenTyp.KOMMA, TokenTyp.OPERATOR>(
          false
      ) {
        parser()
      }
      val größerZeichen = next()
      if (größerZeichen.wert != ">") {
        throw GermanSkriptFehler.SyntaxFehler.ParseFehler(größerZeichen, ">")
      }
    }
    return liste
  }

  protected fun parseTypArgumente() =
      parseTypParameterOderArgumente { parseTypOhneArtikel(
          namensErweiterungErlaubt = false,
          nomenErlaubt = true,
          adjektivErlaubt = true
      ) }

  protected fun parseTypParameter() =
      parseTypParameterOderArgumente(::parseTypParam)

  protected fun parseTypParam(): AST.Definition.TypParam {
    val schnittstellen = mutableListOf<AST.TypKnoten>()
    if (peekType(0) !is TokenTyp.BEZEICHNER_GROSS || peekType(1) == TokenTyp.DOPPEL_DOPPELPUNKT) {
      while (true) {
        val adjektiv = parseTypOhneArtikel(
            namensErweiterungErlaubt = false, nomenErlaubt = false,
            adjektivErlaubt = true)
        schnittstellen += adjektiv
        if (peekType() == TokenTyp.KOMMA) {
          next()
        } else {
          break
        }
      }
    }
    val binder = parseNomenOhneVornomen(false)
    val elternKlasse = if (peekType() is TokenTyp.ALS_KLEIN) {
      next()
      parseTypOhneArtikel(
          namensErweiterungErlaubt = false,
          nomenErlaubt = true,
          adjektivErlaubt = false
      )
    } else {
      null
    }
    return AST.Definition.TypParam(binder, schnittstellen, elternKlasse)
  }

  protected fun parseTypOhneArtikel(
      namensErweiterungErlaubt: Boolean,
      nomenErlaubt: Boolean,
      adjektivErlaubt: Boolean
  ): AST.TypKnoten {
    val modulPfad = parseModulPfad()
    val wortArt = when (peekType()) {
      is TokenTyp.BEZEICHNER_GROSS ->
        // Wenn ein Symbol zum Namen erlaubt ist, dann ist auch ein Adjektiv zum Namen erlaubt
        if (nomenErlaubt) parseNomenOhneVornomen(namensErweiterungErlaubt)
        else null
      is TokenTyp.BEZEICHNER_KLEIN ->
        if (adjektivErlaubt) AST.WortArt.Adjektiv(null, expect("bezeichner"))
        else null
      else -> throw  GermanSkriptFehler.SyntaxFehler.ParseFehler(next(), "großer oder kleiner Bezeichner")
    }
    if (wortArt == null) {
      val erwartet = when {
        nomenErlaubt -> "Bezeichner"
        adjektivErlaubt -> "bezeichner"
        else -> ""
      }
      throw GermanSkriptFehler.SyntaxFehler.ParseFehler(next(), erwartet)
    }
    val typArgumente = parseTypArgumente()
    return AST.TypKnoten(modulPfad, wortArt, typArgumente)
  }

  protected inline fun <reified T: TokenTyp.VORNOMEN.ARTIKEL> parseTypMitArtikel(
      erwarteterArtikel: String,
      symbolErlaubt: Boolean,
      nomenErlaubt: Boolean,
      adjektivErlaubt: Boolean
  ): AST.TypKnoten {
    val artikel = expect<T>(erwarteterArtikel)
    val typ = parseTypOhneArtikel(symbolErlaubt, nomenErlaubt, adjektivErlaubt)
    typ.name.vornomen = artikel
    return typ
  }

  protected inline fun <ElementT, reified TrennerT: TokenTyp, reified StartTokenT: TokenTyp> parseListeMitStart(canBeEmpty: Boolean, elementParser: () -> ElementT): List<ElementT> {
    if (canBeEmpty && peekType() !is StartTokenT) {
      return emptyList()
    }
    überspringeLeereZeilen()
    val liste = mutableListOf(elementParser())
    while (peekType() is TrennerT) {
      next()
      überspringeLeereZeilen()
      liste.add(elementParser())
    }
    return liste
  }

  protected inline fun <ElementT, reified TrennerT: TokenTyp, reified EndTokenT: TokenTyp> parseListeMitEnde(canBeEmpty: Boolean, elementParser: () -> ElementT): List<ElementT> {
    if (canBeEmpty && peekType() is EndTokenT) {
      return emptyList()
    }
    überspringeLeereZeilen()
    val liste = mutableListOf(elementParser())
    while (peekType() is TrennerT) {
      next()
      überspringeLeereZeilen()
      liste.add(elementParser())
    }
    return liste
  }

  protected inline fun <reified T: TokenTyp, reified TrennerT: TokenTyp> parseListe(canBeEmpty: Boolean, erwartet: String): List<TypedToken<T>> {
    if (canBeEmpty && peekType() !is T) {
      return emptyList()
    }
    val liste = mutableListOf<TypedToken<T>>(expect(erwartet))
    überspringeLeereZeilen()
    while (peekType() is TrennerT) {
      next()
      überspringeLeereZeilen()
      liste.add(expect(erwartet))
    }
    return liste
  }

  // zum Parsen der Präpositionslisten bei einer Funktionsdefinition sowie beim Funktionsaufruf
  protected inline fun <ElementT, ParserT> parsePräpositionsListe(
          parser: () -> ParserT,
          combiner: (TypedToken<TokenTyp.BEZEICHNER_KLEIN>, ParserT) -> ElementT
  ): List<ElementT> {
    val list = mutableListOf<ElementT>()
    while (true) {
      // überspringe nur leere Zeile wenn dahinter eine Präposition kommt
      var i = 0
      while (peekType(i) is TokenTyp.NEUE_ZEILE) {
        i++
      }
      val nächstesToken = peek(i)
      if (nächstesToken.typ is TokenTyp.BEZEICHNER_KLEIN && präpositionsFälle.containsKey(nächstesToken.wert)
          && peekType(i+1) is TokenTyp.VORNOMEN && peekType(i+1) !is TokenTyp.VORNOMEN.JEDE) {
        überspringeLeereZeilen()
        list += combiner(expect("Präposition"), parser())
      } else {
        break
      }
    }
    return list
  }

  protected inline fun <ElementT, reified TokenT> parseOptional(parser: () -> ElementT): ElementT? {
    return if (peekType() is TokenT) {
      parser()
    } else {
      null
    }
  }

  protected inline fun<reified T: TokenTyp> parseOptional(): TypedToken<T>? {
    return if (peekType() is T) {
      expect<T>("nicht wichtig")
    } else {
      null
    }
  }

  fun parseAusdruck(
      mitVornomen: Boolean,
      optionalesIstNachVergleich: Boolean,
      linkerAusdruck: AST.Satz.Ausdruck? = null,
      inBedingungsTerm: Boolean = false
  ): AST.Satz.Ausdruck {

    fun parseFunktionOderKonstante(): AST.Satz.Ausdruck {
      val modulPfad = parseModulPfad()
      return when (peekType()) {
        is TokenTyp.BEZEICHNER_KLEIN -> subParse(Satz.Ausdruck.FunktionsAufruf(modulPfad, inBedingungsTerm))
        is TokenTyp.BEZEICHNER_GROSS -> AST.Satz.Ausdruck.Konstante(modulPfad, parseGroßenBezeichner(true))
        else -> throw GermanSkriptFehler.SyntaxFehler.ParseFehler(next(), "Bezeichner")
      }
    }

    fun parseEinzelnerAusdruck(mitArtikel: Boolean): AST.Satz.Ausdruck {
      var ausdruck = when (val tokenTyp = peekType()) {
        is TokenTyp.ZEICHENFOLGE -> AST.Satz.Ausdruck.Zeichenfolge(next().toTyped())
        is TokenTyp.ZAHL -> AST.Satz.Ausdruck.Zahl(next().toTyped())
        is TokenTyp.BOOLEAN -> AST.Satz.Ausdruck.Boolean(next().toTyped())
        is TokenTyp.NICHTS -> AST.Satz.Ausdruck.Nichts(next().toTyped())
        is TokenTyp.BEZEICHNER_KLEIN -> subParse(Satz.Ausdruck.FunktionsAufruf(emptyList(), inBedingungsTerm))
        is TokenTyp.WENN -> subParse(Satz.Ausdruck.Bedingung)
        is TokenTyp.VERSUCHE -> subParse(Satz.Ausdruck.VersucheFange)
        is TokenTyp.WERFE -> subParse(Satz.Ausdruck.Werfe)
        is TokenTyp.SUPER -> subParse(Satz.Ausdruck.SuperBereich)
        is TokenTyp.VORNOMEN -> {
          val subjekt = parseNomenAusdruck<TokenTyp.VORNOMEN>("Vornomen", true, inBedingungsTerm, false).third
          val nächterTokenTyp = peekType()
          // prüfe hier ob ein bedingter Aufruf geparst werden soll
          if (inBedingungsTerm && (nächterTokenTyp is TokenTyp.VORNOMEN || nächterTokenTyp is TokenTyp.BEZEICHNER_KLEIN)) {
            subParse(Satz.Ausdruck.BedingungsAusdruck(subjekt, inBedingungsTerm))
          } else {
            subjekt
          }
        }
        is TokenTyp.REFERENZ.ICH -> {
          if (!hierarchyContainsAnyNode(ASTKnotenID.IMPLEMENTIERUNG, ASTKnotenID.KLASSEN_DEFINITION)) {
            throw GermanSkriptFehler.SyntaxFehler.ParseFehler(next(), null,
                "Die Selbstreferenz 'Ich' darf nur innerhalb einer Klassen-Implementierung verwendet werden.")
          }
          AST.Satz.Ausdruck.SelbstReferenz(next().toTyped())
        }
        is TokenTyp.REFERENZ.DU -> {
          if (!hierarchyContainsNode(ASTKnotenID.NACHRICHTEN_BEREICH)) {
            throw GermanSkriptFehler.SyntaxFehler.ParseFehler(next(), null,
                "Die Nachrichtenbereichreferenz 'Du' darf nur in einem Nachrichtenbereich verwendet werden.")
          }
          AST.Satz.Ausdruck.NachrichtenObjektReferenz(next().toTyped())
        }
        is TokenTyp.OFFENE_KLAMMER -> {
          next()
          überspringeLeereZeilen()
          parseAusdruck(mitVornomen, optionalesIstNachVergleich, null, false).also {
            überspringeLeereZeilen()
            expect<TokenTyp.GESCHLOSSENE_KLAMMER>("')'")
          }
        }
        is TokenTyp.OPERATOR -> {
          if (currentToken != null &&
              currentToken!!.typ is TokenTyp.OPERATOR &&
              currentToken!!.toTyped<TokenTyp.OPERATOR>().typ.operator == Operator.MINUS) {
            throw GermanSkriptFehler.SyntaxFehler.ParseFehler(next(), null, "Es dürfen keine zwei '-' aufeinander folgen.")
          } else if (tokenTyp.operator != Operator.MINUS) {
            throw GermanSkriptFehler.SyntaxFehler.ParseFehler(next(), null)
          } else {
            AST.Satz.Ausdruck.Minus(next().toTyped(), parseEinzelnerAusdruck(false))
          }
        }
        is TokenTyp.BEZEICHNER_GROSS ->
          if (mitArtikel) when (peekType(1)) {
            is TokenTyp.DOPPELPUNKT -> subParse(Satz.Ausdruck.NachrichtenBereich(
                AST.Satz.Ausdruck.Variable(parseNomenOhneVornomen(true))
            ))
            else -> parseFunktionOderKonstante()
          }
          else {
            if (!hierarchyContainsAnyNode(ASTKnotenID.KLASSEN_DEFINITION) && peekType(1) is TokenTyp.DOPPELPUNKT) {
              subParse(Satz.Ausdruck.NachrichtenBereich(
                  AST.Satz.Ausdruck.Variable(parseNomenOhneVornomen(true))
              ))
            }
            else when (peekType(1)) {
              is TokenTyp.DOPPEL_DOPPELPUNKT -> parseFunktionOderKonstante()
              is TokenTyp.OFFENE_ECKIGE_KLAMMER ->
                subParse(Satz.Ausdruck.NomenAusdruck.IndexZugriff(parseNomenOhneVornomen(true), inBedingungsTerm))
              else -> AST.Satz.Ausdruck.Variable(parseNomenOhneVornomen(true))
            }
          }
        else -> throw GermanSkriptFehler.SyntaxFehler.ParseFehler(next())
      }
      while (true) {
        ausdruck = when (peekType()) {
          is TokenTyp.ALS_KLEIN -> {
            next()
            val typ = parseTypOhneArtikel(
                namensErweiterungErlaubt = false, nomenErlaubt = true,
                adjektivErlaubt = false
            )
            AST.Satz.Ausdruck.Konvertierung(ausdruck, typ)
          }
          is TokenTyp.DOPPELPUNKT -> {
            if (
                ausdruck !is AST.Satz.Ausdruck.Bedingung &&
                ausdruck !is AST.Satz.Ausdruck.NachrichtenBereich &&
                !inBedingungsTerm &&
                !hierarchyContainsNode(ASTKnotenID.KLASSEN_DEFINITION)
            ) {
              subParse(Satz.Ausdruck.NachrichtenBereich(ausdruck))
            }
            else return  ausdruck
          }
          else -> return ausdruck
        }
      }
    }

    // pratt parsing: https://matklad.github.io/2020/04/13/simple-but-powerful-pratt-parsing.html
    fun parseBinärenAusdruck(minBindungskraft: Double, letzterOp: Operator?, mitVornomen: Boolean, linkerA: AST.Satz.Ausdruck? = null) : AST.Satz.Ausdruck {
      var links = linkerA?: parseEinzelnerAusdruck(mitVornomen)

      loop@ while (true) {
        val operator = when (val tokenTyp = peekType()) {
          is TokenTyp.OPERATOR -> tokenTyp.operator
          else -> break@loop
        }
        val bindungsKraft = operator.bindungsKraft
        val linkeBindungsKraft = bindungsKraft + if (operator.assoziativität == Assoziativität.RECHTS) 0.1 else 0.0
        val rechteBindungsKraft = bindungsKraft + if (operator.assoziativität == Assoziativität.LINKS) 0.1 else 0.0
        if (linkeBindungsKraft < minBindungskraft) {
          break
        }

        val operatorToken = expect<TokenTyp.OPERATOR>("Operator")

        // Hinter den Operatoren 'kleiner' und 'größer' kann optional das Wort 'als' kommen
        if (operator.klasse == OperatorKlasse.VERGLEICH_ALS && peekType() == TokenTyp.ALS_KLEIN) {
          next()
        }

        // Das String-Interpolations germanskript.Token ist nur dafür da, dass innerhalb einer String Interpolation wieder der Nominativ verwendet wird
        val inStringInterpolation = parseOptional<TokenTyp.STRING_INTERPOLATION>() != null
        überspringeLeereZeilen()

        val rechts = parseBinärenAusdruck(rechteBindungsKraft, operator, mitVornomen = mitVornomen)
        if (optionalesIstNachVergleich &&
            (operator.klasse == OperatorKlasse.VERGLEICH_GLEICH || operator.klasse == OperatorKlasse.VERGLEICH_ALS)) {
          val ist = parseOptional<TokenTyp.ZUWEISUNG>()
          if (ist != null && ist.typ.numerus != Numerus.SINGULAR) {
            throw GermanSkriptFehler.SyntaxFehler.ParseFehler(ist.toUntyped())
          }
        }

        // istAnfang brauchen wir später für den Grammatikprüfer, da dieser dann den Kasus zurücksetzt
        val istAnfang = minBindungskraft == 0.0 || letzterOp!!.klasse != operator.klasse
        links = AST.Satz.Ausdruck.BinärerAusdruck(operatorToken, links, rechts, istAnfang, inStringInterpolation)
      }

      return links
    }

    return parseBinärenAusdruck(0.0, null, mitVornomen, linkerAusdruck)
  }

  protected inline fun<reified VornomenT: TokenTyp.VORNOMEN> parseNomenAusdruck(
      erwartetesVornomen: String,
      inBinärenAusdruck: Boolean,
      inBedingungsTerm: Boolean,
      adjektivErlaubt: Boolean
  ): Triple<AST.WortArt.Adjektiv?, AST.WortArt.Nomen, AST.Satz.Ausdruck> {
    val vornomen = expect<VornomenT>(erwartetesVornomen)
    val adjektiv = (if (adjektivErlaubt) parseOptional<TokenTyp.BEZEICHNER_KLEIN>() else null).let {
      if (it != null) AST.WortArt.Adjektiv(vornomen, it) else null
    }
    var modulPfad: List<TypedToken<TokenTyp.BEZEICHNER_GROSS>>? = null
    if (vornomen.typ == TokenTyp.VORNOMEN.ARTIKEL.UNBESTIMMT || vornomen.typ == TokenTyp.VORNOMEN.ETWAS) {
      modulPfad = parseModulPfad()
    }
    val bezeichner = parseGroßenBezeichner(modulPfad == null)
    var typArgumente = emptyList<AST.TypKnoten>()
    if (vornomen.typ == TokenTyp.VORNOMEN.ARTIKEL.UNBESTIMMT || vornomen.typ == TokenTyp.VORNOMEN.ETWAS) {
      typArgumente = parseTypArgumente()
    }
    val nomen = AST.WortArt.Nomen(vornomen, bezeichner)
    val nächstesToken = peek()
    val ausdruck = when (vornomen.typ) {
      is TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT -> when (nächstesToken.typ) {
        is TokenTyp.OFFENE_ECKIGE_KLAMMER -> subParse(Satz.Ausdruck.NomenAusdruck.IndexZugriff(nomen, inBedingungsTerm))
        is TokenTyp.VORNOMEN.ARTIKEL, is TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN -> {
          when (nächstesToken.wert) {
            "des", "der", "eines", "einer", "meiner", "meines", "deiner", "deines" ->
              subParse(Satz.Ausdruck.NomenAusdruck.EigenschaftsZugriff(nomen, inBinärenAusdruck, inBedingungsTerm))
            else -> AST.Satz.Ausdruck.Variable(nomen)
          }
        }
        else -> when(vornomen.typ) {
          TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT -> AST.Satz.Ausdruck.Variable(nomen)
          TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN -> subParse(Satz.Ausdruck.NomenAusdruck.SelbstEigenschaftsZugriff(nomen))
          TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.DEIN -> subParse(Satz.Ausdruck.NomenAusdruck.NachrichtenBereichsEigenschaftsZugriff(nomen))
          else -> throw GermanSkriptFehler.SyntaxFehler.ParseFehler(vornomen.toUntyped(), "bestimmter Artikel oder Possessivpronomen")
        }
      }
      is TokenTyp.VORNOMEN.ARTIKEL.UNBESTIMMT ->  {
        when (nächstesToken.typ) {
          is TokenTyp.OFFENE_ECKIGE_KLAMMER -> {
            val pluralTyp = AST.TypKnoten(modulPfad!!, nomen, typArgumente)
            subParse(Satz.Ausdruck.NomenAusdruck.Liste(pluralTyp, inBedingungsTerm))
          }
          else -> {
            val klasse = AST.TypKnoten(modulPfad!!, nomen, typArgumente)
            subParse(Satz.Ausdruck.NomenAusdruck.ObjektInstanziierung(klasse, inBedingungsTerm))
          }
        }
      }
      is TokenTyp.VORNOMEN.ETWAS -> {
        val schnittstelle = AST.TypKnoten(modulPfad!!, nomen, typArgumente)
        if (peekType(0) == TokenTyp.OFFENE_KLAMMER) {
          subParse(Satz.Ausdruck.NomenAusdruck.Lambda(schnittstelle, inBedingungsTerm))
        } else {
          var i = 1
          while (peekType(i) == TokenTyp.NEUE_ZEILE) {
            i++
          }
          when (peekType(i)) {
            TokenTyp.VERB, TokenTyp.EIGENSCHAFT, TokenTyp.ALS_GROß, is TokenTyp.VORNOMEN.DEMONSTRATIV_PRONOMEN ->
              subParse(Satz.Ausdruck.NomenAusdruck.AnonymeKlasse(schnittstelle, inBedingungsTerm))
            else -> subParse(Satz.Ausdruck.NomenAusdruck.Lambda(schnittstelle, inBedingungsTerm))
          }
        }
      }
      TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN ->
        if (nächstesToken.typ is TokenTyp.OFFENE_ECKIGE_KLAMMER) subParse(Satz.Ausdruck.NomenAusdruck.IndexZugriff(nomen, inBedingungsTerm))
        else subParse(Satz.Ausdruck.NomenAusdruck.SelbstEigenschaftsZugriff(nomen))
      TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.DEIN ->
        if (nächstesToken.typ is TokenTyp.OFFENE_ECKIGE_KLAMMER) subParse(Satz.Ausdruck.NomenAusdruck.IndexZugriff(nomen, inBedingungsTerm))
        else subParse(Satz.Ausdruck.NomenAusdruck.NachrichtenBereichsEigenschaftsZugriff(nomen))
      is TokenTyp.VORNOMEN.DEMONSTRATIV_PRONOMEN -> throw GermanSkriptFehler.SyntaxFehler.ParseFehler(vornomen.toUntyped(), null,
          "Die Demonstrativpronomen 'diese' und 'jene' dürfen nicht in Ausdrücken verwendet werden.")
      else -> throw Exception("Dieser Fall sollte nie ausgeführt werden.")
    }

    return when (peekType()){
      is TokenTyp.ALS_KLEIN -> {
        next()
        val typ = parseTypOhneArtikel(
            namensErweiterungErlaubt = false, nomenErlaubt = true,
            adjektivErlaubt = false
        )
        Triple(adjektiv, nomen, AST.Satz.Ausdruck.Konvertierung(ausdruck, typ))
      }
      is TokenTyp.OPERATOR -> {
         val operatorAusdruck  =
             if (inBinärenAusdruck) ausdruck
             else parseAusdruck(
                 mitVornomen = true,
                 optionalesIstNachVergleich = false,
                 linkerAusdruck = ausdruck,
                 inBedingungsTerm = inBedingungsTerm
             )
        Triple(adjektiv, nomen, operatorAusdruck)
      }
      else -> Triple(adjektiv, nomen, ausdruck)
    }
  }

  fun parseArgument(inBedingungsTerm: Boolean): AST.Argument {
    var (adjektiv, nomen, wert) = parseNomenAusdruck<TokenTyp.VORNOMEN>(
        "Vornomen", inBinärenAusdruck = false, inBedingungsTerm = inBedingungsTerm, adjektivErlaubt = true)

    if (adjektiv == null && wert is AST.Satz.Ausdruck.Variable) {
      wert = when(peekType()) {
        is TokenTyp.NEUE_ZEILE,
        is TokenTyp.PUNKT,
        is TokenTyp.AUSRUFEZEICHEN,
        is TokenTyp.KOMMA,
        is TokenTyp.BEZEICHNER_KLEIN,
        is TokenTyp.DOPPELPUNKT,
        is TokenTyp.GESCHLOSSENE_KLAMMER -> wert
        else -> parseAusdruck(mitVornomen = false, optionalesIstNachVergleich = false, inBedingungsTerm = inBedingungsTerm)
      }
    }
    return AST.Argument(adjektiv, nomen, wert)
  }

  protected fun<T> parseBereich(erwartetesEndToken: TokenTyp = TokenTyp.PUNKT, parser: () -> T): T {
    expect<TokenTyp.DOPPELPUNKT>("':'")
    val result = parser()
    überspringeLeereZeilen()
    val endToken = next()
    if (endToken.typ != erwartetesEndToken){
      throw GermanSkriptFehler.SyntaxFehler.ParseFehler(endToken, erwartetesEndToken.toString())
    }
    return result
  }

  protected fun runParseBereich(erwartetesEndToken: TokenTyp = TokenTyp.PUNKT, runner: () -> Unit) {
    expect<TokenTyp.DOPPELPUNKT>("':'")
    überspringeLeereZeilen()
    while (peekType() != erwartetesEndToken) {
      runner()
      überspringeLeereZeilen()
    }
    next()
  }

  protected fun parseSätze(endToken: TokenTyp = TokenTyp.PUNKT): AST.Satz.Bereich =
      parseBereich(endToken) { subParse(Programm(id)) }.programm
  // endregion

  class Programm(override val id: ASTKnotenID, private val lexer: Lexer? = null): SubParser<AST.Programm>() {

    val istInGlobalenBereich = lexer != null

    override fun parseImpl(): AST.Programm {
      val programmStart = if (istInGlobalenBereich) next() else null
      val definitionen = AST.DefinitionsContainer()
      val sätze = mutableListOf<AST.Satz>()
      var hauptProgrammEnde = false

      loop@ while (true) {
        überspringeLeereZeilen()
        when {
          parseDefinition(definitionen)?.also { definition ->
            if (definition is AST.Definition.Import) {
              lexer!!.importiereDatei(definition)
            }
          } != null -> Unit
          parseSatz()?.also { satz ->
            // Sätze werden nur von der Hauptdatei eingelesen
            if (!hauptProgrammEnde) {
              sätze += satz
            }
          } != null -> Unit
          else -> when (peekType()) {
            is TokenTyp.HAUPT_PROGRAMM_ENDE -> {
              next()
              hauptProgrammEnde = true
            }
            is TokenTyp.EOF, TokenTyp.PUNKT, TokenTyp.AUSRUFEZEICHEN -> break@loop
            else -> throw GermanSkriptFehler.SyntaxFehler.ParseFehler(next())
          }
        }
      }
      return AST.Programm(programmStart, definitionen, AST.Satz.Bereich(sätze))
    }

    fun parseSatz(): AST.Satz? {
      val nextToken = peek()
      return when (nextToken.typ) {
        is TokenTyp.INTERN -> subParse(Satz.Intern)
        is TokenTyp.VORNOMEN -> {
          if (peekType(2) is TokenTyp.OFFENE_ECKIGE_KLAMMER)
            subParse(Satz.IndexZuweisung)
          else {
            if (peekType(1) is TokenTyp.NEU || peekType(2) is TokenTyp.ZUWEISUNG)
              subParse(Satz.VariablenDeklaration)
            else parseAusdruck(mitVornomen = true, optionalesIstNachVergleich = false, linkerAusdruck = null)
          }
        }
        is TokenTyp.DOPPELPUNKT -> AST.Satz.Bereich(parseSätze(TokenTyp.PUNKT).sätze)
        is TokenTyp.SOLANGE -> subParse(Satz.SolangeSchleife)
        is TokenTyp.FORTFAHREN, is TokenTyp.ABBRECHEN -> subParse(Satz.SchleifenKontrolle)
        is TokenTyp.ZURÜCK -> subParse(Satz.Zurückgabe)
        is TokenTyp.BEZEICHNER_KLEIN ->
          when (nextToken.wert) {
            "gebe" -> subParse(Satz.Zurückgabe)
            "für" -> subParse(Satz.FürJedeSchleife)
            "importiere" -> when {
              peekType(1) is TokenTyp.ZEICHENFOLGE -> null
              else -> subParse(Satz.Ausdruck.FunktionsAufruf(emptyList(), false))
            }
            "verwende" -> when {
              peekType(1) is TokenTyp.BEZEICHNER_GROSS -> null
              else -> subParse(Satz.Ausdruck.FunktionsAufruf(emptyList(), false))
            }
            else -> subParse(Satz.Ausdruck.FunktionsAufruf(emptyList(), false))
          }
        is TokenTyp.EOF, TokenTyp.PUNKT, TokenTyp.AUSRUFEZEICHEN, TokenTyp.HAUPT_PROGRAMM_ENDE -> null
        else -> parseAusdruck(mitVornomen = true, optionalesIstNachVergleich = false, linkerAusdruck = null)
      }
    }

    fun überprüfeDoppelteDefinition(container: AST.DefinitionsContainer, typDef: AST.Definition.Typdefinition) {
      val typName = typDef.namensToken.wert.capitalize()
      if (container.definierteTypen.containsKey(typName)) {
        throw GermanSkriptFehler.DoppelteDefinition.Typ(typDef.namensToken,
            container.definierteTypen.getValue(typName).namensToken)
      }
    }

    fun parseDefinition(container: AST.DefinitionsContainer): AST.Definition? {
      val nextToken = peek()
      return when(nextToken.typ) {
        is TokenTyp.DEKLINATION -> subParse(Definition.DeklinationsDefinition).also { deklination ->
          container.deklinationen += deklination
        }
        is TokenTyp.MODUL -> subParse(Definition.Modul).also { modul ->
          mergeModule(container, modul)
        }
        is TokenTyp.NOMEN -> subParse(Definition.Klasse).also { klasse ->
          überprüfeDoppelteDefinition(container, klasse)
          container.definierteTypen[klasse.namensToken.wert] = klasse
        }
        is TokenTyp.ADJEKTIV -> subParse(Definition.Schnittstelle).also { schnittstelle ->
          überprüfeDoppelteDefinition(container, schnittstelle)
          var schnittstellenName = schnittstelle.namensToken.wert.capitalize()
          // Das Adjektiv wird nominalisiert, indem es groß geschrieben wird und ein 'e' dran gehangen wird,
          // falls noch kein 'e' am Ende vorhanden ist
          if (!schnittstellenName.endsWith('e')) {
            schnittstellenName += "e"
          }
          container.definierteTypen[schnittstellenName] = schnittstelle
        }
        is TokenTyp.VERB -> subParse(Definition.Funktion(false)).also { funktion ->
          container.funktionsListe += funktion
        }
        is TokenTyp.ALIAS -> subParse(Definition.Alias).also { alias ->
          überprüfeDoppelteDefinition(container, alias)
          container.definierteTypen[alias.name.bezeichner.wert] = alias
        }
        is TokenTyp.KONSTANTE -> subParse(Definition.Konstante).also { konstante ->
          if (container.konstanten.containsKey(konstante.name.wert)) {
            throw GermanSkriptFehler.DoppelteDefinition.Konstante(konstante.name.toUntyped(),
              container.konstanten.getValue(konstante.name.wert))
          }
          container.konstanten[konstante.name.wert] = konstante
        }
        is TokenTyp.IMPLEMENTIERE -> subParse(Definition.Implementierung).also { implementierung ->
          container.implementierungen += implementierung
        }
        is TokenTyp.BEZEICHNER_KLEIN -> {
          when (nextToken.wert) {
            "importiere" -> subParse(Definition.Import)
            "verwende" -> subParse(Definition.Verwende).also { verwende ->
              container.verwende += verwende
            }
            else -> null
          }
        }
        else -> null
      }
    }

    fun mergeModule(container: AST.DefinitionsContainer, modul: AST.Definition.Modul) {
      val modulName = modul.name.wert
      container.module.merge(modulName, modul) {
        modulVorhanden, moduleNeu ->
        // Funktionen und Methoden werden einfach hinzugefügt, der Definierer übernimmt hier die Aufgabe,
        // doppelte Definitionen zu erkennen
        modulVorhanden.definitionen.funktionsListe.addAll(moduleNeu.definitionen.funktionsListe)

        // bei den Klassen müssen wir doppelte Definitionen erkennen
        for ((typName, typ) in moduleNeu.definitionen.definierteTypen) {
          überprüfeDoppelteDefinition(modulVorhanden.definitionen, typ)
          modulVorhanden.definitionen.definierteTypen[typName] = typ
        }

        // Die Module müssen mit den anderen Modulen gemergt werden
        for (anderesModul in moduleNeu.definitionen.module.values) {
          mergeModule(modulVorhanden.definitionen, anderesModul)
        }

        // Deklinationen können einfach hinzugefügt werden. Doppelte Deklinationen sind erlaubt.
        modulVorhanden.definitionen.deklinationen.addAll(moduleNeu.definitionen.deklinationen)

        modulVorhanden
      }
    }
  }

  class BedingungsTerm(private val token: Token): SubParser<AST.Satz.BedingungsTerm>() {
    override val id = ASTKnotenID.BEDINGUNGS_TERM

    override fun parseImpl(): AST.Satz.BedingungsTerm {
      val bedingung = parseAusdruck(mitVornomen = true, optionalesIstNachVergleich = true, inBedingungsTerm = true)
      val sätze = parseSätze()
      return AST.Satz.BedingungsTerm(token, bedingung, sätze)
    }
  }

  sealed class Satz<T: AST.Satz>(): SubParser<T>() {
    override fun bewacheKnoten() {
      if (parentNodeId == ASTKnotenID.MODUL) {
        throw GermanSkriptFehler.SyntaxFehler.ParseFehler(next(), null, "In einem Modul sind nur weitere Definitionen und keine Sätze erlaubt.")
      }
    }

    object Intern: Satz<AST.Satz.Intern>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.INTERN

      override fun bewacheKnoten() {
        super.bewacheKnoten()
        if (!hierarchyContainsAnyNode(ASTKnotenID.FUNKTIONS_DEFINITION, ASTKnotenID.IMPLEMENTIERUNG)) {
          throw GermanSkriptFehler.SyntaxFehler.ParseFehler(next(), null, "Das Schlüsselwort 'intern' darf nur in einer Funktionsdefinition stehen.")
        }
      }

      override fun parseImpl(): AST.Satz.Intern {
        val token = expect<TokenTyp.INTERN>("intern")
        if (peekType() !is TokenTyp.PUNKT) {
          throw GermanSkriptFehler.SyntaxFehler.ParseFehler(next(), ".")
        }
        return AST.Satz.Intern(token)
      }
    }

    object VariablenDeklaration: Satz<AST.Satz.VariablenDeklaration>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.VARIABLEN_DEKLARATION

      override fun parseImpl(): AST.Satz.VariablenDeklaration {
        val artikel = expect<TokenTyp.VORNOMEN>("Artikel")
        // man kann nur eigene Eigenschaften neu zuweisen
        if (artikel.typ == TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.DEIN) {
          throw GermanSkriptFehler.SyntaxFehler.ParseFehler(artikel.toUntyped(), null, "Neuzuweisungen mit 'dein' funktionieren nicht.\n" +
              "Man kann die Eigenschaften eines anderen Objekts nur lesen.")
        }
        val neu = if (artikel.typ == TokenTyp.VORNOMEN.ARTIKEL.UNBESTIMMT) {
          parseOptional<TokenTyp.NEU>()
        } else {
          null
        }
        val name = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner")
        val nomen = AST.WortArt.Nomen(artikel, name)
        val zuweisung = expect<TokenTyp.ZUWEISUNG>("Zuweisung")
        überspringeLeereZeilen()
        val ausdruck = parseAusdruck(mitVornomen = true, optionalesIstNachVergleich = false)
        return AST.Satz.VariablenDeklaration(nomen, neu, zuweisung, ausdruck)
      }
    }

    object IndexZuweisung: Satz<AST.Satz>() {
      override val id = ASTKnotenID.LISTEN_ELEMENT_ZUWEISUNG

      override fun parseImpl(): AST.Satz {
        val name = parseNomenMitVornomen<TokenTyp.VORNOMEN>("Vornomen", true)
        if (name.vornomen!!.typ is TokenTyp.VORNOMEN.ARTIKEL.UNBESTIMMT) {
          throw GermanSkriptFehler.SyntaxFehler.ParseFehler(name.vornomen!!.toUntyped(), "Vornomen",
            "Bei Index-Zuweisungen sind unbestimmte Artikel nicht erlaubt.")
        }
        expect<TokenTyp.OFFENE_ECKIGE_KLAMMER>("'['")
        val index = parseAusdruck(mitVornomen = false, optionalesIstNachVergleich = false)
        expect<TokenTyp.GESCHLOSSENE_ECKIGE_KLAMMER>("']'")
        return if (peekType() !is TokenTyp.ZUWEISUNG) {
          AST.Satz.Ausdruck.IndexZugriff(name, index)
        } else {
          val zuweisung = expect<TokenTyp.ZUWEISUNG>("'ist'")
          val wert = parseAusdruck(mitVornomen = true, optionalesIstNachVergleich = false)
          AST.Satz.IndexZuweisung(name, index, zuweisung, wert)
        }
      }
    }

    object Zurückgabe : Satz<AST.Satz.Zurückgabe>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.ZURÜCKGABE

      override fun bewacheKnoten() {
        if (!hierarchyContainsAnyNode(
                ASTKnotenID.FUNKTIONS_DEFINITION,
                ASTKnotenID.KONVERTIERUNGS_DEFINITION,
                ASTKnotenID.EIGENSCHAFTS_DEFINITION
            )) {
          throw GermanSkriptFehler.SyntaxFehler.ParseFehler(next(), null, "'gebe ... zurück' darf hier nicht stehen.")
        }
      }

      override fun parseImpl(): AST.Satz.Zurückgabe {
        val zurück = parseOptional<TokenTyp.ZURÜCK>()
        return if (zurück != null) {
          AST.Satz.Zurückgabe(zurück.toUntyped(), AST.Satz.Ausdruck.Nichts(zurück.changeType(TokenTyp.NICHTS)))
        } else {
          val gebe = parseKleinesSchlüsselwort("gebe")
          überspringeLeereZeilen()
          val ausdruck = parseAusdruck(mitVornomen = true, optionalesIstNachVergleich = false)
          überspringeLeereZeilen()
          expect<TokenTyp.ZURÜCK>("'zurück'")
          AST.Satz.Zurückgabe(gebe.toUntyped(), ausdruck)
        }
      }
    }

    object SolangeSchleife: Satz<AST.Satz.SolangeSchleife>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.SCHLEIFE

      override fun parseImpl(): AST.Satz.SolangeSchleife {
        val solange = expect<TokenTyp.SOLANGE>("'solange'")
        return AST.Satz.SolangeSchleife(subParse(BedingungsTerm(solange.toUntyped())))
      }
    }

    object FürJedeSchleife: Satz<AST.Satz.FürJedeSchleife>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.FÜR_JEDE_SCHLEIFE

      override fun parseImpl(): AST.Satz.FürJedeSchleife {
        val für = parseKleinesSchlüsselwort("für")
        val singular = parseNomenMitVornomen<TokenTyp.VORNOMEN.JEDE>("'jeden', 'jede' oder 'jedes'", true)
        val binder = when (peekType()) {
          is TokenTyp.BEZEICHNER_GROSS -> parseNomenOhneVornomen(true)
          else -> singular
        }
        val nächstesToken = peek()
        var liste: AST.Satz.Ausdruck? = null
        var reichweite: AST.Satz.Reichweite? = null
        if (nächstesToken.typ is TokenTyp.BEZEICHNER_KLEIN) {
          when (nächstesToken.wert) {
            "in" -> {
              parseKleinesSchlüsselwort("in")
              liste = parseAusdruck(mitVornomen = true, optionalesIstNachVergleich = false, inBedingungsTerm = true)
            }
            "von" -> {
              parseKleinesSchlüsselwort("von")
              val anfang = parseAusdruck(mitVornomen = true, optionalesIstNachVergleich = false, inBedingungsTerm = true)
              expect<TokenTyp.BIS>("'bis'")
              val ende = if(peek().wert == "zu") {
                parseKleinesSchlüsselwort("zu")
                parseNomenAusdruck<TokenTyp.VORNOMEN>("'Vornomen'",
                    inBinärenAusdruck = false, inBedingungsTerm = true, adjektivErlaubt = false).third
              } else {
                parseAusdruck(mitVornomen = false, optionalesIstNachVergleich = false, inBedingungsTerm = true)
              }
              reichweite = AST.Satz.Reichweite(anfang, ende)
            }
            else -> throw GermanSkriptFehler.SyntaxFehler.ParseFehler(nächstesToken, "'in' oder 'von ... bis'")
          }
        } else if (singular.istSymbol) {
          throw GermanSkriptFehler.SyntaxFehler.ParseFehler(singular.bezeichner.toUntyped(), "Bezeichner",
              "In der Für-Jede-Schleife ohne 'in' oder 'von ... bis' ist ein Singular, dass nur aus einem Symbol besteht nicht erlaubt.")
        }
        val sätze = parseSätze()
        return AST.Satz.FürJedeSchleife(für, singular, binder, liste, reichweite, sätze)
      }
    }

    object SchleifenKontrolle: Satz<AST.Satz.SchleifenKontrolle>() {
      override val id: ASTKnotenID = ASTKnotenID.SCHLEIFENKONTROLLE

      override fun bewacheKnoten() {
        if (!hierarchyContainsAnyNode(ASTKnotenID.SCHLEIFE, ASTKnotenID.FÜR_JEDE_SCHLEIFE)) {
          val schlüsselwort = next()
          throw GermanSkriptFehler.SyntaxFehler.ParseFehler(schlüsselwort, null, "Das Schlüsselwort '${schlüsselwort.wert}' darf nur in einer Schleife verwendet werden.")
        }
      }

      override fun parseImpl(): AST.Satz.SchleifenKontrolle {
        return when (peekType()) {
          TokenTyp.FORTFAHREN -> AST.Satz.SchleifenKontrolle.Fortfahren
          TokenTyp.ABBRECHEN -> AST.Satz.SchleifenKontrolle.Abbrechen
          else -> throw Exception("Entweder 'fortfahren' oder 'abbrechen'")
        }.also { next() }
      }
    }

    sealed class Ausdruck<T: AST.Satz.Ausdruck>: SubParser<T>() {

      fun parsePräpositionsArgumente(inBedingungsTerm: Boolean): List<AST.PräpositionsArgumente> {
        return parsePräpositionsListe(
            { parseListeMitStart<AST.Argument, TokenTyp.KOMMA, TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT>(false) {
              parseArgument(inBedingungsTerm)
            } }
        ) { präposition, argumente ->
          AST.PräpositionsArgumente(AST.Präposition(präposition), argumente)
        }
      }

      sealed class NomenAusdruck<T: AST.Satz.Ausdruck>(protected val nomen: AST.WortArt.Nomen, protected val inBedingungsTerm: Boolean): Ausdruck<T>() {
        class Liste(private val pluralTyp: AST.TypKnoten, inBedingungsTerm: Boolean)
          : NomenAusdruck<AST.Satz.Ausdruck.Liste>(pluralTyp.name as AST.WortArt.Nomen, inBedingungsTerm) {

          override val id: ASTKnotenID
            get() = ASTKnotenID.LISTE

          override fun parseImpl(): AST.Satz.Ausdruck.Liste {
            expect<TokenTyp.OFFENE_ECKIGE_KLAMMER>("'['")
            val elemente = parseListeMitEnde<AST.Satz.Ausdruck, TokenTyp.KOMMA, TokenTyp.GESCHLOSSENE_ECKIGE_KLAMMER>(true)
              { parseAusdruck(mitVornomen = true, optionalesIstNachVergleich = false) }
            überspringeLeereZeilen()
            expect<TokenTyp.GESCHLOSSENE_ECKIGE_KLAMMER>("']'")
            return AST.Satz.Ausdruck.Liste(pluralTyp, elemente)
          }
        }

        class IndexZugriff(nomen: AST.WortArt.Nomen, inBedingungsTerm: Boolean)
          : NomenAusdruck<AST.Satz.Ausdruck.IndexZugriff>(nomen, inBedingungsTerm) {
          override val id: ASTKnotenID
            get() = ASTKnotenID.LISTEN_ELEMENT

          override fun parseImpl(): AST.Satz.Ausdruck.IndexZugriff {
            expect<TokenTyp.OFFENE_ECKIGE_KLAMMER>("'['")
            val index = parseAusdruck(mitVornomen = false, optionalesIstNachVergleich = false)
            expect<TokenTyp.GESCHLOSSENE_ECKIGE_KLAMMER>("']'")
            return AST.Satz.Ausdruck.IndexZugriff(nomen, index)
          }
        }

        class ObjektInstanziierung(protected val klasse: AST.TypKnoten, inBedingungsTerm: Boolean)
          : NomenAusdruck<AST.Satz.Ausdruck.ObjektInstanziierung>(klasse.name as AST.WortArt.Nomen, inBedingungsTerm) {
          override val id: ASTKnotenID
            get() = ASTKnotenID.OBJEKT_INSTANZIIERUNG

          override fun parseImpl(): AST.Satz.Ausdruck.ObjektInstanziierung {
            val eigenschaftsZuweisungen = when (peekType()) {
              is TokenTyp.BEZEICHNER_KLEIN -> {
                parseKleinesSchlüsselwort("mit")
                parseListeMitStart<AST.Argument, TokenTyp.KOMMA, TokenTyp.VORNOMEN>(false) {
                  parseArgument(inBedingungsTerm)
                }
              }
              else -> emptyList()
            }

            return AST.Satz.Ausdruck.ObjektInstanziierung(klasse, eigenschaftsZuweisungen.toMutableList())
          }
        }

        class EigenschaftsZugriff(nomen: AST.WortArt.Nomen, private val inBinärenAusdruck: Boolean, inBedingungsTerm: Boolean):
            NomenAusdruck<AST.Satz.Ausdruck.EigenschaftsZugriff>(nomen, inBedingungsTerm) {
          override val id: ASTKnotenID
            get() = ASTKnotenID.EIGENSCHAFTS_ZUGRIFF

          override fun parseImpl(): AST.Satz.Ausdruck.EigenschaftsZugriff {
            val objekt = parseNomenAusdruck<TokenTyp.VORNOMEN>(
                "Vornomen", inBinärenAusdruck, inBedingungsTerm, false).third
            return AST.Satz.Ausdruck.EigenschaftsZugriff(nomen, objekt)
          }
        }

        class SelbstEigenschaftsZugriff(val eigenschaftsName: AST.WortArt.Nomen):
            NomenAusdruck<AST.Satz.Ausdruck.SelbstEigenschaftsZugriff>(eigenschaftsName, false) {

          override val id: ASTKnotenID = ASTKnotenID.SelbstEigenschaftsZugriff

          override fun bewacheKnoten() {
            if (!hierarchyContainsAnyNode(ASTKnotenID.IMPLEMENTIERUNG, ASTKnotenID.KLASSEN_DEFINITION)) {
              val vornomen = eigenschaftsName.vornomen!!
              throw GermanSkriptFehler.SyntaxFehler.ParseFehler(vornomen.toUntyped(), null,
                  "Das Possessivpronomen '${vornomen.wert}' darf nur innerhalb einer Klassen-Implementierung verwendet werden.")
            }
          }

          override fun parseImpl(): AST.Satz.Ausdruck.SelbstEigenschaftsZugriff {
            return AST.Satz.Ausdruck.SelbstEigenschaftsZugriff(eigenschaftsName)
          }

        }

        class NachrichtenBereichsEigenschaftsZugriff(val eigenschaftsName: AST.WortArt.Nomen):
            NomenAusdruck<AST.Satz.Ausdruck.NachrichtenObjektEigenschaftsZugriff>(eigenschaftsName, false) {

          override val id: ASTKnotenID = ASTKnotenID.NachrichtenBereichsEigenschaftsZugriff

          override fun bewacheKnoten() {
            if (!hierarchyContainsNode(ASTKnotenID.NACHRICHTEN_BEREICH)) {
              val vornomen = eigenschaftsName.vornomen!!
              throw GermanSkriptFehler.SyntaxFehler.ParseFehler(vornomen.toUntyped(), null,
                  "Das Possessivpronomen '${vornomen.wert}' darf nur in Nachrichtenbereichen verwendet werden.")
            }
          }

          override fun parseImpl(): AST.Satz.Ausdruck.NachrichtenObjektEigenschaftsZugriff {
            return AST.Satz.Ausdruck.NachrichtenObjektEigenschaftsZugriff(eigenschaftsName)
          }

        }

        class Lambda(val typKnoten: AST.TypKnoten, inBedingungsTerm: Boolean):
            NomenAusdruck<AST.Satz.Ausdruck.Lambda>(typKnoten.name as AST.WortArt.Nomen, inBedingungsTerm) {
          override val id: ASTKnotenID = ASTKnotenID.Lambda

          override fun parseImpl(): AST.Satz.Ausdruck.Lambda {
            val bindings = if (peekType() is TokenTyp.OFFENE_KLAMMER) {
              expect<TokenTyp.OFFENE_KLAMMER>("'('")
              parseListeMitEnde<AST.WortArt.Nomen, TokenTyp.KOMMA, TokenTyp.GESCHLOSSENE_KLAMMER>(true) {
                parseNomenOhneVornomen(true)
              }.also { expect<TokenTyp.GESCHLOSSENE_KLAMMER>("')'") }
            } else emptyList()
            val körper = parseSätze()
            return AST.Satz.Ausdruck.Lambda(typKnoten, bindings, körper)
          }
        }

        class AnonymeKlasse(val typKnoten: AST.TypKnoten, inBedingungsTerm: Boolean)
          : NomenAusdruck<AST.Satz.Ausdruck.AnonymeKlasse>(typKnoten.name as AST.WortArt.Nomen, inBedingungsTerm) {
          override val id: ASTKnotenID = ASTKnotenID.ANONYME_KLASSE

          override fun parseImpl(): AST.Satz.Ausdruck.AnonymeKlasse {
            val körper = subParse(Definition.ImplementierungsBereich(true))
            return AST.Satz.Ausdruck.AnonymeKlasse(typKnoten, körper)
          }
        }
      }

      class FunktionsAufruf(val modulPfad: List<TypedToken<TokenTyp.BEZEICHNER_GROSS>>, val inBedingungsTerm: Boolean): Ausdruck<AST.Satz.Ausdruck.FunktionsAufruf>() {
        override val id: ASTKnotenID = ASTKnotenID.FUNKTIONS_AUFRUF

        override fun parseImpl(): AST.Satz.Ausdruck.FunktionsAufruf {
          val verb = expect<TokenTyp.BEZEICHNER_KLEIN>("bezeichner")
          val typArgumente = parseTypArgumente()
          val objekt = parseOptional<AST.Argument, TokenTyp.VORNOMEN> {
            parseArgument(inBedingungsTerm)
          }
          val reflexivPronomen = if (objekt == null) parseOptional<TokenTyp.REFLEXIV_PRONOMEN>() else null
          when (reflexivPronomen?.typ) {
            is TokenTyp.REFLEXIV_PRONOMEN.ERSTE_FORM -> {
              if (!hierarchyContainsAnyNode(ASTKnotenID.IMPLEMENTIERUNG, ASTKnotenID.KLASSEN_DEFINITION)) {
                throw GermanSkriptFehler.SyntaxFehler.UngültigerBereich(reflexivPronomen.toUntyped(),
                    "Das Reflexivpronomen '${reflexivPronomen.typ.pronomen}' kann nur innerhalb einer Klassen-Implementierung verwendet werden.")
              }
            }
            is TokenTyp.REFLEXIV_PRONOMEN.ZWEITE_FORM -> {
              if (!hierarchyContainsAnyNode(ASTKnotenID.NACHRICHTEN_BEREICH)) {
                throw GermanSkriptFehler.SyntaxFehler.UngültigerBereich(reflexivPronomen.toUntyped(),
                    "Das Reflexivpronomen '${reflexivPronomen.typ.pronomen}' kann nur in einem Nachrichtenbereich verwendet werden.")
              }
            }
          }
          val präpositionen = parsePräpositionsArgumente(inBedingungsTerm)
          val suffix = parseOptional<TokenTyp.BEZEICHNER_KLEIN>()
          return AST.Satz.Ausdruck.FunktionsAufruf(typArgumente, modulPfad, null, verb, objekt, reflexivPronomen, präpositionen, suffix)
        }
      }

      object Bedingung: Ausdruck<AST.Satz.Ausdruck.Bedingung>() {
        override val id = ASTKnotenID.BEDINGUNG

        override fun parseImpl(): AST.Satz.Ausdruck.Bedingung {
          val bedingungen = mutableListOf<AST.Satz.BedingungsTerm>()

          val wenn = expect<TokenTyp.WENN>("'wenn'")
          überspringeLeereZeilen()
          bedingungen += subParse(BedingungsTerm(wenn.toUntyped()))

          überspringeLeereZeilen()
          while (peekType() is TokenTyp.SONST) {
            val sonst =expect<TokenTyp.SONST>("'sonst'")
            if (peekType() !is TokenTyp.WENN) {
              val sätze = parseSätze()
              return AST.Satz.Ausdruck.Bedingung(bedingungen, AST.Satz.Ausdruck.Sonst(sonst, sätze))
            }
            expect<TokenTyp.WENN>("'wenn'")
            bedingungen += subParse(BedingungsTerm(sonst.toUntyped()))
            überspringeLeereZeilen()
          }
          return AST.Satz.Ausdruck.Bedingung(bedingungen, null)
        }
      }

      class NachrichtenBereich(private val objektAusdruck: AST.Satz.Ausdruck): Ausdruck<AST.Satz.Ausdruck.NachrichtenBereich>() {
        override val id: ASTKnotenID = ASTKnotenID.NACHRICHTEN_BEREICH

        override fun parseImpl(): AST.Satz.Ausdruck.NachrichtenBereich {
          val sätze = parseSätze(TokenTyp.AUSRUFEZEICHEN)
          val nachrichtenBereich = AST.Satz.Ausdruck.NachrichtenBereich(objektAusdruck, sätze)
          return if (peekType() is TokenTyp.DOPPELPUNKT) {
            // verkette mehrere Nachrichtenbereiche hintereinander
            subParse(NachrichtenBereich(nachrichtenBereich))
          } else {
            nachrichtenBereich
          }
        }
      }

      object SuperBereich: Satz<AST.Satz.Ausdruck.SuperBereich>() {
        override val id: ASTKnotenID = ASTKnotenID.SUPER_BLOCK

        override fun bewacheKnoten() {
          if (!hierarchyContainsAnyNode(ASTKnotenID.IMPLEMENTIERUNG, ASTKnotenID.KLASSEN_DEFINITION)) {
            throw GermanSkriptFehler.SyntaxFehler.UngültigerBereich(next(),
                "Ein Super-Block kann nur innerhalb einer Klassen-Implementierung verwendet werden.")
          }
        }

        override fun parseImpl(): AST.Satz.Ausdruck.SuperBereich {
          val superToken = expect<TokenTyp.SUPER>("'Super'")
          val bereich = parseSätze(TokenTyp.AUSRUFEZEICHEN)
          return AST.Satz.Ausdruck.SuperBereich(superToken, bereich)
        }
      }

      // Bedingungs-Ausdrücke sind etweder ein Subjekt-Funktionsaufruf oder eine Typüberprüfung
      class BedingungsAusdruck(private val subjekt: AST.Satz.Ausdruck, private val inBedingungsTerm: Boolean): Ausdruck<AST.Satz.Ausdruck>() {

        private var _id: ASTKnotenID = ASTKnotenID.TYP_ÜBERPRÜFUNG
        override val id: ASTKnotenID get() = _id

        override fun parseImpl(): AST.Satz.Ausdruck {
          val objekt = parseOptional<AST.Argument, TokenTyp.VORNOMEN> {
            parseArgument(false)
          }
          return if (objekt != null) {
            val objektAusdruck = objekt.ausdruck
            if (objektAusdruck is AST.Satz.Ausdruck.ObjektInstanziierung &&
                objektAusdruck.eigenschaftsZuweisungen.isEmpty() &&
                objektAusdruck.klasse.name == objekt.name &&
                peekType() is TokenTyp.ZUWEISUNG) {
              AST.Satz.Ausdruck.TypÜberprüfung(subjekt, objektAusdruck.klasse, expect("'ist' oder 'sind'"))
            } else {
              parseFunktionsAufruf(objekt)
            }
          } else {
            val modulPfad = parseModulPfad()
            val tokenTyp = peekType(1)
            if (modulPfad.isNotEmpty() || tokenTyp is TokenTyp.ZUWEISUNG || (tokenTyp is TokenTyp.OPERATOR && tokenTyp.operator == Operator.KLEINER)) {
              val adjektiv = expect<TokenTyp.BEZEICHNER_KLEIN>("Adjektiv")
              val typArgumente = parseTypArgumente()
              val typ = AST.TypKnoten(modulPfad, AST.WortArt.Adjektiv(null, adjektiv), typArgumente)
              AST.Satz.Ausdruck.TypÜberprüfung(subjekt, typ, expect("'ist' oder 'sind'"))
            } else {
              parseFunktionsAufruf(null)
            }
          }
        }

        fun parseFunktionsAufruf(objekt: AST.Argument?): AST.Satz.Ausdruck.FunktionsAufruf {
          _id = ASTKnotenID.FUNKTIONS_AUFRUF
          // Reflexivpronomen sind hier nicht erlaubt
          val präpositionen = parsePräpositionsArgumente(inBedingungsTerm)
          var suffix: TypedToken<TokenTyp.BEZEICHNER_KLEIN>? = expect("bezeichner")
          var verb = parseOptional<TokenTyp.BEZEICHNER_KLEIN>()
          if (verb == null) {
            verb = suffix
            suffix = null
          }
          val typArgumente = parseTypArgumente()
          return AST.Satz.Ausdruck.FunktionsAufruf(typArgumente, emptyList(), subjekt, verb!!, objekt, null, präpositionen, suffix)
        }

      }

      object VersucheFange: Ausdruck<AST.Satz.Ausdruck.VersucheFange>() {
        override val id: ASTKnotenID = ASTKnotenID.VERSUCHE_FANGE

        override fun parseImpl(): AST.Satz.Ausdruck.VersucheFange {
          val versuche = expect<TokenTyp.VERSUCHE>("'versuche'")
          val versuchBereich = parseSätze()
          überspringeLeereZeilen()
          val fange = mutableListOf<AST.Satz.Ausdruck.Fange>()
          while (peekType() is TokenTyp.FANGE) {
            fange += parseFange()
            überspringeLeereZeilen()
          }
          val schlussendlich = if (peekType() is TokenTyp.SCHLUSSENDLICH) {
            next()
            parseSätze()
          } else {
            null
          }
          return AST.Satz.Ausdruck.VersucheFange(versuche, versuchBereich, fange, schlussendlich)
        }

        private fun parseFange(): AST.Satz.Ausdruck.Fange {
          val fange = expect<TokenTyp.FANGE>("'fange'")
          val param = parseParameter<TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT>("bestimmter Artikel")
          val bereich = parseSätze()
          return AST.Satz.Ausdruck.Fange(fange, param, bereich)
        }
      }

      object Werfe: Ausdruck<AST.Satz.Ausdruck.Werfe>() {
        override val id: ASTKnotenID = ASTKnotenID.WERFE

        override fun parseImpl(): AST.Satz.Ausdruck.Werfe {
          val werfe = expect<TokenTyp.WERFE>("'werfe'")
          val (_, _, ausdruck) = parseNomenAusdruck<TokenTyp.VORNOMEN>(
              "Vornomen", inBinärenAusdruck = false, inBedingungsTerm = false, adjektivErlaubt = false
          )
          return AST.Satz.Ausdruck.Werfe(werfe, ausdruck)
        }
      }
    }
  }

  sealed class Definition<T: AST.Definition>(): SubParser<T>() {

    override fun bewacheKnoten() {
      if (!(parentNodeId == ASTKnotenID.PROGRAMM ||
            parentNodeId == ASTKnotenID.MODUL ||
            parentNodeId == ASTKnotenID.IMPLEMENTIERUNG ||
            parentNodeId == ASTKnotenID.ANONYME_KLASSE)) {
        throw GermanSkriptFehler.SyntaxFehler.UngültigerBereich(next(),
            "Definitionen können nur im globalem Bereich, in Modulen oder Klasse-Implementierungen geschrieben werden.")
      }
    }

    object Modul: Definition<AST.Definition.Modul>() {
      override val id = ASTKnotenID.MODUL

      override fun parseImpl(): AST.Definition.Modul {
        expect<TokenTyp.MODUL>("'Modul'")
        val modulPfad = parseModulPfad()
        val name = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner")
        val definitionen =  parseBereich { subParse(Programm(id)) }.definitionen
        return modulPfad.reversed().fold(AST.Definition.Modul(name, definitionen)) { modul, bezeichner ->
          val container = AST.DefinitionsContainer()
          container.module[modul.name.wert] = AST.Definition.Modul(modul.name, modul.definitionen)
          AST.Definition.Modul(bezeichner, container)
        }
      }

    }

    object DeklinationsDefinition: Definition<AST.Definition.DeklinationsDefinition>() {
      override val id: ASTKnotenID
        get() = ASTKnotenID.DEKLINATION

      override fun parseImpl(): AST.Definition.DeklinationsDefinition {
        expect<TokenTyp.DEKLINATION>("Deklination")
        überspringeLeereZeilen()
        return when (peekType()) {
          is TokenTyp.DUDEN -> parseDuden()
          else -> parseDeklination()
        }
      }

      // ein Wort ist ein Bezeichner ohne Symbole und mit nur einem Wort
      private fun parseWort(): TypedToken<TokenTyp.BEZEICHNER_GROSS> {
        val bezeichner = expect<TokenTyp.BEZEICHNER_GROSS>("Wort")
        if (bezeichner.typ.istSymbol || bezeichner.typ.teilWörter.size > 1) {
          throw GermanSkriptFehler.SyntaxFehler.ParseFehler(bezeichner.toUntyped(), "Wort", "Ein Wort darf nicht aus " +
              "Teilwörtern und Symbolen bestehen.")
        }
        return bezeichner
      }

      private fun parseFälle(): Array<String> {
        expect<TokenTyp.OFFENE_KLAMMER>("'('")
        val genitiv: String; val dativ: String; val akkusativ: String
        val nominativ = parseWort().wert
        if (peekType() is TokenTyp.GESCHLOSSENE_KLAMMER) {
          genitiv = nominativ
          dativ = nominativ
          akkusativ = nominativ
        } else {
          expect<TokenTyp.KOMMA>("','")
          genitiv = parseWort().wert
          expect<TokenTyp.KOMMA>("','")
          dativ = parseWort().wert
          expect<TokenTyp.KOMMA>("','")
          akkusativ = parseWort().wert
        }

        expect<TokenTyp.GESCHLOSSENE_KLAMMER>("')'")
        return arrayOf(nominativ, genitiv, dativ, akkusativ)
      }

      private fun parseDeklination(): AST.Definition.DeklinationsDefinition.Definition {
        val genus = expect<TokenTyp.GENUS>("Genus").typ.genus
        überspringeLeereZeilen()
        expect<TokenTyp.SINGULAR>("'Singular'")
        val singular = parseFälle()
        überspringeLeereZeilen()
        val plural = if (peekType() is TokenTyp.PLURAL) {
          next()
          parseFälle()
        } else {
          arrayOf(singular[0], singular[0], singular[0], singular[0])
        }

        return AST.Definition.DeklinationsDefinition.Definition(Deklination(genus, singular, plural))
      }

      private fun parseDuden(): AST.Definition.DeklinationsDefinition.Duden {
        expect<TokenTyp.DUDEN>("'Duden'")
        expect<TokenTyp.OFFENE_KLAMMER>("'('")
        val wort = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner")
        expect<TokenTyp.GESCHLOSSENE_KLAMMER>("')'")
        return  AST.Definition.DeklinationsDefinition.Duden(wort)
      }
    }

    class FunktionsSignatur(
        private val erlaubeReflexivPronomen: Boolean
    ): Definition<AST.Definition.FunktionsSignatur>() {

      override val id: ASTKnotenID = ASTKnotenID.FUNKTIONS_SIGNATUR

      override fun bewacheKnoten() {
        // hier muss nichts überwacht werden
        // Da eine Funktionssignatur zu Definitionen zählen und Definitionen nur
        // im globalen Bereich oder in Modulen auftreten dürfen, muss bewacheKnoten() hier
        // wieder überschrieben werden, da Funktionssignaturen innerhalb von Funktions-, Methoden- und
        // Schnittstellendefinitionen verwendet werden
      }

      override fun parseImpl(): AST.Definition.FunktionsSignatur {
        val verb = expect<TokenTyp.VERB>("'Verb'")
        val typParameter = parseTypParameter()
        var rückgabeTyp: AST.TypKnoten? = if (peekType() is TokenTyp.OFFENE_KLAMMER) {
          expect<TokenTyp.OFFENE_KLAMMER>("'('")
          parseTypOhneArtikel(
              namensErweiterungErlaubt = false, nomenErlaubt = true,
              adjektivErlaubt = true
          ).also { expect<TokenTyp.GESCHLOSSENE_KLAMMER>("')'") }
        } else {
          null
        }
        val name = expect<TokenTyp.BEZEICHNER_KLEIN>("bezeichner")
        val reflexivPronomen = if (erlaubeReflexivPronomen) parseOptional<TokenTyp.REFLEXIV_PRONOMEN.ERSTE_FORM>() else null

        var hatRückgabeTypObjekt = false
        val objekt = if (reflexivPronomen == null) {
          if (rückgabeTyp == null && peekType() is TokenTyp.OFFENE_KLAMMER) {
            next()
            hatRückgabeTypObjekt = true
            parseParameter<TokenTyp.VORNOMEN.ARTIKEL>("Artikel").also {
              rückgabeTyp = it.typKnoten
              expect<TokenTyp.GESCHLOSSENE_KLAMMER>("')'")
            }
          } else {
            parseOptional<AST.Definition.Parameter, TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT> {
              parseParameter<TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT>("bestimmter Artikel") }
          }
        } else {
         null
        }
        überspringeLeereZeilen()
        val präpositionsParameter = parsePräpositionsParameter()
        val suffix = parseOptional<TokenTyp.BEZEICHNER_KLEIN>()

        // impliziter Rückgabetyp ist null
        if (rückgabeTyp == null) {
          // Wenn kein Rückgabetyp angegeben ist dann ist der Rückgabetyp Nichts
          val nichts = AST.WortArt.Nomen(null, verb.changeType(TokenTyp.BEZEICHNER_GROSS(arrayOf("Nichts"),"", null)))
          rückgabeTyp = AST.TypKnoten(emptyList(), nichts, emptyList())
        }

        return AST.Definition.FunktionsSignatur(
            typParameter, rückgabeTyp!!, name,
            reflexivPronomen, objekt, hatRückgabeTypObjekt,
            präpositionsParameter, suffix
        )
      }

      fun parsePräpositionsParameter(): List<AST.Definition.PräpositionsParameter> {
        return parsePräpositionsListe({
          parseListeMitStart<AST.Definition.Parameter, TokenTyp.KOMMA, TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT>(false) {
            parseParameter<TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT>("bestimmter Artikel")
          }
        }
        ) {
          präposition, parameter -> AST.Definition.PräpositionsParameter(AST.Präposition(präposition), parameter)
        }
      }
    }

    class Funktion(
        private val erlaubeReflexivPronomen: Boolean
    ): Definition<AST.Definition.Funktion>() {
      override val id = ASTKnotenID.FUNKTIONS_DEFINITION

      override fun parseImpl(): AST.Definition.Funktion {
        val signatur = subParse(FunktionsSignatur(erlaubeReflexivPronomen))
        val sätze = parseSätze()
        return AST.Definition.Funktion(signatur, sätze)
      }
    }

    object Klasse: Definition<AST.Definition.Typdefinition.Klasse>() {
      override var id: ASTKnotenID = ASTKnotenID.KLASSEN_DEFINITION

      override fun parseImpl(): AST.Definition.Typdefinition.Klasse {
        expect<TokenTyp.NOMEN>("'Nomen'")
        val typParameter = parseTypParameter()
        val name = parseNomenOhneVornomen(false)

        val eingenschaften = when(peekType()) {
          is TokenTyp.BEZEICHNER_KLEIN -> {
            parseKleinesSchlüsselwort("mit")
            überspringeLeereZeilen()
            parseListeMitStart<AST.Definition.Parameter, TokenTyp.KOMMA, TokenTyp.VORNOMEN.ARTIKEL>(false) {
              parseParameter<TokenTyp.VORNOMEN.ARTIKEL>("Artikel")
            }
          }
          else -> emptyList()
        }

        überspringeLeereZeilen()

        val elternKlasse = when (peekType()) {
          TokenTyp.ALS_KLEIN -> next().let {
            val elternKlasse = parseTypOhneArtikel(namensErweiterungErlaubt = false, nomenErlaubt = true, adjektivErlaubt = false)
            subParse(Satz.Ausdruck.NomenAusdruck.ObjektInstanziierung(elternKlasse, false))
          }
          else -> null
        }

        überspringeLeereZeilen()
        id = ASTKnotenID.IMPLEMENTIERUNG
        val konstruktorSätze = parseSätze()
        id = ASTKnotenID.KLASSEN_DEFINITION
        return AST.Definition.Typdefinition.Klasse(typParameter, name, elternKlasse, eingenschaften.toMutableList(), konstruktorSätze)
      }
    }

    object Schnittstelle: Definition<AST.Definition.Typdefinition.Schnittstelle>() {
      override val id = ASTKnotenID.SCHNITTSTELLE

      override fun parseImpl(): AST.Definition.Typdefinition.Schnittstelle {
        expect<TokenTyp.ADJEKTIV>("'Adjektiv'")
        val typParameter = parseTypParameter()
        val name = AST.WortArt.Adjektiv(null, expect("Adjektiv"))
        val signaturen = mutableListOf<AST.Definition.FunktionsSignatur>()
        runParseBereich {
          signaturen += subParse(FunktionsSignatur(true))
        }
        return AST.Definition.Typdefinition.Schnittstelle(typParameter, name, signaturen)
      }
    }

    object Alias: Definition<AST.Definition.Typdefinition.Alias>() {
      override val id = ASTKnotenID.ALIAS

      override fun parseImpl(): AST.Definition.Typdefinition.Alias {
        expect<TokenTyp.ALIAS>("'Alias'")
        val name = parseNomenOhneVornomen(false)
        val zuweisung = expect<TokenTyp.ZUWEISUNG>("'ist'")
        if (zuweisung.typ.numerus != Numerus.SINGULAR) {
          throw GermanSkriptFehler.SyntaxFehler.ParseFehler(zuweisung.toUntyped(), "ist")
        }
        val typ = parseTypOhneArtikel(
            namensErweiterungErlaubt = false, nomenErlaubt = true,
            adjektivErlaubt = false
        )
        return AST.Definition.Typdefinition.Alias(name, typ)
      }
    }

    object  Implementierung: Definition<AST.Definition.Implementierung>() {
      override val id = ASTKnotenID.IMPLEMENTIERUNG

      override fun parseImpl(): AST.Definition.Implementierung {
        expect<TokenTyp.IMPLEMENTIERE>("'Implementiere'")

        val typParameter = parseTypParameter()

        val artikel = expect<TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT>("bestimmter Artikel")
        überspringeLeereZeilen()
        val (schnittstellen, typ) = parseSchnittstellenMitKlasse()

        typ.name.vornomen = artikel

        return AST.Definition.Implementierung(typ, typParameter, schnittstellen, subParse(ImplementierungsBereich(false)))
      }

      fun parseSchnittstellenMitKlasse(): Pair<List<AST.TypKnoten>, AST.TypKnoten> {
        val schnittstellen = mutableListOf<AST.TypKnoten>()
        while (true) {
          val adjektivOderNomen = parseTypOhneArtikel(
              namensErweiterungErlaubt = false, nomenErlaubt = true,
              adjektivErlaubt = true)
          if (adjektivOderNomen.name is AST.WortArt.Adjektiv) {
            schnittstellen += adjektivOderNomen
            if (peekType() !is TokenTyp.KOMMA) {
              val typ = parseTypOhneArtikel(namensErweiterungErlaubt = false, nomenErlaubt = true, adjektivErlaubt = false)
              return Pair(schnittstellen, typ)
            }
            next()
            überspringeLeereZeilen()
          } else {
            return  Pair(schnittstellen, adjektivOderNomen)
          }
        }
      }
    }

    class ImplementierungsBereich(private val istAnonymeKlasse: Boolean): Definition<AST.Definition.ImplementierungsBereich>() {
      override val id: ASTKnotenID = ASTKnotenID.IMPLEMENTIERUNG

      override fun parseImpl(): AST.Definition.ImplementierungsBereich {
        val methoden = mutableListOf<AST.Definition.Funktion>()
        val eigenschaften = mutableListOf<AST.Satz.VariablenDeklaration>()
        val berechneteEigenschaften = mutableListOf<AST.Definition.Eigenschaft>()
        val konvertierungen = mutableListOf<AST.Definition.Konvertierung>()

        runParseBereich {
          when (peekType()) {
            is TokenTyp.VERB -> methoden += subParse(Funktion(true))
            is TokenTyp.EIGENSCHAFT -> berechneteEigenschaften += subParse(Eigenschaft)
            is TokenTyp.ALS_GROß -> konvertierungen += subParse(Konvertierung)
            is TokenTyp.VORNOMEN.DEMONSTRATIV_PRONOMEN ->
              if (istAnonymeKlasse) eigenschaften += subParse(Satz.VariablenDeklaration)
              else throw GermanSkriptFehler.SyntaxFehler.ParseFehler(Implementierung.next(), "'.'",
                  "In einem Implementiere-Bereich können nur Methoden oder Eigenschafts-/Konvertierungsdefinitionen definiert werden.")
            else -> throw GermanSkriptFehler.SyntaxFehler.ParseFehler(Implementierung.next(), "'.'",
                "In einem Implementiere-Bereich oder einer Anonymen Klasse können nur Methoden oder Eigenschafts-/Konvertierungsdefinitionen definiert werden.")
          }
        }

        return AST.Definition.ImplementierungsBereich(eigenschaften, methoden, berechneteEigenschaften, konvertierungen)
      }
    }

    object Konvertierung: Definition<AST.Definition.Konvertierung>() {
      override val id = ASTKnotenID.KONVERTIERUNGS_DEFINITION

      override fun parseImpl(): AST.Definition.Konvertierung {
        expect<TokenTyp.ALS_GROß>("'Als'")
        val typ = parseTypOhneArtikel(
            namensErweiterungErlaubt = false, nomenErlaubt = true,
            adjektivErlaubt = false
        )
        val definition = parseSätze()
        return AST.Definition.Konvertierung(typ, definition)
      }
    }

    object Eigenschaft: Definition<AST.Definition.Eigenschaft>() {
      override val id = ASTKnotenID.EIGENSCHAFTS_DEFINITION

      override fun parseImpl(): AST.Definition.Eigenschaft {
        expect<TokenTyp.EIGENSCHAFT>("'Eigenschaft'")
        expect<TokenTyp.OFFENE_KLAMMER>("'('")
        val rückgabeTyp = parseTypOhneArtikel(
            namensErweiterungErlaubt = false, nomenErlaubt = true,
            adjektivErlaubt = true
        )
        expect<TokenTyp.GESCHLOSSENE_KLAMMER>("')'")
        val name = parseNomenOhneVornomen(false)
        val definition = parseSätze()
        return AST.Definition.Eigenschaft(rückgabeTyp, name, definition)
      }
    }

    object Konstante: Definition<AST.Definition.Konstante>() {
      override val id = ASTKnotenID.KONSTANTE

      override fun parseImpl(): AST.Definition.Konstante {
        expect<TokenTyp.KONSTANTE>("'Konstante'")
        val name = parseGroßenBezeichner(true)
        val zuweisung = expect<TokenTyp.ZUWEISUNG>("'ist'")
        if (zuweisung.typ.numerus != Numerus.SINGULAR) {
          throw GermanSkriptFehler.SyntaxFehler.ParseFehler(zuweisung.toUntyped(), "'ist'")
        }
        val wert = parseAusdruck(mitVornomen = false, optionalesIstNachVergleich = false)
        return AST.Definition.Konstante(name, wert)
      }
    }

    object Import: Definition<AST.Definition.Import>() {
      override val id = ASTKnotenID.IMPORT

      override fun parseImpl(): AST.Definition.Import {
        parseKleinesSchlüsselwort("importiere")
        val dateiPfad = expect<TokenTyp.ZEICHENFOLGE>("Dateipfad")
        return AST.Definition.Import(dateiPfad)
      }
    }

    object Verwende: Definition<AST.Definition.Verwende>() {
      override val id = ASTKnotenID.VERWENDE

      override fun parseImpl(): AST.Definition.Verwende {
        parseKleinesSchlüsselwort("verwende")
        val modulPfad = parseModulPfad()
        val modulOderKlasse = expect<TokenTyp.BEZEICHNER_GROSS>("Bezeichner")
        return AST.Definition.Verwende(modulPfad, modulOderKlasse)
      }
    }
  }
}

fun main() {
  val ast = Parser(File("./iterationen/iter_2/code.gm")).parse()
  println(ast)
}