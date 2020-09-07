# Iteration 5

## hinzugefügte Features
- Fehler-Handling
- Generics
- Implementiere-Block
- Aufzählungen
- Zahlendekorierer
- Einheiten
- (Adjektive als Teile von großen Bezeichnern)
- weitere Überlegungen

## Fehler-Handling

Das Fehler-Handling soll wie in anderen Sprachen wie Java, Javascript, ... mit einer try-catch 
`versuche ... fange` und throw `werfe` funktionieren.

Beispiel:

```
versuche:
    die ZahlA ist lese als Zahl
    die ZahlB ist lese als Zahl
    schreibe die Zeile "#{die ZahlA} + #{die ZahlB} = #{die ZahlA + die ZahlB}"
.
fange den Fehler:
    schreibe die Zeile "Es wurde keine gültige Zahl eingegeben. #{die Nachricht des Fehlers}"
.
```

```
Nomen FakultätFehler als Fehler:.

Verb fakultät von der Zahl:
    wenn die Zahl < 0 ist: werfe einen FakultätFehler mit der Nachricht
        "die Fakultät von einer negativen Zahl ist undefiniert!".
    
    wenn die Zahl gleich 0 ist: gebe 1 zurück.
    sonst: gebe die Zahl * (fakultät von der Zahl - 1) zurück.
.
```

## Generics

### Typparameter für Klassen und Schnittstellen

`Nomen<BezeichnerN> ...`
`Adjektiv<BezeichnerN> ...`

Wird ein Typparameter als Parametername verwendet, dann wird beim Aufruf der Funktion/Methode
der Parametername mit dem Namen des eingesetzten Typs ersetzt.

Beispiele:

```
// Listendefinition
Nomen Liste<Typ>:
    jene AnZahl ist 0
.

Adjektiv<Typ> vergleichbar:
    Verb(Zahl) vergleiche den TypA mit dem TypB
.

Verb(Boolean) für Liste beinhaltet den Typ: intern.
Verb für Liste füge den Typ hinzu: intern.
Verb für Liste sortiere mich mit dem Vegleichbarem<Typ>: intern.

// Listen-Methoden-Aufruf
die Zahlen sind einige Zahlen[1, 2, 3, 4]
Zahlen:
    füge die Zahl 5 hinzu
    wenn beinhaltet die Zahl 5:
        füge die Zahl 6 hinzu
    .
    // sortiere absteigend
    sortiere dich mit etwas Vergleichbarem:
        gebe die ZahlB - die ZahlA zurück
    .
!
```

## Implementiere-Bereich
Methoden, Eigenschafts- und Konvertierungsdefinitionen einer Klasse müssen jetzt in einem `implementiere`-Bereich stehen.
Es kann mehrere `implementiere`-Bereiche für eine Klassen geben. Die Definitionen werden dann gesammelt.
Außerdem können Adjektive (Schnittstellen) beim `implementiere`-Bereich hingeschrieben werden, welche die Klasse dann im Block implementieren muss.
```
implementiere ArtikelNb Liste(Adjektive) KlassenTyp: Implementierung.
```

## Aufzählungen
Aufzählungen können beliebig viele Eigenschaften haben.

Beispiel:

```
Aufzählung Ereignis mit der Zeichenfolge Name:
    Weihnachten mit dem Namen="Weihnachten"
    Ostern mit dem Namen="Ostern"
    Haloween mit dem Namen="Haloween"
    Geburtstag mit dem Namen="Geburstag"
.

das Ereignis ist das Ereignis Weihnachten
schreibe die Zeichenfolge (der Name des Ereignisses) // "Weihnachten"
```

#### Überlegung: 
Vielleicht sollten Aufzählungen so gemacht sein, dass man verschiedene
eigene Untertypen als Elemente der Aufzählung auflisten kann.
Also so wie bei der Programmiersprache [Rust](https://doc.rust-lang.org/book/ch06-01-defining-an-enum.html). 
Da GermanSkript keine Nullreferenz hat und diese auch nicht vorgesehen ist (Billion-Dollar-Mistake), könnte
man dann einen Option-Typen machen.

```
Aufzählung<Typ> Option:
    Nomen Nichts:.
    Nomen Etwas mit dem Typ:.
.
```

## Zahlendekorierer

Hinter einer Zahl kann ein Zahlendekorierer kommen, der der Zahl eine neue Bedeutung gibt.

### Zahlenname

[Zahlennamen]([https://de.wikipedia.org/wiki/Zahlennamen]) stehen als Suffix hinter der Zahl und erhöhen die Zahl um eine bestimmte Zehnerpotenz.
Die Regel ist das die Zahl kleiner als die Zehnerpotenz sein muss. Wir nehmen nicht alle Zahlennamen rein sondern starten bei `Hundert`
und enden bei `Dezilliarde` (10^63).

```
200 Hundert => geht nicht
100 Tausend => funktioniert
1,8 Milliarden
```

### Prozent

`Prozent` oder das Symbol `%` dekoriert eine Zahl und wandelt diese von Prozent in Dezimalschreibweise um.

```
10% gleich 0,1
0,35 Prozent gleich 0,0035
120,4 Prozent gleich 1,204
```

Vorschlag: eine Zahl kann in direkt in Prozent ausgegeben werden.

```
die Zahl ist 12,3%
schreibe die Zahl // 0.123
schreibe die Zahl als Prozent // spezielle Umwandlung in String
```

## Einheiten

`Zahl [Zahlenname] [Einheit]`

### Einheitsdefinition

`Einheit(BezeichnerF, Symbol, Operationen)`

Eine Einheit gepaart mit einer Zahl bildet einen neuen Typen. 
Nur die Rechenoperationen, die bei der Einheitsdefinition angegeben werden, können auf
den Typen angewendet werden.

z.B.

```
Einheit(Meter, m)

die Strecke ist 22,5 km
die Streke ist 
```

Ein `Symbol` ist ein Zeichen oder eine kurze kleingeschriebene Zeichenfolge

Für Einheiten gibt es zwei Schreibwesen:
1. die lange Form wo der `BezeicherF` verwendet wird z.B. `Kilometer`
2. die kurze Form wo das `Symbol` verwendet wird z.B. `km`

Die Einheiten können jetzt mit den [SI-Präfixen](https://de.wikipedia.org/wiki/Vors%C3%A4tze_f%C3%BCr_Ma%C3%9Feinheiten)
verwendet werden, um die Zahl in eine bestimmte 10er-Potenz umzuwandeln.

```
1 km gleich 1000 m
70 mg gleich 0,03 g

die Erdmasse ist 5,9722 Zetatonnen
```

Vorschlag: Quadrat- und Kubik- auch noch reinnehmen.

```
156 cm^2 gleich 0,0156 m^2

die Erdmasse ist 1,0833 * 10^12 Kubikkilometer
```

Vorschlag: Einheiten auf Basis anderer Einheiten

```
Einheit(Minute, min) ist 60 Sekunden
```

mit Umrechnung:

```
Einheit(Celsius, C)
Einheit(Fahrenheit, F) ist C * 1.8 + 32 

// wird dann automatisch die Umkehrfunktion gemacht???
die Temperatur ist 10C
schreibe die Temperatur in Fahrenheit als Zeichenfolge // 50 C
```

Vorschlag: Zahlen als Einheit drucken
```
die Strecke ist 1,5 km
schreibe die Strecke als Zeichenfolge // 1,5 km
schreibe die Strecke in m als Zeichenfolge // 1,5 km
```

Vorschlag: arithmetische Operation auf Einheiten
```
1km + 100m gleich 1,1 km
100m * 100m gleich 10.000 m^2

// da `+` links assosiativ ist, wird die Einheit des linken Operanden übernommen
schreibe die Zeichenfolge 1min + 30s als Zeichenfolge // 1,5 min
schreibe die Zeichenfolge 30s + 1min als Zeichenfolge // 90s
```

Zahl+Einheit bilden einen neuen Typen! Sie sind keine Zahlen, sondern eine Zahl und eine Einheit.
Nicht alle arithmetischen Operationen sind bei allen Einheiten erlaubt und arithemetische Operationen sind nur
auf Einheiten erlaubt die ineinander umgerechnet werden können.

Wenn man eine Einheit definiert, müsste noch angegeben werden welche arithmetischen Operationen erlaubt sind:

```
Einheit(Celsius, C, +-)`
```

Die Operatoren gelten jedoch nicht für die Umrechnung. Da sind alle Rechenoperatoren erlaubt.

## Adjektive als Teil von großen Bezeichnern (Nomen)

Bisher ist es möglich für Nomen zusammengesetze Wörter, sowie Symbole am Ende des Wortes zu verwenden.
z.B.
```
der VorName ist "Lukas"
die ZahlX ist 4
```
Nun sollen Nomen um Adjektive ergänzt werden. Adjektive sind ähnlich wie zusammengesetze Wörter nur
dass es für jedes Nomen höchstens ein Adjektiv geben kann, das als kleines Wort vor dem Nomen drangehängt wird.
Das besondere an Adjektiven ist, dass diese vom Grammatik-Prüfer geprüft werden, damit diese die richtige Deklination
haben.

Beispiel:

```
Verb schreibe die Zeichenfolge Name:
    schreibe die Zeile "Name: " plus den Namen
.

ein vollerName ist der VorNamen + den NachNamen
schreibe den vollenNamen
```

## Überlegungen für die nächste Iteration

- Unterstützung von asynchronen Operationen
    - Single-Threaded wie bei JS mit EventLoop