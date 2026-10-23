# Arquitectura — Localizator (MVP)

> Documento vivo. Se actualiza en cada fase si cambia alguna decisión.
>
> Diseño detallado del faro y del emparejamiento: [`FARO.md`](FARO.md). Radar: [`RADAR.md`](RADAR.md).

## 1. Objetivo

Encontrar un **teléfono perdido a corta distancia** (0–30 m) con la **mayor precisión
posible**. El escenario de referencia es un recital: una multitud, mucho ruido de radio
y la persona sabe que el teléfono está "por aquí cerca".

- **Plataforma:** solo Android (Kotlin nativo).
- **Tecnología:** Bluetooth Low Energy (BLE), midiendo la intensidad de señal (RSSI).

## 2. Cómo lo resuelven otros

Samsung *SmartThings Find* y Google *Find Hub* (antes *Find My Device*) funcionan así:

1. El teléfono **ya tiene el software instalado y emparejado** con la cuenta del dueño
   antes de perderse.
2. Mientras está perdido, **emite un "faro" BLE** con un identificador que cambia cada
   cierto tiempo, para que nadie más pueda seguirlo.
3. El dueño, con otro dispositivo, **reconoce ese faro** porque conoce el secreto que
   genera los identificadores, y navega hacia él usando la intensidad de la señal (o UWB
   si ambos equipos lo tienen).
4. Como último paso, **lo hace sonar**.

**Decisión clave:** copiamos este modelo. Intentar localizar con precisión un teléfono
cualquiera, sin nuestra app, es poco fiable por dos motivos:

| Problema sin app propia | Cómo lo resuelve el faro propio |
|---|---|
| La MAC del teléfono cambia cada ~15 min → "perdemos" el dispositivo | El ID rotativo se deriva de un secreto compartido, así que el buscador siempre lo reconoce |
| No sabemos con qué potencia emite → la distancia sale mal | El faro incluye su potencia de emisión y su calibración |
| Entre cientos de dispositivos en un recital, ¿cuál es el mío? | Solo el mío tiene mi ID |
| No se le puede pedir que suene | Conexión GATT autenticada → "¡suena!" |

Igualmente se mantiene un **modo genérico** (escanear todo lo cercano) como herramienta
de diagnóstico y para casos sin app, sabiendo que es menos preciso.

## 3. Roles de la app

La misma app tiene dos modos:

```
  TELÉFONO A (dueño, protegido)              TELÉFONO B (el que busca)
  ┌──────────────────────────┐               ┌──────────────────────────┐
  │ MODO PROTEGIDO           │   anuncios    │ MODO BUSCADOR            │
  │ • servicio en 2º plano   │ ──BLE~4/s───▶ │ • escanea y reconoce ID  │
  │ • emite ID rotativo      │               │ • filtra y estima dist.  │
  │ • servidor GATT "sonar"  │ ◀──GATT────── │ • guía caliente/frío     │
  └──────────────────────────┘   "¡suena!"   │ • brújula de dirección   │
             ▲                               └──────────────────────────┘
             └────── emparejamiento previo por código QR (secreto) ─────┘
```

El teléfono B puede ser el de un amigo: basta con instalar la app y escanear el QR que el
dueño guardó (o volver a emparejar desde una copia de seguridad del secreto).

## 4. Protocolo del faro (anuncio BLE)

Un anuncio BLE "legacy" tiene **31 bytes** como máximo. Usamos un anuncio **no
conectable** con un único campo *Service Data*:

```
 Service Data (AD type 0x21, UUID de 128 bits)    = 2 + 16 + 10 = 28 bytes
 ├─ UUID de servicio Localizator  (16 bytes)
 └─ Payload (10 bytes)
    ├─ versión         1 byte
    ├─ ID rotativo     8 bytes = HMAC-SHA256(secreto, ⌊t / 15 min⌋)[0..8]
    └─ P1m calibrado   1 byte  (RSSI esperado a 1 m, dBm con signo)
```

- **Privacidad:** el ID cambia cada 15 min y no se puede predecir sin el secreto.
- **Tolerancia al reloj:** el buscador prueba las ventanas `t-1`, `t` y `t+1`.
- Para "hacer sonar" se usa un **anuncio conectable** aparte, que solo se activa a
  petición. Ese detalle se define en la fase 10.

## 5. Pipeline de precisión (modo buscador)

