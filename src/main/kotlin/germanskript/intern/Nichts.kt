package germanskript.intern

import germanskript.AST
import germanskript.Interpretierer
import germanskript.Typ
import germanskript.Umgebung

object Nichts: Wert.Objekt.InternesObjekt(Typ.Compound.KlassenTyp.BuildInType.Nichts) {
  override fun rufeMethodeAuf(aufruf: AST.IAufruf, aufrufStapel: Interpretierer.AufrufStapel, umgebung: Umgebung<Wert>, aufrufCallback: AufrufCallback): Wert {
    TODO("Not yet implemented")
  }

  override fun holeEigenschaft(eigenschaftsName: String): Wert {
    TODO("Not yet implemented")
  }

  override fun setzeEigenschaft(eigenschaftsName: String, wert: Wert) {
    TODO("Not yet implemented")
  }
}