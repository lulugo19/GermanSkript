package germanskript

import java.io.File
import germanskript.util.SimpleLogger

class TypPrüfer(startDatei: File): ProgrammDurchlaufer<Typ>(startDatei) {
  val typisierer = Typisierer(startDatei)
  override val definierer = typisierer.definierer
  override var umgebung = Umgebung<Typ>()
  val logger = SimpleLogger()
  val ast: AST.Programm get() = typisierer.ast

  private var zuÜberprüfendeKlasse: AST.Definition.Typdefinition.Klasse? = null
  private var rückgabeTyp: Typ? = null
  private var rückgabeErreicht = false

  fun prüfe() {
    typisierer.typisiere()
    // zuerst die Klassendefinitionen, damit noch private Eigenschaften definiert werden können
    definierer.holeDefinitionen<AST.Definition.Typdefinition.Klasse>().forEach(::prüfeKlasse)

    definierer.funktionsDefinitionen.forEach(::prüfeFunktion)

    // neue germanskript.Umgebung
    durchlaufeAufruf(ast.programmStart!!, ast.programm, Umgebung(), true,null)
  }

  private fun ausdruckMussTypSein(ausdruck: AST.Ausdruck, erwarteterTyp: Typ): Typ {
    val ausdruckTyp = evaluiereAusdruck(ausdruck)
    @Suppress("NAME_SHADOWING")
    var erwarteterTyp = erwarteterTyp
    if (erwarteterTyp is Typ.Generic) {
      // TODO: Wenn das Methodenblock-Objekt nicht existiert oder keine Liste (kein generischer Typ ist) müssen wir irgendetwas machen
      erwarteterTyp = (umgebung.holeMethodenBlockObjekt() as Typ.KlassenTyp.Liste).elementTyp
    }
    else if (erwarteterTyp is Typ.Schnittstelle) {
      when (ausdruckTyp) {
        is Typ.KlassenTyp -> {
          val methoden = ausdruckTyp.klassenDefinition.methoden
          if (!erwarteterTyp.definition.methodenSignaturen.all { methoden.containsKey(it.vollerName!!) }) {
            throw GermanSkriptFehler.UnimplementierteSchnittstelle(holeErstesTokenVonAusdruck(ausdruck),
                ausdruckTyp.klassenDefinition, erwarteterTyp.definition)
          }
          return ausdruckTyp
        }
        // es muss ein Closure sein
        else -> return erwarteterTyp
      }
    }
    if (ausdruckTyp is Typ.KlassenTyp && erwarteterTyp is Typ.KlassenTyp) {
      // eine Kindklasse ist ein gültiger Typ für eine Elternklasse
      var typ: Typ.KlassenTyp? = ausdruckTyp
      while (typ != null) {
        if (typ == erwarteterTyp) {
          return erwarteterTyp
        }
        typ = ausdruckTyp.klassenDefinition.elternKlasse?.typ as Typ.KlassenTyp?
      }
      throw GermanSkriptFehler.TypFehler.FalscherTyp(holeErstesTokenVonAusdruck(ausdruck), ausdruckTyp, erwarteterTyp.name)
    }
    else if (ausdruckTyp != erwarteterTyp) {
      throw GermanSkriptFehler.TypFehler.FalscherTyp(holeErstesTokenVonAusdruck(ausdruck), ausdruckTyp, erwarteterTyp.name)
    }
    return ausdruckTyp
  }

  // breche niemals Sätze ab
  override fun sollSätzeAbbrechen(): Boolean = false

  private fun prüfeFunktion(funktion: AST.Definition.FunktionOderMethode.Funktion) {
    val funktionsUmgebung = Umgebung<Typ>()
    funktionsUmgebung.pushBereich()
    for (parameter in funktion.signatur.parameter) {
      funktionsUmgebung.schreibeVariable(parameter.name, parameter.typKnoten.typ!!, false)
    }
    val signatur = funktion.signatur
    durchlaufeAufruf(signatur.name.toUntyped(), funktion.körper, funktionsUmgebung, false, signatur.rückgabeTyp?.typ)
  }

