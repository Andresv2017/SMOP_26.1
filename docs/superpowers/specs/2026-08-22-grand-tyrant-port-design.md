# Port del Grand Tyrant de 1.20.1 a 26.1

## Dónde encaja

Sexta y última criatura del mod, y la única que queda. Con el Tangoftero, el Salmón, el
Kriftognathus, el Hell Hippo y el Nirasmosaurus portados, y el inventario de items cerrado salvo los
dos suyos, el GT es lo que separa a SMOP 26.1 de tener todo el contenido del legacy.

Es también el mob que más se beneficia de haber ido el último. Cuatro de las cinco piezas que lo
componen ya existen en DeluxeLib o en la base del mod, y la quinta —la multiparte— la comparte con el
Nirasmosaurus, que la aplazó explícitamente hasta aquí.

## Qué se porta y qué no

Igual que en el Niras: **el 1.20.1 es la fuente de la que se lee el diseño, no de la que se copia el
código.**

- **Se porta:** el comportamiento y los **keyframes**, que son arte autorado.
- **No se porta:** `GTAttackController` (275 líneas), `GTAttackGoal` (189), el enum de ataques con sus
  `damageFrames`, `GTMoveControl`, `HitboxEntity`, los dos paquetes de FX y el `SleepCycleController`.
  Todo eso tiene equivalente en librería.
- **Se retunea, no se copia:** los números. Las duraciones del legacy son **punto de partida a
  verificar contra el `withLength` de cada clip**. Adivinarlas fue el fallo más repetido del port del
  Hell Hippo.

## El tamaño real

| | Legacy | Comentario |
|---|---|---|
| Entidad | 295 líneas | La mitad es multiparte y malabares de `AnimationState` |
| Goals | 245 líneas | `GTAttackGoal` 189, dos de targeting |
| Controlador de ataque | 275 líneas | Desaparece entero: lo cubre `HitWindow` |
| Animaciones | **5.428 líneas** | `GTAnimations` 3.365 + `GTAnimationsBase` 2.063 |
| Cabeza decorativa | 4 clases + modelo de 128 líneas | Se reduce a un `StatueConfig` |

Medido en lo que hay que escribir de verdad, el grueso es: atar 5.428 líneas de clips a un rig que
lanza excepción si un hueso no existe, y estrenar Cortex.

## De qué hereda

**`CortexMonster` de DeluxeLib.**

> **Corregido durante la implementación.** Este spec decía `SMOPAnimal`, y se escribió sin conocer
> `CortexMonster` — la clase base que DeluxeLib ya tiene para mobs hostiles gobernados por Cortex, y
> sobre la que está construido el Minotauro de Mythos&Mortals. La diferencia es entre heredar el
> cableado de la FSM y escribirlo: construye el `Cortex`, lo instala como goal detrás de un
> `FloatGoal`, sincroniza el estado vivo al cliente, mantiene un `isMoving()` sincronizado sobre una
> histéresis de movimiento, y reenvía daño, muerte, cambio de objetivo y efectos a la máquina.

Lo que se pierde y lo que se gana al no ser `SMOPAnimal`:

| | |
|---|---|
| **Se gana** | Todo el cableado de Cortex, estado sincronizado, `isMoving()` sincronizado, el marcador `Enemy`, y drops de experiencia |
| **Se pierde** | Ser `TamableAnimal` — y con ello los stubs obligatorios de `isFood` y `getBreedOffspring`, que un jefe nunca iba a usar |
| **Sobrevive** | El sueño. `SleepGoal` está atado a `Mob & ISleepingEntity`, no a `SMOPAnimal` — verificado antes de decidir el cambio |
| **Queda redundante** | El estado de rugido de `SMOPAnimal`: con Cortex, `ROAR` es un estado de la FSM y `CortexMonster` ya lo sincroniza |

Dos consecuencias con efecto real:

- **Sin goals de mirada.** El 1.20.1 llevaba `LookAtPlayerGoal` y `RandomLookAroundGoal`; el Minotauro
  no lleva ninguno. Son además la causa del pivote reportado en juego: parado, el control de rotación
  de cuerpo sigue a la **cabeza**, y esos dos goals no paran de moverla.
