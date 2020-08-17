# Iteration 3

## hinzugefügte Features
- Vererbung
- Module
- überarbeitetes Importieren
- Schnittstellen (Adjektive)

## Vererbung
`Nomen BezeichnerP [als TypP] mit Eigenschaften]: Konstruktor.`

Eine Klasse kann von einer anderen Klasse erben. Dies bedeutet, dass alle Eigenschaften und Methoden der
Elternklasse auf die Kindklasse übertragen werden.
Bei der Objektinitialiserung müssen dann nicht nur die eigenen Eigenschaften, sondern auch die Eigenschaften
der Elternklasse initialisiert werden.
Methodendefinitionen der Elternklasse können in der Kindklasse überschrieben werden. 
Sie können einfach so überschrieben werden. Man braucht kein neues Schlüsselwort.
Um auf die Elternmethode der überschriebenen Methode zu verweisen, wird ein Block verwendet, der
mit dem Schlüsselwort `Super` startet.


Beispiel:

```
Nomen Person mit 
    der Zeichenfolge VorName, 
    der Zeichenfolge NachName,
    der Zahl Alter:

    dieser VolleName ist mein VorName + " " + meinen NachNamen
.

Verb für Person stell mich vor:
    schreibe die Zeile "Hallo, mein Name ist #{mein Vorname} #{mein Nachname}.\n" +
        "Ich bin #{mein Alter} Jahre alt."
.

Nomen Student als Person mit 
    der Zeichenfolge Studiengang,
    der Zahl Semester:
    
    dieser VolleName ist "#{unser VolleName} (Student)"
.

Verb für Student stell mich vor:
    Super: stell mich vor!
    schreibe die Zeile "Ich studiere #{mein Studiengang} im #{mein Semester}. Semester"
.

der Student ist ein Student mit 
    dem VorNamen "Lukas"
    dem NachNamen "Gobelet"
    dem Alter 22
    dem Studiengang "Informatik"
    dem Semester 8


Student: 
    schreibe die Zeile deinen VolleNamen // Lukas Gobelet (Student)
    stell dich vor
!
```


## Module

`Modul BezeichnerF: Definitionen.`

Ein Modul ist ein Behälter für Definitionen. In Ihm können Typen, Funktionen, Methoden, Konstanten und wiederum Module definiert werden.
Ein Modul ist dafür da Code zu organisieren und Namenskollisionen zu verhindern. Der Name des Moduls wird nämlich Teil des vollen Namen einer Definition.
Module können ineinander verschachtelt werden, aber ein Modul kann nur in dem globalen Bereich oder in anderen Modulen definiert werden. 
Um auf einen in einem Modul definierten Typen zu verweisen wird dann der doppelte Doppelpunkt `::` nach dem Modulnamen verwendet.

Beispiel:
```
Modul Zoo:
    Nomen Gehege:.

    Modul Tiere:

        Verb fütter das Tier: drucke die Zeichenfolge "Fütter " + das Tier als Zeichenfolge.
        
        Nomen Tier:.
        
        Modul Säuger:
            Nomen Duden(Pferd) als Tier:.
        .

        Modul Amphibien:
            Nomen Duden(Krokodil) als Tier:.
        .
    .
.

das Gehege ist ein Zoo::Gehege
das Pferd ist ein Zoo::Tiere::Säuger::Pferd

Zoo::Tiere::fütter das Pferd
```
Um aber nicht immer den ganzen Namen verwenden zu müssen kann das `verwende`-Schlüsselwort verwendet werden um innerhalb eines Modulnamens zu gehen.

Beispiele:
```
verwende Zoo
das Gehege ist ein Gehege
das Pferd ist ein Tiere::Säuger::Pferd
```

```
verwende Zoo::Tiere::Säuger
verwende Zoo::Tiere::Amphibien

das Pferd ist ein Pferd
das Krokodil ist ein Krokodil
```

## überarbeitetes Importieren
Momentan ist es so, dass `importieren` denn Compiler dazu veranlässt, die Datei nachdem die momentane Datei gelesen wurde ist zu parsen.
Außerdem werden nicht nur Definitionen sondern Sätze importiert.
Es soll so überarbeitet werden, dass nur noch Module importiert werden können.

## Schnittstellen (Adjektive)

`Adjektiv bezeichner: {Verb(TypP) Methode}.`

Eine Schnittstellendefinition besteht aus Methodensignaturen. Eine Schnittstelle wird automatisch für einen Typ
implementiert, wenn sie alle Methoden definiert. Der Name einer Schnittstelle ist ein Adjektiv oder Partizip.
Eine Schnittstelle hat alle Geschlechter. Bei der Variablendeklaration wird das Adjektiv oder Partizip nominalisiert, wenn
der selbe Typname verwendet wird. Für die Deklination (Flektion) von Adjektiven gibt es in der deutschen
Sprache feste [Regeln](https://deutsch.lingolia.com/de/grammatik/adjektive/deklination). Diese sollen vom Grammatik-Prüfer geprüft werden.
Vor einem Schnittstellen-Argument steht dann immer das Adjektiv der Schnittstelle.

Beispiel:

```
// Schnittstellen Definition
Adjektiv zeichenbar:
    Verb zeichne mich mit der Zeichenfolge Farbe
    Verb skaliere mich mit der Zahl.


// Funktion mit Schnittstelle als Parameter
Verb zeichne das Zeichenbare mit der Zeichenfolge Farbe: 
    Zeichenbare: zeichne mich mit der Farbe
.

Nomen Dreieck:.

// Methoden: implementiere zeichne von Zeichenbar
Verb für Dreieck zeichne mich mit der Zeichenfolge Farbe:
     // zeichne Dreieck
.

// Methoden: implementiere skaliere von Zeichenbar
Verb für Dreieck skaliere mich:
     // skaliere Dreieck
.

das Dreieck ist ein Dreieck

zeichne das zeichenbare Dreieck.
```


