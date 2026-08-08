# Krifto — conectar las animaciones huérfanas

Fecha: 2026-08-06
Entidad: `KriftognathusEntity` (`net.darkblade.smop.entity.krifto`)

## Contexto

`KriftoAnimations` declara cinco clips que `registerAnimations()` nunca referencia:
`boost`, `tamed`, `squawk`, `steal`, `eating`. `KriftoBabyAnimations` además tiene sus
propios `squawk` y `eating`.

Este spec define qué mecánica le da uso a cada uno, más el refactor que las tres
mecánicas necesitan por debajo. Se divide en fases independientes; **cada fase recibe
su propio plan de implementación**. El orden es 0 → 1 → 2 → 3.

| Clip | Destino |
|---|---|
| `eating` | Fase 1 — tameo por alimentación |
| `tamed` | Fase 1 — celebración al completar el tameo |
| `steal` | Fase 2 — robo de items a jugadores |
| `squawk` | Fase 3 — llamado ambiental en reposo |
| `boost` | Queda sin usar, deliberadamente |

El foco del trabajo es **tameo (Fase 1) y robo (Fase 2)**. La Fase 3 es chica y va al
final.

---

# Fase 0 — Primitiva de acción guionada

## El problema

Las tres mecánicas necesitan lo mismo: *correr una animación de una sola pasada, que
se vea en el cliente, que dure lo que dura el clip, y que suelte el lock de movimiento
sola al terminar.*

Esa primitiva no existe. Cada vez que alguien la necesitó, la volvió a escribir a
mano. El comentario de [TangofteroEntity.java:475](../../../src/main/java/net/darkblade/smop/entity/tangoftero/TangofteroEntity.java)
dice la causa raíz: *"BaseAnimation has no 'on stop' hook, so the movement lock has to
be released by a timer"*. Así que el Tangoftero coordina tres llamadas a mano
(`setRoaring(true)` + `roarTicksLeft = getRoarDuration()` + `animator().play(...)`) y
lleva el contador en la entidad.

Sin esta fase, el Krifto repetiría ese hand-roll tres veces más (`eating`, `tamed`,
`squawk`) y sumaría tres campos sincronizados a los ~14 que ya carga.

## Código muerto a borrar

Verificado: **nadie instancia estas clases ni lee este canal.**

- `RoarOnTargetGoal` y `RoarOnHurtGoal` (~180 líneas). Cero instanciaciones en todo el
  código. Se borran los archivos.
- El canal "armado": `shouldRoar`, `triggerRoar()`, `resetShouldRoar()`,
  `shouldRoarNow()`. Solo lo consumían esos goals. El Tangoftero usa su propio
  `roarArmed`, no este.
- La llamada `triggerRoar()` dentro de `SMOPAnimal.hurtServer` (`:170`). Escribe en
  cada golpe de cada entidad del mod y nadie lo lee jamás.
  **`sleepUrge().requestWake()` en esa misma rama se queda** — eso sí está vivo.

## Qué NO se toca

`ROARING`, `isRoaring()`, `setRoaring()`, `getRoarDuration()`, `getRoarSound()`,
`ANIM_ROAR` y toda la cadena del Tangoftero (carne podrida → rugido → `scareUndead()`)
**quedan como están**.

Esa cadena es una mecánica de juego documentada en el javadoc de la clase, no código
muerto. Migrarla a la primitiva nueva es trabajo sobre una entidad que hoy funciona
bien, y se hace después — cuando la primitiva ya esté probada en el Krifto. Hasta
entonces conviven los dos mecanismos, a sabiendas.

## La primitiva

En `SMOPAnimal`, un solo campo sincronizado reemplaza a N booleanos:

```java
private static final EntityDataAccessor<String> ACTION =
        SynchedEntityData.defineId(SMOPAnimal.class, EntityDataSerializers.STRING);

private int actionTicksLeft;
```

API:

| Método | Qué hace |
|---|---|
| `startAction(String name)` | Servidor: setea el nombre, saca la duración de `clipDurationTicks(name)`, llama `onActionStart(name)` |
| `stopAction()` | Corta la acción en curso (interrupciones) |
| `currentAction()` | Nombre en curso, `""` si ninguna |
| `isPerforming(String name)` | El test que usan los `setPlayCondition` |
| `isPerformingAction()` | Hay alguna acción corriendo |
| `actionLocksMovement(String name)` | **Overridable.** Default `true` |
| `onActionStart(String name)` | **Overridable.** Hook para sonido/partículas |