  private fun prüfeKlasse(klasse: AST.Definition.Typdefinition.Klasse) {
    if (klasse.typ.typ != null) {
      return
    }
    typisierer.typisiereKlasse(klasse)
    // Da die Kindklasse abhängig von der Elternklasse ist, muss zuerst die Elternklasse geprüft werden
    if (klasse.elternKlasse != null) {
      if (klasse.elternKlasse.typ == null) {
        typisierer.bestimmeTypen(klasse.elternKlasse, true)
      }
      val elternKlasse = klasse.elternKlasse.typ as Typ.KlassenTyp
      prüfeKlasse(elternKlasse.klassenDefinition)
      klasse.eigenschaften.addAll(0, elternKlasse.klassenDefinition.eigenschaften)
    }
    zuÜberprüfendeKlasse = klasse
    durchlaufeAufruf(klasse.typ.name.bezeichner.toUntyped(), klasse.konstruktor, Umgebung(), true,null)
    klasse.methoden.values.forEach {methode -> prüfeFunktion(methode.funktion)}
    klasse.konvertierungen.values.forEach {konvertierung ->
      durchlaufeAufruf(konvertierung.klasse.name.bezeichner.toUntyped(), konvertierung.definition, Umgebung(), true, konvertierung.typ.typ!!)
    }

    fun fügeElternKlassenMethodenHinzu(elternKlasse: AST.Definition.Typdefinition.Klasse) {
      for ((methodenName, methode) in elternKlasse.methoden) {
        klasse.methoden.putIfAbsent(methodenName, methode)
      }
      for ((konvertierungsTyp, konvertierung) in elternKlasse.konvertierungen) {
        klasse.konvertierungen.putIfAbsent(konvertierungsTyp, konvertierung)
      }
      if (elternKlasse.elternKlasse != null) {
        fügeElternKlassenMethodenHinzu((elternKlasse.elternKlasse.typ as Typ.KlassenTyp).klassenDefinition)
      }
    }

    if (klasse.elternKlasse != null) {
      fügeElternKlassenMethodenHinzu((klasse.elternKlasse.typ as Typ.KlassenTyp).klassenDefinition)
    }
  }

  private fun durchlaufeAufruf(token: Token, bereich: AST.Satz.Bereich, umgebung: Umgebung<Typ>, neuerBereich: Boolean, rückgabeTyp: Typ?) {
    this.rückgabeTyp = rückgabeTyp
    this.umgebung = umgebung
    rückgabeErreicht = false
    durchlaufeBereich(bereich, neuerBereich)
    if (rückgabeTyp != null && !rückgabeErreicht) {
      throw GermanSkriptFehler.RückgabeFehler.RückgabeVergessen(token, rückgabeTyp)
    }
  }

  override fun durchlaufeVariablenDeklaration(deklaration: AST.Satz.VariablenDeklaration) {
    if (deklaration.istEigenschaftsNeuZuweisung) {
      val klasse = zuÜberprüfendeKlasse!!
      val eigenschaft = holeEigenschaftAusKlasse(deklaration.name, klasse)
      if (eigenschaft.typKnoten.name.unveränderlich) {
        throw GermanSkriptFehler.EigenschaftsFehler.EigenschaftUnveränderlich(deklaration.name.bezeichner.toUntyped())
      }
      val eigenschaftTyp = eigenschaft.typKnoten.typ!!
      ausdruckMussTypSein(deklaration.ausdruck, eigenschaftTyp)
    }
    else if (deklaration.istEigenschaft) {
      val klasse = zuÜberprüfendeKlasse!!
      val wert = evaluiereAusdruck(deklaration.ausdruck)
      val typ = AST.TypKnoten(deklaration.name, emptyList())
      typ.typ = wert
      klasse.eigenschaften.add(AST.Definition.TypUndName(typ, deklaration.name))
    }
    else {
      if (deklaration.name.unveränderlich || deklaration.neu != null) {
        val wert = evaluiereAusdruck(deklaration.ausdruck)
        umgebung.schreibeVariable(deklaration.name, wert, !deklaration.name.unveränderlich)
      }
      else {
        // hier müssen wir überprüfen ob der Typ der Variable, die überschrieben werden sollen gleich
        // dem neuen germanskript.Wert ist
        val vorherigerTyp = umgebung.leseVariable(deklaration.name.nominativ)
        val wert = if (vorherigerTyp != null) {
          if (vorherigerTyp.name.unveränderlich) {
            throw GermanSkriptFehler.Variablenfehler(deklaration.name.bezeichner.toUntyped(), vorherigerTyp.name)
          }
          ausdruckMussTypSein(deklaration.ausdruck, vorherigerTyp.wert)
        } else {
          evaluiereAusdruck(deklaration.ausdruck)
        }
        umgebung.überschreibeVariable(deklaration.name, wert)
      }
    }
  }

