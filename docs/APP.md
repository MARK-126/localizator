# Módulo `app` — referencia técnica

Aplicación Android con Jetpack Compose. Depende de `:core`.

| Parámetro | Valor |
|---|---|
| `applicationId` / `namespace` | `io.localizator.app` |
| `minSdk` / `targetSdk` / `compileSdk` | 26 / 35 / 35 |
| JVM | 17 |
| UI | Compose BOM 2024.12.01, Material 3 |
| Versiones | `gradle/libs.versions.toml` |

## Compilación

```bash
./gradlew :app:assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew :core:test :app:assembleDebug
```

Requiere el Android SDK (Android Studio o `ANDROID_HOME`).

## CI

Tests, Android Lint y compilación del APK en cada Pull Request. Ver [`CI.md`](CI.md).

**Descargar el APK:** pestaña *Actions* → ejecución de la PR → *Artifacts* →
`localizator-debug-apk` (zip con `app-debug.apk`).

Cada ejecución firma el APK con una clave debug distinta. Para instalar un APK de otra
ejecución hay que desinstalar antes la versión anterior.

## Permisos

| Permiso | Versiones | Uso |
|---|---|---|
| `BLUETOOTH`, `BLUETOOTH_ADMIN` | ≤ 30 | BLE heredado |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | ≤ 30 | Requisito de escaneo BLE en Android ≤ 11. Lint (`CoarseFineLocation`) exige declarar ambos; en ejecución se solicita `FINE`. |
| `BLUETOOTH_SCAN` (`neverForLocation`) | ≥ 31 | Escaneo |
| `BLUETOOTH_ADVERTISE` | ≥ 31 | Faro del modo protegido |
| `BLUETOOTH_CONNECT` | ≥ 31 | Activar Bluetooth, GATT |

`neverForLocation` declara que el escaneo no se usa para derivar la ubicación física, así
que en Android 12+ no se pide ubicación. Como consecuencia, Android filtra de los resultados
algunos formatos de beacon (iBeacon, Eddystone). Al faro propio (Service Data con UUID de
128 bits) no le afecta.

`uses-feature android.hardware.bluetooth_le required=true`: la app no se ofrece a
dispositivos sin BLE.

## Estructura

```
app/src/main/kotlin/io/localizator/app/
├── MainActivity.kt          Punto de entrada; edge-to-edge; HomeScreen
├── ble/
│   ├── BluetoothPermissions Permisos de ejecución por versión; allGranted()
│   ├── BluetoothState       UNSUPPORTED/OFF/ON; rememberBluetoothState()
│   └── BleScanner           Escaneo BLE como Flow<Advertisement>
└── ui/
    ├── theme/Theme.kt       LocalizatorTheme (color dinámico en Android 12+)
    ├── home/HomeScreen.kt   Estado de permisos y Bluetooth; muestra NearbyScreen si está listo
    └── nearby/
        ├── NearbyViewModel  Escaneo + DeviceTracker + NearbyFilter → StateFlow<NearbyUiState>
        └── NearbyScreen     Lista de dispositivos cercanos
```

### `ble.BluetoothPermissions`

- `required`: en API ≥ 31, `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE` y `BLUETOOTH_CONNECT`;
  en otro caso, `ACCESS_FINE_LOCATION`.
- `allGranted(context)`: indica si todos los permisos de `required` están concedidos.

### `ble.BluetoothState`

- `Context.currentBluetoothState()`: `UNSUPPORTED` si no hay adaptador, `ON` u `OFF` en otro caso.
- `rememberBluetoothState()`: `State<BluetoothState>` que se actualiza con
  `ACTION_STATE_CHANGED`. El receptor se registra mientras el composable está en composición.

### `ui.home.HomeScreen`

Estados, evaluados en este orden:

