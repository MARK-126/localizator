# Seguridad

Registro de revisiones de seguridad y mejoras pendientes. Cada revisión indica el commit revisado.

## Modelo de amenazas

| Activo | Protección |
|---|---|
| Identidad del teléfono protegido | ID rotativo `HMAC-SHA256(secreto, ventana)`; sin el secreto no se puede reconocer ni seguir entre ventanas |
| Ubicación GPS compartida | AES-128-CTR con clave derivada del secreto; solo la descifran los buscadores emparejados |
| Secreto de emparejamiento | 128 bits de `SecureRandom`; `SharedPreferences` privadas; solo sale del dispositivo en el QR, por acción del usuario |

Fuera del modelo para el MVP: atacantes activos que falsifiquen o retransmitan anuncios BLE, y
dispositivos con root.

## Revisión 1 — 2026-10-23

- **Alcance:** todo el código en `main` (`86c74df`): `core`, `app`, manifiesto, `FileProvider` y CI.
- **Método:** identificación de vulnerabilidades y filtrado de falsos positivos; solo se informan
  hallazgos con confianza ≥ 8/10.
- **Resultado:** sin vulnerabilidades de confianza alta.

### Verificado

| Área | Resultado |
|---|---|
| Componentes exportados | Solo `MainActivity` (launcher). `ProtectionService` no exportado; `PendingIntent` con `FLAG_IMMUTABLE` e intents explícitos. `CaptureActivity` sin intent filter |
| Receptores | Registrados con `RECEIVER_NOT_EXPORTED` |
| Copia de seguridad | `allowBackup=false` |
| `FileProvider` | No exportado; solo `cache/qr/`; permiso concedido al destinatario de `ACTION_SEND` |
| `PairingCode.parse` | Formato estricto, Base64 dentro de `runCatching`, secreto de 16 bytes; el nombre solo se muestra como texto |
| Aleatoriedad y claves | Secreto de 128 bits con `SecureRandom`; HMAC-SHA256 truncado a 64 bits; clave de ubicación derivada con HMAC |
| Registros y portapapeles | Sin `Log` ni `println`. "Copiar detalles" copia bytes cifrados y el nombre; nunca el secreto ni la ubicación descifrada |
| CI | Sin interpolación de entradas no confiables ni exposición de secretos |

### Mejoras pendientes (descartadas como vulnerabilidad, confianza < 8)

| ID | Hallazgo | Ubicación | Confianza | Motivo del descarte | Mejora |
|---|---|---|---|---|---|
| [SEC-1](https://github.com/MARK-126/localizator/issues/9) | Ubicación cifrada sin integridad (AES-CTR sin MAC): un anuncio retransmitido con bits alterados desplaza la posición mostrada | `core/.../location/LocationPayload.kt` | 3/10 | Requiere estar cerca de ambos teléfonos dentro de ±15 min; el teléfono real sigue emitiendo la posición correcta; retransmitir sin modificar ya confunde el radar | Cifrado autenticado (AES-GCM) o HMAC truncado en el byte reservado |
| [SEC-2](https://github.com/MARK-126/localizator/issues/10) | El contador del IV se reinicia al reiniciar el servicio dentro de la misma ventana: reutilización de (clave, IV) | `app/.../service/ProtectionService.kt` (`sequence`) | 2/10 | El atacante debe estar a menos de 50 m en ambas capturas; solo obtiene la posición de un teléfono que ya tiene al lado | Contador persistente o nonce aleatorio |
| [SEC-3](https://github.com/MARK-126/localizator/issues/11) | La secuencia en claro continúa entre ventanas: permite enlazar el ID rotativo anterior con el nuevo (solo con ubicación compartida) | `ProtectionService.kt`, `LocationPayload.kt` | 3/10 | Enlace probabilístico (256 valores) que exige observación continua, que ya permite enlazar por RSSI y tiempo | Reiniciar o aleatorizar la secuencia por ventana (se resuelve junto con SEC-2) |

## Decisiones posteriores — 2026-10-24

| ID | Tema | Decisión | Compromiso aceptado |
|---|---|---|---|
| SEC-4 | La acción *Detener* de la notificación funciona con la pantalla bloqueada: quien encuentra el teléfono puede apagar el faro sin el PIN | Exigir desbloqueo (`Notification.Action.Builder.setAuthenticationRequired`, Android 12+) | En Android 8–11 no existe; la acción se quita de la notificación y se detiene desde la app |
| SEC-5 | Emisión antes del primer desbloqueo (*Direct Boot*) | Secreto y estado de protección en el almacenamiento protegido por el dispositivo; servicio `directBootAware` iniciado con `LOCKED_BOOT_COMPLETED` | El secreto deja de estar cifrado con la credencial del usuario: con el teléfono en mano y herramientas forenses o root se podría leer. Quien lo obtiene solo puede reconocer el faro de un teléfono que ya tiene. Los emparejamientos del buscador siguen en el almacenamiento cifrado por credencial |
