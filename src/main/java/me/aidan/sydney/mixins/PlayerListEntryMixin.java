package me.aidan.sydney.mixins;

import com.mojang.authlib.GameProfile;
import me.aidan.sydney.Sydney;
import me.aidan.sydney.modules.impl.core.CapesModule;
import me.aidan.sydney.utils.IMinecraft;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(PlayerListEntry.class)
public class PlayerListEntryMixin implements IMinecraft {
    @Shadow @Final private GameProfile profile;

    /**
     * 1.21.9 turned SkinTextures into a record of AssetInfo textures, so the cape can no longer be
     * swapped in through its constructor; it is applied as a skin override instead.
     */
    @Inject(method = "getSkinTextures", at = @At("TAIL"), cancellable = true)
    private void getSkinTextures(CallbackInfoReturnable<SkinTextures> info) {
        SkinTextures texture = info.getReturnValue();
        if (texture == null || mc.player == null) return;

        CapesModule capes = Sydney.MODULE_MANAGER.getModule(CapesModule.class);
        if (!capes.isToggled() || capes.getCapeTexture() == null) return;

        GameProfile ownProfile = mc.player.getGameProfile();
        if (!profile.name().equals(ownProfile.name()) || !profile.id().equals(ownProfile.id())) return;

        Identifier capeTexture = capes.getCapeTexture();
        SkinTextures.SkinOverride override = new SkinTextures.SkinOverride(
                Optional.empty(),
                Optional.of(new AssetInfo.TextureAssetInfo(capeTexture)),
                Optional.empty(),
                Optional.empty()
        );

        info.setReturnValue(texture.withOverride(override));
    }
}