```
 RSSI crudo (dBm, ~4–10 lecturas/s)
   │
   ▼
 [1] Identificación ── ¿es mi faro? (ID rotativo) / modo genérico: MAC + fabricante
   │
   ▼
 [2] Filtro de Kalman ── suaviza el ruido (±3–6 dB) y resta peso a picos atípicos
   │                     (reflejos, una persona que pasa en medio)
   ▼
 [3] Modelo de pérdida ── d = 10^((P1m − RSSI) / (10·n))
   │   P1m: calibrado (faro propio) > TX Power anunciado > valor típico (−59 dBm)
   │   n:   2.0 espacio libre … 3–4 multitud/interiores
   ▼
 [4] Incertidumbre ── distancia ± error (ej. "2,1 m (1,4–3,2)"), sin falsa exactitud
   │
   ├─▶ [5] Tendencia ── pendiente del RSSI en 3 s → ACERCANDO / ESTABLE / ALEJANDO
   │
   ├─▶ [6] Dirección ── barrido 360° con brújula: el cuerpo bloquea la señal que viene
   │       por detrás, así que el máximo de RSSI apunta al teléfono
   │
   └─▶ [7] Retroalimentación ── vibración/sonido tipo "contador Geiger" cada vez más
           rápido, más "¡hacer sonar!" en el último metro
```

### Precisión esperada (honesta)

| Técnica | Error típico |
|---|---|
| RSSI crudo, sin calibrar | 2–5 m o más (en multitud, peor) |
| + Kalman + P1m calibrado | ~0,5–2 m |
| + tendencia + barrido de dirección | se llega a ~1 m del objetivo de forma guiada |
| + hacer sonar | lo encuentras con el oído |
| UWB (futuro, solo equipos compatibles) | 10–30 cm + ángulo |

**En la práctica, la precisión no la da un número en metros: la da guiar a la persona**
(tendencia, dirección y sonido). Por eso el diseño prioriza esas señales.

## 6. Módulos y capas

```
localizator/
├── core/   Kotlin puro (JVM). SIN dependencias de Android.
│   ├── signal/     KalmanFilter, PathLossModel, TrendDetector, DirectionFinder
│   ├── tracking/   DeviceTracker, DeviceEstimate, Advertisement
│   ├── beacon/     BeaconCodec (codificar/decodificar payload), RotatingId (HMAC)
│   └── calibration/Calibrator
│
└── app/    Android (Jetpack Compose)
    ├── ble/        BleScanner, BeaconAdvertiser, AlarmGattServer
    ├── service/    ProtectionService (foreground service del modo protegido)
    ├── data/       PairingRepository (secreto cifrado), CalibrationRepository
    ├── sensors/    CompassProvider (rotation vector)
    └── ui/         pantallas + ViewModels
        ├── home/       elegir modo
        ├── protect/    activar protección, mostrar QR
        ├── pair/       escanear QR
        ├── nearby/     lista de dispositivos (modo genérico)
        ├── search/     pantalla de búsqueda precisa
        └── calibrate/  asistente de calibración
```

**¿Por qué separar `core`?** Todo lo que define la precisión vive en `core`, así que se
prueba en segundos con datos simulados en cualquier PC, sin teléfono. `app` solo traduce
entre Android y `core`. Este patrón se llama *arquitectura hexagonal* o *puertos y
adaptadores*.

**Patrón de UI:** MVVM. Cada pantalla tiene un `ViewModel` que expone su estado como
`StateFlow`, y la pantalla Compose solo dibuja ese estado.

## 7. Restricciones de Android a tener en cuenta

| Tema | Detalle |
|---|---|
| Permisos (Android 12+) | `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT` |
| Permisos (Android ≤ 11) | `ACCESS_FINE_LOCATION` para escanear |
| Escaneo | `SCAN_MODE_LOW_LATENCY`; Android bloquea más de **5 `startScan` en 30 s** → no reiniciar el escaneo |
| Faro en segundo plano | Requiere *foreground service* (tipo `connectedDevice`) con notificación visible |
| Batería | Faro en `ADVERTISE_MODE_BALANCED` (~4/s): equilibrio entre precisión y batería |
| Brújula | Sensor `TYPE_ROTATION_VECTOR`; se descalibra cerca de metales |
| Versión mínima | `minSdk 26` (Android 8), `targetSdk 35` |

## 8. Pruebas y verificación

1. **Tests unitarios de `core`** con simulaciones (distancia conocida + ruido gaussiano):
   comprueban que el filtrado reduce el error frente al RSSI crudo.
