Deklination Maskulinum Singular(Bereich, Bereichs, Bereich, Bereich) Plural(Bereiche, Bereiche, Bereiche, Bereiche)

Verb schreibe die Zeichenfolge Bereich:
    schreibe die Zeile ""
    schreibe die Zeile "----------------- " + den Bereich + " -----------------"
.

//Typ-Umwandlung
schreibe den Bereich "Typ-Umwandlung"
die Zeichenfolge ist "1,7239"
die Zahl ist die Zeichenfolge als Zahl
schreibe die Zahl

// Binärer Ausdruck
// Es ist jetzt möglich dass bei einem binären Ausdruck als Argument der erste Operand als Parametername erkannt wird
schreibe den Bereich "Binärer Ausdruck"
die Zahl ist 5
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

Deklination Femininum Singular(Person, Person, Person, Person) Plural(Personen, Personen, Personen, Personen)
Deklination Maskulinum Singular(Name, Namens, Namen, Namen) Plural(Namen, Namen, Namen, Namen)
Deklination Maskulinum Singular(Nachname, Nachnamens, Nachnamen, Nachnamen) Plural(Nachnamen, Nachnamen, Nachnamen, Nachnamen)
Deklination Neutrum Singular(Alter, Alters, Alter, Alter) Plural(Alter, Alter, Alter, Alter)
Deklination Femininum Singular(Begrüßung, Begrüßung, Begrüßung, Begrüßung) Plural(Begrüßungen, Begrüßungen, Begrüßungen, Begrüßungen)
Deklination Maskulinum Singular(Abschied, Abschiedes, Abschied, Abschied) Plural(Abschiede, Abschiede, Abschieden, Abschiede)

// Klassendefinition
schreibe den Bereich "Klassendefinition und Objektinstanziierung"
Nomen Person mit
    der Zeichenfolge Name,
    der Zeichenfolge Nachname,
    der Zahl Alter:

    schreibe die Zeile "die Person " + "'" + meinen Namen + " " + meinen Nachnamen + "' wurde erstellt!"
.
die Person ist eine Person mit dem Namen "Lukas", dem Nachnamen "Gobelet", dem Alter 22

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

Deklination Femininum Singular(Wiederholung, Wiederholung, Wiederholung, Wiederholung) Plural(Wiederholungen, Wiederholungen, Wiederholungen, Wiederholungen)
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
Deklination Femininum Singular(Summe, Summe, Summe, Summe) Plural(Summen, Summen, Summen, Summen)
schreibe den Bereich "Symbol-Test"
die Zahlen sind einige Zahlen[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
eine Summe ist 0
für jede Zahl X:
    eine Summe ist die Summe plus das X
.
schreibe die Zeile "Die Summe ist: " + die Summe als Zeichenfolge


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