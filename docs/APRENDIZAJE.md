# Cuaderno de aprendizaje

Conceptos y preguntas para pensar que surgieron al construir Localizator, con sus respuestas.
Cada sección indica la fase en la que apareció. Se amplía con cada fase nueva.

> Sugerencia: intenta responder cada pregunta antes de leer la respuesta.

---

## 1. Señal Bluetooth y distancia

### Conceptos

| Concepto | En pocas palabras |
|---|---|
| **BLE** (Bluetooth Low Energy) | Versión de bajo consumo de Bluetooth. Los dispositivos emiten pequeños *anuncios* sin necesidad de conectarse |
| **RSSI** | Intensidad de la señal recibida, en **dBm**. Cuanto más cerca de 0, más fuerte: −40 es muy cerca y −90 es lejos |
| **dBm** | Escala logarítmica de potencia: cada −3 dB es la mitad de potencia y cada −10 dB es diez veces menos |
| **Modelo log-distance** | `RSSI(d) = P1m − 10·n·log10(d)`: convierte RSSI en metros |
| **P1m** | RSSI esperado a 1 metro. Es el parámetro de calibración más importante |
| **n** (exponente de pérdida) | Cuánto se atenúa la señal con la distancia: 2 en espacio libre, 3–4 entre gente y paredes |
| **Multitrayecto** | La señal rebota en paredes y personas y llega por varios caminos: el RSSI fluctúa aunque nada se mueva |

### Preguntas

**¿Por qué la tendencia ("te acercas") es más útil que la distancia exacta en metros?** *(fase 02)*

> Tu respuesta fue: "la tendencia se puede usar para guiar al usuario con una brújula dentro de un mapa".
>
> Respuesta completa: la tendencia compara la señal **consigo misma** a lo largo del tiempo. Aunque la
> calibración esté mal y la app diga "4 m" cuando son 2, si la señal sube, te estás acercando: el error
> de calibración se cancela. La distancia absoluta, en cambio, arrastra todo el error del modelo. La
> brújula (dirección) es otra técnica distinta, que se combina con la tendencia.

**El rango de distancia es asimétrico, por ejemplo "1,8 m (1,2 – 2,9 m)". ¿Por qué hay más margen hacia arriba que hacia abajo?** *(fase 04)*

> Porque el modelo es **logarítmico**. El margen de error es simétrico en dB (por ejemplo ±4 dB), pero
> `d = 10^((P1m − RSSI) / (10·n))` convierte esos dB en metros con una exponencial: restar 4 dB
> multiplica la distancia por un factor, y sumar 4 dB la divide por ese mismo factor. Multiplicar
> agranda más que dividir, así que el rango se estira hacia lejos. Por eso la precisión empeora tanto
> con la distancia.

**El filtro de 30 m elimina los "1000 m", pero un dispositivo a 25 m reales podría aparecer a 50 m y quedar oculto. ¿Por qué, en un recital, es mejor ocultar de más que de menos?** *(fase 04)*

> Porque en un recital hay cientos de dispositivos y la mayoría están lejos. Mostrarlos todos convierte
> la lista en ruido. Lo que buscas está **cerca** (si estuviera a 50 m, el Bluetooth sería demasiado
> impreciso para guiarte), así que es preferible una lista corta y útil. Además, para tu propio teléfono
> existe *Mis teléfonos*, que ignora los filtros.

---

## 2. Filtrado y precisión

### Conceptos

| Concepto | En pocas palabras |
|---|---|
| **Filtro de Kalman** | Combina una predicción con mediciones ruidosas, ponderando cada una según su incertidumbre |
| **Valor atípico** (*outlier*) | Lectura muy distinta de lo esperado (un reflejo, una persona que pasa); se le resta peso |
| **Error aleatorio** | Ruido que cambia en cada lectura; se reduce promediando |
| **Error sistemático** | Sesgo constante (calibración incorrecta, tu cuerpo tapando siempre el mismo lado); promediar no lo corrige |
| **Exactitud y precisión** | Exactitud: cerca del valor real. Precisión: lecturas parecidas entre sí. Se puede ser preciso pero inexacto |

