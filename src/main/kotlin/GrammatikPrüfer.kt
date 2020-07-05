import util.SimpleLogger
import java.util.*

class GrammatikPrüfer(dateiPfad: String): PipelineComponent(dateiPfad) {
  val deklanierer = Deklanierer(dateiPfad)
  val ast = deklanierer.ast

  val logger = SimpleLogger()

  fun prüfe() {
    deklanierer.deklaniere()

    ast.visit() { knoten ->
      when (knoten) {
        is AST.Definition.Funktion -> prüfeFunktionsDefinition(knoten)
        is AST.Satz.VariablenDeklaration -> prüfeVariablendeklaration(knoten)
        is AST.FunktionsAufruf -> prüfeFunktionsAufruf(knoten)
        is AST.Satz.Zurückgabe -> prüfeZurückgabe(knoten)
        is AST.Ausdruck -> when (knoten) {
            is AST.Ausdruck.BinärerAusdruck -> prüfeBinärenAusdruck(knoten)
            is AST.Ausdruck.Minus -> prüfeMinus(knoten)
            else -> return@visit false
        }
      }
      // visit everything
      true
    }
  }

  private fun prüfeZurückgabe(zurückgabe: AST.Satz.Zurückgabe) {
    if (zurückgabe.ausdruck is AST.Ausdruck.Variable) {
      val variable = zurückgabe.ausdruck
      prüfeNomen(variable.name, EnumSet.of(Kasus.AKKUSATIV))
      prüfeArtikel(variable.artikel!!, variable.name)
    }
  }

  private fun prüfeNomen(nomen: AST.Nomen, fälle: EnumSet<Kasus>) {
    val deklanation = deklanierer.holeDeklination(nomen)

    val numerus = deklanation.getNumerus(nomen.bezeichner.wert)
    nomen.numerus = numerus
    nomen.nominativ = deklanation.getForm(Kasus.NOMINATIV, numerus)
    nomen.genus = deklanation.genus

    for (kasus in fälle) {
      val erwarteteForm = deklanation.getForm(kasus, numerus)
      if (nomen.bezeichner.wert == erwarteteForm) {
        nomen.fälle.add(kasus)
      }
    }
    if (nomen.fälle.isEmpty()) {
      // TODO: berücksichtige auch die möglichen anderen Fälle in der Fehlermeldung
      val kasus = fälle.first()
      val erwarteteForm = deklanation.getForm(kasus, numerus)
      throw GermanScriptFehler.GrammatikFehler.FalscheForm(nomen.bezeichner.toUntyped(), kasus, nomen, erwarteteForm)
    }
  }

  private fun prüfeArtikel(artikel: TypedToken<TokenTyp.ARTIKEL>, nomen: AST.Nomen)
  {
    val bestimmt = artikel.typ is TokenTyp.ARTIKEL.BESTIMMT
    for (kasus in nomen.fälle) {
      val erwarteterArtikel = getArtikel(bestimmt, nomen.genus!!, nomen.numerus!!, kasus)
      if (artikel.wert == erwarteterArtikel) {
        nomen.artikel = erwarteterArtikel
      } else {
        nomen.fälle.remove(kasus)
      }
    }

    if (nomen.artikel == null) {
      val fall = nomen.fälle.first()
      val erwarteterArtikel = getArtikel(bestimmt, nomen.genus!!, nomen.numerus!!, fall)
      throw GermanScriptFehler.GrammatikFehler.FalscherArtikel(artikel.toUntyped(), fall, nomen, erwarteterArtikel)
    }
  }

  private fun prüfeVariablendeklaration(variablenDeklaration: AST.Satz.VariablenDeklaration) {
    val nomen = variablenDeklaration.name
    prüfeNomen(nomen, EnumSet.of(Kasus.NOMINATIV))
    prüfeArtikel(variablenDeklaration.artikel, nomen)
    // logger.addLine("geprüft: $variablenDeklaration")
  }

  private fun prüfeParameter(parameter: AST.Definition.Parameter, fälle: EnumSet<Kasus>) {
    val nomen = parameter.typKnoten.name
    prüfeNomen(nomen, fälle)
    prüfeArtikel(parameter.artikel, nomen)
    if (parameter.name != null) {
      prüfeNomen(parameter.name, EnumSet.of(Kasus.NOMINATIV))
      parameter.name.artikel = getArtikel(true, parameter.name, nomen.fälle.first())
    }
  }

  private fun prüfeFunktionsDefinition(funktionsDefinition: AST.Definition.Funktion) {
    if (funktionsDefinition.rückgabeTyp != null) {
      prüfeNomen(funktionsDefinition.rückgabeTyp.name, EnumSet.of(Kasus.NOMINATIV))
    }
    if (funktionsDefinition.objekt != null) {
      prüfeParameter(funktionsDefinition.objekt, EnumSet.of(Kasus.DATIV, Kasus.AKKUSATIV))
    }
    for (präposition in funktionsDefinition.präpositionsParameter) {
      prüfePräpositionsParameter(präposition)
    }
    // logger.addLine("geprüft: $funktionsDefinition")
  }

