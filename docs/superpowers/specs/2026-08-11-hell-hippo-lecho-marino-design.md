# Hell Hippo — caminar el lecho marino

**Fecha:** 2026-08-11
**Alcance:** locomoción, navegación y clips del `HellHippoEntity` bajo el agua. Fase 1 del port,
sub-fase 1d (*Agua*), corregida.

## El problema

El hipopótamo tiene que poder recorrer el fondo marino como recorre la tierra, y todo lo que ya sabe
hacer tiene que seguir funcionando allí abajo. Hoy no: **se planta en el fondo y tiembla sin
avanzar** — síntoma confirmado in-game.

El diagnóstico se hizo leyendo el código, no probando: hay **tres causas independientes**, y solo una
de ellas produce el síntoma que se ve. Las otras dos están tapadas por ella y aparecerán en cuanto se
arregle la primera, así que las tres entran en este spec.

---

## Diagnóstico

### Causa A — la navegación anfibia está diseñada para lo contrario

`AmphibiousNodeEvaluator.prepare()` pone `WATER` malus **0.0** y `WALKABLE` malus **6.0**
(`AmphibiousNodeEvaluator#prepare`). El pathfinder **prefiere activamente nadar por la columna de
agua antes que pisar el fondo**: una ruta submarina sale flotando varios bloques por encima del
lecho.

Y `PathNavigation.followThePath()` solo avanza de waypoint si

```java
double d1 = Math.abs(this.mob.getY() - (double)vec3i.getY());
boolean flag = d0 <= maxDistanceToWaypoint && d2 <= maxDistanceToWaypoint && d1 < 1.0D;
```

Un animal que se hunde al lecho nunca llega a 1 bloque en Y de un nodo que está tres arriba. El nodo
no avanza jamás → salta `timeoutPath()` → recalcula → vuelve a empezar. **Ese bucle es el temblor.**

### Causa B — la velocidad submarina está ~7× pasada

`Mob.setSpeed(f)` también hace `setZza(f)` (`Mob#setSpeed`), así que el vector que llega a
`travel()` es `(0, 0, ~0.25)` y su `lengthSqr` es 0.0625 < 1 — `getInputVector` **no lo normaliza**.
La aceleración real es por tanto `speed²` ≈ 0.0625 por tick, y la velocidad terminal la fija el drag:

| medio | drag | terminal |
|---|---|---|
| tierra (vanilla) | `friction × 0.91` ≈ 0.546 | ≈ 0.075 b/t |
| agua (`WATER_DRAG = 0.9` actual) | 0.9 | **≈ 0.56 b/t** |

Siete veces y media la velocidad de tierra. No se ve porque el bicho está atascado en la causa A y
nunca llega a acelerar.

El mismo `travel()` casero tampoco amortigua la Y (vanilla usa 0.8), así que `SINK_ACCELERATION` se
acumula sin techo, y nunca llama a `calculateEntityAnimation()`, que vanilla ejecuta al final de
**todas** las ramas de `LivingEntity#travel`.

### Causa C — el goal de nado busca destinos en la columna, no en el fondo

`RandomSwimmingGoal.getPosition()` → `BehaviorUtils.getRandomSwimmablePos(mob, 10, 7)`, que acepta
cualquier bloque `isPathfindable(WATER)` en un rango vertical de ±7. Destinos en media agua, que una
navegación de fondo no puede alcanzar.

---

## Sección 1 · Física: quitar el `travel()` casero, no arreglarlo

26.1 tiene un atributo hecho justo para esto. La rama de agua vive en su propio método,
`LivingEntity#travelInWater`:

```java
float waterWalker = (float)this.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY);
if (!this.onGround()) { waterWalker *= 0.5F; }
if (waterWalker > 0.0F) {
    slowDown += (0.54600006F - slowDown) * waterWalker;   // drag:  0.8  → 0.546
    speed    += (this.getSpeed() - speed) * waterWalker;  // accel: 0.02 → getSpeed()
}
```

Con `WATER_MOVEMENT_EFFICIENCY = 1.0` y el animal pisando suelo, el drag y la aceleración submarinos
quedan **idénticos a los de tierra**. La locomoción del lecho pasa a ser la de vanilla, sin
reimplementar nada.

**Cambios:**

