package me.aidan.sydney.utils.graphics;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import me.aidan.sydney.mixins.accessors.RenderLayerAccessor;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * 1.21.9 replaced the old RenderLayer/RenderPhase builder with RenderSetup + RenderPipeline.
 * Sydney needs a handful of layers whose depth/blend state differs from the vanilla pipelines,
 * so the vanilla pipelines are copied and their state overridden.
 */
public final class SydneyRenderLayers {
    public static final RenderLayer QUADS = layer("sydney_quads", copy(RenderPipelines.DEBUG_QUADS, "pipeline/sydney_quads", DepthTestFunction.NO_DEPTH_TEST, BlendFunction.TRANSLUCENT, false), 1536);
    public static final RenderLayer QUADS_DEPTH = layer("sydney_quads_depth", copy(RenderPipelines.DEBUG_QUADS, "pipeline/sydney_quads_depth", DepthTestFunction.LEQUAL_DEPTH_TEST, BlendFunction.TRANSLUCENT, false), 1536);
    public static final RenderLayer LINES = layer("sydney_lines", copy(RenderPipelines.LINES, "pipeline/sydney_lines", DepthTestFunction.NO_DEPTH_TEST, BlendFunction.TRANSLUCENT, false), 1536);
    public static final RenderLayer LINES_DEPTH = layer("sydney_lines_depth", copy(RenderPipelines.LINES, "pipeline/sydney_lines_depth", DepthTestFunction.LEQUAL_DEPTH_TEST, BlendFunction.TRANSLUCENT, false), 1536);

    private static final RenderPipeline TEXTURED_PIPELINE = copy(RenderPipelines.GUI_TEXTURED, "pipeline/sydney_textured", DepthTestFunction.NO_DEPTH_TEST, BlendFunction.TRANSLUCENT, false);
    private static final Map<Identifier, RenderLayer> TEXTURED = new HashMap<>();

    private SydneyRenderLayers() {
    }

    /** POSITION_TEXTURE_COLOR quads sampling the given texture. */
    public static RenderLayer textured(Identifier texture) {
        return TEXTURED.computeIfAbsent(texture, id -> layer("sydney_textured/" + id,
                RenderSetup.builder(TEXTURED_PIPELINE).texture("Sampler0", id).expectedBufferSize(1536).build(), 1536));
    }

    /** The vanilla entity outline layer for a given entity texture. */
    public static RenderLayer outline(Identifier texture) {
        return net.minecraft.client.render.RenderLayers.outlineNoCull(texture);
    }

    private static RenderLayer layer(String name, RenderPipeline pipeline, int expectedBufferSize) {
        return RenderLayerAccessor.sydney$of(name, RenderSetup.builder(pipeline).expectedBufferSize(expectedBufferSize).build());
    }

    private static RenderLayer layer(String name, RenderSetup setup, int ignored) {
        return RenderLayerAccessor.sydney$of(name, setup);
    }

    /**
     * Rebuilds {@code base} with different raster state. Shaders, samplers, uniforms and shader
     * defines are carried over so the copy behaves exactly like the vanilla pipeline it came from.
     */
    private static RenderPipeline copy(RenderPipeline base, String location, DepthTestFunction depth, BlendFunction blend, boolean cull) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(location)
                .withVertexShader(base.getVertexShader())
                .withFragmentShader(base.getFragmentShader())
                .withVertexFormat(base.getVertexFormat(), base.getVertexFormatMode())
                .withDepthTestFunction(depth)
                .withCull(cull)
                .withPolygonMode(base.getPolygonMode());

        if (blend == null) builder.withoutBlend();
        else builder.withBlend(blend);

        for (String sampler : base.getSamplers()) builder.withSampler(sampler);
        for (RenderPipeline.UniformDescription uniform : base.getUniforms()) builder.withUniform(uniform.name(), uniform.type());
        for (String flag : base.getShaderDefines().flags()) builder.withShaderDefine(flag);

        return builder.build();
    }
}
