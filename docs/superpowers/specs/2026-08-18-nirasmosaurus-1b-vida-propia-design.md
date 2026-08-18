# Nirasmosaurus 1b — Vida propia

Sub-fase del [port del Nirasmosaurus](2026-08-16-nirasmosaurus-port-design.md). La 1a dejó un reptil
que nada, camina, spawnea, dropea y se muere con animación. Esta le da un día y una descendencia.

## Qué queda realmente por hacer

El spec del port lista cuatro cosas para 1b — sueño, reproducción con cría, gesto de reposo y
animación de muerte. Dos de las cuatro se caen antes de empezar:

- **La muerte ya está cerrada en 1a.** `land_death` y `water_death` se registraron con
  `registerDeath` al montar el esqueleto, porque la elección de clip por medio era parte de la
  locomoción y no tenía sentido dejarla a medias. No hay nada que hacer aquí.
- **El gesto de reposo se descarta.** Decisión tomada al diseñar esta fase: el repertorio libre eran
  `roar`, `goofy` y `waiting`, y ninguno se gasta ahora. `waiting` huele a pose de montura parada y
  chocaría con la fase 2; el tono de `goofy` no lo ha visto nadie en juego todavía; y un rugido
  ambiental es una decisión de carácter que se toma mejor con el animal ya vivo delante. El
  `IdleAnimationGoal` no se registra, y los tres clips siguen ahí para cuando haya criterio.

Quedan **sueño** y **reproducción**.

## El sueño: seis clips con nombre propio

El rig trae **dos juegos completos** de clips de sueño, uno por medio, y el animal usa los dos: se
duerme donde le pilla la noche, en el lecho o varado en la orilla. Eso no es negociable —son doce
clips autorados, la mitad de ellos de cría— pero `SleepPhase` identifica cada fase por **un solo
nombre de clip**, así que los dos juegos no caben tal cual.

| Fase | Tierra | Agua |
|---|---|---|
| `PREPARING_SLEEP` | 3.55 s · 71 ticks | 4.0 s · 80 ticks |
| `SLEEPING` | 2.0 s en bucle | 2.0 s en bucle |
| `AWAKENING` | 2.3 s · 46 ticks | 3.0 s · 60 ticks |

Adulto y cría miden lo mismo en las seis casillas, así que la variación por edad se resuelve con el
`AnimSource` de dos vías que ya usa el resto de la clase.

### Lo que parecía la respuesta y no lo es

La primera idea fue **un clip por fase con un `AnimSource` de cuatro vías** —medio × edad— y un
`sleepPhaseDuration` que devolviera la duración del medio real. Es más corto y es incorrecto, por dos
razones que solo aparecen leyendo DeluxeLib:

- `AnimSource.definition()` **llama al supplier en cada frame de render**, no una vez al arrancar. No
  es una elección: es una consulta continua. Un animal que cruzara la superficie dormido cambiaría de
  pose a mitad de clip.
- `BaseAnimation` guarda **una** `durationTicks`, fijada al registrar, y el cliente la usa tal cual:
  `AnimationSyncClientboundPacket` hace `setTicksRemaining(base.getDuration())` con la copia local. Un
  único registro no puede medir 71 ticks en tierra y 80 en agua, y `setDurationTicks` desde el
  servidor no viaja a ninguna parte porque cada lado tiene su propia instancia.

### Lo que se hace

Seis clips registrados con nombre propio: `preparing_sleep`, `sleep` y `awakening` para tierra, y
`preparing_sleep_water`, `sleep_water` y `awakening_water` para agua. Cada uno con su duración real,
que es la del `withLength` de su clip. Dos overrides en la entidad:

- **`onSleepPhaseBegin(phase)`** elige la variante del medio y la lanza con `playIfRegistered`. Los
  uno-shot los arranca el servidor y el cliente los recibe **por nombre**, así que ahí no hay nada que
  sincronizar: el cliente reproduce exactamente el clip que el servidor eligió.
- **`sleepPhaseDuration(phase)`** devuelve la duración del medio real en vez de la del clip homónimo.
  La implementación de `SMOPAnimal` mide `phase.clipName()`, que aquí solo nombra la variante de
  tierra.

