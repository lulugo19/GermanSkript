Deklination Femininum Singular(Zahl, Zahl, Zahl, Zahl) Plural(Zahlen, Zahlen, Zahlen, Zahlen)
Deklination Femininum Singular(Zeichenfolge, Zeichenfolge, Zeichenfolge, Zeichenfolge) Plural(Zeichenfolgen, Zeichenfolgen, Zeichenfolgen, Zeichenfolgen)
Deklination Femininum Singular(Zeile, Zeile, Zeile, Zeile) Plural(Zeilen, Zeilen, Zeilen, Zeilen)
Deklination Maskulinum Singular(Bereich, Bereichs, Bereich, Bereich) Plural(Bereiche, Bereiche, Bereiche, Bereiche)
Deklination Maskulinum Singular(Baum, Baumes, Baum, Baum) Plural(Bäume, Bäume, Bäumen, Bäume)
Deklination Maskulinum Singular(Name, Namens, Namen, Namen) Plural(Namen, Namen, Namen, Namen)

// INTERNE FUNKTIONEN
Verb schreibe die Zeichenfolge: intern. // print

Verb schreibe die Zeichenfolge Zeile: intern. // println

Verb schreibe die Zahl: intern.

Verb(Zeichenfolge) lese: intern.

Verb schreibe die Zeichenfolge Bereich:
    schreibe die Zeile ""
    schreibe die Zeile "----------------- " + den Bereich + " -----------------"
.

// Bedingungen
schreibe den Bereich "Bedingung mit Zahlen"

Verb teste die Zahl:
    wenn die Zahl gleich 3:
      schreibe die Zeile "Alle guten Dinge sind drei!".
    sonst wenn die Zahl gleich 42:
      schreibe die Zeile "Die Antwort auf alles.".
    sonst: schreibe die Zahl.
.

teste die Zahl 11
teste die Zahl 3
teste die Zahl 42
teste die Zahl 12


// Fakultät
schreibe den Bereich "Fakultät"
Verb(Zahl) fakultät von der Zahl:
    wenn die Zahl gleich 0: gebe 1 zurück.
    sonst: gebe die Zahl * (fakultät von der Zahl (die Zahl -1)) zurück.
.

schreibe die Zeichenfolge "Die Fakultät von der Zahl 5 ist: "
schreibe die Zahl (fakultät von der Zahl 5)
schreibe die Zeichenfolge "Die Fakultät von der Zahl 6 ist: "
schreibe die Zahl (fakultät von der Zahl 6)

// Schleife
schreibe den Bereich "Solange-Schleife: Countdown"
Deklination Maskulinum Singular(Zähler, Zählers, Zähler, Zähler) Plural(Zähler, Zähler, Zählern, Zähler)

Verb zähle ab der Zahl runter:
    schreibe die Zeichenfolge "Countdown:"
    ein Zähler ist die Zahl
    solange der Zähler größer gleich 0:
        schreibe die Zahl (der Zähler)
        ein Zähler ist der Zähler - 1
    .
    schreibe die Zeile "Los!"
.

zähle ab der Zahl 10 runter

// Abbrechen und Fortfahren einer Schleife
schreibe den Bereich "Abbrechen und Fortfahren einer Schleife"
schreibe die Zeile "Gebe nur die geraden Zahlen von 1 bis 10 aus."
ein Zähler ist 0
solange wahr:
    ein Zähler ist der Zähler plus 1
    wenn der Zähler > 10: abbrechen.
    wenn der Zähler % 2 gleich 1: fortfahren.
    schreibe die Zahl (der Zähler)
.

// Listen
schreibe den Bereich "Listen"
die Zahlen sind einige Zahlen [2,12, 3, 5, 7,6, 11,4, 13,5, 17,6, 19]

für jede Zahl:
    wenn die Zahl > 13: abbrechen.
    schreibe die Zahl
.

// Listenindex
schreibe den Bereich "Listen Index"
schreibe die Zeichenfolge "Die erste Primzahl ist: "
schreibe die Zahl[0]
schreibe die Zeichenfolge "Die vierte Primzahl ist: "
schreibe die Zahl[die Zahl[0] + 1]
schreibe die Zeichenfolge "Die fünfte Primzahl ist: "
schreibe die Zahl[4]

//Lese-Funktion
schreibe den Bereich "Eingabe"
schreibe die Zeichenfolge "Gebe deinen Namen ein: "
der Name ist lese
schreibe die Zeichenfolge "Dein Name ist " + den Namen