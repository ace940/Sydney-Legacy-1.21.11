package me.aidan.sydney.mixins;

import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import me.aidan.sydney.Sydney;
import me.aidan.sydney.modules.impl.core.FontModule;
import me.aidan.sydney.utils.graphics.GuiRendering;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.21.11 removed TextRenderer.drawLayer; immediate text now goes through the
 * draw(...) overloads (world labels, command renderers) while GUI text is queued by
 * DrawContext (handled in DrawContextMixin). The global custom font therefore hooks
 * draw(...) here — entering the GUI projection scope when the call happens while an
 * orthographic (GUI) projection is active.
 */
@Mixin(TextRenderer.class)
public class TextRendererMixin {
    private static boolean sydney$globalFont() {
        return Sydney.MODULE_MANAGER != null
                && Sydney.MODULE_MANAGER.getModule(FontModule.class).isToggled()
                && Sydney.MODULE_MANAGER.getModule(FontModule.class).customFont.getValue()
                && Sydney.MODULE_MANAGER.getModule(FontModule.class).global.getValue()
                && Sydney.FONT_MANAGER.getFontRenderer() != null;
    }

    @Inject(method = "draw(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V", at = @At("HEAD"), cancellable = true)
    private void draw$String(String text, float x, float y, int color, boolean shadow, Matrix4f matrix, VertexConsumerProvider vertexConsumers, TextRenderer.TextLayerType layerType, int backgroundColor, int light, CallbackInfo info) {
        if (!sydney$globalFont()) return;
        sydney$drawCustom(matrices -> {
            if (shadow) Sydney.FONT_MANAGER.getFontRenderer().drawString(matrices, text, x + Sydney.FONT_MANAGER.getShadowOffset(), y + Sydney.FONT_MANAGER.getShadowOffset(), color, true);
            Sydney.FONT_MANAGER.getFontRenderer().drawString(matrices, text, x, y, color, false);
        }, matrix);
        info.cancel();
    }

    @Inject(method = "draw(Lnet/minecraft/text/Text;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V", at = @At("HEAD"), cancellable = true)
    private void draw$Text(net.minecraft.text.Text text, float x, float y, int color, boolean shadow, Matrix4f matrix, VertexConsumerProvider vertexConsumers, TextRenderer.TextLayerType layerType, int backgroundColor, int light, CallbackInfo info) {
        if (!sydney$globalFont()) return;
        OrderedText ordered = text.asOrderedText();
        sydney$drawCustom(matrices -> {
            if (shadow) Sydney.FONT_MANAGER.getFontRenderer().drawText(matrices, ordered, x + Sydney.FONT_MANAGER.getShadowOffset(), y + Sydney.FONT_MANAGER.getShadowOffset(), color, true);
            Sydney.FONT_MANAGER.getFontRenderer().drawText(matrices, ordered, x, y, color, false);
        }, matrix);
        info.cancel();
    }

    @Inject(method = "draw(Lnet/minecraft/text/OrderedText;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V", at = @At("HEAD"), cancellable = true)
    private void draw$Ordered(OrderedText text, float x, float y, int color, boolean shadow, Matrix4f matrix, VertexConsumerProvider vertexConsumers, TextRenderer.TextLayerType layerType, int backgroundColor, int light, CallbackInfo info) {
        if (!sydney$globalFont()) return;
        sydney$drawCustom(matrices -> {
            if (shadow) Sydney.FONT_MANAGER.getFontRenderer().drawText(matrices, text, x + Sydney.FONT_MANAGER.getShadowOffset(), y + Sydney.FONT_MANAGER.getShadowOffset(), color, true);
            Sydney.FONT_MANAGER.getFontRenderer().drawText(matrices, text, x, y, color, false);
        }, matrix);
        info.cancel();
    }

    /** Runs a custom-font draw with the pose vanilla passed in, under the GUI scope when orthographic. */
    private static void sydney$drawCustom(java.util.function.Consumer<MatrixStack> draw, Matrix4f matrix) {
        boolean gui = RenderSystem.getProjectionType() == ProjectionType.ORTHOGRAPHIC;
        if (gui) GuiRendering.begin();
        try {
            MatrixStack matrices = new MatrixStack();
            matrices.peek().getPositionMatrix().set(matrix);
            draw.accept(matrices);
        } finally {
            if (gui) GuiRendering.end();
        }
    }

    @Inject(method = "getWidth(Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
    private void getWidth(String text, CallbackInfoReturnable<Integer> info) {
        if (!sydney$globalFont()) return;
        info.setReturnValue(Sydney.FONT_MANAGER.getWidth(text));
    }

    @Inject(method = "getWidth(Lnet/minecraft/text/StringVisitable;)I", at = @At("HEAD"), cancellable = true)
    private void getWidth(StringVisitable text, CallbackInfoReturnable<Integer> info) {
        if (!sydney$globalFont()) return;
        info.setReturnValue(Sydney.FONT_MANAGER.getWidth(text.getString()));
    }

    @Inject(method = "getWidth(Lnet/minecraft/text/OrderedText;)I", at = @At("HEAD"), cancellable = true)
    private void getWidth(OrderedText text, CallbackInfoReturnable<Integer> info) {
        if (!sydney$globalFont()) return;
        info.setReturnValue((int) Sydney.FONT_MANAGER.getFontRenderer().getTextWidth(text));
    }
}
