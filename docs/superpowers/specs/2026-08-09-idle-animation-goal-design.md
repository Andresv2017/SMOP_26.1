# Gestos de reposo — `IdleAnimationGoal`

Fecha: 2026-08-09
Alcance: goal reutilizable en SMOP, más la migración del `squawk` del Kriftognathus.
Depende de: `AnimSound` (spec de DeluxeLib del 2026-08-09), ya implementado.

## Problema

El `squawk` del Krifto se dispara desde un override de `playAmbientSound()`. Eso funciona, pero
está mal puesto por tres razones distintas:

**1. Es el hook de sonido, no de animación.** Vanilla llama a `playAmbientSound()` para que el mob
reproduzca su ruido ambiente. Lo estamos usando para lanzar una animación, con el sonido ya movido
al clip vía `AnimSound`. El nombre miente sobre lo que hace.

**2. La cadencia no es nuestra, y la de vanilla no sirve.** `Mob#baseTick` resetea
`ambientSoundTime` en cuanto su tirada pasa, **antes** de cedernos el control — así que cada tirada
que cae mientras el mob vuela o camina se gasta sin producir nada, y la frecuencia real no es la que
el intervalo declara. Además, en juego los llamados salieron a uno o dos segundos uno del otro, algo
que su piso de 80 ticks debería volver imposible; se investigó (descartando audio duplicado en el
OGG, overrides del intervalo, otros llamadores, `PLAY_ONCE.repeats()`, el auto-arranque del animator
y el round-trip de los packets de sync) sin encontrar la causa dentro de nuestro código. Depender de
un temporizador ajeno que no se comporta como documenta no es una base sobre la que construir.

**3. No se puede reutilizar.** Cada animal que quiera un gesto de reposo tiene que volver a escribir
el override, su propio contador y sus propias condiciones.

## Qué se construye

Un goal, `IdleAnimationGoal`, que reproduce un gesto puramente estético cada tanto mientras el
animal está tranquilo.

### Ubicación

`net.darkblade.smop.entity.ai.goal`, junto a los otros goals reutilizables del mod.

En SMOP y no en DeluxeLib por una razón técnica: dispara `startAction(nombre)`, el sistema de
acciones guionadas de `SMOPAnimal`. DeluxeLib no tiene equivalente, así que el goal no tendría dónde
apoyarse allá.

### API

```java
public class IdleAnimationGoal extends Goal {
    public IdleAnimationGoal(SMOPAnimal mob, int cooldownTicks, int cooldownSpreadTicks);

    public IdleAnimationGoal add(String clip);              // peso 1
    public IdleAnimationGoal add(String clip, int weight);
    public IdleAnimationGoal condition(Predicate<SMOPAnimal> extra);
}
```

Uso en el Krifto:

```java
this.goalSelector.addGoal(10, new IdleAnimationGoal(this, SQUAWK_COOLDOWN_TICKS, SQUAWK_SPREAD_TICKS)
        .add(ANIM_SQUAWK)
        .condition(mob -> !this.isFlying()));
```

`condition` existe para lo específico de cada animal: `isFlying()` vive en `SMOPFlyingAnimal`, no en
`SMOPAnimal`, así que no puede ser un default del goal.

### Pesos

Cada clip lleva un peso; se elige uno al azar proporcionalmente. Con un solo clip el peso es
irrelevante y la llamada queda igual de corta que si no existieran.

```java
new IdleAnimationGoal(this, 200, 100)
        .add("squawk", 10)   // común
        .add("stretch", 3)   // ocasional
        .add("yawn", 1);     // raro
```

Un único cooldown gobierna todo el conjunto, que es la diferencia con registrar un goal por gesto:
así dos gestos distintos no pueden salir pegados uno tras otro.

### El sonido no está acá

El goal no conoce el audio. Cada clip lleva sus propios sonidos vía `AnimSound` en
`registerAnimations()`, así que un pool con tres gestos tiene tres sonidos distintos (o dos sonidos
en un mismo gesto, o ninguno) sin que el goal se entere:

```java
squawk.sound(AnimSound.at(3, SMOPSounds.KRIFTO_SQUAWK.get()).pitchJitter(0.05F));
stretch.sound(AnimSound.at(8, SMOPSounds.KRIFTO_YAWN.get()).volume(0.6F));
// shake sin .sound(...) — gesto mudo
```

Volumen, pitch y jitter son por sonido. Un bostezo puede ir bajito y el chillido a todo volumen.

### Sin flags de goal

El goal no reserva `MOVE` ni `LOOK`. Un gesto cosmético que reservara flags bloquearía
comportamientos reales sin ninguna razón — el gesto no mueve al animal a ningún lado. En cambio el
propio goal exige reposo para arrancar, que es la restricción que de verdad hace falta.

Consecuencia: al no tener flags, la prioridad con la que se registre es indiferente para el
arbitraje (`goalCanBeReplacedForAllFlags` da verdadero siempre). Se registra en 10 por legibilidad,
al final de la lista, no porque el número haga algo.

### Ciclo

**`canUse`** — todas estas:

- `mob.tickCount >= nextAllowedTick` (cooldown vencido)
- `!mob.isMoving()`
- `mob.getTarget() == null`
- `!mob.isInSleepCycle()`
- `!mob.isPerformingAction()` — sin esto arrancaría un gesto encima de un `eating` en curso;
  `isMovementLocked()` no alcanza, porque un gesto cosmético justamente no inmoviliza
- `!mob.isMovementLocked()`
- la condición extra del animal
- el pool no está vacío

**`start`** — elige un clip por peso, `mob.startAction(clip)`, y arma el próximo cooldown:
`nextAllowedTick = tickCount + cooldown + random(spread)`.

**`canContinueToUse`** — sigue corriendo *nuestra* acción y siguen valiéndose las condiciones de
reposo. En cuanto aparece un objetivo o el animal empieza a caminar, deja de valer.

**`stop`** — si la acción en curso sigue siendo la nuestra, `mob.stopAction()`. La comprobación
importa: si el clip ya terminó solo, o si algo ajeno arrancó otra acción en el ínterin, cortar a
ciegas pisaría algo que no nos pertenece.

La interrupción llega dentro de 2 ticks (~100 ms): `Mob#serverAiStep` corre el selector completo en
ticks alternos, y es ahí donde se evalúa `canContinueToUse`.

### Estado

| Campo | Persistencia | Para qué |
|---|---|---|
| `nextAllowedTick` | ninguna | Cooldown. No hace falta persistirlo: `tickCount` también arranca en 0 al recargar la entidad, así que quedan consistentes y tras un reload el primer gesto sale libre |

## Migración del Krifto

| Se borra | Se agrega |
|---|---|
| El override completo de `playAmbientSound()` | El registro del goal en `registerGoals()` |
| El campo `nextSquawkTick` | — |
| Las constantes de cooldown pasan a ser argumentos del goal | — |

`getAmbientSound()` se queda devolviendo `null`, que ahora significa lo que dice: este mob no tiene
sonido ambiente de vanilla. `makeSound` ignora un `SoundEvent` nulo, así que la rama de vanilla queda
inerte sin necesidad de override.

## Fuera de alcance

- **Migrar otros animales.** El Tangoftero y el Salmon no tienen hoy clips de reposo sin usar — los
  `sniff`/`dig` del Salmon ya los conduce `SalmonDigGoal`. El goal queda disponible para cuando
  aparezcan.
- **Gestos que se superponen al movimiento** (mover la cola, parpadear mientras camina). Necesitarían
  relajar la exigencia de reposo, y no hay ningún clip que lo pida.

## Verificación

- Un Krifto parado en el suelo hace el gesto cada tanto, con sonido, y entre 6 y 12 segundos de
  separación — nunca dos seguidos.
- No lo hace volando, durmiendo, en combate, ni caminando.
- Si aparece un enemigo o empieza a caminar a mitad del gesto, el gesto se corta.
- Alimentarlo (`eating`) no dispara un gesto encima.
- La cría también lo hace, con su propio clip.
- El Tangoftero y el Salmon siguen igual que antes.
