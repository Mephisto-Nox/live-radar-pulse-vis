# Project instructions

- Follow the architecture and coding standards in `docs/internal/002-Architecture-Guidelines.md` and `docs/internal/003-Codeing-Guidelines.md`.
- Keep Java/Maven in `backend/`, Angular/npm in `frontend/`, and container/deployment configuration in `deployment/`.
- Preserve the null-implementation scope until explicitly expanded: no PDW ingestion, radar algorithms, broker, persistence, or real-time visualization.
- Pin tool and dependency versions to the project setup document. Run relevant tests and builds before reporting success.
- Keep CI builds separated by component. Publish GHCR images only from main/release workflows; do not deploy automatically to a cluster.
- Do not commit credentials, secrets, generated dependency directories, or build outputs.
