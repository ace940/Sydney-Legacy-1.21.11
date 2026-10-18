package me.aidan.sydney.mixins.accessors;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 1.21.9 turned RenderLayer construction into a package private factory, so the only way a
 * mod can build its own layer is through this invoker.
 */
@Mixin(RenderLayer.class)
public interface RenderLayerAccessor {
    @Invoker("of")
    static RenderLayer sydney$of(String name, RenderSetup setup) {
        throw new AssertionError();
    }
}
