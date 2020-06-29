class GrammatikPrüfer(quellCode: String) {
  val deklanierer = Deklanierer(quellCode)
  val ast = deklanierer.ast

  fun prüfe() {
    deklanierer.deklaniere()

    ast.visit { knoten ->
      when (knoten) {
        is AST.Definition.Funktion -> prüfeFunktionsDefinition(knoten)
        is AST.Satz.Variablendeklaration -> prüfeVariablendeklaration(knoten)
        is AST.FunktionsAufruf -> prüfeFunktionsAufruf(knoten)
      }
    }
  }

  private fun prüfeNomen(nomen: AST.Nomen, kasus: Kasus) {
    val deklanation = deklanierer.holeDeklination(nomen)

    val numerus = deklanation.getNumerus(nomen.bezeichner.wert)
    nomen.numerus = numerus
    nomen.nominativ = deklanation.getForm(Kasus.NOMINATIV, numerus)
    nomen.genus = deklanation.genus

    val expectedForm = deklanation.getForm(kasus, numerus)
    if (nomen.bezeichner.wert != expectedForm) {
      throw GermanScriptFehler.GrammatikFehler.FalscheForm(nomen.bezeichner.toUntyped(), kasus, numerus, expectedForm)
    }
  }

  private fun prüfeArtikel(artikel: TypedToken<TokenTyp.ARTIKEL>, nomen: AST.Nomen, kasus: Kasus): String
  {
    val bestimmt = artikel.typ is TokenTyp.ARTIKEL.BESTIMMT
    val expectedArtikel = getArtikel(bestimmt, nomen.genus!!, nomen.numerus!!, kasus)
    if (artikel.wert != expectedArtikel) {
      throw GermanScriptFehler.GrammatikFehler.FalscherArtikel(artikel.toUntyped(), kasus, nomen.numerus!!, expectedArtikel)
    }
    return expectedArtikel
  }

  private fun prüfeVariablendeklaration(variablenDeklaration: AST.Satz.Variablendeklaration) {
    val nomen = variablenDeklaration.name
    prüfeNomen(nomen, Kasus.NOMINATIV)
    prüfeArtikel(variablenDeklaration.artikel, nomen, Kasus.NOMINATIV)
  }

  private fun prüfeParameter(parameter: AST.Definition.Parameter, kasus: Kasus) {
    val nomen = parameter.typ
    prüfeNomen(nomen, kasus)
    prüfeArtikel(parameter.artikel, nomen, kasus)
    if (parameter.name != null) {
      prüfeNomen(parameter.name, Kasus.NOMINATIV)
      parameter.name.artikel = getArtikel(true, parameter.name, Kasus.NOMINATIV)
    }
  }

  private fun prüfeFunktionsDefinition(funktionsDefinition: AST.Definition.Funktion) {
    if (funktionsDefinition.objekt != null) {
      prüfeParameter(funktionsDefinition.objekt, Kasus.AKKUSATIV)
    }
    for (präposition in funktionsDefinition.präpositionsParameter) {
      prüfePräpositionsParameter(präposition)
    }
  }

  private fun prüfePräpositionsParameter(präposition: AST.Definition.PräpositionsParameter) {
    val fälle = präposition.präposition.kasus
    for (kasus in fälle.withIndex()) {
      try {
        for (parameter in präposition.parameter) {
          prüfeParameter(parameter, kasus.value)
        }
      }
      catch (grammatikFehler: GermanScriptFehler.GrammatikFehler) {
        // Wenn der letzte Fall nicht geklappt hat werfe den Fehler
        // TODO Man könnte auch 'prüfe' und die Fehlermeldungen verbessern, sodass der Benutzer auf meherer Fälle aufmerksam gemacht wird
        if (kasus.index == fälle.size-1) {
          throw grammatikFehler
        }
      }
    }
  }

  private fun prüfeArgument(argument: AST.Argument, kasus: Kasus) {
    prüfeNomen(argument.name, kasus)
    prüfeArtikel(argument.artikel, argument.name, kasus)
  }

  private fun prüfePräpositionsArgumente(präposition: AST.PräpositionsArgumente) {
    val fälle = präposition.präposition.kasus
    for (kasus in fälle.withIndex()) {
      try {
        for (argument in präposition.argumente) {
          prüfeArgument(argument, kasus.value)
        }
      }
      catch (grammatikFehler: GermanScriptFehler.GrammatikFehler) {
        // Wenn der letzte Fall nicht geklappt hat werfe den Fehler
        // TODO Man könnte auch 'prüfe' und die Fehlermeldungen verbessern, sodass der Benutzer auf meherer Fälle aufmerksam gemacht wird
        if (kasus.index == fälle.size-1) {
          throw grammatikFehler
        }
      }
    }
  }

  private fun prüfeFunktionsAufruf(funktionsAufruf: AST.FunktionsAufruf) {
    if (funktionsAufruf.objekt != null) {
      prüfeArgument(funktionsAufruf.objekt, Kasus.AKKUSATIV)
    }
    for (präposition in funktionsAufruf.präpositionsArgumente) {
      prüfePräpositionsArgumente(präposition)
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

