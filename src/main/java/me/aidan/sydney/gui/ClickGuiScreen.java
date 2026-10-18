package me.aidan.sydney.gui;

import lombok.Getter;
import lombok.Setter;
import me.aidan.sydney.Sydney;
import me.aidan.sydney.gui.api.DescriptionFrame;
import me.aidan.sydney.modules.Module;
import me.aidan.sydney.modules.impl.core.ClickGuiModule;
import me.aidan.sydney.gui.api.Button;
import me.aidan.sydney.gui.api.Frame;
import me.aidan.sydney.modules.impl.core.ColorModule;
import me.aidan.sydney.utils.color.ColorUtils;
import me.aidan.sydney.utils.graphics.Renderer2D;
import me.aidan.sydney.utils.system.Timer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.awt.*;
import java.util.ArrayList;

@Getter @Setter
public class ClickGuiScreen extends Screen {
    private final ArrayList<Frame> frames = new ArrayList<>();
    private final ArrayList<Button> buttons = new ArrayList<>();
    private final DescriptionFrame descriptionFrame;

    private final Timer lineTimer = new Timer();
    private boolean showLine = false;
    private Color colorClipboard = null;

    public ClickGuiScreen() {
        super(Text.literal(Sydney.MOD_ID + "-click-gui"));

        int x = 6;
        for(Module.Category category : Module.Category.values()) {
            frames.add(new Frame(category, x, 3, 100, 13));
            x += 104;
        }

        this.descriptionFrame = new DescriptionFrame(x, 3, 200, 13);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        if (lineTimer.hasTimeElapsed(400L)){
            showLine = !showLine;
            lineTimer.reset();
        }

        descriptionFrame.setDescription("");
        for(Frame frame : frames) frame.render(context, mouseX, mouseY, delta);

        descriptionFrame.render(context, mouseX, mouseY, delta);
    }

    // 1.21.9 replaced the individual mouse/key/char arguments with Click, KeyInput and CharInput
    // records; the widget API of this screen still works on plain coordinates.
    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        for(Frame frame : frames) frame.mouseDragged(click.x(), click.y(), click.button(), deltaX, deltaY);
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        for (Frame frame : frames) {
            frame.mouseClicked(click.x(), click.y(), click.button());
        }

        descriptionFrame.mouseClicked(click.x(), click.y(), click.button());

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        for (Frame frame : frames) {
            frame.mouseReleased(click.x(), click.y(), click.button());
        }

        descriptionFrame.mouseReleased(click.x(), click.y(), click.button());

        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (Frame frame : frames) {
            frame.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        return this.hoveredElement(mouseX, mouseY).filter(element -> element.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)).isPresent();
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        for (Frame frame : frames) {
            frame.keyPressed(input.key(), input.scancode(), input.modifiers());
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        for (Frame frame : frames) {
            frame.charTyped((char) input.codepoint(), input.modifiers());
        }
        return super.charTyped(input);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        if(Sydney.MODULE_MANAGER.getModule(ClickGuiModule.class).blur.getValue()) applyBlur(context);
        Renderer2D.renderQuad(context.getMatrices(), 0, 0, this.width, this.height, new Color(0, 0, 0, 100));
    }

    @Override
    public void close() {
        super.close();
        Sydney.MODULE_MANAGER.getModule(ClickGuiModule.class).setToggled(false);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    public static Color getButtonColor(int index, int alpha) {
        Color color = Sydney.MODULE_MANAGER.getModule(ClickGuiModule.class).isRainbow() ? ColorUtils.getOffsetRainbow(index*10L) : Sydney.MODULE_MANAGER.getModule(ClickGuiModule.class).color.getColor();
        return ColorUtils.getColor(color, alpha);
    }
}
