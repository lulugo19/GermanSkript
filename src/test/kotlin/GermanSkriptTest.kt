import germanskript.GermanSkriptFehler
import germanskript.Interpretierer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.*

class GermanSkriptTest {

  private fun führeGermanSkriptCodeAus(germanSkriptSource: String) {
    // erstelle temporäre Datei mit dem Source-Code
    val tempFile = createTempFile("germanskript_test_temp", ".gm")
    tempFile.writeText(germanSkriptSource)

    val interpretierer = Interpretierer(tempFile)
    interpretierer.interpretiere()


    // lösche temporäre Datei
    tempFile.delete()
  }

  private fun testeGermanSkriptCode(quellCode: String, erwarteteAusgabe: String) {
    // leite den Standardoutput in einen Byte-Array-Stream um
    val myOut = ByteArrayOutputStream()
    System.setOut(PrintStream(myOut))

    führeGermanSkriptCodeAus(quellCode)

    val actual = myOut.toString()
    assertThat(actual).isEqualToNormalizingNewlines(erwarteteAusgabe)

    // set out back to stdout
    System.setOut(PrintStream(FileOutputStream(FileDescriptor.out)))
  }

  @Test
  @DisplayName("Hallo Welt")
  fun halloWelt() {
    val source = """
      schreibe die Zeile "Hallo Welt"
    """.trimIndent()
    val expectedOutput = "Hallo Welt\n"

    testeGermanSkriptCode(source, expectedOutput)
  }

  @Test
  @DisplayName("Bedingungen")
  fun bedingungen() {
    val source = """
      Verb teste die Zahl:
          wenn die Zahl gleich 3 ist:
            schreibe die Zeile "Alle guten Dinge sind drei!".
          sonst wenn die Zahl gleich 42 ist:
            schreibe die Zeile "Die Antwort auf alles.".
          sonst: schreibe die Zahl.
      .
      teste die Zahl 11
      teste die Zahl 3
      teste die Zahl 42
      teste die Zahl 12
    """.trimIndent()

    val expectedOutput = """
      11
      Alle guten Dinge sind drei!
      Die Antwort auf alles.
      12
      
    """.trimIndent()

    testeGermanSkriptCode(source, expectedOutput)
  }

  @Test
  @DisplayName("Fakultät")
  fun fakultät() {
    val source = """
      Verb(Zahl) fakultät von der Zahl:
        wenn die Zahl gleich 0 ist: gebe 1 zurück.
        sonst: gebe die Zahl * (fakultät von der Zahl - 1) zurück.
      .
      
      schreibe die Zahl (fakultät von der Zahl 3)
      schreibe die Zahl (fakultät von der Zahl 5)
      schreibe die Zahl (fakultät von der Zahl 6)
    """.trimIndent()

    val expectedOutput = """
      6
      120
      720
      
    """.trimIndent()

    testeGermanSkriptCode(source, expectedOutput)
  }

  @Test
  @DisplayName("Solange-Schleife")
  fun solangeSchleife() {
    val source = """
      eine Zahl ist -1
      solange wahr:
        eine Zahl ist die Zahl plus 1
        wenn die Zahl > 10 ist: abbrechen.
        wenn die Zahl mod 2 gleich 1 ist: fortfahren.
        schreibe die Zahl
      .
    """.trimIndent()

    val expectedOutput = """
      0
      2
      4
      6
      8
      10
      
    """.trimIndent()

    testeGermanSkriptCode(source, expectedOutput)
  }

  @Test
  @DisplayName("Listenindex")
  fun listen() {
    val source = """
      die Zahlen sind einige Zahlen [1, 2, 3]
      schreibe die Zahl[0]
      ein Index ist 1
      solange der Index kleiner als die AnZahl der Zahlen ist:
        schreibe die Zahl[Index]
        ein Index ist der Index plus 1
      .

    """.trimIndent()

    val expectedOutput = """
      1
      2
      3
      
    """.trimIndent()

    testeGermanSkriptCode(source, expectedOutput)
  }

