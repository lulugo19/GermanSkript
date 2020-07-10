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

// Klassendefinition
schreibe den Bereich "Objekt einer Klasse instanziieren"
Deklination Femininum Singular(Person, Person, Person, Person) Plural(Personen, Personen, Personen, Personen)
Deklination Maskulinum Singular(Name, Namens, Namen, Namen) Plural(Namen, Namen, Namen, Namen)
Deklination Maskulinum Singular(Nachname, Nachnamens, Nachnamen, Nachnamen) Plural(Nachnamen, Nachnamen, Nachnamen, Nachnamen)
Deklination Neutrum Singular(Alter, Alters, Alter, Alter) Plural(Alter, Alter, Alter, Alter)
Deklination Femininum Singular(Begrüßung, Begrüßung, Begrüßung, Begrüßung) Plural(Begrüßungen, Begrüßungen, Begrüßungen, Begrüßungen)
Deklination Maskulinum Singular(Abschied, Abschiedes, Abschied, Abschied) Plural(Abschiede, Abschiede, Abschieden, Abschiede)

Nomen Person mit
    der Zeichenfolge Name,
    der Zeichenfolge Nachname,
    der Zahl Alter:
.

Verb begrüße die Person mit der Zeichenfolge Begrüßung, der Zeichenfolge Abschied:
    schreibe die Zeile die Begrüßung + " " + den Namen der Person + ". " + den Abschied + "."
.

die Person ist eine Person mit dem Namen "Lukas", dem Nachnamen "Gobelet", dem Alter 22
begrüße die Person mit der Begrüßung "Hey", dem Abschied "Bye"
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