package me.aidan.sydney.mixins.accessors;

import net.minecraft.entity.LimbAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LimbAnimator.class)
public interface LimbAnimatorAccessor {
    /** 1.21.11 renamed LimbAnimator's {@code pos} field to {@code animationProgress}. */
    @Accessor("animationProgress")
    void setPos(float pos);

    @Accessor("animationProgress")
    float getPos();
}