  @Test
  @DisplayName("Für-Jede-Schleifen")
  fun fürJedeSchleifen() {
    val source = """
      die Zahlen sind einige Zahlen [1, 2, 3]
      für jede Zahl:
        schreibe die Zahl
      .
      für jedes X in den Zahlen:
        schreibe die Zahl X
      .
      für jede Zahl in einigen Zahlen [11, 12, 13]:
        schreibe die Zahl
      .
    """.trimIndent()

    val expectedOutput = """
      1
      2
      3
      1
      2
      3
      11
      12
      13
      
    """.trimIndent()

    testeGermanSkriptCode(source, expectedOutput)
  }

  @Test
  @DisplayName("Variablen-Überdeckung")
  fun variablenÜberdeckung() {
    val source = """
      eine Zeichenfolge ist "Erste Variable"
      :
        schreibe die Zeile Zeichenfolge
        eine Zeichenfolge ist "Erste veränderte Variable"
        schreibe die Zeile Zeichenfolge
        eine neue Zeichenfolge ist "Zweite Variable"
        schreibe die Zeile Zeichenfolge
      .
      schreibe die Zeile Zeichenfolge
    """.trimIndent()

    val expectedOutput = """
      Erste Variable
      Erste veränderte Variable
      Zweite Variable
      Erste veränderte Variable
      
    """.trimIndent()

    testeGermanSkriptCode(source, expectedOutput)
  }

  @Test
  @DisplayName("Unveränderliche Variable können nicht neu zugewiesen werden")
  fun unverÄnderlicheVariablen() {
    val source = """
      die Zahl ist 6
      :
        die Zahl ist 5
      .
      die Zahl ist 10 // Fehler hier
    """.trimIndent()

    assertThatExceptionOfType(GermanSkriptFehler.Variablenfehler::class.java).isThrownBy {
      führeGermanSkriptCodeAus(source)
    }
  }

  @Test
  @DisplayName("Klassendefinition und Objektinstanziierung")
  fun klasseDefinitionUndObjekte() {
    val source = """
      Deklination Maskulinum Singular(Name, Namens, Namen, Namen) Plural(Namen)
      Deklination Neutrum Singular(Alter, Alters, Alter, Alter) Plural(Alter)
      Deklination Femininum Singular(Person) Plural(Personen)

      Nomen Person mit
          der Zeichenfolge VorName,
          der Zeichenfolge NachName,
          einer Zahl Alter:

          dieser Name ist "#{mein VorName} #{mein NachName}"
          schreibe die Zeile "#{mein Name} (#{mein Alter} Jahre alt) wurde erstellt!"
      .
      
      die Person ist eine Person mit dem VorNamen "Max", dem NachNamen "Mustermann", dem Alter 23
      die PersonJANE ist eine Person mit dem VorNamen "Jane", dem NachNamen "Doe", dem Alter 41
    """.trimIndent()

    val expectedOutput = """
      Max Mustermann (23 Jahre alt) wurde erstellt!
      Jane Doe (41 Jahre alt) wurde erstellt!
      
    """.trimIndent()

    testeGermanSkriptCode(source, expectedOutput)
  }

  @Test
  @DisplayName("Eigene Konvertierungsdefinition")
  fun konvertierungsDefinition() {
    val quellCode = """
      Deklination Maskulinum Singular(Name, Namens, Namen, Namen) Plural(Namen)
      Deklination Neutrum Singular(Alter, Alters, Alter, Alter) Plural(Alter)
      Deklination Femininum Singular(Person) Plural(Personen)

      Nomen Person mit
          der Zeichenfolge VorName,
          der Zeichenfolge NachName,
          einer Zahl Alter:

          dieser Name ist "#{mein VorName} #{mein NachName}"
          // Man könnte auch schreiben: Ich als Zeichenfolge + " wurde erstellt!"
          schreibe die Zeile "#{Ich} wurde erstellt!"
      .
      
      implementiere die Person:
        als Zeichenfolge:
          gebe "#{mein Name} (#{mein Alter} Jahre alt)" zurück
        .
      .

      die Person ist eine Person mit dem VorNamen "Max", dem NachNamen "Mustermann", dem Alter 29
    """.trimIndent()

    val erwarteteAusgabe = """
      Max Mustermann (29 Jahre alt) wurde erstellt!
      
    """.trimIndent()

    testeGermanSkriptCode(quellCode, erwarteteAusgabe)
  }

