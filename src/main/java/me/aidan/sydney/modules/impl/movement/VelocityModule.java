package me.aidan.sydney.modules.impl.movement;

import me.aidan.sydney.Sydney;
import me.aidan.sydney.events.SubscribeEvent;
import me.aidan.sydney.events.impl.PacketReceiveEvent;
import me.aidan.sydney.events.impl.TickEvent;
import me.aidan.sydney.mixins.accessors.EntityVelocityUpdateS2CPacketAccessor;
import me.aidan.sydney.mixins.accessors.Vec3dAccessor;
import me.aidan.sydney.modules.Module;
import me.aidan.sydney.modules.RegisterModule;
import me.aidan.sydney.settings.impl.BooleanSetting;
import me.aidan.sydney.settings.impl.CategorySetting;
import me.aidan.sydney.settings.impl.ModeSetting;
import me.aidan.sydney.settings.impl.NumberSetting;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

@RegisterModule(name = "Velocity", description = "Modifies the amount of knockback that you receive.", category = Module.Category.MOVEMENT)
public class VelocityModule extends Module {
    public ModeSetting mode = new ModeSetting("Mode", "The method that will be used to achieve the knockback modification.", "Normal", new String[]{"Normal", "Cancel", "Grim"});
    public NumberSetting horizontal = new NumberSetting("Horizontal", "The amount of horizontal knockback that you will receive.", new ModeSetting.Visibility(mode, "Normal"), 0, 0, 100);
    public NumberSetting vertical = new NumberSetting("Vertical", "The amount of vertical knockback that you will receive.", new ModeSetting.Visibility(mode, "Normal"), 0, 0, 100);
    public BooleanSetting explosions = new BooleanSetting("Explosions", "Modifies knockback received from explosions.", true);
    public BooleanSetting pause = new BooleanSetting("Pause", "Pauses the velocity for a certain duration whenever you get rubberbanded.", new ModeSetting.Visibility(mode, "Cancel", "Grim"), true);

    public CategorySetting antiPushCategory = new CategorySetting("AntiPush", "Prevents certain things from pushing you.");
    public BooleanSetting antiPush = new BooleanSetting("AntiPush", "Entities", "Prevents other entities from pushing you.", new CategorySetting.Visibility(antiPushCategory), true);
    public BooleanSetting antiLiquidPush = new BooleanSetting("AntiLiquidPush", "Liquids", "Prevents liquids from pushing you.", new CategorySetting.Visibility(antiPushCategory), false);
    public BooleanSetting antiBlockPush = new BooleanSetting("AntiBlockPush", "Blocks", "Prevents you from being pushed outside of blocks.", new CategorySetting.Visibility(antiPushCategory), true);
    public BooleanSetting antiFishingRod = new BooleanSetting("AntiFishingRod", "FishingRods", "Prevents fishing rods from pushing you.", new CategorySetting.Visibility(antiPushCategory), false);

    private boolean cancel;

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (mc.player == null) return;
        if (!cancel) return;

        if (mode.getValue().equalsIgnoreCase("Grim") && (!pause.getValue() || Sydney.SERVER_MANAGER.getSetbackTimer().hasTimeElapsed(100L))) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY(), mc.player.getZ(), Sydney.ROTATION_MANAGER.getServerYaw(), Sydney.ROTATION_MANAGER.getServerPitch(), mc.player.isOnGround(), mc.player.horizontalCollision));
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, mc.player.isCrawling() ? mc.player.getBlockPos() : mc.player.getBlockPos().up(), Direction.DOWN));
        }

        cancel = false;
    }

    @SubscribeEvent
    public void onPacketReceive(PacketReceiveEvent event) {
        if (mc.player == null) return;

        if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket packet) {
            if (packet.getEntityId() != mc.player.getId()) return;

            switch (mode.getValue()) {
                case "Normal" -> {
                    // The packet carries the knockback as blocks per tick now, so the 8000 scaling that
                    // used to convert the fixed point shorts is gone.
                    Vec3d current = mc.player.getVelocity();
                    Vec3d incoming = packet.getVelocity();
                    double horizontalScale = horizontal.getValue().doubleValue() / 100.0;
                    double verticalScale = vertical.getValue().doubleValue() / 100.0;

                    ((EntityVelocityUpdateS2CPacketAccessor) packet).setVelocity(new Vec3d(
                            (incoming.x - current.x) * horizontalScale + current.x,
                            (incoming.y - current.y) * verticalScale + current.y,
                            (incoming.z - current.z) * horizontalScale + current.z));
                }
                case "Cancel" -> {
                    if (pause.getValue() && !Sydney.SERVER_MANAGER.getSetbackTimer().hasTimeElapsed(100L)) return;

                    event.setCancelled(true);
                }
                case "Grim" -> {
                    if (pause.getValue() && !Sydney.SERVER_MANAGER.getSetbackTimer().hasTimeElapsed(100L)) return;

                    event.setCancelled(true);
                    cancel = true;
                }
            }
        }

        if (event.getPacket() instanceof ExplosionS2CPacket packet && explosions.getValue()) {
            switch (mode.getValue()) {
                case "Normal" -> {
                    if (packet.playerKnockback().isPresent()) ((Vec3dAccessor) packet.playerKnockback().get()).setX((float) (packet.playerKnockback().get().getX() * (horizontal.getValue().doubleValue() / 100.0)));
                    if (packet.playerKnockback().isPresent()) ((Vec3dAccessor) packet.playerKnockback().get()).setY((float) (packet.playerKnockback().get().getY() * (vertical.getValue().doubleValue() / 100.0)));
                    if (packet.playerKnockback().isPresent()) ((Vec3dAccessor) packet.playerKnockback().get()).setZ((float) (packet.playerKnockback().get().getZ() * (horizontal.getValue().doubleValue() / 100.0)));
                }
                case "Cancel" -> {
                    if (pause.getValue() && !Sydney.SERVER_MANAGER.getSetbackTimer().hasTimeElapsed(100L)) return;

                    event.setCancelled(true);
                }
                case "Grim" -> {
                    if (pause.getValue() && !Sydney.SERVER_MANAGER.getSetbackTimer().hasTimeElapsed(100L)) return;

                    event.setCancelled(true);
                    cancel = true;
                }
            }

            if (event.isCancelled()) {
                mc.executeSync(() -> {
                    Vec3d vec3d = packet.center();
                    mc.world.playSound(null, vec3d.getX(), vec3d.getY(), vec3d.getZ(), packet.explosionSound().value(), SoundCategory.BLOCKS, 4.0F, (1.0F + (mc.world.random.nextFloat() - mc.world.random.nextFloat()) * 0.2F) * 0.7F);
                    mc.world.addParticleClient(packet.explosionParticle(), vec3d.getX(), vec3d.getY(), vec3d.getZ(), 1.0, 0.0, 0.0);
                });
            }
        }
    }

    @Override
    public String getMetaData() {
        if (mode.getValue().equalsIgnoreCase("Cancel")) return "0%, 0%";
        if (mode.getValue().equalsIgnoreCase("Grim")) return "Grim";
        return horizontal.getValue().intValue() + "%, " + vertical.getValue().intValue() + "%";
    }
}