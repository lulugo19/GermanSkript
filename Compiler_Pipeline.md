# Die Compiler Pipeline

Die Pipeline definiert welche Schritte das Programm vom Programm-Code bis zum interpretierten Programm
durchläuft. Bei GermanScript besteht die Pipeline aus 8 Komponenten:
1. [Lexer](#lexer)
2. [Parser](#parser)
3. [Deklanierer](#deklanierer)
4. [Bezeichner](#bezeichner)
4. [Grammatik-Checker](#grammatik-prüfer)
5. [Definierer](#definierer)
6. [Type-Checker](#typ-prüfer)
7. [Interpreter](#interpreter)

Der GermanScript-Code wird dann in dieser Reihenfolge durch die Pipeline geschickt.

`Code -> Lexer -> Parser -> Deklanierer -> Bezeichner -> Grammatik-Checker -> Definierer -> Type-Checker -> Interpreter`

Für jede dieser Komponenten wird anschließend beschrieben, welches Endergebnis erwartet wird, welche Anforderungen es gibt,
welche Fehler geworfen werden können und Hinweise für die Implementierung.

Außerdem ist [hier](#bewertung-der-komplexität-der-komponenten) eine grobe Schätzung der Komplexität der Komponenten.

## Lexer
### Endergebnis:
Der German-Script Code ist in Tokens zerlegt.
### Anforderungen:
Die Tokenisierung soll möglichst effizient sein.
### Fehler:
#### SyntaxFehler 
Es wird ein ungültige Zeichensequenz erkannt.
#### Inplementierungs-Hinweise:
Lese den Quellcode Stück für Stück und kombiniere die Zeichen zu Tokens.


## Parser
### Endergebnis:
Es liegt ein AST (abstrakter Syntax-Tree) des Programms vor.
### Fehler:
#### SyntaxFehler
Es wird eine ungültige Sequenz von Tokens erkannt.
### Implementierungs-Hinweise:
Kombiniere die Tokens nach der Grammatik der German-Script-Sprache in die einzelnen semantischen
Bestandteile der Sprache.


## Deklanierer
### Endergebnis:
Es liegt eine Datenstruktur `Wörterbuch` vor, wo alle Bezeichner, die verwendet werden sollen mit dem Genus 
(Maskulinum, Femininum, Neutrum) und den 4 Fällen der deutschen Grammatik (Nominativ, Genitiv, Dativ, Akkusativ) im Singular 
und im Plural drin stehen.
### Anforderungen:
Die Datenstruktur soll eine Methode `getDeklination(nomen: String): Deklination` bereitstellen, 
mit der man die ganze Deklination mit dem Genus für ein Nomen (das in irgendeiner Form stehen kann) zurückgibt.
Dies soll möglichst effizient geschehen.
### Fehler:
#### Dudenfehler:
Das Wort konnte nicht automatisch im Duden nachgeschlagen werden weil:
- keine Internetverbindung besteht
- der Duden das Wort nicht kennt
### Implementierungs-Hinweise:

#### Hinzufügen der deklinierten Nomen
Es müssen alle Deklinations-Anweisungen durchlaufen werden.
Die Deklinationen müssen dem Wörterbuch hinzugefügt werden.
Falls `Duden(Wort)` verwendet wurde, muss im Online-Duden nachgeschaut werden.

Für Interfaces müssen die `Adjektive` auch als Nomen deklaniert werden. Dafür gibt es feste [Regeln](https://deutsch.lingolia.com/de/grammatik/adjektive/deklination).

#### Wie sieht die Datenstruktur `Wörterbuch` aus?
Die Daten werden nach dem NominativS sortiert in einer Tabelle gespeichert.

| Genus | NominativS | GenitivS | DativS | AkkusativS | NominativP | GenitivP | DativP | AkkusativP |
| ----- | ---------- | -------- | ------ | ---------- | ---------- | -------- | ------ | ---------- |
| Maskulinum | Baum | Baums | Baum  | Baum | Bäume | Bäume | Bäumen | Bäume |
| Neutrum | Baumhaus | Baumhauses | Baumhaus | Baumhaus | Baumhäuser | Baumhäuser | Baumhäusern | Baumhäuser |
| Femininum | Dose | Dose | Dose | Dose | Dosen | Dosen | Dosen | Dosen |
| Maskulinum | Keks | Keks | Keks | Keks | Kekse | Kekse | Keksen | Kekse |
| Femininum | Uhr | Uhr | Uhr | Uhr| Uhren | Uhren | Uhren | Uhren |

Jetzt kann binäre Suche verwendet werden, um das Wort über den Nominativ zu suchen und auch um es einzufügen. 
Die Laufzeit von binärer Suche beträgt: `O(log(n))` was gut genug ist.

```
suche Nomen Bäume:
min = 0, max = 5
avg = (min + max) / 2 = 2
Dose > Bäume => max = avg
avg = (min + max) / 2 = 1
Baumhaus > Bäume => max = avg
avg = (min + max) / 2 = 0
*Bäum*e passt
```

Aber was ist, wenn `Baumhaus` gesucht wird und der Algorithmus auf `Baum` landet?

```
suche Nomen Baumhaus:
...
*Baum*haus passt aber es gibt keinen Eintrag für Baumhaus
=> Baumhaus muss hinter Baum kommen, also setze min = avg
```
## Bezeichner
### Endergebnis:
Allen Bezeichnern (außer den freien Bezeichnern für Module und Konstanten) wurde die grammatische Form (Genus,Fälle,Singular/Plural) zugeordnet.
Außerdem sind alle Bezeichner normalisiert (nominativiert) indem bei jedem Bezeichner der Nominativ (im Singular oder im Plural) dabeisteht.
### Fehler:
#### Unbekanntes Wort
Wenn ein Bezeichner nicht im `Wörterbuch` ist, wird dieser Fehler ausgelöst. Der Benutzer wird aufgefordert das Wort
mit der Deklinationsanweisung `Deklination ...` zu deklinieren.
### Implementierungs-Hinweise:
Besuche alle Bezeichner des ASTs, setze die Variable `form`, die Variable `nominativ` und die Variable `numerus` des Bezeichners, indem
das Wort im `Wörterbuch` nachgeschlagen wird. Speicher schon bekannte Formen in einer Hashmap zwischen, sodass das `Wörterbuch` nur abgefragt
werden muss, wenn die Form des Wortes noch nicht bekannt ist.
## Grammatik Prüfer
### Endergebnis:
Die deutsche Grammatik aller Anweisungen ist geprüft.
### Fehler:
#### Grammatik-Fehler:
Wenn die Grammatik falsch ist, wird ein Fehler geworfen. In der Fehlermeldung ist enthalten, wie es richtig heißen würde.
### Implementierungs Hinweise:
Prüfe alle:
- Variablendeklarationen/-zuweisungen
  - der Artikel und der Typ muss im Nominativ stehen
  - der Artikel muss zum Genus des Typen passen
- Funktionsaufrufe und Funktionsdefinitionen
  - der Artikel mit dem Objekt muss im Akksuativ stehen und der Genus des Artikels muss übereinstimmen
  - Präpositionen: der Artikel mit dem Typ muss zur Präposition passen und der Genus des Artikels muss übereinstimmen
- ...
## Definierer
### Endergebnis:
Alle Definitionen sind registiert.
### Fehler:
#### Doppelte Definition:
Wenn der selbe Typ (Nomen), Funktionen/Methoden (Verb), Interface (Adjektiv) oder Alias schon definiert sind.
### Implementierungs Hinweise:
#### Die Typen der Parameter werden der Reihenfolge nach in einer Liste gespeichert

#### Wann gilt eine Funktion als doppelt definiert?
Über den vollständigen Namen einer Funktion, kann sie eindeutig identifiziert werden. 
Der vollständige Name besteht aus: `[für Typ:] Verb [Artikel + Parametername] [Präpositionen mit Artikel + Parametername] [Suffix]`.
Der Rückgabetyp gehört **nicht** zum vollständigen Namen der Funktion.

Beispiele:

| Funktions-/Methodendefinition | Signatur | Typ-Liste |
| ----------------------------- | -------- | --------- |
| `Verb schreibe die Zeichenfolge Zeile` | `schreibe die Zeile` | Zeichenfolge |
| `Verb schreibe die Zeichenfolge` | `schreibe die Zeichenfolge`| Zeichenfolge |
| `Verb(Verbindung) für Sockel verbinde mich mit der Zeichenfolge IP über die Zahl Port` | `für Sockel: verbinde mich mit der IP über den Port` | Zeichenfolge, Zahl |
| `Verb für Liste füge das Element hinzu` | `für Liste: füge das Element hinzu` | Element |

## Typ Prüfer
### Endergebnis:
Die Typen aller Variablen sind bekannt (inferiert), alle Typen sind überprüft und stimmen überein.
### Fehler:
#### Typen stimmen nicht überein
### Implementierungs-Hinweise:
Prüfe:
  - Variablendeklarationen
    - wenn Variable mit Typ ist, dann muss der Typ gleich der Typ des Ausdrucks auf der rechten Seite sein
    - wenn Variable ohne Typ ist, muss sie den Typen des Ausdruck übernehmen
  - Funktionsaufrufe
    - Typen der Argumente müssen mit den Typen der Funktionsparameter übereinstimmen
    
Deklarierte Variablen werden in einer Hashmap gespeichert. Bereiche müssen beachtet werden.

## Interpreter
### Endergebnis:
Das Programm ist ausgeführt wurden.
### Fehler:
#### Division durch Null
#### Index Out of Bounce
### Implementierungs-Hinweise:
Alle Sätze müssen ausgeführt werden...

## Bewertung der Komplexität der Komponenten

Bewertung: 0 bis 5 Plusse `+`

0. Sprachspezifikation(++) -> Lukas
1. Lexer () -> Lukas
2. Parser (+++) -> Finn
3. Deklanierer und Online-Duden (++++) -> Lukas
4. Bezeichner (+) -> Finn
5. Grammatik-Prüfer (++) -> Finn
6. Definierer (++) -> Lukas
7. Typ-Prüfer (+++) -> Finn
8. Interpreter (+++) -> Lukas
