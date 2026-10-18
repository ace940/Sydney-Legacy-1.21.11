package me.aidan.sydney.managers;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Getter;
import lombok.Setter;
import me.aidan.sydney.Sydney;
import me.aidan.sydney.modules.impl.core.FontModule;
import me.aidan.sydney.utils.IMinecraft;
import me.aidan.sydney.utils.color.ColorUtils;
import me.aidan.sydney.utils.font.FontRenderer;
import me.aidan.sydney.utils.graphics.GuiRendering;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.*;
import org.joml.Matrix4f;

import java.awt.*;

@Getter @Setter
public class FontManager implements IMinecraft {
    private FontRenderer fontRenderer;

    public void drawText(DrawContext context, String text, int x, int y, Color color) {
        if (Sydney.MODULE_MANAGER.getModule(FontModule.class).isToggled() && Sydney.MODULE_MANAGER.getModule(FontModule.class).customFont.getValue() && fontRenderer != null) {
            fontRenderer.drawString(context.getMatrices(), text, x, y, color.getRGB(), false);
        } else {
            context.drawText(mc.textRenderer, text, x, y, color.getRGB(), false);
        }
    }

    public void drawTextWithShadow(DrawContext context, String text, int x, int y, Color color) {
        if (Sydney.MODULE_MANAGER.getModule(FontModule.class).isToggled() && Sydney.MODULE_MANAGER.getModule(FontModule.class).customFont.getValue() && fontRenderer != null) {
            if (!Sydney.MODULE_MANAGER.getModule(FontModule.class).shadowMode.getValue().equalsIgnoreCase("None")) fontRenderer.drawString(context.getMatrices(), text, x + getShadowOffset(), y + getShadowOffset(), color.getRGB(), true);
            fontRenderer.drawString(context.getMatrices(), text, x, y, color.getRGB(), false);
        } else {
            context.drawText(mc.textRenderer, text, x, y, color.getRGB(), true);
        }
    }

    public void drawText(DrawContext context, OrderedText text, int x, int y, Color color) {
        if (Sydney.MODULE_MANAGER.getModule(FontModule.class).isToggled() && Sydney.MODULE_MANAGER.getModule(FontModule.class).customFont.getValue() && fontRenderer != null) {
            fontRenderer.drawText(context.getMatrices(), text, x, y, color.getRGB(), false);
        } else {
            context.drawText(mc.textRenderer, text, x, y, color.getRGB(), false);
        }
    }

    public void drawTextWithShadow(DrawContext context, OrderedText text, int x, int y, Color color) {
        if (Sydney.MODULE_MANAGER.getModule(FontModule.class).isToggled() && Sydney.MODULE_MANAGER.getModule(FontModule.class).customFont.getValue() && fontRenderer != null) {
            if (!Sydney.MODULE_MANAGER.getModule(FontModule.class).shadowMode.getValue().equalsIgnoreCase("None")) fontRenderer.drawText(context.getMatrices(), text, x + getShadowOffset(), y + getShadowOffset(), color.getRGB(), true);
            fontRenderer.drawText(context.getMatrices(), text, x, y, color.getRGB(), false);
        } else {
            context.drawText(mc.textRenderer, text, x, y, color.getRGB(), true);
        }
    }

    public void drawTextWithShadow(MatrixStack matrices, String text, int x, int y, VertexConsumerProvider vertexConsumers, Color color) {

        if (Sydney.MODULE_MANAGER.getModule(FontModule.class).isToggled() && Sydney.MODULE_MANAGER.getModule(FontModule.class).customFont.getValue() && fontRenderer != null) {
            if (!Sydney.MODULE_MANAGER.getModule(FontModule.class).shadowMode.getValue().equalsIgnoreCase("None")) fontRenderer.drawString(matrices, text, x + getShadowOffset(), y + getShadowOffset(), color.getRGB(), true);
            fontRenderer.drawString(matrices, text, x, y, color.getRGB(), false);
        } else {
            int tweaked = tweakTransparency(color.getRGB());
            mc.textRenderer.draw(text, x, y, tweaked, true, matrices.peek().getPositionMatrix(), vertexConsumers, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0);
            mc.getBufferBuilders().getEntityVertexConsumers().draw();

            mc.textRenderer.draw(text, x, y, tweaked, false, matrices.peek().getPositionMatrix(), vertexConsumers, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0);
            mc.getBufferBuilders().getEntityVertexConsumers().draw();
        }

    }