El medio se **fija al empezar el ciclo** y se guarda en un booleano sincronizado, `SLEEP_IN_WATER`.
Que esté sincronizado tiene un único motivo, y conviene dejarlo escrito porque no es evidente: `sleep`
es `REPEATING`, y los clips en bucle **los auto-arranca el animador en los dos lados** desde su play
condition. Si la condición leyera `isInWater()` directamente, un animal dormido justo en la orilla
podría estar en el clip de agua en un lado y en el de tierra en el otro. Con el latch, la condición da
lo mismo en cliente y servidor porque es el mismo dato.

Nada de esto toca `SleepPhase`, `SleepGoal` ni `SMOPAnimal`.

### Dos ajustes que arrastra

- `canPlayLocomotion()` pasa a exigir `!isInSleepCycle()`. Sin eso el idle y el nado siguen
  compitiendo por el frame con un animal que está tumbado.
- El `SleepGoal` entra en **prioridad 1**, que hoy ocupa `SwimWanderGoal`. El goal de sueño retiene
  MOVE, LOOK y JUMP, y esa retención es justo lo que preempta al vagabundeo; registrado por debajo, el
  animal nadaría dormido.

### Dónde duerme

No hace falta código para llevarlo al fondo. `SleepGoal` para la navegación, `travel()` recorta 0.005
por tick contra un rozamiento de 0.9, y eso se estabiliza en **un bloque por segundo**: el animal se
duerme donde esté y desciende hasta apoyarse en el lecho.

Lo que sí hizo falta es **apagar el tilt durante el ciclo**. Un animal dormido no está quieto: baja a
velocidad terminal y sin componente horizontal, y el ángulo de trayectoria de eso es noventa grados
hacia abajo — `atan2` lo devuelve, el clamp lo recorta a treinta, y el bicho pasaría todo el descenso
con el morro clavado mientras reproduce una pose de reposo autorada horizontal. El sueño se suma a la
muerte y a la tierra firme en la misma puerta: cuando hay un clip autorado mandando, el tilt se
nivela y se aparta.

### Qué lo despierta

Nada específico. No implementa `ISleepAwareness` ni `ISleepThreatEvaluator`, así que se queda con el
comportamiento por defecto —un jugador cerca lo despierta—, que es el mismo que tiene hoy el Hell
Hippo. `getInterruptingEntityTypes()` ya devuelve el conjunto vacío desde 1a y así se queda: un animal
que duerme en mar abierto no tiene nada en particular que temer.

## La reproducción: huevo, no cría

El spec del port decía "reproducción con cría" y eso era una imprecisión suya. El 1.20.1 tenía un
bloque `niras_egg`, y **los assets ya están en el repo desde el principio**: el blockstate con su
propiedad `hatch` de 0 a 2, tres modelos, nueve texturas y el modelo de item. Poner cría directa
habría dejado todo eso muerto.

Hay además una clase esperando: `EggBlock` —huevo único, solo `HATCH`, dimensiones parametrizadas—
cuyo javadoc dice literalmente *"the Nirasmosaurus nest"*. Se escribió para esto y no la usa nadie. El
Tango y el Krifto usan `SmallEggsBlock` (nidada de 1-4, con propiedad `eggs`), el salmón
`RoeEggsBlock`; el blockstate del Niras no tiene contador de huevos porque es **un** huevo grande.

### El bloque

`EggBlock(SMOPEntities.NIRASMOSAURUS, 600, 8, 10, ofFullCopy(TURTLE_EGG))`.

- **8 × 10 px** salen del propio modelo: `niras_egg_template.json` va de `[4,0,4]` a `[12,10,12]`.
- **600 ticks por etapa**, tres etapas, ~90 s en total. El doble que el Tango y el Krifto, que van a
  300. No es un número heredado —el legacy no dejaba uno legible— sino una proporción: es el animal
  más grande y más raro de los que ponen huevos, y su nido es de uno solo, así que una espera corta lo
  abarataría.

Alrededor del bloque: `registerSimpleBlockItem`, una línea en el bloque de huevos de la pestaña
creativa, y el nombre lo genera solo el `DeluxeLangProvider`.

