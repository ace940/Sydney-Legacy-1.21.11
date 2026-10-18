package me.aidan.sydney.mixins;

import me.aidan.sydney.Sydney;
import me.aidan.sydney.events.impl.KeyInputEvent;
import me.aidan.sydney.events.impl.UnfilteredKeyInputEvent;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {
    @Shadow @Final private MinecraftClient client;

    // 1.21.9 moved the key, scancode and modifier arguments into a KeyInput record and left the
    // GLFW action on its own.
    @Inject(method = "onKey", at = @At("HEAD"))
    private void onKey(long window, int action, KeyInput input, CallbackInfo info) {
        Sydney.EVENT_HANDLER.post(new UnfilteredKeyInputEvent(input.key(), input.scancode(), action, input.modifiers()));
        if (window == client.getWindow().getHandle() && action == 1 && client.currentScreen == null) {
            Sydney.EVENT_HANDLER.post(new KeyInputEvent(input.key(), input.modifiers()));
        }
    }
}
