# Funktionale Featurebeschreibung: AoA-Display (Polardisplay)

Radar-Pulsdaten Live-Display, Stand 12.11.2026, Entwurf

## 1. Zweck und Nutzen

Das AoA-Display (Angle of Arrival) zeigt die Einfallsrichtung der empfangenen Radarpulse in einer Polardarstellung. Der Winkel auf dem Kreis entspricht dem Einfallswinkel, der Abstand vom Mittelpunkt der Signalstärke. Der Bediener erkennt damit auf einen Blick, aus welchen Richtungen Signale kommen, wie stark sie sind und wie sich Richtungen über die Zeit verändern (z. B. Antennenumlauf eines Suchradars oder bewegte Quellen).

Das AoA-Display ergänzt den Wasserfall (Frequenz über Zeit) und das Detailfenster (TOA und PRI) um die räumliche Sicht auf die Signallage.

## 2. Begriffe

- **AoA / Einfallswinkel:** Richtung, aus der ein Puls am Empfänger eintrifft, in Grad (0° bis 360°).
- **Radius:** Abstand vom Mittelpunkt. Er steht für die Amplitude des Pulses (Prototyp: -80 dB im Zentrum bis -30 dB am Rand).
- **Ring / Speiche:** Gitterlinien der Polardarstellung (Ringe für Amplitude, Speichen für Winkel).
- **Emitter-Marker:** Markierung am Rand des Kreises, die den mittleren Einfallswinkel eines erkannten Emitters zeigt.
- **Polarkachel:** Die vom Server vorberechnete Aggregation (Pulszähler je Winkel- und Amplitudenbereich), die das Gateway an die Clients sendet.

## 3. Funktionale Anforderungen

### 3.1 Darstellung

- Die Darstellung bleibt kreisrund und zentriert, wenn sich die Fenstergröße ändert.
- Der Einfallswinkel wird als Winkel auf einem Kreis dargestellt. 0° liegt oben, die Zählung verläuft im Uhrzeigersinn bis 360°.
- Der Radius stellt die Amplitude dar. Schwache Signale liegen innen, starke außen. Der dargestellte Bereich ist im Prototyp -80 bis -30 dB.
- Der dargestellte Amplitudenbereich (innerer und äußerer Radius) ist einstellbar.
- Die Einheit des Radius (dB) ist an der Anzeige angegeben.
- Das Gitter besteht aus Ringen alle 10 dB und Speichen alle 30°. Die Ringe sind mit dem Amplitudenwert, die Hauptrichtungen (0°, 90°, 180°, 270°) mit dem Winkel beschriftet.
- Der Bezugspunkt des Winkels (z. B. geografisch Nord, Bug, Antennenachse) ist einstellbar und an der Anzeige gekennzeichnet.

### 3.2 Pulsdarstellung und Zeitverhalten

- Pulse werden als Punkte an der Position aus Einfallswinkel und Amplitude dargestellt.
- Die Punkte sind nach Emitter eingefärbt (Zuordnung aus Emitter Ref bzw. Analyse).
- Die Pulse ohne Zuordnung erscheinen in einem hellen blau und zurückgenommen.
- Pulse altern: Sie werden über das gewählte Zeitfenster (5 s, 10 s, 30 s) zunehmend transparent und verschwinden danach.
- Pulse altern: Sie werden über das gewählte Zeitfenster (5 s, 10 s, 30 s) zunehmend transparent und verschwinden danach.
- Das Zeitfenster kann gemeinsam mit den anderen Anzeigen (Wasserfall) gesteuert werden.
- Bei hoher Pulsdichte wechselt die Darstellung automatisch von Einzelpunkten auf eine Dichtedarstellung (Pulszähler je Winkel-Amplituden-Bereich als Farbe).
- Die Anzeige aktualisiert sich fortlaufend (Zielsystem 2 bis 10 Hz).
- Die Anzeige kann angehalten und fortgesetzt werden. Beim Fortsetzen springt sie auf den aktuellen Stand.

### 3.3 Emitter-Marker