1. `createAttributes()` añade `.add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0D)`. El atributo ya está
   declarado en `createLivingAttributes()` con default 0.0, y el `Builder`
   es un `HashMap` (`AttributeSupplier.Builder`), así que re-añadirlo sobrescribe limpio. Es un
   `RangedAttribute` con máximo 1.0, o sea que 1.0 es el techo legal.
2. `travel()` se reduce a `super.travel(v)` **más un sesgo de hundimiento**. El que deja vanilla
   (`getFluidFallingAdjustedMovement` → gravedad/16 = 0.005, con damping en Y de 0.8) da una terminal
   de ~0.025 b/t: dos segundos por bloque, se ve flotante al bajar un escalón.
3. Se eliminan `WATER_DRAG` y el `moveRelative`/`move` manuales. Con ello se recupera gratis lo que
   el override se estaba saltando: `calculateEntityAnimation()`, el manejo de `onClimbable`, y el
   empujón anti-atasco de `horizontalCollision` (en `travelInWater`).

### La vertical, concretamente

`travel()` elige **una de dos** verticales según lo que esté haciendo la navegación. No se suman.

```java
@Override
public void travel(@NotNull Vec3 travelVector) {
    super.travel(travelVector);
    if (!this.isEffectiveAi() || !this.isInWater() || this.isVehicle()) {
        return;
    }
    Vec3 velocity = this.getDeltaMovement();
    if (this.isSwimmingFallback()) {
        this.setDeltaMovement(velocity.x, velocity.y + this.swimClimbRate(), velocity.z);
    } else if (!this.onGround()) {
        this.setDeltaMovement(velocity.x, velocity.y - SINK_ACCELERATION, velocity.z);
    }
}
```

- **`isEffectiveAi()`** es una condición de corrección, no una optimización: `super.travel()` ya se
  cierra sobre `isControlledByLocalInstance()`, y empujar el delta en el cliente para una entidad que
  no controla pelearía con la interpolación.
- **No se comprueba `isMovementLocked()`**: dormido en el fondo el animal también tiene que pesar.
  Esto es lo que hace que la sección 5 (*dormir en el lecho*) funcione, y es deliberado.
- **`SINK_ACCELERATION` se queda en 0.03.** Con el damping en Y de 0.8 de vuelta en juego, la
  terminal es `(0.005 + 0.03) / (1 − 0.8)` ≈ **0.175 b/t** (~3.5 bloques/s): baja con peso y sin
  parecer una piedra. En el `travel()` casero ese mismo 0.03 no tenía techo ninguno, porque no
  amortiguaba la Y en absoluto.

### El ascenso: nada en el stack lo producía

**Esto no estaba en el diseño original y es la corrección más importante del documento.** El
`SINK_ACCELERATION` de arriba se justificó solo contra la bajada, y con eso el fallback de nado de la
sección 2 no podía funcionar: el animal encontraba la ruta por encima del obstáculo y no era capaz de
subir por ella.

Vanilla tiene exactamente dos mecanismos de ascenso, y el hipo **no tenía ninguno de los dos**:

1. **La ruta del salto.** Un mob terrestre trepa porque su `MoveControl` llama a
   `getJumpControl().jump()` (`MoveControl#tick`), lo que levanta `jumping`, lo que `aiStep`
   convierte en el empujón de `jumpInFluid`. Pero `DirectionalMoveControl` es horizontal puro y nunca
   pide salto — cero menciones de `jump` en el archivo, y cero `getJumpControl`/`setJumping` en todo
   SMOP y todo DeluxeLib.
2. **La ruta del nado.** Un mob acuático trepa porque su move control conduce la Y directamente hacia
   el waypoint. `DirectionalMoveControl` tampoco toca la Y.

Y aunque la primera hubiera existido, el sesgo la habría anulado: contra el damping de 0.8, el 0.04
de `jumpInFluid` se estabiliza en `(0.8·0.04 − 0.005 − 0.03) / 0.2` = **−0.015**. Seguiría bajando.

La solución sigue la segunda ruta, que es la que usan los mobs acuáticos de vanilla. `swimClimbRate()`
copia la forma de `DrownedMoveControl` — `speed × (dy/dist) × 0.1`, con `dy/dist` la componente Y
normalizada hacia el waypoint. Cero en llano, negativa al bajar.

