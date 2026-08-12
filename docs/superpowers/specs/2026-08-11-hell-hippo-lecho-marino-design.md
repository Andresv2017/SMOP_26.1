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
(`AmphibiousNodeEvaluator.java:22-26`). El pathfinder **prefiere activamente nadar por la columna de
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

`Mob.setSpeed(f)` también hace `setZza(f)` (`Mob.java:557-560`), así que el vector que llega a
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
**todas** las ramas de `travel()` (`LivingEntity.java:2346`).

### Causa C — el goal de nado busca destinos en la columna, no en el fondo

`RandomSwimmingGoal.getPosition()` → `BehaviorUtils.getRandomSwimmablePos(mob, 10, 7)`, que acepta
cualquier bloque `isPathfindable(WATER)` en un rango vertical de ±7. Destinos en media agua, que una
navegación de fondo no puede alcanzar.

---

## Sección 1 · Física: quitar el `travel()` casero, no arreglarlo

26.1 tiene un atributo hecho justo para esto. En la rama de agua de `LivingEntity.travel()`
(`LivingEntity.java:2233-2241`):

```java
float f6 = (float)this.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY);
if (!this.onGround()) { f6 *= 0.5F; }
if (f6 > 0.0F) {
    f4 += (0.54600006F - f4) * f6;   // drag:  0.8  → 0.546
    f5 += (this.getSpeed()  - f5) * f6;   // accel: 0.02 → getSpeed()
}
```

Con `WATER_MOVEMENT_EFFICIENCY = 1.0` y el animal pisando suelo, el drag y la aceleración submarinos
quedan **idénticos a los de tierra**. La locomoción del lecho pasa a ser la de vanilla, sin
reimplementar nada.

**Cambios:**

1. `createAttributes()` añade `.add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0D)`. El atributo ya está
   declarado en `createLivingAttributes()` (`LivingEntity.java:338`) con default 0.0, y el `Builder`
   es un `HashMap` (`AttributeSupplier.java:69`), así que re-añadirlo sobrescribe limpio. Es un
   `RangedAttribute` con máximo 1.0, o sea que 1.0 es el techo legal.
2. `travel()` se reduce a `super.travel(v)` **más un sesgo de hundimiento**. El que deja vanilla
   (`getFluidFallingAdjustedMovement` → gravedad/16 = 0.005, con damping en Y de 0.8) da una terminal
   de ~0.025 b/t: dos segundos por bloque, se ve flotante al bajar un escalón.
3. Se eliminan `WATER_DRAG` y el `moveRelative`/`move` manuales. Con ello se recupera gratis lo que
   el override se estaba saltando: `calculateEntityAnimation()`, el manejo de `onClimbable`, y el
   empujón anti-atasco de `horizontalCollision` (`LivingEntity.java:2258-2260`).

### El sesgo de hundimiento, concretamente

```java
@Override
public void travel(@NotNull Vec3 travelVector) {
    super.travel(travelVector);
    if (this.isEffectiveAi() && this.isInWater() && !this.onGround() && !this.isVehicle()) {
        Vec3 v = this.getDeltaMovement();
        this.setDeltaMovement(v.x, v.y - SINK_ACCELERATION, v.z);
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
  parecer una piedra. Hoy ese mismo 0.03 no tiene techo ninguno porque el `travel()` casero no
  amortigua la Y en absoluto.

### `getFluidJumpThreshold()` se queda — pero su javadoc está al revés

El javadoc actual dice que el override existe para *"never auto-jump out of the water"*. Es lo
contrario de lo que hace el código. Trazando `LivingEntity.java:2773-2799` con
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
**sube el nodo inicial hasta la superficie** (`WalkNodeEvaluator.java:58-67`). `getSurfaceY()` de
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

`Drowned.java:64` hace exactamente lo primero, por la misma razón: que el evaluador terrestre esté
dispuesto a enrutar por agua sin penalización.

### La regla de fallback

Vive en un solo sitio, `moveTo`: se calcula la ruta a pie; si existe y `canReach()`
(`Path.java:131`), se camina; si no, se delega en la acuática y se marca `swimming`. Al salir del
agua o al completarse la ruta acuática, vuelve a terrestre.

**Métodos que deben delegar según el flag, y no son opcionales:**

| método | quién lo llama | qué se rompe si no delega |
|---|---|---|
| `tick()` | `Mob#serverAiStep` | no se sigue la ruta acuática |
| `isDone()` | `DirectionalMoveControl:187`, goals | el move control corta la velocidad a 0 nadando |
| `getPath()` | `PathCarrot` (lookahead de steering) | el steering pierde la ruta y va al waypoint crudo |
| `stop()` / `recomputePath()` | goals, `LeaveWaterShakeGoal:103` | la navegación acuática sigue viva tras parar |

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
  (`PathNavigation.java:407-410`) — **descarta las posiciones de media agua y deja solo el lecho**.

Es decir: el goal de deambular correcto bajo el agua es el terrestre, no el de nado. La navegación
acuática interna sigue teniendo su `isStableDestination` más laxo
(`AmphibiousPathNavigation.java:43-45`), pero ese solo se consulta en el fallback.

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
interim.fluidHeight = Math.max(d1 - aabb.minY, interim.fluidHeight);   // Entity.java:3357
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
| **Dormir en el lecho** | `SleepGoal.canUse()` no mira agua ni suelo: ya podía. Lo que faltaba era que el cuerpo se quedara quieto en el fondo, que es el sesgo de hundimiento de la sección 1 aplicándose también con el movimiento bloqueado. |
| **Tentación** (`TemptGoal`) | Usa `getNavigation().moveTo` — hereda la sección 2. |
| **Seguir a la madre** (`FollowParentGoal`) | Igual. |

---

## Fuera de alcance

- **Cría bajo el agua.** `BreedGoal` heredará el arreglo de navegación igual que los demás, pero no
  es un objetivo de verificación de este spec.
- **El gesto idle (`shake`) sumergido.** Se queda como está: solo en tierra, más el que ya dispara
  `LeaveWaterShakeGoal` al salir del agua.
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

## Riesgos

- **El wrapper de navegación es la pieza con más superficie.** `PathNavigation` tiene muchos métodos
  públicos y los goals los usan de forma dispersa. La tabla de la sección 2 lista los cuatro que
  sabemos que se llaman; si aparece un quinto, se manifestará como un comportamiento raro solo
  durante el fallback de nado, que es el estado menos frecuente y por tanto el más fácil de no ver en
  pruebas cortas.
- **Dos números son de ojo, no de razonamiento.** `SWIM_DEPTH_FRACTION` (dónde cae la raya del gait)
  y `SINK_ACCELERATION` (cuánto pesa al bajar). Ambos se ajustan mirando el render. El resto de las
  constantes del spec están derivadas de código de vanilla que se puede citar.
- **Un caminante de fondo puro no puede escalar paredes.** El fallback de la sección 2 lo cubre, pero
  solo cuando el destino es inalcanzable a pie *y* alcanzable a nado. Un destino inalcanzable de las
  dos formas simplemente no se intenta, que es el comportamiento correcto.
