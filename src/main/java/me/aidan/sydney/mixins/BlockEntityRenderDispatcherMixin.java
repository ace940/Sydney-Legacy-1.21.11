package me.aidan.sydney.mixins;

import me.aidan.sydney.Sydney;
import me.aidan.sydney.modules.impl.visuals.NoRenderModule;
import me.aidan.sydney.utils.IMinecraft;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.9 split block entity rendering into a render state pass; BlockEntityRenderManager.render
 * is the equivalent of the old BlockEntityRenderDispatcher.render.
 */
@Mixin(BlockEntityRenderManager.class)
public class BlockEntityRenderDispatcherMixin implements IMinecraft {
    @Inject(method = "render(Lnet/minecraft/client/render/block/entity/state/BlockEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void sydney$render(BlockEntityRenderState renderState, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState, CallbackInfo info) {
        NoRenderModule module = Sydney.MODULE_MANAGER.getModule(NoRenderModule.class);
        if (!module.isToggled() || module.tileEntities.getValue().equals("Never")) return;

        BlockPos pos = renderState.pos;
        if (pos == null) return;

        if (module.tileEntities.getValue().equals("Always") || (module.tileEntities.getValue().equals("Distance") && mc.player != null
                && Math.sqrt(mc.player.squaredDistanceTo(pos.getX(), pos.getY(), pos.getZ())) > module.tileDistance.getValue().floatValue())) {
            info.cancel();
        }
    }
}
