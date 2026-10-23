# Búsqueda en grupo — diseño preliminar (futuro)

Varios teléfonos con la app (el dueño y sus amigos) miden a la vez su distancia al faro del teléfono
perdido y comparten las mediciones. Con un solo buscador, el Bluetooth da un **anillo**; con varios
buscadores en lugares distintos, los anillos se cruzan y dan una **posición** (multilateración).

Estado: diseño preliminar. Se implementa después de las fases 09 y 10.

## Requisitos

| Tema | Decisión |
|---|---|
| Tamaño del grupo | 2–3 amigos como caso normal; el diseño no limita el número de nodos |
| Acceso de los amigos | **24 h desde el emparejamiento**, garantizado criptográficamente (no solo por la app) |
| Conectividad | Debe funcionar **sin internet** (recitales) |
| Plataforma | Android con la app instalada en todos los nodos |

## Etapas

| Etapa | Entregable | Útil con |
|---|---|---|
| 1. Quién está más cerca | Invitación de 24 h + red entre teléfonos + lista compartida de distancias ("Ana 2 m · tú 4 m · Leo 3 m") | Cualquier geometría y errores de GPS grandes |
| 2. Multilateración | Posición estimada y elipse de error en el mapa | Amigos repartidos alrededor del teléfono |

## Invitación de 24 h: claves horarias

Hoy el ID rotativo se deriva directamente del secreto, y quien tiene el secreto puede reconocer el
faro para siempre. Para invitados se introduce una jerarquía de claves (protocolo del faro, versión 2):

```
secreto (16 B, nunca sale salvo en el QR del dueño)
└── clave horaria  K_h = HMAC-SHA256(secreto, "localizator-hora" ‖ h)[0..16]     h = ⌊epoch_ms / 3 600 000⌋
    ├── ID rotativo  = HMAC-SHA256(K_h, ventana)[0..8]                          ventana de 15 min
    └── clave de ubicación = HMAC-SHA256(K_h, "localizator-ubicacion")[0..16]
```

- **QR de invitado:** `LOCALIZATOR-INV:2:<h₀>:<K_h₀ … K_h₀+24>:<nombre>`, con las 25 claves horarias
  que cubren las 24 h siguientes (≈ 400 B; cabe en un QR).
- El invitado solo puede calcular IDs rotativos y descifrar ubicaciones de esas horas. Las claves
  horarias no permiten recuperar el secreto ni derivar horas futuras (propiedad de HMAC).
- Pasadas las 24 h, la app del invitado borra el emparejamiento; aunque una app modificada lo
  conservara, ya no reconocería el faro.
- El buscador del dueño sigue guardando el secreto y deriva las claves de cualquier hora.
- **Limitación:** no se puede revocar una invitación antes de que caduque (solo cambiando el secreto,
  lo que obliga a reemparejar a todos).

## Red entre teléfonos

[Google Nearby Connections](https://developers.google.com/nearby/connections/overview) con estrategia
`P2P_CLUSTER`: Bluetooth y Wi-Fi sin internet, alcance de decenas de metros. Cada nodo difunde al
grupo, unas 2 veces por segundo:

| Campo | Contenido |
|---|---|
| Nodo | ID aleatorio de la sesión de grupo |
| Distancia | `distanceMeters`, `distanceMin`, `distanceMax` del tracker |
| Posición propia | Latitud, longitud y precisión GPS; `null` si no hay |
| Instante | Reloj del emisor (ms) |

La sesión de grupo se une con el mismo QR de invitado; los mensajes se cifran con una clave derivada
de la clave horaria vigente, así que nadie fuera del grupo los lee.

## Multilateración (etapa 2)

1. Las posiciones GPS de los nodos se proyectan a un plano local en metros (este, norte) con origen
   en el centroide del grupo.
2. Se busca el punto `p` que minimiza `Σ wᵢ (‖p − nodoᵢ‖ − dᵢ)²` por **mínimos cuadrados ponderados**
   (Gauss-Newton), con `wᵢ = 1 / σᵢ²` y `σᵢ` combinando el error de la distancia BLE y el del GPS del nodo.
3. La covarianza del ajuste da la **elipse de error** que se dibuja en el mapa.
4. Si la geometría es mala (GDOP alto: nodos juntos o alineados), no se muestra posición y se
   sugiere dispersarse.

El GPS de los nodos (±5–15 m) limita la precisión. Mejora posterior: medir también la distancia
Bluetooth **entre amigos** para fijar la geometría relativa del grupo sin depender del GPS.

## Privacidad y batería

- Participar es explícito en cada teléfono, y la sesión se cierra al encontrar el teléfono o al caducar.
- Los nodos solo comparten su posición dentro del grupo, nunca con servidores.
- Nearby Connections y el escaneo continuo consumen batería: la sesión se limita a la búsqueda activa.

## Preguntas abiertas

- ¿Qué hacer con nodos cuyo reloj difiere mucho (mediciones desfasadas)?
- ¿Mostrar a cada amigo la posición estimada o solo una flecha hacia ella?
