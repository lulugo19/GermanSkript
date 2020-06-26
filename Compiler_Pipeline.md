# Die Compiler Pipeline

Die Pipeline definiert welche Schritte das Programm vom Programm-Code bis zum interpretierten Programm
durchläuft. Bei GermanScript besteht die Pipeline aus 8 Komponenten:
1. [Lexer](##Lexer)
2. [Parser](##Parser)
3. [Deklanierer](##Deklanierer)
4. [Bezeichner](##Bezeichner)
4. [Grammatik-Checker](##Grammatik-Prüfer)
5. [Definierer](##Definierer)
6. [Type-Checker](##Typ-Prüfer)
7. [Interpreter](##Interpreter)

Der GermanScript-Code wird dann in dieser Reihenfolge durch die Pipeline geschickt.

`Code -> Lexer -> Parser -> Deklanierer -> Bezeichner -> Grammatik-Checker -> Definierer -> Type-Checker -> Interpreter`

Für jede dieser Komponenten wird anschließend beschrieben, welches Endergebnis erwartet wird, welche Anforderungen es gibt,
welche Fehler geworfen werden können und Hinweise für die Implementierung.

## Lexer
### Endergebnis:
Der German-Script Code ist in Tokens zerlegt.
### Anforderungen:
Die Tokenisierung soll möglichst effizient sein.
### Fehler:
##### SyntaxFehler 
Es wird ein ungültige Zeichensequenz erkannt.
#### Inplementierungs-Hinweise:
Lese den Quellcode Stück für Stück und kombiniere die Zeichen zu Tokens.


## Parser
### Endergebnis:
Es liegt ein AST (abstrakter Syntax-Tree) des Programms vor.
### Fehler:
##### SyntaxFehler
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
##### Dudenfehler:
Das Wort konnte nicht automatisch im Duden nachgeschlagen werden weil:
- keine Internetverbindung besteht
- der Duden das Wort nicht kennt
### Implementierungs-Hinweise:

#### Hinzufügen der deklinierten Nomen
Es müssen alle Deklinations-Anweisungen durchlaufen werden.
Die Deklinationen müssen dem Wörterbuch hinzugefügt werden.
Falls `Duden(Wort)` verwendet wurde, muss im Online-Duden nachgeschaut werden.

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
Dose < Bäume => max = avg
avg = (min + max) / 2 = 1
Baumhaus < Bäume => max = avg
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
### Fehler:
### Schritte:

## Grammatik Prüfer
### Endergebnis:
### Fehler:
### Schritte:

## Definierer
### Endergebnis:
### Fehler:
### Schritte:

## Typ Prüfer
### Endergebnis:
### Fehler:
### Schritte:

## Interpreter
### Endergebnis:
### Fehler:
### Schritte:
