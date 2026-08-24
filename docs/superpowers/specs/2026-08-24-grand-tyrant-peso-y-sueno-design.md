# Grand Tyrant — Peso físico y el sueño que se puede rodear

## Dónde encaja

El port del Grand Tyrant (`2026-08-22-grand-tyrant-port-design.md`) llega hasta el módulo 6: la
criatura camina, encadena sus cuatro ataques, ruge, duerme y despierta. Queda pendiente el módulo 7
(la cabeza sobre `Statue`) y la multiparte, que van en DeluxeLib.

Esto no es ninguno de los dos. Es la capa que va **encima** de un port terminado: que el animal pese
lo que mide, y que encontrárselo dormido sea una decisión del jugador y no un trámite.

Se apoya en la pasada de costuras que acabó justo antes de escribir esto, que dejó las seis
transiciones del ciclo de sueño en 0.00 de desajuste y estableció el criterio que aquí se reutiliza:
**un blend sólo compensa cuando el peor desajuste posible es mayor que el arranque propio del clip.**

## Los dos pasos

| | Qué | Toca |
|---|---|---|
| **1** | Peso físico: grietas, pisadas, columna que se enrosca, cabeceo y parpadeo | SMOP (red, cliente, entidad), `GTModel` |
| **2** | El sueño del GT: siesta al spawnear, detección lejana, dos avisos y despertar propio | `SleepGoal`, `SleepUrge`, `ISleepingEntity`, `GTEntity`, `GTAnimations` |

Son independientes. Parar después del paso 1 deja algo publicable.

---

# Paso 1 · Peso físico

## 1.1 · Las grietas del pisotón

**Es una regresión, no una idea.** El GT de 1.20.1 lo tenía: `StompDustFXPacket` mandaba al cliente
un centro, un radio y un umbral, y `CrackFX` pintaba en cada bloque elegido la **textura de rotura de
vanilla** vía `LevelRenderer.destroyBlockProgress(id, pos, etapa)`, con la etapa entre 5 y 8 y un TTL
de 22 ticks, limpiando con `-1` al caducar. Nunca rompió un bloque: es cosmético entero. El port
tiene el polvo y la sacudida, pero no las grietas.

`destroyBlockProgress(int, BlockPos, int)` existe con la misma firma en 26.1 — verificado sobre el
jar del cliente, no supuesto. El efecto porta uno a uno.

### Dónde vive

En SMOP, no en DeluxeLib. SMOP ya tiene su propia red (`SMOPNetwork` sobre `NetworkCreator`, con
`RiderActionServerPacket` como único inquilino), así que un `StompCrackFxClientPacket` entra sin
tocar la librería ni volver a publicarla. Si más adelante otro mob pesado lo quiere, ese es el momento
de promoverlo, no ahora.

Lo dispara `GTEntity#onStompImpact()`, que ya existe y ya corre una vez por cada uno de los tres
frames de impacto del pisotón.

### Dos desviaciones respecto al legacy, y por qué

**Radio 8, no 5.** El comentario que ya está en `onStompImpact` dice que el anillo de partículas se
dibuja *en* el radio de daño para que sea el aviso de dónde cae el golpe, y no decoración. Una grieta
en radio 5 sobre un golpe de radio 8 contaría una mentira sobre dónde duele. El tope de bloques sube
en proporción (de 32 a ~48) para que la densidad no se diluya.

**Las grietas profundizan.** Son tres impactos, no uno. Cada frame vuelve a elegir bloques y, en los
que ya tenían grieta, **sube la etapa** en vez de repintarla. El suelo cede progresivamente durante el
pisotón, que es exactamente lo que se está mirando mientras dura. Tope en 9, que es la última etapa
antes de la rotura.

### Qué se escribe

- `StompCrackFxClientPacket(centro, radio, semilla)` — la elección de bloques se hace en cliente a
  partir de la semilla, para no mandar una lista.
- `GroundCrackFx` (cliente): mapa `BlockPos → (id, ttl, etapa)`, tick de cliente que decrementa y
  limpia con `-1`. El `id` se deriva del `BlockPos` con un hash estable, como en el legacy, para que
  dos grietas distintas no se pisen el identificador.
