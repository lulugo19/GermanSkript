# GermanScript

Eine interpretierte, objektorientierte, statisch typisierte Programmiersprache, die sich wie Deutsch schreibt.
> "Es hat mich wirklich erstaunt, wie gut sich die deutsche Sprache als Programmiersprache eignet." 
> 
>*Lukas Gobelet*

## Hallo Welt in GermanScript
Das Hallo Welt in GermanScript ist ziemlich simpel. Das hier ist alles, was man braucht:

*Datei: HalloWelt.gm*
```
schreibe die Zeile "Hallo Welt!"
```
`schreibe die Zeile` ist eine Funktion, die mit dem Argument "Hallo Welt!" aufgerufen wird.

## Ein GermanScript Programm ausführen
Um ein Germanscript-Programm auszuführen, gehe über die Kommandozeile in das Projektverzeichnis und führe folgenden Befehl aus:
```
.\gradlew run --args="<Dateipfad>"
```
Alternativ kann auch folgendes verwendet werden:
```
.\gms <Dateipfad>
```

## Die Sprache GermanScript
**GermanScript** befindet sich momentan noch in der Entwicklung. Man kann sie schon
verwenden, doch es wird sich wahrscheinlich noch einiges ändern.

Die ganze Spezifikation kann man [hier](./SPEC.md) einsehen. Diese ist jedoch noch ziemlich
in Bearbeitung und noch nicht vollständig.

GermanScript wird iterativ entwickelt. Neue Sprachfeatures werden in Iterationen nach und nach hinzugefügt.
Für jede Iteration gibt es eine eigene Spezifikation, die auf die Gesamtspezifikation aufbaut und noch
einige Details hinzufügt.

Iterationen:
- [Iteration 0](iterationen/iter_0/SPEC.md)
- [Iteration 1](iterationen/iter_1/SPEC.md)
- [Iteration 2](iterationen/iter_2/SPEC.md)
- [Iteration 3](./iterationen/iter_3/SPEC.md)

