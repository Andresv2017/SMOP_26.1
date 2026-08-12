# Hell Hippo — port de 1.20.1 a 26.1

Fecha: 2026-08-09
Alcance: portar el `Hell_HippoEntity` de `Spectacular-Mobs-of-Peligoro` (Forge 1.20.1) a SMOP 26.1,
reformulado sobre `SMOPAnimal` y DeluxeLib.
Depende de: `AnimSound` e `IdleAnimationGoal`, ya implementados.

## Estado — actualizado 2026-08-12

| Fase | Estado |
|---|---|
| **1 — El mob vivo** | **Completa.** Con tres desviaciones respecto de lo escrito aquí, marcadas abajo con *(desviación)*. |
| **2 — Domesticación** | Sin empezar. |
| **3 — Montura y sistemas sociales** | Sin empezar. |

La Fase 1d (*Agua*) se rehízo entera después de escribirse este documento; el diseño que rige ahora
es `2026-08-11-hell-hippo-lecho-marino-design.md` y lo que dice esta sección se conserva solo como
registro de lo que se pidió en su día.

Dos ramas muertas esperan a la Fase 2b: `picksItsOwnFights()` y `tickSeaweed()` leen `isSaddled()`,
que hoy nunca puede ser cierto porque `canUseSlot` no está implementado en ninguna parte del mod.

## Dónde encaja

El mod legacy tiene seis mobs. Tres ya están portados y terminados — Tangoftero, Salmon,
Kriftognathus. Quedan tres, y **ninguno tiene su infraestructura base construida todavía**:

| Mob | Pieza base que falta |
|---|---|
| **Hell Hippo** | montura real: silla, volante, inventario con cofre |
| **Niras** (Nirasmosaurus) | base anfibia (tierra/agua) + hitboxes multi-parte |
| **GT** (Grand Tyrant) | hitboxes multi-parte (`PartEntity`), 7 sub-cajas |

Se elige el Hell Hippo primero porque **es el único con cimientos ya puestos en SMOP 26.1**, hoy sin
usar por nadie:

- `RiderControllable` — interfaz de acciones de montura cuyo javadoc nombra literalmente al Hell
  Hippo (`ATTACK`, `FEAR`, `OPEN_INVENTORY`) y al Niras (`DESCEND`)
- `SMOPKeybinds` + `RiderActionServerPacket` — keybinds del jinete y packet cliente→servidor
- `FearEffect` / `smop:fear` — el debuff de intimidación, con javadoc que dice *"The Hell Hippo's
  intimidation debuff"*
- `HELL_HIPPO_RAW_MEAT` y `HELL_HIPPO_COOKED_MEAT`

Terminarlo cierra código que ya está a medias en el repo, en vez de abrir un frente nuevo.

## La decisión que define el port: de qué hereda

El legacy extiende `AbstractChestedHorse`, que regala silla, cofre y monta. Pero esa clase desciende
de `Animal`, y `SMOPAnimal` desciende de `TamableAnimal`: **son ramas hermanas y no se pueden tener
las dos**.

Ese es el motivo real de que el archivo legacy tenga 1461 líneas en un solo archivo — al quedar
fuera de la jerarquía compartida tuvo que reimplementar a mano sueño, género, animaciones y grupo.

**El port hereda de `GenderedSMOPAnimal`** e implementa a mano las piezas de montura. Verificado
contra el jar de 26.1: `ItemSteerable` y `ContainerEntity` son `public interface`, implementables
sobre cualquier clase.

### La silla ya no es una interfaz: es equipamiento

`Saddleable` **no existe en 26.1** — fue eliminada. Ensillar pasó al sistema genérico de
equipamiento, y eso simplifica el port bastante:

- `Mob#isSaddled()` ya existe en **todo** `Mob`, definido como
  `hasValidEquippableItemForSlot(EquipmentSlot.SADDLE)`. No hay nada que implementar.
- La armadura es `EquipmentSlot.BODY`, otro slot de equipamiento real.
- `canUseSlot(EquipmentSlot)` es el punto donde se decide si el mob admite ese slot. El caballo lo
  usa así: `slot != SADDLE ? super : isAlive() && !isBaby() && isTamed()`.

Consecuencia para la Fase 2: la silla y la armadura no necesitan banderas sincronizadas ni bonos de
atributo a mano — son equipamiento, con su persistencia, su render y su reducción de daño ya
resueltos por vanilla. `canUseSlot` cubre la condición que no depende del jugador (domesticado, no
cría); la condición que **sí** depende de quién interactúa (ser el jugador de confianza, y que el
mob esté dormido) va en `mobInteract`, que es el único lugar con esa información.

