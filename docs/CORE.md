# Módulo `core` — referencia técnica

Biblioteca Kotlin/JVM sin dependencias de Android. Convierte anuncios BLE en estimaciones
de distancia con incertidumbre y tendencia.

- **Lenguaje:** Kotlin 2.0, bytecode JVM 17.
- **Dependencias:** solo `kotlin-stdlib`; en tests, `kotlin-test` (JUnit 5).

## Compilación y tests

```bash
gradle :core:test
```

## Paquetes

| Paquete | Contenido |
|---|---|
| `io.localizator.core.signal` | `PathLossModel`, `KalmanFilter`, `TrendDetector`, `Trend`, `HeadingFilter`, `ProximityFeedback` |
| `io.localizator.core.calibration` | `Calibrator` |
| `io.localizator.core.location` | `SharedLocation`, `LocationPayload`, `GeoPoint`, `Geo`, `LocationFusion`, `FusedLocation`, `BatteryPolicy`, `PowerMode` |
| `io.localizator.core.beacon` | `BeaconProtocol`, `RotatingId`, `BeaconCodec`, `PairedBeacon`, `PairingCode`, `BeaconMatcher` |
| `io.localizator.core.tracking` | `DeviceTracker`, `Advertisement`, `DeviceEstimate`, `ProximityZone`, `ModelSource`, `Manufacturers`, `DeviceCategory`, `NearbyFilter`, `DeviceNames` |

## Flujo de datos

```
Advertisement ──▶ DeviceTracker.onAdvertisement()
                    ├─ KalmanFilter.update(rssi, t)   → RSSI filtrado, varianza P
                    ├─ TrendDetector.add(t, rssiF)    → Trend
                    ├─ modelFor(id, txPower)          → PathLossModel, ModelSource
                    └─ estimate()                     → DeviceEstimate
```

## `signal.PathLossModel`

`RSSI(d) = P1m − 10·n·log10(d)`

| Miembro | Descripción |
|---|---|
| `measuredPowerAt1m` | P1m (dBm). Por defecto −59. |
| `pathLossExponent` | n (> 0). Por defecto 2.0. |
| `distanceMeters(rssi)` | `10^((P1m − rssi) / (10·n))` |
| `expectedRssi(d)` | Inversa de `distanceMeters`; `d > 0`. |
| `fromAdvertisedTxPower(tx, n)` | `P1m = tx − 41` (convención iBeacon/Eddystone). |

## `signal.KalmanFilter`

Filtro escalar con modelo de paseo aleatorio. Estado: estimación `x` (dBm) y varianza `P` (dB²).

| Paso | Operación |
|---|---|
| Inicialización | `x = z`, `P = r` |
| Predicción | `P += q·max(Δt, 0,01 s)` |
| Gating | atípica si `\|z − x\| > g·√(P + r)` |
| Peso de la lectura | normal: `r`; atípica: `r·k`; `c` atípicas seguidas del mismo signo: `P += (z − x)²` y `r` |
| Corrección | `K = P / (P + r')`, `x += K·(z − x)`, `P *= (1 − K)` |

| Parámetro | Símbolo | Por defecto |
|---|---|---|
| `processNoisePerSecond` | q | 0.5 dB²/s |
| `measurementNoise` | r | 16 dB² (σ = 4 dB) |
| `outlierGate` | g | 3.0 |
| `outlierNoiseFactor` | k | 10 |
| `sustainedChangeCount` | c | 3 |

No es thread-safe.

## `signal.TrendDetector`

Pendiente del RSSI filtrado por mínimos cuadrados, sobre una ventana deslizante.

| Parámetro | Por defecto |
|---|---|
| `windowMs` | 3000 ms |
| `thresholdDbPerSecond` | 0.8 dB/s |
| `minSamples` | 5 |

`trend()`: `APPROACHING` si la pendiente supera el umbral, `MOVING_AWAY` si es menor que el
umbral negativo, `STEADY` en otro caso y `UNKNOWN` si no hay muestras suficientes.
No es thread-safe.

## `signal.HeadingFilter`

Suavizado exponencial del rumbo sobre el vector unitario `(cos θ, sin θ)`, lo que evita el salto
en el cruce 359° → 0°. `update(degrees)` devuelve el rumbo en [0, 360); `normalize(degrees)` lleva
cualquier ángulo a ese rango. Parámetro `alpha` en (0, 1], por defecto 0,15. No es thread-safe.

## `signal.ProximityFeedback`

`pulseIntervalMs(d)`: intervalo entre pulsos de vibración, interpolado en escala logarítmica.

