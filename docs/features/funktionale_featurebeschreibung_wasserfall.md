# Funktionale Featurebeschreibung: Wasserfall-Display

Radar-Pulsdaten Live-Display, Stand 11.11.2026, Entwurf

## 1. Zweck und Nutzen

Der Wasserfall zeigt die Frequenzbelegung der empfangenen Radarpulse über die Zeit. Pro Zeitschritt wird die Pulsdichte je Frequenzbereich als Farbwert dargestellt. Der Bediener erkennt damit auf einen Blick, welche Frequenzen aktuell belegt sind, wie sich Belegungen über die Zeit ändern (Einschalten, Abschalten, Frequenzwechsel) und welche Bereiche für eine Detailanalyse interessant sind.

Der Wasserfall ist die Übersichtsansicht. Weitere Detailansichten sind möglich.

## 2. Begriffe

- **Frequenz-Bin:** Ein Frequenzintervall fester Breite (Prototyp: 20 MHz bei 200 Bins über 8 bis 12 GHz).
- **Zeile:** Ein Zeitschritt des Wasserfalls (Prototyp: 100 ms). Pro Zeile wird je Bin die Anzahl der Pulse gezählt.
- **Pulsdichte:** Anzahl der Pulse je Bin und Zeile nach der Extraktion (Filterung).
- **Kachel:** Die vom Server vorberechnete Zeile bzw. der Zeilenblock, den das Gateway an die Clients sendet.

## 3. Funktionale Beschreibung

### 3.1 Darstellung

- Die Frequenz wird auf der horizontalen Achse dargestellt
- Die Zeit wird auf der vertikalen Achse dargestellt. Die neueste Zeile steht oben, ältere Zeilen wandern nach unten.
- Die Pulsdichte je Bin und Zeile wird als Farbe dargestellt. Leere Bins erscheinen im Hintergrundton.
- Die Farbskala ist logarithmisch, damit sowohl schwache als auch dichte Belegungen erkennbar bleiben.
- Die Frequenzachse ist beschriftet (mindestens ganze GHz, Einheit angegeben).
- Die Zeitachse zeigt „jetzt“ und die Fenstergröße.
- Eine Farbskala-Legende zeigt die Zuordnung von Pulsdichte zu Farbe.

### 3.2 Zeitfenster und Aktualisierung

- Der Bediener wählt das sichtbare Zeitfenster (5 s, 10 s, 30 s). Die Zeilenhöhe passt sich an, die Zeilenauflösung bleibt erhalten.
- Der Wasserfall aktualisiert sich fortlaufend (Zielsystem 2 bis 10 Hz)
- Pause: Die Anzeige kann angehalten und fortgesetzt werden. Während der Pause laufen die Daten serverseitig weiter. Beim Fortsetzen springt die Anzeige auf den aktuellen Stand, ohne die Lücke aufzufüllen.
- Zeitfenster bis 5 min sind im Zielsystem wählbar. Längere Zeiträume werden gröber aufgelöst (mehrere Zeilen zusammengefasst).

### 3.3 Filter (Abbild der Extraktion)

- Amplitudenschwelle: Pulse unterhalb der Schwelle werden nicht gezählt. Einstellbereich im Prototyp -80 bis -40 dB, Schritt 1 dB, Standard -65 dB.
- Frequenzband: Der Bediener begrenzt den ausgewerteten Frequenzbereich (von/bis, Schritt 0,1 GHz). Die Anzeige stellt nur den Bereich innerhalb des Frequenzbandes dar, so dass die Darstellungsfläche optimal genutzt wird. Die Beschriftung der Achsen aktualisiert sich damit die Anzeige konsistent ist.
- Die Anzeige nennt die aktuelle Reduktion (Pulse nach Filter im Verhältnis zu Eingangspulsen)
- Die Filter wirken im Zielsystem lokal je Client.

### 3.4 Quellenauswahl

- Der Bediener wählt den Empfänger, dessen Daten angezeigt werden.
- Beim Öffnen der Darstellung ist automatisch der erste vorhandene Empfänger gewählt
- Beim Wechsel des Empfängers wird der Zeitverlauf des neuen Empfängers übernommen, soweit der Live-Zustand vorliegt. Andernfalls beginnt der Wasserfall leer.
- Es soll möglich sein mehrere Empfänger in einer Summenansicht zu kombinieren.

### 3.5 Interaktion

