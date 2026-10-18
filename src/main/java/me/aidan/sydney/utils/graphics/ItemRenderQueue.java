package me.aidan.sydney.utils.graphics;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.RenderCommandQueue;
import net.minecraft.client.render.block.MovingBlockRenderState;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

import java.util.List;

/**
 * Draws a prepared {@link ItemRenderState} into a plain {@link VertexConsumerProvider}.
 *
 * <p>1.21.9 no longer renders items through vertex consumers: the item render state submits its
 * quads into an ordered render command queue that the frame graph executes later. Code that wants
 * the item geometry in its own buffers - such as the nametag module, which draws the held items in
 * world space - can hand the render state this queue, which writes the submitted quads straight
 * into the provider instead.
 */
public final class ItemRenderQueue implements OrderedRenderCommandQueue {
    private final VertexConsumerProvider provider;

    private ItemRenderQueue(VertexConsumerProvider provider) {
        this.provider = provider;
    }

    public static void draw(ItemRenderState state, MatrixStack matrices, VertexConsumerProvider provider, int light, int overlay) {
        state.render(matrices, new ItemRenderQueue(provider), light, overlay, 0);
    }

    @Override
    public void submitItem(MatrixStack matrices, ItemDisplayContext displayContext, int light, int overlay, int outlineColor, int[] tints, List<BakedQuad> quads, RenderLayer layer, ItemRenderState.Glint glint) {
        VertexConsumer consumer = provider.getBuffer(layer);
        MatrixStack.Entry entry = matrices.peek();

        for (BakedQuad quad : quads) {
            int tint = tint(tints, quad);

            float alpha = tint == ItemRenderer.NO_TINT ? 1.0F : ColorHelper.getAlpha(tint) / 255.0F;
            float red = tint == ItemRenderer.NO_TINT ? 1.0F : ColorHelper.getRed(tint) / 255.0F;
            float green = tint == ItemRenderer.NO_TINT ? 1.0F : ColorHelper.getGreen(tint) / 255.0F;
            float blue = tint == ItemRenderer.NO_TINT ? 1.0F : ColorHelper.getBlue(tint) / 255.0F;

            consumer.quad(entry, quad, red, green, blue, alpha, light, overlay);
        }
    }

    @Override
    public void submitCustom(MatrixStack matrices, RenderLayer layer, OrderedRenderCommandQueue.Custom customRenderer) {
        customRenderer.render(matrices.peek(), provider.getBuffer(layer));
    }

    @Override
    public RenderCommandQueue getBatchingQueue(int order) {
        return this;
    }

    private static int tint(int[] tints, BakedQuad quad) {
        if (!quad.hasTint() || tints == null) return ItemRenderer.NO_TINT;

        int index = quad.tintIndex();
        return index >= 0 && index < tints.length ? tints[index] : ItemRenderer.NO_TINT;
    }

    @Override
    public void submitCustom(OrderedRenderCommandQueue.LayeredCustom layeredCustom) { }

    @Override
    public void submitShadowPieces(MatrixStack matrices, float radius, List<EntityRenderState.ShadowPiece> shadowPieces) { }

    @Override
    public void submitLabel(MatrixStack matrices, Vec3d pos, int y, Text text, boolean dropShadow, int light, double squaredDistance, CameraRenderState cameraState) { }

    @Override
    public void submitText(MatrixStack matrices, float x, float y, OrderedText text, boolean dropShadow, net.minecraft.client.font.TextRenderer.TextLayerType layerType, int light, int color, int backgroundColor, int outlineColor) { }

    @Override
    public void submitFire(MatrixStack matrices, EntityRenderState state, Quaternionf rotation) { }

    @Override
    public void submitLeash(MatrixStack matrices, EntityRenderState.LeashData leashData) { }

    @Override
    public <S> void submitModel(net.minecraft.client.model.Model<? super S> model, S state, MatrixStack matrices, RenderLayer layer, int light, int overlay, int tintedColor, Sprite sprite, int outlineColor, ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay) { }

    @Override
    public void submitModelPart(net.minecraft.client.model.ModelPart part, MatrixStack matrices, RenderLayer layer, int light, int overlay, Sprite sprite, boolean bl, boolean bl2, int i, ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay, int j) { }

    @Override
    public void submitBlock(MatrixStack matrices, BlockState state, int light, int overlay, int outlineColor) { }

    @Override
    public void submitMovingBlock(MatrixStack matrices, MovingBlockRenderState state) { }

    @Override
    public void submitBlockStateModel(MatrixStack matrices, RenderLayer layer, BlockStateModel model, float r, float g, float b, int light, int overlay, int outlineColor) { }
}
