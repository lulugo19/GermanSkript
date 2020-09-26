package germanskript

import java.io.BufferedWriter
import java.io.File
import java.text.*
import java.math.*
import kotlin.math.*

typealias AufrufCallback = (Wert, String, argumente: Array<Wert>) -> Wert?

sealed class Wert {
  object Nichts: Wert()

  sealed class Primitiv: Wert() {
    class Zahl(val zahl: Double): Primitiv(), Comparable<Zahl> {
      object Static {
        val format = DecimalFormat()
        init {
          with (format) {
            decimalFormatSymbols.groupingSeparator = '.'
            decimalFormatSymbols.decimalSeparator = ','
            roundingMode = RoundingMode.HALF_UP
            maximumFractionDigits = 20
          }
        }
      }

      private object MathContexts {
        val FLOOR = MathContext(0, RoundingMode.FLOOR)
        val CEIL = MathContext(0, RoundingMode.DOWN)
        val ROUND = MathContext(0, RoundingMode.HALF_UP)
      }

      constructor(zahl: String): this(Static.format.parse(zahl).toDouble())
      override fun toString(): String = Static.format.format(zahl)

      operator fun unaryMinus() = Zahl(-this.zahl)
      operator fun plus(other: Zahl) = Zahl(this.zahl + other.zahl)
      operator fun minus(other: Zahl) = Zahl(this.zahl - other.zahl)
      operator fun times(other: Zahl) = Zahl(this.zahl * other.zahl)

      operator fun div(other: Zahl) = Zahl(this.zahl / other.zahl)

      override fun equals(other: Any?): kotlin.Boolean {
        return if (other is Zahl) {
          this.zahl.compareTo(other.zahl) == 0
        } else {
          false
        }
      }

      override fun hashCode(): Int = this.zahl.hashCode()

      operator fun rem(other: Zahl) = Zahl(this.zahl % other.zahl)
      fun pow(other: Zahl): Zahl = Zahl(this.zahl.pow(other.zahl))
      fun toInt() = zahl.toInt()
      fun toDouble() = zahl
      fun round() = zahl.roundToInt()
      fun floor() = floor(zahl)
      fun ceil() = ceil(zahl)

      override fun compareTo(other: Zahl): Int = this.zahl.compareTo(other.zahl)

      fun isZero() = this.zahl == 0.0
    }

    class Boolean(val boolean: kotlin.Boolean): Primitiv() {
      override fun toString(): String = boolean.toString()
    }

  }

  sealed class Objekt(internal val typ: Typ.Compound.KlassenTyp): Wert() {
    // TODO: String sollte eindeutigen Identifier zurückliefern
    override fun toString() = typ.definition.name.nominativ
    abstract fun holeEigenschaft(eigenschaftsName: String): Wert
    abstract fun setzeEigenschaft(eigenschaftsName: String, wert: Wert)

    class SkriptObjekt(
        typ: Typ.Compound.KlassenTyp,
        val eigenschaften: MutableMap<String, Wert>
    ) : Objekt(typ) {
      override fun holeEigenschaft(eigenschaftsName: String) = eigenschaften.getValue(eigenschaftsName)
      override fun setzeEigenschaft(eigenschaftsName: String, wert: Wert) {
        eigenschaften[eigenschaftsName] = wert
      }
    }

    sealed class InternesObjekt(typ: Typ.Compound.KlassenTyp): Objekt(typ) {

      abstract fun rufeMethodeAuf(
          aufruf: AST.IAufruf,
          aufrufStapel: Interpretierer.AufrufStapel,
          umgebung: Umgebung<Wert>,
          aufrufCallback: AufrufCallback
      ): Wert

      companion object {
        val zeichenFolgenTypArgument = AST.TypKnoten(emptyList(), AST.WortArt.Nomen(null,
            TypedToken.imaginäresToken(TokenTyp.BEZEICHNER_GROSS(arrayOf("Zeichenfolge"),"", null), "Zeichenfolge")),
            emptyList()
        )

        init {
          zeichenFolgenTypArgument.typ = Typ.Compound.KlassenTyp.Zeichenfolge
        }
      }

      class Liste(typ: Typ.Compound.KlassenTyp, val elemente: MutableList<Wert>): InternesObjekt(typ) {
        operator fun plus(liste: Liste) = Liste(typ, (this.elemente + liste.elemente).toMutableList())

        override fun toString(): String {
          return "[${elemente.joinToString(", ")}]"
        }

        override fun holeEigenschaft(eigenschaftsName: String): Wert {
          if (eigenschaftsName == "AnZahl") {
            return Primitiv.Zahl(elemente.size.toDouble())
          }
          throw Exception("Die Eigenschaft ${eigenschaftsName} ist für den Typen Liste nicht definiert.")
        }

