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
- [Iteration 4](./iterationen/iter_4/SPEC.md)
- [Iteration 5](./iterationen/iter_5/SPEC.md)

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

// es wird "Die Antwort auf alles." ausgegeben
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

### Zeichenfolgen

Zeichenfolgen (Literale) werde in GermanSkript zwischen den Anführungszeichen `" "` geschrieben.
Mit dem `+`-Operator kann man Zeichenfolgen aneinander ketten.

```
die Welt ist " Welt"
die Zeichenfolge ist "Hallo " + die Welt
```

#### Zeichenfolge-Interpolation
Bei längeren Zeichenfolgen, in denen mehrere Variablen vorkommen, kann die Verkettung von
Zeichenfolgen über den `+`-Operator schnell unübersichtlich werden. Deshalb bietet GermanSkript
die Zeichenfolgen-Interpolation an, bei der man Ausrücke direkt in Zeichenfolgen einbetten kann.
Diese startet innerhalb einer Zeichenfolge mit `#{` und endet mit `}`.

*ohne Zeichenfolgen-Interpolation*
```
die ZahlX ist 5
die ZahlY ist 10
das Ergebnis ist die ZahlX + die ZahlY
schreibe die Zeile die ZahlX als Zeichenfolge + " + " 
    + die ZahlY als Zeichenfolge + " = " 
    + das Ergebnis als Zeichenfolge
```

*mit Zeichenfolgen-Interpolation*
```
die ZahlX ist 5
die ZahlY ist 10
das Ergebnis ist die ZahlX + die ZahlY
schreibe die Zeile "#{die ZahlX} + #{die ZahlY} = #{das Ergebnis}"
```

### Listen

Wenn man mehrere Werte des gleichen Typs hat, ist es oft praktisch diese in einer Liste zu speichern.
Um eine Liste zu erstellen braucht man den Plural des Typen wowie den Artikel `einige`.
In die eckigen Klammern kommen dann die Elemente der Liste. 

```
die Zahlen sind einige Zahlen[2, 3, 5, 7, 11, 13, 17]
schreibe die AnZahl der Zahlen // 7
```

Um auf ein bestimmtes Element über einen nullbasierten Index zuzugreifen, wird
der Singular und dann wieder eckige Klammern verwendet.

```
schreibe die Zahl[0] // 2
schreibe die Zahl[1] // 3

ein Index ist 2
solange der Index kleiner als die AnZahl der Zahlen ist:
    schreibe die Zahl[Index]
    ein Index ist der Index plus 1
.
```

### Für-Jede-Schleifen
Für jede Schleifen, liefern eine syntaktisch sehr simple Möglichkeit über alle Elemente
einer Liste zu durchlaufen. Nach den Schlüsselwörter `für jede` kommt das Singular
der Listenvariable.

```
die Zahlen sind einige Zahlen[2, 3, 5, 7, 11, 13, 17]

für jede Zahl:
    schreibe die Zahl
.
```

Man kann es auch ohne den Singular und mit einem selbst gewählten Bezeichner machen. 
Aber dann braucht man noch das Schlüsselwort `in`.

```
für jedes X in den Zahlen:
    schreibe die Zahl X
.
```

Man kann die Liste auch direkt nach dem `in` als Ausdruck stehen haben.
```
für jede Zahl in einigen Zahlen[2, 4, 8, 16, 32, 64]:
    schreibe die Zahl
.
```

### Klassen (Nomen)

Klassen kapseln Daten und Methoden, die auf diesen Daten operieren.
Die Daten einer Klasse werden in GermanSkript Eigenschaften genannt.
Methoden sind unabhängig von der Klassendefinition. Dazu kommen wir später.

Eine Klassendefinitionen startet mit dem Schlüsselwort `Nomen`. Anschließend kommt
der Klassenname, welcher ein deutsches Nomen sein muss. Dann kommen die Klasseneigenschaften und
der Konstruktor. Hier ist wieder die Unterscheidung zwischen unveränderlichen Eigenschaften
(*bestimmte Artikel*) und veränderlichen Eigenschaften (*unbestimmte Artikel*) wichtig.

Innerhalb des Konstruktors kann man weitere Klasseneigenschaften definieren. Dies geschieht wie eine
normale Variablendeklaration nur mit den Demonstrativpronomen (`dieser`, `diese`, `dieses`)
für unveränderbare Eigenschaften und (`jener`, `jene`, `jenes`) für veränderbare Eigenschaften.

Auf die eigenen Eigenschaften kann innerhalb des Konstruktors oder in Methoden mit dem Personalpronomen `mein`
zugegriffen werden.

