# Grand Tyrant — Peso físico y sueño: plan de implementación

> **Para trabajadores agénticos:** SUB-SKILL REQUERIDA: usar `superpowers:subagent-driven-development` (recomendado) o `superpowers:executing-plans` para implementar este plan tarea a tarea. Los pasos usan casillas (`- [ ]`) para seguimiento.

**Objetivo:** que el Grand Tyrant pese lo que mide, y que encontrárselo dormido sea una decisión del jugador — rodearlo agachado o despertarlo a sabiendas.

**Arquitectura:** dos módulos independientes sobre el port ya terminado. El módulo A es todo cliente y efectos: un paquete cosmético de grietas, eventos por frame en los clips de marcha, y tres componentes de rig de DeluxeLib que el GT no estrenaba. El módulo B toca el sistema de sueño compartido con cuidado quirúrgico —todo lo nuevo entra con el valor de hoy por defecto— y mete la lógica propia del GT en una clase pequeña aparte, igual que `SleepUrge` vive fuera del goal.

**Stack:** NeoForge 26.1, Java 21, DeluxeLib 1.0.0 (`MobAnimator`, `ParticleFx`, `ScreenShake`, `Rig`, `NetworkCreator`).

## Restricciones globales

- **Este proyecto no tiene framework de tests.** No hay `src/test`. La verificación de cada tarea es, en este orden: `./gradlew compileJava` en verde, la comprobación numérica cuando la tarea toca datos de animación, y la comprobación en juego que cierra cada módulo. No inventes un framework de tests ni añadas dependencias de test.
- **Una fase de sueño dura exactamente lo que su clip.** Más larga deja al animal congelado en el último frame; más corta lo corta a media pose. Si registras un clip, declara su duración en `sleepPhaseDuration`.
- **Manda el loop.** Cuando una transición y un loop discrepan en una pose, se corrige la transición: el loop es lo que se ve durante segundos.
- **Un blend sólo compensa cuando el peor desajuste posible es mayor que el arranque propio del clip.** Con costura limpia, blend de 50 ms.
- **Todo lo que se añada a `SleepGoal`, `SleepUrge` o `ISleepingEntity` entra con el comportamiento de hoy por defecto.** Lo comparten Tangoftero, Kriftognathus, Hell Hippo y Nirasmosaurus. Ningún cambio puede alterarlos.
- Comentarios en el estilo de la casa: explican **por qué**, no qué. En español, como el resto de `GTEntity`.
- Commits frecuentes, uno por tarea.

## Estructura de ficheros

| Fichero | Responsabilidad | Módulo |
|---|---|---|
| `network/packet/StompCrackFxClientPacket.java` *(nuevo)* | Servidor→cliente: "hubo un impacto aquí, con este radio" | A |
| `client/gt/GroundCrackFx.java` *(nuevo)* | Cliente: elige bloques, pinta grietas, las caduca | A |
| `network/SMOPNetwork.java` | Registrar el paquete nuevo | A |
| `entity/gt/GTEntity.java` | Disparar grietas y pisadas; siesta, avisos, variante de despertar | A y B |
| `client/gt/GTModel.java` | Exponer huesos y montar los tres componentes de rig | A |
| `docs/superpowers/tools/seam_audit.py` *(nuevo)* | Medir costuras y velocidad de curvas | B |
| `client/gt/GTAnimations.java` | Costuras de `alt_awakening` | B |
| `entity/sleep/SleepUrge.java` | Partir `isForced()`; estado de siesta | B |
| `entity/sleep/SleepGoal.java` | Radio por mob, evaluador con voz sobre jugadores, entrada directa a `SLEEPING` | B |
| `entity/sleep/ISleepingEntity.java` | `getSleepThreatRadius()` con 4.0 por defecto | B |
| `entity/gt/GTSlumberWatch.java` *(nuevo)* | Contar avisos, enfriarlos, decidir la reacción | B |
| `command/SMOPSleepDebug.java` *(nuevo)* | `/smop debug sleep watch` | B |
| `command/SMOPCommands.java` | Colgar el comando nuevo | B |

---

# Módulo A · Peso físico

## Tarea A1: Las grietas del pisotón

**Ficheros:**
- Crear: `src/main/java/net/darkblade/smop/network/packet/StompCrackFxClientPacket.java`
- Crear: `src/main/java/net/darkblade/smop/client/gt/GroundCrackFx.java`
- Modificar: `src/main/java/net/darkblade/smop/network/SMOPNetwork.java`
- Modificar: `src/main/java/net/darkblade/smop/entity/gt/GTEntity.java` (dentro de `onStompImpact()`)

**Interfaces:**
- Consume: `SMOPNetwork.INSTANCE` (`NetworkCreator`), `GTEntity#onStompImpact()`, `GTEntity.STOMP_RADIUS`.
- Produce: `StompCrackFxClientPacket(int cx, int cy, int cz, int radius)` y `GroundCrackFx.add(BlockPos, int ttl, int stage)`. Nadie más los usa.

**Contexto que necesitas:** el GT de 1.20.1 tenía esto y el port lo perdió. Era cosmético entero: pintaba la textura de rotura de vanilla con `LevelRenderer.destroyBlockProgress(id, pos, etapa)` y la quitaba con `-1`. **Nunca rompía un bloque.** Verificado que ese método existe con la misma firma en 26.1.

- [ ] **Paso 1: escribir el paquete**

Sigue el patrón de `RiderActionServerPacket`, pero al revés (`Side.CLIENT` y `executeClient`).

```java
package net.darkblade.smop.network.packet;

import net.darkblade.deluxelib.network.AbstractNetworkPacket;
import net.darkblade.deluxelib.network.ClientPacketContext;
import net.darkblade.deluxelib.network.ExtendedFriendlyByteBuf;
import net.darkblade.deluxelib.network.PacketSide;
import net.darkblade.deluxelib.network.Side;
import net.darkblade.smop.client.gt.GroundCrackFx;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

/**
 * Servidor → cliente: el Grand Tyrant acaba de pisar aquí.
 *
 * <p>Viaja el centro y el radio, no la lista de bloques: cuáles se agrietan lo decide el cliente. Es
 * un efecto cosmético y nadie tiene que ver exactamente los mismos, así que mandar hasta 48
 * posiciones tres veces por pisotón sería pagar ancho de banda por un acuerdo que no hace falta.
 */
@PacketSide(side = Side.CLIENT)
public final class StompCrackFxClientPacket extends AbstractNetworkPacket<StompCrackFxClientPacket> {

    private int cx;
    private int cy;
    private int cz;
    private int radius;

    /** Lo exige el decodificador; los campos los rellena {@link #read}. */
    public StompCrackFxClientPacket() {}

    public StompCrackFxClientPacket(@NotNull BlockPos center, int radius) {
        this.cx = center.getX();
        this.cy = center.getY();
        this.cz = center.getZ();
        this.radius = radius;
    }

    @Override
    protected void read(@NotNull ExtendedFriendlyByteBuf buf) {
        this.cx = buf.readInt();
        this.cy = buf.readInt();
        this.cz = buf.readInt();
        this.radius = buf.readVarInt();
    }

    @Override
    protected void write(@NotNull ExtendedFriendlyByteBuf buf) {
        buf.writeInt(this.cx);
        buf.writeInt(this.cy);
        buf.writeInt(this.cz);
        buf.writeVarInt(this.radius);
    }

    @Override
    protected void executeClient(@NotNull ClientPacketContext context) {
        GroundCrackFx.stomp(new BlockPos(this.cx, this.cy, this.cz), this.radius);
    }
}
```

- [ ] **Paso 2: escribir el efecto de cliente**

