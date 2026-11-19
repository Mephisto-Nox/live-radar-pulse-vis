# PDW-Format des Simulators

Diese Beschreibung erklärt, wie der Simulator seine Pulsdaten (PDW = *Pulse Descriptor Word*) über UDP verschickt. Sie passt zur mitgelieferten `format.json`. Wenn du die Datei änderst, ändert sich auch das Format. Die Tabellen unten gelten dann nur für die Standardwerte.

> Das Format ist ein frei gewähltes Beispiel für Testzwecke und kein standardisiertes Austauschformat.

## 1. Überblick: ein UDP-Paket

Jedes UDP-Datagramm hat drei Teile:

```
┌────────────┬──────────────────────────────┬───────────┐
│ Paketkopf  │  N × PDW (je 24 Byte)        │  Filler   │
│  22 Byte   │  Standard: 55 × 24 = 1320 B  │  110 Byte │
└────────────┴──────────────────────────────┴───────────┘
              Gesamt: 1452 Byte  →  Overhead 10 %
```

- **Paketkopf** enthält Metadaten (Kennung, Paketzähler, Anzahl PDWs, Prüfsumme).
- **PDWs** sind die eigentlichen Pulsbeschreibungen, lückenlos hintereinander und nach Ankunftszeit sortiert.
- **Filler** sind Füllbytes (Standard: Nullen). Sie bringen den Overhead auf den gewünschten Wert.

Der **Overhead** ist `(Kopf + Filler) / PDW-Nutzdaten`. Hier sind das (22 + 110) / 1320 = 10 %.

## 2. Paketkopf (22 Byte, Big Endian)

| Offset | Bytes | Feld           | Typ | Bedeutung                                                         |
|-------:|------:|----------------|-----|-------------------------------------------------------------------|
| 0      | 4     | `sync`         | u32 | Feste Kennung `0xA5A55A5A`, zum Erkennen des Paketanfangs         |
| 4      | 4     | `seq`          | u32 | Paketzähler, steigt bei jedem Paket um 1 (zum Erkennen von Verlusten) |
| 8      | 2     | `pdw_count`    | u16 | Anzahl der PDWs in diesem Paket                                   |
| 10     | 8     | `first_toa_us` | u64 | Ankunftszeit des ersten PDWs im Paket, in Mikrosekunden           |
| 18     | 4     | `crc32`        | u32 | CRC-32 über den PDW-Block (nur die PDWs, ohne Kopf und Filler)    |

Der Kopf ist **Big Endian** (höchstwertiges Byte zuerst, wie im Netzwerk üblich).

## 3. PDW (24 Byte, Little Endian)

| Offset | Bytes | Feld          | Typ | Einheit im Datenstrom | Umrechnung in Klartext            |
|-------:|------:|---------------|-----|-----------------------|-----------------------------------|
| 0      | 8     | `toa_ns`      | u64 | Nanosekunden          | µs = Wert / 1000                  |
| 8      | 4     | `freq_khz`    | u32 | Kilohertz             | MHz = Wert / 1000                 |
| 12     | 4     | `pw_ns`       | u32 | Nanosekunden          | µs = Wert / 1000                  |
| 16     | 2     | `amp_0p1dbm`  | i16 | 0,1 dBm (mit Vorzeichen) | dBm = Wert / 10                |
| 18     | 2     | `aoa_0p01deg` | u16 | 0,01 Grad             | Grad = Wert / 100                 |
| 20     | 2     | `emitter_id`  | u16 | Nummer                | Ground Truth, siehe unten         |
| 22     | 2     | `flags`       | u16 | Bits                  | reserviert, immer 0               |

Die PDWs sind **Little Endian** (niedrigstwertiges Byte zuerst) und ohne Lücken oder Ausrichtung (*packed*) abgelegt.

### Bedeutung der Felder

