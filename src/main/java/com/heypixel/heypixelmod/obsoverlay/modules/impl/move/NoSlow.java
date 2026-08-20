package com.heypixel.heypixelmod.obsoverlay.modules.impl.move;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMotion;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventPacket;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventSlowdown;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRunTicks;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.utils.PacketUtils;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.ModeValue;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.UseAnim;

@ModuleInfo(
   name = "NoSlow",
   description = "Prevents slowdown when using items",
   category = Category.MOVEMENT
)
public class NoSlow extends Module {
   public static NoSlow INSTANCE;

   private final ModeValue mode = ValueBuilder.create(this, "Mode")
      .setModes("Grim", "Packet", "Spartan", "Vanilla")
      .setDefaultModeIndex(0)
      .build()
      .getModeValue();

   private final BooleanValue bowNoSlow = ValueBuilder.create(this, "Bow")
      .setDefaultBooleanValue(true)
      .setVisibility(() -> mode.isCurrentMode("Grim"))
      .build()
      .getBooleanValue();

   private final BooleanValue crossbowNoSlow = ValueBuilder.create(this, "Crossbow")
      .setDefaultBooleanValue(true)
      .setVisibility(() -> mode.isCurrentMode("Grim"))
      .build()
      .getBooleanValue();

   private final BooleanValue foodNoSlow = ValueBuilder.create(this, "Food")
      .setDefaultBooleanValue(true)
      .setVisibility(() -> mode.isCurrentMode("Grim"))
      .build()
      .getBooleanValue();

   private final BooleanValue potionNoSlow = ValueBuilder.create(this, "Potion")
      .setDefaultBooleanValue(true)
      .setVisibility(() -> mode.isCurrentMode("Grim"))
      .build()
      .getBooleanValue();

   private final BooleanValue shieldNoSlow = ValueBuilder.create(this, "Shield")
      .setDefaultBooleanValue(true)
      .setVisibility(() -> mode.isCurrentMode("Grim"))
      .build()
      .getBooleanValue();

   private final BooleanValue keepSprinting = ValueBuilder.create(this, "Keep Sprinting")
      .setDefaultBooleanValue(true)
      .build()
      .getBooleanValue();

   private final FloatValue useItemTicks = ValueBuilder.create(this, "Use Item Ticks")
      .setDefaultFloatValue(1.0F)
      .setMinFloatValue(1.0F)
      .setMaxFloatValue(20.0F)
      .setFloatStep(1.0F)
      .setVisibility(() -> mode.isCurrentMode("Grim") && bowNoSlow.getCurrentValue())
      .build()
      .getFloatValue();

   private final FloatValue timerDelay = ValueBuilder.create(this, "Timer Delay")
      .setDefaultFloatValue(150.0F)
      .setMinFloatValue(0.0F)
      .setMaxFloatValue(500.0F)
      .setFloatStep(10.0F)
      .setVisibility(() -> mode.isCurrentMode("Grim"))
      .build()
      .getFloatValue();

   private InteractionHand useHand = InteractionHand.MAIN_HAND;
   private InteractionHand lastUseHand = InteractionHand.MAIN_HAND;
   private InteractionHand pendingUseHand;
   private boolean didSwapHand;
   private boolean shouldReleaseItem;
   private int swapInitSlot;
   private int releaseTicksRemaining;
   private int pendingUseCount;
   private boolean isBlinking;
   private int blinkTicks;
   private int blinkDuration;
   private int idleTickCount;
   private long timer = 0L;

   public NoSlow() {
      INSTANCE = this;
   }

   @Override
   public void onEnable() {
      releaseTicksRemaining = 0;
      clearState();
      timer = System.currentTimeMillis();
      super.onEnable();
   }

   @Override
   public void onDisable() {
      clearState();
      restoreUseKeyState();
      super.onDisable();
   }

   private void clearState() {
      didSwapHand = false;
      shouldReleaseItem = false;
      pendingUseHand = null;
      releaseTicksRemaining = 0;
      isBlinking = false;
      blinkTicks = 0;
      blinkDuration = 0;
      idleTickCount = 0;
      pendingUseCount = 0;
   }

   @EventTarget
   public void onSlowdown(EventSlowdown event) {
      if (mc.player == null || !mc.player.isUsingItem()) return;
      ItemStack stack = mc.player.getUseItem();
      if (stack.isEmpty()) return;

      if (mode.isCurrentMode("Grim")) {
         handleGrimSlowdown(event, stack);
         return;
      }

      if (mode.isCurrentMode("Packet")) {
         handlePacketSlowdown(event, stack);
         return;
      }

      if (mode.isCurrentMode("Spartan")) {
         handleSpartanSlowdown(event, stack);
         return;
      }

      if (mode.isCurrentMode("Vanilla")) {
         handleVanillaSlowdown(event, stack);
      }
   }

