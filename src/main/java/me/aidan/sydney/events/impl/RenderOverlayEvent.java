package me.aidan.sydney.events.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.aidan.sydney.events.Event;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

@Getter @AllArgsConstructor
public class RenderOverlayEvent extends Event {
    private final DrawContext context;
    private final float tickDelta;

    /** 1.21.6+ DrawContext poses are 2D; the mod's GUI renderers take overloads for these. */
    public Matrix3x2fStack getMatrices() {
        return context.getMatrices();
    }
}