**Sin tabla de botín**, deliberadamente y por consistencia: ninguno de los otros tres bloques de
huevo tiene una —`src/generated` no contiene un solo `loot_table/blocks`— porque un huevo roto no
deja huevo, igual que el de tortuga en vanilla. Inventarle una aquí rompería la simetría y haría
recogible un nido que debe ser un compromiso.

### El tag, y un bug que aparece de paso

`smop:niras_egg` tiene que entrar en `data/smop/tags/block/egg_blocks.json`, que es lo que
`ProtectEggBaseGoal` consulta para saber si el bloque que vigila sigue siendo un huevo.

Al abrirlo aparece que **ese fichero solo contiene `tangoftero_egg`**. `krifto_egg` y
`salmon_roe_eggs` no están, así que sus goals de guardia llevan desde su fase mirando un bloque que
para ellos no es un huevo. Es un bug preexistente y ajeno a esta fase; se levanta aparte y no se
arregla aquí, para que 1b no cargue con la verificación de otros dos mobs.

### Los goals

- **`GenericBreedGoal`**, no el `BreedGoal` de vanilla que usa el Hell Hippo: ese existe para los
  mamíferos y termina en un bebé. Este vuelca `setHasEgg(true)` en los que no lo son, y el Niras no lo
  es —`isMammal` es `false` por defecto y no se toca.
- **`EggGoalRegistry.registerWithOwnGoal`**: anida en solitario, vigila el huevo que puso él y no el
  que encuentre. Radio de permanencia 6 y de defensa 8, contra el 4 y 6 del salmón y el Krifto,
  porque el animal es mucho mayor y el 4 lo dejaría prácticamente encima del nido. El selector de
  enemigos no matchea a nadie, igual que el `NO_PREY` del salmón — mientras la guardia no ataque, un
  selector con contenido sería una lista que nadie lee.
- **Se aparea y anida en tierra, como una tortuga marina.** Decisión tomada viendo el animal en juego,
  y es la que da forma al resto: `canMate` exige que ninguno de los dos esté en el agua, y la puesta
  usa la ruta **terrestre** de `tryLayEgg` —aire sobre bloque sólido— en vez de la acuática que trae
  el base. Eso se abre con `nestsAshore()`, un hook nuevo en `SMOPWaterAnimal`: ser acuático y
  desovar bajo el agua resultaron ser dos hechos distintos, y el base los tenía fundidos en uno
  porque el salmón hace las dos cosas.
- **Sobre arena o grava**, los mismos dos bloques que `checkNirasSpawnRules` acepta como orilla. Sin
  esa restricción anidaría sobre cualquier piedra o hierba donde le pillara el temporizador, y los
  nidos acabarían tierra adentro en un animal cuya premisa entera es la línea de costa.
- **La distancia de apareamiento de vanilla es inalcanzable para un animal grande.** `BreedGoal` exige
  `distanceToSqr < 9.0`, es decir **tres bloques de centro a centro**. Para una vaca, que mide 0.9 de
  ancho, eso es un abrazo con dos metros de sobra. Dos Nirasmosaurios miden **3.0 cada uno**: sus
  hitboxes chocan exactamente a tres bloques, así que la pareja se queda clavada en el umbral y solo
  lo cruza por suerte, en el tick en que el ángulo entre ambos recorta unos centímetros. Eso era, al
  pie de la letra, "se aparean a veces sí y a veces no estando pegados". Ahora la distancia se mide
  contra los cuerpos —medio ancho de cada uno más un metro, con el 3.0 de vanilla como suelo— y el
  intento **se repite** mientras sigan enamorados, en vez de existir un único tick en el que puede
  ocurrir: vanilla llega a su tick de apareamiento una sola vez y si en ese instante están un palmo
  lejos, el cortejo entero se desperdicia en silencio.
- **El huevo caía en el macho la mitad de las veces.** `GenericBreedGoal` lo marcaba en el animal cuyo
  goal llegaba antes a `breed()`, que es una moneda al aire: los dos miembros de la pareja corren su
  propio `BreedGoal` y solo cuenta el primero, porque la rutina de vanilla resetea el amor de ambos y
  el segundo se corta antes de disparar. En una especie cuyo sexo se ve en la textura eso no es un
  detalle interno: es un macho yéndose a anidar mientras la hembra se queda mirando, y si el jugador
  mata al macho —cosa perfectamente normal— la puesta muere con él. Ahora, cuando ambos son
  `Gendered`, el huevo va a la hembra. **Afecta también al Kriftognathus y al salmón**, que tienen el
  mismo problema desde sus fases; el Tangoftero no, porque no tiene sexos.
