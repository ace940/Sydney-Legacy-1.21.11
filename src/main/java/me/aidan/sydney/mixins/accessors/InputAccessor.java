package me.aidan.sydney.mixins.accessors;

import net.minecraft.client.input.Input;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Input.class)
public interface InputAccessor {
    @Mutable
    @Accessor("movementVector")
    void sydney$setMovementVector(Vec2f movementVector);

    @Accessor("movementVector")
    Vec2f sydney$getMovementVector();
}
