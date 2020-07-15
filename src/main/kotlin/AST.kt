import java.util.*
import kotlin.collections.HashMap

fun <T: AST> List<T>.visit(onVisit: (AST) -> Boolean): Boolean {
  for (element in this) {
    element.visit(onVisit)
  }
  return true
}

enum class FunktionsAufrufTyp {
  FUNKTIONS_AUFRUF,
  METHODEN_SELBST_AUFRUF,
  METHODEN_BLOCK_AUFRUF,
  METHODEN_OBJEKT_AUFRUF,
}

sealed class AST {
  open val children = emptySequence<AST>()
  var parent: AST? = null
    protected set

  inline fun<reified T: AST> findNodeInParents(): T? {
    var par = this.parent
    while (par != null) {
      if (par is T) {
        return par
      }
      par = par.parent
    }
    return null
  }

  protected fun setParentForChildren() {
    for (child in children) {
      child.parent = this
      child.setParentForChildren()
    }
  }

  // visit implementation for all the leaf nodes
  fun visit(onVisit: (AST) -> Boolean) {
    if (onVisit(this)) {
      for (child in children) {
        child.visit(onVisit)
      }
    }
  }

  data class Nomen(
      val vornomen: TypedToken<TokenTyp.VORNOMEN>?,
      val bezeichner: TypedToken<TokenTyp.BEZEICHNER_GROSS>
  ) {
    var deklination: Deklination? = null
    var vornomenString: String? = null
    var numerus: Numerus? = null
    var fälle: EnumSet<Kasus> = EnumSet.noneOf(Kasus::class.java)

    val unveränderlich = vornomen?.typ == TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT
    val istSymbol get() = bezeichner.typ.istSymbol
    val geprüft get() = deklination != null
    val genus get() = if (istSymbol) Genus.NEUTRUM else deklination!!.genus

    val hauptWort: String get() = bezeichner.typ.hauptWort!!
    val nominativ: String get() = ganzesWort(Kasus.NOMINATIV, numerus!!)

    fun hauptWort(kasus: Kasus, numerus: Numerus): String {
      if (istSymbol) {
        return bezeichner.typ.symbol
      }
      return deklination!!.getForm(kasus, numerus)
    }

    fun ganzesWort(kasus: Kasus, numerus: Numerus): String {
      if (istSymbol) {
        return bezeichner.typ.symbol
      }
      return bezeichner.typ.ersetzeHauptWort(deklination!!.getForm(kasus, numerus))
    }
  }


  data class TypKnoten(
      val name: Nomen,
      var typ: Typ? = null
  )

  interface IAufruf {
    val token: Token
    val vollerName: String?
  }

  // Wurzelknoten
  data class Programm(val programmStart: Token?, val definitionen: List<Definition>, val sätze: List<Satz>): AST(), IAufruf {
    override val vollerName = "starte das Programm"
    override val token get() = programmStart!!

    override val children = sequence {
      yieldAll(definitionen)
      yieldAll(sätze)
    }

    init {
      // go through the whole AST and set the parents
      setParentForChildren()
    }
  }

  data class Funktion(
      val verb: TypedToken<TokenTyp.BEZEICHNER_KLEIN>,
      val objekt: Argument?,
      val reflexivPronomen: TypedToken<TokenTyp.REFLEXIV_PRONOMEN>?,
      val präpositionsArgumente: List<PräpositionsArgumente>,
      val suffix: TypedToken<TokenTyp.BEZEICHNER_KLEIN>?,
      override var vollerName: String? = null
  ) : AST(), IAufruf {
    override val token = verb.toUntyped()

    private val _argumente: MutableList<Argument> = mutableListOf()
    val argumente: List<Argument> = _argumente
    var funktionsDefinition: Definition.FunktionOderMethode.Funktion? = null
    var aufrufTyp: FunktionsAufrufTyp = FunktionsAufrufTyp.FUNKTIONS_AUFRUF

    init {
      if (objekt != null) {
        _argumente.add(objekt)
      }
      for (präposition in präpositionsArgumente) {
        _argumente.addAll(präposition.argumente)
      }
    }

    override val children = sequence {
      if (objekt != null) {
        yield(objekt!!)
      }
      yieldAll(präpositionsArgumente)
    }
  }