  @Test
  @DisplayName("veränderliche Eigenschaften eines Objekt")
  fun veränderlicheEigenschaftenEinesObjekts() {
    val source = """
      Deklination Maskulinum Singular(Zähler, Zählers, Zähler, Zähler) Plural(Zähler)
      
      Nomen Zähler:
        jene Zahl ist 0
        schreibe die Zahl (meine Zahl)
      .
      
      implementiere den Zähler:
        Verb erhöhe mich um die Zahl:
          meine Zahl ist meine Zahl + die Zahl
          schreibe die Zahl (meine Zahl)
        .
      
        Verb setze mich zurück:
          meine Zahl ist 0
          schreibe die Zahl (meine Zahl)
        .
      .

      der Zähler ist ein Zähler
      
      erhöhe den Zähler um die Zahl 5
      erhöhe den Zähler um die Zahl 3
      setze den Zähler zurück
      Zähler:
        erhöhe dich um die Zahl -5
        erhöhe dich um die Zahl 10
        setze dich zurück
      !
    """.trimIndent()

    val expectedOutput = """
      0
      5
      8
      0
      -5
      5
      0
      
    """.trimIndent()

    testeGermanSkriptCode(source, expectedOutput)
  }

  @Test
  @DisplayName("Modul (Funktionen)")
  fun module() {
    val source = """
      Verb hallo:
        schreibe die Zeile "Hallo Welt"
      .
      
      Modul Foo:
        Verb hallo:
          schreibe die Zeile "Hallo Foo"
        .
        
        Modul Bar:
          Verb hallo:
            schreibe die Zeile "Hallo Bar"
          .
        .
      .
      
      Modul Foo::Bar:
        Verb test:
          schreibe die Zeile "Test"
        .
      .
      
      hallo
      Foo::hallo
      Foo::Bar::hallo
      Foo::Bar::test
    """.trimIndent()

    val expectedOutput = """
      Hallo Welt
      Hallo Foo
      Hallo Bar
      Test
      
    """.trimIndent()

    testeGermanSkriptCode(source, expectedOutput)
  }

  @Test
  @DisplayName("verwende Module")
  fun verwendeModule() {
    val source = """
      Modul A:
        Modul B:
          Verb test: schreibe die Zeile "Test".
        .
      .
      verwende A::B
      test
    """.trimIndent()

    val expectedOutput = """
      Test
      
    """.trimIndent()

    testeGermanSkriptCode(source, expectedOutput)
  }

  @Test
  @DisplayName("verwende Module (komplexer)")
  fun verwendeModuleKomplexer() {
    val source = """
      Modul A:
        Deklination Neutrum Singular(Bar) Plural(Bars)
        Deklination Neutrum Singular(Foo) Plural(Foos)
        
        verwende C
        Modul B:
          Nomen Foo mit dem Bar:.
          
          implementiere das Bar:
            Verb test:
              schreibe die Zeile "Bar"
            .
          .
        .
        Modul C:
          Nomen Bar:.
        .
      .
      
      verwende A
      
      das Foo ist ein B::Foo mit einem C::Bar
      das Bar ist das Bar des Foo
      Bar: test!
    """.trimIndent()

    val expectedOutput = """
      Bar
      
    """.trimIndent()

    testeGermanSkriptCode(source, expectedOutput)
  }

  @Test
  @DisplayName("Zugriff auf einzelne Zeichen einer Zeichenfolge")
  fun zeichenfolgeUmkehren() {
    val source = """
      Verb(Zeichenfolge) kehre die Zeichenfolge um:
        ein Index ist die Länge der Zeichenfolge minus 1
        ein ERGEBNIS ist ""
        solange der Index größer gleich 0 ist:
          ein ERGEBNIS ist das ERGEBNIS + die Zeichenfolge[Index]
          ein Index ist der Index - 1
        .
        gebe das ERGEBNIS zurück
      .
      
      schreibe die Zeichenfolge (kehre die Zeichenfolge "Hallo Welt" um)
    """.trimIndent()

    testeGermanSkriptCode(source, "tleW ollaH")
  }

