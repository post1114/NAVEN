package com.heypixel.heypixelmod.obsoverlay.modules.impl.misc;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventHandlePacket;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRunTicks;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.ModeValue;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;

@ModuleInfo(
   name = "NoRotateSet",
   description = "Prevents the server from rotating your head",
   category = Category.MISC
)
public class NoRotateSet extends Module {
   private final Minecraft mc = Minecraft.getInstance();

   private final ModeValue mode = ValueBuilder.create(this, "Mode")
      .setModes("SilentAccept", "ResetRotation")
      .setDefaultModeIndex(0)
      .build()
      .getModeValue();

   private float savedYaw;
   private float savedPitch;
   private boolean shouldRestoreRotation = false;

   @EventTarget
   public void onPacket(EventHandlePacket event) {
      if (mc.player == null || mc.getConnection() == null) return;
      Packet<?> packet = event.getPacket();
      if (!(packet instanceof ClientboundPlayerPositionPacket)) return;

      this.savedYaw = mc.player.getYRot();
      this.savedPitch = mc.player.getXRot();
      this.shouldRestoreRotation = true;
   }

   @EventTarget
   public void onTick(EventRunTicks event) {
      if (event.getType() != EventType.POST) return;
      if (mc.player == null) return;

      if (this.shouldRestoreRotation) {
         this.shouldRestoreRotation = false;

         String modeName = this.mode.getCurrentMode();
         if ("SilentAccept".equals(modeName)) {
            mc.player.setYRot(this.savedYaw);
            mc.player.setXRot(this.savedPitch);
         } else if ("ResetRotation".equals(modeName)) {
            mc.player.setYRot(this.savedYaw);
            mc.player.setXRot(this.savedPitch);
         }
      }
   }

   @Override
   public void onDisable() {
      this.shouldRestoreRotation = false;
   }
}
