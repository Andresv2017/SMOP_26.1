# Nirasmosaurus 1c — Combate básico

Sub-fase del [port del Nirasmosaurus](2026-08-16-nirasmosaurus-port-design.md), después de
[1b · Vida propia](2026-08-18-nirasmosaurus-1b-vida-propia-design.md). La 1a dejó un reptil que nada,
camina y se muere; la 1b le dio un día y una descendencia. Esta le da dientes.

El spec del port resume la fase en una línea: *los dos mordiscos simples, en agua y en tierra, por
`HitWindow` sobre `AnimatableMeleeAttackGoal`*. Lo que esa línea no dice, y es la mitad del trabajo,
es **a quién muerde**. El legacy tiene un `PREY_SELECTOR` que solo matchea caballos —un resto de
pruebas, no un diseño— así que la lista de presas no se porta: se decide aquí.

**Caza por iniciativa propia.** No solo se defiende. La alternativa —dejar la caza entera para 1d,
donde vive el agarre— cerraba la fase con un animal que sigue sin hacer nada visible salvo que le
pegues o le toques el nido, y con el clip `water_sprint` muerto por tercera fase consecutiva. La caza
sin agarre ya es un mordisco perfectamente legible; 1d no añade la caza, añade **qué hace con lo que
atrapa**.

## La ventana de daño: una sola, para los cuatro clips

Los cuatro mordiscos —`lbite`, `wbite`, `l_bite`, `w_bite`— están autorados al mismo compás.
`gLowerjaw` abre a 32.5° en 0.1 s, aguanta hasta 0.4 y cierra en 0.45. Idéntico en los cuatro, adulto
y cría, tierra y agua.

**La ventana es ticks 8–9, y sirve para todos.** El `damageFrames = {9}` del legacy resulta ser
correcto, pero eso se sabe ahora por haberlo leído contra los keyframes, no por haberlo heredado —
que es exactamente lo que el spec del port pedía hacer con cada número que viniera de 1.20.1.

Los clips se registran con el helper `clip(nombre, adulto, cría, …)`, así que hay **dos**
animaciones, `bite` y `water_bite`, cada una con su `AnimSource` de dos vías por edad. Por tanto
**dos `HitWindow`, no cuatro**, y obligatoriamente instancias separadas: su estado `hitThisSwing` /
`lastSweepAngle` no puede compartirse entre clips, como el Kriftognathus dejó escrito al montar su
segundo mordisco.

Duración registrada **1.2 s completa**, `PLAY_ONCE`, prioridad 0. Aquí no hay que recortar nada: los
cuatro clips keyframean hasta el final —el último toca 1.2— así que no existe el hueco de cola que
obligó a registrar `preparing_sleep` a 3.15 en vez de a sus 3.55 declarados, ni el que dejaba al Hell
Hippo «rígido después de morder».

## Las dos formas del golpe, y por qué no pueden ser la misma

**En tierra, `box3d` en espacio de cuerpo.** `AttackShape.box` ignora el eje Y por completo —lo dice
la nota de interfaz de la propia clase: *«a ground mob's swing reaches whatever is in front of it, at
any height»*— y en una playa eso significa morder a un jugador plantado en un saliente dos bloques
por encima. Es literalmente el fallo que el Hell Hippo encontró al meterse en el agua.

**En agua, `box3d` + `aimAlongLook()` + `AttackAnchor.look(...)`**, como el mordisco en vuelo del
Krifto. La analogía no es lo que lo justifica; lo justifica un hecho de este animal:
**`SwimSteerControl` no escribe `xRot` a propósito.** Su javadoc lo documenta como el arreglo del
head jitter — la inclinación de la trayectoria se quedó en un campo privado precisamente para que
`xRot` volviera a significar *dónde mira la cabeza*, que es lo que el rig cree que significa. Y quien
escribe `xRot` es el `LookControl`, apuntando al objetivo. Así que apuntar el mordisco a lo largo del
look apunta a donde el cuello va de verdad.

