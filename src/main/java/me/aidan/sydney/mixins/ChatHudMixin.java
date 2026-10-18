package me.aidan.sydney.mixins;

import me.aidan.sydney.Sydney;
import me.aidan.sydney.modules.impl.core.CommandsModule;
import me.aidan.sydney.modules.impl.core.HUDModule;
import me.aidan.sydney.modules.impl.miscellaneous.BetterChatModule;
import me.aidan.sydney.utils.IMinecraft;
import me.aidan.sydney.utils.animations.Easing;
import me.aidan.sydney.utils.color.ColorUtils;
import me.aidan.sydney.utils.mixins.IChatHudLineVisible;
import me.aidan.sydney.utils.text.CustomFormatting;
import me.aidan.sydney.utils.text.FormattingUtils;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.ChatVisibility;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.profiler.Profilers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Mixin(value = ChatHud.class, priority = 999)
public abstract class ChatHudMixin implements IMinecraft {
    @Shadow @Final private List<ChatHudLine.Visible> visibleMessages;

    @Shadow private int scrolledLines;

    @Shadow private boolean hasUnreadNewMessages;

    @Shadow public abstract int getVisibleLineCount();

    /**
     * 1.21.6+ moved the entire chat layout behind the {@link ChatHud.Backend} interface, and
     * 1.21.11 turned the renderer into a private method that both the HUD and the clickable
     * chat screen share. Rewriting that single method keeps every BetterChat feature working
     * in both cases instead of only in the HUD.
     */
    @Inject(method = "render(Lnet/minecraft/client/gui/hud/ChatHud$Backend;IIZ)V", at = @At("HEAD"), cancellable = true)
    private void sydney$render(ChatHud.Backend drawer, int windowHeight, int currentTick, boolean expanded, CallbackInfo ci) {
        BetterChatModule module = Sydney.MODULE_MANAGER.getModule(BetterChatModule.class);
        boolean betterChat = module.isToggled();

        if (mc.options.getChatVisibility().getValue() == ChatVisibility.HIDDEN) return;

        int totalLines = this.visibleMessages.size();
        if (totalLines <= 0) return;

        ci.cancel();
        Profilers.get().push("chat");

        float chatScale = mc.options.getChatScale().getValue().floatValue();
        int chatWidth = MathHelper.ceil((float) ChatHud.getWidth(mc.options.getChatWidth().getValue()) / chatScale);
        int firstLineY = MathHelper.floor((float) (windowHeight - 40) / chatScale);
        float chatOpacity = mc.options.getChatOpacity().getValue().floatValue() * 0.9F + 0.1F;
        float backgroundOpacity = mc.options.getTextBackgroundOpacity().getValue().floatValue();
        double lineSpacing = mc.options.getChatLineSpacing().getValue();
        int lineStep = (int) (9.0 * (lineSpacing + 1.0));
        int textOffset = (int) Math.round(8.0 * (lineSpacing + 1.0) - 4.0 * lineSpacing);
        long unprocessed = mc.getMessageHandler().getUnprocessedMessageCount();

        drawer.updatePose(pose -> {
            pose.scale(chatScale, chatScale);
            pose.translate(4.0F + (betterChat ? module.offset.getValue().floatValue() : 0.0F), 0.0F);
        });

        int firstVisible = Math.min(totalLines - this.scrolledLines, this.getVisibleLineCount());

        // Backgrounds.
        for (int line = firstVisible - 1; line >= 0; line--) {
            ChatHudLine.Visible visible = this.sydney$getLine(totalLines, line);
            if (visible == null) continue;

            float opacity = sydney$lineOpacity(visible, currentTick, expanded);
            if (opacity <= 1.0E-5F) continue;

            int y = firstLineY - line * lineStep;
            drawer.fill(-4, y - lineStep, chatWidth + 8, y, sydney$background(betterChat, module, opacity, backgroundOpacity));
        }

        if (unprocessed > 0L) {
            drawer.fill(-2, firstLineY, chatWidth + 4, firstLineY + 9, sydney$background(betterChat, module, 1.0F, backgroundOpacity));
        }

        // Lines of text, indicators and icons.
        int drawnLines = 0;
        for (int line = firstVisible - 1; line >= 0; line--) {
            ChatHudLine.Visible visible = this.sydney$getLine(totalLines, line);
            if (visible == null) continue;

            float opacity = sydney$lineOpacity(visible, currentTick, expanded);
            if (opacity <= 1.0E-5F) continue;

            drawnLines++;
            int y = firstLineY - line * lineStep;
            float textOpacity = betterChat ? opacity * (module.textAlpha.getValue().floatValue() / 255.0F) : opacity * chatOpacity;
            OrderedText content = processOrderedText(visible.content());

            float animation = sydney$animationProgress(betterChat, module, visible);
            boolean styled = false;

            if (animation != 0.0F) {
                float shift = -mc.textRenderer.getWidth(content) * animation;
                drawer.updatePose(pose -> pose.translate(shift, 0.0F));
                styled = drawer.text(y - textOffset, textOpacity, content);
                drawer.updatePose(pose -> pose.translate(-shift, 0.0F));
            } else {
                styled = drawer.text(y - textOffset, textOpacity, content);
            }

            MessageIndicator indicator = visible.indicator();
            if ((!betterChat || !module.noIndicators.getValue()) && indicator != null) {
                if (((IChatHudLineVisible) (Object) visible).sydney$isClientMessage()) {
                    drawer.fill(-4, y - lineStep, -2, y, ColorUtils.getGlobalColor().getRGB());
                } else {
                    drawer.indicator(-4, y - lineStep, -2, y, textOpacity, indicator);

                    if (indicator.icon() != null) {
                        drawer.indicatorIcon(visible.getWidth(mc.textRenderer), y - textOffset + 9, styled, indicator, indicator.icon());
                    }
                }
            }
        }

        if (betterChat && module.animation.getValue()) {
            module.getAnimationMap().entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue() > module.delay.getValue().intValue());
        }