  private val typDeklination = Deklination(Genus.MASKULINUM, arrayOf("Typ", "Typs", "Typ", "Typ"), arrayOf("Typen", "Typen", "Typen", "Typen"))

  override fun durchlaufeFunktionsAufruf(funktionsAufruf: AST.Funktion, istAusdruck: Boolean): Typ? {
    if (funktionsAufruf.vollerName == null) {
      funktionsAufruf.vollerName = definierer.holeVollenNamenVonFunktionsAufruf(funktionsAufruf, null)
    }
    var funktionsSignatur: AST.Definition.FunktionsSignatur? = null
    logger.addLine("prüfe Funktionsaufruf in ${funktionsAufruf.verb.position}: ${funktionsAufruf.vollerName!!}")
    val methodenBlockObjekt = umgebung.holeMethodenBlockObjekt()

    // ist Methodenaufruf von Block-Variable
    if (methodenBlockObjekt is Typ.KlassenTyp) {
      funktionsSignatur = findeMethode(
          funktionsAufruf,
          methodenBlockObjekt,
          FunktionsAufrufTyp.METHODEN_BLOCK_AUFRUF,
          TokenTyp.REFLEXIV_PRONOMEN.DICH,
          true
      )
    }

    // Schnittstellen Aufruf
    if (funktionsSignatur == null && methodenBlockObjekt is Typ.Schnittstelle) {
      funktionsSignatur = methodenBlockObjekt.definition.methodenSignaturen.find { signatur -> signatur.vollerName!! == funktionsAufruf.vollerName!! }
      if (funktionsSignatur != null) {
        funktionsAufruf.vollerName = funktionsSignatur.vollerName
        funktionsAufruf.aufrufTyp = FunktionsAufrufTyp.METHODEN_BLOCK_AUFRUF
      }
    }

    // ist Methoden-Selbst-Aufruf
    if (funktionsSignatur == null && zuÜberprüfendeKlasse != null) {
      funktionsSignatur = findeMethode(
          funktionsAufruf,
          Typ.KlassenTyp.Klasse(zuÜberprüfendeKlasse!!),
          FunktionsAufrufTyp.METHODEN_SELBST_AUFRUF,
          TokenTyp.REFLEXIV_PRONOMEN.MICH,
          true
      )
    }

    // ist normale Funktion
    val undefiniertFehler = try {
      if (funktionsSignatur == null) {
        funktionsAufruf.funktionsDefinition = definierer.holeFunktionsDefinition(funktionsAufruf)
        funktionsSignatur = funktionsAufruf.funktionsDefinition!!.signatur
        null
      } else {
        null
      }
    } catch (fehler: GermanSkriptFehler.Undefiniert.Funktion) {
      fehler
    }

    // ist Methoden-Objekt-Aufruf als letzte Möglichkeit
    // das bedeutet Funktionsnamen gehen vor Methoden-Objekt-Aufrufen
    if (funktionsSignatur == null && funktionsAufruf.objekt != null) {
      val klasse = try {
        typisierer.bestimmeTypen(funktionsAufruf.objekt.name)
      }
      catch (fehler: GermanSkriptFehler.Undefiniert.Typ) {
        null
      }
      if (klasse is Typ.KlassenTyp) {
        val methoden = klasse.klassenDefinition.methoden
        val klassenTyp = klasse.klassenDefinition.typ
        val reflexivPronomen = when (funktionsAufruf.objekt.name.fälle.first()) {
          Kasus.AKKUSATIV -> "mich"
          Kasus.DATIV -> "mir"
          else -> throw Exception("Dieser Fall sollte nie eintreten, da der Grammatikprüfer dies überprüfen sollte. "
              + "${klassenTyp.name.bezeichner}")
        }
        val methodenName = definierer.holeVollenNamenVonFunktionsAufruf(funktionsAufruf, reflexivPronomen)
        if (methoden.containsKey(methodenName)) {
          funktionsAufruf.vollerName = "für ${klassenTyp.name.bezeichner}: ${methodenName}"
          funktionsAufruf.funktionsDefinition = methoden.getValue(methodenName).funktion
          funktionsAufruf.aufrufTyp = FunktionsAufrufTyp.METHODEN_OBJEKT_AUFRUF
          funktionsSignatur = funktionsAufruf.funktionsDefinition!!.signatur
        }
      }
    }

    if (funktionsSignatur == null) {
      throw undefiniertFehler!!
    }

    if (istAusdruck && funktionsSignatur.rückgabeTyp == null) {
      throw GermanSkriptFehler.SyntaxFehler.FunktionAlsAusdruckFehler(funktionsSignatur.name.toUntyped())
    }

    val parameter = funktionsSignatur.parameter
    val argumente = funktionsAufruf.argumente
    val j = if (funktionsAufruf.aufrufTyp == FunktionsAufrufTyp.METHODEN_OBJEKT_AUFRUF) 1 else 0
    val anzahlArgumente = argumente.size - j
    if (anzahlArgumente != parameter.size) {
      throw GermanSkriptFehler.SyntaxFehler.AnzahlDerParameterFehler(funktionsSignatur.name.toUntyped())
    }

    // stimmen die Argument Typen mit den Parameter Typen überein?
    for (i in parameter.indices) {
      ausdruckMussTypSein(argumente[i+j].wert, parameter[i].typKnoten.typ!!)
    }

    return funktionsSignatur.rückgabeTyp?.typ
  }

