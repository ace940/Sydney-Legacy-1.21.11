package me.aidan.sydney.mixins.accessors;

import net.minecraft.client.Keyboard;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Keyboard.class)
public interface KeyboardAccessor {
    @Invoker("onKey")
    void sydney$onKey(long window, int action, KeyInput input);
}
