package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventAttack;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRunTicks;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.utils.Vector2f;
import com.heypixel.heypixelmod.obsoverlay.utils.rotation.RotationManager;
import com.heypixel.heypixelmod.obsoverlay.utils.rotation.RotationUtils;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
   name = "DoubleHit",
   description = "Experimental feature",
   category = Category.COMBAT
)
public class DoubleHit extends Module {
   public static boolean suppressCrit = false;
   private final Map<Integer, Entity> scheduledAttacks = new LinkedHashMap<>();
   private boolean shouldJump = false;
   private int attackIndex = 0;

   @EventTarget
   public void onAttack(EventAttack event) {
      if (mc.player == null || mc.level == null) return;
      Entity target = event.getTarget();
      if (target == null || !target.isAlive()) return;
      if (!(target instanceof LivingEntity)) return;
      if (!mc.player.onGround()) return;

      this.shouldJump = true;
      this.attackIndex = 0;
      this.scheduledAttacks.put(5, target);
      this.scheduledAttacks.put(6, target);
   }

   @EventTarget
   public void onTick(EventRunTicks event) {
      if (event.getType() != EventType.PRE) return;
      if (mc.player == null || mc.level == null || mc.gameMode == null) return;

      if (this.shouldJump) {
         this.shouldJump = false;
         mc.player.jumpFromGround();
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
               this.doSilentAttack(target);
               suppressCrit = false;
               this.attackIndex++;
            }
         } else {
            it.remove();
            scheduledAttacks.put(delay - 1, target);
         }
      }
   }

   private void doSilentAttack(Entity target) {
      Vector2f oldRotation = RotationManager.rotations != null ? new Vector2f(RotationManager.rotations.x, RotationManager.rotations.y) : null;
      boolean wasActive = RotationManager.active;

      boolean needsSilent = !this.isLookingAt(target);
      if (needsSilent) {
         Vec3 eyePos = mc.player.getEyePosition();
         Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
         float yaw = (float) Math.toDegrees(Math.atan2(targetPos.z - eyePos.z, targetPos.x - eyePos.x)) - 90.0F;
         double diffXZ = Math.sqrt((targetPos.x - eyePos.x) * (targetPos.x - eyePos.x) + (targetPos.z - eyePos.z) * (targetPos.z - eyePos.z));
         float pitch = (float) (-Math.toDegrees(Math.atan2(targetPos.y - eyePos.y, diffXZ)));
         RotationManager.rotations = new Vector2f(yaw, pitch);
         RotationManager.active = true;
      }

      float oldYaw = mc.player.getYRot();
      float oldPitch = mc.player.getXRot();
      if (needsSilent) {
         mc.player.setYRot(RotationManager.rotations.x);
         mc.player.setXRot(RotationManager.rotations.y);
      }

      mc.gameMode.attack(mc.player, target);
      mc.player.swing(InteractionHand.MAIN_HAND);

      mc.player.setYRot(oldYaw);
      mc.player.setXRot(oldPitch);

      if (needsSilent) {
         RotationManager.rotations = oldRotation;
         RotationManager.active = wasActive;
      }
   }

   private boolean isLookingAt(Entity entity) {
      if (mc.player == null) return false;
      Vector2f rotation = RotationManager.rotations;
      if (rotation == null) return false;
      RotationUtils.Data data = RotationUtils.getRotationDataToEntity(entity);
      return data != null && data.getRotation() != null
         && Math.abs(RotationUtils.getAngleDifference(rotation.x, data.getRotation().x)) < 30
         && Math.abs(rotation.y - data.getRotation().y) < 30;
   }

   @Override
   public void onDisable() {
      this.scheduledAttacks.clear();
      this.shouldJump = false;
      this.attackIndex = 0;
      suppressCrit = false;
   }
}
