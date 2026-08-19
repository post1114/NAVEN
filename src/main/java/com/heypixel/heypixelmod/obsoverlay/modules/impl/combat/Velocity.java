package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventHandlePacket;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMotion;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRespawn;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRunTicks;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventStrafe;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.move.LongJump;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.move.Stuck;
import com.heypixel.heypixelmod.obsoverlay.utils.ChatUtils;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import java.util.concurrent.LinkedBlockingDeque;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
   name = "Velocity",
   description = "Reduces knockback.",
   category = Category.MOVEMENT
)
public class Velocity extends Module {
   public static boolean isAttacking;
   public static int attackCount;

   private final LinkedBlockingDeque<Packet<?>> packetQueue = new LinkedBlockingDeque<>();
   private final LinkedBlockingDeque<Packet<?>> movePacketQueue = new LinkedBlockingDeque<>();

   private BooleanValue logging = ValueBuilder.create(this, "Logging").setDefaultBooleanValue(false).build().getBooleanValue();
   private FloatValue attackAmount = ValueBuilder.create(this, "Attack Amount")
      .setDefaultFloatValue(2.0F)
      .setFloatStep(1.0F)
      .setMinFloatValue(1.0F)
      .setMaxFloatValue(5.0F)
      .build()
      .getFloatValue();
   private BooleanValue instantAttack = ValueBuilder.create(this, "Instant Attack").setDefaultBooleanValue(false).build().getBooleanValue();
   private BooleanValue sprintCheck = ValueBuilder.create(this, "Sprint Check").setDefaultBooleanValue(false).build().getBooleanValue();

   private int attackCooldown = 0;
   private Entity attackTarget = null;
   private int attacksRemaining = 0;
   private int flagCooldown = 0;
   private boolean shouldJump = false;
   private int sprintBoostCounter = 0;
   private int hitCounter = 0;
   private boolean isSuspending = false;
   private int suspendTicks = 0;
   private ClientboundSetEntityMotionPacket knockbackPacket = null;
   private volatile boolean isFlushing = false;
   private float instantAttackProgress = 0.0f;
   private boolean isInstantAttacking = false;
   private boolean shouldFlushMotion;

   public Velocity() {
      super("Velocity", "Reduces knockback.", Category.MOVEMENT);
   }

   @Override
   public void onEnable() {
      this.resetAll();
   }

   @Override
   public void onDisable() {
      this.resetAll();
   }

   private void log(String message) {
      if (this.logging.getCurrentValue()) {
         ChatUtils.addChatMessage(message);
      }
   }

   @EventTarget
   public void onRespawn(EventRespawn event) {
      this.resetAll();
   }

   @EventTarget
   public void onMotion(EventMotion event) {
      if (event.getType() == EventType.PRE && this.shouldFlushMotion) {
         while (!this.packetQueue.isEmpty()) {
            Packet<?> packet = this.packetQueue.poll();
            if (packet == null) continue;
            try {
               packet.handle(mc.getConnection());
            } catch (Exception e) {
               e.printStackTrace();
            }
         }
         this.shouldFlushMotion = false;
      }
   }