  private fun findeMethode(
      funktionsAufruf: AST.Funktion,
      typ: Typ.KlassenTyp,
      aufrufTyp: FunktionsAufrufTyp,
      reflexivPronomen: TokenTyp.REFLEXIV_PRONOMEN,
      erlaubeHoleElternDefinition: Boolean
  ): AST.Definition.FunktionsSignatur? {

    // versuche Methode in der Elternklasse zu finden
    if (aufrufTyp == FunktionsAufrufTyp.METHODEN_SELBST_AUFRUF &&
        erlaubeHoleElternDefinition && inSuperBlock && typ.klassenDefinition.elternKlasse != null) {
      val signatur = findeMethode(
          funktionsAufruf, typ.klassenDefinition.elternKlasse!!.typ!! as Typ.KlassenTyp,
          aufrufTyp, reflexivPronomen, false
      )
      if (signatur != null) {
        return signatur
      }
    }

    // ist Methodenaufruf von Block-Variable
    if (typ.klassenDefinition.methoden.containsKey(funktionsAufruf.vollerName!!)) {
      val methodenDefinition = typ.klassenDefinition.methoden.getValue(funktionsAufruf.vollerName!!).funktion
      // Bei einem Selbstaufruf wird die Methodendefinition festgelegt (kein dynamisches Binding)
      if (aufrufTyp == FunktionsAufrufTyp.METHODEN_SELBST_AUFRUF) {
        funktionsAufruf.funktionsDefinition = methodenDefinition
        funktionsAufruf.vollerName = "für ${typ.klassenDefinition.typ.name.bezeichner.wert}: ${funktionsAufruf.vollerName}"
      }
      funktionsAufruf.aufrufTyp = aufrufTyp
      return methodenDefinition.signatur
    }
    else if (typ is Typ.KlassenTyp.Liste && funktionsAufruf.objekt != null) {
      val objektName = funktionsAufruf.objekt.name
      val artikel = GrammatikPrüfer.holeVornomen(TokenTyp.VORNOMEN.ARTIKEL.BESTIMMT, objektName.fälle.first(), typDeklination.genus, objektName.numerus!!)
      val typWort = typDeklination.holeForm(funktionsAufruf.objekt.name.fälle.first(), funktionsAufruf.objekt.name.numerus!!)
      val methodenName = definierer.holeVollenNamenVonFunktionsAufruf(funktionsAufruf, "$artikel $typWort")
      if (typ.klassenDefinition.methoden.containsKey(methodenName)) {
        funktionsAufruf.vollerName = "für Liste: $methodenName"
        funktionsAufruf.funktionsDefinition = typ.klassenDefinition.methoden.getValue(methodenName).funktion
        funktionsAufruf.aufrufTyp = aufrufTyp
        return funktionsAufruf.funktionsDefinition!!.signatur
      }
    }
    else if (funktionsAufruf.reflexivPronomen != null &&
        funktionsAufruf.reflexivPronomen.typ == reflexivPronomen &&
        typ.klassenDefinition.elternKlasse == null) {
      throw GermanSkriptFehler.Undefiniert.Methode(funktionsAufruf.verb.toUntyped(),
          funktionsAufruf,
          typ.klassenDefinition.typ.name.nominativ)
    }
    return null
  }

