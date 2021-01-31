package germanskript.alte_pipeline.intern

import germanskript.*
import germanskript.alte_pipeline.InterpretInjection

open class Objekt(internal val klasse: Typ.Compound.Klasse) {
  override fun toString() = "${klasse.name}@${this.hashCode()}"

  val eigenschaften: MutableMap<String, Objekt> = mutableMapOf()

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
      typ: Typ.Compound.Klasse
  ) : Objekt(typ) {
    override fun holeEigenschaft(eigenschaftsName: String) = eigenschaften.getValue(eigenschaftsName)
    override fun setzeEigenschaft(eigenschaftsName: String, wert: Objekt) {
      eigenschaften[eigenschaftsName] = wert
    }
  }

  class AnonymesSkriptObjekt(
      typ: Typ.Compound.Klasse,
      val umgebung: Umgebung<Objekt>
  ): SkriptObjekt(typ)

  class Lambda(
      typ: Typ.Compound.Klasse,
      val umgebung: Umgebung<Objekt>
  ): SkriptObjekt(typ)

  open fun rufeMethodeAuf(
      aufruf: AST.IAufruf,
      injection: InterpretInjection): Objekt {
    return when (aufruf.vollerName) {
      "gleicht dem Objekt" -> gleichtDemObjekt(injection)
      "als Zeichenfolge" -> alsZeichenfolge()
      "hashe mich" -> hash()
      else -> throw Exception("Ungültiger Aufruf '${aufruf.vollerName}' für das Objekt '$this'!")
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