   @EventTarget
   public void onPacket(EventHandlePacket event) {
      if (mc.player == null || mc.getConnection() == null || mc.gameMode == null) {
         return;
      }
      if (this.isFlushing) {
         return;
      }
      if (this.shouldIgnore()) {
         return;
      }
      if (Naven.getInstance().getModuleManager().getModule(LongJump.class).isEnabled()) {
         return;
      }
      if (mc.player.tickCount < 20) {
         this.resetAll();
         return;
      }

      Packet<?> packet = event.getPacket();

      if (packet instanceof ServerboundMovePlayerPacket && this.isSuspending) {
         this.movePacketQueue.add(packet);
         event.setCancelled(true);
         return;
      }

      if (packet instanceof ClientboundPlayerPositionPacket) {
         if (this.isSuspending) {
            this.release();
         }
         this.resetSuspension();
         this.log("Flag Detected");
         this.flagCooldown = 2;
      }

      if (this.flagCooldown != 0) {
         return;
      }

      if (this.isSuspending) {
         if (!this.isAllowedPacket(packet)) {
            this.packetQueue.add(packet);
            event.setCancelled(true);
         }
         return;
      }

      if (packet instanceof ClientboundSetEntityMotionPacket motionPacket) {
         if (motionPacket.getId() != mc.player.getId()) {
            return;
         }

         double dx = -motionPacket.getXa();
         double dz = -motionPacket.getZa();
         if (Math.abs(dx) > 0.01 || Math.abs(dz) > 0.01) {
            this.hitCounter = 1;
         }

         if (motionPacket.getYa() > 0) {
            this.sprintBoostCounter = this.sprintBoostCounter % 100 + 100;
            if (this.sprintBoostCounter >= 100) {
               this.shouldJump = true;
            }

            Entity target = this.getAttackTarget();
            boolean canAttack = this.isValidTarget(target) && mc.player.isSprinting();

            if (!mc.player.onGround()) {
               this.isSuspending = true;
               this.suspendTicks = 0;
               this.knockbackPacket = motionPacket;
               event.setCancelled(true);
            } else if (canAttack) {
               this.attackTarget = target;
               this.attacksRemaining = this.attackAmount.getCurrentValue().intValue();
            } else {
               this.isSuspending = true;
               this.suspendTicks = 0;
               this.knockbackPacket = motionPacket;
               event.setCancelled(true);
               this.log("Alink Wait");
            }
         }
      }
   }

   @EventTarget
   public void onTick(EventRunTicks event) {
      if (event.getType() != EventType.PRE) return;
      if (mc.player == null || mc.getConnection() == null || mc.gameMode == null) return;
      if (Naven.getInstance().getModuleManager().getModule(LongJump.class).isEnabled()) return;

      if (this.attackCooldown > 0) {
         --this.attackCooldown;
         if (this.attackCooldown <= 0) {
            isAttacking = false;
            attackCount = 0;
         }
      }

      if (this.hitCounter > 0) {
         ++this.hitCounter;
         if (this.hitCounter > 2) {
            this.hitCounter = 0;
         }
      }

      if (mc.player.isDeadOrDying() || !mc.player.isAlive() || this.shouldIgnore()
            || mc.screen instanceof ProgressScreen || mc.screen instanceof DeathScreen) {
         this.clearTarget();
         if (this.isSuspending) {
            this.release();
         }
         if (this.isInstantAttacking) {
            this.isInstantAttacking = false;
            this.instantAttackProgress = 0.0f;
         }
         return;
      }

      if (this.flagCooldown > 0) {
         --this.flagCooldown;
         this.clearTarget();
      }

      if (this.isSuspending) {
         ++this.suspendTicks;
         boolean instantAttackEnabled = this.instantAttack.getCurrentValue();

         if (instantAttackEnabled && this.instantAttackProgress < 3.0f) {
            this.instantAttackProgress += 0.5f;
            this.instantAttackProgress = Math.min(this.instantAttackProgress, 3.0f);
         }

         boolean onGround = mc.player.onGround();
         boolean isTimeout = this.suspendTicks >= 12;

         if (onGround || isTimeout) {
            this.log(isTimeout ? "Alink Timeout" : "ground");
            Entity target = this.getAttackTarget();
            boolean canAttack = this.isValidTarget(target);
            boolean sprinting = mc.player.isSprinting();

            if (onGround && canAttack && sprinting) {
               this.isFlushing = true;
               this.attackTarget = target;
               this.attacksRemaining = this.attackAmount.getCurrentValue().intValue();
               this.sendMovePackets();
               this.applyKnockbackPacket();

               if (instantAttackEnabled && this.instantAttackProgress > 0.0f) {
                  this.attacksRemaining = (int) this.instantAttackProgress;
                  this.scheduleMotionFlush();
                  this.isSuspending = false;
                  this.suspendTicks = 0;
                  this.isFlushing = false;
                  this.isInstantAttacking = true;
               } else {
                  this.doAttackSequence();
                  this.scheduleMotionFlush();
                  this.isSuspending = false;
                  this.suspendTicks = 0;
                  this.isFlushing = false;
               }
            } else {
               this.release();
               if (instantAttackEnabled) {
                  this.instantAttackProgress = 0.0f;
               }
               if (onGround && mc.player.isSprinting()) {
                  mc.player.setSprinting(false);
               }
            }
            return;
         }
         return;
      }

      if (this.isInstantAttacking) {
         this.instantAttackProgress -= 1.0f;
         if (this.instantAttackProgress <= 0.0f) {
            this.instantAttackProgress = 0.0f;
            this.isInstantAttacking = false;
            this.log("done");
         }
      }

      if (this.attacksRemaining > 0 && this.attackTarget != null) {
         this.doAttackSequence();
      }
   }