| Parámetro | Por defecto |
|---|---|
| `nearMeters` → `minIntervalMs` | 0,5 m → 150 ms |
| `farMeters` → `maxIntervalMs` | 10 m → 1500 ms |

Fuera del rango satura; una distancia `NaN` da `maxIntervalMs`.

## `calibration.Calibrator`

Ajusta un `PathLossModel` con muestras tomadas a distancias conocidas. Cada distancia se
resume por la mediana de sus muestras y solo cuentan las que tienen al menos
`MIN_SAMPLES_PER_POINT` (10).

| Distancias válidas | Resultado |
|---|---|
| 0 | `null` |
| 1 | `P1m = mediana + 10·n₀·log10(d)`, con `n = n₀` (`defaultExponent`) |
| ≥ 2 | Regresión lineal sobre `(log10 d, mediana)`, `n = −pendiente/10` acotado a [1.5, 4.5], y P1m recalculado con ese n |

No es thread-safe.

## `tracking.DeviceTracker`

Mantiene un estado por `Advertisement.id`. Es thread-safe (`@Synchronized`).

| Método | Descripción |
|---|---|
| `onAdvertisement(adv)` | Actualiza filtro y tendencia; devuelve `DeviceEstimate`. Los campos `name`, `txPower`, `manufacturerId`, `manufacturerIds` y `category` ausentes conservan el último valor. |
| `snapshot(now)` | Descarta los estados con `now − lastSeen > staleAfterMs` (10 s) y devuelve el resto ordenado por distancia. |
| `estimateFor(id)` | Estimación actual o `null`. |
| `setCalibration(id, model)` / `clearCalibration(id)` | Calibración por dispositivo. |
| `modelFor(id, txPower)` | Prioridad: `CALIBRATED` → `ADVERTISED_TX_POWER` → `DEFAULT`. |

**Incertidumbre:** `σ = √(P + σ_m²)`, con `σ_m` = 2 / 4 / 6 dB para `CALIBRATED` /
`ADVERTISED_TX_POWER` / `DEFAULT`. El intervalo es `[d(RSSI + σ), d(RSSI − σ)]`.

**Zonas** (`ProximityZone.fromDistance`): `IMMEDIATE` < 0,5 m ≤ `NEAR` < 2,5 m ≤ `FAR`.

## `tracking.Manufacturers`

Tabla de Company Identifiers del Bluetooth SIG.

| Método | Resultado |
|---|---|
| `nameOf(id: Int?)` | Nombre, o `null` si `id` es nulo o no está en la tabla |
| `labelOf(id: Int)` | Nombre, o el identificador en hexadecimal (`0x01AB`) |

Fabricantes incluidos: Microsoft `0x0006`, Apple `0x004C`, Samsung `0x0075`, Garmin `0x0087`,
Bose `0x009E`, Google `0x00E0`, Sony `0x012D`. Constantes públicas: `APPLE`, `SAMSUNG`, `GOOGLE`.

## `tracking.DeviceCategory`

Tipo de dispositivo según la *Major Device Class* de Bluetooth.
`fromMajorDeviceClass(major: Int?)`:

| Major class | Categoría |
|---|---|
| `null`, `0x0000` (Miscellaneous), `0x1F00` (Uncategorized) | `UNKNOWN` |
| `0x0200` (Phone) | `PHONE` |
| Cualquier otra | `OTHER` |

Android devuelve `0x0000` cuando no conoce la clase de un dispositivo solo-BLE no emparejado,
por eso se trata como `UNKNOWN`. `DeviceTracker` conserva la última categoría distinta de
`UNKNOWN`.

## `tracking.NearbyFilter`

| Propiedad | Por defecto | Efecto |
|---|---|---|
| `maxDistanceMeters` | 30 m | Descarta `distanceMeters > max` |
| `androidPhonesOnly` | `false` | Solo deja los que cumplen `isLikelyAndroidPhone()` |

`DeviceEstimate.isLikelyAndroidPhone()` es heurístico y evalúa **todos** los fabricantes del
anuncio (`manufacturerIds`), no solo el primero:

| `category` | Resultado |
|---|---|
| `PHONE` | `APPLE ∉ manufacturerIds` |
| `OTHER` | `false` |
| `UNKNOWN` | `manufacturerIds ∩ {SAMSUNG, GOOGLE} ≠ ∅` |

Admite falsos positivos (otros dispositivos Samsung o Google, como auriculares, relojes o
televisores) y falsos negativos (teléfonos que no anuncian fabricante). La identificación exacta
requiere el faro propio.

## `tracking.DeviceNames`