  data class Präposition(val präposition: TypedToken<TokenTyp.BEZEICHNER_KLEIN>) : AST() {
    val fälle = präpositionsFälle
        .getOrElse(präposition.wert) {
          throw GermanScriptFehler.SyntaxFehler.ParseFehler(präposition.toUntyped(), "Präposition")
        }
  }

  sealed class Definition : AST() {

    sealed class DeklinationsDefinition: Definition() {
      data class Definition(val deklination: Deklination): DeklinationsDefinition()
      data class Duden(val wort: TypedToken<TokenTyp.BEZEICHNER_GROSS>): DeklinationsDefinition()
    }

    data class TypUndName(
        val typKnoten: TypKnoten,
        val name: Nomen
    )

    data class PräpositionsParameter(
        val präposition: Präposition,
        val parameter: List<TypUndName>
    )
    sealed class FunktionOderMethode(): Definition(){
      data class Funktion(
              val rückgabeTyp: TypKnoten?,
              val name: TypedToken<TokenTyp.BEZEICHNER_KLEIN>,
              val objekt: TypUndName?,
              val präpositionsParameter: List<PräpositionsParameter>,
              val suffix: TypedToken<TokenTyp.BEZEICHNER_KLEIN>?,
              val sätze: List<Satz>,
              var vollerName: String? = null
      ):FunktionOderMethode() {

        private val _parameter: MutableList<TypUndName> = mutableListOf()
        val parameter: List<TypUndName> = _parameter

        init {
          if (objekt != null) {
            _parameter.add(objekt)
          }
          for (präposition in präpositionsParameter) {
            _parameter.addAll(präposition.parameter)
          }
        }

        override val children = sequence { yieldAll(sätze) }
      }

      data class Methode(
          val funktion: Funktion,
          val klasse: TypKnoten,
          val reflexivPronomen: TypedToken<TokenTyp.REFLEXIV_PRONOMEN>?
      ): FunktionOderMethode() {
        override val children = sequence { yieldAll(funktion.sätze) }
      }

    }

    data class Klasse(
        val typ: TypKnoten,
        val elternKlasse: TypKnoten?,
        val eigenschaften: List<TypUndName>,
        val konstruktorSätze: List<Satz>
    ): Definition() {
      val methoden: HashMap<String, FunktionOderMethode.Methode> = HashMap()
      val konvertierungen: HashMap<String, Konvertierung> = HashMap()
      override val children = sequence {yieldAll(konstruktorSätze)}
    }

    data class Konvertierung(
        val typ: TypKnoten,
        val klasse: Nomen,
        val sätze: List<Satz>
    ): Definition() {
      override val children: Sequence<AST> = sequence { yieldAll(sätze) }
    }
  }


  sealed class Satz : AST() {
    object Intern: Satz()

    sealed class SchleifenKontrolle: Satz() {
      object Fortfahren: SchleifenKontrolle()
      object Abbrechen: SchleifenKontrolle()
    }

    data class VariablenDeklaration(
        val name: Nomen,
        val neu: TypedToken<TokenTyp.NEU>?,
        val zuweisungsOperator: TypedToken<TokenTyp.ZUWEISUNG>,
        val ausdruck: Ausdruck
    ): Satz() {
      override val children = sequenceOf(ausdruck)

      val istEigenschaftsNeuZuweisung = name.vornomen!!.typ == TokenTyp.VORNOMEN.POSSESSIV_PRONOMEN.MEIN
    }

    data class BedingungsTerm(
        val bedingung: Ausdruck,
        val sätze: List<Satz>
    ): AST() {
      override val children = sequence {
          yield(bedingung)
          yieldAll(sätze)
        }
    }

    data class Bedingung(
        val bedingungen: List<BedingungsTerm>,
        val sonst: List<Satz>?
    ): Satz() {
      override val children = sequence {
          yieldAll(bedingungen)
          if (sonst != null) {
            yieldAll(sonst!!)
          }
        }
    }

    data class SolangeSchleife(
        val bedingung: BedingungsTerm
    ) : Satz() {
      override val children = sequence {
          yield(bedingung)
        }
    }

