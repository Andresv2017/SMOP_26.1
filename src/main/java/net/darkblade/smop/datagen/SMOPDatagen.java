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
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.neoforged.neoforge.data.event.GatherDataEvent;

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

            // One fillet per fish.
            this.add(SMOPEntities.SALMON.get(), LootTable.lootTable()
                    .withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1))
                            .add(LootItem.lootTableItem(SMOPItems.RAW_SALMON.get())
                                    .apply(SetItemCountFunction.setCount(ConstantValue.exactly(1.0F))))));
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
