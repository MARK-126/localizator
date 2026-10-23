# Ubicación aproximada por GPS — diseño

Complementa el radar con un mapa cuando el teléfono buscado puede compartir su posición GPS. El
GPS lleva al usuario a la **zona**; el Bluetooth, a los **últimos metros**.

## Modelo: dos círculos

| Círculo | Centro | Radio | Significado |
|---|---|---|---|
| Influencia (GPS) | Posición GPS anunciada por el protegido | Precisión GPS del protegido (m) | El teléfono está en esta zona |
| Precisión (BLE) | Posición GPS del buscador | `[distanceMin, distanceMax]` del tracker | El teléfono está a esta distancia del buscador |

La **zona probable** es la intersección del círculo GPS con el anillo BLE (trilateración con dos
restricciones). El punto estimado es el del anillo BLE (a `distanceMeters`) más cercano al centro
GPS. Si los círculos no se cortan, se muestran los dos sin zona probable y se indica que hay
inconsistencia (GPS impreciso o antiguo).

Sin posición del protegido (recinto cerrado, permiso denegado, batería baja), el mapa muestra solo
el anillo BLE. Sin posición del buscador, no se muestra el mapa: se usa el radar.

## Transporte: respuesta de escaneo cifrada

El anuncio del faro no cambia. La ubicación viaja en la **respuesta de escaneo** (scan response,
31 bytes), que el buscador obtiene con el escaneo activo de Android. Ambos paquetes comparten la
MAC del anuncio, lo que permite asociarlos.

```
Scan response: AD 0x21 (Service Data, UUID de 128 bits)    2 + 16 + 13 = 31 bytes
├── UUID  768d753b-11be-48c1-99d7-51df0044d579
└── Payload (13 bytes)
    ├── [0]       tipo = 0x02 (ubicación)
    ├── [1]       secuencia (contador por actualización, módulo 256)
    ├── [2..11]   cifrado (10 bytes):
    │             lat int32 BE (1e-7 °) · lon int32 BE (1e-7 °) ·
    │             precisión uint8 (m, saturada a 255) · antigüedad uint8 (×10 s, saturada)
    └── [12]      reservado = 0
```

- **Clave:** `HMAC-SHA256(secreto, "localizator-ubicacion")[0..16]`.
- **Cifrado:** AES-128-CTR con IV = `ventana (int64 BE) ‖ secuencia (uint8) ‖ 0…`. Cambiar la
  secuencia en cada actualización evita reutilizar el flujo de clave dentro de una ventana.
- Un buscador sin el secreto solo ve bytes aleatorios. El tipo `0x02` distingue este payload del
  anuncio (versión `0x01`).

## Frecuencia según batería (protegido)

| Batería | GPS | Faro |
|---|---|---|
| ≥ 30 % | Cada 30 s, `PRIORITY_HIGH_ACCURACY` | `ADVERTISE_MODE_BALANCED` |
| 15–30 % | Cada 5 min, `PRIORITY_BALANCED_POWER_ACCURACY` | `ADVERTISE_MODE_BALANCED` |
| < 15 % | Apagado; se sigue anunciando la última posición con su antigüedad | `ADVERTISE_MODE_LOW_POWER` |

La batería se lee con `ACTION_BATTERY_CHANGED` y se reevalúa al cruzar un umbral.

## Permisos

| Teléfono | Permiso | Cuándo |
|---|---|---|
| Protegido | `ACCESS_FINE_LOCATION` (+ `COARSE`) | Al activar "Compartir ubicación" en *Proteger* (opcional) |
| Protegido | `FOREGROUND_SERVICE_LOCATION` | `ProtectionService` pasa a tipo `connectedDevice \| location` mientras se comparte ubicación |
| Buscador | `ACCESS_FINE_LOCATION` (+ `COARSE`) | Al abrir el mapa por primera vez |

No se requiere `ACCESS_BACKGROUND_LOCATION`: la ubicación se obtiene dentro de un foreground
service iniciado desde la interfaz.

## Mapa

OpenStreetMap con `osmdroid` (`org.osmdroid:osmdroid-android`), sin clave de API. Los mosaicos se
descargan con conexión y se guardan en caché.

## Interfaz

- *Proteger*: interruptor "Compartir ubicación aproximada" (desactivado por defecto).
- Radar: botón *Mapa* cuando hay posición del protegido; el mapa muestra los dos círculos, la zona
  probable, este teléfono, la antigüedad de la posición GPS y la distancia BLE.

## Fuera de alcance

Servidor o internet para posiciones fuera del alcance Bluetooth, historial de ubicaciones y mapas
sin conexión.
