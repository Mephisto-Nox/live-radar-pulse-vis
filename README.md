# Pulse Vision

Monorepo for the Pulse Vision radar receiver UI and its Java API. The current implementation contains a minimal end-to-end packet-flow demo: a simulated receiver publishes packet metadata to a broker-neutral in-memory channel, which fans out to a WebSocket and the live dashboard. It does not implement a real receiver protocol, PDW parsing, analysis, Kafka/Redpanda, persistence, or production visualization.

## Repository layout

- `backend/` — Java 25 / Spring Boot REST service and tests
- `frontend/` — Angular 22 application and Nginx runtime image
- `deployment/` — local and production Docker Compose plus Kubernetes manifests
- `scripts/` — shared build and validation helpers
- `.github/workflows/` — component CI and deployment validation

## Toolchain

The project pins the versions documented in `docs/internal/001-Setup-Project-Structure.md`: Temurin JDK 25.0.5+8, Maven 3.10.0 via wrapper, Node.js 26.13.0, npm 12.2.2, and Angular 22.2.5. Dockerfiles also use versioned base-image tags. CI builds each component independently.

## Run locally

Requirements: Docker with the Compose plugin. From the repository root:

```powershell
docker compose -f deployment/docker-compose.yml up --build
```

Open `http://localhost:8080`. The frontend is served by Nginx; `/api/health` is proxied to the backend.

The Live-Übersicht connects to `/ws/packets` and displays generated demo packet metadata (receiver ID, sequence, receive time, and payload length). The demo emits one packet every 250 ms. Only the newest 100 items are kept in the browser.

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

Start the backend from `backend/` in one terminal:

```powershell
.\mvnw.cmd spring-boot:run
```

Start the Angular development server from `frontend/` in another terminal:

```powershell
npm ci
npm start
```

Open the URL printed by Angular CLI (normally `http://localhost:4200`). The development proxy forwards API, actuator, and WebSocket requests to `http://localhost:8080`.

### Packet-flow design

- `PacketReceiver` is the replaceable input boundary; the current implementation is a synthetic demo source.
- `PacketChannel` is the broker-neutral publish/subscribe port; `InMemoryPacketChannel` provides local fan-out without persistence or replay.
- The WebSocket gateway subscribes to the channel independently of the receiver. A future Kafka/Redpanda adapter can implement this port and replace the in-memory channel without changing the receiver contract or frontend message shape.
- The in-memory channel is intentionally not a durable broker: it has no retention, consumer groups, replay, or cross-process delivery. Those semantics belong to a future broker adapter.
- `GET /api/health` reports API status and the packet-pipeline lifecycle state. Actuator readiness/liveness endpoints remain available.

The Maven Wrapper distribution URL is pinned to Maven 3.10.0, and the wrapper runtime is checked in. Backend verification requires a configured Java 25 environment; Docker can also build the backend image when available.

## CI and image publishing

Pull requests run component tests and build container images without publishing them. Pushes to `main` and GitHub Releases publish backend and frontend images to GHCR. No cluster deployment is triggered. GitHub Actions uses the built-in `GITHUB_TOKEN` with package write permission.

The deployment workflow validates Compose syntax and Kubernetes YAML structure. Kubernetes manifests contain placeholder image references and ingress host; replace those values with the target registry and cluster domain before applying them. These manifests are examples, not an automatically deployed environment.

## Endpoints

- `GET /api/health` — application and packet-pipeline status
- `GET /actuator/health/readiness` and `/actuator/health/liveness` — runtime probes
- `ws://<host>/ws/packets` — live packet metadata stream

Start, Detailansicht, and Einstellungen remain placeholder pages; Live-Übersicht displays demo packet metadata only.