        override fun setzeEigenschaft(eigenschaftsName: String, wert: Wert) {
          // vielleicht kommt hier später mal was, sieht aber nicht danach aus
        }

        override fun rufeMethodeAuf(aufruf: AST.IAufruf, aufrufStapel: Interpretierer.AufrufStapel, umgebung: Umgebung<Wert>, aufrufCallback: AufrufCallback): Wert {
          return when (aufruf.vollerName!!) {
            "enthalten das Element" -> enthaltenDenTyp(umgebung)
            "füge das Element hinzu" -> fügeDenTypHinzu(umgebung)
            "entferne an dem Index" -> entferneAnDemIndex(umgebung)
            "sortiere mich mit dem Vergleichbaren" -> sortiereMichMitDemVergleichbaren(umgebung, aufrufCallback)
            else -> throw Exception("Undefinierte Methode für Liste")
          }
        }

        private fun enthaltenDenTyp(umgebung: Umgebung<Wert>): Wert {
          val element = umgebung.leseVariable("Element")!!.wert
          return Primitiv.Boolean(elemente.contains(element))
        }

        private fun fügeDenTypHinzu(umgebung: Umgebung<Wert>): Wert {
          val element = umgebung.leseVariable("Element")!!.wert
          elemente.add(element)
          return Nichts
        }

        private fun entferneAnDemIndex(umgebung: Umgebung<Wert>): Wert {
          val index = umgebung.leseVariable("Index")!!.wert as Primitiv.Zahl
          elemente.removeAt(index.toInt())
          return Nichts
        }

        private fun sortiereMichMitDemVergleichbaren(umgebung: Umgebung<Wert>, aufrufCallback: AufrufCallback): Wert {
          val typArg = typ.typArgumente[0].name.nominativ
          val vergleichbar = umgebung.leseVariable("Vergleichbare")!!.wert
          elemente.sortWith(kotlin.Comparator { a, b ->
            (aufrufCallback(vergleichbar, "vergleiche den ${typArg}A mit dem ${typArg}B", arrayOf(a, b))
                as Primitiv.Zahl).zahl.toInt()
          })
          return Nichts
        }
      }

      data class Zeichenfolge(val zeichenfolge: String): InternesObjekt(Typ.Compound.KlassenTyp.Zeichenfolge), Comparable<Zeichenfolge> {

        override fun rufeMethodeAuf(aufruf: AST.IAufruf, aufrufStapel: Interpretierer.AufrufStapel, umgebung: Umgebung<Wert>, aufrufCallback: AufrufCallback): Wert {
          return when (aufruf.vollerName!!) {
            "code an dem Index" -> codeAnDemIndex(aufruf, aufrufStapel, umgebung)
            "buchstabiere mich groß" -> buchstabiereMichGroß()
            "buchstabiere mich klein" -> buchstabierMichKlein()
            "trenne mich zwischen dem Separator" -> trenneMichZwischenDemSeperator(umgebung)
            else -> throw Exception("Ungültige Methode '${aufruf.vollerName!!}' für die Klasse Zeichenfolge!")
          }
        }

        override fun toString(): String = zeichenfolge

        operator fun plus(zeichenfolge: Zeichenfolge) = this.zeichenfolge + zeichenfolge.zeichenfolge
        override fun compareTo(other: Zeichenfolge): Int = this.zeichenfolge.compareTo(other.zeichenfolge)

        override fun holeEigenschaft(eigenschaftsName: String): Wert {
          if (eigenschaftsName == "Länge") {
            return Primitiv.Zahl(zeichenfolge.length.toDouble())
          } else {
            throw Exception("Ungültige Eigenschaft '$eigenschaftsName' der Klasse 'Zeichenfolge'.")
          }
        }

        override fun setzeEigenschaft(eigenschaftsName: String, wert: Wert) {
          // Eine Zeichenfolge ist immutable
          TODO("Not yet implemented")
        }

        private fun codeAnDemIndex(aufruf: AST.IAufruf, aufrufStapel: Interpretierer.AufrufStapel, umgebung: Umgebung<Wert>): Wert {
          val index = (umgebung.leseVariable("Index")!!.wert as Primitiv.Zahl).toInt()
          if (index < 0 || index >= zeichenfolge.length)
            throw GermanSkriptFehler.LaufzeitFehler(aufruf.token, aufrufStapel.toString(),
                "Index außerhalb des Bereichs. Der Index ist $index, doch die Länge der Zeichenfolge ist ${zeichenfolge.length}.\n")
          return Primitiv.Zahl(zeichenfolge[index].toDouble())
        }