```java
package net.darkblade.smop.client.gt;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Grietas cosméticas en el suelo bajo el pisotón. <b>No rompe nada</b>: pinta la textura de rotura de
 * vanilla y la retira sola.
 *
 * <p>Portado del {@code CrackFX} de 1.20.1, con dos cambios deliberados. Uno: el radio lo manda el
 * pisotón, y en el port ese radio es 8 y no el 5 del legacy, porque el anillo de partículas ya se
 * dibuja en el radio de daño real para que sea el aviso de dónde cae el golpe — una grieta más
 * estrecha contaría una mentira sobre dónde duele. Dos: el pisotón son TRES impactos, y cada uno
 * PROFUNDIZA las grietas que ya hay en vez de repintarlas, así que el suelo cede progresivamente
 * mientras dura, que es justo lo que se está mirando.
 */
@EventBusSubscriber(modid = SMOP.MOD_ID, value = Dist.CLIENT)
public final class GroundCrackFx {

    /** Fuera de 1..9 el renderer ignora la petición; 9 es la última etapa antes de la rotura. */
    private static final int MAX_STAGE = 9;
    private static final int MIN_START_STAGE = 5;
    private static final int MAX_START_STAGE = 8;

    /** Ticks que dura una grieta antes de borrarse. El legacy usaba 22 y se lee bien. */
    private static final int TTL_TICKS = 22;

    /** De cada 10 celdas del disco, se agrietan ~3. Denso queda a barro, y ralo no se lee. */
    private static final int SKIP_UNDER = 7;

    /** Techo por impacto, para que un radio grande no dispare el coste de golpe. */
    private static final int MAX_PER_IMPACT = 48;

    /** Cuánto se busca suelo hacia abajo desde la altura del bicho. */
    private static final int GROUND_SEARCH_DOWN = 6;

    private record Crack(int id, int ttl, int stage) {}

    private static final Map<BlockPos, Crack> ACTIVE = new HashMap<>();

    /**
     * Un impacto: elige celdas dentro del DISCO de radio {@code radius} — disco y no cuadrado, porque
     * la caja de daño del pisotón es un sector de 360 grados, o sea un cilindro — y agrieta el suelo
     * que encuentre bajo cada una.
     */
    public static void stomp(@NotNull BlockPos center, int radius) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        int spawned = 0;
        for (int dx = -radius; dx <= radius && spawned < MAX_PER_IMPACT; dx++) {
            for (int dz = -radius; dz <= radius && spawned < MAX_PER_IMPACT; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                if (level.random.nextInt(10) < SKIP_UNDER) {
                    continue;
                }
                BlockPos ground = findGround(level, center.offset(dx, 0, dz));
                if (ground == null) {
                    continue;
                }
                BlockState state = level.getBlockState(ground);
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state),
                        ground.getX() + 0.5D, ground.getY() + 1.0D, ground.getZ() + 0.5D,
                        0.0D, 0.05D, 0.0D);
                add(ground, TTL_TICKS,
                        MIN_START_STAGE + level.random.nextInt(MAX_START_STAGE - MIN_START_STAGE + 1));
                spawned++;
            }
        }
    }

    /**
     * Añade una grieta, o profundiza la que ya hubiera ahí y le renueva el plazo. Profundizar es lo
     * que encadena los tres impactos del pisotón en un solo gesto.
     */
    public static void add(@NotNull BlockPos pos, int ttl, int stage) {
        LevelRenderer renderer = Minecraft.getInstance().levelRenderer;
        BlockPos key = pos.immutable();
        Crack existing = ACTIVE.get(key);
        int id = existing != null ? existing.id() : stableId(key);
        int finalStage = existing != null
                ? Math.min(MAX_STAGE, existing.stage() + 1)
                : Math.min(MAX_STAGE, stage);
        ACTIVE.put(key, new Crack(id, Math.max(1, ttl), finalStage));
        renderer.destroyBlockProgress(id, key, finalStage);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.@NotNull Post event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        LevelRenderer renderer = Minecraft.getInstance().levelRenderer;
        Iterator<Map.Entry<BlockPos, Crack>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Crack> entry = it.next();
            Crack crack = entry.getValue();
            int left = crack.ttl() - 1;
            if (left <= 0) {
                // -1 es como el renderer borra una entrada de rotura. Sin esto la grieta se queda
                // pintada para siempre, que es el modo de fallo feo de este efecto.
                renderer.destroyBlockProgress(crack.id(), entry.getKey(), -1);
                it.remove();
                continue;
            }
            entry.setValue(new Crack(crack.id(), left, crack.stage()));
        }
    }

    /**
     * Primer bloque con cara superior sólida, bajando. Sin esto, un pisotón al borde de un barranco
     * pintaría grietas flotando en el aire.
     */
    private static BlockPos findGround(@NotNull ClientLevel level, @NotNull BlockPos start) {
        BlockPos pos = start;
        for (int d = 0; d <= GROUND_SEARCH_DOWN; d++) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && state.isFaceSturdy(level, pos, Direction.UP)) {
                return pos.immutable();
            }
            pos = pos.below();
        }
        return null;
    }

    /**
     * El renderer indexa las roturas por id de "quien rompe". Derivarlo de la posición con un hash
     * estable es lo que impide que dos grietas se pisen la entrada, y que una se quede sin borrar.
     */
    private static int stableId(@NotNull BlockPos pos) {
        long h = pos.asLong() ^ 0x9E3779B97F4A7C15L;
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= (h >>> 33);
        return (int) (h & 0x7fffffffL);
    }

    private GroundCrackFx() {
        throw new UnsupportedOperationException("Clase de utilidad");
    }
}
```

Necesita `import net.darkblade.smop.SMOP;` para el `modid`. `ClientTickEvent.Post` con `@SubscribeEvent` sobre un método estático es el patrón verificado en 26.1: mira `PerchClient` y `PossessionClient` en DeluxeLib, que hacen exactamente esto.

- [ ] **Paso 3: registrar el paquete**

En `SMOPNetwork.register`, junto al que ya hay:

```java
INSTANCE.regPacket(RiderActionServerPacket.class);
INSTANCE.regPacket(StompCrackFxClientPacket.class);
```

- [ ] **Paso 4: dispararlo desde el pisotón**

En `GTEntity#onStompImpact()`, después de las dos llamadas a `ParticleFx` que ya hay:

```java
        // Las grietas van a los jugadores del nivel y no por distancia: el efecto es de cliente y se
        // autolimita solo, porque un jugador lejos ni tiene el chunk ni ve el pisotón.
        SMOPNetwork.INSTANCE.sendToPlayersInLevel(serverLevel,
                new StompCrackFxClientPacket(this.blockPosition(), (int) STOMP_RADIUS));
```

- [ ] **Paso 5: compilar**

```bash
./gradlew compileJava
```

- [ ] **Paso 6: commit**

```bash
git add src/main/java/net/darkblade/smop/network src/main/java/net/darkblade/smop/client/gt/GroundCrackFx.java src/main/java/net/darkblade/smop/entity/gt/GTEntity.java
git commit -m "GT: el pisotón vuelve a agrietar el suelo"
```

---

## Tarea A2: Pisadas

**Ficheros:**
- Modificar: `src/main/java/net/darkblade/smop/entity/gt/GTEntity.java` (en `registerAnimations()` y un método nuevo)

**Interfaces:**
- Consume: `BaseAnimation#onFrame(int atTick, Consumer<LivingEntity>)`, `ParticleFx.burst`, `ScreenShake.forPlayer`.
- Produce: `GTEntity#onFootfall(boolean leftFoot, float amplitude)`, privado.

**Contexto que necesitas:** el GT es **bípedo**. Los `arms` son los brazos cortos; sólo hay dos patas y todas sus pisadas son de pata trasera, así que la contención del mareo va por amplitud y radio, no eligiendo patas.

Los frames están **medidos sobre el clip**, no estimados. Ninguna pata anima posición: la marcha va entera en la rotación X del muslo, y el ciclo tiene un tramo lento (el pie clavado, el cuerpo pasando por encima: apoyo) y uno rápido que cubre el mismo recorrido (vuelo). El contacto es la transición rápido→lento, y las dos patas van desfasadas media fase exacta, que es la comprobación de que la lectura es correcta.

| Clip | Ciclo | Izquierda | Derecha |
|---|---|---|---|
| `walk` | 60 ticks | **7** | **37** |
| `sprint` | 20 ticks | **3** | **13** |

- [ ] **Paso 1: constantes**

Junto a las del pisotón en `GTEntity`:

```java
    /** Frames de contacto, medidos sobre los propios clips. @see #onFootfall */
    private static final int WALK_LEFT_FOOTFALL = 7;
    private static final int WALK_RIGHT_FOOTFALL = 37;
    private static final int SPRINT_LEFT_FOOTFALL = 3;
    private static final int SPRINT_RIGHT_FOOTFALL = 13;

    /**
     * Amplitudes muy por debajo del 0.5 del pisotón, y no por timidez: {@code sprint} pisa cada 10
     * ticks, o sea DOS VECES POR SEGUNDO mientras te persigue. Con la amplitud del pisotón eso es
     * insoportable de mirar.
     */
    private static final float WALK_SHAKE_AMPLITUDE = 0.12F;
    private static final float SPRINT_SHAKE_AMPLITUDE = 0.18F;
    /** Corta: 10 bloques con caída lineal, así que sólo se siente cuando lo tienes encima. */
    private static final double FOOTFALL_SHAKE_RADIUS = 10.0D;
    private static final int FOOTFALL_SHAKE_TICKS = 3;
    /** Medio ancho entre patas, para que el polvo salga bajo el pie que toca y no bajo el centro. */
    private static final double FOOTFALL_LATERAL_OFFSET = 1.2D;
    private static final int FOOTFALL_DUST_COUNT = 6;
```

- [ ] **Paso 2: el método del impacto**

```java
    /**
     * Una pisada: polvo bajo el pie que toca y una sacudida corta para quien esté cerca.
     *
     * <p>El polvo va SIEMPRE y la sacudida sólo de cerca, y esa asimetría es deliberada: el polvo es
     * información a distancia —ves de lejos que algo pesado se acerca— y no marea a nadie, mientras
     * que la sacudida a dos pisadas por segundo sí.
     */
    private void onFootfall(boolean leftFoot, float amplitude) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        double yaw = Math.toRadians(this.yBodyRot);
        double side = leftFoot ? FOOTFALL_LATERAL_OFFSET : -FOOTFALL_LATERAL_OFFSET;
        Vec3 foot = new Vec3(
                this.getX() + Math.cos(yaw) * side,
                this.getY(),
                this.getZ() + Math.sin(yaw) * side);

        ParticleOptions debris = new BlockParticleOption(ParticleTypes.BLOCK, this.getBlockStateOn());
        ParticleFx.burst(serverLevel, debris, foot, FOOTFALL_DUST_COUNT, 0.35D, 0.02D);

        for (ServerPlayer player : serverLevel.players()) {
            double distance = player.position().distanceTo(foot);
            if (distance > FOOTFALL_SHAKE_RADIUS) {
                continue;
            }
            float strength = (float) (amplitude * (1.0D - distance / FOOTFALL_SHAKE_RADIUS));
            ScreenShake.forPlayer(player)
                    .duration(FOOTFALL_SHAKE_TICKS)
                    .fadeOut(2)
                    .frequency(14.0F)
                    .amplitude(strength)
                    .seed(this.getId())
                    .fire();
        }
    }
```