```
Deklination Femininum Singular(Person) Plural(Personen)
Deklination Maskulinum Singular(Name, Namens, Namen, Namen) Plural(Namen)
Deklination Neutrum Singular(Alter, Alters, Alter, Alter) Plural(Alter)

Nomen Person mit
    der Zeichenfolge VorName,
    der Zeichenfolge NachName,
    einer Zahl Alter:

    // weitere Eigenschaft
    dieser Name ist "#{mein VorName} #{mein NachName}"
    schreibe die Zeile "Die Person #{mein Name} (#{mein Alter} Jahre alt) wurde erstellt!"
.
```

Ein Objekt der Klasse wird dann folgendermaßen erstellt. 
Alle Eigenschaften müssen in der richtigen Reihenfolge angegeben werden.

```
die Person ist eine Person mit
    dem VorNamen "Lukas", 
    dem NachNamen "Gobelet", 
    dem Alter 22
```

Von außen kann man nun auf die Eigenschaften des Objekts mit dem Genitiv drauf zugreifen.
Man hat von außen nur lesenden Zugriff auf die Eigenschaften und kann diese nicht verändern.

```
der Name ist der Name der Person
schreibe die Zeile Name // Lukas Gobelet

die Zahl ist das Alter der Person
schreibe die Zahl // 22
```

Innerhalb einer Klasse kann man die Eigenschaften jedoch über `mein` ändern, wenn diese als veränderbar
deklariert wurden sind.

### Implementierung einer Klasse
Zu einer Klasse kann noch mehr als der Konstruktor gehören. 

Nämlich:
- Methoden (Verben für Klassen)
- berechnete Eigenschaften
- Konvertierungen

Diese Dinge werden alle in einem besondern Bereich, dem *Implementierungsbereich* definiert.
Der Implementierungsbereich für die Klasse `Person` sieht folgendermaßen aus:
```
Implementiere die Person:
    // hier werden Methoden, berechnete Eigenschaften und Konvertierungen definiert
.
```

In den folgenden Abschnitten wird darauf eingegangen, 
wie genau Methoden, berechnete Eigenschaften und Konvertierungen definiert werden.

#### Methoden

Methoden werden genau so wie Funktionen definiert werden. Innerhalb einer Methode hat man Zugriff
auf die Eigenschaften der Klasse über das Possesivpronomen `mein`. Für Methodenaufrufe der eigenen Klasse
braucht man außerdem innerhalb der Klasse keinen Nachrichtenbereich.

```
Implementiere die Person:
    Verb werde älter:
        mein Alter ist mein Alter plus 1 
    .
.

```

Es können auch Methoden mit dem Reflexivpronomen `mich` als Objekt definiert werden.
Diese spielen eine spezielle Rolle. Dazu kommen wir später.

```
Deklination Femininum Singular(Begrüßung) Plural(Begrüßungen)

implementiere die Person:
    Verb begrüße mich mit der Zeichenfolge Begrüßung:
        schreibe die Zeile "#{Begrüßung} #{mein VorName}!"
    .
.
```

Um die Methoden aufzurufen gibt es die sogenannten Nachrichtenbereiche.
Innerhalb eines Nachrichtenbereichs, kann eine Methode genauso wie eine Funktion
aufgerufen werden. Außerdem kann man innerhalb eines Nachrichtenbereiches mit dem
Personalpronomen `dein` auch ohne den Genitiv auf die Eigenschaften eines Objekts zugreifen.

Nachrichtenbereiche starten mit dem Objekt, dann mit einem `:`, und enden mit einem Ausrufezeichen `!`, 
anstatt wie normale Bereiche mit einem Punkt.

```
die Person ist eine Person mit
    dem VorNamen "Lukas", 
    dem NachNamen "Gobelet", 
    dem Alter 22

// beginne den Nachrichtenbereich
Person:
    schreibe die Zahl (dein Alter) // 22
    werde älter
    schreibe die Zahl (dein Alter) // 23
    
    begrüße dich mit der Begrüßung "Hey" // Hey Lukas!
!
```

Bei `begrüße dich` kann man sehen, dass das Reflexivpronomen `mich` in der Definition
in dem Nachrichtenbereich mit `dich` ersetzt wurde.

Über diesen rein kosmetischen Nutzen hinaus, haben Reflexivpronomen jedoch noch einen anderen Nutzen.
Methoden mit Reflexivpronomen kann man auch außerhalb eines Nachrichtenbereichs wie eine ganz normale
Funktion aufrufen. Dafür wird das Pronomen mit dem Namen der Klasse ersetzt:

```
// außerhalb des Nachrichtenbereichs
begrüße die Person mit der Begrüßung "Yieepiee" // Yieepiee Lukas!
```

Wenn es bei dieser Art des Aufrufes eine Funktion gibt, die genauso heißt wie die Methode, dann
wird diese vor der Methode bevorzugt.

#### berechnete Eigenschaften

Berechnete Eigenschaften einer Klasse sind Eigenschaften, die sich aus anderen Eigenschaften der Klasse
ergeben. 

