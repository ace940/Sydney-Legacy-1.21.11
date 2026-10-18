package me.aidan.sydney.utils.graphics;

import me.aidan.sydney.Sydney;
import me.aidan.sydney.modules.impl.visuals.NoInterpolationModule;
import me.aidan.sydney.utils.IMinecraft;
import me.aidan.sydney.mixins.accessors.RenderLayerSetupAccessor;
import me.aidan.sydney.mixins.accessors.RenderSetupOutputTargetAccessor;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.RenderCommandQueue;
import net.minecraft.client.render.block.MovingBlockRenderState;
import net.minecraft.client.render.entity.EndCrystalEntityRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.EndCrystalEntityRenderState;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.*;

import java.awt.*;
import java.util.List;

public class ModelRenderer implements IMinecraft {
    private static Render render;
    private static Matrix4f matrix4f;
    private static Vec3d offset;
    private static Vec3d camera;
    private static final CameraRenderState CAMERA_STATE = new CameraRenderState();

    public static void renderModel(Entity entity, float scale, float tickDelta, Render render) {
        renderModel(entity, false, scale, tickDelta, render);
    }

    public static void renderModel(Entity entity, boolean staticEntity, float scale, float tickDelta, Render render) {
        ModelRenderer.render = render;
        ModelRenderer.camera = mc.gameRenderer.getCamera().getCameraPos();

        if (!Sydney.MODULE_MANAGER.getModule(NoInterpolationModule.class).isToggled()) ModelRenderer.offset = new Vec3d(MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()), MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()), MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()));
        else ModelRenderer.offset = new Vec3d(entity.getX(), entity.getY(), entity.getZ());

        EntityRenderer<?, ?> renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
        EntityRenderState renderState = ((EntityRenderer<Entity, ?>) renderer).getAndUpdateRenderState(entity, tickDelta);

        MatrixStack matrices = new MatrixStack();
        ModelRenderer.matrix4f = matrices.peek().getPositionMatrix();

        matrices.push();
        matrices.scale(scale, scale, scale);

        // 1.21.9 sends entities through an ordered render command queue instead of a
        // VertexConsumerProvider, so the model vertices are captured by handing the renderers a
        // queue implementation that renders the models into the mod's own vertex consumer.
        if (renderer instanceof LivingEntityRenderer<?, ?, ?> livingRenderer && renderState instanceof LivingEntityRenderState state) {
            ((LivingEntityRenderer) livingRenderer).render(state, matrices, CaptureQueue.INSTANCE, CAMERA_STATE);
        }

        if (renderer instanceof EndCrystalEntityRenderer crystalRenderer && renderState instanceof EndCrystalEntityRenderState state) {
            crystalRenderer.render(state, matrices, CaptureQueue.INSTANCE, CAMERA_STATE);
        }

        matrices.pop();
    }

    /**
     * Draws the submitted entity models straight into {@link CustomVertexConsumer} so they can be
     * turned into the mod's own boxes. Everything that is not a model or a custom draw is ignored.
     */
    private static class CaptureQueue implements OrderedRenderCommandQueue {
        private static final CaptureQueue INSTANCE = new CaptureQueue();

        @Override
        @SuppressWarnings("unchecked")
        public <S> void submitModel(Model<? super S> model, S state, MatrixStack matrices, RenderLayer layer, int light, int overlay, int tintedColor, Sprite sprite, int outlineColor, ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay) {
            if (isItemEntityLayer(layer)) return;

            model.setAngles(state);
            ((Model<S>) model).render(matrices, CustomVertexConsumer.INSTANCE, light, overlay, tintedColor);
        }

        @Override
        public void submitCustom(MatrixStack matrices, RenderLayer layer, OrderedRenderCommandQueue.Custom customRenderer) {
            if (isItemEntityLayer(layer)) return;

            customRenderer.render(matrices.peek(), CustomVertexConsumer.INSTANCE);
        }

        @Override
        public RenderCommandQueue getBatchingQueue(int order) {
            return this;
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
        public void submitModelPart(net.minecraft.client.model.ModelPart part, MatrixStack matrices, RenderLayer layer, int light, int overlay, Sprite sprite, boolean bl, boolean bl2, int i, ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay, int j) { }

        @Override
        public void submitBlock(MatrixStack matrices, BlockState state, int light, int overlay, int outlineColor) { }

        @Override
        public void submitMovingBlock(MatrixStack matrices, MovingBlockRenderState state) { }

        @Override
        public void submitBlockStateModel(MatrixStack matrices, RenderLayer layer, BlockStateModel model, float r, float g, float b, int light, int overlay, int outlineColor) { }

        @Override
        public void submitItem(MatrixStack matrices, ItemDisplayContext displayContext, int light, int overlay, int outlineColor, int[] tints, List<BakedQuad> quads, RenderLayer layer, ItemRenderState.Glint glint) { }

        private static boolean isItemEntityLayer(RenderLayer layer) {
            if (layer == null) return false;

            // Cast through Object: RenderSetup is final, so javac refuses the direct cast to the mixin
            // interface even though the accessor is woven into it at runtime.
            RenderSetupOutputTargetAccessor setup = (RenderSetupOutputTargetAccessor) (Object) ((RenderLayerSetupAccessor) layer).sydney$getRenderSetup();
            return setup != null && setup.sydney$getOutputTarget() == OutputTarget.ITEM_ENTITY_TARGET;
        }
    }

    private static class CustomVertexConsumer implements VertexConsumer {
        public static final CustomVertexConsumer INSTANCE = new CustomVertexConsumer();
        private final float[] xs = new float[4];
        private final float[] ys = new float[4];
        private final float[] zs = new float[4];

        private int i = 0;

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            xs[i] = x;
            ys[i] = y;
            zs[i] = z;

            i++;

            if (i == 4) {
                List<Renderer3D.VertexCollection> quads = render.shine() ? Renderer3D.SHINE_QUADS : Renderer3D.QUADS;
                List<Renderer3D.VertexCollection> debugLines = render.shine() ? Renderer3D.SHINE_DEBUG_LINES : Renderer3D.DEBUG_LINES;

                if (render.fill) {
                    quads.add(new Renderer3D.VertexCollection(new Renderer3D.Vertex(matrix4f, (float) (offset.getX() + xs[0] - camera.getX()), (float) (offset.getY() + ys[0] - camera.getY()), (float) (offset.getZ() + zs[0] - camera.getZ()), render.fillColor().getRGB()),
                            new Renderer3D.Vertex(matrix4f, (float) (offset.getX() + xs[1] - camera.getX()), (float) (offset.getY() + ys[1] - camera.getY()), (float) (offset.getZ() + zs[1] - camera.getZ()), render.fillColor().getRGB()),
                            new Renderer3D.Vertex(matrix4f, (float) (offset.getX() + xs[2] - camera.getX()), (float) (offset.getY() + ys[2] - camera.getY()), (float) (offset.getZ() + zs[2] - camera.getZ()), render.fillColor().getRGB()),
                            new Renderer3D.Vertex(matrix4f, (float) (offset.getX() + xs[3] - camera.getX()), (float) (offset.getY() + ys[3] - camera.getY()), (float) (offset.getZ() + zs[3] - camera.getZ()), render.fillColor().getRGB())));
                }

                if (render.outline) {
                    debugLines.add(new Renderer3D.VertexCollection(new Renderer3D.Vertex(matrix4f, (float) (offset.x + xs[0] - camera.getX()), (float) (offset.y + ys[0] - camera.getY()), (float) (offset.z + zs[0] - camera.getZ()), render.outlineColor().getRGB()),
                            new Renderer3D.Vertex(matrix4f, (float) (offset.x + xs[1] - camera.getX()), (float) (offset.y + ys[1] - camera.getY()), (float) (offset.z + zs[1] - camera.getZ()), render.outlineColor().getRGB()),
                            new Renderer3D.Vertex(matrix4f, (float) (offset.x + xs[1] - camera.getX()), (float) (offset.y + ys[1] - camera.getY()), (float) (offset.z + zs[1] - camera.getZ()), render.outlineColor().getRGB()),
                            new Renderer3D.Vertex(matrix4f, (float) (offset.x + xs[2] - camera.getX()), (float) (offset.y + ys[2] - camera.getY()), (float) (offset.z + zs[2] - camera.getZ()), render.outlineColor().getRGB()),
                            new Renderer3D.Vertex(matrix4f, (float) (offset.x + xs[2] - camera.getX()), (float) (offset.y + ys[2] - camera.getY()), (float) (offset.z + zs[2] - camera.getZ()), render.outlineColor().getRGB()),
                            new Renderer3D.Vertex(matrix4f, (float) (offset.x + xs[3] - camera.getX()), (float) (offset.y + ys[3] - camera.getY()), (float) (offset.z + zs[3] - camera.getZ()), render.outlineColor().getRGB()),
                            new Renderer3D.Vertex(matrix4f, (float) (offset.x + xs[0] - camera.getX()), (float) (offset.y + ys[0] - camera.getY()), (float) (offset.z + zs[0] - camera.getZ()), render.outlineColor().getRGB()),
                            new Renderer3D.Vertex(matrix4f, (float) (offset.x + xs[0] - camera.getX()), (float) (offset.y + ys[0] - camera.getY()), (float) (offset.z + zs[0] - camera.getZ()), render.outlineColor().getRGB())));
                }

                i = 0;
            }

            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer color(int argb) {
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer lineWidth(float width) {
            return this;
        }
    }

    private static class EmptyVertexConsumer implements VertexConsumer {
        private static final EmptyVertexConsumer INSTANCE = new EmptyVertexConsumer();

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer color(int argb) {
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer lineWidth(float width) {
            return this;
        }
    }

    public record Render(boolean fill, Color fillColor, boolean outline, Color outlineColor, boolean shine) { }
}
