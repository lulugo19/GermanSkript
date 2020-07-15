Deklination Maskulinum Singular(Bereich, Bereichs, Bereich, Bereich) Plural(Bereiche, Bereiche, Bereiche, Bereiche)

Verb schreibe die Zeichenfolge Bereich:
    schreibe die Zeile ""
    schreibe die Zeile "----------------- " + den Bereich + " -----------------"
.

//Typ-Umwandlung
schreibe den Bereich "Typ-Umwandlung"
die Zeichenfolge ist "1,7239"
eine Zahl ist die Zeichenfolge als Zahl
schreibe die Zahl

// Binärer Ausdruck
// Es ist jetzt möglich dass bei einem binären Ausdruck als Argument der erste Operand als Parametername erkannt wird
schreibe den Bereich "Binärer Ausdruck"
eine Zahl ist 5
schreibe die Zahl (die Zahl * 5) // so musste man es vorher schreiben
schreibe die Zahl * 5            // jetzt kann man es so schreiben

Verb(Zahl) fakultät von der Zahl:
    wenn die Zahl gleich 0: gebe 1 zurück.
    sonst: gebe die Zahl * (fakultät von der Zahl - 1) zurück.
    // vorher musste man es so schreiben
    // sonst: gebe die Zahl * (fakultät von der Zahl (die Zahl -1)) zurück
.
schreibe die Zeichenfolge "Die Fakulät von der Zahl 6 ist: "
schreibe die Zahl (fakultät von der Zahl 6)

Deklination Femininum Singular(Person) Plural(Personen)
Deklination Maskulinum Singular(Name, Namens, Namen, Namen) Plural(Namen)
Deklination Neutrum Singular(Alter, Alters, Alter, Alter) Plural(Alter, Alter, Alter, Alter)
Deklination Femininum Singular(Begrüßung) Plural(Begrüßungen)
Deklination Maskulinum Singular(Abschied, Abschiedes, Abschied, Abschied) Plural(Abschiede)

// Klassendefinition
schreibe den Bereich "Klassendefinition und Objektinstanziierung"
Nomen Person mit
    der Zeichenfolge Name,
    der Zeichenfolge NachName,
    der Zahl Alter:

    schreibe die Zeile "die Person " + "'" + meinen Namen + " " + meinen NachNamen + "' wurde erstellt!"
.
die Person ist eine Person mit dem Namen "Lukas", dem NachNamen "Gobelet", dem Alter 22

// Funktion mit einem Objekt aufrufen
schreibe den Bereich "Funktion mit einem Objekt als Argument aufrufen"

Verb begrüße die Person mit der Zeichenfolge Begrüßung, der Zeichenfolge Abschied:
    schreibe die Zeile "Funktionsaufruf: " + die Begrüßung + " " + den Namen der Person + ". " + den Abschied + "."
.

begrüße die Person mit der Begrüßung "Hey", dem Abschied "Tschüss"

// Methodendefinition
schreibe den Bereich "Methodendefinition und Methodenblock"

Verb für Person stelle mich vor:
    schreibe die Zeile "Hallo, ich bin " + meinen Namen + "."
.

// Methodenblock
Person:
    stelle dich vor
    schreibe die Zeile "Hey wie geht's denn " + deinen Namen + "?"
!

// Selbstaufruf einer Methode
schreibe den Bereich "Selbstaufruf einer Methode"
Verb für Person begrüße mich mit der Zeichenfolge Begrüßung, der Zeichenfolge Abschied:
    schreibe die Zeile "Methodenaufruf: " + die Begrüßung + " " + meinen Namen + ". " + den Abschied + "."
.

Deklination Femininum Singular(Wiederholung) Plural(Wiederholungen)
Verb für Person begrüße mich mit der Zeichenfolge Begrüßung, der Zeichenfolge Abschied, der Zahl Wiederholung:
    wenn die Wiederholung größer 0:
        begrüße mich mit der Begrüßung, dem Abschied
        begrüße mich mit der Begrüßung, dem Abschied, der Wiederholung minus 1
    .
.

Person: begrüße mich mit der Begrüßung "Hey", dem Abschied "Bye", der Wiederholung 3!

// Funktionsaufrufweise einer Methode
schreibe den Bereich "Funktionsaufrufweise einer Methode"

// Ich kann die Methode 'stelle mich vor' wie eine Funktion aufrufen
// indem ich das Objekt mit einem Parameter mit dem Klassennamen ersetze
stelle die Person vor