2. **CI en GitHub Actions:** en cada rama compila la app (APK) y ejecuta los tests.
   Así sabemos "si compila" antes de mergear, aunque no tengas Android Studio abierto.
3. **Pruebas manuales con 2 teléfonos:** protocolo de medición a 1, 2, 5 y 10 m,
   anotando la distancia real frente a la estimada.

## 9. Plan de fases (una rama por fase → revisión → merge a `main`)

| # | Rama | Entregable | Cómo se verifica |
|---|---|---|---|
| 01 | `feature/01-arquitectura` | Este documento | Lectura y revisión |
| 02 | `feature/02-core-algoritmos` | Módulo `core`: Kalman, path loss, tendencia, tracker, calibración | Tests unitarios |
| 03 | `feature/03-android-base-ci` | Proyecto Android vacío + permisos + CI | La CI compila el APK |
| 04 | `feature/04-escaneo-generico` | Lista de dispositivos cercanos con distancia | APK en un teléfono |
| 05 | `feature/05-faro-y-emparejamiento` | Modo protegido + ID rotativo + QR | 2 teléfonos: B reconoce a A |
| 06 | `feature/06-busqueda-precisa` | Radar: círculo de distancia, banda de error, brújula, caliente/frío y vibración ([`RADAR.md`](RADAR.md)) | Prueba de campo |
| 07 | `feature/07-ubicacion-gps` | Mapa (OpenStreetMap) con círculo GPS y anillo BLE; ubicación cifrada en la respuesta de escaneo ([`UBICACION.md`](UBICACION.md)) | 2 teléfonos al aire libre |
| 08 | `feature/08-calibracion` | Asistente de calibración a 1 m y 3 m | Error medido antes y después |
| 09 | `feature/09-direccion-brujula` | Barrido de 360° → flecha de dirección sobre la brújula del radar | Prueba de campo |
| 10 | `feature/10-hacer-sonar` | Conexión GATT autenticada → alarma | 2 teléfonos |

## 10. Fuera del MVP (futuro)

- **UWB** (`androidx.core.uwb`) en equipos compatibles (Pixel Pro, Galaxy S Ultra).
- **Red colaborativa:** que otros teléfonos reporten la ubicación GPS del faro a un
  servidor (el "encontrar a distancia" de Samsung y Google). Requiere un backend.
- **Activación remota** del modo "perdido" desde la web.
- iOS.

## 11. Glosario

- **BLE:** Bluetooth Low Energy, la versión de bajo consumo de Bluetooth.
- **Anuncio (advertising):** paquete pequeño que un dispositivo BLE emite periódicamente
  sin necesidad de conexión.
- **RSSI:** *Received Signal Strength Indicator*, intensidad de la señal recibida, en dBm
  (más cerca de 0 = más fuerte; −40 es muy cerca, −90 es lejos).
- **P1m:** RSSI esperado a 1 metro. Es el parámetro de calibración más importante.
- **n (exponente de pérdida):** cuánto se atenúa la señal con la distancia.
- **Filtro de Kalman:** algoritmo que combina una predicción con mediciones ruidosas para
  obtener una estimación más estable.
- **HMAC:** función que genera un código a partir de un secreto; sin el secreto no se
  puede reproducir.
- **GATT:** protocolo BLE para conectarse a un dispositivo y leer o escribir datos.
- **Foreground service:** servicio de Android que sigue funcionando en segundo plano
  mostrando una notificación.

## 12. Recursos para aprender

- Fundamentos de BLE: [Nordic DevAcademy – Bluetooth LE Fundamentals](https://academy.nordicsemi.com/courses/bluetooth-low-energy-fundamentals/)
- BLE en Android: [developer.android.com – BLE overview](https://developer.android.com/develop/connectivity/bluetooth/ble/ble-overview)
- Filtro de Kalman paso a paso: [kalmanfilter.net](https://www.kalmanfilter.net/default.aspx)
- Modelo de pérdida: [Log-distance path loss model](https://en.wikipedia.org/wiki/Log-distance_path_loss_model)
- Arquitectura Android: [Guide to app architecture](https://developer.android.com/topic/architecture)
- Kotlin desde cero: [Kotlin Koans](https://play.kotlinlang.org/koans/overview) y [Android Basics with Compose](https://developer.android.com/courses/android-basics-compose/course)
- Git y ramas: [Learn Git Branching](https://learngitbranching.js.org/?locale=es_AR) (interactivo, en español)
