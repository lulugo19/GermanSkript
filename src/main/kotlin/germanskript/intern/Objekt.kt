package germanskript.intern

import germanskript.*

open class Objekt(internal val klasse: Typ.Compound.Klasse) {
  override fun toString() = "${klasse.name}@${this.hashCode()}"

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
      typ: Typ.Compound.Klasse,
      val eigenschaften: MutableMap<String, Objekt>
  ) : Objekt(typ) {
    override fun holeEigenschaft(eigenschaftsName: String) = eigenschaften.getValue(eigenschaftsName)
    override fun setzeEigenschaft(eigenschaftsName: String, wert: Objekt) {
      eigenschaften[eigenschaftsName] = wert
    }
  }

  class AnonymesSkriptObjekt(
      typ: Typ.Compound.Klasse,
      eigenschaften: MutableMap<String, Objekt>,
      val umgebung: Umgebung<Objekt>,
      val istLambda: kotlin.Boolean
  ): SkriptObjekt(typ, eigenschaften)

  open fun rufeMethodeAuf(
      aufruf: AST.IAufruf,
      injection: InterpretInjection): Objekt {
    return when (aufruf.vollerName) {
      "gleicht dem Objekt" -> gleichtDemObjekt(injection)
      "als Zeichenfolge" -> alsZeichenfolge()
      "hashe mich" -> hash()
      else -> throw Exception("Ungültiger Aufruf '${aufruf.vollerName}' für das Objekt ${this}!")
    }
  }

  private fun gleichtDemObjekt(injection: InterpretInjection): Objekt {
    val objekt = injection.umgebung.leseVariable("Objekt")!!.wert
    return Boolean(this == objekt)
  }

  private fun alsZeichenfolge(): Objekt {
    return Zeichenfolge(this.toString())
  }

  private fun hash(): Objekt {
    return Zahl(this.hashCode().toDouble())
  }
}