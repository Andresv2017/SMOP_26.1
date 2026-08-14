# Hell Hippo — port de 1.20.1 a 26.1

Fecha: 2026-08-09
Alcance: portar el `Hell_HippoEntity` de `Spectacular-Mobs-of-Peligoro` (Forge 1.20.1) a SMOP 26.1,
reformulado sobre `SMOPAnimal` y DeluxeLib.
Depende de: `AnimSound` e `IdleAnimationGoal`, ya implementados.

## Estado — actualizado 2026-08-12

| Fase | Estado |
|---|---|
| **1 — El mob vivo** | **Completa.** Con tres desviaciones respecto de lo escrito aquí, marcadas abajo con *(desviación)*. |
| **2 — Domesticación** | **2a y 2b completas**, con desviaciones marcadas abajo. Falta la 2c (cofre, armadura, inventario). |
| **3 — Montura y sistemas sociales** | Sin empezar. |

La Fase 1d (*Agua*) se rehízo entera después de escribirse este documento; el diseño que rige ahora
es `2026-08-11-hell-hippo-lecho-marino-design.md` y lo que dice esta sección se conserva solo como
registro de lo que se pidió en su día.

Las dos ramas que leían `isSaddled()` — `picksItsOwnFights()` y `tickSeaweed()` — ya no están muertas:
la 2b implementó `canUseSlot` y metió el mob en la tag de la silla.

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

> ***(desviación) — "sale gratis" era verdad a medias, y costó un bug.*** Faltan dos cosas que no son
> opcionales:
>
> **La tag.** La silla se declara con
> `setAllowedEntities(EntityTypeTags.CAN_EQUIP_SADDLE)` (`Equippable#saddle`), y `canBeEquippedBy`
> rechaza toda entidad fuera de ella. Sin añadir el mob a
> `data/minecraft/tags/entity_type/can_equip_saddle.json`, `isEquippableInSlot` devuelve false y
> ensillar **no hace absolutamente nada**, sin error ni pista. Con `"replace": false`, para sumar a la
> lista de vanilla en vez de sustituirla.
>
> **Negarse tiene que consumir la acción.** La silla también trae `setEquipOnInteract(true)`, y
> `Player#interactOn` llama a `entity.interact(...)` primero pero **sigue** hasta
> `itemStack.interactLivingEntity(...)` si el resultado no consume — y `InteractionResult.Fail` no
> consume. O sea que un `return FAIL` desde `mobInteract` deja que la ruta genérica equipe la silla de
> todas formas, saltándose las condiciones que se acaban de comprobar. Hay que devolver
> `InteractionResult.CONSUME` (un `Success` con swing `NONE`), o la negativa es decorativa.

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

> ***(desviación) — esta sub-fase se quedó corta al escribirla.*** Describía una cadena de dos pasos.
> Al ir al legacy a resolver una contradicción con la 2a aparecieron **tres pasos más** en medio, y
> son los que le dan sentido al conjunto. Lo que sigue es la cadena real, ya implementada.

```
carne cruda → 1 en 3 → confianza
                          ↓
              te planta cara, 300 ticks (clip intimidate + gruñido)
                          ↓                    ↘ si lo miras 5 s → smop:fear
              poción de debilidad → lo tumba    ↘ si expira despierto → te olvida
                          ↓
                   silla → despierta ensillado
```

**1 · Confianza.** Carne cruda en mano, **1 en 3 por intento**. Es un dado, no un contador — cada
carne es una tirada independiente y el coste no tiene techo. *(La 2a afirma que los tres rituales del
mod cuentan intentos hacia una meta; para este es falso. Cuentan el Krifto y el Niras, que son los dos
usuarios reales de `TameProgress`.)*

El vínculo es la **propiedad de vanilla**, no un flag propio: 1.20.1 llevaba un `DATA_TRUSTING`
sincronizado junto a un `trustingPlayerUUID` que guardaba y cargaba a mano, y `TamableAnimal` ya trae
las dos cosas, sincronizadas y persistidas, y de propina rechaza atacar a su dueño.

