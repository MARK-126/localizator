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
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | todas | Escaneo BLE en Android ≤ 11; compartir ubicación (protegido) y mostrarla en el mapa (buscador). Se solicitan al activar cada función |
| `BLUETOOTH_SCAN` (`neverForLocation`) | ≥ 31 | Escaneo |
| `BLUETOOTH_ADVERTISE` | ≥ 31 | Faro del modo protegido |
| `BLUETOOTH_CONNECT` | ≥ 31 | Activar Bluetooth, GATT |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `FOREGROUND_SERVICE_LOCATION` | todas / ≥ 34 | `ProtectionService` (`connectedDevice`, más `location` al compartir ubicación) |
| `INTERNET`, `ACCESS_NETWORK_STATE` | todas | Descarga de mosaicos de OpenStreetMap |
| `POST_NOTIFICATIONS` | ≥ 33 | Notificación del modo protegido (se pide al activarlo; sin ella el faro funciona igual) |
| `VIBRATE` | todas | Pulsos de proximidad en el radar |
| `CAMERA` | todas | Escanear el QR; la declara y la solicita ZXing (`zxing-android-embedded`) |

`neverForLocation` declara que el escaneo no se usa para derivar la ubicación física, así
que en Android 12+ no se pide ubicación. Como consecuencia, Android filtra de los resultados
algunos formatos de beacon (iBeacon, Eddystone). Al faro propio (Service Data con UUID de
128 bits) no le afecta.

`uses-feature android.hardware.bluetooth_le required=true`: la app no se ofrece a
dispositivos sin BLE.

## Estructura

```
app/src/main/kotlin/io/localizator/app/
├── LocalizatorApp.kt        Application: repositorios compartidos (Context.localizatorApp)
├── MainActivity.kt          Punto de entrada; reanuda ProtectionService si el interruptor está activo
├── ble/
│   ├── BluetoothPermissions Permisos de ejecución por versión; allGranted()
│   ├── BluetoothState       UNSUPPORTED/OFF/ON; rememberBluetoothState()
│   ├── BleScanner           Escaneo BLE como Flow<ScanSample>
│   ├── AdvertisementDetails Contenido en crudo de un anuncio; ScanSample
│   └── BeaconAdvertiser     Emisión del faro
├── data/
│   ├── ProtectionRepository     Secreto, nombre, interruptor y consentimiento propios
│   └── PairedBeaconsRepository  Teléfonos emparejados
├── service/ProtectionService    Foreground service que emite el faro
├── location/LocationSource  Fused Location Provider (Play Services)
├── qr/QrCodes               Generar y leer QR (ZXing)
├── sensors/
│   ├── BatteryMonitor       Nivel y carga desde ACTION_BATTERY_CHANGED
│   ├── CompassProvider      Azimut desde TYPE_ROTATION_VECTOR
│   └── Haptics              Pulsos de vibración
└── ui/
    ├── theme/Theme.kt       LocalizatorTheme (color dinámico en Android 12+)
    ├── home/HomeScreen.kt   Estado de permisos y Bluetooth; muestra MainScreen si está listo
    ├── main/MainScreen.kt   Navegación inferior: Buscar / Proteger; radar y mapa dentro de Buscar
    ├── map/MapScreen.kt     Mapa OpenStreetMap: círculo GPS, anillo BLE y posición estimada
    ├── radar/RadarScreen.kt Búsqueda precisa de un teléfono emparejado
    ├── nearby/
    │   ├── NearbyViewModel  Escaneo + reconocimiento de faros + DeviceTracker + NearbyFilter
    │   ├── NearbyScreen     Mis teléfonos, emparejar y lista de dispositivos
    │   └── DeviceDetailsDialog  Detalle en crudo de un dispositivo, copiable
    └── protect/
        ├── ProtectViewModel Modo protegido
        └── ProtectScreen    Nombre, interruptor, consentimiento, QR y copia de respaldo
```

