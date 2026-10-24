# Compatibilidad — matriz de casos

Objetivo: que la app funcione en cualquier teléfono Android 8+ (`minSdk 26`) y que, cuando algo no
sea posible, lo **diga con un mensaje claro** en lugar de fallar en silencio.

Un teléfono concreto es una combinación de todas las dimensiones. Esta matriz es **viva**: el estado
se actualiza con cada cambio de código y cada prueba en un dispositivo real.

**Estado:** ✅ cubierto · ⚠️ parcial · ❌ no cubierto · ❓ pendiente de prueba

## Decisiones

| Tema | Decisión |
|---|---|
| Diagnóstico | Pantalla de diagnóstico que se muestra automáticamente en el **primer inicio** y cuando cambia la **versión de Android** (`Build.VERSION.SDK_INT`). Informe copiable |
| Teléfono sin emisión BLE (H2) | Se permite usarlo **como buscador**. *Proteger* muestra un mensaje claro de que este teléfono no puede emitir el faro |
| Fallos de emisión (H3, H4) | Se informan en *Proteger* y en la notificación, sin reintentos silenciosos |
| Reinicio del teléfono (S7) | En discusión |

## Diagnóstico

Cada comprobación corresponde a un caso de la matriz.

| Comprobación | API | Casos |
|---|---|---|
| Versión de Android y modelo | `Build.VERSION.SDK_INT`, `Build.MANUFACTURER`, `Build.MODEL` | V*, O* |
| Bluetooth LE | `PackageManager.FEATURE_BLUETOOTH_LE` | H1 |
| Puede emitir el faro | `BluetoothAdapter.isMultipleAdvertisementSupported` (con Bluetooth encendido) | H2 |
| Bluetooth encendido | `BluetoothAdapter.isEnabled` | S1 |
| Ubicación del sistema | `LocationManager.isLocationEnabled` | V1, V2, S2 |
| Google Play Services | `GoogleApiAvailability.isGooglePlayServicesAvailable` | H7, O3 |
| Sensores | `SensorManager.getDefaultSensor` (rotación, magnetómetro, acelerómetro) | H5, H6 |
| Vibrador | `Vibrator.hasVibrator` | H8 |
| Permisos | Bluetooth, ubicación precisa o aproximada, notificaciones, cámara | P1–P5 |
| Optimización de batería | `PowerManager.isIgnoringBatteryOptimizations`, `isPowerSaveMode`, `ActivityManager.isBackgroundRestricted` | S4, S6, O* |
| Lecturas por segundo del faro emparejado | Medición durante el escaneo | H11 |

Ejemplo de informe:

```
Android 11 · motorola moto g(30)
✅ Bluetooth LE           ❌ Puede emitir el faro (H2)
❌ Ubicación del sistema apagada (V2)
✅ Play Services           ⚠️ Sin sensor de rotación (H5)
⚠️ Batería: optimizada (O5)
Lecturas por segundo del faro emparejado: 0,8 (H11)
```

## 1. Versión de Android

| ID | Versión | Qué cambia | Comportamiento esperado | Estado |
|---|---|---|---|---|
| V1 | 8–9 | Escanear exige permiso de ubicación y el interruptor de ubicación encendido; desde 8.1, sin filtros, el escaneo se pausa con la pantalla apagada | Avisar si la ubicación está apagada | ❌ |
| V2 | 10–11 | Igual que V1, con ubicación **precisa** (`ACCESS_FINE_LOCATION`) | Ídem | ❌ |
| V3 | 12 | Permisos `BLUETOOTH_SCAN`, `ADVERTISE`, `CONNECT`; límites para iniciar servicios desde segundo plano | Permisos nuevos; reanudar desde la interfaz | ✅ |
| V4 | 13 | Notificaciones con permiso de ejecución | Si se niega, el faro funciona y el usuario lo sabe | ⚠️ |
| V5 | 14 | Tipo de foreground service obligatorio y coherente con los permisos; la notificación se puede descartar; alarmas exactas denegadas por defecto | Servicio con el tipo correcto | ✅ · ❓ alarmas |
| V6 | 15 | Más límites a servicios iniciados sin interfaz | Ídem | ❓ |

## 2. Hardware

| ID | Caso | Comportamiento esperado | Estado |
|---|---|---|---|
| H1 | Sin Bluetooth LE (instalación manual del APK) | "Teléfono no compatible" | ❌ |
| H2 | BLE sin modo periférico: no puede emitir el faro | Solo buscador, con mensaje claro en *Proteger* | ❌ silencioso |
| H3 | Emisores ocupados por otras apps (`ADVERTISE_FAILED_TOO_MANY_ADVERTISERS`) | Avisar y reintentar | ❌ silencioso |
| H4 | El anuncio no entra (`ADVERTISE_FAILED_DATA_TOO_LARGE`) | Emitir sin la respuesta de ubicación y avisar | ❌ |
| H5 | Sin sensor de rotación | Brújula con acelerómetro y magnetómetro, o radar sin brújula | ⚠️ se oculta, sin alternativa |
| H6 | Sin magnetómetro | Radar sin brújula | ⚠️ |
| H7 | Sin Google Play Services | Ubicación con `LocationManager` | ❌ sin ubicación |
| H8 | Sin vibrador | Solo indicaciones visuales | ❓ |
| H9 | Sin cámara o permiso denegado | Emparejar desde imagen | ✅ |
| H10 | Antena y potencia distintas por modelo | Calibración por pareja | ✅ |
| H11 | El chip entrega un solo resultado por dispositivo (filtro de duplicados) | Detectar la tasa baja y avisar | ❓ |

