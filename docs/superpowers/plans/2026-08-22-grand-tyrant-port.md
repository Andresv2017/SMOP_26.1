# Grand Tyrant — Plan de implementación

> **Para trabajadores agénticos:** SUB-SKILL REQUERIDA: usar `superpowers:subagent-driven-development` (recomendado) o `superpowers:executing-plans` para implementar este plan tarea a tarea. Los pasos usan casillas (`- [ ]`) para seguimiento.

**Objetivo:** portar el Grand Tyrant de 1.20.1 a SMOP 26.1 como jefe con cuatro ataques, rugido, pisotón, barra de jefe y cabeza decorativa, construido sobre DeluxeLib.

**Arquitectura:** la entidad cuelga de `CortexMonster` de DeluxeLib (corregido en la implementación; el spec decía `SMOPAnimal`). El combate va sobre `Cortex`, la FSM de DeluxeLib, con un `AttackSelector` propio que concentra rangos y cooldowns; el daño va en `HitWindow` atado a los clips, no en los goals. La cabeza decorativa sale del sistema `Statue` de la librería.

**Stack:** NeoForge 26.1, DeluxeLib 1.0.0 (composite build), Java 25, Gradle.

**Spec:** [2026-08-22-grand-tyrant-port-design.md](../specs/2026-08-22-grand-tyrant-port-design.md)

## Restricciones globales

- **No se hacen commits.** El usuario los hace. Ningún módulo termina en `git commit`.
- **Estructura 100% DeluxeLib.** La entidad extiende `CortexMonster`; nada de goals de vanilla que la
  librería ya cubra. La referencia es `MinotaurEntity` de Mythos&Mortals.
- **Cada módulo termina con una verificación práctica en juego**, no solo con que compile.
- Sin `System.out.println`. Logger o nada.
- Las duraciones de animación **se leen del `withLength` de cada clip**, nunca se estiman.
- `MobCategory.CREATURE` es decisión tomada. No cambiarla sin datos de `/smop debug spawn`.
- Paquete raíz: `net.darkblade.smop`.
- Comandos de verificación disponibles: `./gradlew compileJava`, `./gradlew runDataServer`, `./gradlew runData`, `./gradlew runGameTestServer`, `./gradlew runClient`.

## Módulos

| # | Módulo | Fase del spec | Se verifica con |
|---|---|---|---|
| 1 | Entidad y animaciones | a | `/summon`, camina, ninguna animación revienta |
| 2 | Spawn natural, barra y muerte | a | Barra visible, aparece en llanura |
| 3 | Cortex: deambular, perseguir, morder | b | Persigue y muerde |
| 4 | Los otros tres ataques y el selector | b | Encadena los cuatro |
| 5 | STOMP: polvo y sacudida | c | Tiembla la cámara |
| 6 | Rugido y sueño | c | Ruge al fijarte, duerme |
| 7 | `gt_head` sobre Statue y botín | d | Cae, se coloca, se ve en mano |

**Fuera de este plan:** la fase **e** (multiparte en DeluxeLib). Es trabajo en otro repositorio y beneficia a dos mobs; lleva su propio spec y su propio plan.

---

### Módulo 1: Entidad y animaciones

Lo más grande y lo más arriesgado: 5.428 líneas de clips contra un rig que lanza excepción si un hueso no existe.

**Archivos:**
- Crear: `src/main/java/net/darkblade/smop/client/gt/GTAnimations.java`
- Crear: `src/main/java/net/darkblade/smop/client/gt/GTAnimationsBase.java`
- Crear: `src/main/java/net/darkblade/smop/client/gt/GTModel.java`
- Crear: `src/main/java/net/darkblade/smop/client/gt/GTRenderer.java`
- Crear: `src/main/java/net/darkblade/smop/entity/gt/GTEntity.java`
- Crear: `src/main/java/net/darkblade/smop/entity/gt/GTState.java`
- Modificar: `src/main/java/net/darkblade/smop/entity/SMOPEntities.java`
- Modificar: `src/main/java/net/darkblade/smop/event/SMOPEntityAttributes.java`
- Modificar: `src/main/java/net/darkblade/smop/client/SMOPClientEvents.java`
- Ya existe, no tocar: `src/main/resources/assets/smop/textures/entity/gt/gt.png`

**Interfaces:**
- Consume: `CortexMonster` (`isMoving()` sincronizado, `buildCortex()`, `defaultState()`, `registerExtraGoals()`), `Cortex`, `WanderBehavior`, `DirectionalMoveControl`, `SmoothBodyRotationControl`, `Animatable#animator()`, `MobAnimator`, `StandardAnimation`, `AnimSource`, `Loop`, `DeluxeEntityRenderState`.
- Produce: `GTEntity` con `createAttributes()`; `SMOPEntities.GT`; `GTModel.LAYER_LOCATION`; los clips `idle`, `walk` cableados por `registerAnimations()`.

- [x] **Paso 1: copiar los dos archivos de animación**