`sanitize(raw: String?)`: recorta los espacios de los extremos. Devuelve `null` si el resultado
queda vacío o contiene caracteres de control o `U+FFFD` (bytes no decodificables como UTF-8).

## Paquete `beacon`

Implementa el protocolo de [`FARO.md`](FARO.md).

| Clase | Responsabilidad |
|---|---|
| `BeaconProtocol` | UUID de servicio, tamaños, `WINDOW_MS = 900 000`, `windowOf(epochMs)`, `nextWindowStart(epochMs)` |
| `RotatingId` | `compute(secret, window) = HMAC-SHA256(secret, int64 BE)[0..8]` |
| `BeaconCodec` | `encode(id, p1m?)` → 10 bytes; `decode(bytes)` → `BeaconPayload?` (tamaño y versión) |
| `PairedBeacon` | Secreto (16 bytes) y nombre; `id = "beacon:" + hex(SHA-256(secret)[0..4])`; `newSecret()` |
| `PairingCode` | `format`/`parse` de `LOCALIZATOR:1:<secreto b64url>:<nombre b64url>` |
| `BeaconMatcher` | `match(payload, nowEpochMs)`: ventanas `t−1..t+1`, IDs esperados en caché por ventana. No es thread-safe |

## Paquete `location`

Implementa [`UBICACION.md`](UBICACION.md).

| Clase | Responsabilidad |
|---|---|
| `LocationPayload` | `encode(location, secret, rotatingId, sequence)` → 13 bytes; `decode(...)` → `SharedLocation?`. Clave `HMAC-SHA256(secret, "localizator-ubicacion")[0..16]`, AES-128-CTR con IV `rotatingId ‖ seq ‖ 0⁷`. Coordenadas int32 en 1e-7°, precisión en m (≤ 255) y antigüedad en unidades de 10 s (≤ 2550 s). UUID de servicio propio |
| `Geo` | `distanceMeters` (haversine), `bearingDegrees` (rumbo inicial), `destination(origen, rumbo, distancia)` sobre esfera de radio 6 371 008,8 m |
| `LocationFusion.fuse` | Punto del anillo BLE (a `distanceMeters`) en el rumbo hacia el centro GPS; `consistent` si `d − precisiónGPS ≤ bleMax` y `d + precisiónGPS ≥ bleMin`; `preferBluetooth` si `bleDistance < precisiónGPS` |
| `BatteryPolicy` | `modeFor(nivel, cargando)`: `NORMAL` (≥ 30 % o cargando), `SAVING` (15–29 %), `CRITICAL` (< 15 %); `gpsIntervalMs`: 30 s, 5 min o `null` |

## Cobertura de tests

| Clase | Comportamiento verificado |
|---|---|
| `PathLossModel` | Distancias de referencia, inversa, conversión de TX Power |
| `KalmanFilter` | Convergencia con ruido σ = 4 dB, rechazo de pico aislado, seguimiento de un cambio sostenido, reacción en ≤ 1 s a 10 Hz |
| `TrendDetector` | Acercamiento, alejamiento y señal estable |
| `Calibrator` | Ajuste con 1 punto, recuperación de P1m y n con 2 puntos y ruido, muestras insuficientes |
| `Manufacturers` | Fabricantes conocidos, desconocidos y nulos, etiqueta hexadecimal |
| `NearbyFilter` | Distancia máxima, filtro Android, heurística por categoría y fabricante, fabricantes no iniciales |
| `location` | Ida y vuelta con precisión de 1e-7°, tamaño de respuesta de escaneo, saturación, dependencia de secuencia e ID, secreto incorrecto, versión y tamaño inválidos; geodesia (destino/distancia/rumbo, 1° ≈ 111 km); fusión consistente e inconsistente; umbrales de batería |
| `HeadingFilter` | Primera lectura, cruce 359°→1°, convergencia, normalización |
| `ProximityFeedback` | Saturación, monotonía, punto medio geométrico |
| `beacon` | ID rotativo determinista y por ventana, codec (P1m, desconocido, versión y tamaño inválidos), código de emparejamiento (ida y vuelta, acentos, inválidos), reconocimiento con ±1 ventana y faros ajenos |
| `DeviceCategory` | Conversión de major class (`0x0000` y `0x1F00` → `UNKNOWN`), anuncio real de un Galaxy S25 Ultra pasa el filtro Android |
| `DeviceNames` | Recorte, vacíos y nulos, bytes no decodificables y caracteres de control |
| `DeviceTracker` | Error filtrado < ½ del crudo y < 0,5 m (3 m, σ = 5 dB), prioridad de modelo, intervalo más estrecho al calibrar, purga y orden, persistencia del nombre y de la categoría |