  @Test
  @DisplayName("Schnittstelle (Adjektiv)")
  fun schnittstelle() {
    val source = """
      Deklination Femininum Singular(Farbe) Plural(Farben)
      Deklination Neutrum Singular(Dreieck, Dreiecks, Dreieck, Dreieck) Plural(Dreiecke, Dreiecke, Dreiecken, Dreiecke)
      
      Adjektiv zeichenbar:
          Verb zeichne mich mit der Zeichenfolge Farbe
          Verb skaliere mich um die Zahl
      .

      Verb zeichne das Zeichenbare mit der Zeichenfolge Farbe: 
          Zeichenbare: zeichne dich mit der Farbe!
      .

      Nomen Dreieck:.
      
      implementiere das zeichenbare Dreieck:
        Verb zeichne mich mit der Zeichenfolge Farbe:
           schreibe die Zeile "zeichne das Dreieck mit der Farbe #{die Farbe}"
        .

        Verb skaliere mich um die Zahl:
           schreibe die Zeile "skaliere das Dreieck um #{die Zahl}"
        .
      .

      das Dreieck ist ein Dreieck

      zeichne das zeichenbare Dreieck mit der Farbe "rot"
      skaliere das Dreieck um die Zahl 2
    """.trimIndent()

    val expectedOutput = """
      zeichne das Dreieck mit der Farbe rot
      skaliere das Dreieck um 2
      
    """.trimIndent()

    testeGermanSkriptCode(source, expectedOutput)
  }

  @Test
  @DisplayName("Schnittstelle 2")
  fun schnittstelle2() {
    val source = """
      Deklination Neutrum Singular(Fenster, Fensters, Fenster, Fenster) Plural(Fenster)
      
      Adjektiv klickbar:
        Verb klick mich
      .
      
      Verb registriere das KlickbareX:
        wenn wahr:
          KlickbaresX: klick dich!
        .
      .
      
      Nomen Fenster:
        jene AnZahl ist 0
      .
      
      implementiere das klickbare Fenster:
          Verb klick mich:
          meine AnZahl ist meine AnZahl plus 1
          schreibe die Zeile "Das Fenster wurde zum #{meine AnZahl}. angeklickt!"
        .
      .

      das Fenster ist ein Fenster
      registriere das klickbare Fenster
      
      klick das Fenster
    """.trimIndent()

    val expectedOutput = """
      Das Fenster wurde zum 1. angeklickt!
      Das Fenster wurde zum 2. angeklickt!
      
    """.trimIndent()

    testeGermanSkriptCode(source, expectedOutput)
  }

  @Test
  @DisplayName("Vererbung")
  fun vererbung() {
    val quellCode = """
      Deklination Femininum Singular(Person) Plural(Personen)
      Deklination Maskulinum Singular(Student, Studenten, Studenten, Studenten) Plural(Studenten)
      Deklination Maskulinum Singular(Name, Namens, Namen, Namen) Plural(Namen)
      Deklination Neutrum Singular(Alter, Alters, Alter, Alter) Plural(Alter)
      Deklination Maskulinum Singular(Studiengang, Studiengangs, Studiengang, Studiengang) Plural(Studiengänge)
      
      Nomen Person mit
        der Zeichenfolge VorName,
        der Zeichenfolge NachName,
        einer Zahl Alter:
        
        dieser Name ist "#{mein VorName} #{mein NachName}"
        schreibe die Zeile "#{mein Name} (#{mein Alter} Jahre alt) wurde erstellt!"
      .
      
      Nomen Student als Person mit der Zeichenfolge Studiengang:
        schreibe die Zeile "#{mein VorName} #{mein NachName} ist ein #{mein Studiengang}-Student!"
      .
      
      implementiere die Person:
        Verb stell mich vor:
          schreibe die Zeile "Hallo, mein Name ist #{mein Name} und ich bin #{mein Alter} Jahre alt!"
        .
      .
      
      implementiere den Studenten:
        Verb stell mich vor:
          Super: stell mich vor!
          schreibe die Zeile "Ich bin #{mein Studiengang}-Student."
        .
      .

      Verb stell die Person vor:
        Person: stell dich vor!
      .
      
      der Student ist ein Student mit 
        dem VorNamen "Lukas",
        dem NachNamen "Gobelet",
        dem Alter 22,
        dem Studiengang "Informatik"
        
      stell die Person Student vor
    """.trimIndent()

    val erwarteteAusgabe = """
      Lukas Gobelet (22 Jahre alt) wurde erstellt!
      Lukas Gobelet ist ein Informatik-Student!
      Hallo, mein Name ist Lukas Gobelet und ich bin 22 Jahre alt!
      Ich bin Informatik-Student.
      
    """.trimIndent()

    testeGermanSkriptCode(quellCode, erwarteteAusgabe)
  }