// Wenn es eine Funktion gibt, die genauso heißt wie die Methode, dann wird diese bevorzugt
// und ich brauche für den Methodenaufruf einen Methodenblock
Person:
    begrüße die Person mit der Begrüßung "Hallo", dem Abschied "Mach's gut" // Funktionsaufruf
    begrüße mich mit der Begrüßung "Hallo", dem Abschied "Mach's gut"
!

// Symbole
Deklination Femininum Singular(Summe) Plural(Summen)
schreibe den Bereich "Symbol-Test"
die Zahlen sind einige Zahlen[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
eine Summe ist 0
für jede Zahl X:
    eine Summe ist die Summe plus das X
.
schreibe die Zeile "Die Summe ist: " + die Summe als Zeichenfolge

// Variablendeklarationen
schreibe den Bereich "Variablendeklarationen"
Deklination Femininum Singular(Variable) Plural(Variablen)
eine Variable ist "Erste Variable"
wenn wahr:
    schreibe die Zeile Variable // Erste Variable
    eine Variable ist "Zweite Variable"
    schreibe die Zeile Variable // Zweite Variable
    eine neue Variable ist "Dritte Variable"
    schreibe die Zeile Variable // Dritte Variable
.
schreibe die Zeile Variable // Zweite Variable

// zusammengesetze Wörter und Symbole
schreibe den Bereich "zusammen gesetzte Wörter und Symbole"
die ErstePrimZahl ist 2
schreibe die ErstePrimZahl
die ZeileX ist "X"
schreibe die ZeileX
die ZeileXXX ist "XXX"
schreibe die ZeileXXX
die SuperlangeZeile ist "Die suuuuuppppeeeerrrrlaaanggggeeeeeeeeeeeeeee Zeile!"
schreibe die SuperlangeZeile

Verb teste die TestZahl:
    ein GERADE ist "gerade"
    wenn die TestZahl modulo 2 gleich 1:
        ein GERADE ist "ungerade"
    .
    schreibe die Zeile "die Zahl " + die TestZahl als Zeichenfolge + " ist " + das GERADE + "!"
.
teste die Zahl 7
teste die Zahl 18

// veränderliche Eigenschaften eines Objekts können jetzt innerhalb des Objekt neu zugewiesen werden
schreibe den Bereich "veränderliche Eigenschaften eines Objekts"
Deklination Maskulinum Singular(Zähler, Zählers, Zähler, Zähler) Plural(Zähler)
Nomen Zähler mit einer Zahl, dem Boolean:
    schreibe die Zeile "Zähler: " + meine Zahl als Zeichenfolge
    // wirft einen Fehler da diese Eigenschaft unveränderlich ist
    // mein Boolean ist wahr
.
Verb für Zähler erhöhe mich um die Zahl:
    meine Zahl ist meine Zahl + die Zahl
    schreibe die Zeile "Zähler: " + meine Zahl als Zeichenfolge
.
Verb für Zähler setze mich zurück:
    meine Zahl ist 0
    schreibe die Zeile "Zähler auf 0 zurückgesetzt!"
.

der Zähler ist ein Zähler mit der Zahl 0, dem Boolean falsch
erhöhe den Zähler um die Zahl 5
erhöhe den Zähler um die Zahl 3
Zähler:
    setze mich zurück
    erhöhe mich um die Zahl 2
    erhöhe mich um die Zahl 100
!

// in Vergleich kann 'ist' kommen
schreibe den Bereich "Fibonacci"
Verb(Zahl) fibonacci von der Zahl:
    wenn die Zahl kleiner gleich 1 ist: gebe die Zahl zurück.
    sonst: gebe ((fibonacci von der Zahl - 1) + (fibonacci von der Zahl - 2)) zurück.
.
schreibe die Zeile "Fibonacci(9) = " + (fibonacci von der Zahl 9) als Zeichenfolge

// eigene Konvertierung
schreibe den Bereich "Konvertierungsdefinition für Person"
als Zeichenfolge für Person:
    gebe meinen Namen + " " + meinen NachNamen + " (" + mein Alter als Zeichenfolge + " Jahre alt)" zurück
.
schreibe die Zeile Person als Zeichenfolge

/*
// Stack Overflow Handling
schreibe den Bereich "Stack Overflow"
Verb rekursiv eins:
    rekursiv zwei
.
Verb rekursiv zwei:
    rekursiv eins
.
rekursiv eins
*/