  override fun durchlaufeZurückgabe(zurückgabe: AST.Satz.Zurückgabe) {
    if (rückgabeTyp == null && zurückgabe.ausdruck != null) {
      throw GermanSkriptFehler.RückgabeFehler.UngültigeRückgabe(zurückgabe.erstesToken.toUntyped())
    }
    if (rückgabeTyp != null) {
      if (zurückgabe.ausdruck == null) {
        throw GermanSkriptFehler.RückgabeFehler.RückgabeVergessen(zurückgabe.erstesToken.toUntyped(), rückgabeTyp!!)
      }
      ausdruckMussTypSein(zurückgabe.ausdruck!!, rückgabeTyp!!)
    }
    // Die Rückgabe ist nur auf alle Fälle erreichbar, wenn sie an keine Bedingung und in keiner Schleife ist
    rückgabeErreicht = zurückgabe.findNodeInParents<AST.Satz.BedingungsTerm>() == null &&
        zurückgabe.findNodeInParents<AST.Satz.FürJedeSchleife>() == null
  }

  private fun prüfeBedingung(bedingung: AST.Satz.BedingungsTerm) {
    ausdruckMussTypSein(bedingung.bedingung, Typ.Boolean)
    durchlaufeBereich(bedingung.bereich, true)
  }

  override fun durchlaufeBedingungsSatz(bedingungsSatz: AST.Satz.Bedingung) {
    bedingungsSatz.bedingungen.forEach(::prüfeBedingung)
    bedingungsSatz.sonst?.also {durchlaufeBereich(it, true)}
  }

  override fun durchlaufeSolangeSchleife(schleife: AST.Satz.SolangeSchleife) {
    prüfeBedingung(schleife.bedingung)
  }

  override fun durchlaufeFürJedeSchleife(schleife: AST.Satz.FürJedeSchleife) {
    val elementTyp = if (schleife.liste != null) {
      val liste = evaluiereAusdruck(schleife.liste)
      if (liste !is Typ.KlassenTyp.Liste) {
        throw GermanSkriptFehler.TypFehler.FalscherTyp(holeErstesTokenVonAusdruck(schleife.liste), liste, "Liste")
      }
      liste.elementTyp
    } else {
      evaluiereListenSingular(schleife.singular)
    }
    umgebung.pushBereich()
    umgebung.schreibeVariable(schleife.binder, elementTyp, false)
    durchlaufeBereich(schleife.bereich, true)
    umgebung.popBereich()
  }

  override fun durchlaufeIntern() {
    // Hier muss nicht viel gemacht werden
    // die internen Sachen sind schon von kotlin Typ-gecheckt :)
    rückgabeErreicht = true
  }

  override fun bevorDurchlaufeMethodenBlock(methodenBlock: AST.Satz.MethodenBlock, blockObjekt: Typ?) {
    if (blockObjekt !is Typ.KlassenTyp && blockObjekt !is Typ.Schnittstelle) {
      throw GermanSkriptFehler.TypFehler.ObjektErwartet(methodenBlock.name.bezeichner.toUntyped())
    }
  }

  override fun durchlaufeAbbrechen() {
    // hier muss nichts gemacht werden...
  }

  override fun durchlaufeFortfahren() {
    // hier muss nichts gemacht werden...
  }

  override fun starteBereich(bereich: AST.Satz.Bereich) {
    // hier muss nichts gemacht werden...
  }

  override fun beendeBereich(bereich: AST.Satz.Bereich) {
    // hier muss nichts gemacht werden...
  }

  override fun evaluiereZeichenfolge(ausdruck: AST.Ausdruck.Zeichenfolge) = Typ.Zeichenfolge

  override fun evaluiereZahl(ausdruck: AST.Ausdruck.Zahl) = Typ.Zahl

  override fun evaluiereBoolean(ausdruck: AST.Ausdruck.Boolean) = Typ.Boolean

