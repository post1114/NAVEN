package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventHandlePacket;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMoveInput;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRunTicks;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.move.Blink;
import com.heypixel.heypixelmod.obsoverlay.utils.ChatUtils;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.ModeValue;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
   name = "TickBase",
   description = "Speeds up ticks when needed to close distance for attacks.",
   category = Category.COMBAT
)
public class TickBase extends Module {
   private final ModeValue mode = ValueBuilder.create(this, "Mode").setModes("Past", "Future").setDefaultModeIndex(0).build().getModeValue();
   private final FloatValue range = ValueBuilder.create(this, "Range")
      .setDefaultFloatValue(3.5F)
      .setFloatStep(0.1F)
      .setMinFloatValue(1.0F)
      .setMaxFloatValue(6.0F)
      .build()
      .getFloatValue();
   private final FloatValue balanceRecovery = ValueBuilder.create(this, "BalanceRecover")
      .setDefaultFloatValue(1.0F)
      .setFloatStep(0.1F)
      .setMinFloatValue(0.0F)
      .setMaxFloatValue(2.0F)
      .build()
      .getFloatValue();
   private final FloatValue balanceMax = ValueBuilder.create(this, "BalanceMax")
      .setDefaultFloatValue(20.0F)
      .setFloatStep(1.0F)
      .setMinFloatValue(1.0F)
      .setMaxFloatValue(200.0F)
      .build()
      .getFloatValue();
   private final FloatValue maxTicks = ValueBuilder.create(this, "MaxTicks")
      .setDefaultFloatValue(4.0F)
      .setFloatStep(1.0F)
      .setMinFloatValue(1.0F)
      .setMaxFloatValue(20.0F)
      .build()
      .getFloatValue();
   private final BooleanValue pauseOnFlag = ValueBuilder.create(this, "PauseOnFlag").setDefaultBooleanValue(true).build().getBooleanValue();
   private final FloatValue pauseTicks = ValueBuilder.create(this, "Pause")
      .setDefaultFloatValue(0.0F)
      .setFloatStep(1.0F)
      .setMinFloatValue(0.0F)
      .setMaxFloatValue(20.0F)
      .build()
      .getFloatValue();
   private final FloatValue cooldown = ValueBuilder.create(this, "Cooldown")
      .setDefaultFloatValue(0.0F)
      .setFloatStep(1.0F)
      .setMinFloatValue(0.0F)
      .setMaxFloatValue(20.0F)
      .build()
      .getFloatValue();
   private final BooleanValue requiresAura = ValueBuilder.create(this, "RequiresAura").setDefaultBooleanValue(true).build().getBooleanValue();
   private final BooleanValue logging = ValueBuilder.create(this, "Logging").setDefaultBooleanValue(false).build().getBooleanValue();

   private int ticksToSkip = 0;
   private float tickBalance = 0f;
   private boolean reachedLimit = false;
   private int cooldownTimer = 0;
   private final List<TickData> tickBuffer = new ArrayList<>();

   @Override
   public void onEnable() {
      this.resetState();
   }

   @Override
   public void onDisable() {
      this.resetState();
   }

   private void resetState() {
      this.ticksToSkip = 0;
      this.tickBalance = 0f;
      this.reachedLimit = false;
      this.cooldownTimer = 0;
      this.tickBuffer.clear();
      Naven.TICK_TIMER = 1.0F;
   }

   private void log(String message) {
      if (this.logging.getCurrentValue()) {
         ChatUtils.addChatMessage("[TickBase] " + message);
      }
   }

