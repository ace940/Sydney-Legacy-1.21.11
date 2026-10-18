package me.aidan.sydney.utils.minecraft;

import me.aidan.sydney.mixins.accessors.ClientWorldAccessor;
import me.aidan.sydney.utils.IMinecraft;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.state.NetworkStateBuilder;
import net.minecraft.util.PlayerInput;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.listener.ServerPlayPacketListener;
import net.minecraft.network.packet.*;
import net.minecraft.network.packet.s2c.common.*;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.network.packet.s2c.query.PingResultS2CPacket;
import net.minecraft.network.state.PlayStateFactories;

public class NetworkUtils implements IMinecraft {
    public static void sendIgnoredPacket(Packet<?> packet) {
        mc.getNetworkHandler().getConnection().send(packet, null, true);
    }

    public static void sendSequencedPacket(SequencedPacketCreator packetCreator) {
        try (PendingUpdateManager pendingUpdateManager = ((ClientWorldAccessor)mc.world).invokeGetPendingUpdateManager().incrementSequence();){
            Packet<ServerPlayPacketListener> packet = packetCreator.predict(pendingUpdateManager.getSequence());
            mc.getNetworkHandler().sendPacket(packet);
        }
    }

    /**
     * 1.21.9 removed ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY / RELEASE_SHIFT_KEY.
     * Sneak state now travels inside PlayerInputC2SPacket, which the vanilla client only
     * re-sends when its own input changes — so an explicit packet keeps the server-side
     * sneak flag set exactly like the old press/release commands did.
     */
    public static void sendSneak(boolean sneak) {
        PlayerInput input = mc.player.input.playerInput;
        mc.player.networkHandler.sendPacket(new PlayerInputC2SPacket(
                new PlayerInput(input.forward(), input.backward(), input.left(), input.right(), input.jump(), sneak, input.sprint())));
    }
}