Con una caja plana, en cambio, un Niras situado bajo un jugador que nada por encima mordería el vacío
indefinidamente, quemando cooldowns: el goal mide el alcance en 3D y dispararía, y la forma
achatada nunca contendría al objetivo.

**El límite conocido:** el rig clampa el cuello a 30° (`lookAt(m -> m.gNeck, 35.0F, 30.0F)`). Pasado
ese ángulo la caja apunta donde dice el hitbox y el modelo ya no acompaña. Es el mismo compromiso que
el Krifto aceptó en vuelo, y se acepta por la misma razón: la alternativa es no poder morder hacia
arriba.

### Los números, y de dónde salen

El morro sobresale mucho más que la caja de colisión. Sumando la cadena del rig —`gNirasmo +15` →
`gNeck −25` → `gHead −14` → el cubo del morro empezando en `−28`, más el `−4` que el propio clip de
mordisco añade a la posición de `gHead`— la punta queda a **56 px = 3.5 bloques** del centro de la
entidad, contra una semianchura de caja de 1.5. Casi dos bloques de hocico fuera del hitbox.

| | Tierra | Agua |
|---|---|---|
| Forma | `box3d(2.0, 1.1, 1.2)` | `box3d(1.6, 1.0, 1.0)` |
| Ancla | `of(2.0, 0.0, 0.9)` | `look(2.4, 0.0, −0.5)` |

El `−0.5` del ancla de agua no es un ajuste fino: `AttackAnchor.look` parte de los **ojos**, y la
altura de ojo por defecto de una entidad de 1.6 es `0.85 × 1.6 = 1.36`, mientras la cabeza del rig
vive a unos 0.8 sobre los pies. Sin corregirlo, el mordisco nace medio bloque por encima de la boca.

Los seis números son **punto de partida a afinar con `/deluxelib debug hitboxes`** mirando el render,
no valores cerrados. El hippo lo dejó escrito en su propia caja: *«a look number: adjust it against
the render»*.

## El goal

Uno solo, `AnimatableMeleeAttackGoal`, eligiendo clip por medio dentro de `onAttack` — el patrón del
Krifto (`animator.getByName(isFlying() ? ANIM_BITE_FLIGHT : "attack")`) pasa a
`isInSwimmingMedium() ? "water_bite" : "bite"`.

El daño no vive en el goal. El goal decide **cuándo** comprometerse; la `HitWindow` barre la caja en
los ticks en que las fauces se cierran. Es la convención de los otros cuatro mobs y la razón de que
el hippo dejara de tener el golpe y la mordida desincronizados.

- **`reach(3.8)`** — el morro llega a 3.5 y la caja de tierra alcanza 4.0 desde el centro. La regla
  que el hippo pagó con dos rondas de pruebas: la puerta que decide si atacar nunca puede ser más
  estrecha que el daño que abre, ni más ancha que la caja que lo aplica.
- **`stopDistance(3.2)`** — deja las fauces justo pasado el objetivo. Conducir la parada con el mismo
  número generoso del alcance aparca al animal un cuerpo entero por detrás de algo que ya podría
  morder.
- **`cooldown(26)`** — el clip dura 24 ticks; dos de respiro entre dentelladas.
- **`attackCondition(t -> !isBaby())` y además `canUse()`/`canContinueToUse()` con la misma
  condición.** No es redundancia: `attackCondition` solo alcanza hasta `checkAndPerformAttack`, o sea
  silencia el mordisco pero no el goal. Una cría con objetivo —y ninguno de los goals de target mira
  la edad— correría la persecución entera hasta la boca y simplemente no mordería, que desde fuera se
  lee como «la cría ataca y está rota», no como «la cría no pelea». El Krifto documenta exactamente
  esto.
- **Velocidad de persecución 1.3, un número para los dos medios.** El legacy pedía 1.2 en tierra y
  1.6 en agua. No se porta esa partición: desde 1b la división por medio vive en el navegador
  terrestre (`LAND_SPEED_SCALE`) y todos los goals hablan en las mismas unidades, y volver a partirlo
  aquí reintroduce el acertijo específico de especie que 1b quitó. Si en juego el agua se queda
  corta, se sube en el navegador, no en el goal.