**Por eso las dos verticales son alternativas y no capas.** El sesgo mantiene al animal pegado al
lecho mientras camina; mientras nada, pegarlo al lecho es justo lo que no se quiere.

*Síntoma que producía:* el animal subía **exactamente un bloque** y no más — que es lo que da la
física de escalón de `move()` con el `STEP_HEIGHT = 1.0` que el hipo declara, sin que intervenga
ningún salto.

### `getFluidJumpThreshold()` se queda — pero su javadoc está al revés

El javadoc actual dice que el override existe para *"never auto-jump out of the water"*. Es lo
contrario de lo que hace el código. Trazando el bloque `jump` de `LivingEntity#aiStep` con
`d4 = Double.MAX_VALUE`:

| estado | rama | resultado |
|---|---|---|
| en agua, `onGround()` | `(this.onGround() \|\| flag && d3 <= d4) && noJumpDelay == 0` | **`jumpFromGround()`** — salto real |
| en agua, sin suelo | `else` | `jumpInFluid(WATER)` — +0.04, nado hacia arriba |

Con el 0.4 por defecto, el primer caso también caería en `jumpInFluid` y el animal solo rebotaría.
**El override no desactiva el salto: es lo único que lo enciende**, y por tanto es lo único que le
permite subir bloques del lecho y salir a la orilla. Se mantiene el valor y se reescribe el javadoc
con el razonamiento correcto.

---

## Sección 2 · Navegación: patrón Drowned con fallback

`AmphibiousPathNavigation` sale del hipo por la causa A.

Se escribe **`SeabedPathNavigation extends GroundPathNavigation`** en
`net.darkblade.smop.entity.ai.navigation` (junto a la `SmartSwimmingNavigation` que ya vive ahí), que
envuelve dos navegaciones:

- **La suya propia**, terrestre, con `setCanFloat(false)`.
- **Una `AmphibiousPathNavigation` interna**, como plan B para nadar.

### Por qué `setCanFloat(false)` es la pieza clave

`WalkNodeEvaluator.getStart()` abre con `if (this.canFloat() && this.mob.isInWater())` y en ese caso
**sube el nodo inicial hasta la superficie** (`WalkNodeEvaluator#getStart`). `getSurfaceY()` de
`GroundPathNavigation` hace lo mismo. Con el flag en false, ambos anclan a los pies
(`Mth.floor(getY() + 0.5)`) y la ruta se pega al lecho — que es exactamente lo que hace que los
waypoints satisfagan el gate de Y de la causa A.

`NodeEvaluator.canFloat` ya es `false` por defecto y nada en la cadena `SMOPAnimal` →
`GenderedSMOPAnimal` → `HellHippoEntity` lo enciende (solo lo hacen `SMOPWaterAnimal:65` y
`SMOPFlyingAnimal:154`). Se pone **explícitamente** de todas formas: es load-bearing y silencioso, y
un futuro cambio en la clase base no debe poder romperlo sin que se note.

### Malus

En el constructor del hipo:

```java
this.setPathfindingMalus(PathType.WATER, 0.0F);
this.setPathfindingMalus(PathType.WATER_BORDER, 0.0F);
```

El constructor de `Drowned` hace exactamente lo primero, por la misma razón: que el evaluador terrestre esté
dispuesto a enrutar por agua sin penalización.

### La regla de fallback

Vive en un solo sitio, `moveTo`: se calcula la ruta a pie; si existe y `canReach()`
, se camina; si no, se delega en la acuática y se marca `swimming`. Al salir del
agua o al completarse la ruta acuática, vuelve a terrestre.

**Métodos que deben delegar según el flag, y no son opcionales:**

| método | quién lo llama | qué se rompe si no delega |
|---|---|---|
| `tick()` | `Mob#serverAiStep` | no se sigue la ruta acuática |
| `isDone()` | `DirectionalMoveControl:187`, goals | el move control corta la velocidad a 0 nadando |
| `getPath()` | `PathCarrot` (lookahead de steering) | el steering pierde la ruta y va al waypoint crudo |
| `stop()` / `recomputePath()` | goals, `LeaveWaterShakeGoal:103` | la navegación acuática sigue viva tras parar |

