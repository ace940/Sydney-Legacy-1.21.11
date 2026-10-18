package me.aidan.sydney.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
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
        // 1.21.9+ hands WorldRenderer its matrices and pops the world matrix before returning, so
        // the matrix stack at this point no longer carries the camera rotation. The new pipeline
        // bakes the transform into the vertices (`BufferBuilder.vertex(Matrix4f, ...)`), so the
        // camera rotation has to live in the matrix handed to the renderers rather than in the
        // legacy model view stack; handing them a stale stack made every world-space overlay draw
        // in raw camera-relative coordinates, which produced screen-filling triangles clipped
        // against the near plane.
        MatrixStack worldStack = new MatrixStack();
        worldStack.multiplyPositionMatrix(new Matrix4f(positionMatrix));

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
        // 1.21.9+ removed RenderSystem.getProjectionMatrix(); the matrices GameRenderer hands to
        // WorldRenderer are the authoritative ones: ordinal 0 is the projection (view bob and hurt
        // tilt are folded into it), ordinal 1 is the camera rotation. Renderer2D.project applies
        // LAST_PROJECTION_MATRIX * LAST_MODEL_MATRIX to a vector that already went through
        // LAST_WORLD_MATRIX, so the rotation belongs in LAST_WORLD_MATRIX and the model matrix stays
        // identity instead of double-applying the view matrix.
        Renderer2D.LAST_PROJECTION_MATRIX.set(projectionMatrix);
        Renderer2D.LAST_MODEL_MATRIX.identity();
        Renderer2D.LAST_WORLD_MATRIX.set(positionMatrix);

        if (System.getProperty("sydney.verifyProjection") != null) verifyProjection(projectionMatrix, positionMatrix);
    }

    /**
     * Debug aid: projects the block or entity under the crosshair through the same
     * projection * camera-rotation transform that the world-space renderer bakes into its
     * vertices. A correctly transformed target must land near the centre of the screen
     * (ndc ~ 0) with w > 0; the broken matrix space produced enormous ndc values instead.
     */
    private long lastVerify = 0;

    private void verifyProjection(org.joml.Matrix4f projection, org.joml.Matrix4f position) {
        if (client.player == null || client.crosshairTarget == null) return;
        long now = System.currentTimeMillis();
        if (now - lastVerify < 1000) return;
        lastVerify = now;

        net.minecraft.util.math.Vec3d target;
        if (client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult block) {
            target = block.getBlockPos().toCenterPos();
        } else if (client.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult entity) {
            target = entity.getEntity().getBoundingBox().getCenter();
        } else {
            return;
        }

        net.minecraft.util.math.Vec3d cam = client.gameRenderer.getCamera().getCameraPos();
        org.joml.Vector4f v = new org.joml.Vector4f((float) (target.x - cam.x), (float) (target.y - cam.y), (float) (target.z - cam.z), 1.0f);
        new org.joml.Matrix4f(projection).mul(position).transform(v);

        float w = v.w;
        Sydney.LOGGER.info("[verify] {} ndc=({}, {}, {}) w={} dist={}",
                client.crosshairTarget.getClass().getSimpleName(),
                String.format("%.3f", v.x / w), String.format("%.3f", v.y / w), String.format("%.3f", v.z / w),
                String.format("%.2f", w), String.format("%.1f", target.distanceTo(cam)));
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
