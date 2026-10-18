package me.aidan.sydney.utils.graphics;

import com.mojang.blaze3d.vertex.VertexFormat;
import me.aidan.sydney.utils.IMinecraft;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class Renderer2D implements IMinecraft {
    public static final Matrix4f LAST_PROJECTION_MATRIX = new Matrix4f();
    public static final Matrix4f LAST_MODEL_MATRIX = new Matrix4f();
    public static final Matrix4f LAST_WORLD_MATRIX = new Matrix4f();

    private static BufferBuilder quads() {
        return Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
    }

    private static BufferBuilder lines() {
        return Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
    }

    public static void renderQuad(MatrixStack matrices, float left, float top, float right, float bottom, Color color) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = quads();

        buffer.vertex(matrix, left, top, 0.0f).color(color.getRGB());
        buffer.vertex(matrix, left, bottom, 0.0f).color(color.getRGB());
        buffer.vertex(matrix, right, bottom, 0.0f).color(color.getRGB());
        buffer.vertex(matrix, right, top, 0.0f).color(color.getRGB());

        SydneyRenderLayers.QUADS.draw(buffer.end());
    }

    public static void renderGradient(MatrixStack matrices, float left, float top, float right, float bottom, Color startColor, Color endColor) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = quads();

        buffer.vertex(matrix, left, top, 0.0f).color(startColor.getRGB());
        buffer.vertex(matrix, left, bottom, 0.0f).color(endColor.getRGB());
        buffer.vertex(matrix, right, bottom, 0.0f).color(endColor.getRGB());
        buffer.vertex(matrix, right, top, 0.0f).color(startColor.getRGB());

        SydneyRenderLayers.QUADS.draw(buffer.end());
    }

    public static void renderSidewaysGradient(MatrixStack matrices, float left, float top, float right, float bottom, Color startColor, Color endColor) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = quads();

        buffer.vertex(matrix, left, top, 0.0f).color(startColor.getRGB());
        buffer.vertex(matrix, left, bottom, 0.0f).color(startColor.getRGB());
        buffer.vertex(matrix, right, bottom, 0.0f).color(endColor.getRGB());
        buffer.vertex(matrix, right, top, 0.0f).color(endColor.getRGB());

        SydneyRenderLayers.QUADS.draw(buffer.end());
    }

    public static void renderOutline(MatrixStack matrices, float left, float top, float right, float bottom, Color color) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = quads();

        buffer.vertex(matrix, left, top, 0.0f).color(color.getRGB());
        buffer.vertex(matrix, left, bottom, 0.0f).color(color.getRGB());
        buffer.vertex(matrix, left + 0.5f, bottom, 0.0f).color(color.getRGB());
        buffer.vertex(matrix, left + 0.5f, top, 0.0f).color(color.getRGB());

        buffer.vertex(matrix, right - 0.5f, top, 0.0f).color(color.getRGB());
        buffer.vertex(matrix, right - 0.5f, bottom, 0.0f).color(color.getRGB());
        buffer.vertex(matrix, right, bottom, 0.0f).color(color.getRGB());
        buffer.vertex(matrix, right, top, 0.0f).color(color.getRGB());

        buffer.vertex(matrix, left, bottom - 0.5f, 0.0f).color(color.getRGB());
        buffer.vertex(matrix, left, bottom, 0.0f).color(color.getRGB());
        buffer.vertex(matrix, right, bottom, 0.0f).color(color.getRGB());
        buffer.vertex(matrix, right, bottom - 0.5f, 0.0f).color(color.getRGB());

        buffer.vertex(matrix, left, top, 0.0f).color(color.getRGB());
        buffer.vertex(matrix, left, top + 0.5f, 0.0f).color(color.getRGB());
        buffer.vertex(matrix, right, top + 0.5f, 0.0f).color(color.getRGB());
        buffer.vertex(matrix, right, top, 0.0f).color(color.getRGB());

        SydneyRenderLayers.QUADS.draw(buffer.end());
    }

    public static void renderLine(MatrixStack matrices, float x, float y, float targetX, float targetY, Color color) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = lines();

        buffer.vertex(matrix, x, y, 0.0f).color(color.getRGB());
        buffer.vertex(matrix, targetX, targetY, 0.0f).color(color.getRGB());

        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);

        SydneyRenderLayers.LINES.draw(buffer.end());

        GL11.glDisable(GL11.GL_LINE_SMOOTH);
    }

    public static void renderArrow(MatrixStack matrices, float x, float y, float width, float height, Color color) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = quads();

        buffer.vertex(matrix, x, y, 0).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
        buffer.vertex(matrix, x - width, y + height, 0).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
        buffer.vertex(matrix, x, y + height, 0).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
        buffer.vertex(matrix, x + width, y + height, 0).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());

        SydneyRenderLayers.QUADS.draw(buffer.end());
    }

    public static void renderArrowOutline(MatrixStack matrices, float x, float y, float width, float height, Color color) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = lines();

        // Draw the triangle as three separate segments: 1.21.9 dropped DEBUG_LINE_STRIP usage here.
        float x1 = x, y1 = y;
        float x2 = x - width, y2 = y + height;
        float x3 = x + width, y3 = y + height;

        buffer.vertex(matrix, x1, y1, 0).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
        buffer.vertex(matrix, x2, y2, 0).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
        buffer.vertex(matrix, x2, y2, 0).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
        buffer.vertex(matrix, x3, y3, 0).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
        buffer.vertex(matrix, x3, y3, 0).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
        buffer.vertex(matrix, x1, y1, 0).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());

        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        SydneyRenderLayers.LINES.draw(buffer.end());
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
    }

    public static void renderCircle(MatrixStack matrices, float x, float y, float radius, Color color) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);

        for (int i = 0; i <= 360; ++i) {
            buffer.vertex(matrix, (float) (x + Math.sin((double) i * 3.141526 / 180.0) * (double) radius), (float) (y + Math.cos((double) i * 3.141526 / 180.0) * (double) radius), 0).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
        }

        SydneyRenderLayers.QUADS.draw(buffer.end());
    }

    public static void renderTexture(MatrixStack matrices, float left, float top, float right, float bottom, Identifier identifier, Color color) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        buffer.vertex(matrix, left, top, 0).texture(0, 0).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
        buffer.vertex(matrix, left, bottom, 0).texture(0, 1).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
        buffer.vertex(matrix, right, bottom, 0).texture(1, 1).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
        buffer.vertex(matrix, right, top, 0).texture(1, 0).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());

        SydneyRenderLayers.textured(identifier).draw(buffer.end());
    }

    // ------------------------------------------------------------------
    // DrawContext (Matrix3x2fStack) overloads. These bracket the draw with the
    // GUI projection/modelview state GuiRenderer would otherwise only set at the
    // end-of-frame flush, so immediate GUI drawing matches vanilla coordinates.
    // ------------------------------------------------------------------

    public static void renderQuad(Matrix3x2fStack stacks, float left, float top, float right, float bottom, Color color) {
        GuiRendering.begin();
        try { renderQuad(GuiRendering.toMatrixStack(stacks), left, top, right, bottom, color); }
        finally { GuiRendering.end(); }
    }

    public static void renderGradient(Matrix3x2fStack stacks, float left, float top, float right, float bottom, Color startColor, Color endColor) {
        GuiRendering.begin();
        try { renderGradient(GuiRendering.toMatrixStack(stacks), left, top, right, bottom, startColor, endColor); }
        finally { GuiRendering.end(); }
    }

    public static void renderSidewaysGradient(Matrix3x2fStack stacks, float left, float top, float right, float bottom, Color startColor, Color endColor) {
        GuiRendering.begin();
        try { renderSidewaysGradient(GuiRendering.toMatrixStack(stacks), left, top, right, bottom, startColor, endColor); }
        finally { GuiRendering.end(); }
    }

    public static void renderOutline(Matrix3x2fStack stacks, float left, float top, float right, float bottom, Color color) {
        GuiRendering.begin();
        try { renderOutline(GuiRendering.toMatrixStack(stacks), left, top, right, bottom, color); }
        finally { GuiRendering.end(); }
    }

    public static void renderLine(Matrix3x2fStack stacks, float x, float y, float targetX, float targetY, Color color) {
        GuiRendering.begin();
        try { renderLine(GuiRendering.toMatrixStack(stacks), x, y, targetX, targetY, color); }
        finally { GuiRendering.end(); }
    }

    public static void renderArrow(Matrix3x2fStack stacks, float x, float y, float width, float height, Color color) {
        GuiRendering.begin();
        try { renderArrow(GuiRendering.toMatrixStack(stacks), x, y, width, height, color); }
        finally { GuiRendering.end(); }
    }

    public static void renderArrowOutline(Matrix3x2fStack stacks, float x, float y, float width, float height, Color color) {
        GuiRendering.begin();
        try { renderArrowOutline(GuiRendering.toMatrixStack(stacks), x, y, width, height, color); }
        finally { GuiRendering.end(); }
    }

    public static void renderCircle(Matrix3x2fStack stacks, float x, float y, float radius, Color color) {
        GuiRendering.begin();
        try { renderCircle(GuiRendering.toMatrixStack(stacks), x, y, radius, color); }
        finally { GuiRendering.end(); }
    }

    public static void renderTexture(Matrix3x2fStack stacks, float left, float top, float right, float bottom, Identifier identifier, Color color) {
        GuiRendering.begin();
        try { renderTexture(GuiRendering.toMatrixStack(stacks), left, top, right, bottom, identifier, color); }
        finally { GuiRendering.end(); }
    }

    public static Vec3d project(Vec3d vec3d) {
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);

        Vector3f target = new Vector3f();
        Vector4f transform = new Vector4f((float) (vec3d.x - camera.x), (float) (vec3d.y - camera.y), (float) (vec3d.z - camera.z), 1.0f).mul(LAST_WORLD_MATRIX);

        Matrix4f matrixProj = new Matrix4f(LAST_PROJECTION_MATRIX);
        Matrix4f matrixModel = new Matrix4f(LAST_MODEL_MATRIX);

        matrixProj.mul(matrixModel).project(transform.x(), transform.y(), transform.z(), viewport, target);

        return new Vec3d(target.x / mc.getWindow().getScaleFactor(), (mc.getWindow().getHeight() - target.y) / mc.getWindow().getScaleFactor(), target.z);
    }
}
