package net.darkblade.smop.entity.egg;

import net.minecraft.util.RandomSource;

/** A mob with simple random cosmetic variants (colours, patterns), rolled when it hatches or spawns. */
public interface RandomVariantCapable {

    void setRandomVariant(RandomSource random);

    int getVariantId();

    int getMaxVariants();
}