    /** Replacement for the removed TextRenderer.tweakTransparency. */
    private static int tweakTransparency(int argb) {
        if ((argb & 0xFC000000) == 0) argb |= 0xFF000000;
        return argb;
    }

    public void drawTextWithOutline(DrawContext context, String text, int x, int y, Color color, Color outlineColor) {
        if (Sydney.MODULE_MANAGER.getModule(FontModule.class).isToggled() && Sydney.MODULE_MANAGER.getModule(FontModule.class).customFont.getValue() && fontRenderer != null) {
            fontRenderer.drawString(context.getMatrices(), FontRenderer.stripControlCodes(text), x + 0.5f, y - 0.5f, outlineColor.getRGB(), false);
            fontRenderer.drawString(context.getMatrices(), FontRenderer.stripControlCodes(text), x - 0.5f, y + 0.5f, outlineColor.getRGB(), false);
            fontRenderer.drawString(context.getMatrices(), FontRenderer.stripControlCodes(text), x + 0.5f, y + 0.5f, outlineColor.getRGB(), false);
            fontRenderer.drawString(context.getMatrices(), FontRenderer.stripControlCodes(text), x - 0.5f, y - 0.5f, outlineColor.getRGB(), false);

            fontRenderer.drawString(context.getMatrices(), text, x, y, color.getRGB(), false);
        } else {
            // DrawContext has no immediate vertex consumers anymore; run the outline draw
            // through the entity buffer under the GUI projection scope instead.
            GuiRendering.begin();
            try {
                Matrix4f pose = GuiRendering.toMatrixStack(context.getMatrices()).peek().getPositionMatrix();
                mc.textRenderer.drawWithOutline(Text.literal(text).asOrderedText(), mc.getWindow().getScaledWidth() / 2.0f - Sydney.FONT_MANAGER.getWidth(text) / 2.0f, mc.getWindow().getScaledHeight() / 2.0f + 16, color.getRGB(), outlineColor.getRGB(), pose, mc.getBufferBuilders().getEntityVertexConsumers(), 0);
                mc.getBufferBuilders().getEntityVertexConsumers().draw();
            } finally {
                GuiRendering.end();
            }
        }
    }

    public void drawRainbowString(DrawContext context, String string, int x, int y, long offset) {
        MutableText builder = Text.empty();

        int[] i = {0};
        Text.literal(string).asOrderedText().accept((index, style, codePoint) -> {
            MutableText text = Text.empty();

            if (style.getColor() == null) {
                long index1 = (long) i[0] * offset;
                Color color = ColorUtils.getOffsetRainbow(index1);
                text.append(Text.literal(String.valueOf(Character.toChars(codePoint))).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color.getRGB()))));
            } else {
                text.append(Text.literal(String.valueOf(Character.toChars(codePoint))).setStyle(style));
            }

            builder.append(text);
            i[0]++;

            return true;
        });

        Sydney.FONT_MANAGER.drawTextWithShadow(context, builder.asOrderedText(), x, y, Color.WHITE);
    }

    public int getWidth(String text) {
        if (Sydney.MODULE_MANAGER.getModule(FontModule.class).isToggled() && Sydney.MODULE_MANAGER.getModule(FontModule.class).customFont.getValue() && fontRenderer != null) {
            return (int) fontRenderer.getTextWidth(text) + Sydney.MODULE_MANAGER.getModule(FontModule.class).widthOffset.getValue().intValue();
        } else {
            return mc.textRenderer.getWidth(text);
        }
    }

    public int getHeight() {
        if (Sydney.MODULE_MANAGER.getModule(FontModule.class).isToggled() && Sydney.MODULE_MANAGER.getModule(FontModule.class).customFont.getValue() && fontRenderer != null) {
            return (int) fontRenderer.getHeight() + Sydney.MODULE_MANAGER.getModule(FontModule.class).heightOffset.getValue().intValue();
        } else {
            return mc.textRenderer.fontHeight;
        }
    }

    public float getShadowOffset() {
        if (Sydney.MODULE_MANAGER.getModule(FontModule.class).shadowMode.getValue().equalsIgnoreCase("None")) {
            return 0.0f;
        } else if (Sydney.MODULE_MANAGER.getModule(FontModule.class).shadowMode.getValue().equalsIgnoreCase("Custom")) {
            return Sydney.MODULE_MANAGER.getModule(FontModule.class).shadowOffset.getValue().floatValue();
        } else {
            return 1.0f;
        }
    }

    public boolean hasFont(String name) {
        for (String fontName : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) if (fontName.equalsIgnoreCase(name)) return true;
        return false;
    }
}
