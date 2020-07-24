# GermanSkript

Eine interpretierte, objektorientierte, statisch typisierte Programmiersprache, die sich wie Deutsch schreibt.
> "Es hat mich wirklich erstaunt, wie gut sich die deutsche Sprache als Programmiersprache eignet." 
> 
>*Lukas Gobelet*

## Hallo Welt in GermanSkript
Das Hallo Welt in GermanSkript ist ziemlich simpel. Das hier ist alles, was man braucht:

*Datei: HalloWelt.gm*
```
schreibe die Zeile "Hallo Welt!"
```
`schreibe die Zeile` ist eine Funktion, die mit dem Argument "Hallo Welt!" aufgerufen wird.

## Ein GermanSkript Programm ausführen

### GermanSkript über Gradle ausführen
Um GermanSkript über Gradle auszuführen, muss in das Projekthauptverzeichnis gegangen werden
und folgender Befehl ausgeführt werden:
```
.\gradlew run --args="<Dateipfad>"
```

### GermanSkript als JAR ausführen
Im Repository ist eine meist aktuelle Version des GermanSkript-Compilers als auführbare JAR-Datei
vorhanden. Diese kann dann über `java` direkt ausgeführt werden:
```
java -jar GermanSkript-1.0-SNAPSHOT.jar <Dateipfad>
```

Um es ein bisschen kürzer zu machen, wurde dieser Befehl in eine Batch Datei mit dem
Namen `gms` geschrieben und man kann es folgendermaßen ausführen:
```
.\gms <Dateipfad>
```

### Die JAR neu erstellen
Um die JAR neu zu erstellen, muss folgende Gradle-Task ausgeführt werden:
```
.\gradlew clean jar
```
Die JAR wird dann in dem Ordner `./build/libs` erstellt.

## Die Sprache GermanSkript
**GermanSkript** befindet sich momentan noch in der Entwicklung. Man kann sie schon
verwenden, doch es wird sich wahrscheinlich noch einiges ändern.

Die ganze Spezifikation kann man [hier](./SPEC.md) einsehen. Diese ist jedoch noch ziemlich
in Bearbeitung und noch nicht vollständig.

GermanSkript wird iterativ entwickelt. Neue Sprachfeatures werden in Iterationen nach und nach hinzugefügt.
Für jede Iteration gibt es eine eigene Spezifikation, die auf die Gesamtspezifikation aufbaut und noch
einige Details hinzufügt.

Iterationen:
- [Iteration 0](iterationen/iter_0/SPEC.md)
- [Iteration 1](iterationen/iter_1/SPEC.md)
- [Iteration 2](iterationen/iter_2/SPEC.md)
- [Iteration 3](./iterationen/iter_3/SPEC.md)

Wenn du Interesse daran hast, wie die Architektur des GermanSkript-Compilers aussieht, kannst
du [hier](./CompilerPipeline.md) mehr Informationen zur Compiler-Pipeline finden.

### Typen

Folgende Typen sind in GermanSkript enthalten:

| Typ | Beschreibung | Beispielwerte |
| --- | ------------ | --------- |
|`Zeichenfolge`| jegliche Zeichenfolgen | `"Hallo"`, `"wie geht's"`, `"Welt"`|
|`Zahl`| jegliche reele Zahlen|`7,123`, `100.000`, `22.712,1298`|
|`Boolean`| Wahrheitswert mit nur zwei möglichen Werten |`wahr`, `falsch`|
|`Liste`| eine Liste von Werten |`einige Zahlen [1, 2, 3, 4, 5]`|

### Bezeichner in GermanSkript
Es gibt verschiedene Arten von Bezeichnern in GermanSkript.
Es gibt `Nomen`, `Symbole` und `Verben`. 

#### Nomen
Nomen sind echte deutsche Nomen und werden groß geschrieben.
Sie werden für Variablen und Klassennamen verwendet.
Da es sich um echte deutsche Wörter handelt, müssen die verschieden Formen des Nomens -
die 4 Fälle (Nominativ, Genitiv, Dativ, Akkusativ) im Singular sowie im Plural, sowie das Geschlecht (Genus) -
bekannt sein.

