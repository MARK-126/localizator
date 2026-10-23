# Localizator

App Android para **encontrar teléfonos perdidos a corta distancia** (por ejemplo, en un
recital) usando Bluetooth Low Energy, priorizando la precisión.

- Arquitectura y decisiones de diseño: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- Cuaderno de aprendizaje (conceptos y preguntas): [`docs/APRENDIZAJE.md`](docs/APRENDIZAJE.md)
- Faro y emparejamiento: [`docs/FARO.md`](docs/FARO.md)
- Búsqueda precisa (radar): [`docs/RADAR.md`](docs/RADAR.md)
- Ubicación aproximada (mapa): [`docs/UBICACION.md`](docs/UBICACION.md)
- Módulos: [`docs/CORE.md`](docs/CORE.md), [`docs/APP.md`](docs/APP.md)
- CI y control de merges: [`docs/CI.md`](docs/CI.md)

## Flujo de trabajo

Cada fase se desarrolla en su propia rama `feature/NN-nombre` y llega a `main` mediante una
Pull Request. La PR solo se puede mergear si la CI está en verde (tests, lint, APK y
convención de commits).

## Configuración local

Las variables de entorno (claves, tokens, URLs de servicios) van en `.env`, en la raíz del
proyecto. Git ignora `.env` y `.env.*`. Las variables necesarias se documentan, sin valores,
en `.env.example`, que sí se versiona.
