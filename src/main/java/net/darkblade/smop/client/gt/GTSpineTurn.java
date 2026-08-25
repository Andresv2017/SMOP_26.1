package net.darkblade.smop.client.gt;

import net.darkblade.deluxelib.anim.MobAnimator;
import net.darkblade.deluxelib.client.render.DeluxeEntityRenderState;
import net.darkblade.deluxelib.client.rig.AnimContext;
import net.darkblade.deluxelib.client.rig.RigComponent;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.WeakHashMap;
import java.util.function.Function;


public final class GTSpineTurn implements RigComponent<GTModel> {

    private record Link(Function<GTModel, ModelPart> part, float factor, float maxDegrees,
                        float responseHz) {}


    private static final List<Link> CHAIN = List.of(
            new Link(m -> m.head, 0.50F, 30.0F, 2.4F),
            new Link(m -> m.neck, 0.35F, 22.0F, 1.7F),
            new Link(m -> m.body_parts, 0.15F, 10.0F, 1.2F),
            new Link(m -> m.tail1, -0.30F, 18.0F, 0.95F),
            new Link(m -> m.tail2, -0.30F, 18.0F, 0.75F),
            new Link(m -> m.tail3, -0.25F, 16.0F, 0.60F));

    private static final float DAMPING = 0.55F;
    private static final float DEADZONE = 3.0F;
    private static final float MAX_STEP_SECONDS = 1.0F / 120.0F;

    private static final class State {
        final float[] pose = new float[CHAIN.size()];
        final float[] velocity = new float[CHAIN.size()];
        long lastNanos = -1L;
    }

    private final WeakHashMap<MobAnimator<?>, State> states = new WeakHashMap<>();

    @Override
    public void apply(@NotNull DeluxeEntityRenderState renderState, @NotNull GTModel model,
                      @NotNull AnimContext ctx) {
        MobAnimator<?> animator = renderState.animator;
        if (animator == null) {
            return;
        }
        State state = this.states.computeIfAbsent(animator, a -> new State());
        float dt = deltaSeconds(state);

        float gap = headingGap(renderState, animator.getEntity(), ctx.partialTick());

        float upstream = gap;
        for (int i = 0; i < CHAIN.size(); i++) {
            Link link = CHAIN.get(i);
            float target = upstream;
            float omega = (float) (2.0 * Math.PI * link.responseHz());
            float remaining = dt;
            while (remaining > 0.0F) {
                float h = Math.min(remaining, MAX_STEP_SECONDS);
                state.velocity[i] += (omega * omega * (target - state.pose[i])
                        - 2.0F * DAMPING * omega * state.velocity[i]) * h;
                state.pose[i] += state.velocity[i] * h;
                remaining -= h;
            }
            upstream = state.pose[i];
        }

        if (animator.isAdditiveBlocked()) {
            return;
        }
        for (int i = 0; i < CHAIN.size(); i++) {
            Link link = CHAIN.get(i);
            float amount = Mth.clamp(state.pose[i] * link.factor(),
                    -link.maxDegrees(), link.maxDegrees());
            if (Math.abs(amount) < 0.05F) {
                continue;
            }
            link.part().apply(model).yRot += (float) Math.toRadians(amount);
        }
    }

    private static float headingGap(@NotNull DeluxeEntityRenderState renderState,
                                    @NotNull LivingEntity entity, float partialTick) {
        float wanted = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        float gap = Mth.wrapDegrees(wanted - renderState.bodyRot);
        float magnitude = Math.abs(gap) - DEADZONE;
        return magnitude <= 0.0F ? 0.0F : Math.copySign(magnitude, gap);
    }

    private static float deltaSeconds(@NotNull State state) {
        long now = System.nanoTime();
        if (state.lastNanos < 0L) {
            state.lastNanos = now;
            return 0.0F;
        }
        float dt = (now - state.lastNanos) / 1_000_000_000.0F;
        state.lastNanos = now;
        return Mth.clamp(dt, 0.0F, 0.1F);
    }
}