   @EventTarget
   public void onStrafe(EventStrafe event) {
      if (mc.player == null) return;

      if (this.shouldJump) {
         this.shouldJump = false;
      }
   }

   private void resetAll() {
      this.clearTarget();
      this.flagCooldown = 0;
      this.shouldJump = false;
      this.sprintBoostCounter = 0;
      this.hitCounter = 0;
      this.resetSuspension();
   }

   private void clearTarget() {
      this.attackTarget = null;
      this.attacksRemaining = 0;
   }

   private void resetSuspension() {
      this.isSuspending = false;
      this.suspendTicks = 0;
      this.knockbackPacket = null;
      this.packetQueue.clear();
      this.movePacketQueue.clear();
      this.isFlushing = false;
      this.instantAttackProgress = 0.0f;
      this.isInstantAttacking = false;
   }

   private boolean shouldIgnore() {
      if (mc.player == null || mc.level == null) return true;
      if (mc.player.isDeadOrDying() || !mc.player.isAlive() || mc.player.getHealth() <= 0.0f) return true;
      if (mc.player.isSpectator() || mc.player.getAbilities().flying) return true;
      if (mc.player.isInLava() || mc.player.isOnFire() || mc.player.isInWater() || mc.player.onClimbable() || mc.player.isSleeping()) return true;
      if (mc.level.getBlockState(mc.player.blockPosition()).is(Blocks.COBWEB)) return true;
      return Naven.getInstance().getModuleManager().getModule(Stuck.class) != null
         && Naven.getInstance().getModuleManager().getModule(Stuck.class).isEnabled();
   }

   private double getAABBDistance(Entity entity) {
      if (mc.player == null) return Double.MAX_VALUE;
      Vec3 eyePos = mc.player.getEyePosition(1.0f);
      AABB box = entity.getBoundingBox();
      double clampedX = Math.max(box.minX, Math.min(eyePos.x, box.maxX));
      double clampedY = Math.max(box.minY, Math.min(eyePos.y, box.maxY));
      double clampedZ = Math.max(box.minZ, Math.min(eyePos.z, box.maxZ));
      return eyePos.distanceTo(new Vec3(clampedX, clampedY, clampedZ));
   }