Además de esos, se delegan `shouldRecomputePath`, `isStuck` y `getTargetPos`. `isStableDestination`
**no** se delega nunca — es de lo que depende la sección 3, y su javadoc lo explica.

`isSwimming()` es público porque la entidad tiene que leerlo: es lo que elige entre las dos
verticales de la sección 1. Los cambios de modo se trazan a nivel `debug`, solo en transiciones.

### Lo que esto NO arregla

`DirectionalMoveControl` no puede saltar, y no solo bajo el agua: **en tierra tampoco**. Ningún mob
de DeluxeLib que lo use puede superar un obstáculo más alto que su `STEP_HEIGHT`. Al hipo se le nota
poco porque declara `STEP_HEIGHT = 1.0`, que cubre casi todo el terreno natural. Es un bug real de la
librería, vive en el otro repo, y queda fuera del alcance de este spec.

---

## Sección 3 · Goals: un solo stroll, no dos

`SMOPRandomSwimmingGoal` sale del hipo por la causa C. Se queda **un único `SMOPRandomStrollGoal`**,
sin la condición `!isInWater()`, sirviendo los dos medios.

Funciona por construcción, no por casualidad. `DefaultRandomPos.getPos()` filtra cada candidato con
`generateRandomPosTowardDirection`, que exige las cuatro:

```java
!GoalUtils.isOutsideLimits(...) && !GoalUtils.isRestricted(...)
&& !GoalUtils.isNotStable(mob.getNavigation(), blockpos)
&& !GoalUtils.hasMalus(mob, blockpos)
```

- `hasMalus` exige malus **== 0** — con `WATER` a 0.0 (sección 2), las posiciones submarinas pasan.
- `isNotStable` → `PathNavigation.isStableDestination(pos)` = *el bloque de abajo es sólido*
  (`PathNavigation#isStableDestination`) — **descarta las posiciones de media agua y deja solo el lecho**.

Es decir: el goal de deambular correcto bajo el agua es el terrestre, no el de nado. La navegación
acuática interna sigue teniendo su `isStableDestination` más laxo
(`AmphibiousPathNavigation#isStableDestination`), pero ese solo se consulta en el fallback.

`LeaveWaterShakeGoal` no se toca: su `!isInWater() && onGround()` sigue siendo correcto.

`SMOPRandomSwimmingGoal` **no se borra del repo** — es una clase genérica que otros mobs acuáticos
pueden querer. Solo deja de usarla el hipo.

---

## Sección 4 · Clips: la raya no es `isInWater()`

Las cinco condiciones de locomoción parten en `isInWater()`, que es cierto también en un charco de un
bloque. El hipo camina y reproduce el clip `swim`.

La raya correcta es **la profundidad del agua sobre los pies**. `getFluidHeight(FluidTags.WATER)`
devuelve exactamente eso, en bloques:

```java
tracker.height = Math.max(fluidTop - entityY, tracker.height);   // EntityFluidInteraction
```

y se calcula en `updateInWaterStateAndDoFluidPushing()` desde `baseTick`, **en las dos caras** — o
sea que es legible desde un `setPlayCondition` sin sincronizar nada, igual que `isInWater()`.

```java
/** Water this far up the body swaps to the water clip set. Half the body: belly-deep. */
private static final float SWIM_DEPTH_FRACTION = 0.5F;

private boolean isSwimDeep() {
    return this.getFluidHeight(FluidTags.WATER) >= this.getBbHeight() * SWIM_DEPTH_FRACTION;
}
```

Para el hipo (2.5 de alto) son 1.25 bloques: un charco de 1 se camina con `walk`, agua de 2 pone
`swim`. Se sustituye `isInWater()` por `isSwimDeep()` en las condiciones de `idle`, `walk`, `sprint`,
`waterIdle` y `swim`; el resto de sus cláusulas (`!isBaby()` en `waterIdle`, `isMoving() || isBaby()`
en `swim`) no cambian.

Expresarlo como **fracción de `getBbHeight()`** y no como constante absoluta hace que sobreviva a un
cambio de hitbox y que se levante limpio a `SMOPAnimal` cuando llegue el Nirasmosaurus, que el spec
del port ya declara anfibio — la misma razón por la que `LeaveWaterShakeGoal` se escribió reutilizable.