  override fun evaluiereListe(ausdruck: AST.Ausdruck.Liste): Typ {
    val listenTyp = typisierer.bestimmeTypen(ausdruck.pluralTyp) as Typ.KlassenTyp.Liste
    ausdruck.elemente.forEach {element -> ausdruckMussTypSein(element, listenTyp.elementTyp)}
    return listenTyp
  }

  override fun evaluiereListenElement(listenElement: AST.Ausdruck.ListenElement): Typ {
    ausdruckMussTypSein(listenElement.index, Typ.Zahl)
    val zeichenfolge = evaluiereVariable(listenElement.singular.hauptWort)
    // Bei einem Zugriff auf einen Listenindex kann es sich auch um eine Zeichenfolge handeln
    if (zeichenfolge != null) {
      if (zeichenfolge !is Typ.Zeichenfolge) {
        throw GermanSkriptFehler.TypFehler.FalscherTyp(
            listenElement.singular.bezeichner.toUntyped(), zeichenfolge, "Zeichenfolge")
      }
      return Typ.Zeichenfolge
    }
    return evaluiereListenSingular(listenElement.singular)
  }

  private fun evaluiereListenSingular(singular: AST.Nomen): Typ {
    val plural = singular.ganzesWort(Kasus.NOMINATIV, Numerus.PLURAL)
    val liste = evaluiereVariable(plural)?:
    throw GermanSkriptFehler.Undefiniert.Variable(singular.bezeichner.toUntyped(), plural)

    return (liste as Typ.KlassenTyp.Liste).elementTyp
  }

  override fun evaluiereObjektInstanziierung(instanziierung: AST.Ausdruck.ObjektInstanziierung): Typ {
    val klasse = typisierer.bestimmeTypen(instanziierung.klasse, true)!!
    if (klasse !is Typ.KlassenTyp) {
      throw GermanSkriptFehler.TypFehler.ObjektErwartet(instanziierung.klasse.name.bezeichner.toUntyped())
    }

    val definition = klasse.klassenDefinition

    // die Eigenschaftszuweisungen müssen mit der Instanzzierung übereinstimmen, Außerdem müssen die Namen übereinstimmen
    var j = 0
    for (i in definition.eigenschaften.indices) {
      val eigenschaft = definition.eigenschaften[i]
      if (eigenschaft.istPrivat) {
        j++
        continue
      }
      if (i >= instanziierung.eigenschaftsZuweisungen.size + j) {
        throw GermanSkriptFehler.EigenschaftsFehler.EigenschaftsVergessen(instanziierung.klasse.name.bezeichner.toUntyped(), eigenschaft.name.nominativ)
      }
      val zuweisung = instanziierung.eigenschaftsZuweisungen[i-j]

      if (eigenschaft.name.nominativ != zuweisung.name.nominativ) {
        GermanSkriptFehler.EigenschaftsFehler.UnerwarteterEigenschaftsName(zuweisung.name.bezeichner.toUntyped(), eigenschaft.name.nominativ)
      }

      // die Typen müssen übereinstimmen
      ausdruckMussTypSein(zuweisung.wert, eigenschaft.typKnoten.typ!!)
    }

    if (instanziierung.eigenschaftsZuweisungen.size > definition.eigenschaften.size) {
      throw GermanSkriptFehler.EigenschaftsFehler.UnerwarteteEigenschaft(
          instanziierung.eigenschaftsZuweisungen[definition.eigenschaften.size].name.bezeichner.toUntyped())
    }
    return klasse
  }

  override fun evaluiereEigenschaftsZugriff(eigenschaftsZugriff: AST.Ausdruck.EigenschaftsZugriff): Typ {
    val klasse = evaluiereAusdruck(eigenschaftsZugriff.objekt)
    return if (klasse is Typ.KlassenTyp) {
      holeEigenschaftAusKlasse(eigenschaftsZugriff.eigenschaftsName, klasse.klassenDefinition).typKnoten.typ!!
    } else if (klasse is Typ.Zeichenfolge && eigenschaftsZugriff.eigenschaftsName.nominativ == "Länge") {
      Typ.Zahl
    }
    else {
      throw GermanSkriptFehler.TypFehler.ObjektErwartet(holeErstesTokenVonAusdruck(eigenschaftsZugriff.objekt))
    }
  }

  override fun evaluiereSelbstEigenschaftsZugriff(eigenschaftsZugriff: AST.Ausdruck.SelbstEigenschaftsZugriff): Typ {
    return holeEigenschaftAusKlasse(eigenschaftsZugriff.eigenschaftsName, zuÜberprüfendeKlasse!!).typKnoten.typ!!
  }

