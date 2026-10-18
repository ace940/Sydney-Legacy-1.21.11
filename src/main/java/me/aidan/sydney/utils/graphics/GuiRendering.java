package me.aidan.sydney.utils.graphics;

import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import me.aidan.sydney.utils.IMinecraft;
import net.minecraft.client.render.ProjectionMatrix2;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * Bridges the mod's immediate Tessellator/RenderLayer drawing to the deferred GUI pipeline
 * introduced in 1.21.6+.
 *
 * <p>Vanilla now collects GUI elements into a {@code GuiRenderState} and only flushes them at
 * the end of the frame, after setting an orthographic projection (see {@code GuiRenderer}).
 * The mod still draws immediately while a screen or HUD callback runs, at which point the
 * RenderSystem projection is whatever the world renderer left behind — so those draws must
 * bracket themselves with the same state GuiRenderer uses:
 * <ul>
 *   <li>orthographic projection sized to the scaled window (near 1000, far 11000, flipped Y),</li>
 *   <li>modelview pushed to the far GUI plane (z = -11000), matching vanilla's DynamicTransforms.</li>
 * </ul>
 *
 * <p>Draw poses coming from {@code DrawContext.getMatrices()} are 2D ({@link Matrix3x2fStack});
 * {@link #toMatrixStack(Matrix3x2fStack)} snapshots the current pose into the 4x4 matrix the
 * mod's renderers bake into vertices.
 *
 * <p>The scope is re-entrant: nested begin/end pairs only change GPU state at the outermost level.
 */
public final class GuiRendering implements IMinecraft {
    private static final ProjectionMatrix2 PROJECTION = new ProjectionMatrix2("sydney-gui", 1000.0F, 11000.0F, true);
    private static int depth;

    private GuiRendering() {
    }

    public static void begin() {
        if (depth++ == 0) {
            RenderSystem.backupProjectionMatrix();
            RenderSystem.setProjectionMatrix(
                    PROJECTION.set(mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight()),
                    ProjectionType.ORTHOGRAPHIC);

            Matrix4fStack modelView = RenderSystem.getModelViewStack();
            modelView.pushMatrix();
            modelView.identity();
            modelView.translate(0.0F, 0.0F, -11000.0F);
        }
    }

    public static void end() {
        if (--depth == 0) {
            RenderSystem.getModelViewStack().popMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }

    /**
     * Snapshots the current 2D GUI pose into a MatrixStack the mod's renderers understand.
     *
     * <p>JOML's Matrix3x2fStack extends Matrix3x2f and always exposes its topmost entry directly, so
     * the stack itself is the current pose.
     */
    public static MatrixStack toMatrixStack(Matrix3x2fStack stack) {
        return toMatrixStack((Matrix3x2f) stack);
    }

    public static MatrixStack toMatrixStack(Matrix3x2f pose) {
        MatrixStack matrices = new MatrixStack();
        Matrix4f target = matrices.peek().getPositionMatrix();

        target.m00(pose.m00()); target.m01(pose.m01()); target.m02(0.0F); target.m03(0.0F);
        target.m10(pose.m10()); target.m11(pose.m11()); target.m12(0.0F); target.m13(0.0F);
        target.m20(0.0F);       target.m21(0.0F);       target.m22(1.0F); target.m23(0.0F);
        target.m30(pose.m20()); target.m31(pose.m21()); target.m32(0.0F); target.m33(1.0F);

        return matrices;
    }
}