- [ ] **Paso 3: colgarlo de los frames**

En `registerAnimations()`, justo después de `walk.blendInMs(700).blendOutMs(400);` y de la creación de `sprint`:

```java
        walk.onFrame(WALK_LEFT_FOOTFALL, entity -> this.onFootfall(true, WALK_SHAKE_AMPLITUDE));
        walk.onFrame(WALK_RIGHT_FOOTFALL, entity -> this.onFootfall(false, WALK_SHAKE_AMPLITUDE));
        sprint.onFrame(SPRINT_LEFT_FOOTFALL, entity -> this.onFootfall(true, SPRINT_SHAKE_AMPLITUDE));
        sprint.onFrame(SPRINT_RIGHT_FOOTFALL, entity -> this.onFootfall(false, SPRINT_SHAKE_AMPLITUDE));
```

- [ ] **Paso 4: compilar**

```bash
./gradlew compileJava
```

- [ ] **Paso 5: commit**

```bash
git add src/main/java/net/darkblade/smop/entity/gt/GTEntity.java
git commit -m "GT: polvo y sacudida en cada pisada"
```

---

## Tarea A3: La columna se enrosca al girar

**Ficheros:**
- Modificar: `src/main/java/net/darkblade/smop/client/gt/GTModel.java`

**Interfaces:**
- Consume: `Rig.Builder#turnLean(TurnLeanAdditive<M>)`, `TurnLeanAdditive.builder()` con `springFrequency(float hz)`, `springDamping(float ratio)`, `gapDeadzone(float degrees)`, `coil(Function<M,ModelPart>, float factor, float maxDegrees)`, `lag(...)`, `bank(...)`, `build()`.
- Produce: campos públicos `body_parts`, `head`, `eyes`, `tail1`, `tail2`, `tail3` en `GTModel`, que la tarea A4 también usa.

**Contexto que necesitas:** `TurnLeanAdditive` lee el **gap de rumbo** —el ángulo entre a dónde apunta la dirección y a dónde apunta el cuerpo visible— y lo pasa por un muelle amortiguado. Encaja en este mob mejor que en ningún otro porque el GT gira a 5°/tick (`TURN_SPEED`), así que un viraje cerrado dura más de un segundo y el gap se queda abierto todo ese rato; en un mob que gira rápido sería un pico que el suavizado se come.

Jerarquía real del rig, leída del `createBodyLayer`: `GT` → `body_parts` → {`neck` → `head` → {`eyes`, `muscles`}, `tail1` → `tail2` → `tail3`}.

- [ ] **Paso 1: exponer los huesos**

En `GTModel`, junto a `root` y `neck`:

```java
    public final ModelPart root;
    public final ModelPart body_parts;
    public final ModelPart neck;
    public final ModelPart head;
    public final ModelPart eyes;
    public final ModelPart tail1;
    public final ModelPart tail2;
    public final ModelPart tail3;

    public GTModel(ModelPart root) {
        super(root);
        this.root = root.getChild("GT");
        this.body_parts = this.root.getChild("body_parts");
        this.neck = this.body_parts.getChild("neck");
        this.head = this.neck.getChild("head");
        this.eyes = this.head.getChild("eyes");
        this.tail1 = this.body_parts.getChild("tail1");
        this.tail2 = this.tail1.getChild("tail2");
        this.tail3 = this.tail2.getChild("tail3");
    }
```

- [ ] **Paso 2: montar el componente**

En el `RIG`, después de `.lookAt(...)`:

```java
                    .turnLean(TurnLeanAdditive.<GTModel>builder()
                            // Muelle lento y bastante amortiguado: es un animal pesado, no un látigo.
                            .springFrequency(1.2F)
                            .springDamping(0.6F)
                            // Sin zona muerta, caminar recto le haría vibrar la columna con el ruido
                            // de un grado de gap que siempre hay.
                            .gapDeadzone(5.0F)
                            .coil(m -> m.body_parts, 0.25F, 12.0F)
                            .coil(m -> m.neck, 0.35F, 18.0F)
                            // La cola llega TARDE, y con factor descendente: cada eslabón se queda
                            // más atrás que el anterior. Eso es lo que la hace pesar.
                            .lag(m -> m.tail1, 0.50F, 25.0F)
                            .lag(m -> m.tail2, 0.35F, 18.0F)
                            .lag(m -> m.tail3, 0.20F, 12.0F)
                            .bank(m -> m.body_parts, 0.20F, 10.0F)
                            .build())
```

- [ ] **Paso 3: compilar**

```bash
./gradlew compileJava
```

- [ ] **Paso 4: commit**

```bash
git add src/main/java/net/darkblade/smop/client/gt/GTModel.java
git commit -m "GT: la columna se enrosca hacia el giro y la cola llega tarde"
```

---

## Tarea A4: Cabeceo y parpadeo

**Ficheros:**
- Modificar: `src/main/java/net/darkblade/smop/client/gt/GTModel.java`

**Interfaces:**
- Consume: los campos de `GTModel` de la tarea A3; `Rig.Builder#idleHead(Function<M,ModelPart>, Predicate<DeluxeEntityRenderState>)` y `#idleBlink(Predicate<DeluxeEntityRenderState>, Function<M,ModelPart>...)`; `MobAnimator#getCurrent(int layer)`.
- Produce: nada que otras tareas usen.

**Contexto que necesitas:** dos reglas que no son opcionales.

**El cabeceo va sobre `head`, no sobre `neck`.** El `lookAt` del rig ya conduce el cuello, y lo hace a propósito: en un animal de cuello largo, girar sólo el cráneo lo desprende del cuerpo. Poner los dos sobre el mismo hueso los haría pelearse.

**El parpadeo se capa durante el sueño.** `sit`, `sleep` y `alert_snore` animan los tres la escala del hueso `eyes` — comprobado, no supuesto. El componente está pensado para estados donde ningún clip toca los ojos, y su propia documentación lo advierte.

La compuerta se lee del animador, que es lo que el estado de render lleva encima (`DeluxeEntityRenderState.animator`), y no de un campo del estado que habría que inventar.

- [ ] **Paso 1: la compuerta**

En `GTModel`:

```java
    /**
     * Nombres de los clips del ciclo de sueño. Se consultan por nombre porque el modelo no ve la
     * entidad: lo único que llega al render es el animador.
     */
    private static final Set<String> SLEEP_CLIPS = Set.of(
            "sitting", "sit", "preparing_sleep", "sleep", "alert_snore",
            "awakening", "alt_awakening", "standing_up");

    /** Qué clip manda ahora mismo en la capa 0, o cadena vacía si no hay ninguno. */
    private static String currentClip(@NotNull DeluxeEntityRenderState state) {
        if (state.animator == null) {
            return "";
        }
        return state.animator.getCurrent(0).getName();
    }

    /** El animal está tumbándose, tumbado o levantándose: ningún gesto de reposo pinta nada aquí. */
    private static boolean asleep(@NotNull DeluxeEntityRenderState state) {
        return SLEEP_CLIPS.contains(currentClip(state));
    }
```

- [ ] **Paso 2: montar los dos componentes**

En el `RIG`, después del `turnLean`:

```java
                    // Sólo en el idle, y por nombre de clip: es un gesto de reposo, y mientras camina
                    // o ataca la cabeza ya tiene quien la mueva.
                    .idleHead(m -> m.head, state -> "idle".equals(currentClip(state)))
                    .idleBlink(state -> !asleep(state), m -> m.eyes)
```

- [ ] **Paso 3: compilar**

```bash
./gradlew compileJava
```

- [ ] **Paso 4: commit**

```bash
git add src/main/java/net/darkblade/smop/client/gt/GTModel.java
git commit -m "GT: cabeceo en reposo y parpadeo, capados durante el sueño"
```

---

## Tarea A5: Verificación práctica del módulo A

**Ficheros:** ninguno. Es una sesión de juego.

- [ ] **Paso 1: arrancar el cliente**

```bash
./gradlew runClient
```

- [ ] **Paso 2: recorrer la lista**

Invoca un GT (`/summon smop:gt`) y comprueba una por una:

- El pisotón agrieta el suelo hasta el borde del radio de daño, las grietas **profundizan** durante los tres impactos y **desaparecen solas** en poco más de un segundo.
- Rompe uno de los bloques agrietados con la mano: se comporta como un bloque intacto. **Ningún bloque quedó dañado de verdad.**
- Pisa junto a un barranco: no hay grietas flotando en el aire.
- Caminando levanta polvo bajo cada pie, alternando lado, y la sacudida sólo se siente de cerca.
- Persiguiéndote a la carrera, la sacudida **no marea**. Si marea, baja `SPRINT_SHAKE_AMPLITUDE` antes de seguir; es el número que el spec marcó como primero a afinar.
- Girando en el sitio: el cuello se enrosca hacia el giro y la cola llega tarde; al terminar rebota y se asienta. Caminando recto **nada vibra**.
- Quieto: mueve la cabeza de vez en cuando y parpadea.
- Dormido: **no parpadea** y **no cabecea**.