Se gana gratis, sin escribir nada: ciclo de sueño de seis fases, acciones guionadas, animador de
DeluxeLib con `AnimSound`, `IdleAnimationGoal`, género, bloqueo de movimiento, grupo/líder, cría.

Se paga: escribir silla, volante e inventario. **El Niras también es montable** (de ahí el keybind
`DESCEND`), así que ese trabajo se usa dos veces.

### Firma

```java
public class HellHippoEntity extends GenderedSMOPAnimal
        implements ItemSteerable, ContainerEntity, RiderControllable,
                   IGroupBehaviour, IHasLeader, ISleepThreatEvaluator, ISleepAwareness
```

Tamaño 2.5×2.5, `MobCategory.CREATURE`, `maxUpStep` elevado como el legacy (`STEP_HEIGHT = 1.0`).

*(desviación)* La firma real hoy es solo
`implements ISleepThreatEvaluator, ISleepAwareness`. `IGroupBehaviour` e `IHasLeader` se cayeron con
la manada — ver 1b — y las tres de montura llegan con las fases 2 y 3.

## Qué se reusa de SMOP 26.1

`IGroupBehaviour`, `IHasLeader`, `GroupUtil`, `FollowGroupLeaderGoal`, `GenericBreedGoal`,
`SMOPRandomStrollGoal`, el ciclo de sueño (`SleepPhase`/`SleepGoal`/`SleepUrge`), `Gendered`,
`FearEffect`, `RiderControllable` + keybinds + packet, y las dos carnes.

*(desviación)* De esa lista **no** se acabaron usando las cuatro de manada (ver 1b) ni
`GenericBreedGoal` — esa existe para las ponedoras y cierra el apareamiento con
`finalizeSpawnChildFromBreeding(..., null)`, o sea corazones y experiencia pero ninguna cría, que es
justo como se veía. Un hipopótamo pare vivo, así que usa el `BreedGoal` de vanilla.

## Fases

Cada fase deja algo jugable y testeable por separado.

---

### Fase 1 — El mob vivo

#### 1a · Esqueleto

Entidad, registro en `SMOPEntities`, spawn en `SMOPSpawns`. Atributos del legacy, sin cambios:

| Atributo | Valor |
|---|---|
| `MAX_HEALTH` | 20.0 |
| `FOLLOW_RANGE` | 28.0 |
| `MOVEMENT_SPEED` | 0.250 |
| `ATTACK_SPEED` | 0.250 |
| `ATTACK_DAMAGE` | 2.0 |
| `ATTACK_KNOCKBACK` | 0.5 |
| `ARMOR_TOUGHNESS` | 0.1 |
| `ARMOR` | (sin valor base) |

Modelo y renderer adulto y cría, animaciones portadas desde `Hell_HippoAnimations` (3294 líneas) al
formato de `KriftoAnimations`. Clips de locomoción conectados: idle, caminar, correr, nadar.

*Verificación:* aparece, camina, nada, tiene cría con su propio modelo.

**Spawn natural** *(desviación)*, resuelto el 2026-08-12 y con números distintos de los del legacy:

| | biomas | peso | manada |
|---|---|---|---|
| 1.20.1 | `savanna` | 15 | 1-3 |
| 26.1 | las tres sabanas + `swamp` + `mangrove_swamp` | **4** | **1-1** |

El peso no se hereda porque el legacy no competía contra la misma tabla. La sabana ya reparte 52
puntos de `CREATURE` (oveja 12, cerdo 10, gallina 10, armadillo 10, vaca 8, caballo 1, burro 1) y el
pantano 50 (las mismas granjeras más rana 10). A peso 15 el hipo se lleva el 22% de las tiradas — más
que la oveja, o sea el animal más común del bioma, que es exactamente como se veía en juego. A 4 se
queda cerca del caballo: el animal grande y propio del bioma con el que te cruzas de vez en cuando.

Son **dos** registros, y el segundo es fácil de olvidar: la entrada de bioma en `SMOPSpawns` y la
regla de colocación en `SMOPEntityAttributes`. Sin la segunda,
`SpawnPlacements#getPlacementType` cae a `NO_RESTRICTIONS` y `checkSpawnRules` devuelve `true` sin
mirar nada, con lo que la entrada de bioma sola colocaría hipos donde cayera. Y la entrada solo vive
en memoria hasta que `runDataServer` la escribe como datapack.