### Preguntas

**Si el faro emite más rápido, el buscador recibe más lecturas por segundo. ¿Por qué mejora eso la precisión?** *(fase 05)*

> Tu respuesta fue: "permite corregir cualquier imprecisión".
>
> Respuesta completa: corrige el **error aleatorio**. Con N lecturas, el ruido promediado baja como
> σ/√N (con 4 veces más lecturas, la mitad de error), y el filtro reacciona antes a un movimiento real.
> Pero **no** corrige el error sistemático: si P1m está mal, mil lecturas dan mil veces la misma
> distancia equivocada. Eso solo lo corrige la **calibración**.

**La primera versión del filtro tardaba más de 5 s en reaccionar a un cambio real. ¿Por qué?** *(fase 02)*

> Porque trataba cada lectura nueva como un valor atípico y le restaba peso. La solución: un pico
> aislado se sigue ignorando, pero **3 lecturas atípicas seguidas en la misma dirección** se toman como
> un movimiento real y el filtro se pone al día.

**¿Por qué la calibración mide a dos distancias (1 m y 3 m) y no solo a 1 m?** *(fase 08)*

> Con una sola distancia solo se puede ajustar **P1m** (dónde empieza la recta); el exponente **n**
> (la pendiente) queda supuesto en 2. Con dos distancias se obtienen dos puntos de la recta
> `RSSI = P1m − 10·n·log10(d)` y se pueden calcular las dos incógnitas. Se usa la **mediana** de cada
> medición, y no la media, para que los picos de multitrayecto no arrastren el resultado.

---

## 3. Anuncios BLE y privacidad

### Conceptos

| Concepto | En pocas palabras |
|---|---|
| **Anuncio** (*advertising*) | Paquete de hasta 31 bytes que un dispositivo emite periódicamente |
| **Estructura AD** | Bloque `[longitud][tipo][datos]` dentro del anuncio. Tipos vistos: `01` flags, `FF` datos de fabricante, `21` Service Data con UUID de 128 bits |
| **Company ID** | Código del fabricante asignado por el Bluetooth SIG: `0x0075` Samsung, `0x004C` Apple |
| **Little-endian** | Los bytes van al revés: `75 00` significa `0x0075` |
| **Respuesta de escaneo** | Otros 31 bytes que el dispositivo envía cuando un escáner la pide (escaneo activo) |
| **MAC aleatoria** | Dirección que el teléfono cambia cada ~15 min para no ser rastreado |
| **IRK** (*Identity Resolving Key*) | Clave compartida al emparejar dispositivos Bluetooth; permite reconocer una MAC aleatoria propia |
| **Class of Device** | Tipo de dispositivo Bluetooth (teléfono, audio…). Android devuelve `0x0000` (*Miscellaneous*) cuando no lo conoce |

### Preguntas

**¿Por qué tu S25 Ultra tiene una MAC distinta cada vez que lo miras? ¿Qué problema resuelve?** *(fase 04)*

> Por **privacidad**. Con una dirección fija, cualquiera con sensores Bluetooth (comercios, publicidad,
> alguien con malas intenciones) podría seguir tus movimientos sin saber tu nombre: "este aparato estuvo
> en el shopping el lunes y el martes". Con direcciones aleatorias que rotan, un extraño ve aparatos
> distintos. Tus propios dispositivos te reconocen gracias a la clave IRK que compartieron al emparejarse.

**¿Por qué no se ve el nombre Bluetooth "Galaxy S25 de Marcos" en la lista?** *(fase 04)*

> Por privacidad (rotar la MAC no serviría si se emitiera el nombre) y por espacio (los 31 bytes del
> anuncio ya están ocupados). Los teléfonos solo emiten su nombre en modo visible. En nuestro diseño,
> el nombre viaja en el **QR**, nunca en la señal.