  @Test
  @DisplayName("Closure")
  fun closures() {
    val quellCode = """
      Adjektiv klickbar:
          Verb klick mich
      .

      Verb registriere das Klickbare:
          Klickbares: klick dich!
      .

      eine Zahl ist 0
      registriere etwas Klickbares:
          die Zahl ist die Zahl + 1
          schreibe die Zeile "Ich wurde zum #{die Zahl}. angeklickt."
      .
    """.trimIndent()

    val erwarteteAusgabe = """
      Ich wurde zum 1. angeklickt.
      
    """.trimIndent()

    testeGermanSkriptCode(quellCode, erwarteteAusgabe)
  }

  @Test
  @DisplayName("Closure zurückgeben")
  fun closureZurückgeben() {
    val quellCode = """
      Adjektiv zählbar:
        Verb(Zahl) zähle weiter
      .
      
      Verb(Zählbares) zähler von der ZahlA zur ZahlB:
        eine Zahl ist die ZahlA minus 1
        
        das Zählbare ist etwas Zählbares:
          wenn die Zahl größer gleich die ZahlB ist:
            eine Zahl ist die ZahlA minus 1
          .
          eine Zahl ist die Zahl plus 1
          gebe die Zahl zurück
        .
        
        gebe das Zählbare zurück
      .
      
      das Zählbare ist zähler von der Zahl 1 zur Zahl 3
      
      Zählbares:
        schreibe die Zeile (zähle weiter) als Zeichenfolge
        schreibe die Zeile (zähle weiter) als Zeichenfolge
        schreibe die Zeile (zähle weiter) als Zeichenfolge
        schreibe die Zeile (zähle weiter) als Zeichenfolge
        schreibe die Zeile (zähle weiter) als Zeichenfolge
      !
    """.trimIndent()

    val erwarteteAusgabe = """
      1
      2
      3
      1
      2
      
    """.trimIndent()

    testeGermanSkriptCode(quellCode, erwarteteAusgabe)
  }

  @Test
  @DisplayName("Objektinitialisierung als Argument")
  fun objektInitialisierungAlsArgument() {
    val quellCode = """
      Deklination Femininum Singular(Person) Plural(Personen)
      Deklination Maskulinum Singular(Name, Namens, Namen, Namen) Plural(Namen)
      
      Nomen Person mit der Zeichenfolge Name:.
      
      Verb begrüße die Person:
        schreibe die Zeichenfolge "Hallo #{der Name der Person}!"
      .
      
      begrüße eine Person mit dem Namen "Max"
    """.trimIndent()

    val erwarteteAusgabe = "Hallo Max!"

    testeGermanSkriptCode(quellCode, erwarteteAusgabe)
  }

  @Test
  @DisplayName("Alias Singular")
  fun aliasSingular() {
    val quellCode = """
      Deklination Femininum Singular(Person) Plural(Personen)
      Deklination Maskulinum Singular(Name, Namens, Namen, Namen) Plural(Namen)
      Deklination Neutrum Singular(Alter) Plural(Alter)
      
      Alias Alter ist Zahl
      Alias Name ist Zeichenfolge
      
      Nomen Person mit dem Namen, einem Alter:
        schreibe die Zeile "#{mein Name} (#{mein Alter} Jahre alt) wurde erstellt!"
      .
      
      die Person ist eine Person mit dem Namen "Lukas", dem Alter 22
      das NeueAlter ist das Alter der Person plus 1
      schreibe die Zeile "#{der Name der Person} ist jetzt #{das NeueAlter} Jahre alt!"
    """.trimIndent()

    val erwarteteAusgabe = """
      Lukas (22 Jahre alt) wurde erstellt!
      Lukas ist jetzt 23 Jahre alt!
      
    """.trimIndent()

    testeGermanSkriptCode(quellCode, erwarteteAusgabe)
  }