    data class FürJedeSchleife(
        val jede: TypedToken<TokenTyp.JEDE>,
        val singular: Nomen,
        val binder: Nomen,
        val liste: Ausdruck?,
        val sätze: List<Satz>
    ): Satz() {
      override val children = sequence {
          if (liste != null) {
            yield(liste!!)
          }
          yieldAll(sätze)
        }
    }

    data class FunktionsAufruf(val aufruf: Funktion): Satz() {
      override val children = sequenceOf(aufruf)
    }

    data class MethodenBlock(val name: Nomen, val sätze: List<Satz>): Satz(){
      override val children = sequence { yieldAll(sätze) }
    }

    data class Zurückgabe(val erstesToken: TypedToken<TokenTyp.BEZEICHNER_KLEIN>, val ausdruck: Ausdruck?): Satz() {
      override val children = sequence {
        if (ausdruck != null) {
          yield(ausdruck!!)
        }
      }
    }
  }

  data class Argument(
      val name: Nomen,
      val wert: Ausdruck
  ): AST() {
    override val children = sequenceOf(wert)
  }

  data class PräpositionsArgumente(val präposition: Präposition, val argumente: List<Argument>): AST() {
    override val children = sequence { yieldAll(argumente) }
  }

  sealed class Ausdruck : AST() {
    data class Zeichenfolge(val zeichenfolge: TypedToken<TokenTyp.ZEICHENFOLGE>) : Ausdruck()

    data class Zahl(val zahl: TypedToken<TokenTyp.ZAHL>) : Ausdruck()

    data class Boolean(val boolean: TypedToken<TokenTyp.BOOLEAN>) : Ausdruck()

    data class Variable(val name: Nomen) : Ausdruck()

    data class Liste(val pluralTyp: Nomen, val elemente: List<Ausdruck>): Ausdruck() {
      override val children = sequence { yieldAll(elemente) }
    }

    data class ListenElement(
        val singular: Nomen,
        val index: Ausdruck
    ): Ausdruck() {
      override val children = sequenceOf(index)
    }

    data class FunktionsAufruf(val aufruf: Funktion): Ausdruck() {
      override val children = sequenceOf(aufruf)
    }

    data class BinärerAusdruck(
        val operator: TypedToken<TokenTyp.OPERATOR>,
        val links: Ausdruck,
        val rechts: Ausdruck,
        val istAnfang: kotlin.Boolean) : Ausdruck() {
      override val children = sequenceOf(links, rechts)
    }

    data class Minus(val ausdruck: Ausdruck) : Ausdruck() {
      override val children = sequenceOf(ausdruck)
    }

    data class Konvertierung(
        val ausdruck: Ausdruck,
        val typ: TypKnoten
    ):Ausdruck(), IAufruf {
      override val token = typ.name.bezeichner.toUntyped()
      override val vollerName = "als ${typ.name}"
    }

    data class ObjektInstanziierung(
        val klasse: TypKnoten,
        val eigenschaftsZuweisungen: List<Argument>
    ): Ausdruck(), IAufruf {
      override val children = sequence { yieldAll(eigenschaftsZuweisungen) }

      override val token: Token = klasse.name.bezeichner.toUntyped()

      override val vollerName: String get() {
        val artikel = when (klasse.name.genus) {
          Genus.MASKULINUM -> "den"
          Genus.FEMININUM -> "die"
          Genus.NEUTRUM -> "das"
        }
        return "erstelle $artikel ${klasse.name.ganzesWort(Kasus.NOMINATIV, Numerus.SINGULAR)}"
      }
    }

    data class EigenschaftsZugriff(
        val eigenschaftsName: Nomen,
        val objekt: Ausdruck
    ): Ausdruck() {
      override val children = sequenceOf(objekt)
    }

    data class MethodenBlockEigenschaftsZugriff(
        val eigenschaftsName: Nomen
    ): Ausdruck()

    data class SelbstEigenschaftsZugriff(
        val eigenschaftsName: Nomen
    ): Ausdruck()

    data class SelbstReferenz(val ich: TypedToken<TokenTyp.REFERENZ.ICH>): Ausdruck()
    data class MethodenBlockReferenz(val du: TypedToken<TokenTyp.REFERENZ.DU>): Ausdruck()
  }
}