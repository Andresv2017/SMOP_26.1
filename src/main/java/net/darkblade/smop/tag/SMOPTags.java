package net.darkblade.smop.tag;

import net.darkblade.smop.SMOP;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class SMOPTags {

    public static final class Blocks {

        public static final TagKey<Block> EGG_BLOCKS = tag("egg_blocks");

        private static TagKey<Block> tag(String name) {
            return TagKey.create(Registries.BLOCK, SMOP.id(name));
        }

        private Blocks() {}
    }

    private SMOPTags() {}
}