*Desviación menor:* las crías quedan fuera del ritual. La carne está también en `FOOD_ITEMS`, así que
interceptarla en una cría costaría el "alimentar para crecer" de vanilla a cambio de un vínculo
inservible — la silla exige adulto.

**2 · El plante.** Recién ganada la confianza y **mientras no lleve silla**, a menos de 10 bloques y
con línea de visión, se planta durante **300 ticks**. No se mueve: `isMovementLocked()` lo incluye, así
que todos los goals de movimiento se retiran, y solo gira sobre su eje siguiendo al jugador.

**3 · No lo mires.** Sostenerle la mirada de frente (`dot > 0.95`) durante **100 ticks** seguidos
aplica `smop:fear` 300 ticks.

**4 · Y caduca.** Si el reloj llega a cero **estando despierto**, pierde la confianza entera y vuelve a
ser salvaje — con lo que puede volver a atacarte. El reloj corre aunque el dueño se desconecte; solo
el *arranque* exige tenerlo delante. Atarlo a la presencia del dueño convertiría el cierre de sesión
en un reinicio gratis.

**5 · La poción de debilidad es la salida.** Y es la pieza que faltaba: `trySaddle` exige un animal
dormido, y esperar a que anochezca mientras te lo comen con los ojos no es un plan. La debilidad lo
tumba en el sitio, a cualquier hora. Mientras duerme, el plante **se suspende entero** — reloj parado,
sin clip, sin fear — y esa congelación es justo la tregua que compras.

**6 · La silla.** Solo **dormido por la poción** (`isKnockedOut()`, no el sueño natural de la noche) y
solo el dueño. Ponerla detiene el plante, gasta la debilidad y lo despierta.

Se agrega también el filtro de objetivo del legacy: un Hell Hippo con dueño no ataca a ese jugador ni
a sus mascotas. `TamableAnimal#canAttack` ya cubre al dueño; lo añadido son sus otras mascotas.

### La señalización, que el port se cargó sin darse cuenta

Este documento decide, con razón, que **los mensajes de chat se eliminan**. Pero en 1.20.1 esos
mensajes eran *la única señal* de los pasos 2 a 5: "is now intimidating", "you are terrified", "has
calmed down and forgotten your trust". Sin ellos la cadena es indescifrable — nadie deduce que hay que
tirarle una poción.

Lo sustituye el clip `intimidate`, que llevaba meses portado y sin registrar, más un gruñido al
entrar. Si el jugador no puede ver que hay un reloj corriendo, esto se lee como que el mob se
des-domestica solo.

**El clip se partió en tres.** Llegó como una sola pieza de 7.5 s y así se registró al principio, y se
veía mal por una razón que ningún `.looping()` arregla: un clip de 7.5 s cubriendo una ventana de 15 s
tiene que reiniciarse, y el reinicio pasa por la salida de la pose de la que está hecho su último
segundo — el animal se relajaba y volvía a entrar, dos veces por plante. Ahora son `intimidate_in`
(0.65 s), `intimidate_loop` (2.0 s, `.looping()`) e `intimidate_out` (0.95 s, conserva el parpadeo),
encadenados como el ciclo de sueño.

*Nota de autoría:* el original tenía **dos periodos distintos** — cuello, cabeza y mandíbula a 2.0 s;
cuerpo y torso a ~2.65 s — así que no existía ventana corta donde todo cerrara a la vez, que es por lo
que estaba autorizado como one-shot. El bote del cuerpo y el hinchado del torso se re-temporizaron a
2.0 s para que el bucle cierre sin costura, a cambio de que vayan un pelín más rápidos.

*Verificación:* alimentarlo repetidamente termina ganando su confianza; se planta sin moverse y
girando hacia el jugador; mirarlo fijo da fear; dejar correr el reloj pierde la confianza; la poción lo
tumba de día y con el jugador al lado; la silla entra dormido por poción y es rechazada despierta, por
otro jugador, o dormido de noche por su cuenta.

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
