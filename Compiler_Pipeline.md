# Die Compiler Pipeline

Die Pipeline definiert welche Schritte das Programm vom Programm-Code bis zum interpretierten Programm
durchläuft. Bei GermanScript besteht die Pipeline aus 8 Komponenten:
1. [Lexer](##1.-Lexer)
2. [Parser](##2.-Parser)
3. [Deklanierer](##3.-Deklanierer)
4. [Bezeichner](##4.-Bezeichner)
4. [Grammatik-Checker](##5.-Grammatik-Prüfer)
5. [Definierer](##6.-Definierer)
6. [Type-Checker](##7.-Type-Prüfer)
7. [Interpreter](##8.-Interpreter)

Der GermanScript-Code wird dann in dieser Reihenfolge durch die Pipeline geschickt.

`Code -> Lexer -> Parser -> Deklanierer -> Bezeichner -> Grammatik-Checker -> Definierer -> Type-Checker -> Interpreter`

Für jede dieser Komponenten wird anschließend beschrieben, welches Endergebnis erwartet wird, welche Anforderungen es gibt,
welche Fehler geworfen werden können und Hinweise für die Implementierung.

## 1. Lexer
#### Endergebnis:
Der German-Script Code ist in Tokens zerlegt.
#### Anforderungen:
Die Tokenisierung soll möglichst effizient sein.
#### Fehler:
#####SyntaxFehler 
Es wird ein ungültige Zeichensequenz erkannt.
#### Inplementierungs-Hinweise:
Lese den Quellcode Stück für Stück und kombiniere die Zeichen zu Tokens.


## 2. Parser
#### Endergebnis:
Es liegt ein AST (abstrakter Syntax-Tree) des Programms vor.
#### Fehler:
#####SyntaxFehler
Es wird eine ungültige Sequenz von Tokens erkannt.
#### Implementierungs-Hinweise:
Kombiniere die Tokens nach der Grammatik der German-Script-Sprache in die einzelnen semantischen
Bestandteile der Sprache.


## 3. Deklanierer
#### Endergebnis:
Es liegt eine Datenstruktur `Wörterbuch` vor, wo alle Bezeichner, die verwendet werden sollen mit dem Genus 
(Maskulinum, Femininum, Neutrum) und den 4 Fällen der deutschen Grammatik (Nominativ, Genitiv, Dativ, Akkusativ) im Singular 
und im Plural drin stehen.
#### Anforderungen:
Die Datenstruktur soll eine Methode `getDeklination(nomen: String): Deklination` bereitstellen, 
mit der man die ganze Deklination mit dem Genus für ein Nomen (das in irgendeiner Form stehen kann) zurückgibt.
Dies soll möglichst effizient geschehen.
#### Fehler:
##### Dudenfehler:
Das Wort konnte nicht automatisch im Duden nachgeschlagen werden weil:
- keine Internetverbindung besteht
- der Duden das Wort nicht kennt
#### Implementierungs-Hinweise:
Die Anweisung `Duden(Wort)` in GermanScript soll ein Wort...

Die Daten werden nach dem NominativS sortiert in einer Tabelle gespeichert.

| Genus | NominativS | GenitivS | DativS | AkkusativS | NominativP | GenitivP | DativP | AkkusativP |
| ----- | ---------- | -------- | ------ | ---------- | ---------- | -------- | ------ | ---------- |
| Maskulinum | Bauarbeiter | Bauarbeiter | Baum  | Baum | Bäume | Bäume | Bäumen | Bäume |
| Maskulinum | Baum | Baums | Baum  | Baum | Bäume | Bäume | Bäumen | Bäume |

## 4. Bezeichner
#### Endergebnis:
#### Fehler:
#### Schritte:

## 5. Grammatik-Prüfer
#### Endergebnis:
#### Fehler:
#### Schritte:

## 6. Definierer
#### Endergebnis:
#### Fehler:
#### Schritte:

## 7. Typ-Prüfer
#### Endergebnis:
#### Fehler:
#### Schritte:

## 8. Interpreter
#### Endergebnis:
#### Fehler:
#### Schritte: