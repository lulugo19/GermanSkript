# GermanScript
Eine objektorientierte, streng typisierte Programmiersprache, die sich wie die deutsche Sprache schreibt.

## Syntax und Semantik
Die Syntax wird in einer Abwandlung der erweiterten Backus-Naur-Form beschrieben:

Syntax in eckigen Klammern ist optional `[Optional]` und Syntax in geschweiften Klammern 
kann ein oder mehrmals wiederholt werden `{Wiederholung}`.

### Groß- und Kleinschreibung
Groß- und Kleinschreibung ist wichtig bei GermanScript. Namen die festgelegt werden können, unterteilen sich in `Nomen`,
die **groß** und `Verben` die **klein** geschrieben werden.
`Nomen` werden bei [Variablendeklarationen](###Deklaration-von-Variablen) und [Typdefinitionen](###Definieren-eines-Typs)
und `Verben` bei der Definition von [Funktionen](###Definieren-einer-Funktion) und Methoden verwendet.

### Grammatik
Da GermanScript ein Teil der deutschen Grammatik beinhaltet, spielt der Genus (das Geschlecht) eines Typs eine wichtige Rolle. 
Wenn Artikel verwendet werden, müssen diese mit dem Genus des Typs übereinstimmen.
Außerdem ist auch noch die Form des Artikels wichtig, jenachdem in welchem Kontext das Nomen verwendet wird.

| Form       | bestimmt / unbestimmt | Syntax-Abkürzung | Verwendung bei | Maskulinum | Femininum | Neutrum | Plural |
| ---------- | --------------------- | ---------------- | -------------- | ---------- | --------- | ------- | ------ |
| Nominativ  | bestimmt | `ArtikelNb` | unveränderbare Variablendeklaration | der | die | das | die |
| Nominativ | unbestimmt | `ArtikelNu` | veränderbare Variablendeklaration | ein | eine | ein |
| Akkusativ  | bestimmt | `ArtikelAb` | Typdefinition, Alias, Eigenschaftsdefinition | den | die | das | die |
| Genitiv | bestimmt | `ArtikelGb` | Feldzugriff/Destrukturierung bei unveränderbaren Variablen | des | der | des | der |
| Genitiv | unbestimmt | `ArtikelGu` | Feldzugriff/Destrukturierung bei veränderbarer Variable | eines | einer | eines | der |

### Bereiche

Ein GermanScript-Programm besteht aus mehreren Bereichen. Innere Bereiche können auf den Inhalt von äußeren Bereichen zugreifen, aber
äußere Bereiche können nicht auf den Inhalt von inneren Bereichen zugreifen.
Ein Bereich startet mit `:` und endet mit `.`. Einen einfachen Bereich kann man auch einen Namen geben, indem man einen Namen vor dem `:` schreibt.
Beispiel:
```
Abschnitt:
    // Bereichsinhalt
    // ...
.
```

### Sätze
Ein GermanScript-Programm besteht aus mehreren Sätzen (im Programmierspachen-Jargon auch Statements genannt).
Sätze werden in GermanScript mit einer neuen Zeile oder mit `;` getrennt.
Folgendes sind Sätze:
- Variablendeklaration
- Funktionsaufrufe
- Methodenaufrufe
- Schlüsselwörter wie `abbrechen` oder `fortfahren`
- `zurück`-Anweisung in Funktionen oder Methoden

Außerdem gibt es noch Kontrollstrukturen, die auch zu den Sätzen zählen. Sie werden aber nicht mit `;` getrennt, 
sondern beginnen jeweils einen neuen Bereich.
Da wären:
- [Bedingungen](###Bedingungen)
- Schleifen
     - [Solange-Schleife](###Solange-Schleife)
     - [Für-Jede-Schleife](###Für-Jede-Schleife)

### Ausdrücke
Ein Ausdruck ist alles was einer Variablen zugewiesen werden kann. Ausdrücke werden außerdem als Argumente bei
eine Funktionsaufruf übergeben.
Folgendes sind Ausdrücke:
- Literale: Zeichenfolge, Zahlen, Listen, Boolsche Werte, Lambdas
- Variablen
- Funktions- oder Methodenaufrufe, die einen Rückgabewert haben
- wenn-dann-sonst-Ausdrücke


### Abbrechen oder Fortfahren einer Schleife
Das Schlüsselwort `abbrechen` dient zur sofortigen Beendigung einer Schleife.
Das Schlüsselwort `fortfahren` springt zu nächsten Schleifen-Iteration.


### Operatoren
Jeder Operator hat neben einem Symbol auch noch eine Textrepräsentation, die stattdessen verwendet werden kann.

Umso höher die Bindungskraft, desto mehr bindet der Operator seine Operanden.

Die Operatoren Gleichheit `==` und Ungleichheit `!=` können bei allen Typen verwendet werden, um die Gleichheit
zu überprüfen. Alle anderen Operatoren können nur bei den Inbuild-Typen `Zahl`, `Liste`, `Boolean` verwendet werden.
Das Überladen von Operatoren ist voerst nicht vorgesehen.

#### Binäre Operatoren
| Funktion | Symbol | Text | Assoziativität | Bindungskraft |
| -------- | ------ | ---- | -------------- | --------- |
| Zuweisung | `=` | `ist` | rechts | 0 |
| Logisches Oder | `\|\|` | `oder` | links | 1 |
| Logisches Und | `&&` | `und` | links | 2 |
| Gleichheit | `==` | `gleich` | links | 3 |
| Ungleichheit | `!=` | `ungleich` | links | 3 |
| Größer | `>` | `größer` | links | 3 |
| Kleiner | `<` | `kleiner` | links | 3 |
| Größer-Gleich | `>=` | `größer gleich` | links | 3 |
| Kleiner-Gleich | `<=` | `kleiner gleich` | links | 3 |
| Plus | `+` | `plus` | links | 4 |
| Minus | `-` | `minus` | links | 4 |
| Mal | `*` | `mal` | links | 5 |
| Geteilt | `/` | `durch` | links | 5 |
| Hoch | `^` | `hoch` | rechts | 6 |

#### Unäre Operatoren
| Funktion | Symbol | Text | Assoziativität | Priorität |
| -------- | ------ | ---- | -------------- | --------- |
| Logisches Nicht | `!` | `nicht` | rechts | 7 |
| Negativ | `-` | `negativ` | rechst | 7 |
| Positiv | `+` | `positiv` | rechts | 7 |


### Deklaration von Variablen
Für eine unveränderbare Variable: `ArtikelNb [Typ] Nomen Zuweisungsoperator Ausdruck`

Für eine veränderbare Variable: `ArtikelNu [Typ] Nomen Zuweisungsoperator Ausdruck`

Variablen können auf zwei Art und Weisen deklariert werden. Für Variablen, die nicht erneut zugewiesen werden können
werden die bestimmten Artikel `der, die, das` verwendet. Und für Variablen die erneut zugewiesen werden können, werden
die unbestimmten Artikel `ein, eine` verwendet. Der Artikel muss außerdem mit dem Geschlecht des Ausdrucks übereinstimmen.
Der Typ kann bei der Deklaration weggelassen werden und wird dann aus dem Ausdruck ermittelt.

Beispiele:

- `eine Zahl X ist 100`
- `die Summe ist X + 5`
- `die Zeichenfolge Beschreibung ist "dunkel, groß und blau"`

Die Deklaration von Variablen, die Listen enthalten ist ein Spezialfall. 
Bei einer Liste, die nicht erneut zugewiesen werden kann wird der Artikel `die` verwendet.
Beispiel: `die Zahlen sind Zahlen[1,2,3,4]`. Bei einer veränderbaren Variable, wird der Artikel komplett weggelassen.
Beispiel: `Wörter sind Zeichenfolgen["Hallo", "Test", "Kiste", "Fluch"]`.
Außerden wird statt dem Zuweisungswort `ist`, `sind` verwendet.

### Bedingungen
```
wenn Bedingung:
  Sätze
{sonst wenn Bedingung: Sätze}
[sonst: Sätze] .
```
Beispiel:
```
wenn X gleich 3:
  drucke "Alle guten Dinge sind drei!"
sonst wenn X gleich 42:
  drucke "Die Antwort auf alles."
sonst drucke X .
```

### Solange-Schleife
`solange Bedingung IBereich`

Solange die Bedingung zutrifft, werden die Sätze ausgeführt.

### Für-Jede-Schleife
`für (jeder | jede | jedes) (Nomen | Destrukturierung) in Ausdruck IBereich`

Für jedes Element in dem iterierbaren Objekt, wird die Schleife einmal ausgeführt, wobei
das Element an den Namen gebunden wird.

Beispiel:

```
für jede Zahl von 1 bis 10:
  drucke Zahl.
```

### Wenn-Dann-Sonst-Ausdruck
`wenn Bedingung dann Ausdruck sonst Ausdruck`

Beispiel:

`wenn X gleich 42 dann "Die Antwort auf alles" sonst "etwas anderes"`

Wenn die Bedingung zutrifft dann wird der erste Ausdruck zurückgegeben, sonst der zweite Ausdruck.
Es ist wichtig, dass die Typen der beiden Ausdrücke übereinstimmen.

### Definieren einer Konstante
`Nomen ist Literal`

Konstanten sind unveränderbar und können nur einmal zugewiesen werden. Nur Zahlen-, Zeichenfolgen- oder Boolean-Literale können einer Konstante zugewiesen werden.

Beispiel: `PI ist 3.14159265359`

### Definieren einer Funktion
`definiere Verb [mit [Rückgabe Typ | Typ [Nomen]] {,Typ [Nomen]}] IBereich`

Beispiel:
```
definiere fakultät mit Rückgabe Zahl, Zahl:
    zurück wenn Zahl gleich 0 dann 1 sonst Zahl mal fakultät Zahl minus 1.
```

### Funktionsaufruf
```
Parameter können entweder als Ausdrücke, wo dann die Reihenfolge die Bindung bestimmt (Reihenfolgen-Form) 
übergeben werden oder als eine Liste von Zuweisungen (Namensform). 
Es kann auch gemischt sein, wobei dann die Reihenfolgen-Form zuerst kommen muss
und dann die Namens-Form.

Parameter: `(Ausdruck {, Ausdruck}) | (Nomen Zuweisungsoperator Ausdruck {, Nomen Zuweisungsoperator Ausdruck})`

Funktionsaufruf: `Verb [Parameter]`
```

### Definieren eines Typs
```
definiere (ArtikelAb Nomen) [als Typ] mit Plural Nomen, Genitiv Nomen: 
  [Nomen {, Nomen} als Typ {Nomen {, Nomen} als Typ}].
```
Beispiel:
```
definiere die Person mit Plural Personen, Genitiv Person:
    Nachname, Vorname als Zeichenfolge
    Alter als Zahl.
    
definiere den Student als Person mit Plural Studenten, Genitiv Students:
    Studiengang als Zeichenfolge
    Semester als Zahl.
```

### Instanziieren eines Objekts eines Typs
`Person [mit Nomen Ausdruck {, Nomen Ausdruck}]`

Beispiel:

`die Person Donald ist Person mit Vorname "Donald", Nachname "Duck"`

### Zugriff auf Felder eines Objekts
`Feld Genitiv-Artikel Objekt`

Beispiel:

`Name (ArtikelGb | ArtikelGu) Person`

### Destrukturierende Zuweisung

#### Destrukturierung von Objekten
Beispiel:
```
die Person ist Person mit Nachname="Peterson", Vorname="Hans", Alter=42
(der Nachname, ein Alter Geburtstagsalter) der Person
Alter ist Alter + 1
drucke Nachname, Alter // Peterson 43
```

#### Destrukturierung von Listen
```
[die Person1, die Person2, RestlichePersonen...] sind Personen[p1, p2, p3, p4, p5]
/*
Person1 = p1
Person2 = p2
RestlichePersonen = [p3, p4, p5]
*/
```

### Definieren einer Methode
`definiere Verb für Typ [mit [Rückgabe Typ | Typ [Nomen]] {,Typ [Nomen]}] IBereich`

Das Verb einer Methode sollte möglichst im Imperativ stehen.

Beispiel:
```
definiere stellDichVor für Person mit Rückgabe Zeichenfolge, Zeichenfolge Begrüßung, Zeichenfolge LetzterSatz:
    zurück Begrüßung + ", " + "mein Name ist " + mein Name " und ich bin " + mein Alter " Jahre alt." + LetzterSatz.
```

### Methodenaufruf
Parameter können entweder als Ausdrücke, wo dann die Reihenfolge die Bindung bestimmt (Reihenfolgen-Form) 
übergeben werden oder als eine Liste von Zuweisungen (Namensform). 
Es kann auch gemischt sein, wobei dann die Reihenfolgen-Form zuerst kommen muss
und dann die Namens-Form.

Parameter: `(Ausdruck {, Ausdruck}) | (Nomen Zuweisungsoperator Ausdruck {, Nomen Zuweisungsoperator Ausdruck})`

Methodenaufruf: `Verb Ausdruck [mit Parameter]!`

Beispiel:

```
Person Rick ist Person mit Vorname="Rick", Nachname="Sanchez", Alter=70
stellDichVor Rick mit Begrüßung="Woooobeeewoobeedubdub!", LetzerSatz="Rülps!"!
```

### Definieren einer Schnittstelle
`definiere Schnittstelle Nomen: {Verb [mit [Rückgabe Typ | Typ [Nomen]] {,Typ [Nomen]}]}.`

Eine Schnittstellendefinition besteht aus Methodensignaturen. Eine Schnittstelle wird automatisch für einen Typ
implementiert, wenn sie alle Methoden definiert. Eine Schnittstelle hat automatisch immer das Geschlecht `Neutrum`.

Beispiel:

```
definiere Schnittstelle Zeichenbares:
    zeichne mit Farbe
    skaliere mit Rückgabe Zahl.
```

### Typ-Alias
`alias Artikel Nomen ist Nomen mit Plural Nomen, Genitiv Nomen`

Beispiel:

`alias das Alter ist Zahl mit Plural Alter, Genitiv Alters`

### Definieren eines Moduls
`Modul Nomen: Definitionen.`
Ein Modul ist ein Behälter für Definitionen. In Ihm können Typen, Funktionen, Methoden, Konstanten und wiederum Module definiert werden.
Ein Modul ist dafür da Code zu organisieren und Namenskollisionen zu verhindern. Der Name des Moduls wird nämlich Teil des vollen Namen einer Definition.
Module können ineinander verschachtelt werden, aber ein Modul kann nur in dem globalen Bereich oder in anderen Modulen definiert werden. Um auf einen in einem Modul definierten Typen zu verweisen wird dann der doppelte Doppelpunkt `::` nach dem Modulnamen verwendet.

Beispiel:
```
Modul Zoo:
    definiere das Gehege mit Plural Gehege:.

    Modul Tiere:

        definiere fütter mit Tier: drucke "Fütter " + Tier als Zeichenfolge.
        
        definiere das Tier mit Plural Tiere:.
        
        Modul Säuger:
            definiere das Pferd als Tier mit Plural Pferde:.
        .

        Modul Amphibien:
            definiere das Krokodil als Tier mit Plural Krokodile:.
        .
    .
.

das Gehege ist Zoo::Gehege
das Pferd ist Zoo::Tiere::Säuger::Pferd

Zoo::Tiere::fütter Pferd
```
Um aber nicht immer den ganzen Namen verwenden zu müssen kann das `verwende`-Schlüsselwort verwendet werden um innerhalb eines Modulnamens zu gehen.

Beispiele:
```
verwende Zoo
das Gehege ist Gehege
das Pferd ist Tiere::Säuger::Pferd
```

```
verwende Zoo::Tiere::Säuger
verwende Zoo::Tiere::Amphibien

das Pferd ist Pferd
das Krokodil ist Krokodil
```

## Typen
GermanScript verfügt vorab über folgende Typen:

### Zahlen
Zahlen werden in der deutschen Schreibweise für Zahlen geschrieben. Das heißt vor dem `,` steht die Ganzzeil und nach
dem Komma die Teilzahl. Außerdem kann man bei der Ganzahl Punkte als Abtrennung der tausender Stellen verwenden.

Beispiel: `898.100.234,129123879`

### Zeichenfolgen
Zeichenfolgen werden innerhalb der Anführungszeichen `""` geschrieben.

### Booleans
Booleans habe zwei Werte `wahr` oder `falsch`.

### Listen
```NomenPlural[\[{Ausdruck}\]]```

Beispiel:

`die Primzahlen sind Zahlen[2, 3, 5, 7, 11, 13]`

```
die Person1 ist Person mit Name=John", Alter=42
die Person2 ist Person mit Name="Susan", Alter=19
die Person3 ist Person mit Name="Egon", Alter=72
die Personen sind Personen[Person1, Person2, Person3]
```

### Funktionen
```\{Nomen} IBereich.```

Beispiel:
```
// sortiere die Personen nach Alter aufsteigend
die Personen sind Personen[Person1, Person2, Person3]

// erstelle Vergleichsfunktion
eine Vergleichsfunktion ist \PersonA, PersonB: 
    zurück wenn Alter der PersonA < Alter der PersonB dann -1 sonst wenn Alter der PersonA > Alter der PersonB dann 1 sonst 0.

sortiere Personen mit Vergleichsfunktion!

// Vergleichsfunktion kann durch  Dekstrukturierung verbessert werden
eine Vergleichsfunktion ist \(Alter A), (Alter B): 
    zurück wenn A < B dann -1 sonst wenn A > B dann 1 sonst 0.

sortiere Personen mit Vergleichsfunktion!
```

### Typ-Umwandlung (Casting)

#### implizit
Ein vererbter Typ lässt sich immer einer Variable mit dem Typ des Elterntyps zuweisen.

#### explizit
`Ausdruck als Typ`

Beispiel:
`"42" als Zahl`

Es kann eine Typumwandlung von einem Typ zu einem anderen Typ für jeden Typen definiert werden:

Syntax: `definiere als Typ1 für Typ2 mit Rückgabe Typ1 IBereich`