- **`toa_ns` (Time of Arrival):** Zeitpunkt, an dem der Puls beim Empfänger eintrifft, gemessen ab Simulationsstart.
- **`freq_khz`:** Trägerfrequenz des Pulses. Beispiel: 9 410 000 kHz sind 9410 MHz (X-Band).
- **`pw_ns` (Pulsbreite):** Dauer des Pulses. Beispiel: 100 ns sind 0,1 µs.
- **`amp_0p1dbm`:** Empfangene Leistung. Typische Werte liegen zwischen −800 und −200 (also −80 bis −20 dBm). Die Amplitude schwankt, wenn die Radarantenne am Empfänger vorbeidreht.
- **`aoa_0p01deg` (Angle of Arrival):** Einfallswinkel 0 bis 359,99 Grad, mit Messrauschen.
- **`emitter_id`:** Nummer der simulierten Emitter-Instanz. In einem echten Empfänger gäbe es diese Information nicht. Sie dient zum Prüfen von Deinterleaving-Algorithmen. Die Nummern werden in der Reihenfolge der Einträge in `emitters.json` vergeben (bei null beginnend). Willst du sie nicht senden, entferne das Feld aus `format.json`.
- **`flags`:** Platzhalter für eigene Erweiterungen.

## 4. Beispiel: ein PDW von Hand dekodieren

Rohdaten (24 Byte, Hex):

```
ac 05 3a af 00 00 00 00   96 a1 ec 00   92 01 00 00   99 fe   c5 28   32 00   00 00
└─────── toa_ns ───────┘  └─ freq_khz ┘ └── pw_ns ──┘ └amp ┘ └ aoa ┘ └ id ┘  └flags┘
```

Weil PDWs Little Endian sind, wird jede Zahl von hinten nach vorn gelesen:

| Feld          | Bytes (Little Endian)        | Gelesen als       | Wert        | Klartext          |
|---------------|------------------------------|-------------------|------------:|-------------------|
| `toa_ns`      | `ac 05 3a af 00 00 00 00`    | `0x00000000AF3A05AC` | 2 939 815 340 | 2 939 815,34 µs |
| `freq_khz`    | `96 a1 ec 00`                | `0x00ECA196`      | 15 507 862  | 15 507,862 MHz    |
| `pw_ns`       | `92 01 00 00`                | `0x00000192`      | 402         | 0,402 µs          |
| `amp_0p1dbm`  | `99 fe`                      | `0xFE99` (mit Vorzeichen) | −359 | −35,9 dBm         |
| `aoa_0p01deg` | `c5 28`                      | `0x28C5`          | 10 437      | 104,37 °          |
| `emitter_id`  | `32 00`                      | `0x0032`          | 50          | Instanz 50        |
| `flags`       | `00 00`                      | `0x0000`          | 0           | nicht gesetzt     |

Dieses PDW stammt von einem Feuerleit-Radar im Ku-Band (ca. 15,5 GHz, kurzer Puls, hohe Pulswiederholrate).

## 5. Pakete in eigenem Code lesen

Beispiel in Python (mit `numpy`), passend zum Standardformat:

```python
import socket, struct, zlib
import numpy as np

pdw = np.dtype([("toa_ns", "<u8"), ("freq_khz", "<u4"), ("pw_ns", "<u4"),
                ("amp", "<i2"), ("aoa", "<u2"), ("emitter_id", "<u2"), ("flags", "<u2")])
HEADER = ">IIHQI"                      # sync, seq, pdw_count, first_toa_us, crc32
HSIZE = struct.calcsize(HEADER)        # 22

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.bind(("0.0.0.0", 5005))
data, _ = sock.recvfrom(65535)

sync, seq, n, first_toa, crc = struct.unpack(HEADER, data[:HSIZE])
assert sync == 0xA5A55A5A
payload = data[HSIZE:HSIZE + n * pdw.itemsize]
assert zlib.crc32(payload) == crc
pdws = np.frombuffer(payload, dtype=pdw)
print(pdws["freq_khz"] / 1000, "MHz")
```

Alles hinter den PDWs im Paket (der Filler) ignorierst du.

## 6. Das Format in `format.json` ändern

`format.json` hat drei Abschnitte: `udp`, `packet_header` und `pdw_format`. Die Reihenfolge der Felder in der Datei ist die Reihenfolge im Paket.

### Felder eines Eintrags

