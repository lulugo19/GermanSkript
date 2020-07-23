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

Die Operatoren Gleichheit `==` und Ungleichheit `!=` können bei allen Typen verwendet werden, um die Gleichheit
zu überprüfen. Alle anderen Operatoren können nur bei den Inbuild-Typen `Zahl`, `Liste`, `Boolean` verwendet werden.


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

### Variablen-Deklarationen
Variablen werden in GermanSkript über die *bestimmten Artikel* `der, die, das`
oder die *unbestimmten Artikel* `ein, eine, einige` und den Zuweisungswörtern `ist` oder `sind` deklariert.

Wenn man *die bestimmen Artikel* verwendet, werden unveränderliche Variablen deklariert,
die nicht erneut zugewiesen werden können. Variablen die mit *unbestimmten Artikeln* deklariert werden
können neu zugewiesen werden.

Das Zuweisungswort `sind` wird verwendet, wenn eine Liste deklariert wird und sonst wird immer das Zuweisungswort `ist`
verwendet.

```
die Zeichenfolge ist "Hallo Welt!"

ein Zahl ist
```

### Bedingungen

### Solange-Schleifen
```
solange Bedingung:
    // mache etwas
.
```

### Für Jede Schleifen

### Funktionen

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