El protocolo del faro y los flujos están en [`FARO.md`](FARO.md); el radar, en [`RADAR.md`](RADAR.md); la ubicación por GPS, en [`UBICACION.md`](UBICACION.md).

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
| Listo | `MainScreen` | — |

Los permisos se vuelven a evaluar en cada `ON_RESUME`, por ejemplo al volver de los ajustes.

### `ble.BleScanner`

`scan(): Flow<ScanSample>`, un *cold flow*: el escaneo empieza al colectar y se detiene al
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
| `name` | `DeviceNames.sanitize(scanRecord.deviceName ?: device.name)` |
| `rssi` | `rssi` |
| `txPower` | `scanRecord.txPowerLevel`; `null` si vale `Int.MIN_VALUE` |
| `timestampMs` | `timestampNanos / 10⁶` (base `elapsedRealtime`) |
| `manufacturerId` | Primera clave de `manufacturerSpecificData` |
| `manufacturerIds` | Todas las claves de `manufacturerSpecificData` |
| `category` | `DeviceCategory.fromMajorDeviceClass(device.bluetoothClass?.majorDeviceClass)` |

`ScanSample.details` (`AdvertisementDetails`):

| Campo | Origen |
|---|---|
| `manufacturerData` | Company ID → datos en hexadecimal |
| `serviceUuids` | `scanRecord.serviceUuids`; `0xXXXX` si deriva del Base UUID con 16 bits |
| `serviceData` | `scanRecord.serviceData`, UUID → datos en hexadecimal |
| `connectable` | `ScanResult.isConnectable` |
| `rawBytes` | `scanRecord.bytes` en hexadecimal |

Errores que terminan el flujo: `SecurityException` (faltan permisos), `IllegalStateException`
(Bluetooth no disponible, o `STATE_TURNING_OFF`/`STATE_OFF` durante el escaneo) y
`ScanFailedException(errorCode)` (`onScanFailed`). `NearbyViewModel` reintenta cada 6 s, así que
el escaneo se reanuda solo al volver a encender el Bluetooth.

### `ui.nearby.NearbyViewModel`

`uiState: StateFlow<NearbyUiState>`:

| Campo | Contenido |
|---|---|
| `devices` | Dispositivos que pasan `filter`, ordenados por distancia ascendente |
| `totalCount` | Dispositivos detectados antes de filtrar |
| `filter` | `NearbyFilter` actual |
| `selected` | `SelectedDevice(estimate, details)` del dispositivo abierto, o `null` |

Acciones: `setMaxDistance(meters)`, `setAndroidPhonesOnly(enabled)` y `select(id)`
(`null` cierra el detalle). Los `AdvertisementDetails` se guardan por dispositivo y se descartan
con el snapshot. Un cambio de filtro se
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
| Distancia | `distanceMeters` + tendencia (`↑` acercando, `↓` alejando, `•` estable); `< 0.1 m` por debajo de 0,1 m |
| Rango | `distanceMinMeters – distanceMaxMeters` |

Al tocar una fila se abre `DeviceDetailsDialog`.

### `ui.nearby.DeviceDetailsDialog`

Informe en texto monoespaciado, una clave por línea: `mac`, `name`, `category`, `rssi`,
`distance`, `model`, `trend`, `samples`, `connectable`, `manufacturer …`, `service`,
`service data …` y `raw`. Se actualiza con cada snapshot. **Copiar** lleva el informe al
portapapeles.

### `data.ProtectionRepository`

`SharedPreferences` `protection`: `secret` (Base64, se genera en el primer acceso), `name` (por
defecto `Build.MODEL`), `enabled`, `consent` y `share_location` (por defecto `false`). Expone `state: StateFlow<ProtectionState>`.

### `data.PairedBeaconsRepository`

`SharedPreferences` `paired_beacons`: conjunto de códigos de emparejamiento. Expone
`beacons: StateFlow<List<PairedBeacon>>`, ordenada por nombre. `add` reemplaza un emparejamiento
con el mismo ID; `remove(id)` lo elimina.

