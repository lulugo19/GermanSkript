package germanskript.intern

import germanskript.*
import java.io.BufferedWriter
import java.io.File
import java.text.*
import java.math.*
import kotlin.math.*

typealias AufrufCallback = (Wert, String, argumente: Array<Wert>) -> Wert?

abstract class Wert {

  abstract class Objekt(internal val typ: Typ.Compound.KlassenTyp): Wert() {
    // TODO: String sollte eindeutigen Identifier zurückliefern
    override fun toString() = typ.definition.name.nominativ
    abstract fun holeEigenschaft(eigenschaftsName: String): Wert
    abstract fun setzeEigenschaft(eigenschaftsName: String, wert: Wert)

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

    abstract class InternesObjekt(typ: Typ.Compound.KlassenTyp): Objekt(typ) {

      abstract fun rufeMethodeAuf(
          aufruf: AST.IAufruf,
          aufrufStapel: Interpretierer.AufrufStapel,
          umgebung: Umgebung<Wert>,
          aufrufCallback: AufrufCallback
      ): Wert

      companion object {
        val zeichenFolgenTypArgument = AST.TypKnoten(emptyList(), AST.WortArt.Nomen(null,
            TypedToken.imaginäresToken(TokenTyp.BEZEICHNER_GROSS(arrayOf("Zeichenfolge"), "", null), "Zeichenfolge")),
            emptyList()
        )

        init {
          zeichenFolgenTypArgument.typ = Typ.Compound.KlassenTyp.BuildInType.Zeichenfolge
        }
      }
    }
  }

  class Closure(
      val schnittstelle: Typ.Compound.Schnittstelle,
      val ausdruck: AST.Satz.Ausdruck.Closure,
      val umgebung: Umgebung<Wert>
  ): Wert()
}