  @Test
  @DisplayName("Alias Plural")
  fun aliasPlural() {
    val quellCode = """
      Deklination Femininum Singular(Person) Plural(Personen)
      Deklination Maskulinum Singular(Mensch, Menschen, Menschen, Menschen) Plural(Menschen)
      Deklination Maskulinum Singular(Name, Namens, Namen, Namen) Plural(Namen)
      
      Nomen Person mit der Zeichenfolge Name:.
      
      Alias Mensch ist Person
      
      die Menschen sind einige Menschen [(ein Mensch mit dem Namen "Max"), (eine Person mit dem Namen "Lukas")]
      
      für jeden Menschen:
        schreibe die Zeile (der Name des Menschen)
      .
    """.trimIndent()

    val erwarteteAusgabe = """
      Max
      Lukas
      
    """.trimIndent()

    testeGermanSkriptCode(quellCode, erwarteteAusgabe)
  }

  @Test
  @DisplayName("Alias Fehler")
  fun aliasFehler() {
    val quellCode = """
      Deklination Neutrum Singular(Alter) Plural(Alter)
      Deklination Femininum Singular(Menge) Plural(Mengen)
      
      Alias Menge ist Zahl
      Alias Alter ist Menge
    """.trimIndent()

    assertThatExceptionOfType(GermanSkriptFehler.AliasFehler::class.java).isThrownBy {
        führeGermanSkriptCodeAus(quellCode)
    }
  }

  @Test
  @DisplayName("berechnete Eigenschaften")
  fun berechneteEigenschaften() {
    val quellCode = """
      Deklination Femininum Singular(Person) Plural(Personen)
      Deklination Maskulinum Singular(Name, Namens, Namen, Namen) Plural(Namen)
      
      Nomen Person mit der Zeichenfolge VorName, der Zeichenfolge NachName:.
      
      implementiere die Person:
        Eigenschaft(Zeichenfolge) Name:
          gebe meinen VorNamen + " " + meinen NachNamen zurück
        .
      .

      die Person ist eine Person mit dem VorNamen "Max", dem NachNamen "Mustermann"
      schreibe die Zeichenfolge (der Name der Person)
    """.trimIndent()

    testeGermanSkriptCode(quellCode, "Max Mustermann")
  }

  @Test
  @DisplayName("Konstante")
  fun konstante() {
    val quellCode = """
      Modul MatheX:
        Konstante PI ist 3,14159265
      .
      
      die Zahl ist 2 * MatheX::PI
      schreibe die Zahl
    """.trimIndent()

    val erwarteteAusgabe = """
      6,2831853
      
    """.trimIndent()

    testeGermanSkriptCode(quellCode, erwarteteAusgabe)
  }

  @Test
  @DisplayName("Verwende Konstante")
  fun verwendeKonstante() {
    val quellCode = """
      Modul MatheX:
        Konstante PI ist 3,14159265
      .
      
      verwende MatheX::PI
      
      schreibe die Zahl PI
    """.trimIndent()

    val erwarteteAusgabe = """
      3,14159265
      
    """.trimIndent()

    testeGermanSkriptCode(quellCode, erwarteteAusgabe)
  }

  @Test
  @DisplayName("Variable überschreibt Konstante")
  fun variableÜberschreibtKonstante() {
    val quellCode = """
      Modul MatheX:
        Konstante PI ist 3,14159265
      .
      
      verwende MatheX
      
      das PI ist 3
      schreibe die Zahl MatheX::PI
      schreibe die Zahl PI
    """.trimIndent()

    val erwarteteAusgabe = """
      3,14159265
      3
      
    """.trimIndent()

    testeGermanSkriptCode(quellCode, erwarteteAusgabe)
  }

  @Test
  @DisplayName("Konstante ist Literal")
  fun konstanteIstLiteral() {
    val quellCode = """
      // eine Konstante kann keine Liste sein
      Konstante PI ist einige Zahlen[1, 2, 3, 4]
    """.trimIndent()

    assertThatExceptionOfType(GermanSkriptFehler.KonstantenFehler::class.java).isThrownBy {
      führeGermanSkriptCodeAus(quellCode)
    }
  }

  @Test
  @DisplayName("Für-Jede-Schleife Reichweiten 1")
  fun reichweiten1() {
    val quellCode = """
      für jede Zahl von 0 bis 3:
        schreibe die Zahl
      .
      schreibe die Zeile ""
      
      für jede Zahl von 2 bis -2:
        schreibe die Zahl
      .
      schreibe die Zeile ""
      
      für jede Zahl von 0,5 bis 3:
        schreibe die Zahl
      .
    """.trimIndent()

    val erwarteteAusgabe = """
      0
      1
      2
      
      2
      1
      0
      -1
      
      0,5
      1,5
      2,5
      
    """.trimIndent()

    testeGermanSkriptCode(quellCode, erwarteteAusgabe)
  }