- **"A veces sí y a veces no" tenía dos causas, y ninguna era la playa.** Una: *aire con arena debajo*
  describe muchos bloques que no son una playa —bolsas dentro de la duna, el hueco bajo un saliente,
  el techo de una cueva marina—, todos ellos sitio legal para un huevo e imposible para un animal de
  3 × 1.6. El sorteo aleatorio caía en uno de esos cada tantas veces. Se filtran con dos condiciones
  que describen lo que de verdad se quiere: **dos bloques de aire** y **cielo abierto**. Dos: el radio
  de llegada era fijo y pequeño, y en un cuerpo de tres bloques de ancho el navegador termina el
  camino dejando el bloque de los pies a menudo más lejos que eso, así que el animal nunca constaba
  como llegado y la puesta se quedaba bloqueada indefinidamente. Ahora se dimensiona con el ancho del
  cuerpo.
- **El viaje al nido es obligatorio.** Poner el huevo bajo sus propios pies justo después de aparearse
  se lee como un fallo, no como anidar. El sitio elegido está a **8 bloques como mínimo**, y se toma
  al azar entre los candidatos para que dos puestas no parezcan el mismo paseo guionizado; solo si no
  hay ninguno a esa distancia se acepta el más cercano, para que una hembra en un islote pequeño
  ponga igualmente. La distancia sería decorativa sin la otra mitad: `isSettledToLay()` exige haber
  **llegado**, porque si no la cuenta atrás de la puesta soltaría el huevo en cuanto el animal
  cruzara arena legal, dos pasos después de salir.
- **`SeekNestSiteGoal`** lleva a la hembra grávida a un sitio donde pueda poner **y la retiene ahí**.
  La retención es la mitad que faltaba, y es la razón de que el goal reserve MOVE: `GenericLayEggGoal`
  espera cuarenta ticks y entonces pide poner *donde el animal esté*, que es exactamente correcto
  para el Tango y el Krifto —cualquier suelo sólido les vale, así que la cuenta atrás siempre termina
  en sitio legal— y deja de serlo en cuanto una especie es exigente con el terreno. Sin retención, la
  hembra se iba de la playa o al mar mientras corría la cuenta.

  Los dos goals cooperan en vez de competir porque el de puesta **no reserva ninguna bandera**: éste
  posee dónde está el animal, aquél posee el huevo. Y repite el camino cada segundo en vez de
  trazarlo una vez, porque el navegador de agua no puede pathear a tierra firme: deja a la hembra en
  la orilla y el navegador terrestre coge el segundo tramo.

### La guardia no ataca todavía

`attackOnApproach = false`, `attackOnBreak = false`, reacción `IGNORE`.

No es una decisión de temperamento, es de secuencia: el goal de ataque no llega hasta 1c, y marcar un
objetivo ahora daría un animal que fija al intruso y no hace absolutamente nada con ello — peor que no
reaccionar, porque parece roto. En 1c, con el mordisco ya montado, ambos pasan a `true` y la madre
defiende el nido de verdad.

## La cría

`SMOPFollowParentGoal(this, 1.1D, 6.0D)`.

Seis bloques, no los tres de vanilla. Es el mismo problema que el Hell Hippo resolvió con cinco: en un
cuerpo de este largo, tres bloques de distancia de seguimiento sitúan a la cría **dentro** de la
madre. Seis, porque el Niras es más largo que el hippo.

## Orden de goals resultante

| Prioridad | Goal | Nota |
|---|---|---|
| 1 | `SleepGoal` | Retiene MOVE/LOOK/JUMP; tiene que ir por encima de todo lo que mueva |
| 2 | `GenericBreedGoal` | |
| 3 | `SMOPFollowParentGoal` | |
| 4 | `ProtectOwnEggGoal` | Vía `EggGoalRegistry`, base 4 |
| 5 | `GenericLayEggGoal` | La registra el mismo `EggGoalRegistry`, en base+1 |
| 6 | `SwimWanderGoal` | Baja desde 1 |
| 7 | `SMOPRandomStrollGoal` | Baja desde 2 |
| 8-9 | Los dos goals de mirada | Sin cambios |

