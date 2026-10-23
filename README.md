# Localizator

App Android para **encontrar teléfonos perdidos a corta distancia** (por ejemplo, en un
recital) usando Bluetooth Low Energy, priorizando la precisión.

- Arquitectura y decisiones de diseño: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- Módulos: [`docs/CORE.md`](docs/CORE.md), [`docs/APP.md`](docs/APP.md)
- CI y control de merges: [`docs/CI.md`](docs/CI.md)

## Flujo de trabajo

Cada fase se desarrolla en su propia rama `feature/NN-nombre` y llega a `main` mediante una
Pull Request. La PR solo se puede mergear si la CI está en verde (tests, lint, APK y
convención de commits).