El nombre de la acción **es** el nombre del clip registrado. Eso es lo que hace que el
lock y la animación no puedan desincronizarse: la duración sale del clip, no de una
constante paralela.

El descuento va en el tick de servidor que ya existe:

```java
if (this.actionTicksLeft > 0 && --this.actionTicksLeft <= 0) {
    this.stopAction();
}
```

`isMovementLocked()` pasa a:

```java
return this.isInSleepCycle() || this.isRoaring()
        || (this.isPerformingAction() && this.actionLocksMovement(this.currentAction()));
```

(`isRoaring()` sigue ahí porque el Tangoftero no se migra en esta fase.)

**No se persiste en NBT.** Una acción de una sola pasada interrumpida por un
save/load simplemente se descarta; persistirla dejaría un mob pinchado al cargar.

**Las animaciones se enganchan solo con `setPlayCondition`**, sin `animator().play()`
explícito. Es el mismo mecanismo que ya usa el ciclo de sueño
(`preparingSleep.setPlayCondition(a -> this.isPreparingSleep())`).

## Ojo con el lock en vuelo

`SMOPFlyingAnimal:713` redefine `isMovementLocked()` como
`isFlying() ? isInSleepCycle() : super.isMovementLocked()`. O sea: **en vuelo, ninguna
acción pincha al mob.**

Esto es correcto para todo lo de este spec y no se cambia:
- `eating` y `tamed` son de suelo (el goal aterriza primero),
- `squawk` solo dispara en suelo,
- `steal` pasa en el aire pero **no debe** lockear — el krifto está en pleno picado.

Consecuencia: `actionLocksMovement("steal")` devuelve `false`, y además la rama de
vuelo lo cubriría igual. Redundante a propósito.

## Verificación

- El Tangoftero sigue rugiendo y espantando no-muertos exactamente igual que antes.
- El mod compila sin los dos goals borrados.
- Nada lee `shouldRoar` tras borrarlo.

---

# Fase 1 — Tameo por alimentación

## Comportamiento

El jugador tira carne de conejo cruda al suelo. Un krifto salvaje la detecta, se
acerca (aterrizando primero si venía volando), se detiene y la come con la animación
`eating`. Repetido 3 o 4 veces, queda domesticado y lo celebra con `tamed`.

## Decisiones tomadas

- **Reemplaza al tameo por right-click.** Se elimina `tryTame()` y su rama en
  `mobInteract`. No hay ninguna otra ruta de domesticación.
- **Contador global, dueño = el último.** Un solo `feedProgress` en la entidad; el
  dueño es quien tiró la última carne (el thrower del `ItemEntity`). No se persiste
  el UUID de nadie: al completar la última comida el thrower ya está a mano.
- **Sin decay.** El progreso se guarda en NBT y no expira. Evita que un jugador
  pierda el ritual por una interrupción ajena.
- **La cría participa.** Tiene su propio clip `eating`, y va a tener `tamed` portado
  (ver más abajo), así que cierra el ritual igual que el adulto.

## Estado nuevo en `KriftognathusEntity`

| Campo | Tipo | Persistencia | Para qué |
|---|---|---|---|
| `feedProgress` | `int` | NBT | comidas consumidas |
| `feedGoal` | `int` | NBT | 3 o 4, sorteado en la primera comida |

**Cero campos sincronizados nuevos** — `eating` y `tamed` viajan por la primitiva de
Fase 0.

## Goal nuevo: `TameFeedGoal`

Ubicación: `net.darkblade.smop.entity.ai.goal`. Flags: `MOVE`, `LOOK`.

