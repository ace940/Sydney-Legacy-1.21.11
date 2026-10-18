package me.aidan.sydney.utils.font;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import lombok.SneakyThrows;
import me.aidan.sydney.Sydney;
import me.aidan.sydney.modules.impl.core.FontModule;
import me.aidan.sydney.modules.impl.miscellaneous.NameProtectModule;
import me.aidan.sydney.utils.IMinecraft;
import me.aidan.sydney.utils.graphics.GuiRendering;
import me.aidan.sydney.utils.graphics.SydneyRenderLayers;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.NativeImageBackedTexture;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;

import java.awt.*;
import java.io.Closeable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FontRenderer implements Closeable, IMinecraft {
    private final Map<NativeImageBackedTexture, ObjectList<DrawEntry>> glyphPages = new HashMap<>();
    private final ObjectList<GlyphMap> maps = new ObjectArrayList<>();

    // 1.21.9 dropped texture GL ids; glyph atlases are registered so a RenderLayer can sample them.
    private static final Map<NativeImageBackedTexture, Identifier> TEXTURE_IDS = new HashMap<>();
    private static final AtomicInteger TEXTURE_COUNTER = new AtomicInteger();

    private Font[] fonts;

    private final float size;
    private final int charsPerPage;
    private final int padding;

    private int multiplier = 0;
    private int previousGameScale = -1;
    private boolean initialized;

    private final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock();

    public FontRenderer(Font[] fonts, float size) {
        this(fonts, size, 256, 5);
    }

    public FontRenderer(Font[] fonts, float size, int charactersPerPage, int paddingBetweenCharacters) {
        Preconditions.checkArgument(size > 0, "size <= 0");
        Preconditions.checkArgument(fonts.length > 0, "fonts.length <= 0");
        Preconditions.checkArgument(charactersPerPage > 4, "Unreasonable charactersPerPage count");
        Preconditions.checkArgument(paddingBetweenCharacters > 0, "paddingBetweenCharacters <= 0");

        this.size = size;
        this.charsPerPage = charactersPerPage;
        this.padding = paddingBetweenCharacters;

        init(fonts, size);
    }

    public void drawString(MatrixStack matrices, String text, float x, float y, int color, boolean dropShadow) {
        drawText(matrices, Text.literal(text).styled(it -> it.withParent(Style.EMPTY.withColor(color))).asOrderedText(), x, y, color, dropShadow);
    }

    /** DrawContext pose overload: runs the draw under the GUI projection/modelview scope. */
    public void drawString(Matrix3x2fStack stacks, String text, float x, float y, int color, boolean dropShadow) {
        GuiRendering.begin();
        try { drawString(GuiRendering.toMatrixStack(stacks), text, x, y, color, dropShadow); }
        finally { GuiRendering.end(); }
    }

    /** DrawContext pose overload: runs the draw under the GUI projection/modelview scope. */
    public void drawText(Matrix3x2fStack stacks, OrderedText text, float x, float y, int color, boolean dropShadow) {
        GuiRendering.begin();
        try { drawText(GuiRendering.toMatrixStack(stacks), text, x, y, color, dropShadow); }
        finally { GuiRendering.end(); }
    }

    public void drawText(MatrixStack matrices, OrderedText text, float x, float y, int color, boolean dropShadow) {
        if (((int) mc.getWindow().getScaleFactor()) != this.previousGameScale) {
            close();
            init(this.fonts, this.size);
        }

        if (Sydney.MODULE_MANAGER != null && Sydney.MODULE_MANAGER.getModule(FontModule.class).isToggled()) {
            x += Sydney.MODULE_MANAGER.getModule(FontModule.class).xOffset.getValue().floatValue();
            y += Sydney.MODULE_MANAGER.getModule(FontModule.class).yOffset.getValue().floatValue();
        }

        if ((color & -67108864) == 0) color |= -16777216;
        if (dropShadow) color = (color & 16579836) >> 2 | color & -16777216;

        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;
        float alpha = (float) (color >> 24 & 255) / 255.0F;

        matrices.push();
        matrices.translate(x, y, 0);
        matrices.scale(1.0f / this.multiplier, 1.0f / this.multiplier, 1.0f);

        Matrix4f matrix4f = matrices.peek().getPositionMatrix();

        float[] currentX = {0};

        synchronized (glyphPages) {
            text.accept((index, style, codePoint) -> {
                char[] chars = Character.toChars(codePoint);

                float currentRed = red;
                float currentGreen = green;
                float currentBlue = blue;

                if (style.getColor() != null) {
                    int rgb = style.getColor().getRgb();

                    if ((rgb & -67108864) == 0) rgb |= -16777216;
                    if (dropShadow) rgb = (rgb & 16579836) >> 2 | rgb & -16777216;

                    currentRed = (float) ((rgb >> 16) & 0xFF) / 255.0F;
                    currentGreen = (float) ((rgb >> 8) & 0xFF) / 255.0F;
                    currentBlue = (float) (rgb & 0xFF) / 255.0F;
                }

                for (char character : chars) {
                    Glyph glyph = locateGlyph(character);
                    if (glyph != null) {
                        if (glyph.value() != ' ') {
                            glyphPages.computeIfAbsent(glyph.parent().getTexture(), integer -> new ObjectArrayList<>()).add(new DrawEntry(currentX[0], 0, currentRed, currentGreen, currentBlue, glyph));
                        }

                        currentX[0] += glyph.width();
                    }
                }

                return true;
            });

            for (NativeImageBackedTexture glyphTexture : glyphPages.keySet()) {
                List<DrawEntry> objects = glyphPages.get(glyphTexture);

                BufferBuilder buffer = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

                for (DrawEntry object : objects) {
                    Glyph glyph = object.toDraw;
                    GlyphMap parent = glyph.parent();

                    float u1 = (float) glyph.u() / parent.getWidth();
                    float v1 = (float) glyph.v() / parent.getHeight();
                    float u2 = (float) (glyph.u() + glyph.width()) / parent.getWidth();
                    float v2 = (float) (glyph.v() + glyph.height()) / parent.getHeight();

                    buffer.vertex(matrix4f, object.atX + 0, object.atY + glyph.height(), 0).texture(u1, v2).color(object.r, object.g, object.b, alpha);
                    buffer.vertex(matrix4f, object.atX + glyph.width(), object.atY + glyph.height(), 0).texture(u2, v2).color(object.r, object.g, object.b, alpha);
                    buffer.vertex(matrix4f, object.atX + glyph.width(), object.atY + 0, 0).texture(u2, v1).color(object.r, object.g, object.b, alpha);
                    buffer.vertex(matrix4f, object.atX + 0, object.atY + 0, 0).texture(u1, v1).color(object.r, object.g, object.b, alpha);
                }

                SydneyRenderLayers.textured(identifierOf(glyphTexture)).draw(buffer.end());
            }

            glyphPages.clear();
        }

        matrices.pop();
    }

    public float getTextWidth(String text) {
        NameProtectModule module = Sydney.MODULE_MANAGER.getModule(NameProtectModule.class);
        if (module.isToggled()) text = text.replaceAll(mc.getSession().getUsername(), module.name.getValue());

        char[] characters = stripControlCodes(text).toCharArray();
        float width = 0;

        for (char ch : characters) {
            Glyph glyph = locateGlyph(ch);
            if (glyph != null) {
                width += glyph.width() / (float) this.multiplier;
            }
        }

        return Math.max(width, 0);
    }

    public float getTextWidth(OrderedText text) {
        float[] dimensions = new float[2];
        text.accept((index, style, codePoint) -> {
            char character = (char) codePoint;

            Glyph glyph = locateGlyph(character);
            if (glyph != null) {
                dimensions[0] += (float) glyph.width() / (float) this.multiplier;
            }

            return true;
        });

        return Math.max(dimensions[0], dimensions[1]);
    }

    public float getHeight() {
        Glyph glyph = locateGlyph('A');
        if (glyph != null) return glyph.height() / (float) this.multiplier;
        return 0.0f;
    }

    /** Registers a glyph atlas so it can be sampled by a RenderLayer, and caches the identifier. */
    private static Identifier identifierOf(NativeImageBackedTexture texture) {
        return TEXTURE_IDS.computeIfAbsent(texture, t -> {
            Identifier id = Identifier.of(Sydney.MOD_ID, "font/glyph_atlas_" + TEXTURE_COUNTER.getAndIncrement());
            mc.getTextureManager().registerTexture(id, t);
            return id;
        });
    }

    public static String stripControlCodes(String text) {
        char[] chars = text.toCharArray();
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < chars.length; i++) {
            char character = chars[i];
            if (character == '§') {
                i++;
                continue;
            }

            builder.append(character);
        }

        return builder.toString();
    }

    private void init(Font[] fonts, float sizePx) {
        if (initialized) throw new IllegalStateException("Double call to init()");
        LOCK.writeLock().lock();

        try {
            this.previousGameScale = (int) mc.getWindow().getScaleFactor();
            this.multiplier = this.previousGameScale;
            this.fonts = new Font[fonts.length];

            for (int i = 0; i < fonts.length; i++) {
                this.fonts[i] = fonts[i].deriveFont(sizePx * this.multiplier);
            }

            initialized = true;
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    private Glyph locateGlyph(char glyph) {
        LOCK.readLock().lock();
        try {
            for (GlyphMap map : maps) {
                if (map.contains(glyph)) {
                    return map.getGlyph(glyph);
                }
            }
        } finally {
            LOCK.readLock().unlock();
        }

        int base = charsPerPage * (int) Math.floor((double) glyph / (double) charsPerPage);
        GlyphMap map = new GlyphMap(this.fonts, (char) base, (char) (base + charsPerPage), padding);
        LOCK.writeLock().lock();

        try {
            map.generate();
            maps.add(map);
        } finally {
            LOCK.writeLock().unlock();
        }

        return map.getGlyph(glyph);
    }

    @SneakyThrows @Override
    public void close() {
        LOCK.writeLock().lock();
        try {
            for (GlyphMap map : maps) map.destroy();
            maps.clear();

            initialized = false;
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    public record DrawEntry(float atX, float atY, float r, float g, float b, Glyph toDraw) { }
}