- **La regla de spawn cambia.** `Animal::checkAnimalSpawnRules` deja de aplicar. Se usa
  `Mob::checkMobSpawnRules`, la neutral, y no la de `Monster`, que exigiría oscuridad y cambiaría la
  decisión de llanura y desierto sin condición de luz.

El género queda fuera igual, y por la misma razón que ya estaba escrita: hay **una sola textura**
(`gt.png`) y `GTRenderer` nunca consulta el sexo — no hay `isMale()` en ninguna parte de su ruta de
render. Era herencia vestigial arrastrada de la base, sin nada que la use.

El **ciclo de sueño de 26.1** (`ISleepingEntity`, `SleepPhase`, `SleepGoal`) se implementa sobre esta
base en el módulo 6, en lugar del `SleepCycleController` ad-hoc del legacy con sus 20 ticks de
transición. El `MobAnimator` lo aporta la entidad implementando `Animatable`, como hace el Minotauro.

## La decisión que define el port: Cortex

El GT es el mob que `PORT_ANALYSIS` señala como candidato perfecto para la FSM de combate de
DeluxeLib, y es el primero de SMOP que la usa. Los otros cinco van con
`AnimatableMeleeAttackGoal`, que resuelve bien **un** ataque; el GT tiene cuatro más un rugido.

Estados: `WANDER · CHASE · BITE · HORN_SWING · CLAW_SWING · STOMP · ROAR`.

Montado con `Cortex.builder()` en el `buildCortex()` que `CortexMonster` exige; la base lo instala
como `CortexGoal` detrás de un `FloatGoal`. Cualquier goal adicional iría en `registerExtraGoals()`,
y hoy no hay ninguno: sin goals de mirada, y el deambular es un estado de la FSM.

| Pieza | Qué la cubre |
|---|---|
| Deambular en reposo | `WanderBehavior` |
| Cerrar distancia | `ChaseTargetBehavior` |
| **Elegir qué ataque toca** | **`AttackSelector` propio** — aquí viven rangos, cooldowns y pesos |
| Cada uno de los 4 ataques | `AnimatedMeleeBehavior` |
| El rugido | `TimedAnimationBehavior`, disparado por una `GlobalRule` al fijar objetivo |
| A quién ataca | `Targeting`: jugadores + `HurtByAttackerTargeting` |

`AttackSelector` es un `@FunctionalInterface` que devuelve el estado al que saltar o `null` si ninguno
es viable. Es el reemplazo directo de `GTAttackGoal` + `GTAttackController`, y concentra en un sitio
lo que el legacy tenía repartido entre un goal, un controlador y un enum.

**El sueño se queda como goal, no como estado de Cortex.** Uniformidad con los otros cinco mobs pesa
más que tenerlo todo en la FSM. Va en `registerExtraGoals()`, y el `CortexGoal` se cierra mientras
`isInSleepCycle()`.

## Los cuatro ataques

Números del legacy. **Se verifican contra los clips reales antes de escribirlos**, no se trasladan.

| Ataque | Duración | Frames de daño | Daño | Knockback Z / Y |
|---|---|---|---|---|
| `BITE` | 17 t | 8 | 18 | 0.35 / 0.05 |
| `HORN_SWING` | 19 t | 10 | 20 | 0.90 / 0.10 |
| `CLAW_SWING` | 32 t | 10 | 18 | 0.60 / 0.30 |
| `STOMP` | 67 t | 14, 26, 46 | 26 | 0.10 / 0.10 |

El daño va en **`HitWindow` + `AttackShape` atado al clip**, como en los otros cuatro mobs, no en el
goal. Cada ataque necesita su propia instancia de `HitWindow`: cada una guarda su `hitThisSwing` y su
`lastSweepAngle`, y compartirlas entre dos clips es lo que el Kriftognathus documenta como
inservible.

Las formas salen de los volúmenes del legacy: los tres primeros son frontales (4 × 2 × 5), el STOMP
es radial y mucho más ancho (10 × 2). Se usa `box3d` y no `box` — el javadoc de `AttackShape` avisa de
que `box` ignora el eje Y, y en un bicho de 6,2 bloques de alto eso significa pisotear a alguien que
está dos bloques por encima.

## El STOMP