**Historia real de la prioridad — probado en juego, no solo leído.** Mi primera corrección
(prioridad 6, ver commit history del spec) arregló la colisión con `LandingGoal` (3) pero
introdujo un bug peor, que solo apareció jugando: a prioridad 6 — estrictamente por encima de
`FlightWanderGoal` (7) — este goal ganaba y **retenía** MOVE/LOOK apenas volaba cerca de una
ofrenda, así que `FlightWanderGoal.tick()` nunca corría. `requestLanding()` no fuerza nada por
sí solo — solo expira `flightDurationTimer` para que `FlightWanderGoal` lo note la próxima vez
que tiquee y decida iniciar el picado. Si ese goal nunca tiquea, nadie lee la expiración: el
krifto quedaba flotando sobre el punto del item indefinidamente, nunca aterrizaba.

**Arreglo real: prioridad 7, empatada con `FlightWanderGoal`, y el goal se aparta por completo
en vuelo.** `WrappedGoal#canBeReplacedBy` solo cede en `<` estricto — un empate es justo lo que
impide que este goal le robe la flag de vuelta a `FlightWanderGoal` apenas se re-evalúa. Pero
un empate no sirve de nada si `TameFeedGoal` sigue *pidiendo* la flag en el aire: por eso
`canUse()`/`canContinueToUse()` excluyen `isFlying()` directamente — este goal **nunca** corre
mientras vuela, ni pide aterrizaje, ni dirige el descenso. Toda esa responsabilidad se mueve a
la entidad (ver más abajo), que no compite por flags de goals.

### canUse

Todas estas condiciones:

- `!isTame()`
- `!isInSleepCycle()`
- `!isMovementLocked()`
- `!isFlying()`
- `getTarget() == null`
- existe un `ItemEntity` en radio 16 (`TameFeedGoal.findOffering`, ver abajo) tal que:
  - su stack es `Items.RABBIT`,
  - no tiene pickup delay,
  - su thrower es un `Player` vivo.
- no hay cooldown activo entre comidas

Si hay varios candidatos, gana el más cercano. `findOffering` es **`static` y público** —
`KriftognathusEntity` lo llama también desde fuera del goal (ver `tickFeedOffering`), porque
ambos tienen que coincidir en qué cuenta como ofrenda.

### Ciclo (todo en tierra — ver más abajo cómo llega ahí desde el aire)

1. **Aproximación.** `getLookControl().setLookAt(item)`. El último tramo se camina a mano
   con `getMoveControl().setWantedPosition(...)` todos los ticks, más un re-path cada 10 ticks
   solo si la navegación ya terminó: `PathNavigation#moveTo` fija `reachRange = 1`, así que el
   pathfinder da por llegado un camino hasta un bloque antes del target — repetir el mismo
   `moveTo` devuelve el mismo camino ya "completo" y el krifto queda parado sin cerrar la
   distancia real. Sin este empujón manual se quedaba plantado a un paso del item.
2. **Alcance.** Se mide con `EAT_REACH` (la convención de `Mob#ITEM_PICKUP_REACH`: la
   bounding box del mob inflada, intersectada contra la del item) en vez de distancia centro
   a centro — la distancia centro a centro no cerraba nunca por la misma razón del punto 1.
3. **Comer.** Dentro de `EAT_REACH`: corta la navegación y llama `startAction("eating")`.
4. **Consumir.** Cuando la acción termina (`!isPerforming("eating")`): consume 1 del
   stack (descarta el `ItemEntity` si queda vacío), `feedProgress++`, partículas.
   - Si es la primera comida, sortea `feedGoal` en 3..4.
5. **Cierre.**
   - `feedProgress >= feedGoal` → `tame(thrower)`, `broadcastEntityEvent(this, 7)`
     (corazones), `startAction(ANIM_TAMED)`.
   - Si no → cooldown de ~40 ticks y el goal termina.
6. **Abandono.** Si pasan 300 ticks sin cerrar la distancia (acorralado, en un saliente,
   cruzando agua), suelta el target actual y entra en cooldown en vez de quedar plantado ahí
   para siempre.

### canContinueToUse

El `ItemEntity` sigue vivo y válido, el krifto sigue sin domesticar, y **no está volando**.

### Interrupciones

Si el krifto recibe daño o el item desaparece durante la fase de comer: `stopAction()`
y el goal termina. **`feedProgress` se conserva.**

## Puente aéreo: `KriftognathusEntity.tickFeedOffering()`

