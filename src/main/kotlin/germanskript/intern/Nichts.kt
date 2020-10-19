package germanskript.intern

import germanskript.AST
import germanskript.Interpretierer
import germanskript.Typ
import germanskript.Umgebung

object Nichts: Wert.Objekt(Typ.Compound.KlassenTyp.BuildInType.Nichts) {}