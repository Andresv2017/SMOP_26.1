package net.darkblade.smop.entity.egg;

import net.minecraft.util.RandomSource;

public interface RandomVariantCapable {

    void setRandomVariant(RandomSource random);

    int getVariantId();

    int getMaxVariants();
}
