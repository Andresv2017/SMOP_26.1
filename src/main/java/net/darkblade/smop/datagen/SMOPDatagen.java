package net.darkblade.smop.datagen;

import net.darkblade.deluxelib.datagen.DeluxeEntityLootSubProvider;
import net.darkblade.deluxelib.datagen.DeluxeLangProvider;
import net.darkblade.deluxelib.spawn.DeluxeBiomeSpawnProvider;
import net.darkblade.smop.SMOP;
import net.darkblade.smop.block.SMOPBlocks;
import net.darkblade.smop.entity.SMOPEntities;
import net.darkblade.smop.item.SMOPItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.data.recipes.SmithingTransformRecipeBuilder;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class SMOPDatagen {

    public static void gatherClientData(GatherDataEvent.Client event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        generator.addProvider(true, new Lang(output));
    }

    public static void gatherServerData(GatherDataEvent.Server event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        CompletableFuture<HolderLookup.Provider> registries = event.getLookupProvider();
        generator.addProvider(true, new LootTableProvider(output, Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(Loot::new, LootContextParamSets.ENTITY),
                        new LootTableProvider.SubProviderEntry(BlockLoot::new, LootContextParamSets.BLOCK)),
                registries));
        generator.addProvider(true, new Recipes(output, registries));
        generator.addProvider(true, new DeluxeBiomeSpawnProvider(output, SMOP.MOD_ID));
    }

    private static final class Loot extends DeluxeEntityLootSubProvider {
        Loot(HolderLookup.Provider registries) {
            super(registries, SMOPEntities.ENTITY_TYPES);
        }

        @Override
        protected void addLootTables() {
            this.add(SMOPEntities.TANGOFTERO.get(), LootTable.lootTable()
                    .withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1))
                            .add(LootItem.lootTableItem(SMOPItems.TANGO_LEG.get())
                                    .apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 2.0F)))))
                    .withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1))
                            .add(LootItem.lootTableItem(SMOPItems.TANGO_FEATHER.get())
                                    .apply(SetItemCountFunction.setCount(UniformGenerator.between(0.0F, 2.0F))))));

            this.add(SMOPEntities.KRIFTOGNATHUS.get(), LootTable.lootTable()
                    .withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1))
                            .add(LootItem.lootTableItem(SMOPItems.KRIFTO_MEAT.get())
                                    .apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 2.0F)))))
                    .withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1))
                            .add(LootItem.lootTableItem(SMOPItems.KRIFTO_WING.get())
                                    .apply(SetItemCountFunction.setCount(UniformGenerator.between(0.0F, 2.0F))))));

            this.add(SMOPEntities.HELL_HIPPO.get(), LootTable.lootTable()
                    .withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1))
                            .add(LootItem.lootTableItem(SMOPItems.HELL_HIPPO_RAW_MEAT.get()))));

            this.add(SMOPEntities.NIRASMOSAURUS.get(), LootTable.lootTable()
                    .withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1))
                            .add(LootItem.lootTableItem(SMOPItems.NIRASMO_MEAT.get())
                                    .apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 2.0F)))))
                    .withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1))
                            .add(LootItem.lootTableItem(SMOPItems.NIRASMO_BEAK.get())
                                    .apply(SetItemCountFunction.setCount(UniformGenerator.between(0.0F, 1.0F))))));

            this.add(SMOPEntities.SALMON.get(), LootTable.lootTable()
                    .withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1))
                            .add(LootItem.lootTableItem(SMOPItems.RAW_SALMON.get())
                                    .apply(SetItemCountFunction.setCount(ConstantValue.exactly(1.0F))))));

            this.add(SMOPEntities.GT.get(), LootTable.lootTable()
                    .withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1))
                            .add(LootItem.lootTableItem(SMOPItems.GT_HEAD.get()))));
        }
    }

    private static final class BlockLoot extends BlockLootSubProvider {

        BlockLoot(HolderLookup.Provider registries) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
        }

        @Override
        protected void generate() {
            this.dropWhenSilkTouch(SMOPBlocks.TANGOFTERO_EGG.get());
            this.dropWhenSilkTouch(SMOPBlocks.KRIFTO_EGG.get());
            this.dropWhenSilkTouch(SMOPBlocks.NIRAS_EGG.get());
            this.dropWhenSilkTouch(SMOPBlocks.SALMON_ROE_EGGS.get());
            this.dropSelf(SMOPBlocks.GT_HEAD.get());
        }

        @Override
        protected @NotNull Iterable<Block> getKnownBlocks() {
            return SMOPBlocks.BLOCKS.getEntries().stream()
                    .map(holder -> (Block) holder.value())
                    .toList();
        }
    }

    private static final class Recipes extends RecipeProvider.Runner {
        Recipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

        @Override
        protected @NotNull RecipeProvider createRecipeProvider(@NotNull HolderLookup.Provider registries,
                                                               @NotNull RecipeOutput output) {
            return new Recipe(registries, output);
        }

        @Override
        public @NotNull String getName() {
            return "SMOP Recipes";
        }
    }

    private static final class Recipe extends RecipeProvider {

        private static final float XP = 0.35F;

        private static final int SMELT_TIME = 200;
        private static final int SMOKE_TIME = 150;
        private static final int CAMPFIRE_TIME = 600;

        Recipe(HolderLookup.Provider registries, RecipeOutput output) {
            super(registries, output);
        }

        @Override
        protected void buildRecipes() {
            cooking(SMOPItems.HELL_HIPPO_RAW_MEAT.get(), SMOPItems.HELL_HIPPO_COOKED_MEAT.get(),
                    "hell_hippo_cooked_meat");
            cooking(SMOPItems.KRIFTO_MEAT.get(), SMOPItems.COOKED_KRIFTO_MEAT.get(),
                    "cooked_krifto_meat");
            cooking(SMOPItems.TANGO_LEG.get(), SMOPItems.COOKED_TANGO_LEG.get(),
                    "cooked_tango_leg");
            cooking(SMOPItems.NIRASMO_MEAT.get(), SMOPItems.COOKED_NIRASMO_MEAT.get(),
                    "cooked_nirasmo_meat");
            cooking(SMOPItems.RAW_SALMON.get(), Items.COOKED_SALMON,
                    "cooked_salmon_from_raw_salmon");

            this.shapeless(RecipeCategory.FOOD, SMOPItems.KRIFTO_STEW.get())
                    .requires(Items.BOWL)
                    .requires(SMOPItems.COOKED_KRIFTO_MEAT.get())
                    .requires(SMOPItems.KRIFTO_WING.get())
                    .requires(Items.CARROT)
                    .unlockedBy(getHasName(SMOPItems.KRIFTO_WING.get()), this.has(SMOPItems.KRIFTO_WING.get()))
                    .save(this.output);

            this.shaped(RecipeCategory.COMBAT, SMOPItems.TANGO_ARROW.get(), 4)
                    .pattern("X")
                    .pattern("#")
                    .pattern("Y")
                    .define('X', Items.FLINT)
                    .define('#', Items.STICK)
                    .define('Y', SMOPItems.TANGO_FEATHER.get())
                    .unlockedBy(getHasName(SMOPItems.TANGO_FEATHER.get()), this.has(SMOPItems.TANGO_FEATHER.get()))
                    .save(this.output);

            this.shaped(RecipeCategory.COMBAT, SMOPItems.NIRAS_SPEAR.get())
                    .pattern("  A")
                    .pattern(" B ")
                    .pattern("B  ")
                    .define('A', SMOPItems.NIRASMO_BEAK.get())
                    .define('B', Items.STICK)
                    .unlockedBy(getHasName(SMOPItems.NIRASMO_BEAK.get()), this.has(SMOPItems.NIRASMO_BEAK.get()))
                    .save(this.output);

            SmithingTransformRecipeBuilder.smithing(
                            Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                            Ingredient.of(Items.GOLDEN_HORSE_ARMOR),
                            Ingredient.of(Items.NETHERITE_INGOT),
                            RecipeCategory.COMBAT,
                            SMOPItems.HELL_HIPPO_ARMOR.get())
                    .unlocks(getHasName(Items.NETHERITE_INGOT), this.has(Items.NETHERITE_INGOT))
                    .save(this.output, SMOP.MOD_ID + ":hellhippo_armor_smithing");
        }

        private void cooking(ItemLike base, ItemLike result, String idPrefix) {
            String unlock = getHasName(base);

            SimpleCookingRecipeBuilder
                    .smelting(Ingredient.of(base), RecipeCategory.FOOD, CookingBookCategory.FOOD,
                            result, XP, SMELT_TIME)
                    .unlockedBy(unlock, this.has(base))
                    .save(this.output, SMOP.MOD_ID + ":" + idPrefix + "_smelting");

            SimpleCookingRecipeBuilder
                    .smoking(Ingredient.of(base), RecipeCategory.FOOD, result, XP, SMOKE_TIME)
                    .unlockedBy(unlock, this.has(base))
                    .save(this.output, SMOP.MOD_ID + ":" + idPrefix + "_smoking");

            SimpleCookingRecipeBuilder
                    .campfireCooking(Ingredient.of(base), RecipeCategory.FOOD, result, XP, CAMPFIRE_TIME)
                    .unlockedBy(unlock, this.has(base))
                    .save(this.output, SMOP.MOD_ID + ":" + idPrefix + "_campfire");
        }
    }

    private static final class Lang extends DeluxeLangProvider {

        Lang(PackOutput output) {
            super(output, SMOP.MOD_ID, "en_us");
        }

        @Override
        protected void addTranslations() {
            add(SMOPItems.COOKED_NIRASMO_MEAT.get(), "Nirasmo Cooked Meat");
            add(SMOPItems.HELL_HIPPO_ARMOR.get(), "Hell Hippo Armor");
            add(SMOPEntities.GT.get(), "Grand Tyrant");
            add(SMOPItems.GT_SPAWN_EGG.get(), "Grand Tyrant Spawn Egg");
            add(SMOPBlocks.GT_HEAD.get(), "Grand Tyrant Head");

            autoItemNames(SMOPItems.ITEMS);
            autoBlockNames(SMOPBlocks.BLOCKS);
            autoEntityNames(SMOPEntities.ENTITY_TYPES);

            add("creativetab.smop_tab", "Spectacular Mobs Of Peligoro");

            add("effect.smop.fear", "Fear");

            add("subtitles.gt_roar", "Grand Tyrant roars");
            add("subtitles.krifto_squawk", "Kriftognathus squawks");

            add("key.category.smop.main", "Spectacular Mobs of Peligoro");
            add("key.smop.attack", "Mounted Attack");
            add("key.smop.fear", "Intimidate");
            add("key.smop.open_inventory", "Open Mount Inventory");
            add("key.smop.descend", "Descend");
        }
    }

    private SMOPDatagen() {}
}
