package net.darkblade.smop.effect;

import net.darkblade.smop.SMOP;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class FearEffect extends MobEffect {

    public FearEffect() {
        super(MobEffectCategory.HARMFUL, 0x4A148C);

        this.addAttributeModifier(Attributes.ATTACK_DAMAGE,
                SMOP.id("effect.fear.attack_damage"),
                -4.0D, // same magnitude as Weakness I
                AttributeModifier.Operation.ADD_VALUE);

        this.addAttributeModifier(Attributes.MOVEMENT_SPEED,
                SMOP.id("effect.fear.movement_speed"),
                -0.15D, // same magnitude as Slowness I
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
        return true;
    }
}