- Búsqueda de suelo hacia abajo, hasta 6 bloques, exigiendo cara superior sólida — igual que el
  legacy, y es lo que evita pintar grietas en el aire sobre un barranco.

## 1.2 · Pisadas

**El GT es bípedo.** Los `arms` son los brazos cortos y sólo hay dos patas, así que no cabe repartir
el efecto entre delanteras y traseras: todas sus pisadas son de pata trasera. La contención del mareo
se hace por amplitud y radio, no eligiendo patas.

### Los frames, medidos

Ninguna de las dos patas anima `POSITION`: la marcha va entera en la rotación X del muslo. El ciclo
tiene dos tramos de duración muy distinta cubriendo el mismo recorrido de 37.5°, y eso es lo que los
identifica sin depender del convenio de signo del eje:

- **tramo lento** (2.05 s en `walk`) — el pie está clavado y el cuerpo pasa por encima: apoyo.
- **tramo rápido** (0.95 s) — la pata va por el aire: vuelo.

El contacto es la transición rápido→lento. Las dos patas van desfasadas exactamente media fase, que
es la comprobación de que la lectura es correcta:

| Clip | Ciclo | Izquierda | Derecha | Factor de apoyo |
|---|---|---|---|---|
| `walk` | 60 ticks | tick **7** | tick **37** | 68% |
| `sprint` | 20 ticks | tick **3** | tick **13** | 65% |

Se confirman en juego antes de dar el paso por bueno; la medida da el candidato, no la sentencia.

### El efecto

Por pisada, polvo del bloque real bajo el pie (`ParticleFx`, con `getBlockStateOn()` como ya hace el
pisotón) y una sacudida corta.

La sacudida es donde está el riesgo: `sprint` pisa cada 10 ticks, o sea **dos veces por segundo**
mientras te persigue. Con la amplitud del pisotón eso es insoportable. Números de partida, a afinar en
juego:

| | Amplitud | Radio | Duración |
|---|---|---|---|
| Pisotón (ya existe) | 0.50 | `STOMP_SHAKE_RADIUS` | 10 ticks |
| `walk` | 0.12 | 10 bloques | 3 ticks |
| `sprint` | 0.18 | 10 bloques | 3 ticks |

Se reutiliza la caída con la distancia que `onStompImpact` ya implementa, así que a 10 bloques la
sacudida vale cero y sólo la sientes cuando lo tienes encima. El polvo, en cambio, va siempre: es
información a distancia y no marea a nadie.

## 1.3 · La columna que se enrosca al girar

`TurnLeanAdditive` lleva en DeluxeLib sin estrenar. Lee el **gap de rumbo** —el ángulo entre a dónde
apunta la dirección y a dónde apunta el cuerpo visible— y lo pasa por un muelle amortiguado de segundo
orden, así que la pose coge inercia, se pasa un poco al terminar el giro y se asienta.

Encaja aquí mejor que en ningún otro mob del mod por una razón medible: el GT gira a **5°/tick**
(`TURN_SPEED`), así que un viraje cerrado le lleva más de un segundo y el gap se queda abierto todo
ese rato. En un mob que gira rápido el gap es un pico que el suavizado se come.

Reparto propuesto:

| Mapeo | Huesos | Por qué |
|---|---|---|
| `coil` | `body_parts`, `neck` | el tronco y el cuello se enroscan *hacia* el giro |
| `lag` | `tail1` → `tail2` → `tail3`, con factor descendente | la cola se queda atrás y llega tarde, que es lo que la hace pesar |
| `bank` | `body_parts` | el cuerpo se inclina al carvear |

Muelle lento y bastante amortiguado (~1.2 Hz, ratio ~0.6) porque es un animal pesado, y **zona muerta
de ~5°** para que caminar recto no le haga vibrar la columna.

`GTModel` sólo expone hoy `root` y `neck`; hay que sacar los huesos que esto necesita.

## 1.4 · Cabeceo y parpadeo

`IdleHeadAdditive` e `IdleBlinkAdditive`, también sin estrenar, y los dos puramente procedurales.

**El cabeceo va sobre `head`, no sobre `neck`.** El `lookAt` del rig ya conduce el cuello — y lo hace
a propósito, porque en un animal de cuello largo girar sólo el cráneo lo desprende del cuerpo. Poner
los dos sobre el mismo hueso los haría pelearse.