- [ ] **Paso 3: anotar los números que hayas tenido que tocar**

Si cambiaste alguna amplitud o radio, actualiza el comentario que lo justifica en `GTEntity` con el valor real y por qué.

- [ ] **Paso 4: commit si hubo ajustes**

```bash
git add -A && git commit -m "GT: afinar el peso físico tras probarlo en juego"
```

---

# Módulo B · El sueño del GT

## Tarea B1: Herramienta de auditoría de costuras

**Ficheros:**
- Crear: `docs/superpowers/tools/seam_audit.py`

**Interfaces:**
- Consume: nada.
- Produce: `python docs/superpowers/tools/seam_audit.py <fichero.java> seam <clipA> <tA> <clipB> <tB>` y `... speed <fichero.java> <clip> <hueso> <canal>`. Las tareas B2 y B7 lo usan.

**Contexto que necesitas:** el spec exige comprobación numérica antes de juego, y esta es la tercera vez que hace falta. Se guarda en el repo para que la siguiente no empiece de cero.

- [ ] **Paso 1: escribir la herramienta**

```python
"""Mide costuras entre clips de animacion y la velocidad de sus curvas.

    python seam_audit.py <fichero.java> seam  <clipA> <tA> <clipB> <tB>
    python seam_audit.py <fichero.java> speed <clip> <hueso> <ROTATION|POSITION|SCALE>

La costura es la diferencia de pose entre el final de un clip y el principio del
siguiente, canal por canal, contando tambien los canales que solo existen en uno
de los dos (los que faltan caen a la pose de reposo). 0.00 = costura limpia.
"""
import re
import sys

KEYFRAME = re.compile(
    r'new Keyframe\(([-\d.]+)F,\s*KeyframeAnimations\.\w+\('
    r'([-\d.eE]+)F,\s*([-\d.eE]+)F,\s*([-\d.eE]+)F\),\s*'
    r'AnimationChannel\.Interpolations\.(\w+)\)')
CHANNEL = re.compile(
    r'\.addAnimation\("([^"]+)",\s*new AnimationChannel\('
    r'AnimationChannel\.Targets\.(\w+),(.*?)\n\s*\)\)', re.S)
HEADER = re.compile(
    r'public static final AnimationDefinition (\w+) = '
    r'AnimationDefinition\.Builder\.withLength\(([\d.]+)F\)(\.looping\(\))?')


def parse(path):
    src = open(path, encoding="utf-8").read()
    marks = [(m.start(), m.group(1), float(m.group(2)), bool(m.group(3)))
             for m in HEADER.finditer(src)]
    defs = {}
    for i, (pos, name, length, looping) in enumerate(marks):
        body = src[pos:marks[i + 1][0] if i + 1 < len(marks) else len(src)]
        chans = {}
        for cm in CHANNEL.finditer(body):
            chans[(cm.group(1), cm.group(2))] = [
                (float(k.group(1)),
                 (float(k.group(2)), float(k.group(3)), float(k.group(4))),
                 k.group(5))
                for k in KEYFRAME.finditer(cm.group(3))]
        defs[name] = dict(length=length, looping=looping, chans=chans)
    return defs


def rest(target):
    return (1.0, 1.0, 1.0) if target == "SCALE" else (0.0, 0.0, 0.0)


def catmullrom(d, p0, p1, p2, p3):
    return 0.5 * (2 * p1 + (p2 - p0) * d
                  + (2 * p0 - 5 * p1 + 4 * p2 - p3) * d * d
                  + (3 * p1 - 3 * p2 + p3 - p0) * d * d * d)


def sample(frames, t):
    """Muestreo como el de vanilla: extremos clavados en su keyframe."""
    if not frames:
        return None
    if t <= frames[0][0]:
        return frames[0][1]
    if t >= frames[-1][0]:
        return frames[-1][1]
    j = max(i for i in range(len(frames)) if frames[i][0] <= t)
    k = min(len(frames) - 1, j + 1)
    span = frames[k][0] - frames[j][0]
    d = (t - frames[j][0]) / span if span > 0 else 0.0
    if frames[k][2] == "LINEAR":
        return tuple(frames[j][1][i] + (frames[k][1][i] - frames[j][1][i]) * d
                     for i in range(3))
    p0 = frames[max(0, j - 1)][1]
    p3 = frames[min(len(frames) - 1, k + 1)][1]
    return tuple(catmullrom(d, p0[i], frames[j][1][i], frames[k][1][i], p3[i])
                 for i in range(3))


def pose(defs, name, t):
    d = defs[name]
    tt = (t % d["length"]) if d["looping"] else min(t, d["length"])
    return {k: sample(f, tt) for k, f in d["chans"].items()}


def seam(defs, a, ta, b, tb):
    pa, pb = pose(defs, a, ta), pose(defs, b, tb)
    keys = sorted(set(defs[a]["chans"]) | set(defs[b]["chans"]))
    dirty = []
    for k in keys:
        va = pa.get(k) or rest(k[1])
        vb = pb.get(k) or rest(k[1])
        d = max(abs(va[i] - vb[i]) for i in range(3))
        if d > 0.01:
            dirty.append((d, k, va, vb))
    dirty.sort(reverse=True)
    print("%s@%s -> %s@%s   (%d canales en la union)"
          % (a, ta, b, tb, len(keys)))
    if not dirty:
        print("  COSTURA LIMPIA")
        return 0
    for d, k, va, vb in dirty:
        print("  %8.3f  %-16s %-9s %s -> %s"
              % (d, k[0], k[1],
                 tuple(round(x, 3) for x in va), tuple(round(x, 3) for x in vb)))
    return len(dirty)


def speed(defs, clip, bone, target):
    frames = defs[clip]["chans"][(bone, target)]
    length = defs[clip]["length"]
    print("%s.%s %s  (%ss)" % (clip, bone, target, length))
    prev, mx, mxt = None, 0.0, 0.0
    for tick in range(int(round(length * 20)) + 1):
        v = sample(frames, tick / 20.0)
        if prev is not None:
            d = max(abs(v[i] - prev[i]) for i in range(3))
            if d > mx:
                mx, mxt = d, tick / 20.0
        prev = v
    print("  velocidad maxima %.2f/tick en t=%.2fs" % (mx, mxt))


if __name__ == "__main__":
    defs = parse(sys.argv[1])
    if sys.argv[2] == "seam":
        sys.exit(1 if seam(defs, sys.argv[3], float(sys.argv[4]),
                           sys.argv[5], float(sys.argv[6])) else 0)
    speed(defs, sys.argv[3], sys.argv[4], sys.argv[5])
```

- [ ] **Paso 2: comprobar que reproduce lo que ya sabemos**

Las seis costuras del ciclo se dejaron limpias hace dos commits. La herramienta tiene que confirmarlo:

```bash
python docs/superpowers/tools/seam_audit.py src/main/java/net/darkblade/smop/client/gt/GTAnimations.java seam sit 4.4 sleep_preparing 0.0
```

Esperado: `COSTURA LIMPIA`. Si sale sucia, el fallo está en la herramienta, no en los datos — arréglala antes de seguir.

- [ ] **Paso 3: commit**

```bash
git add docs/superpowers/tools/seam_audit.py
git commit -m "Herramienta: auditoría de costuras y velocidad de curvas"
```

---

## Tarea B2: Registrar `alert_snore` y `alt_awakening`, y limpiar sus costuras

**Ficheros:**
- Modificar: `src/main/java/net/darkblade/smop/client/gt/GTAnimations.java`
- Modificar: `src/main/java/net/darkblade/smop/entity/gt/GTEntity.java` (`registerAnimations()`)

**Interfaces:**
- Consume: la herramienta de B1.
- Produce: los clips `"alert_snore"` (60 ticks) y `"alt_awakening"` (84 ticks) registrados en el animador, en capa 0 y prioridad 1. Las tareas B6 y B7 los reproducen por nombre.

**Contexto que necesitas:** los dos llevan autorados desde el port sin que nadie los registre. `alert_snore` ya está limpio —medido: 0.00 al entrar desde `sleep` y 0.00 al volver, está autorado como interludio dentro del sueño—, así que sólo hay que registrarlo. `alt_awakening` no:

| Costura | Desajuste | Canal peor | Canales sucios |
|---|---|---|---|
| `sleep` (costura del loop) → `alt_awakening` | 7.37 | `head` POSITION | 1 |
| `alt_awakening` → `sit` | 5.62 | `muscles` ROTATION | 2 |
| `alt_awakening` → `standing_up` | 5.62 | `muscles` ROTATION | 2 |

Son **dos arreglos, no tres**: la segunda y la tercera dan el mismo número en el mismo canal porque la pasada anterior ya dejó el primer frame de `standing_up` igual al de `sit`. Corregir el final de `alt_awakening` contra la pose del loop cierra las dos salidas de una vez.

- [ ] **Paso 1: medir el estado de partida**

```bash
python docs/superpowers/tools/seam_audit.py src/main/java/net/darkblade/smop/client/gt/GTAnimations.java seam sleep 0.0 alt_awakening 0.0
python docs/superpowers/tools/seam_audit.py src/main/java/net/darkblade/smop/client/gt/GTAnimations.java seam alt_awakening 4.2 sit 0.0
```

Esperado, exactamente esto (ya está medido; si te sale otra cosa, alguien tocó los clips desde que se escribió este plan y hay que volver a mirarlo todo):

