package me.aidan.sydney.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.aidan.sydney.Sydney;
import me.aidan.sydney.modules.impl.visuals.NoInterpolationModule;
import me.aidan.sydney.modules.impl.visuals.ShadersModule;
import me.aidan.sydney.utils.IMinecraft;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.ColorHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderManager.class)
public class EntityRenderManagerMixin implements IMinecraft {
    /**
     * 1.21.9 replaced the VertexConsumerProvider entity render path with render states that are
     * pushed into an ordered render command queue, so the two things this hook used to do - swap
     * the vertex consumers for the Shaders module and render non-interpolated - are now done by
     * editing the render state instead.
     */
    @ModifyReturnValue(method = "getAndUpdateRenderState", at = @At("RETURN"))
    private EntityRenderState sydney$getAndUpdateRenderState(EntityRenderState state, Entity entity, float tickProgress) {
        if (state == null || Sydney.MODULE_MANAGER == null) return state;

        // The engine's outline pass is the closest thing 1.21.9+ has to a coloured entity overlay:
        // the outline colour is carried on the render state, and a non-zero colour is also what
        // flips WorldRenderState.hasOutline on, which is what makes WorldRenderer run the entity
        // outline post effect at all. See ShadersModule#getOutlineColor.
        ShadersModule shaders = Sydney.MODULE_MANAGER.getModule(ShadersModule.class);
        if (shaders.isToggled() && shaders.isValidEntity(entity)) {
            state.outlineColor = ColorHelper.fullAlpha(shaders.getOutlineColor(entity).getRGB());
        }

        if (entity != mc.player && entity instanceof PlayerEntity && Sydney.MODULE_MANAGER.getModule(NoInterpolationModule.class).isToggled()) {
            state.x = entity.getX();
            state.y = entity.getY();
            state.z = entity.getZ();

            if (state instanceof LivingEntityRenderState livingState) {
                livingState.bodyYaw = entity.getYaw();
                livingState.relativeHeadYaw = 0.0f;
                livingState.pitch = entity.getPitch();
            }
        }

        return state;
    }
}
