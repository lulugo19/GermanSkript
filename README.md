# GermanScript
Eine objektorientierte, streng typisierte Programmiersprache, die die deutsche Grammatik nachahmt.

## Syntax und Semantik
Die Syntax wird in einer Abwandlung der erweiterten Backus-Naur-Form beschrieben:

Syntax in eckigen Klammern ist optional `[Optional]` und Syntax in geschweiften Klammern 
kann ein oder mehrmals wiederholt werden `{Wiederholung}`.

### Groß- und Kleinschreibung
Groß- und Kleinschreibung ist wichtig bei GermanScript. Namen die festgelegt werden können, unterteilen sich in `Nomen`,
die **groß** und `Verben` die **klein** geschrieben werden.
`Nomen` werden bei [Variablendeklarationen](###Deklaration-von-Variablen) und [Typdefinitionen](###Definieren-eines-Typs)
und `Verben` bei der Definition von [Funktionen](###Definieren-einer-Funktion) und Methoden verwendet.

### Sätze
Ein GermanScript-Programm besteht aus mehreren Sätzen (im Programmierspachen-Jargon auch Statements genannt).
Sätze werden in GermanScript mit einer neuen Zeile oder mit `;` getrennt.
Folgendes sind Sätze:
- Variablendeklaration
- Funktionsaufrufe
- Methodenaufrufe
- Schlüsselwörter wie `abbrechen` oder `fortfahren`
Wenn nachfolgend in der Syntax `Sätze` steht, sind damit keiner, ein Satz oder meherere Sätze gemeint.

### Ausdrücke
Ein Ausdruck ist alles was einer Variablen zugewiesen werden kann. Ausdrücke werden außerdem als Argumente bei
eine Funktionsaufruf übergeben.
Folgendes sind Ausdrücke:
- Literale: Zeichenfolge, Zahlen, Listen, Boolsche Werte
- Variablen
- Funktions- oder Methodenaufrufe, die einen Rückgabewert haben
- wenn-dann-sonst-Ausdrücke


### Operatoren
Jeder Operator hat neben einem Symbol auch noch eine Textrepräsentation, die stattdessen verwendet werden kann.
Umso höher die Bindungskraft, umso mehr bindet der Operator seine Operanden.

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

Alle Operatoren außer der Zuweisung `=` können für jede Klasse definiert werden. 
Dazu muss einfach nur eine Methode mit der Textrepräsentation des Operators und den geeigneten Parametern implementiert werden.

### Deklaration von Variablen
`Artikel [Typ] Nomen Zuweisungsoperator Ausdruck`

Variablen können auf zwei Art und Weisen deklariert werden. Für Variablen, die nicht erneut zugewiesen werden können
werden die bestimmten Artikel `der, die, das` verwendet. Und für Variablen die erneut zugewiesen werden können, werden
die unbestimmten Artikel `ein, eine` verwendet. Der Artikel muss außerdem mit dem Geschlecht des Ausdrucks übereinstimmen.
Der Typ kann bei der Deklaration weggelassen werden.

Beispiele:

- `eine Zahl X ist 100`
- `die Summe ist X + 5`
- `die Zeichenfolge Beschreibung ist "dunkel, groß und blau"`

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

### wenn-dann-sonst-Ausdruck
`wenn Bedingung dann Ausdruck sonst Ausdruck`

Beispiel:

`wenn X gleich 42 dann "Die Antwort auf alles" sonst "etwas anderes"`

Wenn die Bedingung zutrifft dann wird der erste Ausdruck zurückgegeben, sonst der zweite Ausdruck.
Es ist wichtig, dass die Typen der beiden Ausdrücke übereinstimmen.

### solange Schleife
```
solange Bedingung:
  Sätze .
```

Solange die Bedingung zutrifft, werden die Sätze ausgeführt.

### für jede Schleife
```
für (jeder | jede | jedes) Nomen in Ausdruck:
  Sätze .
```
Für jedes Element in dem iterierbaren Objekt, wird die Schleife einmal ausgeführt, wobei
das Element an den Namen gebunden wird.

Beispiel:

```
für jede Zahl von 1 bis 10:
  drucke Zahl.
```

### Abbrechen oder Fortfahren einer Schleife
Das Schlüsselwort `abbrechen` dient zur sofortigen Beendigung einer Schleife.
Das Schlüsselwort `fortfahren` springt zu nächsten Schleifen-Iteration.

### Definieren einer Funktion
```
definiere Verb [mit [Rückgabe Typ | Typ [Nomen]] {,Typ [Nomen]}]: 
  Sätze
  [zurück Ausdruck]
```
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
definiere (Artikel Nomen | Nomen/Nomen[/Nomen]) [als Typ] mit Plural Nomen: 
  [Nomen {, Nomen} als Typ {Nomen {, Nomen} als Typ}].
```
Beispiel:
```
definiere die Person mit Plural Personen:
    Nachname, Vorname als Zeichenfolge
    Alter als Zahl.
    
definiere Student/Studentin/Studi als Person mit Plural Studenten:
    Studiengang als Zeichenfolge
    Semester als Zahl.
```

### Instanziieren eines Objekts eines Typs
`Person [mit Nomen Ausdruck {, Nomen Ausdruck}]`

Beispiel:

`die Person Donald ist Person mit Vorname "Donald", Nachname "Duck"`

### Zugriff auf Felder eines Objekts
`Feld von Objekt`

Beispiel:

`Name von Person`

### Definieren einer Methode
```
definiere Verb für Typ [mit [Rückgabe Typ | Typ [Nomen]] {,Typ [Nomen]}]: 
  Sätze
  [zurück Ausdruck].
```

### Methodenaufruf
Parameter können entweder als Ausdrücke, wo dann die Reihenfolge die Bindung bestimmt (Reihenfolgen-Form) 
übergeben werden oder als eine Liste von Zuweisungen (Namensform). 
Es kann auch gemischt sein, wobei dann die Reihenfolgen-Form zuerst kommen muss
und dann die Namens-Form.

Parameter: `(Ausdruck {, Ausdruck}) | (Nomen Zuweisungsoperator Ausdruck {, Nomen Zuweisungsoperator Ausdruck})`

Methodenaufruf: `Objekt Verb [mit Parameter]

Beispiel:

`Person stellDichVor`

### Definieren einer Schnittstelle
`definiere Schnittstelle Nomen: {Verb [mit [Rückgabe Typ | Typ [Nomen]] {,Typ [Nomen]}]}.`

Eine Schnittstellendefinition besteht aus Methodensignaturen. Eine Schnittstelle wird automatisch für einen Typ
implementiert, wenn sie alle Methoden definiert. Eine Schnittstelle hat das Geschlecht `neutral`.

Beispiel:

```
definiere Schnittstelle Zeichenbares:
    zeichne mit Farbe
    skaliere mit Rückgabe Zahl.
```

### Typ-Alias
`alias Artikel Nomen ist Nomen`

Beispiel:

`alias das Alter ist Zahl`

### destrukturierende Zuweisung
Beispiel:
```
die Person ist Person mit Nachname="Peterson", Vorname="Hans",Alter=42
der Nachname, der Vorname, das Alter von Person
drucke Nachname, Vorname, Alter // Peterson Hans 42
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
Beispiel:

`die Studenten sind Personen Person mit Name="Lukas", Nachname="Test", Person mit Name="Finn", Nachname="XXX"`