   private void handleGrimSlowdown(EventSlowdown event, ItemStack stack) {
      Item item = stack.getItem();
      boolean isBow = item instanceof BowItem;
      boolean isCrossbow = item instanceof CrossbowItem;
      boolean isEdible = stack.isEdible();
      boolean isPotion = item instanceof PotionItem;

      if (isBow && crossbowNoSlow.getCurrentValue()) {
         event.setSlowdown(mc.player.tickCount % 3 != 0);
      } else if (isCrossbow && foodNoSlow.getCurrentValue()) {
         event.setSlowdown(mc.player.tickCount % 3 != 0);
      } else if (isEdible && foodNoSlow.getCurrentValue() || isPotion && potionNoSlow.getCurrentValue()) {
         event.setSlowdown(mc.player.getUseItemRemainingTicks() >= 1 || mc.player.tickCount % 3 != 0);
      } else if (isBow && bowNoSlow.getCurrentValue()) {
         event.setSlowdown(false);
      }

      if (keepSprinting.getCurrentValue()) {
         mc.player.setSprinting(true);
      }
   }

   private void handlePacketSlowdown(EventSlowdown event, ItemStack stack) {
      if (!isFoodOrPotion(stack) || mc.player.getUseItemRemainingTicks() <= 0) return;
      event.setSlowdown(false);
      if (keepSprinting.getCurrentValue()) {
         mc.player.setSprinting(true);
      }
   }

   private void handleSpartanSlowdown(EventSlowdown event, ItemStack stack) {
      event.setSlowdown(false);
      if (keepSprinting.getCurrentValue()) {
         mc.player.setSprinting(true);
      }
   }

   private void handleVanillaSlowdown(EventSlowdown event, ItemStack stack) {
      if (mc.player.getUseItemRemainingTicks() % 2 != 0 && mc.player.getUseItemRemainingTicks() <= 30) {
         event.setSlowdown(false);
         mc.player.setSprinting(true);
      }
   }

   @EventTarget
   public void onRunTicks(EventRunTicks event) {
      if (mc.player == null) {
         clearState();
         return;
      }

      if (isBlinking) {
         blinkTicks++;
      }

      if ((!mode.isCurrentMode("Grim") || !bowNoSlow.getCurrentValue()) && !isIdleState()) {
         clearState();
      }

      if (isUsingState()) {
         if (mc.player.isUsingItem()) {
            idleTickCount = 0;
         } else if (++idleTickCount >= 5) {
            clearState();
         }
      } else {
         idleTickCount = 0;
      }

      if (releaseTicksRemaining > 0) {
         releaseUseKey();
         releaseTicksRemaining--;
         if (releaseTicksRemaining == 0) {
            restoreUseKeyState();
         }
      }

      if (pendingUseHand != null) {
         startUseItem(pendingUseHand, pendingUseCount);
         pendingUseHand = null;
         pendingUseCount = 0;
      }

      if (isBlinking && blinkTicks >= blinkDuration) {
         finishBlink();
         return;
      }

      if (mode.isCurrentMode("Grim") && bowNoSlow.getCurrentValue() && didSwapHand && !isBlinking) {
         if (useHand != lastUseHand) {
            sendSwapOffhand();
         }
         didSwapHand = false;
         shouldReleaseItem = false;
         timer = System.currentTimeMillis();
         releaseTicksRemaining = (int) useItemTicks.getCurrentValue();
         releaseUseKey();
         PacketUtils.sendPacket(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN));
         return;
      }