   @EventTarget
   public void onTick(EventRunTicks event) {
      if (event.getType() != EventType.PRE) return;
      if (mc.player == null || mc.level == null || mc.getConnection() == null) return;
      if (mc.player.getVehicle() != null) return;
      if (Naven.getInstance().getModuleManager().getModule(Blink.class).isEnabled()) return;
      if (mc.player.isDeadOrDying() || !mc.player.isAlive() || mc.screen instanceof ProgressScreen || mc.screen instanceof DeathScreen) {
         this.resetState();
         return;
      }

      if (this.cooldownTimer > 0) {
         this.cooldownTimer--;
      }

      if (this.ticksToSkip-- > 0) {
         mc.player.noPhysics = true;
         mc.player.setDeltaMovement(mc.player.getDeltaMovement().multiply(0, 0, 0));
         return;
      }

      mc.player.noPhysics = false;

      if (this.tickBuffer.isEmpty()) return;
      if (this.cooldownTimer > 0) return;

      Entity nearestEnemy = this.findNearestEnemy();
      if (nearestEnemy == null) return;

      double currentDistanceSq = mc.player.position().distanceToSqr(nearestEnemy.position());
      double rangeSq = this.range.getCurrentValue() * this.range.getCurrentValue();

      int bestTick = -1;
      for (int i = 0; i < this.tickBuffer.size(); i++) {
         TickData tick = this.tickBuffer.get(i);
         double tickDistSq = tick.position.distanceToSqr(nearestEnemy.position());
         if (tickDistSq < currentDistanceSq && tickDistSq <= rangeSq) {
            bestTick = i;
            break;
         }
      }

      if (bestTick <= 0) return;

      if (this.requiresAura.getCurrentValue()) {
         Aura aura = (Aura) Naven.getInstance().getModuleManager().getModule(Aura.class);
         if (!aura.isEnabled()) return;
      }

      switch (this.mode.getCurrentMode()) {
         case "Past" -> {
            int skip = bestTick;
            for (int i = 0; i < skip; i++) {
               Naven.skipTasks.add(() -> {
                  mc.player.noPhysics = true;
                  mc.player.setDeltaMovement(0, 0, 0);
               });
               this.tickBalance -= 1f;
            }
            this.ticksToSkip = (int) this.pauseTicks.getCurrentValue();
            this.log("Past skip: " + skip + " ticks");
         }
         case "Future" -> {
            int totalSkipped = 0;
            for (int i = 0; i < bestTick; i++) {
               this.tickBalance -= 1f;
               totalSkipped++;
            }
            this.log("Future skip: " + totalSkipped + " ticks");
            this.ticksToSkip = totalSkipped + (int) this.pauseTicks.getCurrentValue();
            Naven.TICK_TIMER = 2.0F;
         }
      }

      this.cooldownTimer = (int) this.cooldown.getCurrentValue();
      this.tickBuffer.clear();
   }

   @EventTarget
   public void onPacket(EventHandlePacket event) {
      if (mc.player == null) return;
      if (event.getPacket() instanceof ClientboundPlayerPositionPacket && this.pauseOnFlag.getCurrentValue()) {
         this.tickBalance = 0f;
         this.log("Flag detected, balance reset");
      }
   }

   @EventTarget
   public void onInput(EventMoveInput event) {
      if (mc.player == null || mc.level == null) return;
      if (mc.player.getVehicle() != null) return;
      if (Naven.getInstance().getModuleManager().getModule(Blink.class).isEnabled()) return;

      this.tickBuffer.clear();

      if (this.tickBalance <= 0) {
         this.reachedLimit = true;
      }
      if (this.tickBalance * 2 > this.balanceMax.getCurrentValue()) {
         this.reachedLimit = false;
      }
      if (this.tickBalance <= this.balanceMax.getCurrentValue()) {
         this.tickBalance += this.balanceRecovery.getCurrentValue();
      }

      if (this.reachedLimit) return;

      int tickCount = Math.min((int) this.tickBalance, (int) this.maxTicks.getCurrentValue());
      Vec3 position = mc.player.position();
      Vec3 velocity = mc.player.getDeltaMovement();
      boolean onGround = mc.player.onGround();
      double fallDistance = 0.0;
      int groundY = mc.player.blockPosition().getY();

      for (int i = 0; i < tickCount; i++) {
         double prevY = position.y;
         position = position.add(velocity);
         velocity = velocity.multiply(0.91, 0.98, 0.91);
         velocity = velocity.add(0, -0.08, 0);
         if (onGround) {
            velocity = velocity.multiply(0.6, 1.0, 0.6);
         }
         boolean newOnGround = position.y <= groundY + 1;
         if (!onGround && newOnGround) {
            fallDistance = 0;
         }
         if (!newOnGround) {
            fallDistance += Math.abs(prevY - position.y);
         }
         onGround = newOnGround;
         this.tickBuffer.add(new TickData(position, velocity, onGround, fallDistance));
      }
   }

   private Entity findNearestEnemy() {
      double closestDistSq = Double.MAX_VALUE;
      Entity closest = null;
      for (Entity entity : mc.level.entitiesForRendering()) {
         if (entity == mc.player) continue;
         if (!(entity instanceof Player)) continue;
         if (!entity.isAlive()) continue;
         if (entity.isSpectator()) continue;
         if (mc.player.distanceToSqr(entity) < closestDistSq) {
            closestDistSq = mc.player.distanceToSqr(entity);
            closest = entity;
         }
      }
      return closest;
   }

   private static class TickData {
      final Vec3 position;
      final Vec3 velocity;
      final boolean onGround;
      final double fallDistance;

      TickData(Vec3 position, Vec3 velocity, boolean onGround, double fallDistance) {
         this.position = position;
         this.velocity = velocity;
         this.onGround = onGround;
         this.fallDistance = fallDistance;
      }
   }
}
