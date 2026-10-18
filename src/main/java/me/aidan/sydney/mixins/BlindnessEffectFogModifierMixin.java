package me.aidan.sydney.mixins;

import me.aidan.sydney.Sydney;
import me.aidan.sydney.modules.impl.visuals.NoRenderModule;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.BlindnessEffectFogModifier;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The old BackgroundRenderer.getFogModifier() hook, now that fog modifiers are applied instead. */
@Mixin(BlindnessEffectFogModifier.class)
public class BlindnessEffectFogModifierMixin {
    @Inject(method = "applyStartEndModifier", at = @At("HEAD"), cancellable = true)
    private void sydney$applyStartEndModifier(FogData data, Camera camera, ClientWorld world, float tickDelta, RenderTickCounter tickCounter, CallbackInfo ci) {
        NoRenderModule noRender = Sydney.MODULE_MANAGER.getModule(NoRenderModule.class);
        if (noRender.isToggled() && noRender.blindness.getValue()) ci.cancel();
    }
}
