package me.aidan.sydney.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import me.aidan.sydney.Sydney;
import me.aidan.sydney.modules.impl.visuals.EntityModifierModule;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.math.ColorHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.awt.*;
import java.util.List;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Shadow
    private static int getTint(int[] tints, int index) {
        return 0;
    }

    /**
     * 1.21.9 split item rendering: renderItem now receives the already-baked quads instead of a
     * BlockStateModel, and every quad is drawn through the private renderBakedItemQuads helper. The
     * ItemColor override therefore wraps that helper instead of re-walking the model's quads.
     */
    @WrapOperation(method = "renderItem(Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II[ILjava/util/List;Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/render/item/ItemRenderState$Glint;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/ItemRenderer;renderBakedItemQuads(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;Ljava/util/List;[III)V"))
    private static void renderItem$renderBakedItemQuads(MatrixStack matrices, VertexConsumer vertexConsumer, List<BakedQuad> quads, int[] tints, int light, int overlay, Operation<Void> original, @Local(argsOnly = true) ItemDisplayContext renderMode) {
        EntityModifierModule module = Sydney.MODULE_MANAGER.getModule(EntityModifierModule.class);
        if (module.isToggled() && module.items.getValue() && (renderMode != ItemDisplayContext.GUI || module.itemGlobal.getValue())) {
            sydney$renderBakedItemQuads(matrices, vertexConsumer, quads, tints, module.itemColor.getColor(), light, overlay);
            return;
        }

        original.call(matrices, vertexConsumer, quads, tints, light, overlay);
    }

    @Unique
    private static void sydney$renderBakedItemQuads(MatrixStack matrices, VertexConsumer vertexConsumer, List<BakedQuad> quads, int[] tints, Color color, int light, int overlay) {
        MatrixStack.Entry entry = matrices.peek();

        for (BakedQuad bakedQuad : quads) {
            float f;
            float g;
            float h;
            float j;

            if (bakedQuad.hasTint()) {
                int i = getTint(tints, bakedQuad.tintIndex());
                f = (float) (color == null ? ColorHelper.getAlpha(i) : color.getAlpha()) / 255.0F;
                g = (float) (color == null ? ColorHelper.getRed(i) : color.getRed()) / 255.0F;
                h = (float) (color == null ? ColorHelper.getGreen(i) : color.getGreen()) / 255.0F;
                j = (float) (color == null ? ColorHelper.getBlue(i) : color.getBlue()) / 255.0F;
            } else {
                f = 1.0F;
                g = 1.0F;
                h = 1.0F;
                j = 1.0F;
            }

            vertexConsumer.quad(entry, bakedQuad, g, h, j, f, light, overlay);
        }
    }
}