**El parpadeo se capa durante el ciclo de sueño.** `sit`, `sleep` y `alert_snore` ya animan la escala
del hueso `eyes`; el componente está pensado para estados donde ningún clip toca los ojos, y su propia
documentación lo advierte. La compuerta es `!isInSleepCycle()`.

El cabeceo se capa además mientras ataca o se mueve: es un gesto de reposo.

---

# Paso 2 · El sueño del GT

## Qué se busca

Que encontrarse un Grand Tyrant dormido sea **una decisión**: rodearlo agachado y seguir tu camino, o
despertarlo a sabiendas. Hoy no lo es, porque duerme sólo de noche y te detecta a 4 bloques, que en un
animal de seis bloques de alto es prácticamente estar tocándolo.

## 2.1 · La siesta al spawnear (70/30)

Al aparecer, **70% de probabilidad de estar ya dormido**. Va en `finalizeSpawn`.

### El choque con el reloj, que es el problema de verdad

El GT spawnea como `MobCategory.CREATURE` en llanuras y desierto, o sea sobre todo **de día y en la
generación del mundo**. Y hoy:

- `SleepUrge#wantsToSleep()` exige que sea de noche, así que `SleepGoal#canUse()` ni arrancaría.
- `SleepGoal#shouldWakeUp()` trata la luz del día como motivo de despertar.

O sea que un GT dormido a mediodía se pondría de pie al tick siguiente.

### La solución: separar dos preguntas que hoy son una

`SleepUrge#isForced()` responde hoy a dos cosas a la vez, y el GT necesita una sin la otra:

| Pregunta | Poción del hipopótamo | Siesta del GT |
|---|---|---|
| ¿Aguanta con el sol alto? | sí | **sí** |
| ¿Le da igual quién se acerque? | sí | **no** — de eso va todo el paso 2 |

Se parten en `holdsThroughDaylight()` e `ignoresThreats()`. El Hell Hippo responde que sí a las dos y
**se queda exactamente igual que hoy**; la siesta responde que sí sólo a la primera.

### Entra ya tumbado

La siesta **entra directamente en `SLEEPING`**, saltándose `sitting_down`, `sit` y `preparing_sleep`.
Un animal que aparece dormido ya está tumbado; reproducirle la ceremonia de sentarse sería animar algo
que no ocurrió — el mismo criterio que `SleepGoal#beginLeaving` ya aplica al saltarse `awakening` en
un mob que nunca llegó a tumbarse.

### Se persiste, y esto no es opcional

La fase de sueño **a propósito no se guarda** en NBT: el comentario de `SMOPAnimal` explica que
restaurarla dejaba al mob congelado dormido para siempre. Pero si no se guarda **nada**, un GT dormido
al que le recargas el chunk se pone de pie, y la siesta se pierde por mirar para otro lado.

Lo que se persiste es **el flag de siesta**, no la fase. Al cargar, `canUse()` vuelve a dispararse y
entra otra vez directo a `SLEEPING`: parece que nunca se levantó, y el modo de fallo del comentario de
`SMOPAnimal` no aparece, porque la fase se reconstruye desde el goal en vez de restaurarse a mano.

### Es de un solo uso

Cuando despierta, el flag se apaga para siempre. A partir de ahí el GT vive con las reglas normales:
duerme de noche, con su `WOKE_UP_DELAY_TICKS` de por medio. La siesta es cómo lo encuentras la primera
vez, no un ciclo nuevo.

### La barra de jefe

**Escondida mientras duerme.** Que aparezca en el momento en que despierta es el aviso de que la has
liado, y enseñarla antes destriparía el sigilo: verías la barra desde lejos y sabrías que hay algo
antes de tener que mirar.

## 2.2 · Detección lejana y sigilo

**El radio deja de ser una constante del goal.** `SleepGoal.THREAT_RADIUS` vale 4.0 para todo el mundo;
pasa a `ISleepingEntity#getSleepThreatRadius()` con 4.0 por defecto, y el GT devuelve **16**. Ningún
otro mob cambia.

**Agachado no te detecta.** El precedente de vanilla es el Warden, que ignora a quien va agachado, y es
el mismo contrato implícito: el sigilo es una mecánica que el jugador ya conoce.

