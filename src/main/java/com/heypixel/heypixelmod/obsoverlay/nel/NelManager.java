package com.heypixel.heypixelmod.obsoverlay.nel;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.ui.notification.Notification;
import com.heypixel.heypixelmod.obsoverlay.ui.notification.NotificationLevel;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;

public class NelManager {
   public static final String SERVER_ID_BUJIDAO = "4661334467366178884";
   public static final ResourceLocation CHANNEL_HEYPIXEL = new ResourceLocation("heypixel", "main");
   public static final ResourceLocation CHANNEL_NEL = new ResourceLocation("naven", "nel");

   private static NelManager instance;
   private final Minecraft mc = Minecraft.getInstance();
   private final Map<ResourceLocation, Consumer<FriendlyByteBuf>> serverHandlers = new HashMap<>();
   private final Map<ResourceLocation, Consumer<FriendlyByteBuf>> clientHandlers = new HashMap<>();
   private boolean connectedToBujidao = false;
   private String currentServerId = "";
   private NelScreen nelScreen;

   public static NelManager getInstance() {
      if (instance == null) {
         instance = new NelManager();
      }
      return instance;
   }

   public void init() {
      this.registerDefaultHandlers();
   }

   private void registerDefaultHandlers() {
      this.registerServerHandler(CHANNEL_HEYPIXEL, this::handleHeypixelPacket);
      this.registerServerHandler(CHANNEL_NEL, this::handleNelPacket);
   }

   public void registerServerHandler(ResourceLocation channel, Consumer<FriendlyByteBuf> handler) {
      this.serverHandlers.put(channel, handler);
   }

   public void registerClientHandler(ResourceLocation channel, Consumer<FriendlyByteBuf> handler) {
      this.clientHandlers.put(channel, handler);
   }

   private void handleHeypixelPacket(FriendlyByteBuf data) {
      int packetId = data.readVarInt();
      System.out.println("[NEL] Heypixel packet received, ID: " + packetId);
   }

   private void handleNelPacket(FriendlyByteBuf data) {
      int packetId = data.readVarInt();
      System.out.println("[NEL] NEL packet received, ID: " + packetId);
   }

   public void handleServerPayload(ResourceLocation channel, FriendlyByteBuf data) {
      if (this.serverHandlers.containsKey(channel)) {
         try {
            this.serverHandlers.get(channel).accept(data);
         } catch (Exception e) {
            System.err.println("[NEL] Error handling packet on channel: " + channel);
            e.printStackTrace();
         }
      }
   }

   public void sendPluginMessage(ResourceLocation channel, Consumer<FriendlyByteBuf> writer) {
      if (mc.getConnection() == null) return;

      FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
      writer.accept(buf);

      Packet<?> packet = new ServerboundCustomPayloadPacket(channel, buf);
      mc.getConnection().send(packet);
   }

   public void sendNelPacket(int packetId, Consumer<FriendlyByteBuf> writer) {
      this.sendPluginMessage(CHANNEL_NEL, buf -> {
         buf.writeVarInt(packetId);
         writer.accept(buf);
      });
   }

   public boolean isConnectedToBujidao() {
      return this.connectedToBujidao;
   }

   public void setConnectedToBujidao(boolean connected) {
      this.connectedToBujidao = connected;
   }

   public String getCurrentServerId() {
      return this.currentServerId;
   }

   public void setCurrentServerId(String serverId) {
      this.currentServerId = serverId;
      this.connectedToBujidao = SERVER_ID_BUJIDAO.equals(serverId);
      if (this.connectedToBujidao) {
         Naven.getInstance().getNotificationManager().addNotification(
            new Notification(NotificationLevel.INFO, "NEL: Connected to 布吉岛", 3000L)
         );
      }
   }

   public void openNelScreen() {
      if (this.nelScreen == null) {
         this.nelScreen = new NelScreen();
      }
      mc.setScreen(this.nelScreen);
   }
}