## Verificación

1. De noche se duerme, en el fondo y también varado en la orilla, con el clip del medio correcto y sin
   que el cliente muestre uno distinto del servidor.
2. Se despierta al amanecer, y antes si un jugador se le acerca.
3. Con pescado cocinado en mano, dos adultos **en la playa** se aparean — y en el agua no, por mucho
   pescado que se les dé.
4. La madre pone un `niras_egg` sobre la arena o la grava; el huevo pasa por sus tres estados y
   eclosiona una cría.
5. La cría sigue a la madre sin meterse dentro de ella.

## Lo que apareció al implementarlo

Tres cosas que el diseño no había previsto y que se resolvieron sobre la marcha:

- **El huevo tenía que ser acuático de verdad.** `ofFullCopy(TURTLE_EGG)` a secas habría reemplazado
  la fuente de agua en la que se pone y dejado **una burbuja de aire de un bloque en el fondo del
  mar**. `RoeEggsBlock` ya resolvía esto declarándose agua en `getFluidState`, con un comentario que
  dice exactamente eso; `EggBlock` no lo hacía. Ahora recibe un flag `aquatic` que le da ese
  `getFluidState`, exige fuente de agua en `canSurvive` y rechaza colocarse a mano donde no
  sobreviviría. **Consecuencia para el jugador:** el `niras_egg` del inventario solo se puede poner
  bajo el agua. No se hace con una propiedad `WATERLOGGED` porque el blockstate autorado solo
  enumera `hatch`, y añadir una segunda propiedad dejaría dos tercios de los estados sin modelo.
- **`getBreedOffspring` decía lo contrario de lo que hace el animal.** Venía de 1a devolviendo una
  cría viva. Con `GenericBreedGoal` ese método no se llama nunca —el goal pasa un hijo nulo a
  `finalizeSpawnChildFromBreeding` a propósito— así que era código muerto que afirmaba que el bicho
  pare. Se elimina y se hereda el `null` de `GenderedSMOPAnimal`, que es el contrato de los que ponen
  huevos.
- **Los recién nacidos habrían salido todos machos.** `AbstractEggBlock` crea la cría y la mete en el
  mundo **sin pasar por `finalizeSpawn`**, que es el único sitio donde se sorteaba el sexo. Es el
  mismo bug que 1a arregló para los spawns naturales, por otra puerta. Se resuelve implementando
  `CustomEggBorn` y sorteando el sexo en `onEggBorn`, que es lo que ya hacía el Kriftognathus por
  esta misma razón.

## Lo que apareció al probarlo en juego

- **La pareja cruzaba la playa a toda velocidad.** `MOVEMENT_SPEED` vale 1.0 porque el control de agua
  lo multiplica por su propio 0.01; en tierra no lo divide nadie, así que un modificador de 1.15
  daba cuatro veces y media la velocidad de un paseo. Estaba parcheado escribiendo 0.25 en el único
  goal que solo corría en tierra, y 1b añadió goals que corren en **los dos** medios. La división
  pasa ahora al navegador terrestre —un sitio, `LAND_SPEED_SCALE`— y todos los goals hablan en las
  mismas unidades que los de agua. El paseo vuelve a 1.0 con la misma velocidad efectiva de antes.
- **La hembra no ponía el huevo, y no era el huevo.** `tryLayEgg` del base acuático busca una fuente
  de agua bajo el animal; una pareja alimentada por el jugador se aparea en la orilla, y desde la
  arena esa búsqueda devuelve `null` a la primera. La hembra se quedaba grávida para siempre sin
  nada en pantalla que lo explicara. El Tangoftero y el Krifto nunca lo sufrieron porque usan la
  versión terrestre de `tryLayEgg`.

  El primer arreglo fue devolverla al agua. **Sustituido por la decisión de anidar en tierra**, que
  resuelve lo mismo por el lado correcto: si el animal se aparea y pone donde ya está, no hay viaje
  que orquestar. Lo que queda del primer intento es `GoAshoreToLayGoal`, ahora en sentido inverso.
