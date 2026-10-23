# Integración continua y control de merges

## Flujo

```
rama feature/NN-… ──push──▶ Pull Request a main ──▶ CI (tests-lint-apk, commits)
                                                     │
                               verde + revisión ─────┴──▶ merge (solo vía PR)
```

- `main` solo recibe cambios mediante Pull Request.
- Una PR se puede mergear únicamente si todas las comprobaciones obligatorias están en verde.
- Título del commit de merge: `Merge feature/NN-nombre` (≤ 45 caracteres).
- Al mergear, GitHub borra la rama de la PR (*Settings → General → Pull Requests →
  Automatically delete head branches*). Se puede restaurar desde la PR con *Restore branch*.

## Workflow `.github/workflows/android.yml`

| Evento | Jobs |
|---|---|
| `pull_request` (cualquier rama destino) | `tests-lint-apk`, `commits` |
| `push` a `main` | `tests-lint-apk` |
| `workflow_dispatch` | `tests-lint-apk` |

Una nueva ejecución de la misma rama cancela la anterior (`concurrency`).

### Job `tests-lint-apk`

| Paso | Comando | Falla si |
|---|---|---|
| Tests de core | `./gradlew :core:test` | Falla algún test |
| Android Lint | `./gradlew :app:lintDebug` | Lint encuentra algún error (los avisos no bloquean) |
| Compilar APK | `./gradlew :app:assembleDebug` | Error de compilación |

Artefactos:

| Nombre | Contenido |
|---|---|
| `localizator-debug-apk` | `app-debug.apk` |
| `informes` | Informe HTML de tests y `lint-results-debug.html` |

### Job `commits`

Ejecuta `.github/scripts/check-commits.sh base..head` sobre los commits de la PR.

| Regla | Comprobación |
|---|---|
| Título ≤ 45 caracteres | `${#subject}` con `LC_ALL=C.UTF-8` (cuenta caracteres, no bytes) |
| Sin coautoría | No hay ninguna línea que empiece por `Co-Authored-By:` (sin distinguir mayúsculas) |

Uso local:

```bash
.github/scripts/check-commits.sh main..HEAD
```

## Protección de `main` (configuración en GitHub)

El bloqueo del merge lo aplica GitHub, no el workflow. Se configura una sola vez en
**Settings → Rules → Rulesets → New ruleset → New branch ruleset**:

| Campo | Valor |
|---|---|
| Ruleset name | `main` |
| Enforcement status | `Active` |
| Target branches | `Include default branch` |
| Restrict deletions | ✔ |
| Require a pull request before merging | ✔ (aprobaciones requeridas: 0) |
| Require status checks to pass | ✔, con `tests-lint-apk` y `commits` |
| Require branches to be up to date before merging | ✔ |
| Block force pushes | ✔ |

Los checks solo aparecen en el buscador después de haberse ejecutado al menos una vez en
una PR.