## 3. Fabricante

| ID | Fabricante | Riesgo | Comportamiento esperado | Estado |
|---|---|---|---|---|
| O1 | Samsung | "Apps en suspensión" detiene el servicio | Guía a "Sin restricciones" | ❌ |
| O2 | Xiaomi / Redmi / POCO | Inicio automático desactivado; ahorro agresivo | Guía específica | ❌ |
| O3 | Huawei / Honor | Sin Play Services (H7); cierre agresivo | H7 + guía | ❌ |
| O4 | OnePlus / Oppo / Realme | Optimización agresiva | Guía | ❌ |
| O5 | Motorola | Casi Android puro; emisión BLE variable en gama baja (H2) | Detectar H2 | ❌ |
| O6 | Google Pixel | Referencia | — | ❓ |
| O7 | Android Go | Poca memoria: el sistema cierra procesos | Reanudar al abrir la app | ⚠️ |

## 4. Ajustes y estado del sistema

| ID | Caso | Comportamiento esperado | Estado |
|---|---|---|---|
| S1 | Bluetooth apagado antes o durante el uso | Avisar y reanudar al encender | ✅ |
| S2 | Ubicación del sistema apagada (protegido que comparte, o V1/V2) | Avisar | ❌ |
| S3 | Modo avión con Bluetooth encendido | Funciona | ❓ |
| S4 | Ahorro de batería del sistema | Puede pausar escaneo y GPS: avisar | ❓ |
| S5 | Pantalla apagada mucho tiempo (Doze): el ID rotativo deja de rotar | Rota igual | ❌ sospechado |
| S6 | Batería de la app "Restringida" | Avisar | ❌ |
| S7 | Reinicio del teléfono | No se reactiva (decisión actual, en discusión) | ✅ |
| S8 | Forzar detención o actualizar la app | Reanudar al abrirla | ✅ |
| S9 | Hora del teléfono desfasada más de 15 min | Avisar del desfase | ⚠️ tolerancia ±15 min |
| S10 | Cambio de zona horaria | Sin efecto (epoch) | ✅ · ❓ |
| S11 | Perfil de trabajo o varios usuarios | Funciona en el perfil activo | ❓ |
| S12 | Auriculares, reloj o auto conectados | Convivencia | ❓ |

## 5. Permisos

| ID | Caso | Comportamiento esperado | Estado |
|---|---|---|---|
| P1 | Bluetooth denegado o "no volver a preguntar" | Explicar y abrir ajustes | ⚠️ |
| P2 | Solo ubicación aproximada (Android 12+) | Avisar: el protegido compartiría una posición de cientos de metros | ⚠️ |
| P3 | Ubicación denegada con "compartir" activado | Desactivar el interruptor y explicar | ⚠️ |
| P4 | Notificaciones denegadas (Android 13+) | Avisar | ⚠️ |
| P5 | Permisos retirados por no usar la app | Volver a pedirlos | ⚠️ |
| P6 | Permiso revocado con el servicio activo | Detener y avisar | ❓ |

## 6. Combinaciones de teléfonos y roles

| ID | Caso | Estado |
|---|---|---|
| R1 | Mismo modelo en ambos roles | ✅ S23 Ultra ↔ S25 Ultra |
| R2 | Marcas distintas (Samsung ↔ Motorola) | ❓ |
| R3 | Buscador antiguo y protegido nuevo, y al revés | ❓ |
| R4 | Un teléfono protegido y buscador a la vez | ❓ |
| R5 | Varios buscadores emparejados al mismo protegido | ❓ |
| R6 | Un buscador con varios teléfonos emparejados | ❓ |
| R7 | Reinstalación en el protegido: secreto nuevo, emparejamiento viejo inútil | ⚠️ sin aviso |
| R8 | Versiones con protocolo distinto | ⚠️ se rechaza sin mensaje |

## 7. Emparejamiento

| ID | Caso | Estado |
|---|---|---|
| E1 | QR en pantalla con la cámara (brillo, reflejos) | ✅ · ❓ |
| E2 | QR desde captura, foto o impresión | ✅ |
| E3 | QR ajeno, dañado o modificado | ✅ |
| E4 | Emparejar dos veces el mismo teléfono | ❓ |
| E5 | Nombre con emojis o muy largo | ❓ |

## 8. Entorno de radio (pruebas de campo)

| ID | Caso | Estado |
|---|---|---|
| A1 | Aire libre con línea de vista | ❓ |
| A2 | Interior con paredes | ❓ |
| A3 | Multitud (recital) | ❓ |
| A4 | En bolsillo, mochila o bajo un almohadón | ❓ |
| A5 | Cientos de dispositivos BLE | ❓ |
| A6 | Fuera de alcance y regreso | ❓ |
| A7 | Boca abajo sobre metal | ❓ |

## Estrategia de prueba

- **Lógica** (`core`): tests unitarios en la CI.
- **Capacidades**: el diagnóstico convierte cada caso ❌ en una comprobación automática con mensaje.
- **Dispositivos**: no se puede probar cada combinación. Se eligen teléfonos que cubran por pares
  versión × fabricante × capacidad (*pairwise testing*) y se registra aquí cada informe de diagnóstico.

## Dispositivos probados

| Teléfono | Android | Rol | Resultado |
|---|---|---|---|
| Samsung Galaxy S23 Ultra | — | Buscador | Funciona (fases 04–08) |
| Samsung Galaxy S25 Ultra | — | Protegido | Funciona (fases 04–08) |
| Motorola (modelo pendiente) | — | Pendiente | No reconocido; causa por diagnosticar (H2 o V1/V2) |
