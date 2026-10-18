package me.aidan.sydney.mixins;

import me.aidan.sydney.Sydney;
import me.aidan.sydney.events.impl.KeyboardTickEvent;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin extends Input {
    @Inject(method = "tick", at = @At(value = "TAIL"))
    private void tick$TAIL(CallbackInfo info) {
        // 1.21.9 replaced movementForward/movementSideways with the movementVector Vec2f (x = sideways, y = forward).
        KeyboardTickEvent event = new KeyboardTickEvent(movementVector.y, movementVector.x);
        Sydney.EVENT_HANDLER.post(event);
        if (event.isCancelled()) {
            this.movementVector = new Vec2f(event.getMovementSideways(), event.getMovementForward());
        }
    }
}
