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
| `ACCESS_FINE_LOCATION` | ≤ 30 | Requisito de escaneo BLE en Android ≤ 11 |
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
│   └── BluetoothState       UNSUPPORTED/OFF/ON; rememberBluetoothState()
└── ui/
    ├── theme/Theme.kt       LocalizatorTheme (color dinámico en Android 12+)
    └── home/HomeScreen.kt   Estado de permisos y Bluetooth
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
| Listo | Confirmación | — |

Los permisos se vuelven a evaluar en cada `ON_RESUME`, por ejemplo al volver de los ajustes.