   private Entity getHitResultEntity() {
      if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY) {
         Entity hitEntity = ((EntityHitResult) mc.hitResult).getEntity();
         if (hitEntity instanceof LivingEntity && hitEntity != mc.player && hitEntity.isAlive() && !hitEntity.isSpectator()) {
            return hitEntity;
         }
      }
      return null;
   }

   private Entity getAttackTarget() {
      if (Aura.target != null) {
         return Aura.target;
      }
      return this.getHitResultEntity();
   }

   private boolean isValidTarget(Entity entity) {
      if (entity == null || !entity.isAlive()) return false;
      if (entity instanceof LivingEntity living) {
         if (living.isDeadOrDying() || living.getHealth() <= 0.0f) return false;
      }
      double maxReach = 3.7f;
      return !(this.getAABBDistance(entity) > maxReach);
   }

   private void doAttackSequence() {
      if (this.attackTarget == null || !this.attackTarget.isAlive()) {
         this.clearTarget();
         return;
      }
      double maxReach = 3.7f;
      if (this.getAABBDistance(this.attackTarget) > maxReach) {
         this.clearTarget();
         return;
      }
      isAttacking = true;
      attackCount = this.attacksRemaining--;
      this.attackCooldown = 2;
      this.doAttack(this.attackTarget);
      if (this.attacksRemaining <= 0) {
         this.clearTarget();
         if (this.instantAttack.getCurrentValue()) {
            this.log("Attack (" + this.attackAmount.getCurrentValue().intValue() + ")");
         }
      }
   }

   private boolean doAttack(Entity entity) {
      if (mc.player == null || mc.gameMode == null) return false;
      if (this.sprintCheck.getCurrentValue() && !mc.player.isSprinting()) {
         this.log("not sprinting");
         return false;
      }
      boolean wasSprinting = mc.player.isSprinting();
      if (wasSprinting) {
         mc.player.setSprinting(false);
      }
      mc.gameMode.attack(mc.player, entity);
      mc.player.swing(InteractionHand.MAIN_HAND);
      if (wasSprinting) {
         Vec3 velocity = mc.player.getDeltaMovement();
         mc.player.setDeltaMovement(velocity.x * 0.6, velocity.y, velocity.z * 0.6);
      }
      if (!this.instantAttack.getCurrentValue()) {
         this.log("Attack (" + this.attacksRemaining + ")");
      }
      return true;
   }

   private void sendMovePackets() {
      if (mc.getConnection() == null) return;
      while (!this.movePacketQueue.isEmpty()) {
         Packet<?> packet = this.movePacketQueue.poll();
         if (packet == null) continue;
         try {
            mc.getConnection().send(packet);
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
   }

   private void applyKnockbackPacket() {
      if (this.knockbackPacket != null && mc.getConnection() != null) {
         try {
            this.knockbackPacket.handle(mc.getConnection());
         } catch (Exception e) {
            e.printStackTrace();
         }
         this.knockbackPacket = null;
      }
   }

   private void scheduleMotionFlush() {
      if (mc.getConnection() == null) return;
      this.shouldFlushMotion = true;
   }

   private boolean isAllowedPacket(Packet<?> packet) {
      return packet instanceof ClientboundSetEntityMotionPacket
         || packet instanceof ClientboundSetHealthPacket
         || packet instanceof ClientboundPlayerPositionPacket
         || packet instanceof ClientboundSoundPacket
         || packet instanceof ClientboundPlayerChatPacket
         || packet instanceof ClientboundPlayerCombatKillPacket
         || packet instanceof ClientboundContainerClosePacket
         || packet instanceof ClientboundHurtAnimationPacket
         || packet instanceof ClientboundSetTitleTextPacket
         || packet instanceof ClientboundSetPlayerTeamPacket
         || packet instanceof ClientboundSystemChatPacket
         || packet instanceof ClientboundDisconnectPacket
         || (packet instanceof ClientboundAnimatePacket && ((ClientboundAnimatePacket) packet).getId() != mc.player.getId());
   }

   private void release() {
      this.isFlushing = true;
      this.sendMovePackets();
      this.applyKnockbackPacket();
      this.scheduleMotionFlush();
      this.isFlushing = false;
      this.isSuspending = false;
      this.suspendTicks = 0;
      this.instantAttackProgress = 0.0f;
      this.isInstantAttacking = false;
   }

   static {
      isAttacking = false;
      attackCount = 0;
   }
}
