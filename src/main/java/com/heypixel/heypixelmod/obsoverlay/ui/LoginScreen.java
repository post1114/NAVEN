package com.heypixel.heypixelmod.obsoverlay.ui;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.utils.ChatUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.VerifyManager;
import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class LoginScreen extends Screen {
   private static final Minecraft mc = Minecraft.getInstance();
   private EditBox tokenInput;
   private String statusMessage = "";
   private boolean verifying = false;
   private int animTick = 0;

   public LoginScreen() {
      super(Component.nullToEmpty("Naven Login"));
   }

   protected void init() {
      this.tokenInput = new EditBox(
         this.font,
         this.width / 2 - 100,
         this.height / 2 + 10,
         200,
         20,
         Component.nullToEmpty("Token")
      );
      this.tokenInput.setMaxLength(256);
      this.tokenInput.setHint(Component.nullToEmpty("Enter your token"));
      this.addWidget(this.tokenInput);
      this.setInitialFocus(this.tokenInput);
   }

   public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      PoseStack stack = g.pose();
      this.renderBackground(g, mouseX, mouseY, partialTick);
      this.animTick++;

      int centerX = this.width / 2;
      int centerY = this.height / 2;

      // Title
      String title = "Naven";
      float titleScale = 1.5F;
      int titleWidth = (int)(this.font.width(title) * titleScale);
      this.font.draw(stack, title, (float)(centerX - titleWidth / 2), (float)(centerY - 60), Color.WHITE.getRGB());

      // Subtitle
      String subtitle = "Verification Required";
      int subtitleWidth = this.font.width(subtitle);
      this.font.draw(stack, subtitle, (float)(centerX - subtitleWidth / 2), (float)(centerY - 35), Color.GRAY.getRGB());

      // Input box
      if (this.tokenInput != null) {
         this.tokenInput.render(g, mouseX, mouseY, partialTick);
      }

      // Verify button
      String btnText = this.verifying ? "Verifying..." : "Verify";
      int btnWidth = 80;
      int btnHeight = 20;
      int btnX = centerX - btnWidth / 2;
      int btnY = centerY + 40;
      boolean hoveringBtn = mouseX >= btnX && mouseX <= btnX + btnWidth && mouseY >= btnY && mouseY <= btnY + btnHeight;
      int btnColor = hoveringBtn ? 0xFF3662EC : 0xFF191919;
      g.fill(btnX, btnY, btnX + btnWidth, btnY + btnHeight, btnColor);
      int btnTextWidth = this.font.width(btnText);
      this.font.draw(stack, btnText, (float)(centerX - btnTextWidth / 2), (float)(btnY + 6), Color.WHITE.getRGB());

      // Status message
      if (!this.statusMessage.isEmpty()) {
         int statusWidth = this.font.width(this.statusMessage);
         int statusColor = Naven.verified ? 0xFF00CC00 : 0xFFFF3333;
         this.font.draw(stack, this.statusMessage, (float)(centerX - statusWidth / 2), (float)(centerY + 70), statusColor);
      }

      // If verified, show success and auto-close
      if (Naven.verified) {
         String successMsg = "Access Granted";
         int successWidth = this.font.width(successMsg);
         this.font.draw(stack, successMsg, (float)(centerX - successWidth / 2), (float)(centerY + 85), 0xFF00CC00);

         if (this.animTick % 20 == 0) {
            Minecraft.getInstance().setScreen(null);
         }
      }
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == 0) {
         int centerX = this.width / 2;
         int centerY = this.height / 2;
         int btnWidth = 80;
         int btnHeight = 20;
         int btnX = centerX - btnWidth / 2;
         int btnY = centerY + 40;

         if (mouseX >= btnX && mouseX <= btnX + btnWidth && mouseY >= btnY && mouseY <= btnY + btnHeight) {
            this.doVerify();
            return true;
         }
      }
      return super.mouseClicked(mouseX, mouseY, button);
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode == 257 || keyCode == 335) { // Enter or Numpad Enter
         this.doVerify();
         return true;
      }
      return super.keyPressed(keyCode, scanCode, modifiers);
   }

   private void doVerify() {
      if (this.verifying || Naven.verified) return;

      String token = this.tokenInput != null ? this.tokenInput.getValue().trim() : "";
      if (token.isEmpty()) {
         this.statusMessage = "Please enter a token";
         return;
      }

      this.verifying = true;
      this.statusMessage = "Verifying...";

      new Thread(() -> {
         VerifyManager.verify(token);
         this.statusMessage = Naven.verifyStatus;
         this.verifying = false;
      }, "Naven-Verify").start();
   }

   public boolean shouldPause() {
      return true;
   }

   public void onClose() {
      if (!Naven.verified) {
         Minecraft.getInstance().stop(Minecraft.getInstance().isLocalServer());
      }
   }
}
