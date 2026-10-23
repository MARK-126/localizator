# Faro propio y emparejamiento — diseño

Permite que un teléfono **buscador** reconozca a un teléfono **protegido** entre cientos de
dispositivos, con un nombre elegido por el dueño, sin que un tercero pueda identificarlo.

## Roles

| Rol | Qué hace | Qué guarda |
|---|---|---|
| Protegido | Emite el faro mientras el modo protegido está activo | Su secreto y su nombre |
| Buscador | Escanea, reconoce el faro y muestra la distancia | Secreto y nombre de cada teléfono emparejado |

Un mismo teléfono puede ser protegido y buscador a la vez.

## Decisiones

| Tema | Decisión |
|---|---|
| Emparejamiento | Código QR. El protegido puede compartir el QR como imagen (galería, nube, impresión) para emparejar otro buscador aunque el protegido ya esté perdido |
| Activación | Interruptor manual. La primera activación muestra una explicación y requiere consentimiento expreso. No se reactiva al reiniciar |
| Frecuencia | `ADVERTISE_MODE_BALANCED` (~4 anuncios/s) |
| Potencia | `ADVERTISE_TX_POWER_MEDIUM` |

## Secreto e identificador

- **Secreto:** 16 bytes aleatorios (`SecureRandom`), generados una vez en el protegido.
- **ID de emparejamiento:** `beacon:` + los primeros 4 bytes de `SHA-256(secreto)` en hexadecimal.
  Es estable y local; nunca se emite.
- **ID rotativo:** `HMAC-SHA256(secreto, ventana)[0..8]`, con
  `ventana = ⌊epoch_ms / 900 000⌋` (15 min) codificada como `int64` big-endian.
- **Tolerancia de reloj:** el buscador acepta las ventanas `t − 1`, `t` y `t + 1` (±15 min).

## Anuncio

Anuncio *legacy*, no conectable, sin nombre ni TX Power, con un único campo *Service Data*:

```
AD 0x21 (Service Data, UUID de 128 bits)              2 + 16 + 10 = 28 bytes
├── UUID  768d753b-11be-48c1-99d7-51df0044d579
└── Payload (10 bytes)
    ├── [0]     versión = 1
    ├── [1..8]  ID rotativo
    └── [9]     P1m (dBm, int8); 0x7F = desconocido
```

El ID rotativo cambia en cada límite de ventana; el emisor reinicia el anuncio con el payload
nuevo. Android también rota la MAC del anuncio, así que la identidad estable solo la conoce
quien tiene el secreto.

## Código de emparejamiento (contenido del QR)

```
LOCALIZATOR:1:<secreto>:<nombre>
```

`<secreto>` y `<nombre>` (UTF-8) van en Base64 URL-safe sin relleno. El buscador rechaza
versiones desconocidas, secretos que no tengan 16 bytes y nombres vacíos.

## Flujos

**Activar la protección (en el protegido)**

1. Pantalla *Proteger* → nombre del teléfono (por defecto, el modelo).
2. Interruptor → la primera vez, diálogo de consentimiento.
3. Se inicia `ProtectionService` (*foreground service* `connectedDevice`, con una notificación
   fija y una acción *Detener*).

**Emparejar (en el buscador)**

- *Escanear QR* con la cámara, o
- *Desde imagen*: elegir una foto del QR guardado, sin permisos de almacenamiento.

**Buscar**

La lista muestra primero *Mis teléfonos* (faros emparejados, con nombre y distancia), aunque haya
filtros activos, y debajo el resto de dispositivos.

## Almacenamiento

`SharedPreferences` privadas de la app. El secreto no sale del dispositivo salvo en el QR. En un
dispositivo con root sería legible; se acepta para el MVP.

## Fuera de alcance de esta fase

Calibración de P1m (fase 07), hacer sonar por GATT (fase 09), activación remota y UWB.