Hierfür gibt es die Deklinationsanweisung, welche ein Nomen bekannt macht.
Diese sieht z.B. für das Nomen `Buch` folgendermaßen aus:
```
Deklination Neutrum Singular(Buch, Buchs, Buch, Buch) Plural(Bücher, Bücher, Büchern, Bücher)
```
Nach dem Schlüsselwort `Deklination` kommt das Geschlecht des Nomens `Maskulinum` (männlich), `Femininum` (weiblich)
oder `Neutrum` (neutral). Anschließend kommen die 4. Fälle des Nomens im `Singular` und `Plural` in der Reihenfolge:
Nominativ, Gentiv, Dativ und Akkusativ.

Da Nomen-Deklinationen selbst für deutsche Muttersprachler manchmal etwas schwierig sind
und damit es bequemer ist, bietet GermanSkript die Möglichkeit Nomen automatisch im [Online-Duden](https://www.duden.de/)
nachzuschauen. Die Deklination von oben kann man dann einfach so schreiben:
```
Deklination Duden(Buch)
```
Beim Ausführen wird dann automatisch im Duden nachgeschlagen und die Anweisung durch die längere Form ersetzt.

##### Zusammengesetze Nomen
Nomen können aus anderen Nomen zusammengesetzt werden wofür man dann keine neue Deklinationsanweisung braucht.
Diese zusammengesetzten Nomen werden in `PascalCase` geschrieben. z.B. das Nomen `SachBuch`.

#### Symbole
Symbole werden für Variablennamen verwendet und werden komplett in Großbuchstaben geschrieben.
Sie brauchen keine Deklinationsanweisung. Sie können entweder alleine stehen, wobei sie dann automatisch das Geschlecht
Neutrum zugegordnet bekommen. Oder sie können am Ende eines `Nomens` stehen, wobei der Name des Nomens um das Symbol erweitert wird.


```
das X ist 6
schreibe die Zahl das X

die ZeichenfolgeHALLO ist "Hallo"
schreibe die ZeichenfolgeHALLO
```

#### Verben
Verben werden als Bezeichner für Funktionen und Methoden verwendet. Sie werden einfach in Kleinbuchstaben geschrieben.


### Operatoren
Jeder Operator hat neben einem Symbol auch noch eine Textrepräsentation, die stattdessen verwendet werden kann.
Umso höher die Bindungskraft, desto mehr bindet der Operator seine Operanden.

Die Operatoren bilden folgende Klassen:

| Klasse | Verwendung |
| ------ | ------------ |
| Arithemetisch | mathematische Operatoren |
| Logisch | um Booleans miteinander zu verketten  |
| Vergleich | Werte vergleichen |

Die Operatoren Gleichheit `=` und Ungleichheit `!=` können bei allen Typen verwendet werden, um die Gleichheit
zu überprüfen. Alle anderen Operatoren können nur bei den Inbuild-Typen `Zeichenfolge`, `Zahl`, `Liste`, `Boolean` verwendet werden.


| Funktion | Symbol | Text | Assoziativität | Bindungskraft | Klasse |
| -------- | ------ | ---- | -------------- | ------------- | ------ |
| Logisches Oder | <code>&#124;</code> | `oder `| links | 1 | Logisch |
| Logisches Und | `&` | `und` | links | 2 | Logisch |
| Gleichheit | `=` | `gleich` | links | 3 | Vergleich |
| Ungleichheit | `!=` | `ungleich` | links | 3 | Vergleich |
| Größer | `>` | `größer` | links | 3 | Vergleich |
| Kleiner | `<` | `kleiner` | links | 3 | Vergleich |
| Größer-Gleich | `>=` | `größer gleich` | links | 3 | Vergleich |
| Kleiner-Gleich | `<=` | `kleiner gleich` | links | 3 | Vergleich |
| Plus | `+` | `plus` | links | 4 | Arithmetisch |
| Minus | `-` | `minus` | links | 4 | Arithmetisch |
| Mal | `*` | `mal` | links | 5 | Arithmetisch |
| Geteilt | `/` | `durch` | links | 5 | Arithmetisch |
| Modulo | `mod` | `modulo` | links | 5 | Arithmetisch |
| Hoch | `^` | `hoch` | rechts | 6 | Arithmetisch |

Der plus-Operator (`+`) kann bei Zeichenfolgen, sowie bei Listen verwendet werden, um Zeichenfolgen, bzw. Listen zu verketten.

### Variablen-Deklarationen
Variablen werden in GermanSkript über die *bestimmten Artikel* `der, die, das`
oder die *unbestimmten Artikel* `ein, eine, einige` und den Zuweisungswörtern `ist` oder `sind` deklariert.
Variablen müssen in GermanSkript direkt bei der Deklaration initialisiert werden.

Wenn man *die bestimmen Artikel* verwendet, werden unveränderliche Variablen deklariert,
die nicht erneut zugewiesen werden können. Variablen die mit *unbestimmten Artikeln* deklariert werden
können jedoch neu zugewiesen werden.

Das Zuweisungswort `sind` wird verwendet, wenn eine Liste deklariert wird und sonst wird immer das Zuweisungswort `ist`
verwendet.

```
Deklination Femininum Singular(Welt) Plural(Welten)

// unveränderbare Variable die nicht erneut zugewiesen werden kann
die Welt ist "Hallo Welt"
schreibe die Zeile Welt     // gibt "Hallo Welt" aus

// veränderbare Variable die erneut zugewiesen kann
eine Zahl ist 10
// gibt 10 aus
schreibe die Zahl 
// weist Zahl neu zu
eine Zahl ist die Zahl plus 32
// gibt 42 aus
schreibe die Zahl 
```

### Variablen und Bereiche
Ein Bereich fängt in GermanSkript mit einem Doppelpunkt `:` an und endet mit einem einfachen Punkt `.`.

Bereiche werden in GermanSkript an verschiedenen Stellen wie bei Schleifen, Bedingungen und Funktionen verwendet.
Man kann aber auch einfach so ein Bereich erstellen, indem man einfach ein `:` schreibt. Dann etwas in den Bereich schreibt
und den Bereich mit einem `.` beendet.

Variablen sind nur innerhalb des Bereiches gültig, indem sie deklariert wurden sind. Von einem inneren Bereich können auf Variablen
des äußeren Bereichs zugegriffen werden, aber nicht anders herum.

Innerhalb eines Bereichs müssen verschiedene Variablen, unterschiedliche Namen haben. Jedoch kann in einem inneren Bereich
der Variablenname einer Variable des äußeren Bereichs wieder verwendet werden. Die äußere Variable wird dann überdeckt (*Shadowing*).

Beispiel:

```
Deklination Femininum Singular(Variable) Plural(Variablen)

eine Variable ist "Erste Variable"
// neuer Bereich
:
    schreibe die Zeile Variable // Erste Variable
    eine Variable ist "Erste veränderte Variable"
    schreibe die Zeile Variable // Erste veränderte Variable
    
    // hier wird eine neue Variable erstellt, die die aus dem äußeren Bereich überdeckt
    eine neue Variable ist "Zweite Variable"
    schreibe die Zeile Variable // Zweite Variable
.
schreibe die Zeile Variable // Erste veränderte Variable
```

Hier sieht man auch ein neues Schlüsselwort namens `neue`. 
Dieses braucht man nur, um eine neue veränderbare Variable mit dem gleichen Namen zu erstellen.
Da man unveränderbare Variablen nicht neu zuweisen kann, braucht man bei ihnen **nicht** das Schlüsselwort `neu`,
weil dann immer eine neue Variable erstellt wird.

### Bedingungen

Bedingungen erlauben es GermanSkript-Code nur auszuführen, wenn eine bestimmte 
Bedingung (ein Boolean: `wahr` oder `falsch`) zutrifft.

Wenn Vergleichsoperatoren wie `gleich`, `ungleich`, `kleiner`, `größer`, usw. verwendet werden, kann optional
hinter dem Vergleich das Wort `ist` kommen, sodass es sich mehr wie Deutsch liest.

```
die Zahl ist 42

wenn die Zahl gleich 3 ist:
  schreibe die Zeile "Alle guten Dinge sind drei!".
sonst wenn die Zahl gleich 42 ist:
  schreibe die Zeile "Die Antwort auf alles.".
sonst: schreibe die Zahl.

// es wird "Die Antwort auf alles." augegeben
```

### Solange-Schleifen

Solange-Schleifen sind äquivalent zu der `while`-Schleife anderer Programmiersprachen.

```
Deklination Maskulinum Singular(Zähler, Zählers, Zähler, Zähler) Plural(Zähler, Zähler, Zählern, Zähler)

ein Zähler ist 3
schreibe die Zeile "Countdown: "

solange der Zähler größer gleich 0 ist:
    schreibe die Zahl Zähler
    ein Zähler ist der Zähler - 1
.
schreibe die Zeile "Los!"

/*
Ausgabe:
Countdown:
3
2
1
0
Los!
*/
```
### Funktionen
Wie in anderen Programmierprachen, kann man in GermanSkript, Code in Funktionen auslagern und diese Funktionen immer
wieder aufrufen, wenn man sie braucht. Funktionen in GermanSkript können aus mehreren Wörtern bestehen. Die Parameternamen
werden zum Teil des Namens der Funktion. Parameter werden mit Präpositionen und/oder Kommas voneinander abgegrenzt. 

Das Syntax einer Funktionsdefinition ist:

```Verb[(Rückgabetyp)] bezeichner Parameter [Suffix]: (Sätze|intern).```

Parameter: `[Objekt] [Kommaliste(Präposition)]`

Um dies zu verdeutlichen hier einige Beispiele.

| Funktionsdefinition | RückgabeTyp | Funktionsaufruf Beispiel | vollständiger Funktionsname |
| ------------------- | ----------- | --------------- | --------------------------- |
| `Verb schreibe die Zeichenfolge Zeile:.` | keiner | `schreibe die Zeile "Hallo Welt"` | schreibe die Zeile |
| `Verb(Zahl) runde die Zahl ab:.` | `Zahl` | `runde die Zahl 3,14 ab` | runde die Zahl ab |
| `Verb(Verbindung) verbinde den Client mit der Zeichenfolge IP über die Zahl Port:.` | `Verbindung` | `verbinde den Client mit der IP "127.0.0.1" über den Port 80` | verbinde den Client mit der IP über den Port |
| `Verb begrüße die Zeichenfolge Person mit der Zeichenfolge Begrüßung, der Zeichenfolge Abschied nach der Zeichenfolge Anfang:.` | keiner | `begrüße die Person "Max" mit der Begrüßung "Hallo", dem Abschied "Bye" nach dem Anfang "An einem wunderschönen Tag..."` | begrüße die Person mit der Begrüßung, dem Abschied nach dem Anfang |


Hier ist noch ein anderers Beispiel. Die Definition und der Aufruf der [Fakultätsfunktion](https://de.wikipedia.org/wiki/Fakult%C3%A4t_(Mathematik)).
```
// Funktionsdefinition
Verb(Zahl) fakultät von der Zahl:
    wenn die Zahl gleich 0 ist: gebe 1 zurück.
    sonst: gebe die Zahl * (fakultät von der Zahl - 1) zurück.
.

// Funktionsaufruf
die Zahl ist fakultät von der Zahl 5
schreibe die Zeile "Die Fakultät von der Zahl 5 ist: " + die Zahl als Zeichenfolge
```

### Listen

Wenn man mehrere Werte des gleichen Typs hat, ist es oft praktisch diese in einer Liste zu speichern.
Um eine Liste zu erstellen braucht man den Plural des Typen wowie den Artikel `einige`.
In die eckigen Klammern kommen dann die Elemente der Liste. 

```
die Zahlen sind einige Zahlen[2, 3, 5, 7, 11, 13, 17]
schreibe die Zeile (die AnZahl der Zahlen) // 7
```

Um auf ein bestimmtes Element über einen nullbasierten Index zuzugreifen, wird
die Einzahl und dann wieder eckige Klammern verwendet.

```
schreibe die Zahl[0] // 2
schreibe die Zahl[1] // 3

eine Zahl ist 2
solange die Zahl kleiner als die AnZahl der Zahlen ist:
    schreibe die Zahl
    eine Zahl ist die Zahl minus 1
.
```


### Für-Jede-Schleifen

### Klassen (Nomen)

### Objektinitialisierung

### Methoden

## Roadmap
- weitere Sprachfeatures wie Module, Interfaces, Vererbung, ... (siehe [Iteration 3](./iterationen/iter_3/SPEC.md))
- Standardbibliothek erweitern
- Entwicklung eines Language Servers nach dem [Language Server Protocol](https://microsoft.github.io/language-server-protocol/)
, um Funktionalitäten wie Auto-Complete, Code-Navigation und Fehlermeldungen im Editor zu ermöglichen.
- Grafik-Bibliothek für GermanSkript erstellen
- Einsatzgebiete von GermanSkript erkunden: Kann es verwendet werden, um Kindern/Jugendlichen programmieren beizubringen?
- Andere Compiler-Targets evaluieren:
    - Java
    - Java Bytecode
    - Javascript
- Webseite für GermanSkript


