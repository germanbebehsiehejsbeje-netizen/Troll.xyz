package dev.mzc.client.module.impl.render.totempopchams;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;

public class TotemPopChamsLayers {

    private static final RenderPipeline TRANSLUCENT_ENTITY_HIGHLIGHT_PIPELINE = RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withCull(true)
            .withLocation("translucent_entity_highlight")
            .build();

    public static RenderLayer TRANSLUCENT_ENTITY_HIGHLIGHT = RenderLayer.of(
            "translucent_entity_highlight",
            RenderSetup.builder(TRANSLUCENT_ENTITY_HIGHLIGHT_PIPELINE)
                    .translucent()
                    .expectedBufferSize(256)
                    .build()
    );

    private static final RenderPipeline WIREFRAME_PIPELINE = RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
            .withCull(false)
            .withLocation("wireframe_outline")
            .build();

    public static RenderLayer WIREFRAME = RenderLayer.of(
            "wireframe_outline",
            RenderSetup.builder(WIREFRAME_PIPELINE)
                    .translucent()
                    .expectedBufferSize(256)
                    .build()
    );
}
