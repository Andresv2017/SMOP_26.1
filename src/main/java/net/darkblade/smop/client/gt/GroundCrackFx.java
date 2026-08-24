package net.darkblade.smop.client.gt;

import net.darkblade.smop.SMOP;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

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

    /**
     * Etapa en el borde del disco y bajo el pie. <b>La grieta se lee por la profundidad, no por el
     * sitio:</b> hondo donde cayó el pie y superficial en el borde cuenta un impacto, mientras que una
     * etapa al azar por bloque —lo que hacía la primera versión— sólo parece ruido.
     *
     * <p>Se deja el 9 sin usar a propósito: es a donde llega el centro cuando el segundo y el tercer
     * impacto profundizan lo que ya había.
     */
    private static final int RIM_STAGE = 4;
    private static final int FOOT_STAGE = 8;

    /**
     * Cuánto aguanta una grieta a plena profundidad antes de empezar a cerrarse.
     *
     * <p>Cinco segundos, contra los 22 ticks del legacy. Con 22 no daba tiempo ni a ver el pisotón
     * entero: sus tres impactos caen en los ticks 14, 26 y 46 del clip, así que las grietas del
     * primero se borraban ANTES de que llegara el tercero y el suelo parpadeaba en vez de resentirse.
     */
    private static final int HOLD_TICKS = 100;

    /**
     * Y luego se cierran solas, bajando de etapa, en vez de esfumarse de un frame para otro.
     *
     * <p>Tres segundos para las ocho etapas: se va viendo cómo el suelo se recompone. Desaparecer de
     * golpe era lo que hacía que el efecto se sintiera apagado por un temporizador y no curado.
     */
    private static final int FADE_TICKS = 60;

    /**
     * Intentos de agrietar por impacto. Salen menos posiciones que intentos porque dos tiradas pueden
     * caer en la misma columna, y esa pérdida es mayor cerca del centro — que es donde interesa que se
     * amontonen.
     */
    private static final int SAMPLES_PER_IMPACT = 90;

    /** Cuánto se busca suelo hacia abajo desde la altura del bicho. */
    private static final int GROUND_SEARCH_DOWN = 6;

    /**
     * @param baseStage la profundidad que alcanzó, de la que se parte al cerrarse
     * @param shownStage la que está pintada ahora mismo, para no reenviarla cada tick
     */
    private record Crack(int id, int ticksLeft, int baseStage, int shownStage) {}

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
        RandomSource random = level.getRandom();
        // Las columnas ya tocadas en ESTE impacto. Sin esto, dos tiradas en la misma columna la
        // profundizarían dos veces y el escalonado por distancia dejaría de contar lo que dice.
        Set<Long> touched = new HashSet<>();

        for (int i = 0; i < SAMPLES_PER_IMPACT; i++) {
            // Muestreo polar, y la RAÍZ es lo que lo hace uniforme por área: sin ella todo se
            // amontona en el centro, porque un anillo lejano tiene más superficie que uno cercano.
            //
            // Sortear posiciones en vez de barrer el cuadrado es el arreglo de un fallo real: barrer
            // con un tope hacía que el tope llegara a media pasada, y el disco se agrietaba de -8 a
            // +2 dejando SIEMPRE el mismo tercio intacto. Medido antes de cambiarlo.
            double normalized = Math.sqrt(random.nextDouble());
            double distance = radius * normalized;
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int dx = (int) Math.round(Math.cos(angle) * distance);
            int dz = (int) Math.round(Math.sin(angle) * distance);
            if (!touched.add(BlockPos.asLong(dx, 0, dz))) {
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
            add(ground, stageFor(normalized));
        }
    }

    /**
     * Etapa de una grieta según lo lejos que cayó del pie, de 0 (bajo el pie) a 1 (en el borde).
     */
    private static int stageFor(double normalizedDistance) {
        double depth = 1.0D - Mth.clamp(normalizedDistance, 0.0D, 1.0D);
        return RIM_STAGE + (int) Math.round(depth * (FOOT_STAGE - RIM_STAGE));
    }

    /**
     * Añade una grieta, o profundiza la que ya hubiera ahí y le renueva el plazo. Profundizar es lo
     * que encadena los tres impactos del pisotón en un solo gesto.
     */
    public static void add(@NotNull BlockPos pos, int stage) {
        LevelRenderer renderer = Minecraft.getInstance().levelRenderer;
        BlockPos key = pos.immutable();
        Crack existing = ACTIVE.get(key);
        int id = existing != null ? existing.id() : stableId(key);
        int finalStage = existing != null
                ? Math.min(MAX_STAGE, existing.baseStage() + 1)
                : Math.min(MAX_STAGE, stage);
        // El plazo se renueva entero: manda el impacto más reciente, no el primero.
        ACTIVE.put(key, new Crack(id, HOLD_TICKS + FADE_TICKS, finalStage, finalStage));
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
            int left = crack.ticksLeft() - 1;
            // Aguanta a plena profundidad y, pasado el plazo, se va cerrando etapa a etapa.
            int stage = left >= FADE_TICKS
                    ? crack.baseStage()
                    : Math.round(crack.baseStage() * (float) left / FADE_TICKS);
            if (stage <= 0) {
                // -1 es como el renderer borra una entrada de rotura. Sin esto la grieta se queda
                // pintada para siempre, que es el modo de fallo feo de este efecto.
                renderer.destroyBlockProgress(crack.id(), entry.getKey(), -1);
                it.remove();
                continue;
            }
            // Sólo se reenvía cuando la etapa CAMBIA. Repintar 76 bloques cada tick durante ocho
            // segundos sería tirar trabajo del renderer para dibujar lo mismo.
            if (stage != crack.shownStage()) {
                renderer.destroyBlockProgress(crack.id(), entry.getKey(), stage);
            }
            entry.setValue(new Crack(crack.id(), left, crack.baseStage(), stage));
        }
    }

    /**
     * Primer bloque con cara superior sólida, bajando. Sin esto, un pisotón al borde de un barranco
     * pintaría grietas flotando en el aire.
     */
    @Nullable
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