Encaja además con lo que *significan* los clips: `widle`/`swim` son el juego "dentro del agua" y ya
están rebasados a nivel de suelo, así que son los clips de caminar el lecho. El bug nunca fue qué
clip, sino dónde estaba la raya.

### Dos decisiones explícitas

**Sin histéresis, de momento.** Un Schmitt trigger de verdad necesita memoria, y esa memoria habría
que sincronizarla. En un lecho plano el `minY` es constante y la raya se cruza una sola vez, al entrar
o al salir. Si en pruebas parpadea en una orilla en pendiente, el arreglo ya está en el repo:
`SMOPAnimal.MoveHold` es `protected` justamente para esto y `SMOPWaterAnimal` ya corre una segunda
instancia para su umbral de sprint. Sería un flag sincronizado más, el mismo patrón que `MOVING`.

**`tickSeaweed()` no comparte el predicado.** Sigue usando `isFullySubmerged()` (`isUnderWater()`,
los ojos). "El animal entero mojado" es una pregunta distinta de "qué gait toca", y ahí la línea
estricta es la correcta. Si se compartiera el predicado, tunear el look de la animación movería en
silencio cuándo crecen las algas.

### Alternativas descartadas

| opción | por qué no |
|---|---|
| `isUnderWater()` / `isFullySubmerged()` | El builder no declara eye height (`.sized(2.5F, 2.5F)` y nada más), así que es la de por defecto, `0.85 × 2.5 = 2.125`. Necesitaría ~2.2 bloques de agua: en agua de 2 el hipo va sumergido hasta el lomo y seguiría haciendo el `walk` de tierra. Cambia un caso feo por otro, y la raya no se puede tunear sin tocar el hitbox. |
| Flag `SWIMMING` atado al modo de navegación de la sección 2 | Semánticamente lo más honesto, y da el resultado equivocado: caminar el lecho sumergido *es* caminar, así que reproduciría el clip de tierra en el fondo del mar y `widle`/`swim` solo aparecerían en el fallback raro. Los clips de agua desaparecerían del juego normal. |

---

## Sección 5 · Lo que ya tiene, funcionando sumergido

Ninguno de los goals existentes tiene un gate de agua o de suelo, así que casi todo se arregla solo
en cuanto la locomoción funciona. No hay código nuevo en esta sección — es la lista de verificación.

| comportamiento | qué lo hace funcionar |
|---|---|
| **Combate** (perseguir y morder) | `AnimatableMeleeAttackGoal` navega vía `getNavigation()`, hereda la sección 2. `faceCombatTarget()` y el ancla de la `HitWindow` leen `getYRot()` — son horizontales, no necesitan cambio. |
| **Dormir en el lecho** | `SleepGoal.canUse()` no mira agua ni suelo: ya podía. Lo que faltaba era que el cuerpo se quedara quieto en el fondo, que es el sesgo de hundimiento de la sección 1. |
| **Tentación** (`TemptGoal`) | Usa `getNavigation().moveTo` — hereda la sección 2. |
| **Seguir a la madre** | Igual, más la distancia de la sección 6. |

### Lo que la auditoría encontró

Se leyó cada uno buscando gates de agua o de suelo. Tres resultados que no eran obvios:

- **`SleepGoal.start()` llama a `getNavigation().stop()`** (`SleepGoal.java:123`), y nuestro `stop()`
  limpia el flag de nado. Así que un hipo que se duerme mientras el fallback conducía cae a modo
  caminar, y `travel()` le aplica el hundimiento en vez del ascenso. El caso borde está cubierto sin
  código extra, pero lo estaba por suerte y ahora está escrito.
- **`AnimatableMeleeAttackGoal` tiene un `dy <= 1.5`** (línea 92) que solo decide **cuándo parar la
  navegación**, no si se ataca. El ataque se decide con `distanceToSqr` en 3D contra el reach. Así que
  un objetivo por encima se sigue persiguiendo en vez de plantarse ante él, que es lo correcto.
