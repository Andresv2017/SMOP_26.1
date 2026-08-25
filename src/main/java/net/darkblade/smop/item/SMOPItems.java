package net.darkblade.smop.item;

import net.darkblade.smop.SMOP;
import net.darkblade.smop.block.SMOPBlocks;
import net.darkblade.smop.entity.SMOPEntities;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Item registry and the mod's creative tab.
 *
 * <p>Only the plain drop/food items exist so far; weapons, armor, spawn eggs and block items are
 * added as their mobs and blocks land (see PORT_ANALYSIS.md for the phase order). The tab's
 * {@code displayItems} lambda is the natural place to extend, since it runs long after every
 * register has finished loading.
 */
public final class SMOPItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SMOP.MOD_ID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SMOP.MOD_ID);

    // ───────────────────────────────────────────────────── MISC / MATERIALS ─────

    public static final DeferredItem<Item> NIRASMO_BEAK =
            ITEMS.registerSimpleItem("nirasmo_beak");

    public static final DeferredItem<Item> KRIFTO_WING =
            ITEMS.registerSimpleItem("krifto_wing");

    public static final DeferredItem<Item> TANGO_FEATHER =
            ITEMS.registerSimpleItem("tango_feather");

    // ───────────────────────────────────────────────────── FOOD ─────

    public static final DeferredItem<Item> HELL_HIPPO_RAW_MEAT =
            ITEMS.registerSimpleItem("hell_hippo_raw_meat", () -> food(SMOPFoods.HELL_HIPPO_RAW_MEAT));

    public static final DeferredItem<Item> HELL_HIPPO_COOKED_MEAT =
            ITEMS.registerSimpleItem("hell_hippo_cooked_meat", () -> food(SMOPFoods.HELL_HIPPO_COOKED_MEAT));

    public static final DeferredItem<Item> RAW_SALMON =
            ITEMS.registerSimpleItem("raw_salmon", () -> food(SMOPFoods.RAW_SALMON));

    public static final DeferredItem<Item> NIRASMO_MEAT =
            ITEMS.registerSimpleItem("nirasmo_meat", () -> food(SMOPFoods.NIRASMO_MEAT));

    public static final DeferredItem<Item> COOKED_NIRASMO_MEAT =
            ITEMS.registerSimpleItem("cooked_nirasmo_meat", () -> food(SMOPFoods.COOKED_NIRASMO_MEAT));

    public static final DeferredItem<Item> KRIFTO_MEAT =
            ITEMS.registerSimpleItem("krifto_meat", () -> food(SMOPFoods.KRIFTO_MEAT));

    public static final DeferredItem<Item> COOKED_KRIFTO_MEAT =
            ITEMS.registerSimpleItem("cooked_krifto_meat", () -> food(SMOPFoods.COOKED_KRIFTO_MEAT));

    public static final DeferredItem<Item> TANGO_LEG =
            ITEMS.registerSimpleItem("tango_leg", () -> food(SMOPFoods.TANGO_LEG));

    public static final DeferredItem<Item> COOKED_TANGO_LEG =
            ITEMS.registerSimpleItem("cooked_tango_leg", () -> food(SMOPFoods.COOKED_TANGO_LEG));

    /**
     * Returning the empty bowl after eating is the {@code usingConvertsTo} property, not a
     * {@code BowlFoodItem} subclass.
     */
    public static final DeferredItem<Item> KRIFTO_STEW =
            ITEMS.registerSimpleItem("krifto_stew",
                    () -> food(SMOPFoods.KRIFTO_STEW).stacksTo(1).usingConvertsTo(Items.BOWL));

    // ───────────────────────────────────────────────────── BLOCK ITEMS ─────

    public static final DeferredItem<net.minecraft.world.item.BlockItem> TANGOFTERO_EGG =
            ITEMS.registerSimpleBlockItem("tangoftero_egg", SMOPBlocks.TANGOFTERO_EGG);

    public static final DeferredItem<net.minecraft.world.item.BlockItem> KRIFTO_EGG =
            ITEMS.registerSimpleBlockItem("krifto_egg", SMOPBlocks.KRIFTO_EGG);

    public static final DeferredItem<net.minecraft.world.item.BlockItem> SALMON_ROE_EGGS =
            ITEMS.registerSimpleBlockItem("salmon_roe_eggs", SMOPBlocks.SALMON_ROE_EGGS);

    public static final DeferredItem<net.minecraft.world.item.BlockItem> NIRAS_EGG =
            ITEMS.registerSimpleBlockItem("niras_egg", SMOPBlocks.NIRAS_EGG);

    // ───────────────────────────────────────────────────── DIG DROPS ─────

    /**
     * What a salmon turns up per substrate, picked at random from the matching list — see
     * {@code SalmonDigGoal}. Plain item lists rather than loot tables because the salmon is not
     * breaking the block as a player would: the drop is what it <em>found</em> buried, not what the
     * block itself yields.
     */
    /**
     * The rare find: what was buried long before anyone was around to bury it.
     *
     * <p>Rolled <em>before</em> the substrate pool rather than merged into it — see
     * {@code SalmonDigGoal#dropFor}. Merging would have been the smaller edit and the wrong one:
     * the pools hold two to four items each, so dropping a dozen sherds in would have made the
     * relic the ordinary result and the stick the rarity, which is backwards.
     *
     * <p>Vanilla's sherds are deliberate rather than decorative. Sand and gravel are the two
     * substrates its own archaeology uses, so a fish turning them over and surfacing pottery reads
     * as the same act the player already knows from suspicious blocks — and {@code ANGLER} in
     * particular is the sherd about fishing. Bone covers what pottery does not.
     */
    public static final java.util.List<Item> RELIC_DIG_DROPS = java.util.List.of(
            Items.ANGLER_POTTERY_SHERD,
            Items.SHELTER_POTTERY_SHERD,
            Items.SNORT_POTTERY_SHERD,
            Items.HOWL_POTTERY_SHERD,
            Items.MOURNER_POTTERY_SHERD,
            Items.SKULL_POTTERY_SHERD,
            Items.DANGER_POTTERY_SHERD,
            Items.EXPLORER_POTTERY_SHERD,
            Items.BONE);

    public static final java.util.List<Item> SAND_DIG_DROPS = java.util.List.of(Items.STICK, Items.SANDSTONE);
    public static final java.util.List<Item> GRAVEL_DIG_DROPS = java.util.List.of(Items.FLINT);
    public static final java.util.List<Item> MUD_DIG_DROPS = java.util.List.of(Items.CLAY_BALL);
    public static final java.util.List<Item> DIRT_DIG_DROPS = java.util.List.of(Items.POTATO, Items.CARROT);

    // ───────────────────────────────────────────────────── ARMOUR ─────

    /**
     * Barding for a saddled Hell Hippo. Worn in {@link EquipmentSlot#BODY}, like horse armour.
     *
     * <p><b>The +5 armour is a data component, not code.</b> The modifier rides on the item and vanilla
     * applies and reverts it with the equipment, so the number below is the whole of it.
     *
     * <p>{@code setAllowedEntities} restricts it to this one mob, which is also what stops it being
     * strapped to a horse. Resolving {@code SMOPEntities} inside the properties lambda is safe for the
     * same reason the spawn eggs below can do it: the lambda runs while the item registry is being
     * filled, by which point entity types exist.
     *
     * <p>{@code setDamageOnHurt(false)} matches horse armour — barding absorbs without wearing out.
     */
    public static final DeferredItem<Item> HELL_HIPPO_ARMOR =
            ITEMS.registerItem("hellhippo_armor", props -> new Item(props
                    .stacksTo(1)
                    .attributes(ItemAttributeModifiers.builder()
                            .add(Attributes.ARMOR,
                                    new AttributeModifier(SMOP.id("armor.hell_hippo"),
                                            5.0D, AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.BODY)
                            .build())
                    .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.BODY)
                            .setEquipSound(SoundEvents.HORSE_ARMOR)
                            .setAllowedEntities(SMOPEntities.HELL_HIPPO.get())
                            .setDamageOnHurt(false)
                            .build())));

    // ───────────────────────────────────────────────────── WEAPONS ─────

    /**
     * A throwable javelin, and the stack size is the balance lever.
     *
     * <p>Four is a handful: enough that the spear is ammunition rather than a single precious trident,
     * few enough that you notice spending one. A stack of sixteen makes throwing one weightless. The
     * melee numbers say the rest — a poor sword and a good javelin, and the slow swing is what says so.
     */
    public static final DeferredItem<Item> NIRAS_SPEAR =
            ITEMS.registerItem("niras_spear", props -> new NirasSpearItem(props
                    .stacksTo(4)
                    .attributes(ItemAttributeModifiers.builder()
                            .add(Attributes.ATTACK_DAMAGE,
                                    new AttributeModifier(SMOP.id("niras_spear.attack_damage"),
                                            3.0D, AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            .add(Attributes.ATTACK_SPEED,
                                    new AttributeModifier(SMOP.id("niras_spear.attack_speed"),
                                            -3.0D, AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            .build())));

    // ───────────────────────────────────────────────────── AMMUNITION ─────

    /**
     * Fletched with a Tangoftero feather. Loadable by any bow or crossbow through the
     * {@code minecraft:arrows} item tag, which is where that permission lives — the class only
     * decides what flies.
     */
    public static final DeferredItem<Item> TANGO_ARROW =
            ITEMS.registerItem("tango_arrow", TangoArrowItem::new);

    // ───────────────────────────────────────────────────── SPAWN EGGS ─────

    /**
     * {@link SpawnEggItem} takes only properties — which entity it spawns is a data component, applied
     * here via {@code Item.Properties#spawnEgg}, and the colours come from the entity's own definition.
     */
    public static final DeferredItem<SpawnEggItem> TANGOFTERO_SPAWN_EGG =
            ITEMS.registerItem("tangoftero_spawn_egg",
                    props -> new SpawnEggItem(props.spawnEgg(SMOPEntities.TANGOFTERO.get())));

    public static final DeferredItem<SpawnEggItem> KRIFTOGNATHUS_SPAWN_EGG =
            ITEMS.registerItem("kriftognathus_spawn_egg",
                    props -> new SpawnEggItem(props.spawnEgg(SMOPEntities.KRIFTOGNATHUS.get())));

    public static final DeferredItem<SpawnEggItem> SALMON_SPAWN_EGG =
            ITEMS.registerItem("salmon_spawn_egg",
                    props -> new SpawnEggItem(props.spawnEgg(SMOPEntities.SALMON.get())));

    public static final DeferredItem<SpawnEggItem> HELL_HIPPO_SPAWN_EGG =
            ITEMS.registerItem("hell_hippo_spawn_egg",
                    props -> new SpawnEggItem(props.spawnEgg(SMOPEntities.HELL_HIPPO.get())));

    public static final DeferredItem<SpawnEggItem> NIRASMOSAURUS_SPAWN_EGG =
            ITEMS.registerItem("nirasmosaurus_spawn_egg",
                    props -> new SpawnEggItem(props.spawnEgg(SMOPEntities.NIRASMOSAURUS.get())));

    // ───────────────────────────────────────────────────── CREATIVE TAB ─────

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SMOP_TAB =
            CREATIVE_TABS.register("smop_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("creativetab.smop_tab"))
                    .icon(() -> new ItemStack(HELL_HIPPO_COOKED_MEAT.get()))
                    .displayItems((params, output) -> {
                        // FOOD
                        output.accept(HELL_HIPPO_RAW_MEAT.get());
                        output.accept(HELL_HIPPO_COOKED_MEAT.get());
                        output.accept(RAW_SALMON.get());
                        output.accept(NIRASMO_MEAT.get());
                        output.accept(COOKED_NIRASMO_MEAT.get());
                        output.accept(KRIFTO_MEAT.get());
                        output.accept(COOKED_KRIFTO_MEAT.get());
                        output.accept(TANGO_LEG.get());
                        output.accept(COOKED_TANGO_LEG.get());
                        output.accept(KRIFTO_STEW.get());

                        // ARMOUR
                        output.accept(HELL_HIPPO_ARMOR.get());

                        // WEAPONS
                        output.accept(NIRAS_SPEAR.get());

                        // AMMUNITION
                        output.accept(TANGO_ARROW.get());

                        // MISC / MATERIALS
                        output.accept(NIRASMO_BEAK.get());
                        output.accept(KRIFTO_WING.get());
                        output.accept(TANGO_FEATHER.get());

                        // EGGS (BLOCK ITEMS)
                        output.accept(TANGOFTERO_EGG.get());
                        output.accept(KRIFTO_EGG.get());
                        output.accept(SALMON_ROE_EGGS.get());
                        output.accept(NIRAS_EGG.get());

                        // SPAWN EGGS
                        output.accept(TANGOFTERO_SPAWN_EGG.get());
                        output.accept(KRIFTOGNATHUS_SPAWN_EGG.get());
                        output.accept(SALMON_SPAWN_EGG.get());
                        output.accept(HELL_HIPPO_SPAWN_EGG.get());
                        output.accept(NIRASMOSAURUS_SPAWN_EGG.get());
                    })
                    .build());

    private static Item.Properties food(SMOPFoods.Entry entry) {
        return entry.applyTo(new Item.Properties());
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
    }

    private SMOPItems() {}
}