- Cursor: Beim Überfahren zeigt eine Anzeige Frequenz, Zeitpunkt und Pulsdichte der Zelle unter dem Mauszeiger.
- Frequenzbereich markieren: Ein Bereich im Wasserfall kann aufgezogen werden. Er wird als Frequenzband für den Zoom übernommen.
- Zoom auf einen Frequenzbereich mit höherer Frequenzauflösung. Die Kachelauflösung wird dafür vom Server nachgeliefert.
- Frequenzbereich markieren: Ein Bereich im Wasserfall kann aufgezogen werden. Er kann durch den Bediner als Frequenzband für das Detailfenster übernommen.
- Emitter-Marker: Frequenzen der erkannten Emitter werden als Markierung eingeblendet und mit der Emitterliste verknüpft. Ein Klick auf einen Marker wählt den Emitter.
- Markierung eines Einzelpunkts (Frequenz) für die Übergabe an das Detailfenster.

### 3.6 Status und Fehlerverhalten

- Fällt der Datenstrom aus oder verzögert er sich, wird dies sichtbar gekennzeichnet (z. B. Warnhinweis). Die Anzeige zeigt veraltete Zeilen nicht als aktuell.
- Fehlende Zeilen (Datenverlust) werden als Lücke dargestellt und nicht interpoliert.
- Zeigt ein Client langsamer an, als Kacheln eintreffen, werden Zwischenzeilen verworfen oder zusammengefasst. Die Anzeige bleibt zeitlich korrekt.
- Die angezeigte Latenz (Puls bis Anzeige) wird mitgeführt und angezeigt. Ein Überschreiten von 2 s wird markiert.

## 4. Daten und Schnittstellen

### 4.1 Eingang (Kachel vom Server)

Jede Kachel enthält mindestens:

- Receiver-ID (oder Kennung der Summenansicht)
- Zeitstempel der Zeile (Beginn und Dauer)
- Frequenzbereich und Bin-Anzahl (damit der Client die Achse ableiten kann)
- Auflösungsstufe
- Zählwerte je Bin (kompakt, z. B. 8 oder 16 Bit je Bin)
- Zusatzangaben: Anzahl Eingangspulse und Pulse nach Filter für die Anzeige der Reduktion

Die Übertragung erfolgt binär über WebSocket. Der Client erhält keine Einzelpulse.

### 4.2 Steuerung (Client zum Server)

- Empfängerauswahl
- Filter (Amplitudenschwelle, Frequenzband)
- Zoombereich und gewünschte Auflösungsstufe

## 5. Nicht-funktionale Anforderungen

- **Latenz:** Vom Puls bis zur Anzeige im Wasserfall unter 2 s, Planungsziel 300 bis 500 ms für den Rohpuls-Aggregationspfad.
- **Aktualisierungsrate:** 2 bis 10 Zeilen pro Sekunde, ruckelfreie Darstellung bei Zeitfenstern bis 30 s.
- **Datenmenge pro Client:** Die Kachelrate liegt im Bereich weniger hundert kByte pro Sekunde (200 Bins bei 10 Hz). Skalierung auf viele Clients über das Gateway.
- **Darstellung:** Rendern im Browser über Canvas oder WebGL. Farbskalen sind für Rot-Grün-Sehschwäche geeignet.
- **Hell- und Dunkelmodus:** Die Bedienelemente folgen dem System, die Wasserfall-Fläche selbst ist dunkel.

## 6. Abgrenzung

Nicht Bestandteil des Wasserfalls:

- Anzeige von Einzelpulsen, TOA und PRI (Detailfenster)
- Winkelinformation (Polardisplay)
- Emittererkennung und Analyse (Analysestufe, der Wasserfall zeigt nur deren Ergebnisse als Marker)
- Speicherung und Wiedergabe historischer Daten über den Live-Puffer hinaus

## 8. Abnahmekriterien (Auszug)

1. Ein zugeschalteter Emitter erscheint innerhalb von 2 s als farbiger Streifen im passenden Frequenzbereich.
2. Ein Frequenzwechsel eines Emitters ist als Versatz des Streifens erkennbar.
3. Eine Änderung der Amplitudenschwelle verändert die angezeigte Belegung und die Reduktionsanzeige innerhalb von 2 s.
4. Beim Umschalten des Zeitfensters bleibt der bereits dargestellte Verlauf erhalten.
5. Ein künstlich erzeugter Datenausfall wird sichtbar gekennzeichnet und nicht als gültige Belegung dargestellt.
6. Bei langsamem Client bleibt die zeitliche Zuordnung der Zeilen korrekt.

## 9. Offene Punkte

- Wirken Filter global in der Extraktion oder lokal je Client? Global spart Last, lokal erlaubt unterschiedliche Sichten. Alternativ: Extraktion grob global, Feinfilter nur in der Anzeige.
- Gewünschte Frequenzauflösung und Gesamtfrequenzbereich im Zielsystem.
- Maximales Zeitfenster und Anforderungen an Wiedergabe (z. B. Zurückblättern im Live-Puffer).
- Darstellung bei mehreren Empfängern: eigene Wasserfälle, Summe oder beides.
- Farbskala: feste oder automatisch angepasste Skalierung, Möglichkeit zur manuellen Einstellung.