Copiar `GTAnimations.java` y `GTAnimationsBase.java` desde `C:\Andrés\Spectacular-Mobs-of-Peligoro\src\main\java\net\darkblade\smopmod\entity\custom\gt\client\animations\` al paquete nuevo. **Cambiar solo la línea `package`** a `net.darkblade.smop.client.gt`. Los keyframes son arte autorado y no se tocan.

Si aparece `import net.minecraft.resources.ResourceLocation`, cambiarlo a `net.minecraft.resources.Identifier`.

- [x] **Paso 1b: inventario de clips, verificado**

Los dos archivos no están repartidos por tema, así que conviene tenerlo delante:

| Archivo | Clips |
|---|---|
| `GTAnimationsBase` | `bite`, `horn_swing`, `idle`, `walk`, `sprint`, `widle`, `swim`, `roar` |
| `GTAnimations` | `attack_stomp`, `claw_swing`, `sitting`, `sit`, `standing_up`, `sleep_preparing`, `sleep`, `alert_snore`, `awakening`, `alt_awakening`, `eating`, `death` |

Ojo a los dos que engañan: el mordisco y el rugido están en **Base**, y el pisotón se llama
**`attack_stomp`**, no `stomp`. `widle` y `swim` son de agua y este mob no la usa: quedan sin gastar,
como el `sprint`.

- [x] **Paso 2: listar los huesos que declara cada clip**

```bash
grep -ohE 'addAnimation\("[A-Za-z0-9_]+"' src/main/java/net/darkblade/smop/client/gt/GTAnimations.java src/main/java/net/darkblade/smop/client/gt/GTAnimationsBase.java | sed 's/.*("//;s/"//' | sort -u
```

Guardar esa lista. Es el contrato que el rig tiene que cumplir.

- [x] **Paso 3: portar `GTModel`**

Partir de `GTModel.java` del legacy. La geometría se copia cubo a cubo. Lo que cambia es la firma: en 26.1 el modelo no ve la entidad.

```java
public class GTModel extends EntityModel<DeluxeEntityRenderState> {

    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(SMOP.id("gt"), "main");

    public GTModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        // geometría del legacy, sin cambios
    }
}
```

Seguir el patrón exacto de `src/main/java/net/darkblade/smop/client/niras/NirasmosaurusModel.java`, que es el más reciente y ya usa `Rig`.

- [x] **Paso 4: comparar huesos del rig contra los del paso 2**

```bash
grep -ohE 'addOrReplaceChild\("[A-Za-z0-9_]+"' src/main/java/net/darkblade/smop/client/gt/GTModel.java | sed 's/.*("//;s/"//' | sort -u
```

Todo hueso que aparezca en el paso 2 y **no** aquí es un canal huérfano. **Podarlo del clip**, borrando su bloque `.addAnimation("hueso", ...)` entero. 26.1 lanza excepción al aplicar el clip; 1.20.1 lo saltaba en silencio. Fue el fallo más repetido del port del Hell Hippo.

- [x] **Paso 5: escribir `GTEntity` con lo mínimo**

```java
public class GTEntity extends CortexMonster<GTEntity, GTState> implements Animatable<GTEntity> {

    private final MobAnimator<GTEntity> animator;

    public GTEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.animator = new MobAnimator<>(this);
        this.moveControl = new DirectionalMoveControl<>(this)
                .setTurnSpeed(5.0F)          // lo que giraba el GTMoveControl del legacy
                .setCombatTurnSpeed(15.0F)
                .setFaceLockRadius(10.0D);
    }

    @Override
    protected @NotNull BodyRotationControl createBodyControl() {
        SmoothBodyRotationControl<GTEntity> control = new SmoothBodyRotationControl<>(this);
        control.bodyLagStill = 0.02F;   // parado, el cuerpo casi ignora la cabeza
        control.bodyMax = 10.0F;        // coherente con un rumbo que gira 5 por tick
        return control;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.20D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.5D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.STEP_HEIGHT, 2.5D);   // setMaxUpStep ya no existe
    }

    @Override protected @NotNull GTState defaultState() { return GTState.WANDER; }

    @Override
    protected @NotNull Cortex<GTEntity, GTState> buildCortex() {
        return Cortex.<GTEntity, GTState>builder(GTState.WANDER)
                .register(GTState.WANDER, new WanderBehavior<GTEntity, GTState>(1.0D)
                        .wanderRange(25, 10))
                .build();
    }

    @Override public @NotNull MobAnimator<GTEntity> animator() { return this.animator; }
}
```

**Nada de `registerGoals`.** `CortexMonster` instala el `CortexGoal` detrás de un `FloatGoal` él mismo;
los goals extra irían en `registerExtraGoals()`, y aquí no hay ninguno. En particular **no** se ponen
`LookAtPlayerGoal` ni `RandomLookAroundGoal`: parado, el control de rotación de cuerpo persigue a la
cabeza, y esos dos goals la mueven sin parar — es exactamente el pivote que se reportó en juego.

**El `GTState` hace falta desde este módulo**, porque `CortexMonster` es genérico sobre él. Está en el
módulo 3, paso 1; adelántalo aquí.

`registerDeath` y no una condición de reproducción: `MobAnimator` engancha solo en `LivingDeathEvent`
y mantiene el cadáver lo que dure el clip. Es lo mismo que hace el Nirasmosaurus con sus dos muertes.

**El orden de los argumentos engaña.** La firma real es
`StandardAnimation(String name, AnimSource data, Loop loop, int layer, int priority, float duration)`
— **la capa va antes que la prioridad**. Invertirlos registra la locomoción en una capa aditiva sobre
nada y el clip no se ve nunca, sin ningún error en el log. Pasó en la primera pasada de este módulo.

Las duraciones ya están medidas del `withLength` real: `idle` 10.0, `walk` 3.0, `death` 2.25. Para los
módulos siguientes, medidas de paso: `bite` 0.8333 (≈17 t), `horn_swing` 0.9167 (≈18 t),
`claw_swing` 1.6 (32 t exactos), `attack_stomp` 3.35 (67 t exactos), `roar` **5.2 (104 t, no los 100
que dice el legacy)**.

- [x] **Paso 6: registrar el tipo de entidad**

En `SMOPEntities.java`, junto a los demás:

```java
public static final DeferredHolder<EntityType<?>, EntityType<GTEntity>> GT =
        ENTITY_TYPES.register("gt",
                () -> EntityType.Builder.<GTEntity>of(GTEntity::new, MobCategory.CREATURE)
                        .sized(3.2F, 6.2F)
                        .clientTrackingRange(16)
                        .build(ResourceKey.create(Registries.ENTITY_TYPE, SMOP.id("gt"))));