### Renumeración de goals

El ataque entra en 2 y todo lo de debajo baja uno. **Empates no**: `WrappedGoal#canBeReplacedBy` solo
cede la bandera con un `<` estricto, así que dos goals a la misma prioridad no pueden quitarse
MOVE el uno al otro una vez arrancados — el trampa que 1b documenta a propósito del Krifto.

| Prioridad | Goal | Nota |
|---|---|---|
| 1 | `SleepGoal` | Retiene MOVE/LOOK/JUMP |
| **2** | **`AnimatableMeleeAttackGoal`** | Nuevo |
| 3 | `GenericBreedGoal` | Baja desde 2 |
| 4 | `SeekNestSiteGoal` | Baja desde 3 |
| 5 | `SMOPFollowParentGoal` | Baja desde 4 |
| 6 / 7 | `ProtectOwnEggGoal` / `GenericLayEggGoal` | `EggGoalRegistry`, base 6 |
| 8 | `SwimWanderGoal` | Baja desde 7 |
| 9 | `SMOPRandomStrollGoal` | Baja desde 8 |
| 10 / 11 | Los dos goals de mirada | Bajan desde 9/10 |

## A quién ataca

**Represalia.** `HurtByTargetGoal` con la guarda del hippo: un Niras dormido **no despierta a
pelear** (`!isInSleepCycle()`), porque quien decide cuándo despierta es el `SleepGoal`. Sin
`setAlertOthers()` — el hippo alerta a los suyos porque vive en familia; éste no tiene manada, y esa
decisión ya está tomada en el spec del port.

**Caza.** Dos goals más, ambos colgados de `picksItsOwnFights()`:

- `NearestAttackableTargetGoal<Player>` — sí, ataca al jugador sin provocación. Un depredador de tres
  bloques que te ignora mientras nadas a su lado no da miedo, y el miedo es la mitad de este animal.
  Tiene una consecuencia que se paga en fase 2: el ritual de señuelo exige que el jugador se acerque
  con pescado cocinado en la mano. El legacy resolvió el choque bloqueando la adquisición de objetivo
  mientras el señuelo estaba activo (`isLureActive`); esa puerta se vuelve a necesitar entonces, no
  ahora.
- `NearestAttackableTargetGoal<Mob>` con el selector de presas: **bacalao, pez tropical, pez globo,
  calamar y tortuga**.

**Tipado sobre `Mob`, no sobre `Animal`.** El Hell Hippo tipa el suyo como
`NearestAttackableTargetGoal<Animal>` y ahí funciona porque sus presas son ovejas, cabras y vacas.
Aquí eso dejaría fuera a casi todo el océano: los peces de vanilla, el calamar y el delfín extienden
`WaterAnimal`, no `Animal`. Solo la tortuga es `Animal`. Es la razón de que esta lista no pueda
copiarse de un mob terrestre.

**El delfín queda fuera**, y no por bondad: es lo único ahí abajo que puede pelear de vuelta, y no
interesa que la primera lectura del mob sea «mata delfines». **La tortuga entra** porque es
justamente la escena que 1d quiere: algo con caparazón zarandeado en las fauces. **El ahogado queda
fuera** porque es `Monster`: incluirlo convertiría al Niras en aliado del jugador, que es lo contrario
de lo que es.

**`picksItsOwnFights()` = `!isBaby() && !isInSleepCycle() && !isTame()`.** El `!isTame()` va desde ya
aunque el tameo sea de fase 2: cuesta cero, y evita que un animal domado salga a cazar tortugas por
su cuenta el día que exista. Cuando llegue la silla se le añade `!isSaddled()`, como en el hippo.

**El nido saca los dientes.** `attackOnApproach` y `attackOnBreak` pasan a `true`, y
`NO_NEST_ENEMIES` deja de ser «nadie» para pasar a ser el selector de presas más el jugador. Es
literalmente lo que 1b dejó anotado para esta fase: *«en 1c, con el mordisco ya montado, ambos pasan
a `true` y la madre defiende el nido de verdad»*.