Tres frames de daño, más los efectos que el legacy mandaba por red a mano:

- `StompDustFXPacket` → **`ParticleFx`** de DeluxeLib.
- `ShakeCameraPacket` + `CameraUtil` → **`ScreenShake`** / `ShakeProfile`, que además usa ruido fBm en
  vez de jitter.

Dos de los cuatro paquetes del legacy desaparecen así. De los otros dos, `StoCSyncFlying` ya se
eliminó en la Fase 1 y `RiderActionPacket` sobrevive rediseñado.

## El rugido y la barra

El rugido **no necesita clase nueva**: es un estado de la FSM, que `CortexMonster` ya sincroniza al
cliente, y el sonido **`gt_roar` está registrado desde la Fase 1 y sin gastar**. Falta solo quién lo dispare, y eso es la `GlobalRule` de
arriba. El legacy usaba `RoarOnTargetGoal(this, 100, true)` — 100 ticks, y ese 100 es el
`getRoarDuration()` que la entidad ya sabe responder.

La barra de jefe es un `ServerBossEvent` rojo. El mod ya tiene el patrón: `RiderAbility` lo usa para
los cooldowns del Hell Hippo, incluida la parte fiddly de que aparezca, siga al jugador correcto y
desaparezca cuando toca.

**Desviación deliberada:** el legacy no tenía barra. 300 puntos de vida sin barra son un saco de
golpes sin lectura, y es la señal de que esto es un jefe y no fauna grande.

## La cabeza decorativa

`gt_head` sale del sistema **`Statue`** de DeluxeLib, que ya trae `StatueBlock`,
`StatueBlockEntity`, `StatueRenderer`, `StatueItemRenderer`, `StatueItems` y `StatueRegistry`. El
legacy tenía cuatro clases propias para esto (`HeadBlock`, `HeadBlockEntity`, `HeadBlockRenderer`,
`GrandTyrantHeadModel`); de las cuatro solo se porta el modelo.

Lo que aportamos es un `StatueConfig` —nombre, capa de modelo, textura, pose de reposo, transformas
de item por contexto y la línea base x/y/z/escala— registrado en `StatueRegistry`. Las poses no se
adivinan: **`StatueTuner` las afina en vivo y las imprime listas para pegar** sobre la llamada al
constructor.

Es también el segundo item que falta (`gt_head` como `BlockItem`), y el `Statue` le da el modelo 3D
en mano sin tocar el sistema de modelos especiales.

## Spawn y botín

Llanura y desierto, peso 5, cantidad 1, **`MobCategory.CREATURE`** — el legacy tal cual, por decisión
explícita. Tamaño 3,2 × 6,2. `maxUpStep` 2,5. Vía datagen con `DeluxeBiomeSpawns`, como el resto.

**Riesgo conocido, a verificar con datos, no a discutir ahora.** La instrumentación que se escribió
para el Niras (`SMOPSpawnDebug`) midió que `CREATURE` no está lleno: está lleno por un factor de tres
a ocho y no se recupera. Muestreado en cuatro sitios distintos de un mundo nuevo, la cuenta iba de 27
a 79 contra un tope de 10, y el 100% de 4.335 intentos murió en esa puerta. La causa es que
`NaturalSpawner#createState` cuenta `level.getAllEntities()` del nivel entero, así que cada vaca y
cada oveja de cualquier chunk cargado gasta el mismo presupuesto de diez — y como son `Animal`, nunca
despawnean.

La consecuencia esperable es que el GT no aparezca nunca en survival. **Se comprueba con
`/smop debug spawn` una vez esté vivo.** Si se confirma, cambiar la categoría a `MONSTER` es una línea
en `SMOPEntities`, y el spec no pretende decidirlo por adelantado.

Botín: `gt_head` garantizado, por tabla generada con `DeluxeEntityLootProvider`, no por
`dropCustomDeathLoot` como el legacy.

## Hitboxes multiparte: van las últimas, y en DeluxeLib

**Verificado hoy: DeluxeLib sigue sin tener nada de `PartEntity`.** No ha cambiado desde que el spec
del Niras lo comprobó.

