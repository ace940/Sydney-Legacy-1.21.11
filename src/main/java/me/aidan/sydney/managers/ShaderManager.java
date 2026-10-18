package me.aidan.sydney.managers;

import lombok.Getter;
import me.aidan.sydney.utils.IMinecraft;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumers;

import java.awt.*;
import java.util.Optional;

/**
 * Renders the coloured "shader" overlay used by the Shaders module.
 *
 * <p>Up to 1.21.8 this drew the outline geometry into its own framebuffer and ran a custom sobel
 * post-process effect over it. 1.21.9 replaced RenderPhase/MultiPhase and the post-effect uniform
 * plumbing with the RenderPipeline system, so the overlay is now produced with the engine's own
 * outline pipeline, which renders the same coloured silhouette for the selected entities.
 */
@Getter
public class ShaderManager implements IMinecraft {
    private final OutlineVertexConsumerProvider vertexConsumerProvider = new OutlineVertexConsumerProvider();

    public void prepare() {
        // Nothing to set up now that the outline is drawn by the engine's own target.
    }

    public void render(int renderMode, float opacity) {
        // The overlay is flushed by ShadersModule through getVertexConsumerProvider().draw().
    }

    public void resize(int width, int height) {
        // No framebuffer to resize.
    }

    public VertexConsumerProvider create(VertexConsumerProvider parent, Color color) {
        return create(parent, color, true);
    }

    /**
     * @param fill when true the entity keeps its normal rendering underneath the overlay,
     *             which is what the module's "Fill" and "Both" modes used to produce.
     */
    public VertexConsumerProvider create(VertexConsumerProvider parent, Color color, boolean fill) {
        return layer -> {
            VertexConsumer parentBuffer = parent.getBuffer(layer);

            Optional<RenderLayer> outline = layer.getAffectedOutline();
            if (outline.isEmpty()) return parentBuffer;

            vertexConsumerProvider.setColor(color.getRGB());

            VertexConsumer outlineBuffer = vertexConsumerProvider.getBuffer(outline.get());
            return fill ? VertexConsumers.union(outlineBuffer, parentBuffer) : outlineBuffer;
        };
    }
}
