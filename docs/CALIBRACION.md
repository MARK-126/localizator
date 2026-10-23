# Calibración — diseño

Ajusta el modelo de distancia a cada pareja **buscador ↔ teléfono emparejado**. Elimina el error
sistemático de P1m y n (ver [`CORE.md`](CORE.md), `PathLossModel`), que ningún filtrado corrige.

## Por qué por pareja

El RSSI a 1 m depende del emisor (potencia, antena, funda) y también del receptor (antena y
sensibilidad del buscador). Por eso la calibración se guarda **en el buscador** y **por teléfono
emparejado**.

## Asistente

Desde el radar: *Calibrar* → tres pasos.

| Paso | Instrucción | Medición |
|---|---|---|
| 1 | Teléfonos a **1 m**, cara a cara, a la altura del pecho, sin obstáculos ni personas en medio | 12 s de RSSI crudo del teléfono emparejado |
| 2 | Teléfonos a **3 m**, en las mismas condiciones | 12 s |
| 3 | Resultado: P1m, n y comparación antes/después | — |

- Se necesitan al menos `Calibrator.MIN_SAMPLES_PER_POINT` (10) lecturas por distancia; si no se
  alcanzan (faro apagado, fuera de alcance), el paso se puede repetir.
- **Ajuste:** `Calibrator.fit()`: regresión sobre las medianas a 1 m y 3 m; n acotado a [1,5; 4,5].
- **Evaluación:** para cada distancia medida, distancia estimada con la mediana de RSSI usando el
  modelo anterior y el calibrado.
- *Guardar* aplica el modelo; *Repetir* reinicia el asistente; *Cancelar* lo descarta.

## Efecto

| | Sin calibrar | Calibrado |
|---|---|---|
| Origen del modelo | TX Power anunciado o P1m = −59 dBm, n = 2 | P1m y n medidos |
| Error del modelo en la banda | 4–6 dB | 2 dB (`DeviceTracker.modelErrorDb`) |
| Banda en el radar y anillo del mapa | Ancha | Estrecha |

## Persistencia y prioridad

- `SharedPreferences` privadas `calibration`: ID de emparejamiento → `P1m;n`.
- La calibración del usuario tiene prioridad sobre el P1m anunciado por el faro.
- *Olvidar* un teléfono elimina también su calibración. En el radar se puede borrar la calibración.
