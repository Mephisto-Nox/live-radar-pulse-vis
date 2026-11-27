# Pulse Vision

Monorepo for the Pulse Vision radar receiver UI and its Java API. The backend accepts the documented UDP datagram format, validates and decodes PDWs, publishes JSON batches to Apache Kafka, and serves server-aggregated waterfall and AOA tiles to the browser.

## Repository layout

- `backend/` — Java 25 / Spring Boot REST service and tests
- `frontend/` — Angular 22 application and Nginx runtime image
- `deployment/` — local and production Docker Compose plus Kubernetes manifests
- `scripts/` — shared build and validation helpers
- `.github/workflows/` — component CI and deployment validation

## Toolchain

The project pins the versions documented in `.docs/internal/001-Setup-Project-Structure.md`: Temurin JDK 25.0.5+8, Maven 3.10.0 via wrapper, Node.js 26.13.0, npm 12.2.2, and Angular 22.2.5. The local Compose stack uses Apache Kafka 4.1.1. Dockerfiles and Compose images use versioned tags. CI builds each component independently.

## Run locally

Requirements: Docker with the Compose plugin. From the repository root:

```powershell
docker compose -f deployment/docker-compose.yml up --build
```

Open `http://localhost:8080`. German is the default UI language; use the DE/EN control in the top bar to switch language. The light/dark theme follows the operating-system preference until changed manually; the choice is stored in browser local storage. The frontend is served by Nginx; `/api/health` and `/api/pdw/health` are proxied to the backend. Kafka is available on `localhost:9092`; the backend uses its internal Compose address `kafka:9092`. Send simulator datagrams to `127.0.0.1:5005` (UDP).

The navigation opens the waterfall (`/waterfall`) and AOA (`/aoa`) on separate pages so their WebSocket and data status can be diagnosed independently. The legacy `/dashboard` route redirects to `/waterfall`. Waterfall overview rows use 600 frequency bins by 41 amplitude bins; a selected band requests 2,000-bin zoom tiles. The AOA page consumes an independent Kafka group and streams polar rows with 1° angle bins, a bounded point sample, and density counts. Each page has its own receiver, time-window, frequency, amplitude-threshold, and pause controls.

The Kubernetes backend manifest requests 1 GiB memory and sets a 2 GiB limit for the bounded 30-second waveform and AOA buffers. Recheck these values against receiver count and pulse rate when changing the retention window.

Stop the services with `docker compose -f deployment/docker-compose.yml down`.

## Build and test without Docker

Backend, from `backend/`:

```powershell
.\mvnw.cmd -B verify
```

Frontend, from `frontend/`:

```powershell
npm ci
npm test -- --watch=false
npm run build
```

### Local development without Docker

Start the local Kafka broker from the repository root in one terminal:

```powershell
docker compose -f deployment/docker-compose.yml up kafka
```

Start the backend from `backend/` in another terminal. It binds UDP port 5005 and connects to the local broker:

```powershell
$env:KAFKA_BOOTSTRAP_SERVERS = "localhost:9092"
.\mvnw.cmd spring-boot:run
```

Start the Angular development server from `frontend/` in a third terminal:

```powershell
npm ci
npm start
```

Open the URL printed by Angular CLI (normally `http://localhost:4200`). The development proxy forwards API, actuator, and WebSocket requests to `http://localhost:8080`.

### Packet-flow design

- `PacketReceiver` is the replaceable input boundary; the synthetic demo receiver and UDP PDW receiver remain separate flows.
- UDP PDWs are published to Kafka by `PdwKafkaPublisher`; `WaterfallKafkaConsumer` and `AoaKafkaConsumer` use independent consumer groups and pass decoded batches to their respective aggregators.
- `AoaAggregator` retains only compact angle, amplitude, frequency, and row-time data in the bounded live window so clients can apply different frequency filters without receiving raw PDWs.
- Aggregation runs in server memory at 10 Hz using UDP receive event-time. The most recent 30 event-time seconds are kept per discovered receiver. The browser receives histogram tiles, not individual PDWs, and applies frequency/amplitude filters locally. If Kafka falls behind, the newest processed interval remains visible and the UI latency warning reflects the lag rather than silently dropping all rows as late.
- The legacy `/ws/packets` endpoint remains a demo metadata stream and is not used by the waterfall screen.
- `GET /api/health` reports API status and the packet-pipeline lifecycle state. Actuator readiness/liveness endpoints remain available.

### UDP-to-Kafka PDW flow