| Costura | Canal | `alt_awakening` tiene | Tiene que valer |
|---|---|---|---|
| entrada | `head` POSITION | `(5.1573, -0.3926, 7.3654)` | **`(0, 0, 0)`** — lo que sostiene `sleep` |
| salida | `arms` ROTATION | `(0, 0, 0)` | **`(-5, 0, 0)`** — lo que sostiene `sit` |
| salida | `muscles` ROTATION | `(5.625, 0, 0)` | **`(0, 0, 0)`** — `sit` no anima ese hueso, así que su pose es la de reposo |

Los dos de salida son **el mismo fallo de familia** que ya se corrigió en los otros cuatro clips: los uno-shot acaban con `arms` en 0 mientras los loops sostienen −5.

- [ ] **Paso 2: medir la velocidad de las curvas ANTES de tocarlas**

Para cada canal sucio que te haya listado el paso 1:

```bash
python docs/superpowers/tools/seam_audit.py src/main/java/net/darkblade/smop/client/gt/GTAnimations.java speed alt_awakening head POSITION
python docs/superpowers/tools/seam_audit.py src/main/java/net/darkblade/smop/client/gt/GTAnimations.java speed alt_awakening muscles ROTATION
```

Apunta las velocidades máximas. Son la referencia contra la que se comprueba que el arreglo no mete un latigazo.

- [ ] **Paso 3: corregir la entrada**

En el clip `alt_awakening` de `GTAnimations.java`, el **primer** keyframe (t=0.0) del canal `head` POSITION pasa de `posVec(5.1573F, -0.3926F, 7.3654F)` a `posVec(0.0F, 0.0F, 0.0F)`.

Comprueba antes cuántos keyframes tiene ese canal: si el segundo está muy cerca en el tiempo, mover el primero 7.37 unidades mete un latigazo, y el paso 6 te lo va a cazar. En ese caso reparte la corrección también sobre el segundo, como se hizo con el canal `head` de `awakening`.

Escribe encima un comentario que diga qué pose es y por qué, en el estilo del que ya hay en el canal `head` de `awakening`.

- [ ] **Paso 4: corregir la salida**

El **último** keyframe (t=4.2) de los otros dos canales:

- `arms` ROTATION: de `degreeVec(0.0F, 0.0F, 0.0F)` a `degreeVec(-5.0F, 0.0F, 0.0F)`.
- `muscles` ROTATION: de `degreeVec(5.625F, 0.0F, 0.0F)` a `degreeVec(0.0F, 0.0F, 0.0F)`.

- [ ] **Paso 5: comprobar que las tres costuras quedan limpias**

```bash
python docs/superpowers/tools/seam_audit.py src/main/java/net/darkblade/smop/client/gt/GTAnimations.java seam sleep 0.0 alt_awakening 0.0
python docs/superpowers/tools/seam_audit.py src/main/java/net/darkblade/smop/client/gt/GTAnimations.java seam alt_awakening 4.2 sit 0.0
python docs/superpowers/tools/seam_audit.py src/main/java/net/darkblade/smop/client/gt/GTAnimations.java seam alt_awakening 4.2 standing_up 0.0
```

Esperado: `COSTURA LIMPIA` en las tres.

- [ ] **Paso 6: comprobar que ninguna curva se disparó**

Repite los comandos `speed` del paso 2. Si alguna velocidad máxima ha subido más de un 60% sobre la de antes, **para**: significa que el keyframe corregido está demasiado lejos de su vecino y hace falta repartir la corrección, no clavarla en el extremo. Anota los pares antes/después en el mensaje del commit.

- [ ] **Paso 7: registrar los dos clips**

En `GTEntity#registerAnimations()`, junto a los otros seis del ciclo:

```java
        // El ronquido NO es una fase: es un interludio dentro de SLEEPING. Medido, entra desde
        // `sleep` y vuelve a `sleep` con 0.00 de desajuste, o sea que puede desplazar al loop y
        // dejar que el arranque automático lo reponga sin que la máquina de fases se entere.
        StandardAnimation alertSnore = new StandardAnimation("alert_snore",
                new AnimSource(() -> GTAnimations.alert_snore), Loop.PLAY_ONCE, 0, 1, 3.0F);
        // El despertar de cuando te descubre. Misma fase que `awakening` y 4 ticks más largo, que es
        // justo lo que sleepPhaseDuration tiene que devolver cuando toca este.
        StandardAnimation altAwakening = new StandardAnimation("alt_awakening",
                new AnimSource(() -> GTAnimations.alt_awakening), Loop.PLAY_ONCE, 0, 1, 4.2F);

        alertSnore.blendInMs(50).blendOutMs(400);
        altAwakening.blendInMs(450).blendOutMs(400);

        alertSnore.setPlayCondition(a -> this.sleepPhase() == SleepPhase.SLEEPING);
        altAwakening.setPlayCondition(a -> this.sleepPhase() == SleepPhase.AWAKENING);
```

y añádelos a la llamada de `register(...)` que ya existe.

Los blends salen de la regla global: `alert_snore` entra por costura limpia y siempre desde el mismo sitio, así que corta a 50 ms. `alt_awakening` hereda la decisión de su gemelo — 450 ms, porque arranca despacio y sigue teniendo un camino sucio posible, el golpe que corta el respiro a mitad.

- [ ] **Paso 8: compilar**

```bash
./gradlew compileJava
```

- [ ] **Paso 9: commit**

```bash
git add src/main/java/net/darkblade/smop/client/gt/GTAnimations.java src/main/java/net/darkblade/smop/entity/gt/GTEntity.java
git commit -m "GT: registrar alert_snore y alt_awakening, y limpiar sus costuras"
```

---

## Tarea B3: Partir `isForced()` en dos preguntas

**Ficheros:**
- Modificar: `src/main/java/net/darkblade/smop/entity/sleep/SleepUrge.java`
- Modificar: `src/main/java/net/darkblade/smop/entity/sleep/SleepGoal.java`

**Interfaces:**
- Consume: nada nuevo.
- Produce: en `SleepUrge` — `public boolean holdsThroughDaylight()`, `public boolean ignoresThreats()`, `public void napUntilDisturbed(boolean value)`, `public boolean isNapping()`. `isForced()` **desaparece**; su único uso vivo está en `SleepGoal`.

**Contexto que necesitas:** hoy `isForced()` responde a dos cosas a la vez, y el GT necesita una sin la otra:

| Pregunta | Poción del hipopótamo | Siesta del GT |
|---|---|---|
| ¿Aguanta con el sol alto? | sí | **sí** |
| ¿Le da igual quién se acerque? | sí | **no** — de eso va todo el módulo |

El Hell Hippo responde que sí a las dos y **tiene que quedar exactamente igual que hoy**.

- [ ] **Paso 1: el estado nuevo en `SleepUrge`**

Junto al campo `forced`:

```java
    /**
     * Una cabezada que ignora el reloj pero NO a quien se acerca. La usa el Grand Tyrant al aparecer
     * en el mundo: un animal de seis bloques al que te encuentras tumbado a mediodía tiene que seguir
     * tumbado, pero enterarse de que estás ahí es justo la gracia.
     *
     * @see #forceSleep(boolean) que sí ignora las dos cosas
     */
    private boolean napping;
```

- [ ] **Paso 2: las dos preguntas**

Sustituye `isForced()` por:

```java
    /**
     * Si este sueño sobrevive al amanecer. Lo cumplen tanto la poción como la cabezada de spawn:
     * ninguno de los dos empezó porque fuera de noche, así que tampoco acaba porque deje de serlo.
     */
    public boolean holdsThroughDaylight() {
        return this.forced || this.napping;
    }

    /**
     * Si además da igual quién esté al lado. <b>Sólo la poción.</b> Separarlo de la pregunta de
     * arriba es lo que permite un sueño que aguanta el sol y aun así te oye llegar.
     */
    public boolean ignoresThreats() {
        return this.forced;
    }

    /** Empieza o termina la cabezada. @see #napping */
    public void napUntilDisturbed(boolean value) {
        this.napping = value;
    }

    public boolean isNapping() {
        return this.napping;
    }
```

- [ ] **Paso 3: que la cabezada también dé ganas de dormir**

En `wantsToSleep()`, la primera línea pasa de `if (this.forced)` a:

```java
        if (this.forced || this.napping) {
            return true;
        }
```

Sin esto, `SleepGoal#canUse()` no arrancaría nunca de día y la cabezada no llegaría a empezar.

- [ ] **Paso 4: los dos usos en `SleepGoal`**

En `canUse()`:

```java
        return this.urge.ignoresThreats() || this.findThreats().isEmpty();
```

En `shouldWakeUp()`, las dos líneas del medio:

```java
        // Un sueño forzado sólo lo termina quien lo forzó, y eso es la petición de arriba.
        if (this.urge.ignoresThreats()) {
            return false;
        }
        // El amanecer despierta, salvo a quien no se durmió por la hora.
        if (!this.urge.isNight() && !this.urge.holdsThroughDaylight()) {
            return true;
        }
```

- [ ] **Paso 5: comprobar que nadie más llamaba a `isForced`**

```bash
grep -rn "isForced" src/main/java/
```

Esperado: **ninguna coincidencia**. Si sale alguna, cámbiala a la de las dos preguntas que corresponda y explica por qué en el commit.

- [ ] **Paso 6: compilar**

```bash
./gradlew compileJava
```