Como el goal se aparta por completo en vuelo, nadie dentro del sistema de goals está mirando
si hay una ofrenda mientras el krifto vuela. La entidad lo hace desde `tick()`, cada
`OFFERING_SCAN_INTERVAL` (10) ticks — es una query de entidades en radio 16, no vale la pena
más seguido:

- **En vuelo:** si hay ofrenda (`feedOffering != null`), llama `requestLanding()` — así el
  picado normal arranca ahora en vez de esperar a que `computeMaxFlightTicks()` expire solo
  (hasta 30s de vuelta en círculos sobre una comida que nunca nota).
- **En tierra:** `hasFeedOfferingNearby()` bloquea el despegue *programado* en
  `KriftoTakeoffGoal.canUse()` — el ritual son varias mordidas con cooldown entre medio,
  más largo que un `groundRestTimer` que normalmente ya viene drenando desde el aterrizaje;
  sin este freno el krifto despegaba entre mordidas y el ritual arrancaba de cero un ciclo de
  vuelo después. El despegue por **target real** no se bloquea — una amenaza vale más que el
  almuerzo, y en ese caso `TameFeedGoal` ya se aparta solo por tener `getTarget() != null`.

**Descenso apuntado.** `getDescentTarget()` (hook genérico ya existente en `SMOPFlyingAnimal`,
usado por la fase de picado) devuelve la posición de `feedOffering` en vez de `null` — sin
esto el picado corre derecho hacia adelante y el krifto tocaba tierra lejos de la comida que
vio desde altura de crucero, a menudo fuera del radio 16 que `findOffering` busca, así que
aterrizaba y se olvidaba para qué había bajado.

**Colchón post-tameo.** `onActionStart(ANIM_TAMED)` llama `delayTakeoff(clipDurationTicks(...)
+ TAMED_GROUND_HOLD_TICKS)` (hook genérico también preexistente en `SMOPFlyingAnimal`). El
lock de movimiento de `tamed` se levanta el mismo tick en que termina el clip, y el
`groundRestTimer` casi siempre ya está en cero para entonces (viene drenando desde antes del
ritual) — sin este colchón `KriftoTakeoffGoal` disparaba en el tick siguiente y la cola del
festejo, más el primer vuelo con el nuevo dueño, pasaban a mitad de la animación.

## Animaciones

En `registerAnimations()`:

```java
StandardAnimation eating = clip("eating",
        () -> KriftoAnimations.eating, () -> KriftoBabyAnimations.eating,
        Loop.PLAY_ONCE, 1, 2.5F);
StandardAnimation tamed = clip("tamed",
        () -> KriftoAnimations.tamed, () -> KriftoBabyAnimations.tamed,
        Loop.PLAY_ONCE, 1, 2.5F);

eating.setPlayCondition(a -> this.isPerforming("eating"));
tamed.setPlayCondition(a -> this.isPerforming("tamed"));
```

Prioridad 1 para que ganen sobre la locomoción de suelo (prioridad 2-3) sin
competir con las one-shot de vuelo. Ambas van al `register(...)` existente.

## Portar `tamed` al rig de la cría

`KriftoAnimations.tamed` (líneas 2193-2541, 2.5 s) anima 14 huesos y **no toca
`gNeck` ni `gHGead`** — justo los de nombre más divergente entre rigs.

**Mapeo:** 10 canales cruzan 1:1 sacando el prefijo `g` (`gPiglug`→`piglug`,
`gBody_parts`→`body_parts`, `gTail`→`tail`, `gEyes`→`eyes`,
`gLower_jaw`→`lower_jaw`, `gFront_legs`→`front_legs`, `gLeft_leg1`/`gRight_leg1`,
`gLeft_wing`/`gRight_wing`).

**Los 4 restantes no tienen equivalente:** `gLeft_calf`, `gRight_calf`,
`gLeft_claws2`, `gRight_claws2`. El adulto tiene la pata trasera en tres tramos
(`leg2 → calf → claws2`); la cría solo tiene `left_leg2`/`right_leg2`. Se resuelve
plegando la rotación de `calf` sobre `leg2` y descartando `claws2`.

**Dos ajustes obligatorios:**

1. Las traslaciones (`posVec`) están en píxeles del rig adulto. La cría es más chica,
   así que hay que escalarlas o el movimiento se lee exagerado. Las rotaciones cruzan
   tal cual.
