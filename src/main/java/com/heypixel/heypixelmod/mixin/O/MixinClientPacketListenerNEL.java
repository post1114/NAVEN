package com.heypixel.heypixelmod.mixin.O;

import com.heypixel.heypixelmod.obsoverlay.nel.NelManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientPacketListener.class})
public class MixinClientPacketListenerNEL {

   @Inject(
      method = {"handleCustomPayload"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onHandleCustomPayload(ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
      ResourceLocation channel = packet.getIdentifier();
      FriendlyByteBuf data = packet.getData();

      NelManager nelManager = NelManager.getInstance();
      if (nelManager != null) {
         nelManager.handleServerPayload(channel, data);
      }
   }
}
