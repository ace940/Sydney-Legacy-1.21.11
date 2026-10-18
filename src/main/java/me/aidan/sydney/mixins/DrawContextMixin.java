package me.aidan.sydney.mixins;

import me.aidan.sydney.Sydney;
import me.aidan.sydney.modules.impl.core.FontModule;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;

/**
 * 1.21.6+ queues DrawContext text into the deferred GuiRenderState, which is turned into
 * glyph quads at end-of-frame and never calls the old TextRenderer.drawLayer hook. To keep
 * the Font module's "Global" option working, DrawContext's text entry points are redirected
 * to the mod's custom font renderer while global mode is enabled; it snapshots the same 2D
 * pose and draws immediately under the GUI projection scope.
 */
@Mixin(DrawContext.class)
public abstract class DrawContextMixin {
    @Inject(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIIZ)V", at = @At("HEAD"), cancellable = true)
    private void drawText$String(TextRenderer textRenderer, String text, int x, int y, int color, boolean shadow, CallbackInfo info) {
        if (text == null || !sydney$shouldRedirect()) return;

        DrawContext self = (DrawContext) (Object) this;
        if (shadow) Sydney.FONT_MANAGER.drawTextWithShadow(self, text, x, y, new Color(color, true));
        else Sydney.FONT_MANAGER.drawText(self, text, x, y, new Color(color, true));
        info.cancel();
    }

    @Inject(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;IIIZ)V", at = @At("HEAD"), cancellable = true)
    private void drawText$Ordered(TextRenderer textRenderer, OrderedText text, int x, int y, int color, boolean shadow, CallbackInfo info) {
        if (text == null || !sydney$shouldRedirect()) return;

        DrawContext self = (DrawContext) (Object) this;
        if (shadow) Sydney.FONT_MANAGER.drawTextWithShadow(self, text, x, y, new Color(color, true));
        else Sydney.FONT_MANAGER.drawText(self, text, x, y, new Color(color, true));
        info.cancel();
    }

    private static boolean sydney$shouldRedirect() {
        return Sydney.MODULE_MANAGER != null
                && Sydney.MODULE_MANAGER.getModule(FontModule.class).isToggled()
                && Sydney.MODULE_MANAGER.getModule(FontModule.class).customFont.getValue()
                && Sydney.MODULE_MANAGER.getModule(FontModule.class).global.getValue()
                && Sydney.FONT_MANAGER.getFontRenderer() != null;
    }
}