- UDP bind address, port, receiver ID, Kafka bootstrap servers, topic, and bounded publish-queue capacity are configurable through environment variables; defaults are listed in `backend/src/main/resources/application.properties`.
- The receiver accepts the format in `.docs/interfaces/pdw-format-description.md`: a 22-byte big-endian header, packed 24-byte little-endian PDWs, then optional filler. It checks sync word, declared PDW length, and CRC-32 over only the PDWs; filler is ignored.
- This documented wire format is the supplied simulator/test format, not a standardized real receiver protocol; adapt the decoder when the production receiver format is confirmed.
- One Kafka JSON record is published per valid UDP datagram, keyed by receiver ID. Each PDW carries `toaNs`, `rfKHz`, `pwNs`, `aoaHundredthDegree`, and `powerTenthDbm`; units remain the raw documented units. The simulator-only emitter ID and flags are not forwarded.
- The Kafka consumer creates one waterfall row per 100 ms UDP receive-time window. The row contains 200 frequency bins (20 MHz each, 8–12 GHz) by 41 amplitude bins (1 dB each, −80 to −40 dBm), plus input count. Out-of-band frequencies and powers below −80 dBm are excluded from the histogram and counted as ignored.
- `/ws/waterfall` exchanges JSON status/configuration messages, then sends binary `PVWF` version-1 tiles. AOA uses `/ws/aoa` and binary `PVAO` version-1 tiles with density counts and a bounded sample of points. Both streams mark packet-sequence gaps. Clients choose receivers and time window; frequency and amplitude filters are applied while creating per-client AOA tiles.
- The receiver ID is configured statically (`RECEIVER_ID`) because the datagram format does not contain one. Dynamic source registration is not implemented.
- Invalid datagrams are discarded and counted. Kafka submission uses a bounded queue and asynchronous producer; when the queue is full, the newest batch is discarded and counted rather than blocking UDP reception. Queue drops, decode rejects, decoded PDWs, publisher results, consumer batches and recovered/expired late batches are reported by `GET /api/pdw/health`. Its Kafka status reports publisher-worker lifecycle, not an active broker probe.
- The Kafka listener consumes poll results in batches (default maximum 1,000 records) and uses a 10-minute poll interval. Sustained rates may still exceed a single keyed receiver partition's throughput; monitor `consumedBatches` against `publishedBatches` and broker consumer-group lag. `KAFKA_AUTO_OFFSET_RESET` defaults to `latest` so a new group does not replay expired history; set it to `earliest` only when intentionally replaying retained data. Rows are revised while retained when delayed consumer batches arrive; batches older than the 30-second event-time window are counted as expired.
- The wire format uses unsigned 64-bit TOA fields. This Java implementation accepts only values through `Long.MAX_VALUE`; larger TOA values are rejected. The simulator's elapsed-time values are expected to remain within that range.
- The topic has 16 partitions, one replica for local development, and a five-minute retention setting. Production Compose expects an externally managed Kafka cluster through `KAFKA_BOOTSTRAP_SERVERS`; the backend requests explicit topic creation/configuration, so either grant that permission or pre-provision `pdw-waterfall-v1` with matching settings. Broker-side implicit topic auto-creation is disabled locally. SASL/TLS credentials and trust configuration for production Kafka are not wired yet; do not expose the local PLAINTEXT configuration to an untrusted network.

Inspect published records in a second terminal:

```powershell
docker compose -f deployment/docker-compose.yml exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic pdw-waterfall-v1 --from-beginning
```

The Maven Wrapper distribution URL is pinned to Maven 3.10.0, and the wrapper runtime is checked in. Backend verification requires a configured Java 25 environment; Docker can also build the backend image when available.

## CI and image publishing

Pull requests run component tests and build container images without publishing them. Pushes to `main` and GitHub Releases publish backend and frontend images to GHCR. No cluster deployment is triggered. GitHub Actions uses the built-in `GITHUB_TOKEN` with package write permission.

The deployment workflow validates Compose syntax and Kubernetes YAML structure. Kubernetes manifests contain placeholder image references and ingress host; replace those values with the target registry and cluster domain before applying them. These manifests are examples, not an automatically deployed environment.

## Endpoints

- `GET /api/health` — application and packet-pipeline status
- `GET /api/pdw/health` — UDP receiver/Kafka publisher lifecycle and ingest counters
- `/ws/waterfall` — per-receiver server-aggregated binary waterfall tiles
- `/ws/aoa` — per-receiver polar tiles with bounded point samples and density counts
- `GET /actuator/health/readiness` and `/actuator/health/liveness` — runtime probes
- `ws://<host>/ws/packets` — legacy demo packet-metadata stream
- `udp://<host>:5005` — documented PDW datagram input (configurable)
- Kafka topic `pdw-waterfall-v1` — JSON batches, one record per valid UDP datagram

Start, Detailansicht, and Einstellungen remain placeholder pages; Live-Übersicht displays the live waterfall and AOA view when PDW data is available.
