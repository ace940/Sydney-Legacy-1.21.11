package me.aidan.sydney.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.aidan.sydney.Sydney;
import me.aidan.sydney.modules.impl.visuals.FullBrightModule;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 1.21.9 replaced the lightmap framebuffer with a lightmap UBO read by the lightmap shader, so the
 * Gamma mode no longer has a framebuffer to clear. The shader mixes its result towards a brightened
 * variant of itself by the "brightness factor" uniform (see core/lightmap.fsh), and pushing that
 * factor past the 1.0 the vanilla gamma slider can reach saturates the lightmap instead.
 *
 * <p>The old getDarknessFactor hook is gone as well: blindness is applied by
 * {@link BlindnessEffectFogModifierMixin} now.
 */
@Mixin(LightmapTextureManager.class)
public class LightmapTextureManagerMixin {
    @ModifyExpressionValue(method = "update", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"))
    private float update$brightnessFactor(float original) {
        if (Sydney.MODULE_MANAGER == null) return original;

        FullBrightModule module = Sydney.MODULE_MANAGER.getModule(FullBrightModule.class);
        if (module.isToggled() && module.mode.getValue().equalsIgnoreCase("Gamma")) {
            return 10.0f;
        }

        return original;
    }
}