  @Test
  @DisplayName("Für-Jede-Schleife Reichweiten 2")
  fun reichweiten2() {
    val quellCode = """
      einige Zahlen sind einige Zahlen [2, 3, 5, 7, 11]
      für jeden Index von 0 bis zur AnZahl der Zahlen:
        schreibe die Zahl[Index]
      .
    """.trimIndent()

    val erwarteteAusgabe = """
      2
      3
      5
      7
      11
      
    """.trimIndent()

    testeGermanSkriptCode(quellCode, erwarteteAusgabe)
  }

  @Test
  @DisplayName("Versuche-Fange")
  fun versucheFange() {
    val quellCode = """
      versuche:
        die Zahl ist "Hallo" als Zahl
      .
      fange den KonvertierungsFehler:
        schreibe die Zeile (die FehlerMeldung des KonvertierungsFehlers)
      .
    """.trimIndent()

    val erwarteteAusgabe = "Die Zeichenfolge 'Hallo' kann nicht in eine Zahl konvertiert werden.\n"

    testeGermanSkriptCode(quellCode, erwarteteAusgabe)
  }

  @Test
  @DisplayName("Werfe Fehler")
  fun werfeFehler() {
    val quellCode = """
      Verb(Zahl) fakultät von der Zahl:
        wenn die Zahl kleiner 0 ist: 
          werfe einen Fehler mit der FehlerMeldung "Die Fakultät von einer negativen Zahl ist undefiniert."
        .
        wenn die Zahl gleich 0 ist: gebe 1 zurück.
        sonst: gebe die Zahl * (fakultät von der Zahl - 1) zurück.
      .
      
      versuche:
        schreibe die Zahl (fakultät von der Zahl 3)
        schreibe die Zahl (fakultät von der Zahl -1)
        schreibe die Zahl (fakultät von der Zahl 5)
      .
      fange die Zahl:
        schreibe die Zeile "Die Zahl sollte nicht gefangen werden"
      .
      fange den Fehler:
        schreibe die Zeile (die FehlerMeldung des Fehlers)
      .
    """.trimIndent()

    val erwarteteAusgabe = """
      6
      Die Fakultät von einer negativen Zahl ist undefiniert.
      
    """.trimIndent()

    testeGermanSkriptCode(quellCode, erwarteteAusgabe)
  }

  @Test
  @DisplayName("Liste (füge hinzu, enthält)")
  fun liste() {
    val quellCode = """
      die Zahlen sind einige Zahlen[1, 2, 3, 4]
      Zahlen:
        füge die Zahl 5 hinzu
        wenn enthält die Zahl 5:
          schreibe die Zeile "Die Zahl 5 wurde hinzugefügt!"
        .
      !
    """.trimIndent()

    val erwarteteAusgabe = "Die Zahl 5 wurde hinzugefügt!\n"

    testeGermanSkriptCode(quellCode, erwarteteAusgabe)
  }

  @Test
  @DisplayName("Generics")
  fun generics() {
    val quellCode = """
      Deklination Maskulinum Singular(Test, Tests, Test, Test) Plural(Tests)
      Nomen<Typ> Test mit dem Typ:.
      
      der Test ist ein Test<Zahl> mit der Zahl 5
      schreibe die Zahl des Tests
    """.trimIndent()

    val erwarteteAusgabe = "5\n"

    testeGermanSkriptCode(quellCode, erwarteteAusgabe)
  }

  @Test
  @DisplayName("Liste sortieren")
  fun listeSortieren() {
    val quellCode = """
      die Zahlen sind einige Zahlen[1, 2, 3]
      Zahlen:
        sortiere dich mit etwas Vergleichbarem: gebe die ZahlB - die ZahlA zurück.
      !
      schreibe die Zeichenfolge (die Zahlen als Zeichenfolge)
    """.trimIndent()

    val erwarteteAusgabe = "[3, 2, 1]"

    testeGermanSkriptCode(quellCode, erwarteteAusgabe)
  }


}