Se registra `ON_GROUND` pese a ser anfibio: eso decide dónde se le coloca al nacer, no dónde puede ir.

#### 1b · Vida propia

Sueño de seis fases, género, cría, grupo/líder (`GroupType.PACK`), y gestos de reposo vía
`IdleAnimationGoal` con los clips estéticos que traiga el rig.

*Verificación:* duerme de noche y despierta, se agrupa siguiendo a un líder, gesticula estando quieto.

**Dos desviaciones**, ambas deliberadas:

- *(desviación)* **El sueño tiene tres fases, no seis.** El ciclo se arma con los clips que el mob
  registre, y este rig no trae set de sentarse — así que sus fases son `preparing_sleep`, `sleep` y
  `awakening`, y las de sentarse simplemente no existen. Mismo caso que el Tangoftero.
- *(desviación)* **La manada se descartó entera.** Llegó a portarse y se quitó: el líder era el
  miembro que devolviese primero una consulta espacial, y el desempate de quién elegía era el id de
  entidad más bajo *dentro del vecindario de cada miembro* — así que miembros en extremos opuestos de
  un grupo laxo veían vecindarios distintos, elegían líderes distintos, y partían la manada que
  existían para mantener unida. En su lugar el hipo aparece solo, y una hembra trae cría el 50% de
  las veces (`CALF_COMPANION_CHANCE`), lo que da la misma lectura en pantalla sin estado que pueda
  contradecirse consigo mismo. `IGroupBehaviour`, `IHasLeader`, `GroupUtil` y `FollowGroupLeaderGoal`
  quedan sin usar por este mob.

#### 1c · Combate

Los goals de combate del legacy, reformulados: `HellHippoAttackGoal`, `HellHippoDefendOwnerGoal`,
`HellHippoHurtByTargetGoal`, `HippoTargetPlayerGoal`, `HippoTargetPreyGoal` (ovejas, cabras, vacas).

El golpe va por `HitWindow`/`AttackShape` de DeluxeLib, no por un `hurt()` a mano dentro del goal —
mismo tratamiento que recibieron el Krifto y el Tangoftero.

*Verificación:* caza presas, contraataca al ser golpeado, no ataca dormido.

#### 1d · Agua

`HellHippoWaterStrollGoal`, `HellHippoLeaveWaterShakeGoal`, y las algas: sumergido por completo 200
ticks le crecen algas encima; esquilarlas con tijeras da 2 kelp y bloquea el recrecimiento 100 ticks.

*Verificación:* deambula bajo el agua, se sacude al salir, le crecen algas y se pueden esquilar.

> ***(desviación) — esta sub-fase está superada.*** Lo que se construyó aquí no bastaba: el animal se
> plantaba en el fondo y temblaba. Se rehízo entera bajo
> `2026-08-11-hell-hippo-lecho-marino-design.md`, que es el documento que rige. En resumen: la
> navegación anfibia se cambió por una `SeabedPathNavigation` propia que camina el lecho y solo nada
> cuando no hay ruta a pie; la física submarina pasó a `WATER_MOVEMENT_EFFICIENCY` en vez de un
> `travel()` casero; los dos goals de deambular se unificaron en uno; y la raya entre los clips de
> tierra y los de agua dejó de ser `isInWater()` para pasar a la profundidad del agua sobre el cuerpo.
> Las algas y el sacudón siguen como se pidió aquí, con el gesto de reposo ahora prohibido dentro del
> agua.

---

### Fase 2 — Domesticación

#### 2a · Progreso de tameo reutilizable

El mod tiene **tres rituales de tameo distintos**, y los tres comparten la misma contabilidad:

| Mob | Ritual | Forma |
|---|---|---|
| Krifto | tirar carne al suelo, se acerca y come ×3-4 | goal (`TameFeedGoal`, ya portado) |
| Niras | cebo en mano, guarda distancia, el jugador se aleja, come ×3 | goal (`PreAggroTameGoal` en legacy) |
| Hell Hippo | alimentar a mano, 1 de 3 | interacción en `mobInteract` |

Difieren en **cómo el mob llega a la comida**; coinciden en **contar intentos hacia una meta,
persistirlos, y decidir quién queda de dueño al cerrar**. Se extrae solo eso — la parte que de verdad
es igual — sacándolo de lo que ya funciona en el Krifto (`feedProgress`/`feedGoal`/
`incrementFeedProgress`/`getFeedGoal`, hoy campos privados de `KriftognathusEntity`).