- Für jeden aktiven Emitter wird am Kreisrand eine Markierung mit der Emitter-ID im mittleren Einfallswinkel angezeigt.
- Ein Emitter gilt als aktiv, wenn innerhalb der letzten 1,5 s Pulse empfangen wurden. Andernfalls alter der Marker analog zu den Pulsen heraus (wird zunehmend transparent und verschwinden danach).
- Die Winkelstreuung des Emitters (Unsicherheit) wird als Sektor am Marker angezeigt.
- Die Auswahl eines Emitters in der Emitterliste hebt ihn im AoA-Display hervor, alle anderen werden abgedunkelt. Die Schnittstelle ist von Außen zugänglich, sodass auch andere Module die Möglichkeit haben sich hier anzubinden.
- Ein Klick auf einen Marker oder auf einen Punkt wählt den zugehörigen Emitter, auch in den anderen Anzeigen. Es gibt auch für andere Module die Möglichkeit an diese Event anzubinden.

### 3.4 Filter und Quellenauswahl

- Die Amplitudenschwelle gilt auch für das AoA-Display. Pulse unterhalb der Schwelle werden nicht dargestellt.
- Das Frequenzband gilt auch für das AoA-Display. Nur Pulse innerhalb des Bandes werden dargestellt.
- Der Bediener wählt den Empfänger. Die Winkel beziehen sich auf den gewählten Empfänger.
- Summenansicht mehrerer Empfänger: Die Pulse werden je Empfänger unterscheidbar dargestellt (Farbe oder Symbol).
- Die Filter wirken wie beim Wasserfall (lokal je Client, siehe Featurebeschreibung Wasserfall).

### 3.5 Interaktion

- Cursor: Beim Überfahren zeigt eine Anzeige Winkel, Amplitude und Pulsanzahl in der Umgebung des Mauszeigers.
- Sektor markieren: Ein Winkelbereich kann aufgezogen werden. Er wird als Auswahl für das Detailfenster oder als Filter übernommen.
- Ein ausgewählter Sektor wird mit dem Wasserfall gekoppelt: Dieser zeigt dann nur Pulse aus dem Sektor.
- Zoom auf einen Sektor oder Amplitudenbereich.
- Die Radiusgröße ist umschaltbar (Amplitude, Frequenz oder Alter der Pulse).

### 3.6 Status und Fehlerverhalten

- Fällt der Datenstrom aus oder verzögert er sich, wird dies sichtbar gekennzeichnet. Veraltete Pulse werden nicht als aktuell dargestellt.
- Fehlende Daten (Datenverlust) werden nicht interpoliert.
- Zeigt ein Client langsamer an, als Kacheln eintreffen, werden Zwischenstände verworfen oder zusammengefasst. Die Anzeige bleibt zeitlich korrekt.
- Die Latenz (Puls bis Anzeige) wird angezeigt. Ein Überschreiten von 2 s wird markiert.

## 4. Daten und Schnittstellen

### 4.1 Eingang (Polarkachel vom Server)

Jede Kachel enthält mindestens:

- Receiver-ID (oder Kennung der Summenansicht)
- Zeitstempel und Dauer der Kachel
- Winkelraster (z. B. 1° oder 2°) und Amplitudenraster (z. B. 1 dB) mit Bin-Anzahl
- Zählwerte je Winkel-Amplituden-Bereich (bei 1° und 1 dB ca. 18 kByte je Kachel vor Kompression, dünn besetzt und gut komprimierbar)
- Optional eine begrenzte Stichprobe von Einzelpulsen (Winkel, Amplitude, Emitterzuordnung) für die Punktdarstellung
- Emitter-Liste mit mittlerem Einfallswinkel, Winkelstreuung, letzter Aktivität und Zuordnung
- Winkelreferenz (Nord, Bug, Antennenachse)

Die Übertragung erfolgt binär über WebSocket oder SSE. Der Client erhält nicht alle Einzelpulse.

### 4.2 Steuerung (Client zum Server)

- Empfängerauswahl
- Filter (Amplitudenschwelle, Frequenzband)
- Sektorauswahl und Zoombereich
- Gewünschtes Winkel- und Amplitudenraster