### `ble.BeaconAdvertiser`

`start(payload)` reinicia el anuncio: legacy, no conectable, `ADVERTISE_MODE_BALANCED`,
`ADVERTISE_TX_POWER_MEDIUM`, sin límite de tiempo, sin nombre ni TX Power y con Service Data
`BeaconProtocol.SERVICE_UUID → payload`. Devuelve `false` si faltan permisos o Bluetooth. Los
rechazos asíncronos (`onStartFailure`) se notifican con `onFailure(errorCode)`.

### `service.ProtectionService`

Foreground service de tipo `connectedDevice`, con una notificación fija (canal `protection`,
importancia baja) y la acción *Detener*. El bucle de emisión:

| Evento | Acción |
|---|---|
| Nueva ventana del ID rotativo | Payload nuevo (`BeaconCodec.encode(RotatingId(...), P1m desconocido)`) y reinicio del anuncio |
| Bluetooth vuelve a `STATE_ON` | Reinicio inmediato del anuncio (Android lo detiene al apagar el Bluetooth sin notificarlo) |
| Fallo del emisor | Reintento en ≤ 30 s |
| Acción *Detener* | `enabled = false` y `stopSelf()` |

Devuelve `START_STICKY`. No se inicia al arrancar el teléfono; `MainActivity` lo reanuda al abrir
la app si `enabled` es `true` y hay permisos.

### `ScanSample.beaconPayload`

`scanRecord.getServiceData(SERVICE_UUID)`. `NearbyViewModel` lo decodifica y lo compara con
`BeaconMatcher` (reconstruido al cambiar los emparejados). Si coincide, el anuncio se registra con
`id = PairedBeacon.id` y `name = PairedBeacon.name`, de modo que la identidad no cambia aunque
rote la MAC. Si el payload trae P1m, se aplica como calibración de ese ID.

### Emparejar (`NearbyScreen`)

| Opción | Mecanismo |
|---|---|
| Escanear QR | `ScanContract` de ZXing (solo QR, sin pitido). `CaptureActivity` se fuerza a vertical en el manifiesto (`tools:replace="screenOrientation"`) |
| Desde imagen | `PickVisualMedia` (sin permisos) → `QrCodes.loadBitmap` (lado ≤ 1600 px) → `QrCodes.decode` |

El resultado se muestra en un `Toast`: el nombre emparejado o "código no válido". *Mis teléfonos*
lista los emparejados aunque haya filtros activos. Al tocar uno detectado se abre el radar
(`onOpenRadar`); uno no detectado se olvida tocándolo. Desde el radar, *Detalles* abre el informe
en crudo, con la opción *Olvidar*.

### `ui.protect.ProtectScreen`

| Elemento | Comportamiento |
|---|---|
| Nombre | Campo editable; se guarda con *Guardar nombre* o con la acción *Done* del teclado |
| Interruptor | La primera activación muestra el diálogo de consentimiento; en Android 13+ solicita `POST_NOTIFICATIONS` antes de iniciar el servicio |
| QR | `QrCodes.encode(PairingCode, 768 px)` |
| Compartir copia de respaldo | PNG en `cache/qr/`, compartido mediante `FileProvider` (`${applicationId}.files`) con `ACTION_SEND` |

### `sensors.CompassProvider`

`azimuthDegrees(): Flow<Double>`: `TYPE_ROTATION_VECTOR` a `SENSOR_DELAY_UI` →
`getRotationMatrixFromVector` → `remapCoordinateSystem` según `Display.rotation` →
`getOrientation`. Devuelve grados desde el norte magnético, en [0, 360). Si no hay sensor, el flujo
termina sin emitir (`isAvailable = false`).

`NearbyViewModel.heading: StateFlow<Double?>` aplica `HeadingFilter` y comparte la suscripción
(`WhileSubscribed(5 s)`): el sensor solo está activo mientras el radar es visible.

