package me.aidan.sydney.mixins;

import me.aidan.sydney.Sydney;
import me.aidan.sydney.events.impl.MouseInputEvent;
import me.aidan.sydney.events.impl.UnfilteredMouseInputEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {
    @Shadow @Final private MinecraftClient client;

    // 1.21.9 moved the button and modifier arguments into a MouseInput record and left the GLFW
    // action on its own.
    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void onMouseButton(long window, MouseInput input, int action, CallbackInfo info) {
        Sydney.EVENT_HANDLER.post(new UnfilteredMouseInputEvent(input.button(), action, input.modifiers()));
        if (window == client.getWindow().getHandle() && action == 1 && client.currentScreen == null) {
            Sydney.EVENT_HANDLER.post(new MouseInputEvent(input.button()));
        }
    }
}
