# Iteration 6

### Adjektivklasse

Beispiel:
```
// definiere eine Adjektivklasse für optionale Typen
Adjektivklasse<Typ> optional:
    
    Adjektiv vorhanden:
        Verb hole mich
    .

    Adjektiv fehlend:.
.

// erstelle eine Klasse Person, wo das Alter optional ist
Nomen Person mit
    der Zeichenfolge Name,
    einem Optionalen<Zahl> Alter:.

Implementiere die Person:
    Verb werde älter:
        wenn mein Alter?
            vorhanden ist:
                das momentaneAlter ist hole mein Alter
                mein Alter ist etwas Vorhandenes: das momentaneAlter + 1.
            fehlend ist:
                schreibe die Zeile "Das Alter der Person fehlt!"
            .
        .
    .
.

// GermanSkript Fehler: eine Person kann nicht zugleich vorhanden und fehlend sein!
Implementiere die vorhandene<Person>, fehlende<Person> Person:

.


// erstelle eine Instanz der Klasse
die PersonA ist eine Person mit dem Namen "Max", dem Alter (etwas Vorhandenes: 22.)

// und eine zweite Instanz ohne Alter
die PersonB ist eine Person mit dem Namen "Maria", dem Alter (etwas Fehlendes)

PersonA: werde älter!
PersonB: werde älter! // Das Alter der Person fehlt
```

Es könnte auch eine Adjektivklasse für ein Ergebnis das fehlschlagen kann existieren:

```
Adjektivklasse<ErgebnisTyp, FehlerTyp> fehlbar:
    
    Adjektiv erfolgreich:
        Verb hole (den ErgebnisTyp Ergebnis)
    .

    Adjektiv fehlerhaft:
        Verb hole (den FehlerTyp Fehler)
    .
.

Verb konvertiere die Zeichenfolge in (eine fehlbare<Zahl> Zahl):
    // Implementierung ...
.

das Ergebnis ist konvertiere die Zeichenfolge "789,12" in eine Zahl
die Zahl ist das Ergebnis?
    ist erfolgreich: hole das Ergebnis.
    sonst: 0.
.
```

### Nomenklasse

```
Nomenklasse Baum<Typ>:
    
    Nomen Knoten mit
        dem Typ Wert,
        dem Baum linkerBaum, 
        dem Baum rechterBaum:.

    Nomen Blatt:.
.

Implementiere den Baum:
    Verb durchlaufe mich mit dem Konsumierenden<Typ>:
        die Wurzel ist Ich
        wenn die Wurzel?
            ein Knoten ist:
                Konsumierendes: konsumiere den Typ (der Wert der Wurzel)!
                durchlaufe den linkenBaum der Wurzel
                durchlaufe den rechtenBaum der Wurzel
            .
            sonst:. // mache nichts
        .
    .
.

ein Baum ist ein Knoten 
    mit dem Wert 5,
    dem Baum (ein Knoten mit dem Wert 4, dem Baum (ein Blatt), dem Baum (
        ein Knoten mit dem Wert 2, dem Baum (ein Blatt), dem Baum (ein Blatt)))
    dem Baum (ein Knoten mit dem Wert 11, dem Baum (ein Blatt), dem Baum (ein Blatt))

// 5, 4, 2, 11
durchlaufe den Baum mit etwas Konsumierendem: schreibe die Zahl.

```

```
Nomenklasse<Typ> Optionales:
    
    Nomen Vorhandenes mit dem Typ:.

    Nomen Fehlendes:.
.
```