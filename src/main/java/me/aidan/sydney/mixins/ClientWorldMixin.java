package me.aidan.sydney.mixins;

import me.aidan.sydney.Sydney;
import me.aidan.sydney.events.impl.EntitySpawnEvent;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public class ClientWorldMixin {
    @Inject(method = "addEntity", at = @At(value = "HEAD"))
    private void addEntity(Entity entity, CallbackInfo info) {
        Sydney.EVENT_HANDLER.post(new EntitySpawnEvent(entity));
    }
}