| Condición | Contenido | Acción |
|---|---|---|
| Sin BLE | Mensaje de dispositivo no compatible | — |
| Permisos pendientes | Explicación | `RequestMultiplePermissions`; tras una denegación, también un enlace a los ajustes de la app |
| Bluetooth desactivado | Aviso | `ACTION_REQUEST_ENABLE` |
| Listo | `NearbyScreen` | — |

Los permisos se vuelven a evaluar en cada `ON_RESUME`, por ejemplo al volver de los ajustes.

### `ble.BleScanner`

`scan(): Flow<Advertisement>`, un *cold flow*: el escaneo empieza al colectar y se detiene al
cancelar la colección.

| Ajuste | Valor |
|---|---|
| Modo | `SCAN_MODE_LOW_LATENCY` |
| `reportDelay` | 0 (un resultado por anuncio) |
| Tipo de anuncio | Solo *legacy* (BLE 4.x), compatible con todos los adaptadores |
| Filtros | Ninguno |

Conversión de `ScanResult` a `Advertisement`:

| Campo | Origen |
|---|---|
| `id` | `device.address` |
| `name` | `scanRecord.deviceName`, o `device.name` si no viene en el anuncio |
| `rssi` | `rssi` |
| `txPower` | `scanRecord.txPowerLevel`; `null` si vale `Int.MIN_VALUE` |
| `timestampMs` | `timestampNanos / 10⁶` (base `elapsedRealtime`) |
| `manufacturerId` | Primera clave de `manufacturerSpecificData` |
| `category` | `device.bluetoothClass.majorDeviceClass`: `PHONE` → `PHONE`; `UNCATEGORIZED` o ausente → `UNKNOWN`; otra → `OTHER` |

Errores que terminan el flujo: `SecurityException` (faltan permisos), `IllegalStateException`
(Bluetooth no disponible) y `ScanFailedException(errorCode)` (`onScanFailed`).

### `ui.nearby.NearbyViewModel`

`uiState: StateFlow<NearbyUiState>`:

| Campo | Contenido |
|---|---|
| `devices` | Dispositivos que pasan `filter`, ordenados por distancia ascendente |
| `totalCount` | Dispositivos detectados antes de filtrar |
| `filter` | `NearbyFilter` actual |

Acciones: `setMaxDistance(meters)` y `setAndroidPhonesOnly(enabled)`. Un cambio de filtro se
aplica de inmediato sobre el último snapshot.

| Parámetro | Valor | Descripción |
|---|---|---|
| `REFRESH_INTERVAL_MS` | 500 | Periodo de `DeviceTracker.snapshot(elapsedRealtime)` |
| `STOP_TIMEOUT_MS` | 5000 | `SharingStarted.WhileSubscribed`: el escaneo se detiene 5 s después del último suscriptor |
| `RETRY_DELAY_MS` | 6000 | Espera antes de reintentar un escaneo fallido (límite de Android: 5 inicios cada 30 s) |

Los dispositivos sin anuncios durante 10 s se descartan (`DeviceTracker.staleAfterMs`).

### `ui.nearby.NearbyScreen`

Se suscribe con `collectAsStateWithLifecycle`, así que el escaneo solo funciona con la pantalla
visible (estado `STARTED`).

| Elemento | Contenido |
|---|---|
| Barra de filtros | Chip "Solo Android" y chips de distancia máxima: 5, 15 y 30 m (por defecto 30) |
| Contador | "N de M dispositivos" (visibles de detectados) |
| Estados vacíos | "Buscando…" si no se detectó nada; "Ningún dispositivo coincide" si el filtro lo oculta todo |
| Título | `name`, o el fabricante, o "Dispositivo desconocido" |
| Subtítulo | Fabricante (si el título es el nombre) · MAC |
| Detalle | RSSI filtrado (dBm) · zona de proximidad |
| Distancia | `distanceMeters` + tendencia (`↑` acercando, `↓` alejando, `•` estable) |
| Rango | `distanceMinMeters – distanceMaxMeters` |