**El filtro de la ventana** excluye a otros Nirasmosaurus **salvo al objetivo declarado**. Ese
*escape hatch* no es opcional: sin él la exclusión se aplica en el predicado de
`getEntitiesOfClass`, antes de que el test de forma llegue a correr, y el resultado se lee como un
fallo de geometría que ninguna cantidad de retoques en la caja puede arreglar. Al Krifto le costó
tres intentos descubrirlo.

## Los atributos, que hoy son de vaca

10 de vida y 2 de daño: lo mismo que un Tangoftero de un bloque, y la mitad de vida que el Hell
Hippo. Es el legacy tal cual, y el legacy tampoco lo pensó.

**30 de vida y 4 de daño.**

El 4 y no más es deliberado: 1d añade agarre, sacudida y giro de la muerte, y cada uno querrá pegar
más fuerte que una dentellada suelta. Si el mordisco base ya hace tres corazones, los especiales no
tienen dónde crecer.

La `HitWindow` lee el atributo `ATTACK_DAMAGE` en vez de llevar un literal — convención del hippo, y
la que deja un solo sitio donde tocar el número.

**Knockback de la ventana 0.2**, bajo a propósito y por la misma razón que el daño: lo que 1d quiere
es una presa que se quede en las fauces, no una que salga despedida. (El atributo
`ATTACK_KNOCKBACK` sigue en 0 y es irrelevante aquí: la ventana lleva el suyo.)

## Lo que se enciende solo

`water_sprint` deja de estar muerto. `MeleeAttackGoal` de vanilla pone `setAggressive(true)` al
arrancar y lo quita al parar, y esa bandera **sí** viaja sincronizada — que es exactamente por lo que
la condición de ese clip se escribió en 1a contra `isAggressive()` y no contra `getTarget()`, que no
se sincroniza para mobs. El comentario que dejó 1a («nada lo pone hasta que llegue el goal de ataque
en 1c») se cumple aquí.

**Se verifica leyendo la fuente del goal antes de darlo por bueno**, no de memoria.

## Riesgos abiertos

Dos cosas que no se pueden decidir desde el código y van a la lista de pruebas:

- **Un cuerpo de tres bloques de ancho pathfindeando bajo el agua.** El salmón usa el mismo goal sin
  problema, pero mide 1.5. Un pasillo por el que un salmón entra puede no tener hueco para éste, y el
  síntoma sería una persecución que se queda clavada en vez de una que falla.
- **Una persecución que cruza la orilla.** `syncControlsToMedium()` cambia navegador y move control
  cada tick según el medio, y `MeleeAttackGoal` sostiene un `path` construido antes del cambio, que
  re-traza cada ~10 ticks. Es posible que se pierda un tick o dos en la línea de costa.

## Fuera de alcance

- **El agarre, la sacudida y el giro de la muerte.** Son 1d, y son las tres que justifican el bicho.
- **El delfín como presa**, descartado arriba.
- **El rugido al recibir daño.** El legacy tenía un `RoarOnHurtGoal` en prioridad 0 con
  `getRoarDuration() = 30`; en 26.1 esa clase **no existe** y habría que escribirla, y `lroar` /
  `wroar` siguen sin gastar. Queda fuera por disciplina de alcance, no por coste: es el candidato
  obvio para lo siguiente si al verlo pelear le falta voz.
- **Hitboxes multiparte**, que siguen yendo con el GT.

## Verificación

1. Muerde en tierra, y el daño cae en el tick en que se cierran las fauces — comprobado con
   `/deluxelib debug hitboxes` puesto, no a ojo.
2. Muerde en agua, incluyendo a un objetivo claramente por encima y a otro por debajo.
3. Persigue y mata una tortuga en la playa y un banco de bacalao en mar abierto.
4. La cría ni muerde ni persigue.
5. La madre defiende el huevo de verdad: se acerca un intruso y esta vez pasa algo.
6. El nado rápido aparece al fijar objetivo y desaparece al perderlo, igual en cliente y servidor.
7. Un Niras dormido al que golpeas no se levanta peleando: se despierta como siempre.
8. Dos Niras cerca: el que pelea muerde a su objetivo y no al vecino.