2. Verificar clipping contra el suelo — la cría tiene otra altura de cadera. Se pasa
   la skill `anim-clip` sobre el clip portado antes de darlo por bueno.

Resultado: `KriftoBabyAnimations.tamed`.

## Qué se elimina

- La rama `stack.is(Items.RABBIT) && !this.isTame()` en `mobInteract`
  (`KriftognathusEntity:774`).
- El método `tryTame()` completo (`KriftognathusEntity:802`).

`isFood()` sigue devolviendo `CHICKEN` — es el item de breeding, no se toca.

## Verificación

- Tirar carne de conejo cerca de un krifto salvaje en el suelo: camina, come, sube el
  contador.
- Tirarla estando el krifto en vuelo: aterriza antes de comer, nunca come en el aire.
- Completar 3-4 comidas: queda domesticado, corazones, corre `tamed`.
- Repetir con una cría: mismo ciclo, con el `tamed` portado.
- Right-click con carne de conejo sobre un krifto salvaje: ya **no** lo domestica.
- Pegarle a mitad de la animación de comer: se libera el lock y el progreso se
  mantiene.

---

# Fase 2 — Robo de items

## Comportamiento

Un krifto salvaje adulto detecta a un jugador, vuela en círculo amplio a su
alrededor, se acerca, ejecuta `steal` y le saca un item de la hotbar. Se aleja
llevándolo agarrado con las patas traseras y lo suelta lejos.

## Decisiones tomadas

- **Roba un slot al azar de la hotbar**, stack entero (no 1 unidad — más legible:
  "me faltan las 12 flechas" en vez de "me falta una"). Este punto había quedado
  abierto en el brainstorming original; se cerró así al implementar por ser lo más
  simple y lo más notorio para el jugador.
- **Trigger oportunista con cooldown largo.** No es un impuesto constante.
- **Recuperable:** si lo matás mientras carga, el item se dropea
  (`dropCustomDeathLoot`).
- **Nunca pide despegue.** A diferencia de lo que decía la primera versión de este
  spec, el goal **no** intenta levantar vuelo para robar — solo se activa si el
  krifto ya está volando por su cuenta (`FlightWanderGoal` de siempre). Ver
  "Prioridad" más abajo para el porqué.
- **Sin persistencia NBT** para `stolenItem` ni el cooldown — mismo criterio que las
  scripted actions de Fase 0: un asalto dura segundos (órbita, picada, robo, fuga),
  así que un save/load cayendo justo en esa ventana pierde el item; se acepta ese
  costo a cambio de no tener que resolver el Codec de `ItemStack` contra
  `ValueOutput`/`ValueInput`, que ningún otro lugar del mod usa todavía.

## Estado nuevo

| Campo | Tipo | Persistencia | Para qué |
|---|---|---|---|
| `stolenItem` | `EntityDataAccessor<ItemStack>` (vía `EntityDataSerializers.ITEM_STACK`) | ninguna | render en las patas y drop al morir |
| `cooldownUntilTick` | `int`, vive en el goal, no en la entidad | ninguna | evita reincidencia del mismo krifto |

`steal` viaja por la primitiva de Fase 0. A diferencia de `eating`/`tamed`,
**no** hereda el lock de movimiento — todo el robo pasa en pleno vuelo activo, y
lockearlo dejaría al krifto congelado en el aire en mitad de la picada.

## Goal nuevo: `StealFromPlayerGoal`

Ubicación: `net.darkblade.smop.entity.ai.goal`. Flags: `MOVE`, `LOOK`.

**Prioridad 5, empatada con `FollowOwnerFlyingGoal`** — mutuamente excluyentes por
construcción (esa requiere `isTame()` + dueño, esta requiere `!isTame()`), así que
nunca compiten de verdad por la flag. La razón de fondo es la misma lección de
`TameFeedGoal` en Fase 1, pero aplicada distinto: `TameFeedGoal` solo quiere
*esperar* a aterrizar y por eso se aparta del todo en vuelo; este goal en cambio
*es* vuelo activo de principio a fin (órbita, picada, fuga), así que sigue el patrón
de `FollowOwnerFlyingGoal` en su lugar — prioridad estrictamente por debajo de
`TakeoffGoal` (2) y `LandingGoal` (3) para que esos siempre puedan robarle la flag,
con su propio `flightSettled()` (`!isTakingOff() && !isLanding()`) para apartarse
solo en vez de depender únicamente de que lo fuercen.