- [ ] **Paso 7: commit**

```bash
git add src/main/java/net/darkblade/smop/entity/sleep/
git commit -m "Sueño: separar 'aguanta el sol' de 'ignora a quien llega'"
```

---

## Tarea B4: Radio de amenaza por mob, y voz del evaluador sobre jugadores

**Ficheros:**
- Modificar: `src/main/java/net/darkblade/smop/entity/sleep/ISleepingEntity.java`
- Modificar: `src/main/java/net/darkblade/smop/entity/sleep/SleepGoal.java`

**Interfaces:**
- Consume: `ISleepThreatEvaluator#shouldInterruptSleepDueTo(LivingEntity)`, que ya existe.
- Produce: `ISleepingEntity#getSleepThreatRadius()` con `4.0D` por defecto. La tarea B6 lo sobrescribe en el GT.

**Contexto que necesitas:** dos cosas bloquean el diseño hoy.

Una: el radio es `private static final double THREAT_RADIUS = 4.0D` en el goal, igual para todos. En un animal de seis bloques de alto, cuatro bloques es prácticamente estar tocándolo.

Dos, y es de fontanería: en `isThreat`, si el intruso es un jugador se decide con `ISleepAwareness` y **nunca se llega a consultar `ISleepThreatEvaluator`**. Un mob que quiera afinar por jugador no tiene hoy dónde hacerlo.

- [ ] **Paso 1: el radio, en la interfaz**

En `ISleepingEntity`:

```java
    /**
     * Radio en el que este animal se entera de que hay algo al lado mientras duerme.
     *
     * <p>Cuatro bloques por defecto, que es lo que valía la constante del goal cuando era igual para
     * todos. Un animal grande querrá más — no por oído fino, sino porque cuatro bloques medidos desde
     * su centro caen dentro de su propio cuerpo.
     */
    default double getSleepThreatRadius() {
        return 4.0D;
    }
```

- [ ] **Paso 2: usarlo en el goal**

Borra la constante `THREAT_RADIUS` y en `findThreats()`:

```java
        return this.mob.level().getEntitiesOfClass(LivingEntity.class,
                this.mob.getBoundingBox().inflate(this.mob.getSleepThreatRadius()),
                this::isThreat);
```

- [ ] **Paso 3: dejar opinar al evaluador sobre jugadores**

`isThreat` pasa a:

```java
    private boolean isThreat(LivingEntity nearby) {
        if (nearby == this.mob || !nearby.isAlive()) {
            return false;
        }
        // El evaluador va PRIMERO y también sobre jugadores. Antes el caso del jugador se resolvía
        // entero con ISleepAwareness y el evaluador no llegaba a verlo nunca, así que un mob que
        // quisiera afinar por jugador — de eso vive el sigilo del Grand Tyrant — no tenía dónde.
        // ISleepAwareness sigue siendo el sí/no grueso para quien no necesita más.
        if (this.mob instanceof ISleepThreatEvaluator evaluator) {
            return evaluator.shouldInterruptSleepDueTo(nearby);
        }
        if (nearby instanceof Player player && !player.isSpectator()) {
            return !(this.mob instanceof ISleepAwareness aware) || aware.shouldWakeOnPlayerProximity();
        }
        return this.mob.getInterruptingEntityTypes().contains(nearby.getType());
    }
```

**Ojo con el orden:** ahora un mob que implemente el evaluador decide **todo**, jugadores incluidos. Comprueba quién lo implementa antes de dar el paso por bueno:

```bash
grep -rln "ISleepThreatEvaluator" src/main/java/
```

Si alguno de los que salgan contaba con que los jugadores se resolvían por otro lado, su evaluador tiene que empezar delegando el caso del jugador a la regla de antes. Anótalo en el commit.

- [ ] **Paso 4: compilar**

```bash
./gradlew compileJava
```

- [ ] **Paso 5: commit**

```bash
git add src/main/java/net/darkblade/smop/entity/sleep/
git commit -m "Sueño: radio de amenaza por mob y evaluador con voz sobre jugadores"
```

---

## Tarea B5: La siesta al spawnear

**Ficheros:**
- Modificar: `src/main/java/net/darkblade/smop/entity/sleep/SleepGoal.java`
- Modificar: `src/main/java/net/darkblade/smop/entity/gt/GTEntity.java`

**Interfaces:**
- Consume: `SleepUrge#napUntilDisturbed`, `#isNapping` (B3).
- Produce: en `GTEntity` — `finalizeSpawn` override, `addAdditionalSaveData`/`readAdditionalSaveData` con la clave `"SpawnNap"`.

**Contexto que necesitas:** el GT spawnea como `MobCategory.CREATURE` en llanuras y desierto, o sea **de día y en la generación del mundo**, que es exactamente cuando las reglas de sueño dicen que no. B3 ya arregló el reloj; falta que entre tumbado y que sobreviva a una recarga de chunk.

**La fase de sueño no se persiste, y eso no se toca.** `SMOPAnimal` documenta por qué: restaurarla dejaba al mob congelado dormido para siempre. Lo que se guarda es **el flag de siesta**; al cargar, el goal vuelve a arrancar y reconstruye la fase él solo. Si te ves guardando `SleepPhase` en NBT, estás en el modo de fallo que ese comentario describe.

- [ ] **Paso 1: entrar ya tumbado**

En `SleepGoal#start()`:

```java
    @Override
    public void start() {
        this.leaving = false;
        this.startled = false;
        this.threatScanCooldown = 0;
        // Quien llega dormido ya está tumbado: reproducirle la ceremonia de sentarse sería animar
        // algo que no ocurrió. Es el mismo criterio que beginLeaving aplica al saltarse `awakening`
        // en un mob que nunca llegó a tumbarse.
        this.enter(this.urge.isNapping() ? SleepPhase.SLEEPING : this.firstFrom(ENTRY, 0));
    }
```

- [ ] **Paso 2: que despertarse la termine**

En `SleepGoal#stop()`, junto a los otros reseteos:

```java
        // La siesta es de un solo uso: al primer despertar se apaga para siempre y el animal pasa a
        // sus reglas normales de noche. Si no, volvería a tumbarse en cuanto te fueras y el encuentro
        // dejaría de ser un momento para volverse un bucle.
        this.urge.napUntilDisturbed(false);
```

- [ ] **Paso 3: la tirada al aparecer**

En `GTEntity`:

```java
    /**
     * Con qué probabilidad aparece ya dormido.
     *
     * <p>Setenta de cada cien, y es una decisión de diseño, no un número bonito: encontrarse un animal
     * de este tamaño tiene que dejarte elegir entre rodearlo y despertarlo. Si apareciera despierto
     * casi siempre, la elección la tomaría el spawn por ti.
     */
    private static final float SPAWN_ASLEEP_CHANCE = 0.70F;

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor level,
                                                  @NotNull DifficultyInstance difficulty,
                                                  @NotNull EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData spawnData) {
        if (this.getRandom().nextFloat() < SPAWN_ASLEEP_CHANCE) {
            this.sleepUrge().napUntilDisturbed(true);
        }
        return super.finalizeSpawn(level, difficulty, reason, spawnData);
    }
```

- [ ] **Paso 4: persistirlo**

En `GTEntity`, siguiendo la forma que usa `SMOPAnimal` (26.1 va con `ValueOutput`/`ValueInput`, no con `CompoundTag`):

```java
    /**
     * Se guarda el FLAG de siesta, no la fase de sueño.
     *
     * <p>La fase a propósito no se persiste en ningún mob — {@code SMOPAnimal} documenta que
     * restaurarla dejaba al animal congelado dormido para siempre. Pero si no se guardara nada, un GT
     * dormido al que le recargas el chunk se pondría de pie y la siesta se perdería por mirar para
     * otro lado. Con el flag, el goal vuelve a arrancar al cargar y entra otra vez directo a
     * SLEEPING: parece que nunca se levantó, y la fase la reconstruye él en vez de restaurarse a mano.
     */
    @Override
    protected void addAdditionalSaveData(@NotNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("SpawnNap", this.sleepUrge().isNapping());
    }

    @Override
    protected void readAdditionalSaveData(@NotNull ValueInput input) {
        super.readAdditionalSaveData(input);
        this.sleepUrge().napUntilDisturbed(input.getBooleanOr("SpawnNap", false));
    }
```

- [ ] **Paso 5: esconder la barra mientras duerme**

Busca en `GTEntity` dónde se llama a `this.sleepUrge().tick()` y añade al lado:

```java
            // La barra aparece en el instante en que deja de estar dormido, o sea al arrancar el clip
            // de despertar. Enseñarla antes destriparía el sigilo: la verías desde lejos y sabrías que
            // hay algo antes de tener que mirar.
            this.bossBar.setVisible(this.sleepPhase() != SleepPhase.SLEEPING);
```

- [ ] **Paso 6: compilar**

```bash
./gradlew compileJava
```

- [ ] **Paso 7: commit**

```bash
git add src/main/java/net/darkblade/smop/entity/sleep/SleepGoal.java src/main/java/net/darkblade/smop/entity/gt/GTEntity.java
git commit -m "GT: 70% de aparecer dormido, y la siesta sobrevive a la recarga"
```

---

## Tarea B6: Los dos avisos y el sigilo

**Ficheros:**
- Crear: `src/main/java/net/darkblade/smop/entity/gt/GTSlumberWatch.java`
- Modificar: `src/main/java/net/darkblade/smop/entity/gt/GTEntity.java`

