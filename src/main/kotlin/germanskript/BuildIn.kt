package germanskript

object BuildIn {
  object Klassen {
    lateinit var objekt: Typ.Compound.Klasse
    lateinit var nichts: Typ.Compound.Klasse
    lateinit var niemals: Typ.Compound.Klasse
    lateinit var zahl: Typ.Compound.Klasse
    lateinit var zeichenfolge: Typ.Compound.Klasse
    lateinit var boolean: Typ.Compound.Klasse
    lateinit var reichweite: Typ.Compound.Klasse
    lateinit var schreiber: Typ.Compound.Klasse
    lateinit var liste: AST.Definition.Typdefinition.Klasse
  }

  object Schnittstellen {
    lateinit var iterierbar: AST.Definition.Typdefinition.Schnittstelle
    lateinit var addierbar: AST.Definition.Typdefinition.Schnittstelle
    lateinit var subtrahierbar: AST.Definition.Typdefinition.Schnittstelle
    lateinit var multiplizierbar: AST.Definition.Typdefinition.Schnittstelle
    lateinit var dividierbar: AST.Definition.Typdefinition.Schnittstelle
    lateinit var potenzierbar: AST.Definition.Typdefinition.Schnittstelle
    lateinit var modulobar: AST.Definition.Typdefinition.Schnittstelle
    lateinit var negierbar: AST.Definition.Typdefinition.Schnittstelle
    lateinit var vergleichbar: AST.Definition.Typdefinition.Schnittstelle
  }
}