**canUse:** no cliente, adulto, `!isTame()`, **ya volando** (nunca pide despegue —
ver más abajo), `flightSettled()`, sin target, sin cooldown, `stolenItem` vacío, y
una tirada de 1 en 100 por tick — así el intento tarda en promedio unos segundos en
dispararse una vez que se cumplen las demás condiciones, sin ser instantáneo ni
necesitar un temporizador aparte. Recién ahí busca víctima: jugador vivo, no
espectador, no creativo, con al menos un slot de hotbar ocupado, el más cercano en
radio 16.

**Por qué nunca pide despegue.** Encadenar esto a un `requestTakeoff()` hubiera
significado resolver el mismo puente aéreo que `TameFeedGoal` necesitó en Fase 1
(entidad monitoreando, `hasFeedOfferingNearby()` bloqueando despegues programados,
etc.) para una mecánica que además no lo necesita: un krifto salvaje ya vuela
regularmente por su cuenta vía `FlightWanderGoal`. Dejar que el robo solo se
active mientras ya está en el aire es más simple y además lee mejor — la sorpresa
de que baje en picada desde donde ya estaba, no que despegue para ir a buscar
específicamente a alguien.

**Máquina de 4 fases** (todas en `KriftognathusEntity.ai.goal.StealFromPlayerGoal`,
usando `OrbitFlightController` para el movimiento):

1. **ORBIT** — radio 7, altura 4, velocidad angular 3°/tick, ~5 s (100 ticks).
2. **DIVE** — apunta directo a la víctima (+0.6 en Y) hasta contacto (² ≤ 4.0,
   o sea ~2 bloques). Timeout de seguridad a los 200 ticks: si nunca conecta
   (la víctima voló, se metió bajo techo), aborta sin pagar nada y entra en
   cooldown igual.
3. **SNATCH** — el robo se ejecuta **al entrar a esta fase**, no en un frame
   intermedio del clip ni al terminar: `steal` dura 0.65 s y el golpe de cabeza ya
   pasó la mitad de su recorrido para cuando un callback a mitad de clip llegaría a
   dispararse, así que esperar a un frame en particular se hubiera visto
   desincronizado. Vacía un slot de hotbar al azar de la víctima, lo guarda en
   `stolenItem`, corre `startAction("steal")`, y esta fase solo espera a que el
   clip termine (`!isPerforming("steal")`).
4. **FLEE** — vuela en línea recta alejándose de la víctima (el mismo
   `OrbitFlightController`, pero apuntado a un punto lejano en esa dirección en vez
   de a un punto en círculo) hasta 25 bloques de distancia o 400 ticks de
   timeout. Ahí `spawnAtLocation` el `ItemEntity`, limpia `stolenItem` y arma el
   cooldown (5 minutos).

## Refactor: extraer el controlador orbital

`FollowOwnerFlyingGoal` tenía un controlador PD (`accel = kp·error − kd·velocity`)
con ganancias documentadas como intocables, embebido directo en su `tick()`. La fase
ORBIT (y de hecho las cuatro fases del robo) necesitan el mismo controlador con
otros parámetros.

Se extrajo a `OrbitFlightController` en `entity.ai.goal.flying` —
`step(mob, target, fallbackFacing)`, parametrizada por ganancia de posición,
amortiguación, tope de velocidad y velocidad de giro. `FollowOwnerFlyingGoal` pasa a
usarla sin cambiar su comportamiento observable (compilación verificada); las
ganancias y sus comentarios se mudaron con ella. `StealFromPlayerGoal` instancia su
propia copia con ganancias más sueltas (más veloz, giro más cerrado) — misma
matemática, otro carácter de vuelo.

## Render del item robado

Era la parte más cara — no existía ningún patrón de render de `ItemStack` sobre
entidad en el mod. La API real de 26.1 (confirmada contra fuentes decompiladas
frescas, no contra el caché viejo que dio una firma incorrecta de `spawnAtLocation`
más arriba) resultó ser la línea "render state" que ya usa el resto del renderer,
solo que además tiene un objeto dedicado para items:

