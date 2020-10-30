package germanskript

import java.io.File
import java.util.*

class Entsüßer(startDatei: File): PipelineKomponente(startDatei) {
  val typPrüfer = TypPrüfer(startDatei)
  val ast = typPrüfer.ast

  fun entsüße() {
    typPrüfer.prüfe()

    ast.visit { knoten ->
      when (knoten) {
        is AST.Satz.FürJedeSchleife -> entsüßeFürJedeSchleife(knoten)
      }
      return@visit true
    }
  }

  private fun entsüßeFürJedeSchleife(fürJedeSchleife: AST.Satz.FürJedeSchleife) {
    val parent = fürJedeSchleife.parent as AST.Satz.Bereich

    val iterierbar = when  {
      fürJedeSchleife.iterierbares != null -> fürJedeSchleife.iterierbares
      fürJedeSchleife.reichweite != null -> erstelleReichweite(fürJedeSchleife.reichweite)
      else -> {
        val plural = fürJedeSchleife.singular.copy()
        plural.deklination = fürJedeSchleife.singular.deklination
        plural.numera = EnumSet.of(Numerus.PLURAL)
        plural.adjektiv = fürJedeSchleife.singular.adjektiv
        AST.Satz.Ausdruck.Variable(plural)
      }
    }

    val iteratorNomen = AST.WortArt.Nomen(TypedToken.imaginäresToken(TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT, "den"),
        TypedToken.imaginäresToken(TokenTyp.BEZEICHNER_GROSS(arrayOf("_Iterator"), "", null), "_Iterator"))

    val holeIteratorAufruf = AST.Satz.Ausdruck.FunktionsAufruf(
        emptyList(),
        emptyList(),
        null,
        TypedToken.imaginäresToken(TokenTyp.BEZEICHNER_KLEIN("hole"), "hole"),
        AST.Argument(
            null,
            iteratorNomen,
            AST.Satz.Ausdruck.Variable(iteratorNomen)),
        null,
        emptyList(),
        null
    )

    holeIteratorAufruf.vollerName = "hole den Iterator"
    holeIteratorAufruf.aufrufTyp = FunktionsAufrufTyp.METHODEN_BEREICHS_AUFRUF
    val holeIterator = AST.Satz.Ausdruck.MethodenBereich(iterierbar, AST.Satz.Bereich(mutableListOf(holeIteratorAufruf)))

    val unsichtbaresIteratorNomen = AST.WortArt.Nomen(
        null,
        TypedToken.imaginäresToken(TokenTyp.BEZEICHNER_GROSS(arrayOf("_Iterator"),"", null), "_Iterator"))

    unsichtbaresIteratorNomen.deklination = Deklination(Genus.MASKULINUM, Array(4) {"_Iterator"}, Array(4) {"_Iterator"})
    unsichtbaresIteratorNomen.numera = EnumSet.of(Numerus.SINGULAR)

    val varDeklaration = AST.Satz.VariablenDeklaration(
        unsichtbaresIteratorNomen, null,
        TypedToken.imaginäresToken(TokenTyp.ZUWEISUNG(Numerus.SINGULAR), "ist"),
        holeIterator
    )
    val bedingung = AST.Satz.Ausdruck.FunktionsAufruf(
        emptyList(),
        emptyList(),
        AST.Satz.Ausdruck.Variable(unsichtbaresIteratorNomen),
        TypedToken.imaginäresToken(TokenTyp.BEZEICHNER_KLEIN("läuft"), "läuft"),
        null, null, emptyList(),
        TypedToken.imaginäresToken(TokenTyp.BEZEICHNER_KLEIN("weiter"), "weiter")
    )
    bedingung.aufrufTyp = FunktionsAufrufTyp.METHODEN_SUBJEKT_AUFRUF
    bedingung.vollerName = "läuft weiter"

    val holeIteration = AST.Satz.Ausdruck.FunktionsAufruf(
        emptyList(),
        emptyList(),
        AST.Satz.Ausdruck.Variable(unsichtbaresIteratorNomen),
        TypedToken.imaginäresToken(TokenTyp.BEZEICHNER_KLEIN("hole"), "hole"),
        null, null, emptyList(), null
    )

    holeIteration.vollerName = "hole den Typ"
    holeIteration.aufrufTyp = FunktionsAufrufTyp.METHODEN_SUBJEKT_AUFRUF

    val iteration = AST.Satz.VariablenDeklaration(
        fürJedeSchleife.binder, null, TypedToken.imaginäresToken(TokenTyp.ZUWEISUNG(Numerus.SINGULAR), "ist"), holeIteration)

    fürJedeSchleife.bereich.sätze.add(0, iteration)
    val solangeSchleife = AST.Satz.SolangeSchleife(AST.Satz.BedingungsTerm(
        fürJedeSchleife.für.toUntyped(), bedingung, fürJedeSchleife.bereich
    ))

    // ersetze die Für-Jede-Schleife
    val index = parent.sätze.indexOf(fürJedeSchleife)
    parent.sätze[index] = AST.Satz.Bereich(mutableListOf(
        varDeklaration,
        solangeSchleife
    ))
  }

  private fun erstelleReichweite(reichweite: AST.Satz.Reichweite): AST.Satz.Ausdruck.ObjektInstanziierung {
    val reichWeitenNomen = AST.WortArt.Nomen(null,
        TypedToken.imaginäresToken(TokenTyp.BEZEICHNER_GROSS(arrayOf("Reich", "Weite"), "", null), "ReichWeite"))

    reichWeitenNomen.numera = EnumSet.of(Numerus.SINGULAR)
    reichWeitenNomen.deklination = Deklination(Genus.FEMININUM, Array(4) {"ReichWeite"}, Array(4) {"ReichWeite"})

    val reichweitenKlasse = AST.TypKnoten(emptyList(), reichWeitenNomen, emptyList())
    reichweitenKlasse.typ = BuildIn.Klassen.reichweite

    val startNomen = AST.WortArt.Nomen(TypedToken.imaginäresToken(TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT, "dem"),
        TypedToken.imaginäresToken(TokenTyp.BEZEICHNER_GROSS(arrayOf("Start"), "", null), "Start"))

    startNomen.numera = EnumSet.of(Numerus.SINGULAR)
    startNomen.deklination = Deklination(Genus.MASKULINUM, Array(4) {"Start"}, Array(4) {"Start"})


    val endNomen = AST.WortArt.Nomen(TypedToken.imaginäresToken(TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT, "dem"),
        TypedToken.imaginäresToken(TokenTyp.BEZEICHNER_GROSS(arrayOf("Ende"), "", null), "Ende"))

    endNomen.numera = EnumSet.of(Numerus.SINGULAR)
    endNomen.deklination = Deklination(Genus.MASKULINUM, Array(4) {"Ende"}, Array(4) {"Ende"})

    return AST.Satz.Ausdruck.ObjektInstanziierung(
        reichweitenKlasse,
        mutableListOf(
            AST.Argument(null, startNomen,reichweite.anfang),
            AST.Argument(null, endNomen, reichweite.ende)
        )
    )
  }
}