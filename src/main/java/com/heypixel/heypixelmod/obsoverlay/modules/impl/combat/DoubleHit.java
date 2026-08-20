package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventAttack;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRunTicks;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.utils.rotation.RotationManager;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

@ModuleInfo(
   name = "DoubleHit",
   description = "Experimental feature",
   category = Category.COMBAT
)
public class DoubleHit extends Module {
   public static boolean suppressCrit = false;
   private final Map<Integer, Entity> scheduledAttacks = new LinkedHashMap<>();
   private boolean shouldJump = false;
   private int jumpDelay = 0;
   private int attackIndex = 0;

   @EventTarget
   public void onAttack(EventAttack event) {
      if (mc.player == null || mc.level == null) return;
      Entity target = event.getTarget();
      if (target == null || !target.isAlive()) return;
      if (!(target instanceof LivingEntity)) return;
      if (!mc.player.onGround()) return;

      this.shouldJump = true;
      this.jumpDelay = 1;
      this.attackIndex = 0;
      this.scheduledAttacks.put(5, target);
      this.scheduledAttacks.put(6, target);
   }

   @EventTarget
   public void onTick(EventRunTicks event) {
      if (event.getType() != EventType.PRE) return;
      if (mc.player == null || mc.level == null || mc.gameMode == null) return;

      if (this.shouldJump) {
         if (this.jumpDelay > 0) {
            this.jumpDelay--;
         } else {
            this.shouldJump = false;
            mc.player.jumpFromGround();
         }
      }

      if (this.scheduledAttacks.isEmpty()) return;

      Iterator<Map.Entry<Integer, Entity>> it = this.scheduledAttacks.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<Integer, Entity> entry = it.next();
         int delay = entry.getKey();
         Entity target = entry.getValue();

         if (delay <= 0) {
            it.remove();
            if (target.isAlive() && mc.player.isAlive()) {
               suppressCrit = (this.attackIndex == 1);
               boolean pauseSprint = (this.attackIndex == 1);
               this.doSilentAttack(target, pauseSprint);
               suppressCrit = false;
               this.attackIndex++;
            }
         } else {
            it.remove();
            scheduledAttacks.put(delay - 1, target);
         }
      }
   }

   private void doSilentAttack(Entity target, boolean pauseSprint) {
      boolean wasSprinting = mc.player.isSprinting();
      if (pauseSprint && wasSprinting) {
         mc.player.setSprinting(false);
      }

      float oldYaw = mc.player.getYRot();
      float oldPitch = mc.player.getXRot();
      if (RotationManager.rotations != null) {
         mc.player.setYRot(RotationManager.rotations.x);
         mc.player.setXRot(RotationManager.rotations.y);
      }

      mc.player.swing(InteractionHand.MAIN_HAND);
      mc.gameMode.attack(mc.player, target);

      mc.player.setYRot(oldYaw);
      mc.player.setXRot(oldPitch);

      if (pauseSprint && wasSprinting) {
         mc.player.setSprinting(true);
      }
   }

   @Override
   public void onDisable() {
      this.scheduledAttacks.clear();
      this.shouldJump = false;
      this.jumpDelay = 0;
      this.attackIndex = 0;
      suppressCrit = false;
   }
}