**Si un ladrón se lleva tu S25 con el modo protegido activo y escanea con su teléfono, ¿puede saber que esa señal es "el S25 de Marcos"?** *(fase 05)*

> **No.** La señal solo contiene un ID rotativo de 8 bytes que parece aleatorio y cambia cada 15 min.
> Para reconocerlo hace falta el **secreto** del QR. El nombre tampoco viaja en la señal: lo guarda cada
> buscador al emparejarse. Lo único que el ladrón podría notar es que hay "un dispositivo con el UUID de
> Localizator" cerca, pero no de quién es.

---

## 4. Criptografía del faro

### Conceptos

| Concepto | En pocas palabras |
|---|---|
| **Secreto compartido** | 16 bytes aleatorios generados en el protegido y entregados al buscador en el QR |
| **HMAC** | Función que mezcla un mensaje con un secreto; sin el secreto no se puede reproducir el resultado |
| **ID rotativo** | `HMAC(secreto, ventana de 15 min)`: cambia cada ventana, pero solo quien tiene el secreto lo puede calcular |
| **Tolerancia de reloj** | El buscador acepta la ventana anterior y la siguiente, por si los relojes difieren hasta 15 min |
| **AES-CTR** | Cifrado que genera un flujo de clave y lo combina con los datos (XOR). Nunca hay que repetir el IV con la misma clave |
| **IV** (vector de inicialización) | Valor que hace único cada cifrado. Usamos el ID rotativo + un contador |

---

## 5. Brújula, ángulos y mapas

### Conceptos

| Concepto | En pocas palabras |
|---|---|
| **Sensor de rotación** (`TYPE_ROTATION_VECTOR`) | Fusiona acelerómetro, giroscopio y magnetómetro para obtener la orientación del teléfono |
| **Norte magnético** | Hacia donde apunta la brújula. No coincide exactamente con el norte geográfico, y los metales lo desvían |
| **Media circular** | Los ángulos no se promedian directamente: el promedio de 359° y 1° no es 180°. Se promedian vectores `(cos θ, sin θ)` |
| **GPS** | Posición por satélites. Error típico de 3–5 m al aire libre, 5–15 m entre gente y peor bajo techo |
| **Haversine** | Fórmula para la distancia entre dos coordenadas sobre la esfera terrestre |
| **Trilateración** | Ubicar algo cruzando **distancias** (círculos). La triangulación usa **ángulos** |
| **UWB** (Ultra-Wideband) | Radio con varias antenas que mide distancia (10–30 cm) y **ángulo de llegada**. La tienen los S Ultra, los Pixel Pro y los iPhone 11+ |

### Preguntas

**¿Por qué no se pueden mostrar los dos teléfonos en el mapa solo con Bluetooth?** *(fase 06)*

> Porque el Bluetooth solo mide **qué tan fuerte llega** la señal, un único número. La misma intensidad
> puede venir de cualquier dirección: el teléfono está en algún punto de un **círculo**. Para la dirección
> hace falta medir el ángulo de llegada (UWB) o deducirlo girando (tu cuerpo bloquea la señal que viene
> de atrás).

**La brújula indica el norte magnético. ¿Qué le pasa en un recital, rodeado de escenarios metálicos y parlantes enormes?** *(fase 06)*

> Los metales y los imanes de los parlantes **desvían el campo magnético**: la brújula puede equivocarse
> decenas de grados y moverse sola al acercarte a una estructura. Afecta directamente a la flecha de
> dirección de la fase 09, que depende de saber hacia dónde mirabas en cada medición. Por eso la
> tendencia (↑/↓), que no usa la brújula, sigue siendo la guía más fiable. Mitigación: avisar cuando el
> sensor informa baja precisión y pedir que se calibre la brújula (el gesto del "8").