        private fun buchstabiereMichGroß() = Zeichenfolge(zeichenfolge.toUpperCase())

        private fun buchstabierMichKlein() = Zeichenfolge(zeichenfolge.toLowerCase())

        private fun trenneMichZwischenDemSeperator(umgebung: Umgebung<Wert>): Wert {
          val separator = umgebung.leseVariable("Separator")!!.wert as Zeichenfolge

          return Liste(Typ.Compound.KlassenTyp.Liste(listOf(zeichenFolgenTypArgument)),
              zeichenfolge.split(separator.zeichenfolge).map { Zeichenfolge(it) }.toMutableList())
        }
      }

      class Datei(typ: Typ.Compound.KlassenTyp, val eigenschaften: MutableMap<String, Wert>): InternesObjekt(typ) {

        lateinit var file: File

        override fun rufeMethodeAuf(
            aufruf: AST.IAufruf,
            aufrufStapel: Interpretierer.AufrufStapel,
            umgebung: Umgebung<Wert>,
            aufrufCallback: AufrufCallback): Wert {

          return when (aufruf.vollerName!!) {
            "erstelle die Datei" -> konstruktor()
            "lese die Zeilen" -> leseZeilen()
            "hole den Schreiber" -> holeSchreiber()
            else -> throw Exception("Die Methode '${aufruf.vollerName!!}' ist nicht definiert!")
          }
        }

        override fun holeEigenschaft(eigenschaftsName: String): Wert {
          return eigenschaften.getValue(eigenschaftsName)
        }

        override fun setzeEigenschaft(eigenschaftsName: String, wert: Wert) {

        }

        private fun konstruktor(): Nichts {
          file = File((eigenschaften.getValue("DateiName") as Zeichenfolge).zeichenfolge)
          return Nichts
        }

        private fun leseZeilen(): Liste {
          val zeilen = file.readLines().map<String, Wert> { zeile -> Zeichenfolge(zeile) }.toMutableList()
          return Liste(Typ.Compound.KlassenTyp.Liste(listOf(zeichenFolgenTypArgument)), zeilen)
        }

        private fun holeSchreiber() : Schreiber {
          return Schreiber(Typisierer.schreiberTyp, file.bufferedWriter())
        }
      }

      class Schreiber(typ: Typ.Compound.KlassenTyp, private val writer: BufferedWriter): InternesObjekt(typ) {
        override fun rufeMethodeAuf(aufruf: AST.IAufruf, aufrufStapel: Interpretierer.AufrufStapel, umgebung: Umgebung<Wert>, aufrufCallback: AufrufCallback): Wert {
          return when(aufruf.vollerName!!) {
            "schreibe die Zeile" -> schreibeDieZeile(umgebung)
            "schreibe die Zeichenfolge" -> schreibeDieZeichenfolge(umgebung)
            "füge die Zeile hinzu" -> fügeDieZeileHinzu(umgebung)
            "füge die Zeichenfolge hinzu" -> fügeDieZeichenfolgeHinzu(umgebung)
            "schließe mich" -> schließe()
            else -> throw Exception("Die Methode '${aufruf.vollerName}' ist für die Klasse 'Schreiber' nicht definiert.")
          }
        }

        override fun holeEigenschaft(eigenschaftsName: String): Wert {
          throw Exception("Die Klasse Schreiber hat keine Eigenschaften!")
        }

        override fun setzeEigenschaft(eigenschaftsName: String, wert: Wert) {
          TODO("Not yet implemented")
        }

        private fun schreibeDieZeichenfolge(umgebung: Umgebung<Wert>): Nichts {
          val zeile = umgebung.leseVariable("Zeichenfolge")!!.wert as Zeichenfolge
          writer.write(zeile.zeichenfolge)
          return Nichts
        }

        private fun schreibeDieZeile(umgebung: Umgebung<Wert>): Nichts {
          val zeile = umgebung.leseVariable("Zeile")!!.wert as Zeichenfolge
          writer.write(zeile.zeichenfolge)
          writer.newLine()
          return Nichts
        }

        private fun fügeDieZeichenfolgeHinzu(umgebung: Umgebung<Wert>): Nichts {
          val zeile = umgebung.leseVariable("Zeichenfolge")!!.wert as Zeichenfolge
          writer.append(zeile.zeichenfolge)
          return Nichts
        }

        private fun fügeDieZeileHinzu(umgebung: Umgebung<Wert>): Nichts {
          val zeile = umgebung.leseVariable("Zeile")!!.wert as Zeichenfolge
          writer.appendln(zeile.zeichenfolge)
          return Nichts
        }

        private fun schließe(): Nichts {
          writer.close()
          return Nichts
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
