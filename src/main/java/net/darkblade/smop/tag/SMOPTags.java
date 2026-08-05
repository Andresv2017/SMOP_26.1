package net.darkblade.smop.tag;

import net.darkblade.smop.SMOP;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Tag keys owned by SMOP. The tag contents themselves are datapack files under {@code data/smop/tags/}. */
public final class SMOPTags {

    public static final class Blocks {

        /**
         * Every mob egg block in the mod. Read by the egg-protection goals so a mother reacts to
         * <em>any</em> nest being broken, not just her own species'.
         *
         * <p>The tag's data file lands in Fase 2 along with the egg blocks — a tag listing blocks
         * that are not registered yet fails datapack loading.
         */
        public static final TagKey<Block> EGG_BLOCKS = tag("egg_blocks");

        private static TagKey<Block> tag(String name) {
            return TagKey.create(Registries.BLOCK, SMOP.id(name));
        }

        private Blocks() {}
    }

    private SMOPTags() {}
}
