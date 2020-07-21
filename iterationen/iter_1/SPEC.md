# Iteration 1

## hinzugefügte Sprachsyntax
- Deklanation mit Duden
- Bedingungen
- Listen
- Schleifen
    - Solange-Schleife
    - Für-Jede-Schleife

## Sätze

### Bedingung
```
wenn Bedingung:
  Sätze
{sonst wenn Bedingung: Sätze}
[sonst: Sätze] .
```

### Solange-Schleife
`solange Bedingung: Sätze.`

### Für-Jede-Schleife
`für (jeden | jede | jedes) BezeichnerA [in Liste]: Sätze.`

## Ausdrücke
### Liste
```BezeichnungP \[SemikolonListe(Ausdruck)\]```

Beispiel:

`die Primzahlen sind einige Zahlen[2; 3; 5; 7; 11; 13]`

```
die Person1 ist Person mit dem Namen=John", dem Alter=42
die Person2 ist Person mit dem Namen="Susan", dem Alter=19
die Person3 ist Person mit dem Namen="Egon", dem Alter=72
die Personen sind Personen[Person1, Person2, Person3]

Personen: füge Person mit dem Namen="Test", dem Alter=23 hinzu
```


Listen fangen in GermanScript mit dem Index 0 an.

Zugriff auf Element per Liste:

`ArtikelAb NomenNS[Index]`

```
der Erste ist die Person[0]
eine Zweite ist die Person[1]
```

## Funktionsaufrufe und -definitionen unterstützen Listen
z.B. `addiere einige Zahlen`
```
Verb(Zahl) addiere die Zahlen:
    eine Summe ist 0
    für jede Zahl in Zahlen:
        eine Summe ist Summe + Zahl
    .
    gebe die Summe zurück
.

// Funktion mit Variable aufrufen
die Zahlen sind einige Zahlen[1, 2, 3, 4, 5]
addiere die Zahlen

die Nummern sind einige Zahlen[1, 2, 3, 4, 5]
addiere die Zahlen Nummern

// Funktion direkt mit einer Liste aufrufen
addiere einige Zahlen[2, 3, 5, 7, 11, 13]
```

## Definitionen

### Deklination mit Duden
`Deklination Duden(Bezeichner)`

## neue interne Funktionen

### lese
Liest die nächste ganze Zeile von der Standardeingabe ein
`lese`

## Todo
- Bug beheben mit Präpositionen Dativ und Akkusativ und dem falschen vollen Namen der Funktion (Lukas)
- Typchecker: Bedingung checken -> Die Bedingung muss vom Typ Boolean sein (Finn)
- Interpreter: Bedingung (Lukas)