### `sensors.Haptics`

`pulse(ms)`: `VibrationEffect.createOneShot` con amplitud por defecto, mediante `VibratorManager`
en Android 12+ y `Vibrator` en versiones anteriores. No hace nada sin vibrador.

### `ui.radar.RadarScreen`

| Elemento | Comportamiento |
|---|---|
| Escala | Primer valor de {1, 2, 5, 10, 20, 30} m ≥ 1,2 × `distanceMaxMeters`; 4 anillos y etiqueta a 45° |
| Banda | Anillo entre `distanceMinMeters` y `distanceMaxMeters` (α = 0,18) |
| Círculo | `distanceMeters`, línea discontinua |
| Brújula | Marcas cada 30° y N/E/S/O en `bearing − heading`: el norte de la pantalla sigue al norte real |
| Sin señal | Conserva la última estimación con el color `outline` y muestra "Señal perdida" |
| Vibración | Pulso de 30 ms cada `ProximityFeedback.pulseIntervalMs(distancia)` mientras haya señal y el interruptor esté activo |
| Atrás | `BackHandler` y el botón ← vuelven a la lista |

### Ubicación compartida (protegido)

`ProtectionService` combina `BatteryMonitor` con `BatteryPolicy` y, si `share_location` está activo
y hay permiso, solicita posiciones a `LocationSource` con el intervalo y la prioridad del modo:

| `PowerMode` | GPS | Anuncio |
|---|---|---|
| `NORMAL` | 30 s, `PRIORITY_HIGH_ACCURACY` | `ADVERTISE_MODE_BALANCED` |
| `SAVING` | 5 min, `PRIORITY_BALANCED_POWER_ACCURACY` | `ADVERTISE_MODE_BALANCED` |
| `CRITICAL` | Apagado (se mantiene la última posición) | `ADVERTISE_MODE_LOW_POWER` |

Con una posición disponible, el anuncio lleva una respuesta de escaneo
(`LocationPayload.SERVICE_UUID → LocationPayload.encode(...)`) y se reinicia con cada posición
nueva, con cada cambio de modo y al menos cada 60 s (antigüedad), incrementando la secuencia. El
tipo de foreground service incluye `location` solo si se comparte ubicación con permiso; si el
sistema lo rechaza, el servicio continúa como `connectedDevice`.

### Ubicación recibida (buscador)

`ScanSample.locationPayload = scanRecord.getServiceData(LocationPayload.SERVICE_UUID)`. Si el
anuncio del mismo resultado coincide con un teléfono emparejado, `NearbyViewModel` descifra la
ubicación con su secreto y el ID rotativo del anuncio, y la guarda como
`ReceivedLocation(location, receivedAtMs)`. Se expone en `MyPhone.location` y se conserva aunque el
teléfono deje de detectarse. `ageSeconds(now)` suma la antigüedad anunciada y el tiempo desde la
recepción.

### `ui.map.MapScreen`

| Elemento | Origen |
|---|---|
| Mapa | osmdroid, `TileSourceFactory.MAPNIK`, zoom inicial 19, caché en `cache/osmdroid`; `userAgentValue = packageName` |
| Posición propia | `NearbyViewModel.ownLocationUpdates()` (5 s, alta precisión); solicita permiso de ubicación al abrir |
| Círculo GPS (azul) | `MyPhone.location`: centro y radio = precisión |
| Anillo BLE (naranja) | Centro en la posición propia, radio exterior `distanceMax`, hueco `distanceMin` |
| Posición estimada | `LocationFusion.fuse(...)`, solo si es `consistent` |
| Resumen | GPS (precisión y antigüedad), Bluetooth (rango) y "~d m hacia el <rumbo de 8 puntos>", o aviso de inconsistencia |

El radar abre el mapa con *Mapa*; *Atrás* vuelve al radar.