  override fun evaluiereMethodenBlockEigenschaftsZugriff(eigenschaftsZugriff: AST.Ausdruck.MethodenBlockEigenschaftsZugriff): Typ {
    val methodenBlockObjekt = umgebung.holeMethodenBlockObjekt() as Typ.KlassenTyp
    return holeEigenschaftAusKlasse(eigenschaftsZugriff.eigenschaftsName, methodenBlockObjekt.klassenDefinition).typKnoten.typ!!
  }

  private fun holeEigenschaftAusKlasse(eigenschaftsName: AST.Nomen, klasse: AST.Definition.Typdefinition.Klasse): AST.Definition.TypUndName {
    for (eigenschaft in klasse.eigenschaften) {
      if (eigenschaftsName.nominativ == eigenschaft.name.nominativ) {
        return eigenschaft
      }
    }
    throw GermanSkriptFehler.Undefiniert.Eigenschaft(eigenschaftsName.bezeichner.toUntyped(), klasse.typ.name.nominativ)
  }

  override fun evaluiereBinärenAusdruck(ausdruck: AST.Ausdruck.BinärerAusdruck): Typ {
    val linkerTyp = evaluiereAusdruck(ausdruck.links)
    val operator = ausdruck.operator.typ.operator
    if (!linkerTyp.definierteOperatoren.containsKey(operator)) {
      throw GermanSkriptFehler.Undefiniert.Operator(ausdruck.operator.toUntyped(), linkerTyp.name)
    }
    // es wird erwartete, dass bei einem binären Ausdruck beide Operanden vom selben Typen sind
    ausdruckMussTypSein(ausdruck.rechts, linkerTyp)
    return linkerTyp.definierteOperatoren.getValue(operator)
  }

  override fun evaluiereMinus(minus: AST.Ausdruck.Minus): Typ {
    return ausdruckMussTypSein(minus.ausdruck, Typ.Zahl)
  }

  override fun evaluiereKonvertierung(konvertierung: AST.Ausdruck.Konvertierung): Typ{
    val ausdruck = evaluiereAusdruck(konvertierung.ausdruck)
    val konvertierungsTyp = typisierer.bestimmeTypen(konvertierung.typ, true)!!
    if (!ausdruck.kannNachTypKonvertiertWerden(konvertierungsTyp)){
      throw GermanSkriptFehler.KonvertierungsFehler(konvertierung.typ.name.bezeichner.toUntyped(),
          ausdruck, konvertierungsTyp)
    }
    return konvertierungsTyp
  }

  override fun evaluiereSelbstReferenz() = zuÜberprüfendeKlasse!!.typ.typ!!

  override fun evaluiereClosure(closure: AST.Ausdruck.Closure): Typ.Schnittstelle {
    val schnittstelle = typisierer.bestimmeTypen(closure.schnittstelle, true)
    if (schnittstelle !is Typ.Schnittstelle) {
      throw GermanSkriptFehler.SchnittstelleErwartet(closure.schnittstelle.name.bezeichner.toUntyped())
    }
    if (schnittstelle.definition.methodenSignaturen.size != 1) {
      throw GermanSkriptFehler.UngültigeClosureSchnittstelle(closure.schnittstelle.name.bezeichner.toUntyped(), schnittstelle.definition)
    }
    val prevRückgabeTyp = rückgabeTyp
    val prevRückgabeErreicht = rückgabeErreicht
    val signatur = schnittstelle.definition.methodenSignaturen[0]
    umgebung.pushBereich()
    for (parameter in signatur.parameter) {
      umgebung.schreibeVariable(parameter.name, parameter.typKnoten.typ!!, false)
    }
    durchlaufeAufruf(
        closure.schnittstelle.name.bezeichner.toUntyped(),
        closure.körper, umgebung,
        false,
        signatur.rückgabeTyp?.typ
    )
    umgebung.popBereich()
    rückgabeTyp = prevRückgabeTyp
    rückgabeErreicht = prevRückgabeErreicht
    return schnittstelle
  }
}

fun main() {
  val typPrüfer = TypPrüfer(File("./beispiele/HalloWelt.gm"))
  typPrüfer.prüfe()
  typPrüfer.logger.print()
}