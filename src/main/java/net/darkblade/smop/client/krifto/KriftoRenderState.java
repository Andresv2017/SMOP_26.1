package net.darkblade.smop.client.krifto;

import net.darkblade.deluxelib.client.render.DeluxeEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public class KriftoRenderState extends DeluxeEntityRenderState {

    public boolean male;
    public String spawnBiome = "default";
    public final ItemStackRenderState stolenItem = new ItemStackRenderState();
}
