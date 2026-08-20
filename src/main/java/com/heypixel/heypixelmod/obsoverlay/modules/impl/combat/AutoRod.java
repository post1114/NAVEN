package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRunTicks;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.utils.PacketUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.rotation.RotationManager;
import com.heypixel.heypixelmod.obsoverlay.utils.rotation.RotationUtils;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
   name = "AutoRod",
   description = "Automatically uses fishing rod for combat",
   category = Category.COMBAT
)
public class AutoRod extends Module {
   private final FloatValue range = ValueBuilder.create(this, "Range")
      .setDefaultFloatValue(4.0F)
      .setFloatStep(0.5F)
      .setMinFloatValue(2.0F)
      .setMaxFloatValue(10.0F)
      .build()
      .getFloatValue();

   private final FloatValue cooldown = ValueBuilder.create(this, "Cooldown")
      .setDefaultFloatValue(6.0F)
      .setFloatStep(1.0F)
      .setMinFloatValue(1.0F)
      .setMaxFloatValue(50.0F)
      .build()
      .getFloatValue();

   private final BooleanValue autoRotate = ValueBuilder.create(this, "AutoRotate")
      .setDefaultBooleanValue(true)
      .build()
      .getBooleanValue();

   private final BooleanValue smartPull = ValueBuilder.create(this, "SmartPull")
      .setDefaultBooleanValue(true)
      .build()
      .getBooleanValue();

   private int cooldownTimer = 0;
   private boolean waitingForPull = false;
   private int pullTicks = 0;
   private FishingHook currentBobber = null;

   @EventTarget
   public void onTick(EventRunTicks event) {
      if (event.getType() != EventType.PRE) return;
      if (mc.player == null || mc.level == null || mc.gameMode == null) return;

      if (cooldownTimer > 0) {
         cooldownTimer--;
         return;
      }

      if (mc.screen instanceof AbstractContainerScreen) return;
      if (mc.player.isUsingItem()) return;

      int rodSlot = findRodSlot();
      if (rodSlot == -1) return;

      updateBobber();

      if (waitingForPull) {
         pullTicks++;
         if (pullTicks >= 30) {
            waitingForPull = false;
            pullTicks = 0;
            reelIn(rodSlot);
         }
         return;
      }

      if (currentBobber != null && currentBobber.getHookedIn() != null) {
         reelIn(rodSlot);
         return;
      }

      LivingEntity target = findTarget();
      if (target == null) return;

      if (currentBobber == null) {
         castRod(rodSlot, target);
      } else if (smartPull.getCurrentValue()) {
         double dist = mc.player.distanceTo(target);
         if (dist > range.getCurrentValue()) {
            reelIn(rodSlot);
         }
      }
   }

   private void updateBobber() {
      if (currentBobber != null && (currentBobber.isRemoved() || currentBobber.getPlayerOwner() != mc.player)) {
         currentBobber = null;
      }
      if (currentBobber == null) {
         for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof FishingHook hook && hook.getPlayerOwner() == mc.player) {
               currentBobber = hook;
               break;
            }
         }
      }
   }

   private LivingEntity findTarget() {
      List<LivingEntity> targets = StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), false)
         .filter(e -> e instanceof LivingEntity && e != mc.player && !e.isRemoved())
         .map(e -> (LivingEntity) e)
         .filter(e -> e.getHealth() > 0)
         .filter(e -> mc.player.distanceTo(e) <= range.getCurrentValue() + 2.0F)
         .sorted(Comparator.comparingDouble(mc.player::distanceTo))
         .collect(Collectors.toList());
      return targets.isEmpty() ? null : targets.get(0);
   }

   private int findRodSlot() {
      for (int i = 0; i < 9; i++) {
         if (mc.player.getInventory().items.get(i).getItem() == Items.FISHING_ROD) {
            return i;
         }
      }
      return -1;
   }

    private void castRod(int slot, LivingEntity target) {
       mc.player.getInventory().selected = slot;

       if (autoRotate.getCurrentValue()) {
          Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
          RotationManager.rotations = RotationUtils.getRotationsVector(targetPos);
          RotationManager.active = true;
       }

       PacketUtils.sendSequencedPacket(id -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, id));

       waitingForPull = true;
       pullTicks = 0;
    }

    private void reelIn(int slot) {
       mc.player.getInventory().selected = slot;
       PacketUtils.sendSequencedPacket(id -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, id));

       cooldownTimer = (int) cooldown.getCurrentValue();
       waitingForPull = false;
       pullTicks = 0;
       currentBobber = null;
    }

   @Override
   public void onDisable() {
      cooldownTimer = 0;
      waitingForPull = false;
      pullTicks = 0;
      currentBobber = null;
      RotationManager.rotations = null;
      RotationManager.active = false;
   }
}
