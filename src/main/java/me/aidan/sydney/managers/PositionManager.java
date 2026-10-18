package me.aidan.sydney.managers;

import lombok.Getter;
import me.aidan.sydney.Sydney;
import me.aidan.sydney.events.SubscribeEvent;
import me.aidan.sydney.events.impl.PacketSendEvent;
import me.aidan.sydney.utils.IMinecraft;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;

@Getter
public class PositionManager implements IMinecraft {
    public PositionManager() {
        Sydney.EVENT_HANDLER.subscribe(this);
    }

    private double serverX;
    private double serverY;
    private double serverZ;

    private boolean serverOnGround;

    private boolean serverSprinting;
    private boolean serverSneaking;

    private int serverSlot;

    @SubscribeEvent
    public void onPacketSend(PacketSendEvent event) {
        if (mc.player == null) return;

        if (event.getPacket() instanceof PlayerMoveC2SPacket packet) {
            if (packet.changesPosition()) {
                serverX = packet.getX(mc.player.getX());
                serverY = packet.getY(mc.player.getY());
                serverZ = packet.getZ(mc.player.getZ());
            }

            serverOnGround = packet.isOnGround();
        }

        if (event.getPacket() instanceof UpdateSelectedSlotC2SPacket packet) {
            serverSlot = packet.getSelectedSlot();
        }

        if (event.getPacket() instanceof ClientCommandC2SPacket packet) {
            switch (packet.getMode()) {
                case START_SPRINTING -> serverSprinting = true;
                case STOP_SPRINTING -> serverSprinting = false;
            }
        }

        // 1.21.9+: sneak state is carried by PlayerInputC2SPacket instead of the old
        // PRESS_SHIFT_KEY / RELEASE_SHIFT_KEY command packets.
        if (event.getPacket() instanceof PlayerInputC2SPacket packet) {
            serverSneaking = packet.input().sneak();
        }
    }
}