Hay un detalle de fontanería que hay que arreglar para que esto sea posible: en `SleepGoal#isThreat`,
si el intruso es un jugador se decide con `ISleepAwareness` y **nunca se llega a consultar
`ISleepThreatEvaluator`**. Un mob que quiera afinar por jugador —este— no tiene hoy dónde hacerlo. El
evaluador pasa a poder opinar también sobre jugadores; `ISleepAwareness` sigue siendo el sí/no grueso
para quien no necesita más (el Tangoftero).

## 2.3 · Los dos avisos

`alert_snore` (3.0 s, 47 canales) lleva autorado desde el port sin que nadie lo registre. **Encaja sin
tocar la máquina de fases**, y está medido: entra desde `sleep` con **0.00** de desajuste y vuelve a
`sleep` con **0.00**. Está autorado como interludio dentro del sueño.

Mecánicamente se apoya en cómo funciona ya el animador: al lanzarlo desplaza al loop `sleep` (misma
prioridad), y al terminar —es `PLAY_ONCE`— el arranque automático de clips repetidos vuelve a poner
`sleep`, cuya condición sigue siendo cierta porque **la fase nunca cambió**. Reaparece en su frame 0,
que es exactamente la pose donde el ronquido acaba.

### La cadencia

Sobre el escaneo que el goal ya hace cada 10 ticks:

| | Valor | Por qué |
|---|---|---|
| Avisos antes de despertar | 2 | los pide el diseño |
| Gracia tras un aviso | 5 s | que un solo acercamiento no gaste los dos de golpe |
| Olvido | un aviso cada 30 s sin detectarte | mismo orden que `WOKE_UP_DELAY_TICKS` |

Los avisos **se enfrían con el tiempo**, no se borran al salir del radio ni son un presupuesto
irrecuperable. Es la única de las tres opciones que deja corregir un acercamiento torpe retirándote,
sin permitir farmear avisos entrando y saliendo del borde.

## 2.4 · El despertar propio

| Cómo despierta | Clip | Pausa sentado |
|---|---|---|
| Solo, al alba | `awakening` (80 ticks) | sí, la de siempre |
| Por un jugador — avisos agotados o un golpe | **`alt_awakening`** (84 ticks) | **no** |

**No necesita nada de la librería.** `GTEntity` ya implementa `onSleepPhaseBegin` y
`sleepPhaseDuration`, que es donde se elige la variante y se declara su duración — el mismo mecanismo
que el Nirasmosaurus usa para sus variantes del medio. La única disciplina es que las dos vayan
juntas: 84 ticks para el alternativo, 80 para el normal, porque una fase más larga que su clip deja al
animal congelado en el último frame y una más corta lo corta a media pose.

Saltarse la pausa es la regla que `SleepGoal` ya tiene para quien despierta de un golpe
(`EXIT_STARTLED`), extendida a "despertado por un jugador". Un jefe al que despiertas porque estás
encima de él no se queda cuatro segundos sentado parpadeando.

## 2.5 · Las costuras de `alt_awakening`

Están sucias, y ya están medidas:

| Costura | Desajuste | Canal peor | Canales sucios |
|---|---|---|---|
| `sleep` (costura del loop) → `alt_awakening` | **7.37** | `head` POSITION | 1 |
| `alt_awakening` → `sit` | **5.62** | `muscles` ROTATION | 2 |
| `alt_awakening` → `standing_up` | **5.62** | `muscles` ROTATION | 2 |

La tercera aparece porque el despertar por jugador se salta la pausa sentado y va directo a
levantarse. Se arreglan con la pasada que acaba de hacerse en las otras seis: **manda el loop**, la
transición aterriza en la pose que su vecino sostiene, y la curva resultante se comprueba evaluando la
catmullrom de vanilla frame a frame para que ningún canal se dispare.

Y son **dos arreglos, no tres**: la segunda y la tercera dan el mismo número en el mismo canal porque
la pasada anterior ya dejó el primer frame de `standing_up` igual al de `sit`. Corregir el final de
`alt_awakening` contra la pose del loop cierra las dos costuras de salida a la vez.

Con la costura limpia, `alt_awakening` hereda la decisión de blend de su gemelo: 450 ms, porque
arranca despacio y sigue teniendo un camino sucio posible (el golpe que corta el respiro a mitad).