- **El huevo no se podía colocar.** Consecuencia directa del apaño acuático de la sección anterior.
  Sustituido por waterlogging de verdad: `EggBlock` implementa `SimpleWaterloggedBlock`, el
  blockstate pasa a enumerar `hatch` × `waterlogged` reusando los mismos tres modelos, y `tryLayEgg`
  marca el bloque como anegado al ponerlo. Se coloca en tierra y bajo el agua, y sigue sin abrir
  burbuja en el lecho.
- **La muerte en tierra parecía la de agua, y no era la elección: era vanilla.**
  `LivingEntityRenderer#setupRotations` multiplica la pose por `Axis.ZP.rotationDegrees(fall *
  getFlipDegrees())`, con 90° por defecto, y `NirasRenderer` no lo anulaba. Los dos clips de muerte
  giran el cuerpo sobre **ese mismo eje** —el de tierra 22.5°, el de agua 110°—, así que con el
  volteo de vanilla vivo se componen: 22.5 + 90 = ~112, a dos grados del clip de agua. El clip
  correcto se estaba reproduciendo todo el tiempo. `getFlipDegrees() → 0`, igual que el Hell Hippo y
  el salmón, que ya lo llevaban por lo mismo.
- **`isInWater()` es la pregunta equivocada para un cuerpo de tres bloques.** Es verdadera si
  **cualquier** parte de la caja toca agua, y un reptil varado en la arena casi siempre tiene la cola
  en el mar — así que un animal que moría claramente en la playa reproducía la muerte de agua, la que
  vuelca el cuerpo 110° panza arriba. Y no era solo la muerte: `idle`, `walk` y `sprint` usaban el
  mismo test, o sea que andar por la orilla reproducía el ciclo de **nado**. Las siete condiciones
  pasan a preguntar si el animal **está nadando**, que es lo que de verdad querían saber:
  `isUnderWater() || (isInWater() && !onGround())`. Eso separa los cuatro casos reales — cola mojada
  en la arena, apoyado en el lecho, nadando a cualquier profundidad, y tierra adentro.
- **El salto al salir del despertar lo causaba `canPlayLocomotion()`.** Excluir el ciclo de sueño ahí
  —que parecía obvio: un animal tumbado no debe tener el idle compitiendo por el frame— mata al idle
  que tiene que estar corriendo **por debajo**. Y lo que asoma al terminar un clip `PLAY_ONCE` no es
  su último fotograma sino la **pose bind**, así que el despertar acababa en un salto. La exclusión
  es por prioridad, no por play condition: los clips de sueño van en 1 y la locomoción en 3. El Hell
  Hippo y el Tangoftero lo dicen en este mismo método; ignorarlo costó dos rondas de pruebas.
- **La entrada al sueño en tierra se congelaba 0.4 s.** `lsleep_preparing` declara 3.55 s y deja de
  tener keyframes en 3.15, y una fase dura exactamente lo que su clip registrado. Registrado a 3.15.
  El par de agua no tiene ese hueco —4.0 declarado, 4.0 animado— y va como está autorado.

## Herramienta

`/smop debug nest` cubre la cadena entera —enamoramiento, emparejamiento, huevo, viaje, llegada,
puesta— y existe aparte de `/smop debug swim` porque son cadenas distintas que corren sobre los
mismos animales. Tiene dos formas y la segunda es la que importa:

- **`/smop debug nest`** fotografía el estado de los que tengas cerca. Los veredictos los dan los
  propios mobs: `canMate` se llama sobre la pareja real y si un bloque sirve de nido se le pregunta a
  la especie, así que la herramienta no puede divergir de las reglas.
- **`/smop debug nest watch [segundos]`** sigue la cadena en vivo y anuncia **solo las transiciones**:
  se enamoró, encontró pareja, recibió el huevo, eligió nido, llegó, puso. Esto no es comodidad: cada
  uno de esos momentos dura un tick o dos, y una foto tomada a mano cae casi siempre en el hueco
  entre dos — que es justo la parte que parece que no pasa nada.

## Fuera de alcance

- El gesto de reposo (`IdleAnimationGoal`), aplazado sin fecha por falta de criterio, no por coste.
- Los huevos que faltan en `egg_blocks`, que son del Krifto y del salmón.
- Todo 1c en adelante: mordisco, agarre, sacudida, giro, señuelo y montura.
