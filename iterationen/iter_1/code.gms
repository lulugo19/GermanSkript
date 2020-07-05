Deklination Femininum Singular(Zahl, Zahl, Zahl, Zahl) Plural(Zahlen, Zahlen, Zahlen, Zahlen)
Deklination Femininum Singular(Zeichenfolge, Zeichenfolge, Zeichenfolge, Zeichenfolge) Plural(Zeichenfolgen, Zeichenfolgen, Zeichenfolgen, Zeichenfolgen)
Deklination Femininum Singular(Zeile, Zeile, Zeile, Zeile) Plural(Zeilen, Zeilen, Zeilen, Zeilen)

// INTERNE FUNKTIONEN
Verb schreibe die Zeichenfolge: intern. // print

Verb schreibe die Zeichenfolge Zeile: intern. // println

Verb schreibe die Zahl: intern.

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

Verb(Zahl) fakultät von der Zahl:
    wenn die Zahl gleich 0: gebe 1 zurück.
    sonst: gebe die Zahl * (fakultät von der Zahl (die Zahl - 1)) zurück.
.

schreibe die Zeichenfolge "Die Fakultät von der Zahl 5 ist: "
schreibe die Zahl (fakultät von der Zahl 5)