| Schlüssel | Pflicht | Bedeutung |
|-----------|---------|-----------|
| `name`    | ja      | Name des Feldes (frei wählbar) |
| `type`    | ja      | `u8`, `u16`, `u32`, `u64`, `i8`, `i16`, `i32`, `i64`, `f32`, `f64` |
| `source`  | ja, außer bei `const` | Woher der Wert kommt (Liste unten) |
| `const`   | alternativ zu `source` | Fester Wert, auch als Hex-Text wie `"0xA5A55A5A"` |
| `mult`    | nein (Standard 1) | Faktor, mit dem der Wert multipliziert wird |
| `offset`  | nein (Standard 0) | Wird nach der Multiplikation addiert |
| `modulo`  | nein    | Wertebereich umbrechen, zum Beispiel für Zähler mit Überlauf |

Gerechnet wird `Wert = Quelle × mult + offset`. Bei Ganzzahlen wird gerundet und auf den Wertebereich des Typs begrenzt.

### Verfügbare Quellen

| Bereich | Quelle | Inhalt |
|---------|--------|--------|
| PDW     | `toa_us`     | Ankunftszeit in µs |
| PDW     | `freq_mhz`   | Frequenz in MHz |
| PDW     | `pw_us`      | Pulsbreite in µs |
| PDW     | `amp_dbm`    | Amplitude in dBm |
| PDW     | `aoa_deg`    | Einfallswinkel in Grad |
| PDW     | `emitter_id` | Emitter-Instanz (Ground Truth) |
| PDW     | `pdw_index`  | Laufende PDW-Nummer |
| Kopf    | `packet_seq`     | Paketzähler |
| Kopf    | `pdw_count`      | Anzahl PDWs im Paket |
| Kopf    | `first_toa_us`   | Zeit des ersten PDWs |
| Kopf    | `send_time_us`   | Sendezeit (Uhrzeit des Rechners, µs) |
| Kopf    | `crc32_payload`  | CRC-32 über den PDW-Block |

Die Byte-Reihenfolge stellst du pro Abschnitt mit `"byte_order": "little"` oder `"big"` ein.

### Beispiele

Frequenz mit 10 kHz Auflösung in 16 Bit (Wert × 100, da 1 MHz = 100 × 10 kHz):

```json
{"name": "freq_10khz", "type": "u16", "source": "freq_mhz", "mult": 100}
```

TOA als 32-Bit-Zähler in Nanosekunden, der nach rund 4,3 Sekunden überläuft:

```json
{"name": "toa_ns32", "type": "u32", "source": "toa_us", "mult": 1000, "modulo": 4294967296}
```

Amplitude als Gleitkommazahl direkt in dBm:

```json
{"name": "amp_dbm", "type": "f32", "source": "amp_dbm"}
```

### Overhead und Paketgröße (`udp`)

| Schlüssel | Bedeutung |
|-----------|-----------|
| `host`, `port` | Ziel des Streams |
| `overhead_percent` | Gewünschter Overhead in Prozent der PDW-Nutzdaten (Standard 10) |
| `mtu_payload` | Maximale UDP-Nutzlast pro Paket in Byte (Standard 1472, passt in einen Ethernet-Frame mit 1500 Byte) |
| `pdws_per_packet` | Feste Anzahl PDWs pro Paket. `null` bedeutet: automatisch aus `mtu_payload` und Overhead berechnen |
| `count_ip_udp_headers` | `true` rechnet die 28 Byte IP- und UDP-Kopf mit in den Overhead ein |
| `filler` | `"zeros"` (Nullen) oder `"random"` (Zufallsbytes) |

Ist der Paketkopf allein schon größer als der gewünschte Overhead, entsteht kein Filler und der tatsächliche Overhead liegt höher. Der Simulator gibt beim Start den tatsächlichen Wert aus.

## 7. Kurzreferenz der Einheiten

| Größe | Im Datenstrom | Klartext |
|-------|---------------|----------|
| Zeit | ns (u64) | 1 000 ns = 1 µs |
| Frequenz | kHz (u32) | 1 000 kHz = 1 MHz |
| Pulsbreite | ns (u32) | 1 000 ns = 1 µs |
| Amplitude | 0,1 dBm (i16) | −359 = −35,9 dBm |
| Winkel | 0,01 ° (u16) | 10 437 = 104,37 ° |
