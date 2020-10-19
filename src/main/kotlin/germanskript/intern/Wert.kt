package germanskript.intern

import germanskript.*

typealias AufrufCallback = (Wert, String, argumente: Array<Wert>) -> Wert?

abstract class Wert {

  open class Objekt(internal val typ: Typ.Compound.KlassenTyp): Wert() {
    override fun toString() = "${typ.name}@${this.hashCode()}"

    open fun holeEigenschaft(eigenschaftsName: String): Wert {
      TODO("Not yet implemented")
    }

    open fun setzeEigenschaft(eigenschaftsName: String, wert: Wert){
      TODO("Not yet implemented")
    }

    companion object {
      val zeichenFolgenTypArgument = AST.TypKnoten(emptyList(), AST.WortArt.Nomen(null,
          TypedToken.imaginäresToken(TokenTyp.BEZEICHNER_GROSS(arrayOf("Zeichenfolge"), "", null), "Zeichenfolge")),
          emptyList()
      )

      init {
        zeichenFolgenTypArgument.typ = Typ.Compound.KlassenTyp.BuildInType.Zeichenfolge
      }
    }

    open class SkriptObjekt(
        typ: Typ.Compound.KlassenTyp,
        val eigenschaften: MutableMap<String, Wert>
    ) : Objekt(typ) {
      override fun holeEigenschaft(eigenschaftsName: String) = eigenschaften.getValue(eigenschaftsName)
      override fun setzeEigenschaft(eigenschaftsName: String, wert: Wert) {
        eigenschaften[eigenschaftsName] = wert
      }
    }

    class AnonymesSkriptObjekt(
        typ: Typ.Compound.KlassenTyp,
        eigenschaften: MutableMap<String, Wert>,
        val umgebung: Umgebung<Wert>
    ): SkriptObjekt(typ, eigenschaften)

    open fun rufeMethodeAuf(
        aufruf: AST.IAufruf,
        aufrufStapel: Interpretierer.AufrufStapel,
        umgebung: Umgebung<Wert>,
        aufrufCallback: AufrufCallback): Wert {
      return when (aufruf.vollerName) {
        "gleicht dem Objekt" -> gleichtDemObjekt(umgebung)
        "als Zeichenfolge" -> alsZeichenfolge()
        "hashe mich" -> hash()
        else -> throw Exception("Ungültiger Aufruf '${aufruf.vollerName}' für das Objekt ${this}!")
      }
    }

    private fun gleichtDemObjekt(umgebung: Umgebung<Wert>): Wert {
      val objekt = umgebung.leseVariable("Objekt")
      return Boolean(this === objekt)
    }

    private fun alsZeichenfolge(): Wert {
      return  Zeichenfolge(this.toString())
    }

    private fun hash(): Wert {
      return Zahl(this.hashCode().toDouble())
    }
  }

  class Closure(
      val schnittstelle: Typ.Compound.Schnittstelle,
      val ausdruck: AST.Satz.Ausdruck.Closure,
      val umgebung: Umgebung<Wert>
  ): Wert()
}