## 5. Nicht-funktionale Anforderungen

- **Latenz:** Vom Puls bis zur Anzeige unter 2 s, Planungsziel 300 bis 500 ms für den Rohpuls-Aggregationspfad.
- **Aktualisierungsrate:** 2 bis 10 Updates pro Sekunde, ruckelfreie Darstellung bei Zeitfenstern bis 30 s.
- **Darstellungsmenge:** Die Anzahl gleichzeitig gezeichneter Punkte ist begrenzt (Richtwert einige tausend). Darüber wird auf die Dichtedarstellung umgeschaltet (AOA-14).
- **Winkelgenauigkeit:** Die Darstellung gibt die Genauigkeit der Quelle wieder, die Rasterung darf diese nicht verschlechtern.
- **Darstellung:** Rendern im Browser über Canvas oder WebGL. Farben sind für Rot-Grün-Sehschwäche unterscheidbar oder durch Symbole ergänzt.
- **Hell- und Dunkelmodus:** Die Bedienelemente folgen dem System, die Zeichenfläche selbst ist dunkel.

## 6. Abgrenzung

Nicht Bestandteil des AoA-Displays:

- Ortung und Triangulation über mehrere Empfänger (Korrelationsstufe, später)
- Anzeige von TOA und PRI (Detailfenster)
- Frequenzverlauf (Wasserfall)
- Emittererkennung und Analyse (Analysestufe, das AoA-Display zeigt nur deren Ergebnisse als Marker und Einfärbung)
- Speicherung und Wiedergabe historischer Daten über den Live-Puffer hinaus

## 7. Prototyp und Zielsystem

Der vorhandene Prototyp deckt Darstellung (AOA-01 bis AOA-05), Punktdarstellung mit Alterung (AOA-10 bis AOA-12, AOA-15, AOA-16), Emitter-Marker (AOA-20, AOA-21, AOA-23) und Filter und Empfängerauswahl (AOA-30 bis AOA-32) mit simulierten Daten ab. Die Punkte sind dort eine Stichprobe der gefilterten Pulse und werden im Browser aggregiert. Dichtedarstellung, Sektorauswahl, Cursor, Zoom, Summenansicht, Winkelreferenz, Fehlerverhalten und die serverseitige Kachelschnittstelle sind noch offen.

## 8. Abnahmekriterien (Auszug)

1. Ein Emitter mit bekanntem Einfallswinkel erscheint innerhalb von 2 s im richtigen Winkel (Abweichung höchstens ein Winkelraster) und mit passendem Marker.
2. Ein Emitter mit Antennenumlauf zeigt eine periodisch wechselnde Amplitude, erkennbar als wechselnder Radius.
3. Eine Änderung der Amplitudenschwelle verändert die dargestellten Pulse innerhalb von 2 s.
4. Beim Wechsel des Empfängers ändern sich die Winkel entsprechend, und Emitter, die dieser Empfänger nicht sieht, verschwinden.
5. Die Auswahl eines Emitters in der Liste hebt ihn im AoA-Display hervor.
6. Bei sehr hoher Pulsdichte bleibt die Anzeige flüssig, die Darstellung wechselt auf Dichte.
7. Ein künstlich erzeugter Datenausfall wird sichtbar gekennzeichnet.

## 9. Offene Punkte

- Winkelreferenz: geografisch Nord, Bug oder Antennenachse, und ob die Umrechnung im Server oder im Client erfolgt.
- Gewünschte Winkelauflösung und -genauigkeit sowie Amplitudeneinheit (relative dB oder kalibrierte dBm).
- Radiusgröße: nur Amplitude oder umschaltbar (z. B. Frequenz, Alter).
- Darstellung bei mehreren Empfängern: ein gemeinsames Polardisplay oder je ein Display, und ob schon Peilstrahlen für die spätere Korrelation gezeigt werden sollen.
- Punktdarstellung (Stichprobe von Einzelpulsen) oder nur Dichterraster, und ab welcher Pulsdichte umgeschaltet wird.
- Farbcodierung: nach Emitter, Frequenz oder Alter.
