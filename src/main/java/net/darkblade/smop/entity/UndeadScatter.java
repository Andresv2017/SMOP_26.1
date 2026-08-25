package net.darkblade.smop.entity;

import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class UndeadScatter {

    private UndeadScatter() {}

    private static final double MIN_DISTANCE = 1.0E-4D;

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
