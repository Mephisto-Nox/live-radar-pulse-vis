# Pulse Vision

Monorepo for the Pulse Vision radar receiver UI and its Java API. The current implementation is deliberately a runnable skeleton: no receiver integration, PDW ingestion, analysis, broker, persistence, or live visualization is implemented.

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

The Maven Wrapper distribution URL is pinned to Maven 3.10.0, and the wrapper runtime is checked in. Backend verification requires a configured Java 25 environment; Docker can also build the backend image when available.

## CI and image publishing

Pull requests run component tests and build container images without publishing them. Pushes to `main` and GitHub Releases publish backend and frontend images to GHCR. No cluster deployment is triggered. GitHub Actions uses the built-in `GITHUB_TOKEN` with package write permission.

The deployment workflow validates Compose syntax and Kubernetes YAML structure. Kubernetes manifests contain placeholder image references and ingress host; replace those values with the target registry and cluster domain before applying them. These manifests are examples, not an automatically deployed environment.

## Null implementation endpoints

- `GET /api/health` — application status
- `GET /actuator/health/readiness` and `/actuator/health/liveness` — runtime probes

The frontend contains navigable placeholder pages for Start, Live-Übersicht, Detailansicht, and Einstellungen. All display that data processing is not yet active.
