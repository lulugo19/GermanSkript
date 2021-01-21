package germanskript.imm.intern

import germanskript.*
import germanskript.imm.IMM_AST
import germanskript.imm.Interpretierer

open class Objekt(val klasse: IMM_AST.Definition.Klasse, internal val typ: Typ.Compound.Klasse) {
  override fun toString() = "${typ.name}@${this.hashCode()}"

  protected val eigenschaften: MutableMap<String, Objekt> = mutableMapOf()

  open fun holeEigenschaft(eigenschaftsName: String): Objekt {
    TODO("Not yet implemented")
  }

  open fun setzeEigenschaft(eigenschaftsName: String, wert: Objekt){
    TODO("Not yet implemented")
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
      klasse: IMM_AST.Definition.Klasse,
      typ: Typ.Compound.Klasse
  ) : Objekt(klasse, typ) {
    override fun holeEigenschaft(eigenschaftsName: String) = eigenschaften.getValue(eigenschaftsName)
    override fun setzeEigenschaft(eigenschaftsName: String, wert: Objekt) {
      eigenschaften[eigenschaftsName] = wert
    }
  }

  class ClosureObjekt(
      klasse: IMM_AST.Definition.Klasse,
      typ: Typ.Compound.Klasse,
      val umgebung: Interpretierer.Umgebung,
      val objektArt: IMM_AST.Satz.Ausdruck.ObjektArt
  ): SkriptObjekt(klasse, typ)

  open fun rufeMethodeAuf(
      aufruf: IMM_AST.Satz.Ausdruck.IAufruf,
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