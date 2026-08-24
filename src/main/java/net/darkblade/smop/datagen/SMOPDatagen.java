package net.darkblade.smop.datagen;

import net.darkblade.deluxelib.datagen.DeluxeEntityLootProvider;
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
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.data.recipes.SmithingTransformRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Data generation entry points.
 *
 * <p><b>Port note.</b> 1.20.1 shipped a hand-written {@code assets/smop/lang/en_us.json}; it is
 * generated now, so display names cannot drift out of sync with the registry. Anything whose
 * auto-derived name would be wrong is overridden explicitly below and everything else falls out of
 * {@code autoItemNames} ({@code krifto_meat -> "Krifto Meat"}).
 *
 * <p>26.1 note: {@code GatherDataEvent} is abstract — listeners go on its concrete
 * {@code Client}/{@code Server} subclasses. Entity loot and biome spawn providers arrive with the
 * first mob (Fase 3); there is nothing for them to generate yet.
 */
public final class SMOPDatagen {

    /** Wired from {@code SMOP}'s constructor. */
    public static void gatherClientData(GatherDataEvent.Client event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        generator.addProvider(true, new Lang(output));
    }

    /** Wired from {@code SMOP}'s constructor. */
    public static void gatherServerData(GatherDataEvent.Server event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        CompletableFuture<HolderLookup.Provider> registries = event.getLookupProvider();
        generator.addProvider(true, new EntityLoot(output, registries));
        generator.addProvider(true, new Recipes(output, registries));
        // Writes every entry SMOPSpawns submitted as a neoforge:add_spawns biome modifier. Filters
        // by namespace internally, so DeluxeLib's own test mobs never leak into smop's datapack.
        generator.addProvider(true, new DeluxeBiomeSpawnProvider(output, SMOP.MOD_ID));
    }

