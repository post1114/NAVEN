package com.heypixel.heypixelmod.obsoverlay.nel;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class NelScreen extends Screen {
   private final NelManager nelManager = NelManager.getInstance();
   private String statusMessage = "";
   private int statusColor = Color.WHITE.getRGB();

   public NelScreen() {
      super(Component.literal("NEL - Network Extension Library"));
   }

   @Override
   protected void init() {
      super.init();

      int centerX = this.width / 2;
      int startY = this.height / 4 + 40;

      this.addRenderableWidget(Button.builder(
         Component.literal("Connect to 布吉岛"),
         button -> this.connectToBujidao()
      ).bounds(centerX - 100, startY, 200, 20).build());

      this.addRenderableWidget(Button.builder(
         Component.literal("Check Server"),
         button -> this.checkServer()
      ).bounds(centerX - 100, startY + 30, 200, 20).build());

      this.addRenderableWidget(Button.builder(
         Component.literal("Back"),
         button -> this.minecraft.setScreen(null)
      ).bounds(centerX - 100, startY + 90, 200, 20).build());

      if (this.nelManager.isConnectedToBujidao()) {
         this.statusMessage = "Connected to 布吉岛";
         this.statusColor = new Color(0, 200, 0).getRGB();
      } else {
         this.statusMessage = "Not connected";
         this.statusColor = new Color(200, 200, 0).getRGB();
      }
   }

   private void connectToBujidao() {
      this.statusMessage = "Connecting to 布吉岛...";
      this.statusColor = Color.WHITE.getRGB();

      if (this.minecraft.getCurrentServer() != null) {
         String serverIp = this.minecraft.getCurrentServer().ip;
         if (serverIp != null && (serverIp.contains("bujidao") || serverIp.contains("netease"))) {
            this.statusMessage = "Detected server, connecting...";
            this.statusColor = new Color(0, 200, 0).getRGB();
         } else {
            this.statusMessage = "Please join 布吉岛 server first";
            this.statusColor = new Color(200, 0, 0).getRGB();
         }
      } else {
         this.statusMessage = "Not connected to any server";
         this.statusColor = new Color(200, 0, 0).getRGB();
      }
   }

   private void checkServer() {
      if (this.nelManager.isConnectedToBujidao()) {
         this.statusMessage = "Server: 布吉岛 (ID: " + NelManager.SERVER_ID_BUJIDAO + ")";
         this.statusColor = new Color(0, 200, 0).getRGB();
      } else if (this.minecraft.getCurrentServer() != null) {
         this.statusMessage = "Connected to: " + this.minecraft.getCurrentServer().ip;
         this.statusColor = new Color(200, 200, 0).getRGB();
      } else {
         this.statusMessage = "Not connected to any server";
         this.statusColor = new Color(200, 0, 0).getRGB();
      }
   }

   @Override
   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      this.renderBackground(guiGraphics);

      PoseStack poseStack = guiGraphics.pose();
      poseStack.pushPose();
      poseStack.translate(0, 0, 0);

      guiGraphics.drawCenteredString(
         this.font,
         "NEL - Network Extension Library",
         this.width / 2,
         this.height / 4 - 10,
         Color.WHITE.getRGB()
      );

      guiGraphics.drawCenteredString(
         this.font,
         "Server: 布吉岛",
         this.width / 2,
         this.height / 4 + 10,
         new Color(100, 200, 255).getRGB()
      );

      guiGraphics.drawCenteredString(
         this.font,
         this.statusMessage,
         this.width / 2,
         this.height / 4 + 65,
         this.statusColor
      );

      poseStack.popPose();

      super.render(guiGraphics, mouseX, mouseY, partialTick);
   }

   @Override
   public boolean shouldCloseOnEsc() {
      return true;
   }
}