      if (mode.isCurrentMode("Grim") && bowNoSlow.getCurrentValue() && shouldReleaseItem
         && mc.player.isUsingItem() && canSwapHands()) {
         shouldReleaseItem = false;
         startUseItemDefault(mc.player.getUsedItemHand());
      }
   }

   @EventTarget
   public void onMotion(EventMotion event) {
      if (event.getType() == EventType.PRE && isBlinking && blinkTicks >= blinkDuration && !didSwapHand) {
         stopBlink();
      }
   }

   @EventTarget
   public void onPacket(EventPacket event) {
      if (mc.player == null) return;

      if (event.isIncoming() && shouldQueuePacket(event.getPacket())) {
         event.setCancelled(true);
         return;
      }

      handleOffhandPacket(event);

      if (event.getPacket() instanceof ServerboundPlayerActionPacket actionPacket
         && actionPacket.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
         blinkTicks = Math.max(blinkTicks, 1);
      }

      if (event.getPacket() instanceof ServerboundUseItemOnPacket useOnPacket
         && didSwapHand
         && useOnPacket.getHand() == useHand
         && mc.player.getInventory().selected == swapInitSlot) {
         InteractionHand other = useOnPacket.getHand() == InteractionHand.MAIN_HAND
            ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
         PacketUtils.sendPacket(new ServerboundUseItemOnPacket(other, useOnPacket.getHitResult(), useOnPacket.getSequence()));
      }

      if (event.getPacket() instanceof ServerboundUseItemPacket usePacket) {
         if (didSwapHand || releaseTicksRemaining > 0) {
            event.setCancelled(true);
         } else if (mode.isCurrentMode("Grim") && bowNoSlow.getCurrentValue()) {
            if (System.currentTimeMillis() - timer < timerDelay.getCurrentValue() && releaseTicksRemaining <= 0) {
               event.setCancelled(true);
            } else if (!canSwapHands()) {
               shouldReleaseItem = true;
            } else {
               ItemStack handStack = mc.player.getItemInHand(usePacket.getHand());
               UseAnim anim = handStack.getUseAnimation();
               if ((anim == UseAnim.BOW && crossbowNoSlow.getCurrentValue())
                  || (anim == UseAnim.CROSSBOW && !CrossbowItem.isCharged(handStack) && foodNoSlow.getCurrentValue())) {
                  shouldReleaseItem = false;
                  startBlink(1);
               } else if (isEatOrDrink(handStack)) {
                  shouldReleaseItem = false;
                  event.setCancelled(true);
                  pendingUseHand = usePacket.getHand();
                  pendingUseCount = usePacket.getSequence();
               }
            }
         }
      }
   }

   private void startUseItem(InteractionHand hand, int count) {
      if (mc.player == null) return;
      didSwapHand = true;
      lastUseHand = hand;
      swapInitSlot = mc.player.getInventory().selected;
      useHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
      sendSwapOffhand();
      if (count > 0) {
         PacketUtils.sendPacket(new ServerboundUseItemPacket(useHand, count));
      } else {
         PacketUtils.sendSequencedPacket(useHand, ServerboundUseItemPacket::new);
      }
      startBlink(2);
   }

   private void startUseItemDefault(InteractionHand hand) {
      startUseItem(hand, 0);
   }

   private void finishBlink() {
      shouldReleaseItem = false;
      if (!isBlinking || !didSwapHand || mc.player == null) return;
      if (useHand != lastUseHand) {
         sendSwapOffhand();
      }
      didSwapHand = false;
      timer = System.currentTimeMillis();
      releaseTicksRemaining = (int) useItemTicks.getCurrentValue();
      releaseUseKey();
      PacketUtils.sendPacket(new ServerboundPlayerActionPacket(
         ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN));
   }

   private boolean canSwapHands() {
      if (mc.player == null) return false;
      ItemStack mainHand = mc.player.getMainHandItem();
      ItemStack offHand = mc.player.getOffhandItem();
      if (mainHand.isEmpty() || offHand.isEmpty()) return true;
      if (mainHand.getItem() == Items.ENCHANTED_GOLDEN_APPLE && offHand.getItem() == Items.GOLDEN_APPLE) return false;
      if (offHand.getItem() == Items.ENCHANTED_GOLDEN_APPLE && mainHand.getItem() == Items.GOLDEN_APPLE) return false;
      return mainHand.getItem() != offHand.getItem();
   }

   private boolean isFoodOrPotion(ItemStack stack) {
      Item item = stack.getItem();
      return stack.isEdible() || item instanceof PotionItem;
   }

   private boolean isEatOrDrink(ItemStack stack) {
      UseAnim anim = stack.getUseAnimation();
      return anim == UseAnim.EAT || anim == UseAnim.DRINK;
   }

   private void handleOffhandSlowdown(EventSlowdown event, ItemStack stack) {
      if (!isFoodOrPotion(stack) || mc.player.getUseItemRemainingTicks() <= 0) return;
      event.setSlowdown(false);
      if (keepSprinting.getCurrentValue()) {
         mc.player.setSprinting(true);
      }
   }

   private void startBlink(int duration) {
      isBlinking = true;
      blinkTicks = 0;
      blinkDuration = duration;
   }

   private void stopBlink() {
      isBlinking = false;
      blinkTicks = 0;
   }

   private void sendSwapOffhand() {
      if (mc.player != null) {
         mc.player.getInventory().selected = swapInitSlot;
      }
   }

   private void releaseUseKey() {
      mc.options.keyUse.setDown(true);
   }

   private void restoreUseKeyState() {
      mc.options.keyUse.setDown(false);
   }

   private boolean isIdleState() {
      return useHand == InteractionHand.MAIN_HAND && !didSwapHand && !shouldReleaseItem;
   }

   private boolean isUsingState() {
      return mc.player != null && mc.player.isUsingItem();
   }

   private boolean shouldQueuePacket(Packet<?> packet) {
      return packet instanceof ServerboundUseItemPacket
         || packet instanceof ServerboundUseItemOnPacket
         || packet instanceof ServerboundPlayerActionPacket;
   }

   private void handleOffhandPacket(EventPacket event) {
      // Handle offhand swap packets
   }
}