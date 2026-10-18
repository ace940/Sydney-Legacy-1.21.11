package me.aidan.sydney.mixins;

import me.aidan.sydney.Sydney;
import me.aidan.sydney.modules.impl.visuals.EntityModifierModule;
import me.aidan.sydney.modules.impl.visuals.NoRenderModule;
import me.aidan.sydney.utils.IMinecraft;
import me.aidan.sydney.utils.mixins.ILivingEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.Arm;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends EntityRenderer<T, S> implements IMinecraft {
    public LivingEntityRendererMixin(EntityRendererFactory.Context context) { super(context); }

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void render(S livingEntityRenderState, MatrixStack matrixStack, OrderedRenderCommandQueue queue, CameraRenderState cameraRenderState, CallbackInfo info) {
        if (Sydney.MODULE_MANAGER.getModule(NoRenderModule.class).isToggled() && Sydney.MODULE_MANAGER.getModule(NoRenderModule.class).corpses.getValue() && livingEntityRenderState.deathTime > 0) {
            info.cancel();
        }
    }

    @ModifyArgs(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;scale(FFF)V", ordinal = 1))
    private void render$scale(Args args, S livingEntityRenderState, MatrixStack matrixStack, OrderedRenderCommandQueue queue, CameraRenderState cameraRenderState) {
        if (Sydney.MODULE_MANAGER.getModule(EntityModifierModule.class).isToggled() && Sydney.MODULE_MANAGER.getModule(EntityModifierModule.class).players.getValue()) {
            if (!((Object) this instanceof PlayerEntityRenderer)) return;

            args.set(0, -Sydney.MODULE_MANAGER.getModule(EntityModifierModule.class).playerScale.getValue().floatValue());
            args.set(1, -Sydney.MODULE_MANAGER.getModule(EntityModifierModule.class).playerScale.getValue().floatValue());
            args.set(2, Sydney.MODULE_MANAGER.getModule(EntityModifierModule.class).playerScale.getValue().floatValue());
        }
    }

    @ModifyArgs(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V"))
    private void render$render(Args args, S livingEntityRenderState, MatrixStack matrixStack, OrderedRenderCommandQueue queue, CameraRenderState cameraRenderState) {
        if (Sydney.MODULE_MANAGER.getModule(EntityModifierModule.class).isToggled() && Sydney.MODULE_MANAGER.getModule(EntityModifierModule.class).players.getValue()) {
            if (!((Object) this instanceof PlayerEntityRenderer)) return;

            args.set(4, Sydney.MODULE_MANAGER.getModule(EntityModifierModule.class).playerColor.getColor().getRGB());
        }
    }

    @ModifyVariable(method = "updateRenderState", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float updateRenderState$tickDelta(float tickDelta, T livingEntity) {
        return ((ILivingEntity) livingEntity).sydney$isStaticPlayerEntity() ? 1.0F : tickDelta;
    }

    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void updateRenderState$rotations(T livingEntity, S livingEntityRenderState, float f, CallbackInfo info) {
        if (livingEntity == mc.player && Sydney.ROTATION_MANAGER.inRenderTime()) {
            livingEntityRenderState.bodyYaw = Sydney.ROTATION_MANAGER.getRenderRotations()[0];
            livingEntityRenderState.relativeHeadYaw = MathHelper.wrapDegrees(Sydney.ROTATION_MANAGER.getRenderRotations()[0] - livingEntityRenderState.bodyYaw);
            livingEntityRenderState.pitch = Sydney.ROTATION_MANAGER.getRenderRotations()[1];
        }
    }

}