---

## Verificación

**Paso 1**

- El pisotón agrieta el suelo en el radio del daño, las grietas profundizan durante los tres impactos
  y desaparecen solas; ningún bloque queda roto de verdad.
- Al caminar levanta polvo bajo cada pie en los frames medidos, y la sacudida sólo se siente de cerca.
- Girando en el sitio, el cuello se enrosca hacia el giro y la cola llega tarde; al terminar, rebota y
  se asienta. Caminando recto, nada vibra.
- Quieto, mueve la cabeza de vez en cuando y parpadea. **Dormido no parpadea**, porque los clips ya le
  animan los ojos.

**Paso 2**

- Invocado a mediodía, ~7 de cada 10 aparecen tumbados y **siguen tumbados** aunque pase el tiempo.
- Recargar el chunk no lo pone de pie.
- Acercarse sin agacharse: ronca, ronca, y al tercer contacto despierta con `alt_awakening` y **sin**
  sentarse. La barra de jefe aparece justo ahí.
- Acercarse agachado: no despierta, por cerca que pases.
- Retirarse tras un ronquido y volver medio minuto después: vuelve a tener margen.
- Golpearlo dormido: despierta con `alt_awakening`, sin avisos y sin pausa.
- De noche, ya despierto y sin nadie cerca, se acuesta y se levanta como hasta ahora.
- El Hell Hippo sigue durmiéndose con la poción y aguantando al jugador encima.

**Numérica, antes de juego**

- Auditoría de costuras: `alt_awakening` a 0.00 en sus tres transiciones.
- Velocidad de las curvas editadas, antes y después, con la catmullrom de vanilla.

## Desviaciones deliberadas

- **Radio de grieta 8 y no 5** del legacy, para que coincida con el radio de daño real del port.
- **Las grietas profundizan** entre los tres impactos, que el legacy no hacía.
- **La sacudida de pisada se contiene por amplitud y radio**, no eligiendo patas: el GT es bípedo y no
  hay patas delanteras entre las que repartir.
- **`isForced()` se parte en dos** en vez de añadir un tercer flag suelto, porque las dos preguntas ya
  estaban ahí mezcladas y el hipopótamo seguía necesitando las dos.
- **Se persiste el flag de siesta, no la fase de sueño**, que sigue sin guardarse por la razón que
  documenta `SMOPAnimal`.

## Riesgos

| Riesgo | Por qué | Mitigación |
|---|---|---|
| **Mareo por sacudida al correr** | `sprint` pisa 2 veces por segundo | Amplitud de partida baja (0.18 frente a 0.50 del pisotón) y radio 10 con caída; es lo primero a afinar en juego |
| **Los frames de pisada son una lectura, no una medida directa** | las patas no animan posición; se deduce del reparto apoyo/vuelo | El desfase de media fase cuadra en los dos clips; se confirma en juego antes de darlo por bueno |
| **Tocar `SleepGoal` afecta a cinco mobs** | es compartido con Tango, Krifto, hipopótamo y Niras | Todo lo nuevo entra por defecto con el valor de hoy: radio 4.0, evaluador ausente, sin siesta |
| **La siesta persistida y la fase no persistida** | son dos decisiones opuestas sobre lo mismo | La fase se reconstruye desde el goal, nunca se restaura a mano |
| **Un GT dormido para siempre en un chunk cargado** | la siesta no acaba sola, por diseño | Es lo pedido; el flag muere al primer despertar |

## Fuera de alcance

- **Sonido.** Sólo existe `roar1.ogg`; ni el pisotón ni las pisadas ni el mordisco suenan. `AnimSound`
  clava sonidos a frames concretos y está listo, pero hacen falta assets. Es lo siguiente que más
  peso daría por esfuerzo.
- **Poise/stagger**, que el spec del port dejó fuera a propósito y sigue fuera.
- **`eating`** (4.3 s, autorado y sin usar). Arranca desde una pose muy distinta de la de sentado —92°
  en los dedos— así que es un gesto de pie con su propio disparador, y eso es otro diseño.
- **`widle` y `swim`**, que son variantes de agua y este animal no las quiere.
- La cabeza sobre `Statue` y la multiparte, que ya tienen su sitio en el plan del port.
