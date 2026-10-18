package me.aidan.sydney.mixins.accessors;

import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderSetup.class)
public interface RenderSetupOutputTargetAccessor {
    @Accessor("outputTarget")
    OutputTarget sydney$getOutputTarget();
}
