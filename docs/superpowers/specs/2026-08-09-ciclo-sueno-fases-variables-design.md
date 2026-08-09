# Ciclo de sueño de longitud variable — API de SMOP

Fecha: 2026-08-09
Alcance: `net.darkblade.smop.entity.sleep`, `SMOPAnimal`, y el registro de animaciones
de las entidades que duermen.

## Problema

El ciclo de sueño tiene tres fases fijas: `preparing_sleep` → `sleep` → `awakening`.
Al Tangoftero le alcanza. El Krifto tiene seis animaciones autoradas y necesita
recorrerlas todas:

```
sitting → sit → sleep_preparing → sleep → awakening → standing_up
```

No es que el Krifto reemplace al ciclo corto: los dos tienen que convivir, y una
entidad nueva debe poder elegir sin configurar nada de más.

## Modelo de fases

Un enum `SleepPhase` con el nombre del clip que le corresponde a cada una:

| Fase | Clip registrado | Tipo | Opcional |
|---|---|---|---|
| `NONE` | — | — | — |
| `SITTING_DOWN` | `sitting` | one-shot | sí |
| `SITTING` | `sit` | loop | sí |
| `PREPARING_SLEEP` | `preparing_sleep` | one-shot | no |
| `SLEEPING` | `sleep` | loop | no |
| `AWAKENING` | `awakening` | one-shot | no |
| `STANDING_UP` | `standing_up` | one-shot | sí |

### Secuencia

```
entrada:            SITTING_DOWN → SITTING → PREPARING_SLEEP → SLEEPING
salida tranquila:                 SLEEPING → AWAKENING → SITTING → STANDING_UP → NONE
salida sobresaltada:              SLEEPING → AWAKENING → STANDING_UP → NONE
salida temprana:      SITTING → STANDING_UP → NONE
```

Hay **dos preguntas independientes** que se combinan al salir:

**¿Llegó a dormirse?** Si no —lo interrumpieron mientras se sentaba, o a mitad de
acostarse— se saltea `AWAKENING`. Ese clip es la transición de acostado a sentado, y un
bicho que nunca se acostó no tiene de qué despertar: correrlo animaría un sueño que no
pasó.

**¿Lo golpearon?** Despertarse solo (amanecer, una amenaza cerca) da derecho a un momento
sentado antes de pararse; un golpe no. `SleepUrge.requestWake()` tiene **un solo**
llamador, `SMOPAnimal#hurtServer`, así que consumir ese pedido *es* la señal de "me
pegaron" y es lo que elige el camino de salida.

La pausa sentada tampoco es un compromiso: un golpe que llega **durante** ella también
corta directo a pararse. Sería incoherente que pegarle un segundo tarde lo dejara sentado
ocho segundos más.

`SITTING` aparece en la entrada y en la salida, y por eso el goal lleva una bandera de
"estoy saliendo": sentarse bajando lleva a acostarse, y sentarse subiendo lleva a
pararse — la fase sola no alcanza para saber cuál sigue.

### Fases opcionales: se infieren de los clips

Una fase existe para una entidad **si y solo si** esa entidad registró su clip. Sin
métodos que declarar, sin banderas: se autoran los clips y el ciclo se arma solo.

Esto no es magia nueva, es el precedente que el código ya sigue — `SMOPAnimal` documenta
hoy sobre las duraciones de fase que *"0 (no clip registered) disables the phase"*. La
alternativa (un método que enumere las fases) sería un segundo lugar donde declarar lo
mismo, con la posibilidad de que queden desincronizados.

Consecuencia: el Tangoftero recorre `PREPARING_SLEEP → SLEEPING → AWAKENING → NONE` sin
que nadie lo configure, y el Krifto pasa a seis fases con solo registrar los tres clips
que le faltan.

Las tres opcionales se evalúan **de forma independiente**: no hay combinaciones inválidas
que haya que detectar ni rechazar. Registrar `sitting` sin `sit` da una entidad que se
agacha y se acuesta enseguida; registrar `sit` sin `standing_up` da una que se sienta y
se levanta de golpe al final. Ninguna de las dos es un error que el sistema deba impedir
— son decisiones de animación, y el ciclo simplemente salta lo que no existe.