Las 7 partes del GT (`front`, `neck`, `head`, `back`, `tail1..3`) se posicionan en el legacy por
trigonometría en `aiStep()`, a distancias de 2,5 a 7,3 bloques del centro. El Niras tiene 2 y las
aplazó explícitamente "a la fase del GT", con la condición de que **cuando se hagan van en DeluxeLib,
no en SMOP**, porque las comparten dos mobs.

Van en la **última** fase, por la misma razón por la que el Niras las aplazó: es la pieza sin
precedente en la librería y no debe bloquear a un jefe que por lo demás es abordable. Cuando aterrice,
retrofitea a los dos.

Hasta entonces el GT sale con un solo AABB, con la consecuencia asumida de que golpear la cola cuenta
igual que golpear la cabeza.

## Fases

Ordenadas para que parar en cualquier corte deje algo publicable.

### a · Esqueleto

Entidad sobre `SMOPAnimal`, modelo, renderer (translúcido: el legacy usa
`RenderType.entityTranslucent`), registro, atributos, spawn en biomas, tabla de botín, barra de jefe.

Los clips portados con los canales huérfanos podados — **26.1 lanza excepción si un hueso del clip no
existe en el rig**, mientras 1.20.1 lo saltaba en silencio. Las duraciones se leen del `withLength`,
no se estiman.

*Verificación:* aparece, camina, ruge, se muere, y ninguna animación revienta.

### b · Cortex y los cuatro ataques

La FSM completa: `WanderBehavior`, `ChaseTargetBehavior`, el `AttackSelector` propio, los cuatro
`AnimatedMeleeBehavior` con sus `HitWindow`, y el `Targeting`.

*Verificación:* persigue, encadena los cuatro ataques, y el daño cae en el frame en que la animación
conecta — comprobado con `/deluxelib debug hitboxes` puesto, no a ojo.

### c · Efectos y sueño

Los tres frames del STOMP con `ParticleFx` y `ScreenShake`. El ciclo de sueño de 26.1.

*Verificación:* el pisotón levanta polvo y sacude la cámara; duerme y despierta.

### d · La cabeza

`StatueConfig` + `StatueRegistry`, modelo portado, poses afinadas con `StatueTuner`.

*Verificación:* cae al morir, se coloca, se ve en mano y en el suelo.

### e · Multiparte en DeluxeLib

Las 7 partes del GT y, de vuelta, las 2 del Niras.

*Verificación:* golpear la cola no cuenta como golpear la cabeza, en los dos mobs.

## Desviaciones deliberadas

- **Barra de jefe**, que el legacy no tenía.
- **Sin género**, que el legacy tenía sin usar.
- **Sueño con el sistema de 26.1**, no con el `SleepCycleController` ad-hoc.
- **La cabeza cae por tabla de botín**, no por `dropCustomDeathLoot`.
- **Cortex en vez de goals**, siendo el primer mob del mod que lo usa.

## Riesgos

| Riesgo | Por qué | Mitigación |
|---|---|---|
| **5.428 líneas de animación** | 26.1 lanza excepción por hueso inexistente; 1.20.1 no | Podar canales huérfanos en la fase a, antes de nada más |
| **Cortex se estrena aquí** | Ningún mob de SMOP lo usa, y el GT es el más complejo | Fase b aislada; si Cortex no cuadra, `AnimatableMeleeAttackGoal` sigue siendo la salida |
| **Multiparte sin precedente** | No existe en la librería | Va la última, con un AABB único hasta entonces |
| **`CREATURE` saturado** | Medido: 27–79 contra un tope de 10 | `/smop debug spawn` con el mob vivo; `MONSTER` es una línea |

## Fuera de alcance

- Poise/stagger, que DeluxeLib ofrece y encajaría en un jefe. No entra: el GT ya estrena Cortex y
  multiparte, y tres sistemas nuevos en un mob es demasiada superficie a la vez.
- Estructura del nido de Tangoftero, que sigue sin portar y no es de este mob.
- Las fases 1d, 2 y 3 del Nirasmosaurus.

## El corte mínimo

**a + b + c** dan un jefe completo, con sus cuatro ataques, su rugido, su pisotón y su barra. **d** es
el trofeo que hace que matarlo deje algo. **e** es deuda de librería que arrastran dos mobs y que se
puede pagar cuando convenga.
