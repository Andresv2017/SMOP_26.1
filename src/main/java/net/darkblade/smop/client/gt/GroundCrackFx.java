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


@EventBusSubscriber(modid = SMOP.MOD_ID, value = Dist.CLIENT)
public final class GroundCrackFx {

    private static final int MAX_STAGE = 9;

    private static final int RIM_STAGE = 4;
    private static final int FOOT_STAGE = 8;

    private static final int HOLD_TICKS = 100;
    private static final int FADE_TICKS = 60;

    private static final int SAMPLES_PER_IMPACT = 90;

    private static final int GROUND_SEARCH_DOWN = 6;

    private record Crack(int id, int ticksLeft, int baseStage, int shownStage) {}

    private static final Map<BlockPos, Crack> ACTIVE = new HashMap<>();


    public static void stomp(@NotNull BlockPos center, int radius) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        RandomSource random = level.getRandom();
        Set<Long> touched = new HashSet<>();

        for (int i = 0; i < SAMPLES_PER_IMPACT; i++) {
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

    private static int stageFor(double normalizedDistance) {
        double depth = 1.0D - Mth.clamp(normalizedDistance, 0.0D, 1.0D);
        return RIM_STAGE + (int) Math.round(depth * (FOOT_STAGE - RIM_STAGE));
    }

    public static void add(@NotNull BlockPos pos, int stage) {
        LevelRenderer renderer = Minecraft.getInstance().levelRenderer;
        BlockPos key = pos.immutable();
        Crack existing = ACTIVE.get(key);
        int id = existing != null ? existing.id() : stableId(key);
        int finalStage = existing != null
                ? Math.min(MAX_STAGE, existing.baseStage() + 1)
                : Math.min(MAX_STAGE, stage);
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
            int stage = left >= FADE_TICKS
                    ? crack.baseStage()
                    : Math.round(crack.baseStage() * (float) left / FADE_TICKS);
            if (stage <= 0) {
                renderer.destroyBlockProgress(crack.id(), entry.getKey(), -1);
                it.remove();
                continue;
            }
            if (stage != crack.shownStage()) {
                renderer.destroyBlockProgress(crack.id(), entry.getKey(), stage);
            }
            entry.setValue(new Crack(crack.id(), left, crack.baseStage(), stage));
        }
    }

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