    private static final class EntityLoot extends DeluxeEntityLootProvider {
        EntityLoot(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries, Loot::new);
        }
    }

    private static final class Loot extends DeluxeEntityLootSubProvider {
        Loot(HolderLookup.Provider registries) {
            super(registries, SMOPEntities.ENTITY_TYPES);
        }

        @Override
        protected void addLootTables() {
            // 1–2 legs and a feather, both rolled independently.
            this.add(SMOPEntities.TANGOFTERO.get(), LootTable.lootTable()
                    .withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1))
                            .add(LootItem.lootTableItem(SMOPItems.TANGO_LEG.get())
                                    .apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 2.0F)))))
                    .withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1))
                            .add(LootItem.lootTableItem(SMOPItems.TANGO_FEATHER.get())
                                    .apply(SetItemCountFunction.setCount(UniformGenerator.between(0.0F, 2.0F))))));

            // Wing and a little meat.
            this.add(SMOPEntities.KRIFTOGNATHUS.get(), LootTable.lootTable()
                    .withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1))
                            .add(LootItem.lootTableItem(SMOPItems.KRIFTO_MEAT.get())
                                    .apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 2.0F)))))
                    .withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1))
                            .add(LootItem.lootTableItem(SMOPItems.KRIFTO_WING.get())
                                    .apply(SetItemCountFunction.setCount(UniformGenerator.between(0.0F, 2.0F))))));

            // One cut of meat, exactly as 1.20.1's hand-written hell_hippo.json had it: a single pool
            // of one roll holding raw meat at chance 1. Without this table the animal dropped nothing
            // at all, which left both HELL_HIPPO_RAW_MEAT and HELL_HIPPO_COOKED_MEAT unobtainable in
            // survival — the cooked one being the mod's own creative-tab icon.
            this.add(SMOPEntities.HELL_HIPPO.get(), LootTable.lootTable()
                    .withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1))
                            .add(LootItem.lootTableItem(SMOPItems.HELL_HIPPO_RAW_MEAT.get()))));

            // Meat and the beak, rolled independently, following the Kriftognathus' shape: the cut you
            // butcher an animal for plus the one distinctive part of it.
            //
            // NOT ported — 1.20.1 has no nirasmosaurus loot table at all, only hell_hippo.json and
            // salmon.json, so NIRASMO_MEAT and NIRASMO_BEAK were unobtainable in survival there and
            // would have stayed unobtainable here. Two of the three items already sit in the creative
            // tab (raw, cooked and the beak), so the drop is what makes that tab honest.
            //
            // 1-2 meat off a three-block animal, against the Hell Hippo's flat 1: this one is longer
            // than the hippo is wide. The beak is 0-1 rather than the wing's 0-2, because an animal has
            // one beak — the Kriftognathus has two wings, and its table says so.
            this.add(SMOPEntities.NIRASMOSAURUS.get(), LootTable.lootTable()
                    .withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1))
                            .add(LootItem.lootTableItem(SMOPItems.NIRASMO_MEAT.get())
                                    .apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 2.0F)))))
                    .withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1))
                            .add(LootItem.lootTableItem(SMOPItems.NIRASMO_BEAK.get())
                                    .apply(SetItemCountFunction.setCount(UniformGenerator.between(0.0F, 1.0F))))));

            // One fillet per fish.
            this.add(SMOPEntities.SALMON.get(), LootTable.lootTable()
                    .withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1))
                            .add(LootItem.lootTableItem(SMOPItems.RAW_SALMON.get())
                                    .apply(SetItemCountFunction.setCount(ConstantValue.exactly(1.0F))))));
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

    /**
     * Every recipe 1.20.1 shipped as hand-written JSON, minus the Niras spear — that one waits for
     * its item to exist.
     *
     * <p><b>Why generated rather than copied.</b> The seventeen files are still sitting in the legacy
     * tree and none of them would load here: the recipe format changed under us. {@code "ingredient":
     * {"item": "x"}} is now the bare string {@code "x"}, {@code "result"} became an object keyed by
     * {@code id}, and {@code "category"} is mandatory. A silently-rejected recipe is worse than a
     * missing one, so the builders write the current shape and the compiler checks the item ids.
     *
     * <p><b>The ids are the legacy ids, deliberately.</b> Every {@code save} below names its recipe
     * explicitly instead of taking the default derived from the result, so a world that already knows
     * these recipes keeps knowing them and the unlock advancements keep their paths. Two of them
     * would break outright without it: the salmon pair resolves to {@code minecraft:cooked_salmon},
     * which is vanilla's own recipe id and would collide.
     */
    private static final class Recipe extends RecipeProvider {

        /** 0.35 across the board in 1.20.1, which is also what vanilla pays for any cut of meat. */
        private static final float XP = 0.35F;

        private static final int SMELT_TIME = 200;
        private static final int SMOKE_TIME = 150;
        private static final int CAMPFIRE_TIME = 600;

        Recipe(HolderLookup.Provider registries, RecipeOutput output) {
            super(registries, output);
        }

        @Override
        protected void buildRecipes() {
            // Five cuts of meat, three appliances each. Four of the five results were unobtainable in
            // survival until this line — including hell_hippo_cooked_meat, which the creative tab uses
            // as its own icon.
            cooking(SMOPItems.HELL_HIPPO_RAW_MEAT.get(), SMOPItems.HELL_HIPPO_COOKED_MEAT.get(),
                    "hell_hippo_cooked_meat");
            cooking(SMOPItems.KRIFTO_MEAT.get(), SMOPItems.COOKED_KRIFTO_MEAT.get(),
                    "cooked_krifto_meat");
            cooking(SMOPItems.TANGO_LEG.get(), SMOPItems.COOKED_TANGO_LEG.get(),
                    "cooked_tango_leg");
            cooking(SMOPItems.NIRASMO_MEAT.get(), SMOPItems.COOKED_NIRASMO_MEAT.get(),
                    "cooked_nirasmo_meat");
            // The odd one out: the mod's raw salmon cooks into VANILLA's cooked salmon rather than a
            // smop item of its own, which is why its ids carry the _from_raw_salmon that the other
            // four do not need.
            cooking(SMOPItems.RAW_SALMON.get(), Items.COOKED_SALMON,
                    "cooked_salmon_from_raw_salmon");

            // Bowl, cooked meat, a wing and a carrot. Unlocked by the wing: it is the one ingredient
            // that says you have actually met a Kriftognathus.
            this.shapeless(RecipeCategory.FOOD, SMOPItems.KRIFTO_STEW.get())
                    .requires(Items.BOWL)
                    .requires(SMOPItems.COOKED_KRIFTO_MEAT.get())
                    .requires(SMOPItems.KRIFTO_WING.get())
                    .requires(Items.CARROT)
                    .unlockedBy(getHasName(SMOPItems.KRIFTO_WING.get()), this.has(SMOPItems.KRIFTO_WING.get()))
                    .save(this.output);

            // ── NOT a port: 1.20.1 has no recipe for the tango arrow at all. ──
            //
            // Which left it obtainable only from the creative tab, in a mod where nothing else drops
            // it either. Vanilla's own arrow — flint, stick, feather, four out — is the obvious shape
            // to borrow, and borrowing it verbatim is the point: this arrow is mechanically identical
            // to minecraft:arrow (see TangoArrowEntity), so charging more for it would be a tax on
            // using the pretty one, and charging less would make Tangoftero feathers the arrow meta.
            // Same cost, different feather.
            //
            // If this is unwanted, deleting this one block is the whole undo.
            this.shaped(RecipeCategory.COMBAT, SMOPItems.TANGO_ARROW.get(), 4)
                    .pattern("X")
                    .pattern("#")
                    .pattern("Y")
                    .define('X', Items.FLINT)
                    .define('#', Items.STICK)
                    .define('Y', SMOPItems.TANGO_FEATHER.get())
                    .unlockedBy(getHasName(SMOPItems.TANGO_FEATHER.get()), this.has(SMOPItems.TANGO_FEATHER.get()))
                    .save(this.output);

            // The javelin: a beak on two sticks, in the legacy's own diagonal. One per craft, and the
            // stack caps at four, so arming yourself costs four beaks off four Nirasmosaurus.
            this.shaped(RecipeCategory.COMBAT, SMOPItems.NIRAS_SPEAR.get())
                    .pattern("  A")
                    .pattern(" B ")
                    .pattern("B  ")
                    .define('A', SMOPItems.NIRASMO_BEAK.get())
                    .define('B', Items.STICK)
                    .unlockedBy(getHasName(SMOPItems.NIRASMO_BEAK.get()), this.has(SMOPItems.NIRASMO_BEAK.get()))
                    .save(this.output);

            // Netherite-tier barding, upgraded off gold horse armour. Both vanilla ingredients still
            // exist in 26.1 under the same ids, so this ports across untouched.
            SmithingTransformRecipeBuilder.smithing(
                            Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                            Ingredient.of(Items.GOLDEN_HORSE_ARMOR),
                            Ingredient.of(Items.NETHERITE_INGOT),
                            RecipeCategory.COMBAT,
                            SMOPItems.HELL_HIPPO_ARMOR.get())
                    .unlocks(getHasName(Items.NETHERITE_INGOT), this.has(Items.NETHERITE_INGOT))
                    .save(this.output, SMOP.MOD_ID + ":hellhippo_armor_smithing");
        }

        /**
         * Furnace, smoker and campfire for one ingredient, with the legacy's own times and ids.
         *
         * <p>Not {@code RecipeProvider#simpleCookingRecipe}, which looks like the same thing: that one
         * derives its id from the result and would hand the salmon pair vanilla's {@code
         * minecraft:cooked_salmon}. {@code idPrefix} is what the legacy file was called, minus the
         * appliance suffix.
         */
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
            // Manual overrides: names auto-derivation would get wrong.
            add(SMOPItems.COOKED_NIRASMO_MEAT.get(), "Nirasmo Cooked Meat");
            // Registry id is "hellhippo_armor" — one word, inherited from 1.20.1 and kept so the
            // legacy recipe and model files port across unchanged. Auto-derivation would read it
            // literally and call it "Hellhippo Armor".
            add(SMOPItems.HELL_HIPPO_ARMOR.get(), "Hell Hippo Armor");
            // Registry id is "gt". Auto-derivation would call it "Gt"; the 1.20.1 lang said
            // "Grant Tyrant", which was a typo. It is Grand, with a d.
            add(SMOPEntities.GT.get(), "Grand Tyrant");

            // Everything else: krifto_meat -> "Krifto Meat", tango_feather -> "Tango Feather", ...
            autoItemNames(SMOPItems.ITEMS);
            autoBlockNames(SMOPBlocks.BLOCKS);
            autoEntityNames(SMOPEntities.ENTITY_TYPES);

            add("creativetab.smop_tab", "Spectacular Mobs Of Peligoro");

            add("effect.smop.fear", "Fear");

            add("subtitles.gt_roar", "Grand Tyrant roars");
            add("subtitles.krifto_squawk", "Kriftognathus squawks");

            // Keybinds. The category key is derived from its Identifier: key.category.<ns>.<path>.
            add("key.category.smop.main", "Spectacular Mobs of Peligoro");
            add("key.smop.attack", "Mounted Attack");
            add("key.smop.fear", "Intimidate");
            add("key.smop.open_inventory", "Open Mount Inventory");
            add("key.smop.descend", "Descend");
        }
    }

    private SMOPDatagen() {}
}
