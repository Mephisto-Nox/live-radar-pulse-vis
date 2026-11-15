# Pulse Vision

Monorepo for the Pulse Vision radar receiver UI and its Java API. The backend accepts the documented UDP datagram format, validates and decodes the PDW fields used by the waterfall, then publishes one JSON batch per UDP datagram to Apache Kafka. The existing dashboard remains a separate demo metadata stream; it is not a production visualization consumer.

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

Open `http://localhost:8080`. The frontend is served by Nginx; `/api/health` and `/api/pdw/health` are proxied to the backend. Kafka is available on `localhost:9092`; the backend uses its internal Compose address `kafka:9092`. Send simulator datagrams to `127.0.0.1:5005` (UDP).

The Live-Übersicht connects to `/ws/packets` and displays generated demo packet metadata (receiver ID, sequence, receive time, and payload length). The demo emits one packet every 250 ms. Only the newest 100 items are kept in the browser. PDW batches are published to the separate Kafka topic `pdw-waterfall-v1`.

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

- `PacketReceiver` is the replaceable input boundary; the current implementation is a synthetic demo source.
- The existing `PacketChannel`/`InMemoryPacketChannel` and WebSocket path remains a separate demo metadata stream for the current dashboard; it is not used for PDW delivery.
- The UDP PDW path publishes directly to Kafka through `PdwKafkaPublisher`; it is configured independently from the demo UI path.
- `GET /api/health` reports API status and the packet-pipeline lifecycle state. Actuator readiness/liveness endpoints remain available.

### UDP-to-Kafka PDW flow

- UDP bind address, port, receiver ID, Kafka bootstrap servers, topic, and bounded publish-queue capacity are configurable through environment variables; defaults are listed in `backend/src/main/resources/application.properties`.
- The receiver accepts the format in `.docs/interfaces/pdw-format-description.md`: a 22-byte big-endian header, packed 24-byte little-endian PDWs, then optional filler. It checks sync word, declared PDW length, and CRC-32 over only the PDWs; filler is ignored.
- This documented wire format is the supplied simulator/test format, not a standardized real receiver protocol; adapt the decoder when the production receiver format is confirmed.
- One Kafka JSON record is published per valid UDP datagram, keyed by receiver ID. Each PDW carries `toaNs`, `rfKHz`, `pwNs`, `aoaHundredthDegree`, and `powerTenthDbm`; units remain the raw documented units. The simulator-only emitter ID and flags are not forwarded.
- The receiver ID is configured statically (`RECEIVER_ID`) because the datagram format does not contain one. Dynamic source registration is not implemented.
- Invalid datagrams are discarded and counted. Kafka submission uses a bounded queue and asynchronous producer; when the queue is full, the newest batch is discarded and counted rather than blocking UDP reception. Queue drops, decode rejects, decoded PDWs, and publish results are reported by `GET /api/pdw/health`. Its Kafka status reports publisher-worker lifecycle, not an active broker probe.
- The wire format uses unsigned 64-bit TOA fields. This Java implementation accepts only values through `Long.MAX_VALUE`; larger TOA values are rejected. The simulator's elapsed-time values are expected to remain within that range.
- The topic has 16 partitions, one replica for local development, and a five-minute retention setting. The production Compose file expects an externally managed Kafka cluster through `KAFKA_BOOTSTRAP_SERVERS`; ensure the service account can create/configure the topic or provision it with matching settings. SASL/TLS credentials and trust configuration for production Kafka are not wired yet; do not expose the local PLAINTEXT configuration to an untrusted network.

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
- `GET /actuator/health/readiness` and `/actuator/health/liveness` — runtime probes
- `ws://<host>/ws/packets` — live packet metadata stream
- `udp://<host>:5005` — documented PDW datagram input (configurable)
- Kafka topic `pdw-waterfall-v1` — JSON batches, one record per valid UDP datagram

Start, Detailansicht, and Einstellungen remain placeholder pages; Live-Übersicht displays demo packet metadata only.