- `KriftognathusRenderer` ya recibe un `itemModelResolver` heredado de
  `LivingEntityRenderer` (`protected final ItemModelResolver`). En
  `extractRenderState` se llama `itemModelResolver.updateForLiving(state.stolenItem,
  entity.getStolenItem(), ItemDisplayContext.GROUND, entity)`, igual que el patrón
  vanilla `HoldingEntityRenderState` (usado por el Fox para el item en la boca).
- `KriftoRenderState` gana un campo `public final ItemStackRenderState stolenItem`.
- `StolenItemLayer` (nuevo, `client.krifto`) es un `RenderLayer<KriftoRenderState,
  EntityModel<? super KriftoRenderState>>` — el tipo de modelo tiene que ser el
  supertipo abstracto, no `KriftognathusModel` directo, porque
  `AgeableMobRenderer` comparte ese parámetro entre el modelo adulto y el de cría.
  El `submit()` hace `instanceof KriftognathusModel` antes de tocar cualquier hueso
  — no es defensivo de más: la cría no tiene un equivalente a `gBack_legs`, y
  además nunca tiene nada que dibujar (`StealFromPlayerGoal` es adulto-only).
- `KriftognathusModel` expone la cadena `piglug` → `legs` → `backLegs` como campos
  públicos. El layer camina los tres con `ModelPart#translateAndRotate` en orden —
  **no alcanza con trasladar solo por el offset de `backLegs`**: ese hueso está
  tres niveles bajo la raíz, y `gPiglug` sola carga la mayor parte de la
  locomoción del bicho (rebota en `tamed`, se inclina al volar, etc.), así que su
  posición/rotación cambia todo el tiempo y hay que arrastrarla.

## Verificación

- Un krifto salvaje ya volando cerca de un jugador con la hotbar cargada
  eventualmente orbita, pica y roba.
- El item robado se ve agarrado en las patas traseras mientras se aleja.
- Lo suelta lejos y el `ItemEntity` es recuperable.
- Matarlo mientras carga dropea el item.
- Un krifto domesticado nunca roba.
- En creativo nunca roba.
- Un krifto en el suelo no despega solo para ir a robar.

---

# Fase 3 — Squawk ambiental

## Comportamiento

Cada tanto, un krifto quieto en el suelo lanza su llamado con la animación `squawk`
(1.25 s) junto al sonido ambiente.

## Implementación

Override de `playAmbientSound()`. Vanilla ya lo llama con la cadencia de
`getAmbientSoundInterval()`, así que no hace falta ningún goal ni contador propio:
se aprovecha el reloj que ya existe.

El override reproduce el sonido de siempre y además llama `startAction("squawk")`,
**solo si**: no está volando, no está en ciclo de sueño, no tiene target y no se está
moviendo.

**No lleva lock de movimiento** — `actionLocksMovement("squawk")` devuelve `false`.
Es un llamado breve que solo arranca con el mob ya detenido; si empieza a caminar a
mitad, el blend se encarga. Un lock de 1.25 s haría que el krifto ignore un peligro
mientras chilla.

Animación: `clip("squawk", ...)` con adulto y cría (ambos clips ya existen),
`Loop.PLAY_ONCE`, prioridad 1, `setPlayCondition(a -> this.isPerforming("squawk"))`.

## Verificación

- Un krifto parado en el suelo chilla cada tanto, con el clip correcto según edad.
- No chilla en vuelo, durmiendo, ni en combate.
- Chillar no lo deja pinchado en el lugar.

---

# `boost`

Queda sin usar deliberadamente. Se le agrega un comentario en `KriftoAnimations` que
lo diga, para que la próxima auditoría de código muerto no lo reporte como olvido.

# Deuda conocida, fuera de alcance

Migrar la cadena de rugido del Tangoftero a la primitiva de Fase 0, y con eso borrar
`ROARING`, `getRoarDuration()`, `getRoarSound()` y `ANIM_ROAR`. Se hace cuando la
primitiva esté probada en el Krifto. Mientras tanto conviven los dos mecanismos.
