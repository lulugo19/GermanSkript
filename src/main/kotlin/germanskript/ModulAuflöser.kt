package germanskript

import java.io.File

class ModulAuflöser(startDatei: File): PipelineKomponente(startDatei) {
  val ast = Parser(startDatei).parse()

  fun löseModulPfadeAuf() {
    löseModulPfadeAuf(ast.definitionen)
  }

  private fun löseModulPfadeAuf(definitionen: AST.DefinitionsContainer) {
    definitionen.verwende.forEach { verwende ->
      val verwendeteDefinitionen =
          if (verwende.modulPfad.isEmpty()) definitionen
          else löseModulPfadAuf(verwende, verwende.modulPfad).definitionen
      val name = verwende.modulOderKlasse.wert
      when {
        // Priorität: Konstanten -> Typen -> Module
        verwendeteDefinitionen.konstanten.containsKey(name) ->
          definitionen.verwendeteKonstanten[name] = verwendeteDefinitionen.konstanten.getValue(name)
        verwendeteDefinitionen.definierteTypen.containsKey(name) ->
          definitionen.verwendeteTypen[name] = verwendeteDefinitionen.definierteTypen.getValue(name)
        verwendeteDefinitionen.module.containsKey(name) ->
          definitionen.verwendeteModule += verwendeteDefinitionen.module.getValue(name).definitionen
        else -> throw GermanSkriptFehler.Undefiniert.Modul(verwende.modulOderKlasse.toUntyped())
      }
    }
    definitionen.module.values.forEach {modul -> löseModulPfadeAuf(modul.definitionen)}
  }

  fun löseModulPfadAuf(knoten: AST, modulPfad: List<TypedToken<TokenTyp.BEZEICHNER_GROSS>>): AST.Definition.Modul {
    // versuche das Modul lokal zu finden
    val lokal = knoten.findNodeInParents<AST.DefinitionsContainer>()
    if (lokal != null) {
      try {
        return findeModul(lokal, modulPfad)
      } catch (fehler: GermanSkriptFehler.Undefiniert.Modul) {
        // just catch it...
      }
    }

    // ansonsten versuche das Modul global zu finden
    return findeModul(ast.definitionen, modulPfad)
  }

  fun findeModul(definitionen: AST.DefinitionsContainer, modulPfad: List<TypedToken<TokenTyp.BEZEICHNER_GROSS>>): AST.Definition.Modul {
    val (potenziellesModul, index) = holeModulFallsVorhanden(definitionen, modulPfad)
    var modul = potenziellesModul
    var maxModulTiefe = index
    for (verwendetesModul in definitionen.verwendeteModule) {
      if (modul != null) {
        break
      }
      val (potenziellesModul, index) = holeModulFallsVorhanden(verwendetesModul, modulPfad)
      modul = potenziellesModul
      maxModulTiefe = kotlin.math.max(maxModulTiefe, index)
    }
    return modul ?: throw GermanSkriptFehler.Undefiniert.Modul(modulPfad[maxModulTiefe].toUntyped())
  }

  fun holeModulFallsVorhanden(definitionen: AST.DefinitionsContainer, modulPfad: List<TypedToken<TokenTyp.BEZEICHNER_GROSS>>):
      Pair<AST.Definition.Modul?, Int> {
    var definitionen = definitionen
    for ((index, bezeichner) in modulPfad.withIndex()) {
      if (!definitionen.module.containsKey(bezeichner.wert)) {
        return null to index
      }
      definitionen = definitionen.module.getValue(bezeichner.wert).definitionen
    }
    return definitionen.parent as AST.Definition.Modul to modulPfad.size - 1
  }
}