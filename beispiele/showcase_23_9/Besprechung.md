# Projektbesprechung 23.09

### Fortschritt in den letzen 4. Wochen

#### neue Sprachfeatures:
- versuche-fange-schlussendlich Fehlerbehandlung
- Generics
    - bei Funktionen
    - bei Klassen und Schnittstellen
- Implementiere-Block
- weitere Verbesserungen für Schnittstellen (Adjektive)
- Expressions (Ausdrücke) sind jetzt Statements (Sätze)
    - wie bei Kotlin
    - Bedingung kann jetzt als Ausdruck stehen
    - genauso wie Methodenbereiche
    - bei Closures wird das letze Statement implizit als Rückgabewert gewertet
- Bedingungs-Aufrufweise einer Methode

#### Erstellung der Standardbibliothek
- Standardeingabe, Standardausgabe
- Zeichenfolge:
    - code an dem Index: *gibt Code-Character an dem Index als Zahl zurück*
    - index von der Zeichenfolge
    - letzter_index von der Zeichenfolge
    - enthält die Zeichenfolge
    - teile mich: *substring*
    - buchstabiere mich groß
    - buchstabiere mich klein
    - endet mit der Zeichenfolge
    - startet mit der Zeichenfolge
    - wiederhole mich mit der Anzahl
- Liste:
    - enthält das Element
    - sortiere mich
    - filter mich
    - mappe mich
    - reduziere mich
    - kopiere mich
- Mathe:
    - Konstante *PI* und *E*
    - runde die Zahl (auf/ ab)
    - sinus, cosinus, tangens
    - maximum, minimum
    - betrag
    - randomisiere
    - randomisiere zwischen dem Minimum und dem Maximum
- Datei:
    - lese_zeilen
    - hole_schreiber
- Schreiber:
    - schreibe die Zeichenfolge/Zeile
    - füge die Zeichenfolge/Zeile hinzu
    - schließe mich
    
### Wie geht es weiter?
Abweichung vom ursprünglichen Plan: Erstellung eines Language Servers usw.

- Sprachfeatures weiter ausbauen
- weiter Sprachfeatures überlegen/verbessern:
    - Aufzählungen (algebraische Datentypen)
    - Rückgabetyp als Wort?
    - Einheiten? `3m + 3cm = 303 cm`
    - Zahlendekorierer wie z.B. `3 Millionen = 3.000.000` oder `12% = 0,12`
- Compiler-Fehlerausgabe verbessern
- Erstellung eines REPL (Read-eval-print-loop)
- an Dokumentation arbeiten