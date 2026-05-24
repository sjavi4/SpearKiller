package me.autobot.client.mixin;

import me.autobot.client.SpearKillerClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class MixinLocalPlayer {

    @Inject(method = "sendPosition", at = @At(value = "HEAD"), cancellable = true)
    private void suppressSendPos(CallbackInfo ci) {
        if (SpearKillerClient.SUPPRESS) {
            ci.cancel();
        }
    }
}