        if (unprocessed > 0L) {
            float textOpacity = betterChat ? module.textAlpha.getValue().floatValue() / 255.0F : chatOpacity;
            drawer.text(firstLineY + 1, textOpacity, Text.translatable("chat.queue", unprocessed).asOrderedText());
        }

        if (expanded) {
            int totalHeight = totalLines * lineStep;
            int visibleHeight = drawnLines * lineStep;
            int scroll = this.scrolledLines * visibleHeight / totalLines - firstLineY;
            int height = visibleHeight * visibleHeight / totalHeight;

            if (totalHeight != visibleHeight) {
                int color = scroll > 0 ? 170 : 96;
                int trackColor = this.hasUnreadNewMessages ? 13382451 : 3355562;
                int x = chatWidth + 4;
                drawer.fill(x, -scroll, x + 2, -scroll - height, ColorHelper.withAlpha(color, trackColor));
                drawer.fill(x + 2, -scroll, x + 1, -scroll - height, ColorHelper.withAlpha(color, 13421772));
            }
        }

        Profilers.get().pop();
    }

    @Unique
    private ChatHudLine.Visible sydney$getLine(int totalLines, int line) {
        int index = line + this.scrolledLines;
        if (index < 0 || index >= totalLines) return null;

        return this.visibleMessages.get(index);
    }

    @Unique
    private static float sydney$lineOpacity(ChatHudLine.Visible visible, int currentTick, boolean expanded) {
        if (expanded) return 1.0F;

        double delta = (currentTick - visible.addedTime()) / 200.0;
        delta = (1.0 - delta) * 10.0;
        delta = MathHelper.clamp(delta, 0.0, 1.0);

        return (float) (delta * delta);
    }

    @Unique
    private static float sydney$animationProgress(boolean betterChat, BetterChatModule module, ChatHudLine.Visible visible) {
        if (!betterChat || !module.animation.getValue()) return 0.0F;

        Long start = module.getAnimationMap().get(visible);
        if (start == null) return 0.0F;

        return 1.0F - Easing.toDelta(start, module.delay.getValue().intValue());
    }

    @Unique
    private static int sydney$background(boolean betterChat, BetterChatModule module, float opacity, float backgroundOpacity) {
        if (betterChat && !module.background.getValue().equals("Default")) {
            if (module.background.getValue().equals("Clear")) return 0;

            return module.color.getColor().getRGB();
        }

        return ColorHelper.toAlpha(opacity * backgroundOpacity);
    }

    @Unique
    private OrderedText processOrderedText(OrderedText orderedText) {
        MutableText builder = Text.empty();

        int[] i = {0};
        orderedText.accept((index, style, codePoint) -> {
            MutableText text = Text.empty();

            if (style.getColor() != null && (style.getColor().toString().toLowerCase().equals(CustomFormatting.CLIENT.getName()) || style.getColor().toString().toLowerCase().equals(CustomFormatting.RAINBOW.getName()))) {
                if (style.getColor().toString().toLowerCase().equals(CustomFormatting.CLIENT.getName())) {
                    text.append(Text.literal(String.valueOf(Character.toChars(codePoint))).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(ColorUtils.getGlobalColor().getRGB()))));
                }

                if (style.getColor().toString().toLowerCase().equals(CustomFormatting.RAINBOW.getName())) {
                    long index1 = (long) i[0] * (Sydney.MODULE_MANAGER.getModule(HUDModule.class).rainbowOffset.getValue().longValue() * 5L);
                    Color color = ColorUtils.getOffsetRainbow(index1);
                    text.append(Text.literal(String.valueOf(Character.toChars(codePoint))).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color.getRGB()))));
                }
            } else {
                text.append(Text.literal(String.valueOf(Character.toChars(codePoint))).setStyle(style));
            }

            builder.append(text);
            i[0]++;

            return true;
        });

        return builder.asOrderedText();
    }

    @Redirect(method = "addVisibleMessage", at = @At(value = "INVOKE", target = "Ljava/util/List;addFirst(Ljava/lang/Object;)V"))
    private void addVisibleMessage(List<Object> instance, Object e) {
        ChatHudLine.Visible visible = (ChatHudLine.Visible) e;
        BetterChatModule module = Sydney.MODULE_MANAGER.getModule(BetterChatModule.class);
        if (module.isToggled() && module.animation.getValue()) module.getAnimationMap().put(visible, System.currentTimeMillis());
        instance.addFirst(e);
    }

    @ModifyArgs(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHudLine;<init>(ILnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V"))
    private void addMessage(Args args, Text message, MessageSignatureData signature, MessageIndicator indicator) {
        BetterChatModule module = Sydney.MODULE_MANAGER.getModule(BetterChatModule.class);
        if (module.isToggled() && module.timestamps.getValue()) {
            args.set(1, Text.literal("").append(Text.literal(FormattingUtils.getFormatting(Sydney.MODULE_MANAGER.getModule(CommandsModule.class).secondaryWatermarkColor.getValue()) + module.opening.getValue() + FormattingUtils.getFormatting(Sydney.MODULE_MANAGER.getModule(CommandsModule.class).primaryWatermarkColor.getValue()) + new SimpleDateFormat("HH:mm").format(new Date()) + FormattingUtils.getFormatting(Sydney.MODULE_MANAGER.getModule(CommandsModule.class).secondaryWatermarkColor.getValue()) + module.closing.getValue() + Formatting.RESET + " ")).append(message));
        }
    }
}