In der Klasse `Person` wurde eine unveränderbare Eigenschaft `Name` erstellt, 
der sich aus dem Vornamen und dem Nachnamen zusammensetzt.
Was ist jetzt aber, wenn sich der Vorname oder der Nachname der Person ändert. 
Dann müsste man die Eigenschaft `Name` auch neu ändern.
Um dieses Problem zu lösen, kann man die Eigenschaft `Name` zu einer berechneten Eigenschaft machen:

```
Implementiere die Person:
    Eigenschaft(Zeichenfolge) Name:
        gebe meinen VorNamen + " " + meinen NachNamen zurück
    .
.
```

Auf die berechnete Eigenschaft kann man ganauso wie bei normalen Eigenschaften zugreifen.

```
die Person ist eine Person mit
    dem VorNamen "Lukas", 
    dem NachNamen "Gobelet", 
    dem Alter 22

// gibt Lukas Gobelet aus
schreibe die Zeile (der Name der Person) 
```

#### Konvertierungen

Für Konvertierungen gibt es in GermanSkript eine eigene Syntax. Hierfür schreibt man nach dem Ausdruck,
der konvertiert werden soll das `als`-Schlüsselwort und dann den Typ, in den der Ausdruck konvertiert
werden soll.

```
die Zahl ist 10
schreibe die Zeile (die Zahl * die Zahl) als Zeichenfolge // 100
```

Für die Standardtypen sind einige Konvertierungen definiert. Wie z.B. eine Zahl als Zeichenfolge,
oder eine Zeichenfolge als Boolean, jenachdem ob die Zeichenfolge leer ist oder nicht.

Darüber hinaus ist es möglich für Klassen eigene Konvertierungen zu definieren:

```
// Konvertierungsdefinition für die Klasse Person in den Typ Zeichenfolge
implementiere die Person:
    Als Zeichenfolge:
        gebe "#{mein Name} (#{mein Alter} Jahre alt)" zurück
    .
.

die Person ist eine Person mit
    dem VorNamen "Max",
    dem NachNamen "Mustermann",
    dem Alter 42

// gibt "Max Mustermann (42 Jahre alt) aus
schreibe die Zeile (die Person als Zeichenfolge)
```

### Importieren

Man kann sein GermanSkript-Programm über mehrere Dateien schreiben.
Hierfür gibt es die `importiere`-Anweisung. Es werden nur Definitionen importiert. 
Also Funktions-, Methoden-, Klassen- und Konvertierungsdefinitionen.
Das importierte Skript wird also nicht ausgeführt.

*Programm.gm*
```
importiere "./Person.gm"

eine Person ist eine Person mit
    dem VorNamen "Max"
    dem NachNamen "Mustermann"
    dem Alter 42
```

*Person.gm*
```
Deklination Femininum Singular(Person) Plural(Personen)
Deklination Maskulinum Singular(Name, Namens, Namen, Namen) Plural(Namen)
Deklination Neutrum Singular(Alter, Alters, Alter, Alter) Plural(Alter)

Nomen Person mit
    der Zeichenfolge VorName,
    der Zeichenfolge NachName,
    einer Zahl Alter:

    // weitere Eigenschaft
    dieser Name ist "#{mein VorName} #{mein NachName}"
    schreibe die Zeile "Die Person #{mein Name} (#{mein Alter} Jahre alt) wurde erstellt!"
.
```

### Standardbibliothek
Die [Standardbibliothek](./stdbib) enthält einige Funktionen und außerdem Methoden für Listen. 
Da GermanSkript noch mitten in der Entwicklung ist, wird diese hier nicht genauer aufgeführt.

Die bisher wichtigsten Funktionen in der Standardbibliothek sind das Schreiben in die Standardausgabe
und das Lesen von der Standardeingabe.

| Funktion | Beschreibung |
| -------- | ------------ |
| `schreibe die Zeichenfolge` | Schreibt eine Zeichenfolge in die Standardausgabe |
| `schreibe die Zeile` | Schreibt eine ganz Zeile in die Standardausgabe |
| `lese` | Liest eine ganze Zeile von der Standardeingabe. Gibt die Eingabe als Zeichenfolge zurück.

## Syntax-Highligthing
Syntax-Highlighting  wird über eine TextMate-Grammar
[hier](./syntax_highlighting/syntaxes/germanskript.tmLanguage.json) unterstützt.

Das Syntax-Highlighting wurde als VS-Code-Erweiterung erstellt.
Um es für VS-Code zu nutzen, kann der Ordner `./syntax-highlighting` in `%USER-PROFILE%/.vscode/extensions`
kopiert werden.
Außerdem kann man das Syntax-Highlighting auch in IntelliJ über das TextMate-Plugin importieren.
Bei anderen Editoren klappt es wahrscheinlich auch.

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


