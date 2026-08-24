package net.darkblade.smop.client.gt;

import net.darkblade.smop.SMOP;
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
import org.jetbrains.annotations.Nullable;

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
                if (level.getRandom().nextInt(10) < SKIP_UNDER) {
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
                        MIN_START_STAGE + level.getRandom().nextInt(MAX_START_STAGE - MIN_START_STAGE + 1));
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
