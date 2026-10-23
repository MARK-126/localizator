# Búsqueda precisa (radar) — diseño

Vista que guía al usuario hacia un teléfono emparejado durante los últimos metros.

## Alcance

| Incluye | No incluye |
|---|---|
| Distancia estimada como círculo y margen de error como banda | Posición del otro teléfono en un mapa (BLE no da dirección) |
| Brújula que gira con el teléfono | Flecha de dirección hacia el objetivo (fase 09, barrido de 360°) |
| Tendencia (acercando / alejando) y vibración por proximidad | Calibración de P1m (fase 08) |

## Navegación

*Buscar* → tocar un teléfono detectado en *Mis teléfonos* → **radar**. *Atrás* (o el gesto del
sistema) vuelve a la lista. *Detalles* abre el informe en crudo existente.

## Pantalla

```
┌────────────────────────────────────┐
│ ←  S25 de Marcos          Detalles │
│                N                   │
│          ·  ░░░░░░░  ·             │  ░  banda [distanceMin, distanceMax]
│        · ░░  ○ ○ ○  ░░ ·           │  ○  círculo en distanceMeters
│     O  · ░░ ○   ●   ○ ░░ ·  E      │  ●  este teléfono (centro)
│        · ░░  ○ ○ ○  ░░ ·           │  N/E/S/O giran con el rumbo
│          ·  ░░░░░░░  ·             │  anillos de escala con su valor en m
│                S                   │
│            2,1 m   ↑               │
│        1,4 – 3,2 m · Acercándote   │
│   [ Vibración ✓ ]                  │
└────────────────────────────────────┘
```

| Elemento | Origen |
|---|---|
| Círculo y banda | `DeviceEstimate.distanceMeters`, `distanceMinMeters`, `distanceMaxMeters` |
| Escala | Primer valor de {1, 2, 5, 10, 20, 30} m mayor o igual que `1,2 × distanceMax` |
| Rumbo | Sensor `TYPE_ROTATION_VECTOR` → azimut, ajustado a la rotación de la pantalla y suavizado con `HeadingFilter` (α = 0,15) |
| Tendencia | `DeviceEstimate.trend` |
| Sin señal | Si el teléfono deja de detectarse, el radar muestra "Señal perdida" y conserva la última distancia en gris |

## Vibración

Un pulso de 30 ms cada `ProximityFeedback.pulseIntervalMs(distancia)`: 150 ms a 0,5 m o menos y
1500 ms a 10 m o más, con interpolación logarítmica entre ambos. Solo vibra mientras el radar está
visible, el teléfono se detecta y el interruptor *Vibración* está activo (por defecto, sí).

## Sensores y permisos

- `TYPE_ROTATION_VECTOR` (fusión de acelerómetro, giroscopio y magnetómetro). Sin el sensor, la
  brújula se oculta y el resto del radar funciona igual.
- `android.permission.VIBRATE` (permiso normal, sin diálogo).
