package net.darkblade.smop.entity;

import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Sends every undead near a point pathing away from it.
 *
 * <p>The mod's one shared piece of anti-undead behaviour, and it has two callers with different
 * numbers: the Tangoftero's roar, which is the original, and the tango arrow, which is that roar at
 * range and half the reach. They were the same loop written twice, which is the kind of duplication
 * that stays in sync exactly until someone tunes one of them.
 *
 * <p><b>It moves them, it does not debuff them.</b> That is the line between this mod's two fears:
 * the Hell Hippo applies {@code smop:fear} and leaves you weakened where you stand, the Tangoftero
 * makes things run. Reaching for the effect here would blur two mobs into one.
 *
 * <p>Server-side only — it writes to {@link net.minecraft.world.entity.ai.navigation.PathNavigation},
 * which does not exist meaningfully on the client.
 */
public final class UndeadScatter {

    private UndeadScatter() {}

    /** Below this, the direction away from the origin is numerically meaningless. */
    private static final double MIN_DISTANCE = 1.0E-4D;

    /**
     * @param origin       what they run from
     * @param radius       how far the panic reaches
     * @param fleeDistance how far each one is sent, measured from {@code origin} outwards
     * @param speed        navigation speed modifier for the retreat
     * @param except       one entity to leave alone — the roaring Tangoftero itself, or the undead an
     *                     arrow just hit, which should stay put rather than run off the shot
     * @return how many were actually sent running, so a caller can skip its feedback when nothing
     *         heard it
     */
    public static int scatter(Level level, Vec3 origin, double radius, double fleeDistance, double speed,
                              @Nullable Entity except) {
        AABB range = new AABB(origin, origin).inflate(radius);
        List<Mob> undead = level.getEntitiesOfClass(Mob.class, range,
                mob -> mob.isAlive() && mob != except && mob.is(EntityTypeTags.UNDEAD));

        int scattered = 0;
        for (Mob mob : undead) {
            double dx = mob.getX() - origin.x;
            double dz = mob.getZ() - origin.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance < MIN_DISTANCE) {
                // Standing exactly on the origin: no direction to run in. Leave it; next tick it will
                // have drifted and the caller's own cooldown decides whether it gets another chance.
                continue;
            }

            double scale = fleeDistance / distance;
            mob.getNavigation().moveTo(mob.getX() + dx * scale, mob.getY(), mob.getZ() + dz * scale, speed);
            scattered++;
        }
        return scattered;
    }
}
