package net.darkblade.smop.item;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;

/**
 * Nutrition/saturation and eat-time side effects for SMOP's edible items.
 *
 * <p>{@code FoodProperties} is a bare {@code (nutrition, saturation, canAlwaysEat)} record: its
 * builder has neither {@code .effect(...)} nor {@code .meat()}.
 * <ul>
 *   <li>Eat-time status effects moved to the {@link Consumable} component — hence the
 *       {@link Entry} pairing below, fed to {@code Item.Properties#food(FoodProperties, Consumable)}.</li>
 *   <li>{@code .meat()} is gone entirely. Its only gameplay effect was letting wolves be fed the
 *       item, which is now driven by the {@code minecraft:wolf_food} item tag — see
 *       {@code data/minecraft/tags/item/wolf_food.json}.</li>
 * </ul>
 *
 * <p>Also note {@code MobEffects.DAMAGE_BOOST} was renamed to {@link MobEffects#STRENGTH}, and the
 * {@code MobEffects} constants are {@code Holder<MobEffect>} rather than raw effects.
 */
public final class SMOPFoods {

    /** A food's nutrition values paired with the consume behaviour that carries its side effects. */
    public record Entry(FoodProperties properties, Consumable consumable) {
        /** Applies both halves to an item's properties. */
        public Item.Properties applyTo(Item.Properties props) {
            return props.food(this.properties, this.consumable);
        }
    }

    // ───────────────────────────────────────────────────── HELL HIPPO ─────

    public static final Entry HELL_HIPPO_COOKED_MEAT =
            new Entry(food(4, 0.5F), withChanceEffect(MobEffects.STRENGTH, 200, 0.1F));

    public static final Entry HELL_HIPPO_RAW_MEAT =
            new Entry(food(2, 0.3F), withChanceEffect(MobEffects.HUNGER, 200, 0.1F));

    // ───────────────────────────────────────────────────── SALMON ─────

    public static final Entry RAW_SALMON =
            new Entry(food(2, 0.5F), withChanceEffect(MobEffects.HUNGER, 200, 0.1F));

    // ───────────────────────────────────────────────────── NIRASMOSAURUS ─────

    public static final Entry NIRASMO_MEAT =
            new Entry(food(2, 0.2F), withChanceEffect(MobEffects.HUNGER, 200, 0.1F));

    public static final Entry COOKED_NIRASMO_MEAT =
            new Entry(food(3, 0.4F), Consumables.DEFAULT_FOOD);

    // ───────────────────────────────────────────────────── KRIFTOGNATHUS ─────

    public static final Entry KRIFTO_MEAT =
            new Entry(food(2, 0.3F), withChanceEffect(MobEffects.HUNGER, 200, 0.1F));

    public static final Entry COOKED_KRIFTO_MEAT =
            new Entry(food(6, 0.8F), Consumables.DEFAULT_FOOD);

    public static final Entry KRIFTO_STEW =
            new Entry(food(8, 0.8F), Consumables.DEFAULT_FOOD);

    // ───────────────────────────────────────────────────── TANGOFTERO ─────

    public static final Entry TANGO_LEG =
            new Entry(food(3, 0.4F), withChanceEffect(MobEffects.HUNGER, 200, 0.1F));

    public static final Entry COOKED_TANGO_LEG =
            new Entry(food(7, 0.9F), Consumables.DEFAULT_FOOD);

    private static FoodProperties food(int nutrition, float saturationModifier) {
        return new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturationModifier).build();
    }

    private static Consumable withChanceEffect(Holder<MobEffect> effect, int durationTicks, float probability) {
        return Consumables.defaultFood()
                .onConsume(new ApplyStatusEffectsConsumeEffect(
                        new MobEffectInstance(effect, durationTicks), probability))
                .build();
    }

    private SMOPFoods() {}
}