## Estado sincronizado: un campo, no seis banderas

Hoy son **tres booleanos sincronizados** en `SMOPAnimal` (`SLEEPING`, `PREPARING_SLEEP`,
`AWAKENING`). Con seis fases la vía obvia sería sumar tres más — y esa es exactamente la
explosión de booleanos que ya se rechazó al construir la primitiva de acción guionada
(un solo campo `ACTION` sincronizado en vez de un flag por acción).

Se aplica la misma solución: **un solo campo sincronizado con la fase actual**, y los
getters existentes pasan a ser derivados:

```java
public boolean isSleeping()        { return this.sleepPhase() == SleepPhase.SLEEPING; }
public boolean isPreparingSleep()  { return this.sleepPhase() == SleepPhase.PREPARING_SLEEP; }
public boolean isAwakening()       { return this.sleepPhase() == SleepPhase.AWAKENING; }
public boolean isInSleepCycle()    { return this.sleepPhase() != SleepPhase.NONE; }
```

Lo que esto compra, y es el punto: **ninguna play condition ni entidad existente cambia**.
Los `setPlayCondition(a -> this.isSleeping())` del Tangoftero y del Krifto siguen
funcionando tal cual, y `SalmonDigGoal`, `AssistFlockGoal` y `TameFeedGoal` siguen
consultando `isInSleepCycle()` sin enterarse. El neto de campos sincronizados es
**3 → 1**, no 3 → 6.

`isInSleepCycle()` además queda más honesto: una comparación contra `NONE` en lugar del
OR de tres banderas que en teoría podrían estar todas puestas a la vez.

## Duraciones

- **Transiciones one-shot** (`SITTING_DOWN`, `PREPARING_SLEEP`, `AWAKENING`,
  `STANDING_UP`): salen de `clipDurationTicks(fase.clipName())`. Es el patrón vigente —
  la duración de la fase *es* el largo del clip, así que el lock de movimiento y la
  animación no pueden desincronizarse.
- **`SLEEPING`**: hasta que algo lo despierte, como ahora.
- **`SITTING`**: nuevo `getSittingDuration()` sobrescribible. Default aleatorio de 3 a 8
  segundos (`60 + random.nextInt(100)`), siguiendo el idiom de `computeGroundRestTicks()`,
  para que una manada no se acueste toda en el mismo tick.

## Un hook, no seis

Los tres hooks actuales (`onPreparingSleepBegin`, `onSleepBegin`, `onAwakeningBegin`)
están implementados **solo** en `SMOPAnimal`, y los tres son la misma línea mecánica:
`playIfRegistered(nombreDelClip)`. Ninguna entidad los sobrescribe. O sea que son pura
indirección: el goal avisa "empezó la fase X" y la entidad traduce eso a "reproducí el
clip X".

Si la fase conoce su propio nombre de clip, esa traducción no hace falta. Los tres se
reemplazan por uno, con la misma división que ya usan hoy: el default en la interfaz es
vacío y `SMOPAnimal` lo implementa.

```java
// ISleepingEntity — vacío, igual que los tres que reemplaza.
default void onSleepPhaseBegin(SleepPhase phase) {}

// SMOPAnimal — la implementación real.
@Override
public void onSleepPhaseBegin(SleepPhase phase) {
    this.playIfRegistered(phase.clipName());
}
```

El default **no puede** llamar a `playIfRegistered` desde la interfaz: ese método es
`protected` en `SMOPAnimal` y un método default no tiene acceso. Por eso la implementación
va en la clase, exactamente donde está hoy.

Queda un único punto de extensión (una entidad que quiera un sonido al despertar lo
sobrescribe y llama a `super`), la convención de nombres queda explícita en el enum, y
agregar fases no agranda la interfaz. Los tres métodos viejos se borran sin que ninguna
entidad se entere.

## Bug a corregir: el ciclo persistido deja al mob trabado

`SMOPAnimal` persiste las tres banderas de sueño y las restaura al cargar. Pero
`SleepGoal.canUse()` empieza con:

```java
if (this.mob.isInSleepCycle() || !this.urge.wantsToSleep()) return false;
```

Un mob guardado durmiendo carga con las banderas puestas, y **por eso mismo el goal se
niega a arrancar**. Nadie queda manejando el ciclo, nada limpia las banderas, y como
`isInSleepCycle()` alimenta `isMovementLocked()`, el mob queda congelado dormido de forma
permanente. Ni siquiera el daño lo saca: `requestWake()` solo marca una bandera que el
goal tendría que consumir, y el goal no está corriendo.

Es la misma forma que el bug del perch que ya se corrigió: estado persistido cuyo driver
no se restaura.

**Corrección: no persistir la fase de sueño.** Al cargar, el mob está despierto; si sigue
siendo de noche y está tranquilo, `SleepUrge` lo vuelve a dormir en unos segundos. Elimina
la clase entera de estados trabados en vez de intentar restaurarlos con cuidado. Se borran
las tres claves NBT (`Sleeping`, `PreparingSleep`, `Awakening`).

## Corrección aparte: duraciones declaradas desactualizadas

`clipDurationTicks()` lee la duración **declarada** al registrar la animación, no el largo
real del clip — porque `AnimationDefinition` es client-only y el servidor no puede
preguntarle al clip cuánto dura. Son dos números que tienen que coincidir, escritos en
dos lugares.

El export nuevo del Krifto acortó los clips y esos números quedaron viejos:

| Fase | Clip real | Declarado hoy |
|---|---|---|
| `preparing_sleep` | 0.5 s | 2.5 s |
| `sleep` | 2.0 s | 4.0 s |
| `awakening` | 0.3 s | 3.5 s |

Como `SleepGoal` usa el declarado como timer, hoy el Krifto se queda **congelado en el
último frame 2 s al acostarse y 3,2 s al despertar** — exactamente lo que advierte el
comentario que ya está en `SleepGoal`: *"Padding the phase past the clip leaves the mob
frozen on the last frame for the difference."*

Se corrigen los tres al largo real. (El de `sleep` es inocuo porque `SLEEPING` no usa el
timer, pero queda mal como documentación.)

## El timer de fase corre a ritmo completo

`SleepGoal` declara `requiresUpdateEveryTick()`, y para este goal eso es **corrección, no
suavidad**. `Mob#serverAiStep` solo corre el goal selector completo en ticks alternos y
deja el resto a `tickRunningGoals(false)`, que saltea cualquier goal que no lo pida. Sin
eso, el contador de fase avanza un tick de goal cada dos del juego y **cada fase dura
exactamente el doble que su clip**: la animación termina a mitad de su propia fase y lo que
haya debajo en el blend —el idle de pie— se ve durante el resto.

Síntoma exacto: al despertar se veía `awakening` → *parado* → `sitting` → `standing_up` →
*parado*. Los dos "parado" no eran clips, era el idle asomando en el hueco.

Estuvo oculto mientras las duraciones declaradas eran más largas que los clips: el mob
quedaba congelado en un último frame de todos modos. Hacerlas coincidir lo destapó.

## Qué toca en cada entidad

- **Tangoftero, Salmon:** nada. Cero cambios.
- **Krifto:** registrar `sitting`, `sit` y `standing_up` con sus play conditions, y
  corregir las tres duraciones declaradas.

## Limpieza de paso

El javadoc de `ISleepingEntity` dice que el ciclo lo maneja un `SleepCycleController`,
clase que no existe — lo maneja `SleepGoal`. Se corrige.

## Verificación

- El Tangoftero duerme exactamente como antes: se acuesta, duerme, se levanta.
- El Krifto recorre las seis fases en orden, sin congelarse entre medio.
- Despertar al Krifto mientras está en `sit` lo hace pararse directo, sin animar un
  despertar.
- Despertarlo dormido corre `awakening` y después `standing_up`.
- Guardar y cargar con un mob durmiendo: al volver está despierto y se mueve; no queda
  trabado.
- Una manada de Kriftos no se sienta toda en el mismo tick.
