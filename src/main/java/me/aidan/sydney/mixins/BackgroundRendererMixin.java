package me.aidan.sydney.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.aidan.sydney.Sydney;
import me.aidan.sydney.modules.impl.visuals.AtmosphereModule;
import me.aidan.sydney.modules.impl.visuals.NoRenderModule;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.awt.*;

/**
 * 1.21.9 replaced the BackgroundRenderer/Fog pair with FogRenderer + FogData. The private
 * applyFog(ByteBuffer, int, Vector4f, float x6) writes the six fog distances into the fog UBO,
 * so its float arguments are the modern equivalent of the old Fog constructor arguments.
 */
@Mixin(FogRenderer.class)
public class BackgroundRendererMixin {
    @ModifyArgs(method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V", at = @At(value = "INVOKE", target = "Ljava/nio/ByteBuffer;putFloat(F)Ljava/nio/ByteBuffer;"))
    private void sydney$applyFog(Args args) {
        NoRenderModule noRender = Sydney.MODULE_MANAGER.getModule(NoRenderModule.class);
        AtmosphereModule atmosphere = Sydney.MODULE_MANAGER.getModule(AtmosphereModule.class);

        if (noRender.isToggled() && noRender.fog.getValue()) {
            // Push every fog plane far past the render distance so nothing is fogged.
            for (int i = 3; i <= 8; i++) args.set(i, 1_000_000.0f);
            return;
        }

        if (atmosphere.isToggled() && atmosphere.modifyFog.getValue()) {
            float start = atmosphere.fogStart.getValue().floatValue();
            float end = atmosphere.fogEnd.getValue().floatValue();

            // (environmental start, environmental end, render distance start, render distance end, sky end, cloud end)
            args.set(3, start);
            args.set(4, end);
            args.set(5, start);
            args.set(6, end);
            args.set(7, end);
            args.set(8, end);
        }
    }

    @ModifyReturnValue(method = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;", at = @At("RETURN"))
    private Vector4f sydney$applyFogColor(Vector4f original, Camera camera, int i, RenderTickCounter tickCounter, float viewDistance, ClientWorld world) {
        AtmosphereModule atmosphere = Sydney.MODULE_MANAGER.getModule(AtmosphereModule.class);
        if (!atmosphere.isToggled() || !atmosphere.modifyFog.getValue()) return original;

        Color color = atmosphere.fogColor.getColor();
        return new Vector4f(color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f, color.getAlpha() / 255.0f);
    }
}
