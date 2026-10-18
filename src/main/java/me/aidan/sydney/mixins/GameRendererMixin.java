package me.aidan.sydney.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import me.aidan.sydney.Sydney;
import me.aidan.sydney.events.impl.RenderShaderEvent;
import me.aidan.sydney.events.impl.RenderWorldEvent;
import me.aidan.sydney.modules.impl.miscellaneous.FOVModifierModule;
import me.aidan.sydney.modules.impl.player.NoEntityTraceModule;
import me.aidan.sydney.modules.impl.visuals.AspectRatioModule;
import me.aidan.sydney.modules.impl.visuals.FreecamModule;
import me.aidan.sydney.modules.impl.visuals.NoRenderModule;
import me.aidan.sydney.utils.graphics.Renderer2D;
import me.aidan.sydney.utils.graphics.Renderer3D;
import me.aidan.sydney.utils.minecraft.WorldUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow @Final private MinecraftClient client;

    @Shadow public abstract float getFarPlaneDistance();

    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void renderWorld$HEAD(RenderTickCounter tickCounter, CallbackInfo info) {
        Renderer3D.prepare();
    }

    @Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/memory/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V", shift = At.Shift.AFTER))
    private void renderWorld$swap(RenderTickCounter tickCounter, CallbackInfo info, @Local(ordinal = 1) Matrix4f positionMatrix, @Local(ordinal = 0) float tickDelta, @Local MatrixStack matrixStack) {
        // The matrices passed to WorldRenderer are already the world projection and inverse
        // camera rotation. The custom debug pipelines consume that transform through the global
        // projection buffer, so overlay vertices must remain in camera-relative coordinates with
        // an identity per-vertex matrix. Applying positionMatrix here rotates them a second time,
        // which causes the large screen-spanning triangles in ESP/fill overlays.
        MatrixStack worldStack = new MatrixStack();

        Sydney.EVENT_HANDLER.post(new RenderWorldEvent(worldStack, tickDelta));

        Renderer3D.draw(Renderer3D.QUADS, Renderer3D.DEBUG_LINES, false);
        Renderer3D.draw(Renderer3D.SHINE_QUADS, Renderer3D.SHINE_DEBUG_LINES, true);

        Sydney.EVENT_HANDLER.post(new RenderWorldEvent.Post(worldStack, tickDelta));
    }

    @Inject(method = "renderWorld", at = @At("TAIL"))
    private void renderWorld$TAIL(RenderTickCounter renderTickCounter, CallbackInfo info) {
        Sydney.EVENT_HANDLER.post(new RenderShaderEvent.Post());
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void tiltViewWhenHurt(CallbackInfo info) {
        if (Sydney.MODULE_MANAGER.getModule(NoRenderModule.class).isToggled() && Sydney.MODULE_MANAGER.getModule(NoRenderModule.class).hurtCamera.getValue()) {
            info.cancel();
        }
    }

    @Inject(method = "updateCrosshairTarget", at = @At("HEAD"), cancellable = true)
    private void updateCrosshairTarget(float tickDelta, CallbackInfo info) {
        // 1.21.11 inlined findCrosshairTarget into updateCrosshairTarget, so both the freecam
        // override and NoEntityTrace now take over the whole method instead of one call site.
        if (Sydney.MODULE_MANAGER.getModule(FreecamModule.class).isToggled()) {
            FreecamModule freecam = Sydney.MODULE_MANAGER.getModule(FreecamModule.class);
            client.crosshairTarget = WorldUtils.getRaytraceTarget(freecam.getFreeYaw(), freecam.getFreePitch(), freecam.getFreeX(), freecam.getFreeY(), freecam.getFreeZ());
            info.cancel();
            return;
        }

        if (client.player != null && client.world != null
                && Sydney.MODULE_MANAGER.getModule(NoEntityTraceModule.class).isToggled()
                && Sydney.MODULE_MANAGER.getModule(NoEntityTraceModule.class).shouldIgnore()) {
            Vec3d start = client.player.getEyePos();
            Vec3d end = start.add(client.player.getRotationVec(tickDelta).multiply(client.player.getBlockInteractionRange()));
            client.crosshairTarget = client.world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, client.player));
            info.cancel();
        }
    }

    @Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true)
    private void showFloatingItem(ItemStack floatingItem, CallbackInfo info) {
        if (Sydney.MODULE_MANAGER.getModule(NoRenderModule.class).isToggled() && Sydney.MODULE_MANAGER.getModule(NoRenderModule.class).totemAnimation.getValue()) {
            info.cancel();
        }
    }

    @Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/memory/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V", shift = At.Shift.AFTER))
    private void renderWorld(RenderTickCounter tickCounter, CallbackInfo info, @Local(ordinal = 0) Matrix4f projectionMatrix, @Local(ordinal = 1) Matrix4f positionMatrix) {
        // 1.21.9+ removed RenderSystem.getProjectionMatrix(); retain the same world transform
        // the renderer gives to its command pipelines. Projection already includes bob/hurt tilt,
        // and positionMatrix is the inverse camera rotation.
        Renderer2D.LAST_PROJECTION_MATRIX.set(projectionMatrix);
        Renderer2D.LAST_MODEL_MATRIX.identity();
        Renderer2D.LAST_WORLD_MATRIX.set(positionMatrix);
    }


    @Inject(method = "getFov", at = @At("TAIL"), cancellable = true)
    private void getFOV(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> info) {
        FOVModifierModule module = Sydney.MODULE_MANAGER.getModule(FOVModifierModule.class);
        if(module.isToggled()) {
            if(info.getReturnValue() == 70 && !module.items.getValue()) return;
            info.setReturnValue(module.fov.getValue().floatValue());
        }
    }

    @Inject(method = "getBasicProjectionMatrix",at = @At("TAIL"), cancellable = true)
    public void getBasicProjectionMatrix(float fovDegrees, CallbackInfoReturnable<Matrix4f> info) {
        if (Sydney.MODULE_MANAGER.getModule(AspectRatioModule.class).isToggled()) {
            MatrixStack matrixStack = new MatrixStack();
            matrixStack.peek().getPositionMatrix().identity();

            matrixStack.peek().getPositionMatrix().mul(new Matrix4f().setPerspective((float)(fovDegrees * 0.01745329238474369), Sydney.MODULE_MANAGER.getModule(AspectRatioModule.class).ratio.getValue().floatValue(), 0.05f, getFarPlaneDistance()));
            info.setReturnValue(matrixStack.peek().getPositionMatrix());
        }
    }
}