- **El mordisco alcanza hacia arriba, y eso es correcto.** `AttackShape.Box#contains` no tiene
  término vertical: calcula `forward` y `side` y devuelve, con la Y ignorada por completo — el javadoc
  de la interfaz lo declara como decisión de diseño ("un swing de mob terrestre alcanza lo que tenga
  delante, a cualquier altura"). En `box(2.6, 1.1)` el **1.1 es medio-ancho lateral, no altura**. El
  único tope vertical lo pone la fase ancha: `AABB.inflate(broadRadius + 1)` con
  `broadRadius = √(2.6² + 1.1²) = 2.82`, o sea ±3.82 desde el ancla — más de lo que el goal permite
  atacar, que son 3.5 en 3D. Un objetivo nadando por encima del hipo **sí se muerde**.

`TemptGoal` y `BreedGoal` no tienen ningún gate de agua ni de suelo.

---

---

## Sección 6 · Añadidos durante la implementación

Dos peticiones que llegaron con el trabajo ya en marcha y se hicieron aquí porque tocan la misma zona.

### El shake, prohibido dentro del agua

`IdleAnimationGoal` ya tenía el hook exacto, así que es una línea en el registro:
`.condition(animal -> !animal.isInWater())`.

`isInWater()` y no `isSwimDeep()` **a propósito**: es el mismo predicado que ya usa
`LeaveWaterShakeGoal`, y los dos caminos que llegan a este único clip no deben discrepar sobre cuándo
vale. Pasarlo por `condition()` en vez de por `canUse()` da además que un hipo que se meta al agua a
mitad del gesto lo pierda, porque el goal reevalúa esa condición mientras corre.

### `SMOPFollowParentGoal`

Vanilla para la cría a tres bloques centro-a-centro, escrito a fuego como un `9.0` literal en
`canUse` y en `canContinueToUse`, con `parent` privado — no hay forma de ensanchar la distancia
heredando sin reimplementar también la búsqueda del padre. La clase nueva es esa con el número fuera.

Tres bloques es una medida de vaca. La distancia es centro-a-centro, así que lo que compra depende de
lo anchos que sean los animales:

| | ancho adulto | ancho cría | se tocan a | hueco a 3 bloques |
|---|---|---|---|---|
| Vaca | 0.9 | 0.45 | 0.675 | 2.33 |
| Hell Hippo | 2.5 | 1.25 | 1.875 | **1.13** |

La cría sale de `LivingEntity#getAgeScale`, que devuelve 0.5. Y sobre estos rigs el modelo sobresale
mucho de la caja — el javadoc del propio hipo documenta que el morro queda 2.1 bloques por delante de
la cara frontal del hitbox — así que a tres bloques la cabeza de la cría se ve dentro de la madre.
El hipo usa **5.0**, que deja unos 3 bloques entre cuerpos. Número de ojo.

Dos desviaciones deliberadas del original, ambas por robustez: el radio de búsqueda es
`max(8, followDistance × 2)` (con 8 fijo, una distancia mayor que 8 dejaría el goal muerto en
silencio, porque la búsqueda no devolvería ningún adulto que pudiera calificar), y `tick()` comprueba
`parent != null`, que vanilla desreferencia sin mirar.

### El mordisco pasa a `box3d`

Con el alcance vertical ya entendido (ver la auditoría de la sección 5: `box` ignora la Y y solo la
fase ancha la acota, a ±3.8), queda una decisión de look que en tierra nunca se planteó porque los
objetivos comparten suelo con el atacante. Bajo el agua no lo comparten, y un jugador nadando tres
bloques por encima recibía daño de un mordisco que visiblemente no le llegaba.

`AttackShape.box3d(2.6, 1.1, 1.5)` en lugar de `box(2.6, 1.1)`. El medio-alto de 1.5 se mide desde el
ancla, que está a 0.9, así que cubre de −0.6 a +2.4 sobre los pies del hipo: cualquier cosa apoyada
en su mismo suelo, más un escalón o dos de pendiente, y nada flotando por encima.

**Dos diferencias con `box` que no son solo el tope vertical:**

- `box3d` mide contra el **centro** del objetivo (`position() + bbHeight/2`); `box` medía contra su
  posición de pies. La raya cae por tanto a media altura de la víctima.
- La tolerancia que se suma en vertical es `target.getBbWidth() / 2`, el medio-**ancho** del objetivo,
  no su medio-alto. Para un jugador son 0.3.

La forma está pensada originalmente para mobs voladores que apuntan con pitch, emparejada con
`aimAlongLook()` y `AttackAnchor.look`. Aquí el `facing` sigue siendo el yaw del cuerpo, plano, con lo
que el `up` que calcula `Box3D` sale siendo el world-up y la caja queda a nivel — que es exactamente
lo que se quiere para un animal que muerde de frente. 1.5 es número de ojo.

---

## Fuera de alcance

- **Cría bajo el agua.** `BreedGoal` hereda el arreglo de navegación igual que los demás, pero no es
  un objetivo de verificación de este spec.
- **Que `DirectionalMoveControl` pueda saltar.** Es un bug real y afecta a todos los mobs de
  DeluxeLib, en tierra y en agua. Vive en el otro repo.
- **Que `DirectionalMoveControl` conduzca la Y por sí mismo.** El ascenso se resuelve en
  `HellHippoEntity#travel` porque ahí está contenido; llevarlo al move control de DeluxeLib tocaría a
  todos sus mobs.
- **Todo lo montado.** `travel()` sigue delegando en `super` cuando `isVehicle()`; el pilotaje del
  jinete es fase 3.

## Criterios de aceptación

1. El hipo **recorre el fondo marino** sin atascarse ni temblar, a velocidad comparable a la que
   camina en tierra (no siete veces más rápido).
2. Deambula bajo el agua eligiendo destinos **del lecho**, no de media agua.
3. **Sube bloques del fondo** y sale a la orilla por su propio pie.
4. Frente a un obstáculo que no se puede rodear caminando (una fosa, un muro alto), **nada por
   encima** y luego vuelve a caminar.
5. **Persigue y muerde** a un objetivo en el fondo, y el mordisco acierta.
6. **Duerme en el lecho** el ciclo completo, sin flotar ni derivar.
7. Responde a la zanahoria/carne y una cría sigue a su madre, sumergidos.
8. En agua de **un bloque** camina con el clip `walk`; en agua de **dos** usa `swim`.
9. Sigue sacudiéndose al salir del agua y siguen creciendo las algas al sumergirse del todo — la
   sección 4 no las tocó.
10. **No** hace el gesto de shake mientras está dentro del agua, y sí lo sigue haciendo en seco.
11. Una cría se para a unos 5 bloques de su madre, con hueco visible entre los dos cuerpos, y sigue
    arrancando detrás de ella cuando se aleja.

## Riesgos

- **El wrapper de navegación es la pieza con más superficie.** `PathNavigation` tiene muchos métodos
  públicos y los goals los usan de forma dispersa. La tabla de la sección 2 lista los que sabemos que
  se llaman; si aparece uno más, se manifestará como un comportamiento raro solo durante el fallback
  de nado, que es el estado menos frecuente y por tanto el más fácil de no ver en pruebas cortas.
- **Tres números son de ojo, no de razonamiento.** `SWIM_DEPTH_FRACTION` (dónde cae la raya del
  gait), `SINK_ACCELERATION` (cuánto pesa al bajar) y `FOLLOW_PARENT_DISTANCE` (cuánto se separa la
  cría). El `SWIM_CLIMB_GAIN` no cuenta: es el 0.1 de `DrownedMoveControl`. El resto de las
  constantes están derivadas de código de vanilla que se puede citar.
- **Un caminante de fondo puro no puede escalar paredes.** El fallback de la sección 2 lo cubre, pero
  solo cuando el destino es inalcanzable a pie *y* alcanzable a nado. Un destino inalcanzable de las
  dos formas simplemente no se intenta, que es el comportamiento correcto.

## Lección: por qué se coló el bug del ascenso

El spec justificó `SINK_ACCELERATION` **solo contra la bajada** — cuánto tarda en caer un bloque — y
nunca lo contrastó con la subida, pese a que el fallback de nado de la sección 2 dependía de poder
subir. Las dos secciones se diseñaron por separado y su interacción no se calculó.

Es barato de detectar y caro de encontrar en juego: la aritmética que lo demuestra
(`(0.8·0.04 − 0.035) / 0.2 = −0.015`) es una línea, y el síntoma in-game — subir exactamente un
bloque — era indistinguible de "el fallback no se activa". Cuando dos secciones de un spec empujan la
misma variable en direcciones opuestas, hay que hacer la cuenta en el spec.
