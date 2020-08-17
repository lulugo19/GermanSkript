# Iteration 4

## hinzugefügte Features
- Closures
- Alias
- Aufzählungen
- Für-Jede-Schleife über Zahlen
- (Adjektive als Teil von Bezeichnern)

## Closures
`etwas Bezeichner: Sätze.`

Closures funktionieren über die Schnittstellen (Adjektive). Wenn eine Schnittstelle nur eine einzige
Methode definiert, dann kann man für diese Schnittstelle ein Closure erstellen. 
Der Bezeichner, der nach dem Vornomen `etwas` kommt ist das nominalisierte Adjektiv der Schnittstelle.

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

## Alias
`Alias BezeichnerP ist Typ`

Beispiel:

`Alias Alter ist Zahl`

Ein Typ kann über den Alias einen andern Namen bekommen, über dem man auf diesen Typ verweisen kann.

## Aufzählungen
Aufzählungen können beliebig viele Eigenschaften haben.


Beispiel:

```
Aufzählung Ereignis mit der Zeichenfolge Name:
    Weihnachten mit dem Namen="Weihnachten"
    Ostern mit dem Namen="Ostern"
    Haloween mit dem Namen="Haloween"
    Geburtstag mit dem Namen="Geburstag"
.

das Ereignis ist das Ereignis Weihnachten
schreibe die Zeichenfolge (der Name des Ereignisses) // "Weihnachten"
```

## Für-Jede-Schleife über Zahlen
Beispiel:

```
für jede Zahl von 1 bis 12:
    schreibe die Zahl 
.
```

## Adjektive als Teil von großen Bezeichnern (Nomen)

Bisher ist es möglich für Nomen zusammengesetze Wörter, sowie Symbole am Ende des Wortes zu verwenden.
z.B.
```
der VorName ist "Lukas"
die ZahlX ist 4
```
Nun sollen Nomen um Adjektive ergänzt werden. Adjektive sind ähnlich wie zusammengesetze Wörter nur
dass es für jedes Nomen höchstens ein Adjektiv geben kann, das als kleines Wort vor dem Nomen drangehängt wird.
Das besondere an Adjektiven ist, dass diese vom Grammatik-Prüfer geprüft werden, damit diese die richtige Deklination
haben.

Beispiel:

```
Verb schreibe die Zeichenfolge Name:
    schreibe die Zeile "Name: " plus den Namen
.

ein vollerName ist der VorNamen + den NachNamen
schreibe den vollenNamen
```