Deliberadamente **no** se abstrae el acercamiento: uno es un goal que camina hacia un ítem tirado,
otro es un goal con retirada del jugador, y el tercero ni siquiera es un goal. Forzar los tres a una
sola forma sería inventar una abstracción que los datos no piden.

El Krifto migra a la pieza nueva en esta sub-fase, que es lo que la valida con un caso ya probado en
juego.

*Verificación:* el ritual del Krifto sigue comportándose exactamente igual, incluido conservar el
progreso al recibir daño y a través de guardar/cargar.

#### 2b · Confianza y silla

La cadena del legacy se preserva tal cual, porque es lo mejor que tiene el mob:

1. **Confianza** — alimentarlo a mano con carne cruda (`Items.BEEF` en el legacy); una probabilidad
   de 1 en 3 por intento. Al lograrlo guarda el UUID del jugador. El goal de tentación y la cría
   usan zanahoria y carne (`CARROT`, `BEEF`).
2. **Silla** — solo se le puede poner **mientras duerme** y **solo el jugador de confianza**.
   Ponerla lo despierta.

Es una domesticación en dos etapas con una ventana de vulnerabilidad, y vale la pena conservarla.

Se agrega también el filtro de objetivo del legacy: un Hell Hippo con dueño no ataca a ese jugador ni
a sus mascotas.

*Verificación:* alimentarlo repetidamente termina ganando su confianza; la silla es rechazada
despierto o por otro jugador; aceptada dormido por el de confianza, y lo despierta.

#### 2c · Cofre, armadura e inventario

Cofre (requiere silla) e inventario vía `ContainerEntity`.

**La armadura queda gratis.** El legacy la llevaba como bandera sincronizada `DATA_ARMOR` más un
bono de atributo aplicado y revertido a mano (`updateArmorBonus`), unas 70 líneas. En 26.1 es
`EquipmentSlot.BODY`: vanilla ya le da persistencia, render y reducción de daño. Solo hay que
declarar que el mob admite el slot y que el ítem sea equipable ahí.

*Verificación:* el cofre solo entra con silla puesta; el inventario abre, guarda y persiste; la
armadura se ve puesta y reduce el daño recibido.

---

### Fase 3 — Montura y sistemas sociales

#### 3a · Volante

`ItemSteerable` y el boost. El jinete controla la dirección.

*Verificación:* montado se conduce; el boost acelera y se consume.

#### 3b · Intimidación

Pulso de radio 10 que aplica `smop:fear` durante 60 ticks a todo lo vivo alrededor, **salvo** el
jinete, otros Hell Hippos, y las mascotas del jinete. Cooldown propio con boss bar visible únicamente
para el jinete. Se dispara con `RiderAction.FEAR`, que ya llega por el packet existente.

#### 3c · Ataque montado

Raycast de 5.5 bloques desde los ojos; pega al objetivo más cercano en la mira. Cooldown propio con
su boss bar. Se dispara con `RiderAction.ATTACK`.

*Verificación de la fase:* ambas acciones responden a sus keybinds, respetan cooldown, las boss bars
solo las ve el jinete y desaparecen al desmontar.

---

## Qué cambia respecto del legacy

**Los mensajes de chat se eliminan.** El legacy escribe una línea de chat en casi cada acción
(`"§9Hell Hippo unleashes FEAR!"`, `"§aHell Hippo is now equipped with a chest."`, y una decena más).
Entre boss bar, animación y sonido la acción ya se comunica; el texto sobra, no está traducido, y
ensucia el chat en cada uso.

**La armadura pasa a ser equipamiento de vanilla** (`EquipmentSlot.BODY`), borrando la bandera
sincronizada y el manejo manual del bono de atributo. Ver 2c.

**El combate pasa por `HitWindow`**, no por `hurt()` manual dentro del goal.

## Fuera de alcance

- **Niras y GT.** Cada uno lleva su propio spec, y cada uno necesita infraestructura que este port no
  construye (base anfibia, hitboxes multi-parte).
- **El goal de cebo con retirada** (`PreAggroTameGoal`). Se evaluará al portar el Niras, que es su
  único usuario real; el Hell Hippo no lo necesita porque su tameo es una interacción.
- **Migrar la cadena de rugido del Tangoftero** a la primitiva de acción guionada. Deuda anotada en
  el spec de animaciones huérfanas del Krifto, independiente de este trabajo.
