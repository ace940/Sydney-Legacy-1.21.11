package me.aidan.sydney.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import me.aidan.sydney.Sydney;
import me.aidan.sydney.events.impl.RenderShaderEvent;
import me.aidan.sydney.modules.impl.visuals.BlockHighlightModule;
import me.aidan.sydney.modules.impl.visuals.FreecamModule;
import me.aidan.sydney.modules.impl.visuals.NoRenderModule;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.memory.ObjectAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    // 1.21.9+ hands WorldRenderer its matrices instead of the GameRenderer, and it takes a fog
    // buffer, the fog colour and the render-sky flag on top of what it used to. The entity render
    // pass also moved: entities are now collected into render states and pushed into an ordered
    // render command queue, so the old renderEntity injections live in EntityRenderManagerMixin.
    @Inject(method = "render", at = @At("HEAD"))
    private void render(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera, Matrix4f positionMatrix, Matrix4f basicProjectionMatrix, Matrix4f projectionMatrix, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo info, @Local(argsOnly = true, ordinal = 0) LocalBooleanRef blockOutline) {
        if (Sydney.MODULE_MANAGER != null && Sydney.MODULE_MANAGER.getModule(BlockHighlightModule.class).isToggled()) {
            blockOutline.set(false);
        }
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;updateCamera(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;Z)V"), index = 2)
    private boolean render$updateCamera(boolean spectator) {
        return Sydney.MODULE_MANAGER.getModule(FreecamModule.class).isToggled() || spectator;
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V", ordinal = 0, shift = At.Shift.BEFORE))
    private void render$swap(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera, Matrix4f positionMatrix, Matrix4f basicProjectionMatrix, Matrix4f projectionMatrix, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo info) {
        Sydney.EVENT_HANDLER.post(new RenderShaderEvent());
    }

    @Inject(method = "hasBlindnessOrDarkness(Lnet/minecraft/client/render/Camera;)Z", at = @At("HEAD"), cancellable = true)
    private void hasBlindnessOrDarkness(Camera camera, CallbackInfoReturnable<Boolean> info) {
        if (Sydney.MODULE_MANAGER.getModule(NoRenderModule.class).isToggled() && Sydney.MODULE_MANAGER.getModule(NoRenderModule.class).blindness.getValue()) {
            info.setReturnValue(false);
        }
    }

    @Inject(method = "onResized", at = @At("TAIL"))
    private void onResized(int width, int height, CallbackInfo info) {
        if (Sydney.SHADER_MANAGER != null) {
            Sydney.SHADER_MANAGER.resize(width, height);
        }
    }
}