**Interfaces:**
- Consume: `ISleepThreatEvaluator` (con la voz sobre jugadores de B4), `ISleepingEntity#getSleepThreatRadius` (B4), el clip `"alert_snore"` (B2).
- Produce: `GTSlumberWatch` con `enum Reaction { IGNORE, WARN, WAKE }`, `Reaction react(LivingEntity intruder)`, `void tick()`, `void reset()`, `int warnings()`. Sólo la usa `GTEntity`.

**Contexto que necesitas:** tres cosas que se pueden hacer mal.

**Uno.** El escaneo de amenazas corre cada 10 ticks y llama a `isThreat` **por cada entidad de la caja**, y además `canUse()` lo llama también **cuando el mob está despierto**. Contar avisos ahí sin más los gastaría solos. La cuenta sólo puede correr con el animal en `SLEEPING`.

**Dos.** Los avisos **se enfrían con el tiempo**, no se borran al salir del radio. Es lo que deja corregir un acercamiento torpe retirándote, sin permitir farmear avisos entrando y saliendo del borde.

**Tres.** El sigilo usa `isDiscrete()`, que es el "voy agachado para que no me vean" de vanilla —lo mismo que mira el Warden—, y no `isShiftKeyDown()`, que es la tecla cruda.

- [ ] **Paso 1: la clase de la vigilia**

```java
package net.darkblade.smop.entity.gt;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Cuánta paciencia le queda al Grand Tyrant dormido.
 *
 * <p>Vive fuera de {@code GTEntity} por la misma razón que {@code SleepUrge} vive fuera de
 * {@code SleepGoal}: es un reloj con reglas propias, y mezclarlo con las mil líneas de la entidad
 * haría que nadie pudiera leerlo entero de una vez.
 *
 * <p><b>Cuenta, no actúa.</b> Decide qué merece el intruso; quién ronca y quién despierta es cosa de
 * la entidad. Esa separación es lo que permite probar la cuenta sin un mundo cargado.
 */
public final class GTSlumberWatch {

    /** Ronquidos de aviso antes de despertar de verdad. */
    private static final int WARNINGS_ALLOWED = 2;

    /**
     * Tras un aviso, lo que tarda en poder darse el siguiente. Sin esto, un solo acercamiento gastaría
     * los dos avisos en dos escaneos consecutivos y el jugador no vería más que un ronquido doble.
     */
    private static final int GRACE_TICKS = 100;

    /**
     * Lo que tarda en perdonarse UN aviso sin detectar a nadie. Mismo orden que el
     * {@code WOKE_UP_DELAY_TICKS} del sueño, para que las dos esperas se sientan de la misma familia.
     */
    private static final int FORGET_TICKS = 600;

    /** Qué merece el intruso que se acaba de detectar. */
    public enum Reaction {
        /** Ni se ha enterado: va agachado. */
        IGNORE,
        /** Un ronquido de aviso. Sigue dormido. */
        WARN,
        /** Se acabó la paciencia. */
        WAKE
    }

    private int warnings;
    private int graceLeft;
    private int sinceLastNotice;

    /** Una vez por tick, con el animal dormido. Es lo que enfría los avisos. */
    public void tick() {
        if (this.graceLeft > 0) {
            this.graceLeft--;
        }
        if (this.warnings <= 0) {
            return;
        }
        this.sinceLastNotice++;
        if (this.sinceLastNotice >= FORGET_TICKS) {
            this.warnings--;
            this.sinceLastNotice = 0;
        }
    }

    /**
     * Qué hacer con {@code intruder}. <b>Llamar sólo con el animal dormido</b>: el escaneo del goal
     * corre también estando despierto, y contar allí gastaría los avisos sin que nadie los viera.
     */
    public @NotNull Reaction react(@NotNull LivingEntity intruder) {
        if (intruder instanceof Player player) {
            if (player.isSpectator()) {
                return Reaction.IGNORE;
            }
            // isDiscrete y no isShiftKeyDown: es el "voy agachado para que no me vean" de vanilla,
            // el mismo que mira el Warden, y ya contempla los casos raros (montado, arrastrándose).
            if (player.isDiscrete()) {
                return Reaction.IGNORE;
            }
        }
        if (this.warnings >= WARNINGS_ALLOWED) {
            return Reaction.WAKE;
        }
        if (this.graceLeft > 0) {
            return Reaction.IGNORE;
        }
        this.warnings++;
        this.graceLeft = GRACE_TICKS;
        this.sinceLastNotice = 0;
        return Reaction.WARN;
    }

    /** Vuelta a empezar: al despertar y al volver a dormirse. */
    public void reset() {
        this.warnings = 0;
        this.graceLeft = 0;
        this.sinceLastNotice = 0;
    }

    /** Avisos gastados. Lo lee el comando de depuración. */
    public int warnings() {
        return this.warnings;
    }
}
```

- [ ] **Paso 2: engancharla en la entidad**

En `GTEntity`, añade `ISleepThreatEvaluator` a los `implements` y:

```java
    /**
     * Dieciséis bloques, contra los cuatro de todos los demás.
     *
     * <p>No es oído fino: es que cuatro bloques medidos desde el centro de un animal de seis de alto
     * caen dentro de su propio cuerpo, así que la regla común aquí significa "te detecta si le estás
     * tocando". Con dieciséis, rodearlo es una maniobra que se ve venir y se puede decidir.
     */
    private static final double SLEEP_DETECTION_RADIUS = 16.0D;

    private final GTSlumberWatch slumberWatch = new GTSlumberWatch();

    @Override
    public double getSleepThreatRadius() {
        return SLEEP_DETECTION_RADIUS;
    }

    /**
     * La decisión de si esto despierta al animal, y roncar es parte de decidirlo.
     *
     * <p>Sí, tiene efecto: el ronquido sale de aquí. El sitio natural sería un hook aparte, pero este
     * método ES donde el mob decide qué le merece un intruso, y separarlos obligaría a recorrer la
     * caja dos veces para preguntar lo mismo.
     */
    @Override
    public boolean shouldInterruptSleepDueTo(@NotNull LivingEntity nearby) {
        // El escaneo también corre estando despierto — canUse() lo llama para decidir si puede
        // tumbarse — y contar avisos ahí los gastaría sin que nadie los oyera.
        if (this.sleepPhase() != SleepPhase.SLEEPING) {
            return false;
        }
        return switch (this.slumberWatch.react(nearby)) {
            case IGNORE -> false;
            case WARN -> {
                this.animator().play(this.animator().getByName("alert_snore"));
                yield false;
            }
            case WAKE -> true;
        };
    }
```

- [ ] **Paso 3: enfriar los avisos**

Junto a `this.sleepUrge().tick()`:

```java
            if (this.sleepPhase() == SleepPhase.SLEEPING) {
                this.slumberWatch.tick();
            }
```

- [ ] **Paso 4: reiniciarla al despertar**

En `GTEntity#onSleepPhaseBegin`, al principio:

```java
        if (phase == SleepPhase.SLEEPING) {
            this.slumberWatch.reset();
        }
```

- [ ] **Paso 5: compilar**

```bash
./gradlew compileJava
```

- [ ] **Paso 6: commit**

```bash
git add src/main/java/net/darkblade/smop/entity/gt/
git commit -m "GT: dos ronquidos de aviso, y agachado no te detecta"
```

---

## Tarea B7: `alt_awakening` como despertar por jugador

**Ficheros:**
- Modificar: `src/main/java/net/darkblade/smop/entity/gt/GTEntity.java`
- Modificar: `src/main/java/net/darkblade/smop/entity/sleep/SleepGoal.java`

**Interfaces:**
- Consume: el clip `"alt_awakening"` (B2), `GTSlumberWatch` (B6).
- Produce: `GTEntity#wokenByPlayer()`, que el comando de depuración de B8 lee.

**Contexto que necesitas:** la variante **no necesita nada de la librería**. `GTEntity` ya implementa `onSleepPhaseBegin` y `sleepPhaseDuration`, que es donde se elige el clip y se declara su duración — el mismo mecanismo que el Nirasmosaurus usa para sus variantes. La única disciplina es que las dos vayan juntas: **84 ticks para el alternativo, 80 para el normal.**

| Cómo despierta | Clip | Pausa sentado |
|---|---|---|
| Solo, al alba | `awakening` (80) | sí |
| Por un jugador — avisos agotados o un golpe | `alt_awakening` (84) | no |

- [ ] **Paso 1: la bandera**

En `GTEntity`:

```java
    /** Duración de {@code alt_awakening}: 4.2 s. Va emparejada con la elección de clip, abajo. */
    private static final int ALT_AWAKENING_TICKS = 84;

    /**
     * Si el despertar en curso lo causó un jugador. Decide dos cosas a la vez: qué clip suena y si se
     * salta la pausa sentado. No se persiste — sólo vale mientras dura el despertar.
     */
    private boolean wokenByPlayer;

    public boolean wokenByPlayer() {
        return this.wokenByPlayer;
    }
```

- [ ] **Paso 2: levantarla donde se decide despertar**

En `shouldInterruptSleepDueTo` de la tarea B6, la rama `WAKE` pasa a:

```java
            case WAKE -> {
                this.wokenByPlayer = true;
                yield true;
            }
```

Y en el `hurtServer` que ya llama a `this.sleepUrge().requestWake()`, junto a esa llamada:

```java
                // Un golpe también es "te despertó un jugador", y el que más.
                this.wokenByPlayer = true;
```

- [ ] **Paso 3: elegir el clip y su duración**

En `sleepPhaseDuration`, el caso de `AWAKENING`:

```java
            case AWAKENING -> this.wokenByPlayer ? ALT_AWAKENING_TICKS : AWAKENING_TICKS;
```

En `onSleepPhaseBegin`, antes de la llamada genérica:

```java
        if (phase == SleepPhase.AWAKENING && this.wokenByPlayer) {
            this.animator().play(this.animator().getByName("alt_awakening"));
            return;
        }
```

- [ ] **Paso 4: bajarla al terminar el ciclo**

En `onSleepPhaseBegin`, en el caso de `SLEEPING` que la tarea B6 ya añadió:

```java
        if (phase == SleepPhase.SLEEPING) {
            this.slumberWatch.reset();
            this.wokenByPlayer = false;
        }
```

- [ ] **Paso 5: saltarse la pausa sentado**

`SleepGoal#beginLeaving` elige hoy la salida con `this.startled`, que significa "le pegaron". La regla es la misma —quien despierta por un jugador no se gana un momento para sentarse a espabilar— así que se generaliza preguntándole al mob.

En `ISleepingEntity`:

```java
    /**
     * Si este despertar se salta la pausa sentada del camino de vuelta.
     *
     * <p>Falso por defecto, que es lo que hacían todos: sólo un golpe la saltaba, y eso lo sigue
     * decidiendo el goal por su cuenta. Sirve para un mob que tenga MÁS motivos que el golpe para no
     * entretenerse — el Grand Tyrant, al que despiertas por estar encima de él.
     */
    default boolean skipsSittingPauseOnWake() {
        return false;
    }
```

En `SleepGoal#beginLeaving`, la línea que elige el camino:

```java
        this.exitPath = (this.startled || this.mob.skipsSittingPauseOnWake())
                ? EXIT_STARTLED : EXIT_CALM;
```

Y en `GTEntity`:

```java
    @Override
    public boolean skipsSittingPauseOnWake() {
        return this.wokenByPlayer;
    }
```

- [ ] **Paso 6: compilar**

```bash
./gradlew compileJava
```

- [ ] **Paso 7: commit**

```bash
git add src/main/java/net/darkblade/smop/entity/
git commit -m "GT: alt_awakening cuando te descubre, y sin pararse a espabilar"
```

---

## Tarea B8: Comando de depuración

**Ficheros:**
- Crear: `src/main/java/net/darkblade/smop/command/SMOPSleepDebug.java`
- Modificar: `src/main/java/net/darkblade/smop/command/SMOPCommands.java`

**Interfaces:**
- Consume: `GTEntity#sleepPhase()`, `#wokenByPlayer()`, `GTSlumberWatch#warnings()`, `SleepUrge#isNapping()`.
- Produce: `/smop debug sleep` — un volcado del estado de sueño de los GT cercanos.

**Contexto que necesitas:** sin esto, "¿por qué no ha roncado?" se responde mirando fijamente a un dinosaurio. El proyecto ya tiene cinco comandos de este tipo (`SMOPBiteDebug`, `SMOPSwimDebug`…) y todos siguen la misma forma: un `static LiteralArgumentBuilder<CommandSourceStack> build()` colgado en `SMOPCommands`. `SMOPDeathDebug` es el más corto si quieres verlo entero.

- [ ] **Paso 1: exponer lo que el comando necesita leer**

`slumberWatch` y `sleepUrge()` son privados. En `GTEntity`:

```java
    /** Sólo para el comando de depuración. */
    public int sleepWarnings() {
        return this.slumberWatch.warnings();
    }

    /** Sólo para el comando de depuración. */
    public boolean isSpawnNapping() {
        return this.sleepUrge().isNapping();
    }
```

- [ ] **Paso 2: escribir el comando**

```java
package net.darkblade.smop.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.darkblade.smop.entity.gt.GTEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * {@code /smop debug sleep} — vuelca el estado de sueño de los Grand Tyrant cercanos.
 *
 * <p>Existe porque sin él la pregunta "¿por qué no ha roncado?" se responde mirando fijamente a un
 * dinosaurio. Los avisos y el enfriamiento son invisibles por definición: lo único que se ve en juego
 * es que ronca o no ronca.
 */
public final class SMOPSleepDebug {

    /** Lo bastante para cubrir de sobra el radio de detección de 16 del propio bicho. */
    private static final double RADIUS = 64.0D;

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("debug").then(Commands.literal("sleep")
                .executes(ctx -> report(ctx.getSource())));
    }

    private static int report(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Ejecútalo como jugador: el volcado mira a tu alrededor."));
            return 0;
        }
        List<GTEntity> nearby = player.level().getEntitiesOfClass(GTEntity.class,
                new AABB(player.blockPosition()).inflate(RADIUS));
        if (nearby.isEmpty()) {
            source.sendFailure(Component.literal("No hay ningún Grand Tyrant en " + (int) RADIUS + " bloques."));
            return 0;
        }
        for (GTEntity gt : nearby) {
            String line = String.format("[sleep] #%d  fase=%s  siesta=%s  avisos=%d  porJugador=%s  dist=%.1f  agachado=%s",
                    gt.getId(),
                    gt.sleepPhase(),
                    gt.isSpawnNapping(),
                    gt.sleepWarnings(),
                    gt.wokenByPlayer(),
                    gt.position().distanceTo(player.position()),
                    player.isDiscrete());
            source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
        }
        return nearby.size();
    }

    private SMOPSleepDebug() {}
}
```

`agachado` sale del jugador que ejecuta el comando, no del bicho, y está ahí a propósito: es la mitad de la ecuación del sigilo y la que más fácil se te olvida comprobar.

- [ ] **Paso 3: colgarlo**

En `SMOPCommands`, junto a los otros cinco `.then(...)`:

```java
                .then(SMOPSleepDebug.build())
```

- [ ] **Paso 4: compilar**

```bash
./gradlew compileJava
```

- [ ] **Paso 5: commit**

```bash
git add src/main/java/net/darkblade/smop/command/
git commit -m "Debug: /smop debug sleep, para ver avisos y siesta del GT"
```

---

## Tarea B9: Verificación práctica del módulo B

**Ficheros:** ninguno. Es una sesión de juego, con `/smop debug sleep` a mano.

- [ ] **Paso 1: arrancar**

```bash
./gradlew runClient
```

- [ ] **Paso 2: la siesta**

- Invoca diez GT a mediodía. **Alrededor de siete aparecen tumbados**, y siguen tumbados pasado un rato largo: el sol no los levanta.
- Aléjate hasta descargar el chunk y vuelve. **Sigue tumbado.** Este es el paso que más fácil se rompe.
- `/smop debug sleep` sobre uno dormido: fase `SLEEPING`, siesta `true`, avisos `0`.

- [ ] **Paso 3: los avisos y el sigilo**

- Acércate **sin agachar**: ronca al entrar en los 16 bloques, ronca otra vez pasados unos segundos, y al tercer contacto **despierta**. Comprueba con el comando que los avisos suben 0 → 1 → 2.
- La barra de jefe **aparece justo al despertar**, no antes.
- Despierta con `alt_awakening` y **no se sienta**: se levanta del tirón.
- Otro GT: acércate **agachado** y pásale por al lado. **No ronca y no despierta**, y los avisos siguen en 0.
- Otro más: gasta un aviso, retírate y espera medio minuto. El comando enseña el aviso perdonado y vuelves a tener margen.

- [ ] **Paso 4: los caminos que no son el feliz**

- Golpea a uno dormido: despierta con `alt_awakening`, sin avisos y sin pausa.
- De noche, con uno ya despierto y nadie cerca: se acuesta con la ceremonia entera y se levanta con `awakening` —el normal— y **con** su pausa sentado.

- [ ] **Paso 5: que no se haya roto nadie más**

Esto es lo que protege a los otros cuatro mobs del cambio en `SleepGoal`:

- **Hell Hippo:** dale la poción. Se duerme, aguanta con el sol alto y **aguanta contigo encima**, que es lo que la poción compra.
- **Tangoftero:** duerme de noche y **no le molesta que te acerques**; sigue despertándose sólo por lo que le tocaba.
- **Kriftognathus** y **Nirasmosaurus:** duermen y despiertan como antes, y te detectan a la distancia de siempre — no a dieciséis.

- [ ] **Paso 6: cerrar**

Marca en el spec lo que haya quedado afinado con otro número del que decía, y commitea los ajustes.

```bash
git add -A && git commit -m "GT: afinar el sueño tras probarlo en juego"
```

---

## Orden y dependencias

```
A1 ─ A2 ─ A3 ─ A4 ─ A5        (módulo A, independiente)

B1 ─ B2 ┐
B3 ─ B4 ┼─ B5 ─ B6 ─ B7 ─ B8 ─ B9
```

- **A y B no se tocan.** Se pueden hacer en cualquier orden, o parar después de A.
- **B3 antes que B5**, porque la siesta necesita el reloj partido.
- **B4 antes que B6**, porque el sigilo necesita que el evaluador vea a los jugadores.
- **B2 antes que B6 y B7**, porque los dos reproducen clips que B2 registra.
- **B1 antes que B2**, porque B2 se verifica con la herramienta.
