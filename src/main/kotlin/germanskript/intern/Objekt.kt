package germanskript.intern

import germanskript.*
import germanskript.IM_AST
import germanskript.Interpretierer

open class Objekt(val klasse: IM_AST.Definition.Klasse, internal val typ: Typ.Compound.Klasse) {
  override fun toString() = "${typ.name}@${this.hashCode()}"

  protected val eigenschaften: MutableMap<String, Objekt> = mutableMapOf()

  open fun holeEigenschaft(eigenschaftsName: String): Objekt {
    return eigenschaften.getValue(eigenschaftsName)
  }

  open fun setzeEigenschaft(eigenschaftsName: String, wert: Objekt){
    eigenschaften[eigenschaftsName] = wert
  }

  companion object {
    val zeichenFolgenTypArgument = AST.TypKnoten(emptyList(), AST.WortArt.Nomen(null,
        TypedToken.imaginäresToken(TokenTyp.BEZEICHNER_GROSS(arrayOf("Zeichenfolge"), "", null), "Zeichenfolge")),
        emptyList()
    )

    init {
      zeichenFolgenTypArgument.typ = BuildIn.Klassen.zeichenfolge
    }
  }

  open class SkriptObjekt(
      klasse: IM_AST.Definition.Klasse,
      typ: Typ.Compound.Klasse
  ) : Objekt(klasse, typ)

  class ClosureObjekt(
      klasse: IM_AST.Definition.Klasse,
      typ: Typ.Compound.Klasse,
      val umgebung: Interpretierer.Umgebung,
      val objektArt: IM_AST.Satz.Ausdruck.ObjektArt
  ): SkriptObjekt(klasse, typ)

  open fun rufeMethodeAuf(
      aufruf: IM_AST.Satz.Ausdruck.IAufruf,
      injection: Interpretierer.InterpretInjection): Objekt {
    return when (aufruf.name) {
      "gleicht dem Objekt" -> gleichtDemObjekt(injection)
      "als Zeichenfolge" -> alsZeichenfolge()
      "hashe mich" -> hash()
      else -> throw Exception("Ungültiger Aufruf '${aufruf.name}' für das Objekt '$this'!")
    }
  }

  private fun gleichtDemObjekt(injection: Interpretierer.InterpretInjection): Objekt {
    val objekt = injection.umgebung.leseVariable("Objekt")
    return Boolean(this == objekt)
  }

  private fun alsZeichenfolge(): Objekt {
    return Zeichenfolge(this.toString())
  }

  private fun hash(): Objekt {
    return Zahl(this.hashCode().toDouble())
  }
}