```

`clientTrackingRange(16)` y no 10: mide 6,2 bloques de alto y hay que verlo venir.

- [x] **Paso 7: registrar atributos, renderer y capa de modelo**

En `SMOPEntityAttributes.onAttributes`:
```java
event.put(SMOPEntities.GT.get(), GTEntity.createAttributes().build());
```

En `SMOPClientEvents.onRegisterLayerDefinitions`:
```java
event.registerLayerDefinition(GTModel.LAYER_LOCATION, GTModel::createBodyLayer);
```

En `SMOPClientEvents.onRegisterRenderers`:
```java
event.registerEntityRenderer(SMOPEntities.GT.get(), GTRenderer::new);
```

`GTRenderer` extiende `CustomMobRenderer` de DeluxeLib. El legacy usa `RenderType.entityTranslucent` — mantenerlo.

- [x] **Paso 8: generar el lang**

```bash
./gradlew runData
```

`autoEntityNames` deriva `gt` → "Gt", que está mal. Añadir el override en `SMOPDatagen.Lang.addTranslations`, junto a los que ya hay:

```java
add(SMOPEntities.GT.get(), "Grand Tyrant");
```

La Fase 1 dejó anotado que el lang copiado decía "Grant Tyrant" — es errata, va con d de Grand.

- [ ] **Paso 9: verificación práctica**

```bash
./gradlew runClient
```

En el juego:

1. `/summon smop:gt ~ ~ ~5` — aparece, mide claramente más que tú.
2. Tiene **300 de vida**: `/data get entity @e[type=smop:gt,limit=1] Health`.
3. Camina cuando deambula y se queda quieto cuando no: los clips `idle` y `walk` se alternan sin saltos.
4. **Ninguna excepción de animación en el log.** Es la comprobación que justifica el módulo: si el paso 4 dejó un hueso huérfano, revienta aquí y no antes.
5. Se muere sin crash: `/kill @e[type=smop:gt]`.
6. El nombre sale como **Grand Tyrant**.

---

### Módulo 2: Spawn natural, barra de jefe y muerte

**Archivos:**
- Modificar: `src/main/java/net/darkblade/smop/entity/SMOPSpawns.java`
- Modificar: `src/main/java/net/darkblade/smop/event/SMOPEntityAttributes.java` (spawn placement)
- Modificar: `src/main/java/net/darkblade/smop/entity/gt/GTEntity.java`

**Interfaces:**
- Consume: `SMOPEntities.GT`, `DeluxeBiomeSpawns`.
- Produce: `GTEntity` con barra de jefe viva mientras esté vivo.

- [x] **Paso 1: declarar el spawn**

En `SMOPSpawns.register()`, siguiendo el patrón que ya hay:

```java
DeluxeBiomeSpawns.builder(SMOPEntities.GT::get, MobCategory.CREATURE)
        .spawnRate(5, 1, 1)
        .biomes(Biomes.PLAINS, Biomes.DESERT)
        .submit();
```

Peso 5, uno solo, llanura y desierto: los números del legacy sin tocar.

- [x] **Paso 2: registrar el spawn placement**

En `SMOPEntityAttributes.onSpawnPlacements`, con la misma forma que los demás:

```java
event.register(SMOPEntities.GT.get(),
        SpawnPlacementTypes.ON_GROUND,
        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
        Animal::checkAnimalSpawnRules,
        RegisterSpawnPlacementsEvent.Operation.REPLACE);
