package me.aidan.sydney.mixins.accessors;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientPlayerEntity.class)
public interface ClientPlayerEntityAccessor {
    /** 1.21.11 removed {@code isWalking()} and made {@code canSprint} take the collision flag. */
    @Invoker("canSprint")
    boolean invokeCanSprint(boolean horizontalCollision);

    @Accessor("lastOnGround")
    void setLastOnGround(boolean lastOnGround);
}
