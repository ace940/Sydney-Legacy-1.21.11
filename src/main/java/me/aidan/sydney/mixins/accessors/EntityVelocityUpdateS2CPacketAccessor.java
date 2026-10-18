package me.aidan.sydney.mixins.accessors;

import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityVelocityUpdateS2CPacket.class)
public interface EntityVelocityUpdateS2CPacketAccessor {
    // 1.21.9 stores the velocity as a Vec3d in blocks per tick instead of three fixed point shorts.
    @Accessor("velocity") @Mutable
    void setVelocity(Vec3d velocity);
}