```

- [x] **Paso 3: la barra de jefe**

En `GTEntity`, siguiendo cómo `RiderAbility` maneja su `ServerBossEvent`:

```java
private final ServerBossEvent bossBar =
        new ServerBossEvent(this.getDisplayName(),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

@Override
public void startSeenByPlayer(@NotNull ServerPlayer player) {
    super.startSeenByPlayer(player);
    this.bossBar.addPlayer(player);
}

@Override
public void stopSeenByPlayer(@NotNull ServerPlayer player) {
    super.stopSeenByPlayer(player);
    this.bossBar.removePlayer(player);
}

@Override
public void tick() {
    super.tick();
    if (!this.level().isClientSide()) {
        this.bossBar.setProgress(this.getHealth() / this.getMaxHealth());
    }
}
```

`startSeenByPlayer`/`stopSeenByPlayer` y no una lista propia: vanilla ya sabe quién tiene la entidad en seguimiento, y con `clientTrackingRange(16)` del módulo 1 eso son 16 chunks.

- [x] **Paso 4: generar los datos**

```bash
./gradlew runDataServer
```

Confirmar que aparece `src/generated/server/data/smop/neoforge/biome_modifier/gt.json` con llanura y desierto.

- [ ] **Paso 5: verificación práctica**

```bash
./gradlew runClient
```

1. `/summon smop:gt ~ ~ ~5` — **sale la barra roja arriba**, y baja al golpearlo.
2. Aléjate 200 bloques: la barra desaparece. Vuelve: reaparece.
3. Mátalo: la barra desaparece y no queda pegada.
4. **El spawn natural**, que es lo que el spec marca como riesgo: ve a una llanura en un mundo nuevo y corre `/smop debug spawn`. Anota si el GT llega siquiera a intentarse o si `CREATURE` lo bloquea en la puerta de categoría, como pasó con el Niras.

> Si el paso 4 confirma que nunca se intenta, **no cambiar nada todavía**: anotarlo y decidirlo con el dato. Cambiar a `MONSTER` es una línea en `SMOPEntities`.

---

### Módulo 3: Cortex — deambular, perseguir y morder

El módulo que estrena la FSM. Un solo ataque, para probar el sistema antes de meter los cuatro.

**Archivos:**
- Crear: `src/main/java/net/darkblade/smop/entity/gt/GTState.java`
- Crear: `src/main/java/net/darkblade/smop/entity/gt/GTAttackSelector.java`
- Modificar: `src/main/java/net/darkblade/smop/entity/gt/GTEntity.java`

**Interfaces:**
- Consume: `Cortex.builder(S)`, `.register(S, Behavior)`, `.targeting(Targeting)`, `.globalRule(GlobalRule)`, `.build()`; `CortexGoal(E, Cortex)`; `WanderBehavior(double)`, `.onTargetFound(StateEnum)`; `ChaseTargetBehavior(double, AttackSelector)`; `AnimatedMeleeBehavior(String, int, StateEnum)`; `NearestEntityTargeting(Class, double)`; `HitWindow`, `AttackShape`, `AttackAnchor`.
- Produce: `GTState` (enum de estados), `GTAttackSelector` (elige ataque), y el `Cortex` montado en `GTEntity`.

- [x] **Paso 1: el enum de estados**

`StateEnum` exige `int id()` y `String name()`; `name()` lo da el enum.

```java
public enum GTState implements StateEnum {
    WANDER(0),
    CHASE(1),
    BITE(2),
    HORN_SWING(3),
    CLAW_SWING(4),
    STOMP(5),
    ROAR(6);

    private final int id;

    GTState(int id) { this.id = id; }

    @Override public int id() { return this.id; }
}
```

Los siete estados desde ya, aunque este módulo solo cablee tres: el enum es barato y los módulos 4 y 6 no tendrán que tocarlo.

- [x] **Paso 2: el selector, con un solo ataque de momento**

```java
public final class GTAttackSelector implements AttackSelector<GTEntity> {

    /** Alcance del mordisco, medido al borde de la hitbox y no al centro. */
    private static final double BITE_RANGE = 6.0D;

    @Override
    public @Nullable StateEnum select(GTEntity gt, BehaviorContext context) {
        LivingEntity target = gt.getTarget();
        if (target == null) {
            return null;
        }
        return gt.distanceTo(target) <= BITE_RANGE ? GTState.BITE : null;
    }
}
```

- [x] **Paso 3: montar el Cortex en la entidad**

```java
private Cortex<GTEntity, GTState> cortex;

@Override
@Override
protected @NotNull Cortex<GTEntity, GTState> buildCortex() {
    return Cortex.<GTEntity, GTState>builder(GTState.WANDER)
            .targeting(new NearestEntityTargeting<>(Player.class, 40.0D))
            .register(GTState.WANDER, new WanderBehavior<GTEntity, GTState>(1.0D)
                    // 25/10 y no el 10/7 por defecto: es lo que el legacy pasaba a su CustomWanderGoal,
                    // porque un bicho de seis bloques con el radio normal parece arrastrar los pies.
                    .wanderRange(25, 10)
                    .onTargetFound(GTState.CHASE))
            .register(GTState.CHASE, new ChaseTargetBehavior<GTEntity, GTState>(1.0D,
                    new GTAttackSelector()))
            .register(GTState.BITE, new AnimatedMeleeBehavior<GTEntity, GTState>(
                    "bite", 17, GTState.CHASE))
            .build();
}
```

Este módulo **amplía** el `buildCortex()` que el módulo 1 ya dejó puesto: añade el `targeting`, el
`onTargetFound` del wander, y los dos estados nuevos. No hay `registerGoals` que tocar —
`CortexMonster` instala el `CortexGoal` por su cuenta.

El `17` es la duración del legacy: **verificarla contra el `withLength` del clip `bite`** y corregirla si no coincide.

- [x] **Paso 4: el clip del mordisco y su ventana de daño**

En `registerAnimations()`, junto a `idle` y `walk`:

```java
StandardAnimation bite = new StandardAnimation("bite",
        new AnimSource(() -> GTAnimationsBase.bite), Loop.PLAY_ONCE, 0, 0, 0.0F);
bite.blendInMs(80).blendOutMs(150);

HitWindow.of(8, 8)
        .shape(AttackShape.box3d(5.0F, 2.0F, 4.0F))
        .anchor(AttackAnchor.of(3.0F, 0.0F, 2.0F))
        .damage(18.0F)
        .knockback(0.35F)
        .filter(target -> !(target instanceof GTEntity))
        .applyTo(bite);

this.animator().register(idle, walk, bite);
```

`box3d` y no `box`: el javadoc de `AttackShape` avisa de que `box` ignora el eje Y, y en un bicho de 6,2 de alto eso muerde a quien esté dos bloques por encima. El frame 8 y el daño 18 son del legacy; el volumen y el ancla son números de ojo, a cuadrar con `/deluxelib debug hitboxes`.

Sustituir el `0.0F` de duración por el `withLength` real.

- [ ] **Paso 5: verificación práctica**

```bash
./gradlew runClient
```

1. `/summon smop:gt ~ ~ ~10` en creativo. Deambula solo.
2. Pásate a supervivencia y acércate: **te fija y viene a por ti**.
3. A distancia de mordisco, muerde. La animación se reproduce entera.
4. `/deluxelib debug hitboxes` puesto: **el daño cae en el frame en que las fauces se cierran**, no al empezar ni al acabar.
5. Aléjate: deja de perseguir y vuelve a deambular. La FSM vuelve a `WANDER` sin quedarse colgada.

---

### Módulo 4: Los otros tres ataques y el selector completo

**Archivos:**
- Modificar: `src/main/java/net/darkblade/smop/entity/gt/GTAttackSelector.java`
- Modificar: `src/main/java/net/darkblade/smop/entity/gt/GTEntity.java`

**Interfaces:**
- Consume: lo del módulo 3.
- Produce: los estados `HORN_SWING`, `CLAW_SWING` y `STOMP` cableados, con sus `HitWindow`.

- [x] **Paso 1: los tres comportamientos**

Añadir al builder del Cortex, con las duraciones del legacy **a verificar contra el `withLength` de cada clip**:

```java
.register(GTState.HORN_SWING, new AnimatedMeleeBehavior<GTEntity, GTState>(
        "horn_swing", 19, GTState.CHASE))
.register(GTState.CLAW_SWING, new AnimatedMeleeBehavior<GTEntity, GTState>(
        "claw_swing", 32, GTState.CHASE))
.register(GTState.STOMP, new AnimatedMeleeBehavior<GTEntity, GTState>(
        "attack_stomp", 67, GTState.CHASE))
```

- [x] **Paso 2: los tres clips y sus ventanas**

Tres frontales y uno radial. Cada uno con **su propia instancia** de `HitWindow`: cada una guarda su `hitThisSwing` y su `lastSweepAngle`, y compartirlas entre clips es lo que el Kriftognathus documenta como inservible.

```java
// Cornada: el que más empuja.
HitWindow.of(10, 10)
        .shape(AttackShape.box3d(5.0F, 2.0F, 4.0F))
        .anchor(AttackAnchor.of(3.0F, 0.0F, 2.0F))
        .damage(20.0F).knockback(0.90F)
        .filter(target -> !(target instanceof GTEntity))
        .applyTo(hornSwing);

// Zarpazo: más lento, empuja hacia arriba.
HitWindow.of(10, 10)
        .shape(AttackShape.box3d(5.0F, 2.0F, 4.0F))
        .anchor(AttackAnchor.of(3.0F, 0.0F, 2.0F))
        .damage(18.0F).knockback(0.60F)
        .filter(target -> !(target instanceof GTEntity))
        .applyTo(clawSwing);

// Pisotón: tres impactos, radial y mucho más ancho.
for (int frame : new int[]{14, 26, 46}) {
    HitWindow.of(frame, frame)
            .shape(AttackShape.sphere(10.0F))
            .anchor(AttackAnchor.of(0.0F, 0.0F, 0.0F))
            .damage(26.0F).knockback(0.10F)
            .filter(target -> !(target instanceof GTEntity))
            .applyTo(stomp);
}
```

El legacy le da al STOMP 10 de ancho contra los 4 de los demás, y lo ancla en el centro: es radial, no frontal. `AttackShape.sphere(float radius)` existe — verificado en la interfaz sellada, que permite `Sector`, `Capsule`, `Sphere`, `Box`, `Box3D` y `Beam`.

- [x] **Paso 3: el selector con los cuatro**

```java
private static final double BITE_RANGE = 6.0D;
// Los tres ordinarios comparten ATTACK_RANGE; el legacy no les daba alcances distintos.
private static final double STOMP_RANGE = 6.0D;   // MENOS que ATTACK_RANGE, no más

@Override
public @Nullable StateEnum select(GTEntity gt, BehaviorContext context) {
    LivingEntity target = gt.getTarget();
    if (target == null) {
        return null;
    }
    double distance = gt.distanceTo(target);

    // OJO, al revés de lo que parece: en el legacy el pisotón alcanza MENOS (6) que los demás (8).
    // Es un golpe cercano de área ancha. Además exige suelo, misma altura y línea de visión, y lleva
    // un hueco mínimo de 60 ticks para que no salgan tres seguidos de 67 ticks cada uno.
    if (distance <= STOMP_RANGE && canStomp(gt, target) && gt.getRandom().nextInt(100) < 30) {
        return GTState.STOMP;
    }
    if (distance <= BITE_RANGE) {
        return gt.getRandom().nextBoolean() ? GTState.BITE : GTState.HORN_SWING;
    }
    if (distance <= SWING_RANGE) {
        return GTState.CLAW_SWING;
    }
    return null;
}
```

Los pesos son un punto de partida jugable, no un diseño cerrado: aquí es donde se ajusta el ritmo del jefe.

- [x] **Paso 4: verificación práctica**

```bash
./gradlew runClient
```

1. En supervivencia, con armadura, pelea contra él un minuto entero.
2. **Salen los cuatro ataques.** Si alguno no sale nunca, el rango o el peso del selector lo está tapando.
3. `/deluxelib debug hitboxes`: el pisotón alcanza claramente más lejos que el mordisco, y **golpea tres veces** en una sola animación.
4. La cornada te manda más lejos que el zarpazo; el zarpazo te levanta más.
5. Ningún ataque se corta a medias ni se solapa con el siguiente.

---

### Módulo 5: STOMP — polvo y sacudida

**Archivos:**
- Modificar: `src/main/java/net/darkblade/smop/entity/gt/GTEntity.java`

**Interfaces:**
- Consume: `ScreenShake` / `ScreenShakes` / `ShakeProfile`, `ParticleFx` (DeluxeLib).
- Produce: método `onStompImpact()` llamado desde los frames del pisotón.

- [x] **Paso 1: enganchar el efecto a los frames del pisotón**

Los tres `HitWindow` del módulo 4 ya corren en los frames 14, 26 y 46. Añadirles `.onSweep(...)` para que el efecto salga exactamente cuando cae el daño, en vez de contar ticks aparte:

```java
.onSweep((attacker, origin, facing, shape, hits) -> this.onStompImpact())
```

- [x] **Paso 2: escribir el impacto**

APIs verificadas: `ScreenShake` es fluida y **termina en `.fire()`**; `ParticleFx.ring(level, particle, centro, radio)` dibuja un anillo horizontal.

```java
/** Radio del anillo de polvo, en bloques. Coincide con el alcance del pisotón. */
private static final double STOMP_DUST_RADIUS = 10.0D;

private void onStompImpact() {
    if (!(this.level() instanceof ServerLevel serverLevel)) {
        return;
    }

    // El polvo sale al nivel de los pies, no del centro del bicho: mide 6,2 de alto.
    Vec3 feet = new Vec3(this.getX(), this.getY(), this.getZ());
    ParticleFx.ring(serverLevel,
            new BlockParticleOption(ParticleTypes.BLOCK, this.getBlockStateOn()),
            feet, STOMP_DUST_RADIUS);

    // Corta y seca: un pisotón no es un terremoto. `around` la manda a todo el que
    // esté en rango en ese nivel; `fire()` es el terminal de la API fluida.
    ScreenShake.around(serverLevel)
            .duration(8)
            .fadeOut(4)
            .amplitude(0.35F)
            .frequency(1.8F)
            .fire();
}
```

`BlockParticleOption` con `getBlockStateOn()` y no un polvo fijo: pisar arena levanta arena y pisar hierba levanta hierba, gratis.

- [x] **Paso 3: verificación práctica**

```bash
./gradlew runClient
```

1. Provoca el pisotón en supervivencia.
2. **La cámara tiembla** en los tres impactos, no solo en el primero.
3. **Sale polvo** en el suelo, en el punto donde pisa.
4. Alguien a 30 bloques no siente la sacudida: tiene radio, no es global.
5. En un servidor dedicado (`./gradlew runServer` y conectar) no hay `NoClassDefFoundError`: los efectos son cliente y el servidor no puede tocarlos directamente.

---

### Módulo 6: Rugido y sueño

**Archivos:**
- Modificar: `src/main/java/net/darkblade/smop/entity/gt/GTEntity.java`
- Modificar: `src/main/java/net/darkblade/smop/entity/gt/GTState.java` (ya tiene `ROAR` desde el módulo 1)

**Interfaces:**
- Consume: `SMOPSounds.GT_ROAR`; `TimedAnimationBehavior(String, int, StateEnum)`; `GlobalRule`; `SleepGoal`, `SleepPhase`, `ISleepingEntity`; `CortexMonster#registerExtraGoals()`.
- Produce: el GT ruge al fijar objetivo y duerme con el ciclo de 26.1.

- [x] **Paso 1: el rugido es un estado, no un flag**

Al pasar a `CortexMonster` esto cambió: ya no hay `isRoaring()`/`setRoaring()` de `SMOPAnimal` que
mantener. **`ROAR` es un estado de la FSM** y la base ya lo sincroniza al cliente, así que el clip se
ata a `syncedState() == GTState.ROAR` y no hace falta ninguna bandera propia.

Lo único que queda es reproducir el sonido al entrar en el estado, con los 104 ticks medidos del clip.

`SMOPSounds.GT_ROAR` está registrado desde la Fase 1 y **nunca se ha usado** — verificado: `SOUNDS.register("gt_roar", () -> SoundEvent.createFixedRangeEvent(SMOP.id("gt_roar"), 64.0F))`. Rango fijo de 64 bloques, que es exactamente lo que quieres de un rugido de jefe.

- [x] **Paso 2: el estado ROAR y su disparador**

```java
.register(GTState.ROAR, new TimedAnimationBehavior<GTEntity, GTState>(
        "roar", 104, GTState.CHASE).faceTarget())
```

Y la regla global que lo dispara al fijar objetivo, que es lo que hacía `RoarOnTargetGoal(this, 100, true)` del legacy.

`GlobalRule<E>` es un `@FunctionalInterface` con un solo método, verificado:
`@Nullable Integer evaluate(E entity, BehaviorContext context, int currentStateId)` — devuelve el **id** del estado al que saltar, o `null` para no intervenir.

La regla se dispara **una vez por objetivo nuevo**, no cada tick que haya objetivo, y para eso hace falta recordar a quién se le rugió:

```java
/** A quién se le rugió por última vez, para no rugir cada tick que siga ahí. */
@Nullable private LivingEntity lastRoaredAt;

private @Nullable Integer roarOnNewTarget(GTEntity gt, BehaviorContext context, int currentStateId) {
    LivingEntity target = gt.getTarget();
    if (target == null) {
        gt.lastRoaredAt = null;   // perdió el objetivo: el siguiente vuelve a merecer rugido
        return null;
    }
    if (target == gt.lastRoaredAt || currentStateId == GTState.ROAR.id()) {
        return null;
    }
    gt.lastRoaredAt = target;
    return GTState.ROAR.id();
}
```

Y en el builder: `.globalRule(this::roarOnNewTarget)`.

El `currentStateId == GTState.ROAR.id()` no es redundante: las reglas globales se evalúan **antes** que el comportamiento activo, así que sin él la regla se reentraría a sí misma en el primer tick del rugido.

- [x] **Paso 3: el clip del rugido**

```java
StandardAnimation roar = new StandardAnimation("roar",
        new AnimSource(() -> GTAnimationsBase.roar), Loop.PLAY_ONCE, 0, 0, 0.0F);
```

Duración del `withLength` real. Si no coincide con los 100 ticks del paso 2, **manda el clip**: ajustar el `TimedAnimationBehavior` y `getRoarDuration()` a lo que mida.

- [x] **Paso 4: el sueño**

Registrar `SleepGoal` como en los otros mobs, e implementar los overrides de `ISleepAwareness` que la base pida. El legacy despertaba por proximidad (`shouldWakeOnPlayerProximity()` en `true`) — mantenerlo.

Copiar la forma de `src/main/java/net/darkblade/smop/entity/tangoftero/TangofteroEntity.java`, no inventarla.

- [x] **Paso 5: cerrar el Cortex mientras duerme**

El `CortexGoal` y el `SleepGoal` se pelearían por el control. Como la base instala el `CortexGoal` por
su cuenta, la vía limpia es una `GlobalRule` que devuelva `WANDER` mientras el ciclo esté activo, más
el `SleepGoal` añadido en `registerExtraGoals()`:

```java
@Override
protected void registerExtraGoals() {
    this.goalSelector.addGoal(4, new SleepGoal<>(this, /* SleepUrge del mob */));
}
```

- [ ] **Paso 6: verificación práctica**

```bash
./gradlew runClient
```

1. Acércate hasta que te fije: **ruge, con sonido**, y luego te persigue. No ruge otra vez sin perderte de vista primero.
2. El rugido se oye a distancia y no se corta a medias.
3. `/time set night` (o el que le toque): **se duerme**, y la animación de dormir se reproduce.
4. Acércate dormido: **despierta**.
5. Dormido **no ataca**: el Cortex está cerrado, no se queda mordiendo en sueños.

---

### Módulo 7: `gt_head` sobre Statue, y el botín

**Archivos:**
- Crear: `src/main/java/net/darkblade/smop/block/SMOPBlockEntities.java`
- Crear: `src/main/java/net/darkblade/smop/client/gt/GTHeadModel.java`
- Crear: `src/main/java/net/darkblade/smop/client/gt/GTHeadStatue.java` (el `StatueConfig` y su `StatueType`)
- Modificar: `src/main/java/net/darkblade/smop/block/SMOPBlocks.java`
- Modificar: `src/main/java/net/darkblade/smop/item/SMOPItems.java`
- Modificar: `src/main/java/net/darkblade/smop/client/SMOPClientEvents.java`
- Modificar: `src/main/java/net/darkblade/smop/SMOP.java` (registrar el nuevo `DeferredRegister`)
- Modificar: `src/main/java/net/darkblade/smop/datagen/SMOPDatagen.java` (botín)

**Interfaces:**
- Consume: `StatueBlock(Properties, Supplier<BlockEntityType<StatueBlockEntity>>)`, `StatueBlockEntity.registerType(register, name, blockSupplier, type)`, `StatueRegistry.register(StatueType, Supplier<Item>, StatueConfig)`, `StatueRenderer`, `StatueConfig(name, layer, texture, restingPose, x, y, z, scale, itemTransforms)`, `StatueTuner`.
- Produce: bloque e item `gt_head`, y la tabla de botín del GT.

- [ ] **Paso 1: crear el registro de block entities**

SMOP no tiene ninguno todavía. Crear `SMOPBlockEntities` con un `DeferredRegister<BlockEntityType<?>>` y registrarlo en `SMOP.java` junto a los demás.

- [ ] **Paso 2: el bloque, su block entity y su item**

La receta exacta está en el javadoc de `StatueBlock`. Ojo al orden de declaración: el supplier del block entity se referencia cualificado porque se declara después.

```java
public static final StatueType GT_HEAD_TYPE = new StatueType(SMOP.id("gt_head"));

public static final DeferredBlock<StatueBlock> GT_HEAD = BLOCKS.registerBlock("gt_head",
        props -> new StatueBlock(props, SMOPBlockEntities.GT_HEAD_BE),
        () -> BlockBehaviour.Properties.of().strength(5.0F));
```

En `SMOPItems`:
```java
public static final DeferredItem<BlockItem> GT_HEAD =
        ITEMS.registerSimpleBlockItem("gt_head", SMOPBlocks.GT_HEAD);
```
Y añadirlo a `displayItems` de la pestaña creativa.

`noOcclusion()` lo aplica `StatueBlock` en su constructor — **no hace falta ponerlo**, y su javadoc explica que sin él la estatua sale como silueta negra.

- [ ] **Paso 3: portar el modelo de la cabeza**

`GrandTyrantHeadModel.java` del legacy (128 líneas) → `GTHeadModel`, con su propia `ModelLayerLocation` y registrada en `onRegisterLayerDefinitions`. Misma mecánica que el paso 3 del módulo 1.

- [ ] **Paso 4: el `StatueConfig` y el registro cliente**

```java
StatueRegistry.register(SMOPBlocks.GT_HEAD_TYPE, SMOPItems.GT_HEAD, GT_HEAD_CONFIG);
event.registerBlockEntityRenderer(SMOPBlockEntities.GT_HEAD_BE.get(), StatueRenderer::new);
```

Los `x/y/z/scale` y las transformas de item **no se adivinan**: se ponen a cero y se afinan en el paso 6.

- [ ] **Paso 5: la tabla de botín**

En `SMOPDatagen.Loot.addLootTables`, junto a las otras cinco:

```java
this.add(SMOPEntities.GT.get(), LootTable.lootTable()
        .withPool(LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1))
                .add(LootItem.lootTableItem(SMOPItems.GT_HEAD.get()))));
```

Garantizada, una por muerte. El legacy lo hacía con `dropCustomDeathLoot`; aquí va por tabla, como el resto del mod.

```bash
./gradlew runDataServer
```

- [ ] **Paso 6: afinar la pose con el afinador en vivo**

```
/deluxelib debug statuetune
```

Ajustar con el numpad hasta que la cabeza se asiente bien sobre el bloque, pulsar la tecla de imprimir, y **pegar los números sobre la llamada al constructor de `StatueConfig`**. Misma mecánica que el afinador de items.

- [ ] **Paso 7: verificación práctica**

```bash
./gradlew runClient
```

1. Mata un GT en supervivencia: **suelta la cabeza**, una.
2. Colócala: se ve el modelo 3D, **no una silueta negra** (si sale negra, falta `noOcclusion` — pero `StatueBlock` ya lo aplica, así que revisa que estés usando `StatueBlock` y no un `Block` normal).
3. Mira la cabeza colocada: **mira hacia ti**, porque `getStateForPlacement` la orienta hacia quien la puso.
4. En la mano y en el inventario se ve como cabeza, no como cubo rosa.
5. Rómpela y vuelve a colocarla: no se pierde ni cambia de orientación al recargar el mundo.
6. **Servidor dedicado**: coloca una y reinicia. Sin `NoClassDefFoundError` — es el fallo que `StatueType` existe para evitar, y está documentado en la librería como reproducido el 2026-07-30.

---

## Lo que queda fuera

La **fase e del spec** — las 7 hitboxes multiparte en DeluxeLib, más el retrofit a las 2 del Nirasmosaurus. Es trabajo en otro repositorio, sin precedente en la librería, y da servicio a dos mobs. Merece su propio spec y su propio plan.

Hasta entonces el GT vive con un solo AABB de 3,2 × 6,2, y golpear la cola cuenta igual que golpear la cabeza.