**El anillo Bluetooth del mapa está centrado en tu GPS, que también tiene error. ¿Cómo afecta eso a la posición estimada? ¿Qué pasa si los dos teléfonos están uno al lado del otro?** *(fase 07)*

> Tu respuesta fue: "se sumarían los errores llegando a ser contradictorios en cuanto a la ubicación del
> dispositivo".
>
> Respuesta completa: correcto. Si los errores son independientes se combinan como `√(e₁² + e₂²)`
> (±8 m y ±12 m dan unos ±14 m), y en el peor caso se suman (20 m). Con los teléfonos juntos, el
> Bluetooth dice "0,3 m", pero los dos GPS pueden ubicarlos a 15 m: **contradicción**. Por eso la app
> avisa "GPS y Bluetooth no coinciden", y la regla general es: **cerca, manda el Bluetooth; lejos, el GPS
> da la zona**.

---

## 6. Búsqueda en grupo

### Conceptos

| Concepto | En pocas palabras |
|---|---|
| **Multilateración** | Trilateración con más de tres distancias: se busca el punto que mejor encaja con todas, aunque ninguna sea exacta |
| **Mínimos cuadrados ponderados** | Elegir el punto que minimiza la suma de errores al cuadrado, dando más peso a las mediciones más fiables (`peso = 1/σ²`) |
| **GDOP** (dilución geométrica de la precisión) | Cuánto amplifica la geometría el error. Nodos repartidos alrededor: GDOP bajo. Nodos juntos o en fila: GDOP alto |
| **Jerarquía de claves** | Derivar claves "hijas" de un secreto "padre": se puede entregar una hija sin revelar al padre ni a sus hermanas |
| **Clave horaria** | Clave hija válida para una hora. Entregar 24 claves horarias da acceso exactamente durante 24 h |
| **Red P2P** (Nearby Connections) | Teléfonos que se comunican directamente por Bluetooth y Wi-Fi, sin internet ni servidor |

### Preguntas

**Tres amigos están juntos en la misma esquina y miden 10 m, 10 m y 11 m. ¿Por qué no alcanza para ubicar el teléfono?** *(fase 11)*

> Porque los tres círculos tienen casi el mismo centro: se superponen en una franja larga en lugar de
> cortarse en un punto. Es un GDOP alto. Más nodos en el mismo lugar no ayudan; lo que ayuda es que se
> **separen** alrededor de la zona.

**¿Por qué a los amigos se les entregan claves horarias y no el secreto con una fecha de vencimiento?** *(fase 11)*

> Una fecha de vencimiento solo la respeta una app que se porte bien; quien tiene el secreto puede
> seguir reconociendo el faro con una app modificada. Con claves horarias, después de 24 h **no tiene
> con qué** calcular los IDs nuevos: el límite lo impone la matemática, no la app.

**Si un amigo tiene un GPS de ±15 m, ¿conviene darle el mismo peso que a uno de ±4 m?** *(fase 11)*

> No. Su círculo está mal centrado, así que su medición debe pesar menos: `σ` combina el error de la
> distancia Bluetooth y el del GPS del nodo, y el peso es `1/σ²` (un nodo con el doble de error pesa
> la cuarta parte).

---

## 7. Android

### Conceptos

| Concepto | En pocas palabras |
|---|---|
| **Permisos de ejecución** | Los que el usuario acepta en un diálogo (`BLUETOOTH_SCAN`, ubicación, cámara…) |
| **`neverForLocation`** | Declara que el escaneo Bluetooth no se usa para ubicar; así no hace falta permiso de ubicación en Android 12+ |
| **Foreground service** | Servicio que sigue funcionando en segundo plano mostrando una notificación fija |
| **Jetpack Compose** | Forma declarativa de construir la interfaz: la pantalla es una función del estado |
| **MVVM / `ViewModel`** | El `ViewModel` guarda el estado de la pantalla y sobrevive a rotaciones |
| **`StateFlow`** | Flujo de datos que siempre tiene un valor actual y avisa cuando cambia |
| **`Canvas`** | Superficie para dibujar a mano en Compose (el radar) |

