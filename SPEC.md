# GermanSkript
Eine objektorientierte, streng typisierte Programmiersprache, die sich wie die deutsche Sprache schreibt.

## Syntax und Semantik
Die Syntax wird in einer Abwandlung der erweiterten Backus-Naur-Form beschrieben:

Syntax in eckigen Klammern ist optional `[Optional]` und Syntax in geschweiften Klammern 
kann ein oder mehrmals wiederholt werden `{Wiederholung}`. 
Außerdem ist die Komma-seperierte Kommaliste eine häufige Syntax in unserer Sprache.
Das Pattern Dafür ist `Ding {, Ding}`. Hierfür verwenden wir die Abkürzung `Kommaliste(Ding)`.

### Groß- und Kleinschreibung
Groß- und Kleinschreibung ist wichtig bei GermanSkript. Namen die festgelegt werden können, sogenannte Bezeichner können groß oder klein geschrieben werden.
Für große Bezeichner verwenden wir die Syntax `Bezeichner` und für kleine Bezeichner die Syntax `bezeichner`.
Nomen werden bei der [Variablendeklarationen](#deklaration-von-variablen) und der [Klassendefinition](#definieren-einer-klasse)
Verben bei der Definition von [Funktionen](#definieren-einer-funktion) und [Methoden](#definieren-einer-methode) verwendet 
und Adjektive bei der Definition von [Schnittstellen](#definieren-einer-schnittstelle) verwendet.

#### Bezeichner
Bezeichner, also großgeschriebene Wörter werden in GermanSkript für Klassen- und Variablenbezeichner verwendet.
Die Bezeichner müssen aber vorher mit den 4 Fällen in Singular und Plural bekannt sein, damit diese verwendet werden können.

Die Form der Bezeichner is je nach Fall entscheidend. Im Folgendem wird folgende Syntax-Abkürzungen verwendet 
um die Form des Bezeichners anzugeben.

| Kasus (Fall) | Singular oder Plural | Singular | Plural |
| ------------ | -------- | ------ | -------------------- |
| Nominativ    | `BezeichnerN` | `BezeichnerNS` | `BezeichnerNP` |
| Genitiv      | `BezeichnerG` | `BezeichnerGS` | `BezeichnerGP` |
| Dativ | `BezeichnerD` | `BezeichnerDS` | `BezeichnerDP` |
| Akkusativ | `BezeichnerA` | `BezeichnerAS` | `BezeichnerAP` |

Um einen Bezeichner bekannt zu machen (zu deklinieren) wird folgende Syntax verwendet:

`Deklination Genus Singular(BezeichnerNS, BezeichnerGS, BezeichnerDS, BezeichnerAS) Plural(BezeichnerNP, BezeichnerGP, BezeichnerDP, BezeichnerAP)`.

Beispiel für `Kind`:

`Deklination Neutrum Singular(Kind, Kindes, Kind, Kind) Plural(Kinder, Kinder, Kindern, Kindern)`

Da es aber aufwendig werden kann, jeden Bezeichner selber zu deklinieren, kann auch automatisch im [Online-Duden]((https://www.duden.de/woerterbuch))
nachgeschaut werden. Dann wird folgende Syntax verwendet:

`Deklination Duden(BezeichnerN)`

Beispiel für `Kind`:

`Deklination Duden(Kind)`


Es gibt aber auch Bezeichner die diese 4 Fälle **nicht** brauchen. Diese werden für die Bezeichnung von [Modulen](#definieren-eines-moduls)
und [Konstanten](#definieren-einer-Konstante) und [Einheiten](#einheiten) verwendet und müssen nicht deklaniert werden.


### Artikel
Da GermanSkript ein Teil der deutschen Grammatik beinhaltet, spielt der Genus (das Geschlecht) eines Typs eine wichtige Rolle.
Außerdem ist auch noch die Form des Artikels wichtig, jenachdem in welchem Fall das Nomen verwendet wird.

| Kasus (Fall) | bestimmt / unbestimmt | Syntax-Abkürzung | Verwendung bei | Maskulinum | Femininum | Neutrum | Plural |
| ------------ | --------------------- | ---------------- | -------------- | ---------- | --------- | ------- | ------ |
| Nominativ  | bestimmt | `ArtikelNb` | Variablendeklaration bestimmt | der | die | das | die |
| Nominativ | unbestimmt | `ArtikelNu` | Variablendeklaration unbestimmt | ein | eine | ein | einige |
| Genitiv | bestimmt | `ArtikelGb` | Eigenschaftszugriff, Destrukturierung | des | der | des | der |
| Genitiv | unbestimmt | `ArtikelGu` |  | eines | einer | eines | einiger |
| Dativ  | bestimmt | `ArtikelDb` | Destrukturierung, Konstruktor, Argumente | dem | der | dem | den |
| Dativ  | unbestimmt | `ArtikelDu` |  | einem | einer | einem | einigen |
| Akkusativ  | bestimmt | `ArtikelAb` | Eigenschaftsdefinition, Aufzählungsdefinition, Methodenaufruf | den | die | das | die |
| Akkusativ  | unbestimmt | `ArtikelAu` | Typdefinition, Alias, Eigenschaftsdefinition, Aufzählungsdefinition, Methodenaufruf | einen | eine | ein | einige |


### Bereiche
Ein GermanSkript-Programm besteht aus mehreren Bereichen. Innere Bereiche können auf den Inhalt von äußeren Bereichen zugreifen, aber
äußere Bereiche können nicht auf den Inhalt von inneren Bereichen zugreifen.
Ein Bereich startet mit `:` und endet mit `.`.

### Sätze
Ein GermanSkript-Programm besteht aus mehreren Sätzen (im Programmierspachen-Jargon auch Statements genannt).
Sätze werden in GermanSkript mit einer neuen Zeile oder mit `;` getrennt.
Folgendes sind Sätze:
- Variablendeklaration
- Funktionsaufrufe
- Methodenaufrufe
- Schlüsselwörter wie `abbrechen` oder `fortfahren`
- `gebe zurück`-Anweisung in Funktionen oder Methoden

Außerdem gibt es noch Kontrollstrukturen, die auch zu den Sätzen zählen. Sie werden aber nicht mit `;` getrennt, 
sondern beginnen jeweils einen neuen Bereich.
Da wären:
- [Bedingungen](#bedingungen)
- Schleifen
     - [Solange-Schleife](#solange-schleife)
     - [Für-Jede-Schleife](#für-jede-schleife)
     
In der Syntax wird für keinen, einen oder mehrere Sätze, die Syntax `Sätze` verwendet.

### Ausdrücke
Ein Ausdruck ist alles was einer Variablen zugewiesen werden kann. Ausdrücke werden außerdem als Argumente bei
einem Funktions- oder Methodenaufruf übergeben. Im 
Folgendes sind Ausdrücke:
- Literale: Zeichenfolge, Zahlen, Boolsche Werte, Listen, Objekt
- Binäre Ausdrücke
- Variablen

die Ausdrücke gliedern sich in zwei Klassen, die bedeutend sind bei Funktions- und Methodenaufrufen sowie

ArtikelAb BezeichnerA1 (Variable | Literal) | ArtikelAu (Liste | Objektinstanziierung)

### Abbrechen oder Fortfahren einer Schleife
Das Schlüsselwort `abbrechen` dient zur sofortigen Beendigung einer Schleife.
Das Schlüsselwort `fortfahren` springt zu nächsten Schleifen-Iteration.


### Operatoren
Jeder Operator hat neben einem Symbol auch noch eine Textrepräsentation, die stattdessen verwendet werden kann.

Umso höher die Bindungskraft, desto mehr bindet der Operator seine Operanden.

Die Operatoren Gleichheit `=` und Ungleichheit `!=` können bei allen Typen verwendet werden, um die Gleichheit
zu überprüfen. Alle anderen Operatoren können nur bei den Inbuild-Typen `Zahl`, `Liste`, `Boolean` verwendet werden.
Das Überladen von Operatoren ist voerst nicht vorgesehen.

Die Operatoren bilden folgende Klassen:

| Klasse | Verwendung | Kasus (Fall) |
| ------ | ------------ | ------------ |
| Arithemetisch | mathematische Operatoren | Akkusativ |
| Logisch | um Booleans miteinander zu verketten | Akkusativ |
| Vergleich | Werte vergleichen | Akkusativ |

Der Kasus (Fall) ist bei Operatoren auch wichtig. 
Der erste Operand hat immer den Fall Nominativ und die Fälle der restliche Operanden 
sind je nach Klasse (siehe Tabelle).


#### Binäre Operatoren
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

#### Unäre Operatoren

##### Der Operator `nicht`:

`nicht` kann vor folgenden Operatoren verwendet werden, um diese umzukehren: `gleich`, `ungleich`, `größer`, `kleiner`, `größer gleich`, `kleiner gleich`.

```
nicht gleich => ungleich
nicht ungleich => gleich
nicht größer => kleiner gleich
nicht kleiner => größer gleich
nicht größer gleich => kleiner
nicht kleiner gleich => größer
```

##### Der Operator `minus`:

`minus` oder das Symbol `-` kann vor einer Zahl stehen, um diese zu negieren.
Bei Variablen muss nach dem `minus` der Akkusativ kommen.


### Variablen

#### Deklaration von Variablen
Für eine unveränderbare Variable: `ArtikelNb BezeichnerN Zuweisungsoperator AusdruckN`

Für eine veränderbare Variable: `ArtikelNu [neuer|neue|neues] BezeichnerN Zuweisungsoperator AusdruckN`

Variablen können auf zwei Art und Weisen deklariert werden. Für Variablen, die nicht erneut zugewiesen werden können
werden die bestimmten Artikel `der, die, das` verwendet. Und für Variablen die erneut zugewiesen werden können, werden
die unbestimmten Artikel `ein, eine, einige` verwendet. Der Artikel muss außerdem mit dem Genus und dem Numerus des Ausdrucks übereinstimmen.
Der Typ wird bei der Deklaration weggelassen werden und wird dann aus dem Ausdruck ermittelt.


#### Neuzuweisung von Variablen

`ArtikelNu BezeichnerN Zuweisungsoperator AusdruckN`

#### Verweis auf Variable
Um auf eine Variable zu verweisen, muss der bestimmte Artikel gefolgt von dem Namen der Variablen werden.
Dabei müssen Artikel und Name je nach dem Fall in der richtigen Form stehen.

Beispiele:

```
eine Zahl ist 100
die Summe ist die Zahl + 5
eine Zahl ist 100 + 10 // Neuzuweisung
die Summe ist 42 // Fehler, kann nicht neu zugewiesen werden

die Zeichenfolge Beschreibung ist "dunkel, groß und blau"
```

#### Shadowing von Variablen
In einem inneren Bereich kann eine neue Variable mit dem selben Namen, wie die äußere Variable erstellt werden, 
wobei sie die äußere Variable überschattet. Auf die äußere Variable kann jetzt nicht mehr zugregriffen werden.
```
die Zahl ist 5
:
    die Zahl ist 30 // kein Fehler
    schreibe die Zeichenfolge Zahl als Zeichenfolge // 30
.
schreibe die Zeichenfolge Zahl als Zeichenfolge // 5
```

Um eine veränderbare Variablen mit dem selben Namen in einem inneren Bereich neu zu erstellen gibt es das `neue` Schlüsselwort.
```
eine Zahl ist 5
:
    eine Zahl ist 12        // verändere die äußere Variable
    eine neue Zahl ist 30   // erstelle eine neue innere veränderbare Variable
    
    die Zahl ist 10         // Fehler: der Name Zahl ist schon in Gebrauch
.
```

### Bedingungen
```
wenn Bedingung:
  Sätze
{sonst wenn Bedingung: Sätze}
[sonst: Sätze] .
```

Bei Bedingungen ist das Besondere, dass bei den Vergleichsoperatoren `gleich`, `ungleich`, `kleiner`, `größer`, `kleiner gleich` danach immer ein `ist` kommen kann.

Beispiel:
```
wenn die Zahl gleich 3 ist:
  schreibe die Zeile "Alle guten Dinge sind drei!".
sonst wenn die Zahl gleich 42 ist:
  schreibe die Zeile "Die Antwort auf alles.".
sonst schreibe die Zahl
```

### Solange Schleife
`solange Bedingung: Sätze.`

Solange die Bedingung zutrifft, werden die Sätze ausgeführt.

### Für Jede Schleife
`für (jeden | jede | jedes) BezeichnerA in AusdruckAP: Sätze.`

Für jedes Element in dem iterierbaren Objekt, wird die Schleife einmal ausgeführt, wobei
das Element an den Namen gebunden wird.

Beispiel:

```
für jede Primzahl in einigen Zahlen [2, 3, 5, 7, 11, 13, 17, 19, 23]:
    schreibe die Zahl Primzahl
.
```

Vorschlag: syntaktischer Zucker

```
:
    das Iterierbare ist AusdruckAP
    Iterierbare:
        solange weiter:
            ArtikelPb BezeichnerA ist nächstes
            Sätze
        .
    !
.
```

### Definieren einer Funktion
`Verb[(Rückgabetyp)] bezeichner Parameter [Suffix]: (Sätze|intern).`

Parameter: `[Objekt] [Kommaliste(Präposition)]`

Objekte: `ArtikelAb TypA [NomenN]`

Präposition: `(PräpG | PräpD | PräpA | PräpAD) Kommaliste((ArtikelGb | ArtikelDb | ArtikelAb) (TypG | TypD | TypA) [BezeichnerP])`

PräpG: `angesichts, anhand, anlässlich, anstatt, anstelle, aufgrund, 
außerhalb, beiderseits, bezüglich, diesseits, infolge, innerhalb, 
jenseits, längs, links, mithilfe, oberhalb, rechts, unterhalb, statt, südlich, 
trotz, ungeachtet, unweit, während, wegen, westlich`

PräpD: `aus, außer, bei, binnen, entgegen, entsprechend, gegenüber, gemäß, mit, nach, nahe, samt, seit, zu, zufolge, zuliebe`

PräpA: `für, um, durch, entlang, gegen, ohne, wider`

PräpAD: `an, auf, hinter, in, neben, über, unter, vor, zwischen`

Die Präposition muss mit der Form des Artikels und des Typen übereinstimmen.

Beispiele:
```
Verb(Zahl) fakultät von der Zahl:
    wenn die Zahl gleich 0 ist: gebe die Zahl zurück. 
    sonst: gebe die Zahl mal die Zahl - 1 zurück.
```

```
Verb begrüße die Person mit der Zeichenfolge Begrüßung:
     schreibe die Zeile die Begrüßung + " " + der Name der Person.

Verb begrüße mit dem Namen nach der Uhrzeit:.
```

```
Verb stelle den Gegenstand her:
     "stelle " + Name des Gegenstands + " her...".
```

### Funktionsaufruf
`Verb [Argumente] [Suffix]`

Argumente: `[Objekt] [Kommaliste(Präposition)]`

Objekt: `ArtikelAb BezeichnerA1 (Variable | Literal) | ArtikelAu (Liste | Objektinstanziierung)`

Variable: `BezeichnerA2`

Literal: `Zahl | Zeichenfolge | Boolean`

Liste: siehe [Liste](#listen)

Objektinstanziierung: siehe [Objektinstanziierung](#instanziieren-eines-objekts-einer-klasse)

Präposition: `PräpGDA Kommaliste(ArtikelGDAb BezeichnerA1 [BezeicherA2 | Zahl |])`


Beispiele:
```
fakultät von 3

begrüße die Person mit der Begrüßung "Hey, wie geht's "

die Person Kumpel ist eine Person mit dem Namen="Fred"
die Begrüßung ist "Alter, was läuft?!"
begrüße den Kumpel mit der Begrüßung

begrüße eine Person mit dem Namen "Josuah" mit der Begrüßung "Hey, Josuah!"


der Tisch ist ein Gegenstand mit dem Namen "Tisch".
stelle den Gegenstand Tisch her
```

### Definieren einer Klasse

`Nomen BezeichnerP [als TypP] [mit Eigenschaften]: Konstruktor.`

Eigenschaften: `Kommaliste(ArtikelD TypD [BezeichnerP])`

#### Konstruktor

Konstruktor: `Sätze`

Der Konstruktor ist dafür da das Objekt zu initialisieren.
In dem Konstruktor können die Demonstativpronomen `diese` oder `jene`
verwendet werden um Eigenschaften zu erstellen, auf die man von außen nicht zugreifen kann.

Der Konstruktor ist eine spezielle [Methode](#definieren-einer-methode) und es gelten
die Regeln für Methoden.

Beispiel:
```
Nomen Person mit
    der Zeichenfolge Vorname,
    der Zeichenfolge Nachname,
    der Zahl Alter:

    dieser Name ist Vorname + " " + Nachname
    // jener Name ist Vorname + " " + Nachname
.

Nomen Student als Person mit 
    einer Zeichenfolge Studiengang,
    einer Zahl Semester:.
```

Vorschlag:

Es wäre cool wenn man anstatt die Fälle selbst anzugeben den Duden verwenden könnte.
Man würde eine HTTP-Anfrage an den Duden senden.

### Instanziieren eines Objekts einer Klasse
`Bezeichner [mit Kommaliste(ArtikelDb BezeichnerD [AusdruckD])`]


Beispiel:

`die Person Donald ist eine Person mit dem Vornamen "Donald", dem Nachnamen "Duck", dem Alter 42`

### Zugriff auf Eigenschaften eines Objekts
mit dem Genitiv: `Artikelb Eigenschaft ArtikelGb AusdruckG`

Beispiel: `der Name der Person`

### berechnete Eigenschaften einer Klasse
`Eigenschaft(Typ) BezeichnerN für Typ: Sätze.`

Eine berechnete Eigenschaft ist eine Eigenschaft die sich aus anderen Eigenschaften der Klasse ergibt.

Beispiel:

```
Eigenschaft(Zeichenfolge) Name für Person:
    gebe meinen VorNamen + " " meinen NachNamen zurück
.

die Person ist eine Person mit dem VorNamen "Max", dem NachNamen "Mustermann"
schreibe die Zeichenfolge (der Name der Person) // Max Mustermann
```

### Definieren einer Methode

`Verb[(Typ)] für Typ Verb [mir|mich] Parameter [Suffix]`

Das Verb einer Methode sollte im Imperativ stehen. Außerdem kann das Verb optional noch einen Suffix bekommen,
der dann bei dem Methodenaufruf am Ende stehen muss.

Innerhalb einer Methode kann man direkt ohne Methodenblock auf eigene Methoden zugreifen.
Auf die Eigenschaften des eigenen Objekts kann innerhalb einer Methode mit `mein` zugegriffen werden.

Beispiel:
```
Verb(Zeichenfolge) für Person stelle mich mit der Zeichenfolge Begrüßung, der Zeichenfolge LetzterSatz vor:
    zurück Begrüßung + ", " + "mein Name ist " + mein Name " und ich bin " + mein Alter " Jahre alt." + LetzterSatz.
```

### Methodenblock

`Bezeichner: Sätze!`

Um eine Methode aufzurufen gibt es den sogenannten Methodenblock. Man startet einen neuen Block mit dem Bezeichner
des Objekts, auf den man die Methode/n aufrufen möchte. Innerhalb des Blocks kann man jetzt die Methoden ganz normal wie Funktionen
aufrufen. Hat eine Funktion die gleiche Signatur wie eine Methode, wird die Funktion überschattet. Der Block endet diesmal nicht
mit einem `.` sondern einem `!`.

In Methodenblöcken kann auf Eigenschaften des Objekts mit `dein` zugegriffen werden. Wenn das Objekt eine Liste ist wird stattdessen `eure` verwendet.

Wenn in einer Methodendefinition `mir` (Dativ) oder `mich` (Akkusativ) verwendet wurde, wird diese bei allen Objekten mit `dir`
oder `dich` und bei Listen mit `euch` oder `euren` ersetzt.

Beispiel:

```
die Person Rick ist eine Person mit dem Vornamen "Rick", dem Nachnamen "Sanchez", dem Alter 70
Rick: stelle dich mit der Begrüßung "Woooobeeewoobeedubdub!", dem Nachwort "Rülps!" vor!
```

```
Verb(Verbindung) für Client verbinde mich:.
Verb für Client sende die Nachricht mit dem Flag:.

der Client ist Client mit ...
Client: verbinde dich!
        sende die Nachricht "Hallo Welt" mit dem Flag "X"!
```

### Definieren einer Schnittstelle
`Adjektiv bezeichner: {Verb(TypP) Methode}.`

Eine Schnittstellendefinition besteht aus Methodensignaturen. Eine Schnittstelle wird automatisch für einen Typ
implementiert, wenn sie alle Methoden definiert. Der Name einer Schnittstelle ist ein Adjektiv oder Partizip.
Eine Schnittstelle hat alle Geschlechter. Bei der Variablendeklaration wird das Adjektiv oder Partizip nominalisiert, wenn
der selbe Typname verwendet wird.

Beispiel:

```
Adjektiv zeichenbar:
    Verb zeichne mich mit der Farbe
    Verb skaliere mich mit der Zahl.
```

| Kasus (Fall) | Singular | Plural |
| ------------ | -------- | ------ |
| Nominativ    | Zeichenbare | Zeichenbaren |
| Genitiv      | Zeichenbarens | Zeichenbaren |
| Dativ | Zeichenbaren | Zeichenbaren |

```
// Funktion
Verb zeichne das Zeichenbare: 
    Zeichenbar: zeichne mich mit der Farbe "rot"
.

// Methoden: implementiere zeichne von Zeichenbar
Verb für Dreieck zeichne mich:
     // zeichen das Dreieck
.

// Methoden: implementiere skaliere von Zeichenbar
Verb für Dreieck skaliere mich:
     // skaliere das Dreieck
.

das Dreieck ist ein Dreieck

zeichne das zeichenbare Dreieck.
```


### Definieren einer Konstante
`Konstante BezeichnerF ist Literal`

Konstanten sind unveränderbar und können nur einmal zugewiesen werden. Nur Zahlen-, Zeichenfolgen- oder Boolean-Literale können einer Konstante zugewiesen werden.

Beispiel: `Konstante PI ist 3,14159265359`

### Closures
`etwas Bezeichner: Sätze.`

Closures funktionieren über die Schnittstellen (Adjektive). Wenn eine Schnittstelle nur eine einzige
Methode definiert, dann kann man für diese Schnittstelle ein Closure erstellen. 
Der Bezeichner, der nach dem Vornomen `etwas` kommt ist das nominalisierte Adjektiv der Schnittstelle.
Closures bilden einen neuen Typen.

```
Adjektiv klickbar:
    Verb klick mich
.

Verb registriere das Klickbare:
    Klickbare: klick mich!
.

eine Zahl ist 0
registriere etwas Klickbares:
    die Zahl ist die Zahl plus 1
    schreibe die Zeile "Ich wurde zum #{die Zahl}. angeklickt."
.
```


### Fehler-Handling

### Alias

`Alias BezeichnerP ist Typ`


Beispiel:

`Alias Alter ist Zahl`

### Definieren einer Aufzählung

Beispiel:

```
Alias Name ist Zeichenfolge

Aufzählung Ereignis mit dem Namen:
    Weihnachten mit dem Namen="Weihnachten",
    Ostern mit dem Namen="Ostern",
    Haloween mit dem Namen="Haloween",
    Geburtstag mit dem Namen="Geburstag",
.

das Ereignis ist das Ereignis Weihnachten
schreibe die Zeichenfolge der Name des Ereignisses // "Weihnachten"
```

### Destrukturierende Zuweisung

<strong>Kommt höchst wahrscheinlich nicht in die Sprache!!!</strong>

#### Destrukturierung von Objekten
Beispiel:
```
die Person ist eine Person mit dem NachNamen="Peterson", dem VorNamen="Hans", dem Alter=42, der Adresse (eine Adresse mit der Straße "Bla", dem Ort "Blub")
(der Nachname, ein Alter Geburtstagsalter, (die Straße) der Adresse) der Person
// der Nachname ist der Nachname der Person
// ein Alter Geburtstagalter ist das Alter der Person
// die Straße ist die Straße der Adresse der Person
das Alter ist das Alter + 1
 den Nachname, das Alter // Peterson 43
```

#### Destrukturierung von Listen
```
[die Person1, die Person2, einige RestlichePersonen...] sind die Personen[p1, p2, p3, p4, p5]
/*
Person1 = p1
Person2 = p2
RestlichePersonen = [p3, p4, p5]
*/
```

### Definieren eines Moduls
`Modul BezeichnerF: Definitionen.`
Ein Modul ist ein Behälter für Definitionen. In Ihm können Typen, Funktionen, Methoden, Konstanten und wiederum Module definiert werden.
Ein Modul ist dafür da Code zu organisieren und Namenskollisionen zu verhindern. Der Name des Moduls wird nämlich Teil des vollen Namen einer Definition.
Module können ineinander verschachtelt werden, aber ein Modul kann nur in dem globalen Bereich oder in anderen Modulen definiert werden. Um auf einen in einem Modul definierten Typen zu verweisen wird dann der doppelte Doppelpunkt `::` nach dem Modulnamen verwendet.

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
GermanSkript verfügt vorab über folgende Typen:

### Zahlen

`Zahl [Zahlenname | Prozent]`

Zahlen werden in der deutschen Schreibweise für Zahlen geschrieben. Das heißt vor dem `,` steht die Ganzzeil und nach
dem Komma die Teilzahl. Außerdem kann man bei der Ganzahl Punkte als Abtrennung der Tausender-Stellen verwendet werden.

Beispiel: `898.100.234,129123879`


#### Zahlendekorierer

Hinter einer Zahl kann ein Zahlendekorierer kommen, der der Zahl eine neue Bedeutung gibt.

##### Zahlenname

[Zahlennamen]([https://de.wikipedia.org/wiki/Zahlennamen]) stehen als Suffix hinter der Zahl und erhöhen die Zahl um eine bestimmte Zehnerpotenz.
Die Regel ist das die Zahl kleiner als die Zehnerpotenz sein muss. Wir nehmen nicht alle Zahlennamen rein sondern starten bei `Hundert`
und enden bei `Dezilliarde` (10^63).

```
200 Hundert => geht nicht
100 Tausend => funktioniert
1,8 Milliarden
```

##### Prozent

`Prozent` oder das Symbol `%` dekoriert eine Zahl und wandelt diese von Prozent in Dezimalschreibweise um.

```
10% gleich 0,1
0,35 Prozent gleich 0,0035
120,4 Prozent gleich 1,204
```

Vorschlag: eine Zahl kann in direkt in Prozent ausgegeben werden.

```
die Zahl ist 12,3%
schreibe die Zahl // 0.123
schreibe die Zahl als Prozent // spezielle Umwandlung in String
```

### Einheiten

`Zahl [Zahlenname] [Einheit]`

#### Einheitsdefinition

`Einheit(BezeichnerF, Symbol, Operationen)`

Eine Einheit gepaart mit einer Zahl bildet einen neuen Typen. 
Nur die Rechenoperationen, die bei der Einheitsdefinition angegeben werden, können auf
den Typen angewendet werden.

z.B.

```
Einheit(Meter, m)

die Strecke ist 22,5 km
die Streke ist 
```

Ein `Symbol` ist ein Zeichen oder eine kurze kleingeschriebene Zeichenfolge

Für Einheiten gibt es zwei Schreibwesen:
1. die lange Form wo der `BezeicherF` verwendet wird z.B. `Kilometer`
2. die kurze Form wo das `Symbol` verwendet wird z.B. `km`

Die Einheiten können jetzt mit den [SI-Präfixen](https://de.wikipedia.org/wiki/Vors%C3%A4tze_f%C3%BCr_Ma%C3%9Feinheiten)
verwendet werden, um die Zahl in eine bestimmte 10er-Potenz umzuwandeln.

```
1 km gleich 1000 m
70 mg gleich 0,03 g

die Erdmasse ist 5,9722 Zetatonnen
```

Vorschlag: Quadrat- und Kubik- auch noch reinnehmen.

```
156 cm^2 gleich 0,0156 m^2

die Erdmasse ist 1,0833 * 10^12 Kubikkilometer
```

Vorschlag: Einheiten auf Basis anderer Einheiten

```
Einheit(Minute, min) ist 60 Sekunden
```

mit Umrechnung:

```
Einheit(Celsius, C)
Einheit(Fahrenheit, F) ist C * 1.8 + 32 

// wird dann automatisch die Umkehrfunktion gemacht???
die Temperatur ist 10C
schreibe die Temperatur in Fahrenheit als Zeichenfolge // 50 C
```

Vorschlag: Zahlen als Einheit drucken
```
die Strecke ist 1,5 km
schreibe die Strecke als Zeichenfolge // 1,5 km
schreibe die Strecke in m als Zeichenfolge // 1,5 km
```

Vorschlag: arithmetische Operation auf Einheiten
```
1km + 100m gleich 1,1 km
100m * 100m gleich 10.000 m^2

// da `+` links assosiativ ist, wird die Einheit des linken Operanden übernommen
schreibe die Zeichenfolge 1min + 30s als Zeichenfolge // 1,5 min
schreibe die Zeichenfolge 30s + 1min als Zeichenfolge // 90s
```

Zahl+Einheit bilden einen neuen Typen! Sie sind keine Zahlen, sondern eine Zahl und eine Einheit.
Nicht alle arithmetischen Operationen sind bei allen Einheiten erlaubt und arithemetische Operationen sind nur
auf Einheiten erlaubt die ineinander umgerechnet werden können.

Wenn man eine Einheit definiert, müsste noch angegeben werden welche arithmetischen Operationen erlaubt sind:

```
Einheit(Celsius, C, +-)`
```

Die Operatoren gelten jedoch nicht für die Umrechnung. Da sind alle Rechenoperatoren erlaubt.

### Zeichenfolgen
Zeichenfolgen werden innerhalb der Anführungszeichen `""` geschrieben.

### Booleans
Booleans habe zwei Werte `wahr` oder `falsch`.

### Listen
```BezeichnungPGDA [\[{Ausdruck}\]]```

Beispiel:

`die Primzahlen sind Zahlen[2, 3, 5, 7, 11, 13]`

```
die Person1 ist Person mit dem Namen=John", dem Alter=42
die Person2 ist Person mit dem Namen="Susan", dem Alter=19
die Person3 ist Person mit dem Namen="Egon", dem Alter=72
die Personen sind Personen[Person1, Person2, Person3]

Personen: füge Person mit dem Namen="Test", dem Alter=23 hinzu
```


Listen fangen in GermanCcript mit dem Index 1 an.

Zugriff auf Element per Liste:

`ArtikelAb Index. Singular [AusdruckGP | AusdruckDP]`

```
die Person Erste ist die 1. Person der Personen
eine Person ist die 2. Person einiger Personen
```


### Typ-Umwandlung (Casting)

#### implizit
Ein vererbter Typ lässt sich immer einer Variable mit dem Typ des Elterntyps zuweisen.

```
Nomen Person:.

Nomen Student als Person:.

die Person Lukas ist Student.
```

#### explizit
`Ausdruck als Typ`

Beispiel:
`die Zahl ZweiUndVierzig ist "42" als Zahl`

Es kann eine Typumwandlung von einem Typ zu einem anderen Typ für jeden Typen definiert werden:

Syntax: 

```
alias Vorname ist Zeichenfolge
alias Nachname ist Zeichenfolge

Nomen Person mit dem Vornamen, dem Nachnamen:.

Als Zeichenfolge für Person:
    gebe meinen Namen Vorname + " " + meinen Namen Nachname zurück
.

die Person Lukas ist eine Person mit dem "Lukas", dem Nachnamen "Gobelet"
drucke die Zeichenfolge Lukas als Zeichenfolge // Lukas Gobelet
```
