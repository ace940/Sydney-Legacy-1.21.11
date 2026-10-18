package me.aidan.sydney.mixins.accessors;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderLayer.class)
public interface RenderLayerSetupAccessor {
    @Accessor("renderSetup")
    RenderSetup sydney$getRenderSetup();
}
