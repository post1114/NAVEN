package com.heypixel.heypixelmod.obsoverlay.modules.impl.misc;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventPacket;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.ui.notification.Notification;
import com.heypixel.heypixelmod.obsoverlay.ui.notification.NotificationLevel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPingPacket;

@ModuleInfo(
   name = "AntiCheatDetect",
   description = "Attempts to detect the anti-cheat used by the server",
   category = Category.MISC
)
public class AntiCheatDetect extends Module {
   private final List<Integer> transactions = new ArrayList<>();
   private boolean capturing = false;

   @EventTarget
   public void onPacket(EventPacket event) {
      if (mc.player == null || mc.getConnection() == null) return;
      if (event.getType() != EventType.RECEIVE) return;

      Packet<?> packet = event.getPacket();

      if (packet instanceof ClientboundLoginPacket) {
         this.transactions.clear();
         this.capturing = true;
         return;
      }

      if (packet instanceof ClientboundPingPacket pingPacket && this.capturing) {
         this.transactions.add(pingPacket.getId());
         if (this.transactions.size() >= 5) {
            this.capturing = false;
            this.detectAntiCheat();
         }
      }
   }

   private void detectAntiCheat() {
      String serverAddress = "";
      if (mc.getCurrentServer() != null) {
         serverAddress = mc.getCurrentServer().ip != null ? mc.getCurrentServer().ip : "";
      }

      String result = guessAntiCheat(serverAddress, this.transactions);
      if (result != null) {
         Naven.getInstance().getNotificationManager().addNotification(
            new Notification(NotificationLevel.INFO, "AntiCheat Detect: " + result, 5000L)
         );
      }
   }

   public static String guessAntiCheat(String address, List<Integer> txns) {
      if (txns.size() < 5) return null;

      if (address.toLowerCase().endsWith("hypixel.net")) {
         return "Watchdog";
      }

      List<Integer> diffs = new ArrayList<>();
      for (int i = 1; i < txns.size(); i++) {
         diffs.add(txns.get(i) - txns.get(i - 1));
      }

      int first = txns.get(0);

      boolean allSameDiff = true;
      int commonDiff = diffs.get(0);
      for (int d : diffs) {
         if (d != commonDiff) {
            allSameDiff = false;
            break;
         }
      }

      if (allSameDiff) {
         if (commonDiff == 1) {
            if (first >= -23772 && first <= -23762) return "Vulcan";
            if ((first >= 95 && first <= 105) || (first >= -20005 && first <= -19995)) return "Matrix";
            if (first >= -32773 && first <= -32762) return "Grizzly";
            return "Verus";
         }
         if (commonDiff == -1) {
            if (first >= -8287 && first <= -8280) return "Errata";
            if (first < -3000) return "Intave";
            if (first >= -5 && first <= 0) return "Grim";
            if (first >= -3000 && first <= -2995) return "Karhu";
            return "Polar";
         }
      }

      if (txns.size() >= 3 && txns.get(0) == txns.get(1)) {
         boolean restIncByOne = true;
         for (int i = 3; i < txns.size(); i++) {
            if (txns.get(i) - txns.get(i - 1) != 1) {
               restIncByOne = false;
               break;
            }
         }
         if (restIncByOne) return "Verus";
      }

      if (diffs.size() >= 3 && diffs.get(0) >= 100 && diffs.get(1) == -1) {
         boolean restMinusOne = true;
         for (int i = 2; i < diffs.size(); i++) {
            if (diffs.get(i) != -1) {
               restMinusOne = false;
               break;
            }
         }
         if (restMinusOne) return "Polar";
      }

      if (first < -3000 && txns.contains(0)) {
         return "Intave";
      }

      if (txns.size() >= 4
         && txns.get(0) == -30767
         && txns.get(1) == -30766
         && txns.get(2) == -25767) {
         boolean restIncByOne = true;
         for (int i = 4; i < txns.size(); i++) {
            if (txns.get(i) - txns.get(i - 1) != 1) {
               restIncByOne = false;
               break;
            }
         }
         if (restIncByOne) return "Old Vulcan";
      }

      return "Unknown";
   }

   @Override
   public void onDisable() {
      this.transactions.clear();
      this.capturing = false;
   }
}
