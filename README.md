# GermanScript
Eine objektorientierte, streng typisierte Programmiersprache, die die deutsche Grammatik nachahmt.

## Syntax und Semantik
Die Syntax wird in einer Abwandlung der erweiterten Backus-Naur-Form beschrieben:

Syntax in eckigen Klammern ist optional `[Optional]` und Syntax in geschweiften Klammern 
kann ein oder mehrmals wiederholt werden `{Wiederholung}`.

### Groß- und Kleinschreibung
Groß- und Kleinschreibung ist wichtig bei GermanScript. Namen die festgelegt werden können, unterteilen sich in `Nomen`,
die **groß** und `Verben` die **klein** geschrieben werden.
`Nomen` werden bei [Variablendeklarationen](###Deklaration-von-Variablen) und [Klassendefinitionen](###Definition-von-Klassen)
und `Verben` bei der Definition von [Funktionen](###Definition-von-Funktionen) und Methoden verwendet.

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
- Zuweisungsoperator: `=` oder `ist`
- Vergleichsoperator: `==` oder `gleich`
- Größer-Operator: `>` oder `größer`
- Kleiner-Operator: `<` oder `kleiner`
- Größer-Gleich-Operator: `>=` oder `größer gleich`
- Kleiner-Gleich-Operator: `<=` oder `kleiner gleich`
- Plus: `+` oder `plus`
- Minus: `-` oder `minus`
- Mal: `*` oder `mal`
- Durch: `/` oder `durch`
- Hoch: `^` oder `hoch`

Alle Operatoren außer dem Zuweisungsoperator können für jede Klasse definiert werden. Siehe Überladung von Operatoren.

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
für (jeder | jede | jedes) Nomen von Ausdruck:
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

### Definieren einer Klasse
`definiere (Artikel Nomen | Nomen/Nomen[/Nomen]) [als Typ] mit Plural Nomen: [Nomen als Typ {,Nomen als Typ}.]`
Beispiel:
```
definiere die Person mit Plural Personen:
    Nachname, Vorname als Zeichenfolge
    Alter als Zahl.
    
definiere Student/Studentin/Studi als Person mit Plural Studenten:
    Studiengang als Zeichenfolge,
    Semester als Zahl.
```

### Instanziieren eines Objekts einer Klasse
`Person [mit Nomen Ausdruck {, Nomen Ausdruck}]`

Beispiel:

`die Person Donald ist Person mit Vorname "Donald", Nachname "Duck"`

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

Parameter: `[Ausdruck {, Ausdruck}][Nomen Zuweisungsoperator Ausdruck {, Nomen Zuweisungsoperator Ausdruck}]`

Methodenaufruf: `Objekt Verb [mit Parameter]

Beispiel:

`Person stellDichVor`


`Verb Objekt [mit Parameter]!`

Beispiel:

`Sortiere Liste! `

### Überladen eines Operators

### Definieren einer Schnittstelle
`definiere Schnittstelle Nomen: {Verb [Verb!] [mit [Rückgabe Typ | Typ [Nomen]] {,Typ [Nomen]}]}`

Eine Schnittstellendefinition besteht aus Methodensignaturen. Eine Schnittstelle wird automatisch für eine Klasse
implementiert, wenn sie alle Methoden definiert.
