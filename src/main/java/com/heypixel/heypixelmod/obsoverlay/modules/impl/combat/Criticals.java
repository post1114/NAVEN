package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventAttack;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

@ModuleInfo(
   name = "Criticals",
   description = "Makes your attacks critical hits.",
   category = Category.COMBAT
)
public class Criticals extends Module {

   @EventTarget
   public void onAttack(EventAttack event) {
      if (DoubleHit.suppressCrit) return;
      Entity target = event.getTarget();
      if (target == null) return;
      if (!(target instanceof LivingEntity)) return;
      if (!target.isAlive()) return;
      if (mc.player == null || mc.getConnection() == null) return;
      if (mc.player.onGround()) {
         Module doubleHit = Naven.getInstance().getModuleManager().getModule(DoubleHit.class);
         if (doubleHit == null || !doubleHit.isEnabled()) return;
      }

      mc.getConnection().send(
         new ServerboundMovePlayerPacket.PosRot(
            mc.player.getX(),
            mc.player.getY() - 0.000001,
            mc.player.getZ(),
            mc.player.getYRot(),
            mc.player.getXRot(),
            false
         )
      );
   }
}
