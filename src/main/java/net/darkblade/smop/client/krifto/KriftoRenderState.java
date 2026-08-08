package net.darkblade.smop.client.krifto;

import net.darkblade.deluxelib.client.render.DeluxeEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

/**
 * Carries the things the Krifto's texture and extra layers depend on — its sex, the biome it
 * hatched in, and whatever it is currently carrying off after a heist.
 *
 * <p>26.1's {@code getTextureLocation} receives the render state rather than the entity, so anything
 * the texture choice reads has to be extracted onto the state first.
 */
public class KriftoRenderState extends DeluxeEntityRenderState {

    public boolean male;
    public String spawnBiome = "default";
    /** Resolved from {@code KriftognathusEntity#getStolenItem()} — see {@link StolenItemLayer}. */
    public final ItemStackRenderState stolenItem = new ItemStackRenderState();
}