---

## 8. Flujo de trabajo

### Conceptos

| Concepto | En pocas palabras |
|---|---|
| **Rama** | Copia paralela del código para trabajar sin tocar `main` |
| **Pull Request** | Propuesta de llevar una rama a `main`, con revisión y CI |
| **CI** (integración continua) | GitHub compila y prueba cada PR automáticamente |
| **Android Lint** | Análisis estático que detecta errores típicos de Android (permisos, APIs, recursos) |
| **Ruleset** | Reglas de GitHub que protegen `main`: PR obligatoria y checks en verde |
| **Test unitario** | Código que comprueba automáticamente un comportamiento ("un pico aislado apenas mueve la estimación") |
| **`.env`** | Archivo con configuración secreta que Git ignora; `.env.example` documenta qué variables hacen falta |

---

## Recursos

| Tema | Recurso |
|---|---|
| BLE desde cero | [Nordic DevAcademy – Bluetooth LE Fundamentals](https://academy.nordicsemi.com/courses/bluetooth-low-energy-fundamentals/) |
| BLE en Android | [developer.android.com – BLE overview](https://developer.android.com/develop/connectivity/bluetooth/ble/ble-overview) |
| Fabricantes y UUID | [Bluetooth SIG – Assigned Numbers](https://www.bluetooth.com/specifications/assigned-numbers/) |
| Privacidad Bluetooth | [Bluetooth Technology Protecting Your Privacy](https://www.bluetooth.com/blog/bluetooth-technology-protecting-your-privacy/) |
| Filtro de Kalman | [kalmanfilter.net](https://www.kalmanfilter.net/default.aspx) |
| Modelo de pérdida | [Log-distance path loss model](https://en.wikipedia.org/wiki/Log-distance_path_loss_model) |
| Exactitud y precisión | [Precisión y exactitud](https://es.wikipedia.org/wiki/Precisi%C3%B3n_y_exactitud) |
| HMAC | [HMAC (Wikipedia)](https://es.wikipedia.org/wiki/HMAC) |
| AES-CTR | [Modos de operación: CTR](https://es.wikipedia.org/wiki/Modos_de_operaci%C3%B3n_de_una_unidad_de_cifrado_por_bloques) |
| Estadística direccional | [Estadística direccional](https://es.wikipedia.org/wiki/Estad%C3%ADstica_direccional) |
| Trilateración | [Trilateración](https://es.wikipedia.org/wiki/Trilateraci%C3%B3n) |
| Mínimos cuadrados | [Mínimos cuadrados](https://es.wikipedia.org/wiki/M%C3%ADnimos_cuadrados) |
| GDOP | [Dilution of precision](https://en.wikipedia.org/wiki/Dilution_of_precision_(navigation)) |
| Red sin internet | [Nearby Connections](https://developers.google.com/nearby/connections/overview) |
| Derivación de claves | [HKDF (RFC 5869)](https://datatracker.ietf.org/doc/html/rfc5869) |
| Haversine | [Fórmula del semiverseno](https://es.wikipedia.org/wiki/F%C3%B3rmula_del_semiverseno) |
| Sensores de Android | [Position sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_position) |
| Foreground services | [Foreground services](https://developer.android.com/develop/background-work/services/fgs) |
| Kotlin | [Kotlin Koans](https://play.kotlinlang.org/koans/overview) |
| Compose | [Android Basics with Compose](https://developer.android.com/courses/android-basics-compose/course) · [Graphics in Compose](https://developer.android.com/develop/ui/compose/graphics/draw/overview) |
| Arquitectura Android | [Guide to app architecture](https://developer.android.com/topic/architecture) |
| Git y ramas | [Learn Git Branching](https://learngitbranching.js.org/?locale=es_AR) |
| Configuración | [The Twelve-Factor App: Config](https://12factor.net/es/config) |