  private fun prüfePräpositionsParameter(präposition: AST.Definition.PräpositionsParameter) {
    for (parameter in präposition.parameter) {
      prüfeParameter(parameter, präposition.präposition.fälle)
    }
  }

  private fun prüfeArgument(argument: AST.Argument, fälle: EnumSet<Kasus>) {
    prüfeNomen(argument.name, fälle)
    prüfeArtikel(argument.artikel, argument.name)
    if (argument.wert is AST.Ausdruck.Variable) {
      val variable = argument.wert
      prüfeNomen(variable.name, EnumSet.of(Kasus.NOMINATIV))
      if (variable.artikel != null) {
        prüfeArtikel(variable.artikel, variable.name)
      }
    }
  }

  private fun prüfePräpositionsArgumente(präposition: AST.PräpositionsArgumente) {
    for (argument in präposition.argumente) {
      prüfeArgument(argument, präposition.präposition.fälle)
    }
  }

  private fun prüfeFunktionsAufruf(funktionsAufruf: AST.FunktionsAufruf) {
    if (funktionsAufruf.objekt != null) {
      prüfeArgument(funktionsAufruf.objekt, EnumSet.of(Kasus.AKKUSATIV))
    }
    for (präposition in funktionsAufruf.präpositionsArgumente) {
      prüfePräpositionsArgumente(präposition)
    }
    // logger.addLine("geprüft: $funktionsAufruf")
  }

  private fun prüfeBinärenAusdruck(binärerAusdruck: AST.Ausdruck.BinärerAusdruck) {
    if (binärerAusdruck.links is AST.Ausdruck.Variable) {
      val variable = binärerAusdruck.links
      val kasus = if (binärerAusdruck.istAnfang) Kasus.NOMINATIV
      else binärerAusdruck.operator.typ.operator.klasse.kasus
      prüfeNomen(variable.name, EnumSet.of(kasus))
      prüfeArtikel(variable.artikel!!, variable.name)
    }
    if (binärerAusdruck.rechts is AST.Ausdruck.Variable) {
      val variable = binärerAusdruck.rechts
      val kasus = binärerAusdruck.operator.typ.operator.klasse.kasus
      prüfeNomen(variable.name, EnumSet.of(kasus))
      prüfeArtikel(variable.artikel!!, variable.name)
    }
    logger.addLine("geprüft: $binärerAusdruck")
  }

  private fun prüfeMinus(knoten: AST.Ausdruck.Minus) {
    if (knoten.ausdruck is AST.Ausdruck.Variable) {
      val variable = knoten.ausdruck
      prüfeNomen(variable.name, EnumSet.of(Kasus.AKKUSATIV))
      prüfeArtikel(variable.artikel!!, variable.name)
    }
  }


  private fun getArtikel(bestimmt: Boolean, nomen: AST.Nomen, kasus: Kasus): String {
    return getArtikel(bestimmt, nomen.genus!!, nomen.numerus!!, kasus)
  }

  private fun getArtikel(bestimmt: Boolean, genus: Genus, numerus: Numerus, kasus: Kasus): String {
    return when (kasus) {
      Kasus.NOMINATIV-> when(numerus) {
        Numerus.SINGULAR -> {
          when(genus) {
            Genus.MASKULINUM -> if (bestimmt) "der" else "ein"
            Genus.FEMININUM -> if (bestimmt) "die" else "eine"
            Genus.NEUTRUM -> if (bestimmt) "das" else "ein"
          }
        }
        Numerus.PLURAL -> {
          if (bestimmt) "die" else "einige"
        }
      }

      Kasus.GENITIV-> when(numerus) {
        Numerus.SINGULAR -> {
          when(genus) {
            Genus.MASKULINUM -> if (bestimmt) "des" else "eines"
            Genus.FEMININUM -> if (bestimmt) "der" else "einer"
            Genus.NEUTRUM -> if (bestimmt) "des" else "eines"
          }
        }
        Numerus.PLURAL -> {
          if (bestimmt) "der" else "einiger"
        }
      }

      Kasus.DATIV -> when(numerus) {
        Numerus.SINGULAR -> {
          when(genus) {
            Genus.MASKULINUM -> if (bestimmt) "dem" else "einem"
            Genus.FEMININUM -> if (bestimmt) "der" else "einer"
            Genus.NEUTRUM -> if (bestimmt) "dem" else "einem"
          }
        }
        Numerus.PLURAL -> {
          if (bestimmt) "den" else "einigen"
        }
      }

      Kasus.AKKUSATIV-> when(numerus) {
        Numerus.SINGULAR -> {
          when(genus) {
            Genus.MASKULINUM -> if (bestimmt) "den" else "einen"
            Genus.FEMININUM -> if (bestimmt) "die" else "eine"
            Genus.NEUTRUM -> if (bestimmt) "das" else "ein"
          }
        }
        Numerus.PLURAL -> {
          if (bestimmt) "die" else "einige"
        }
      }
    }
  }
}

fun main() {
  val grammatikPrüfer = GrammatikPrüfer("./iterationen/iter_0/code.gms")
  grammatikPrüfer.prüfe()
  grammatikPrüfer.logger.print()
}

