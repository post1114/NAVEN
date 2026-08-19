package com.heypixel.heypixelmod.obsoverlay.ui;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.utils.RenderUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.VerifyManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class LoginScreen extends Screen {
   private static final Minecraft mc = Minecraft.getInstance();

   private static final int BG_COLOR = 0xFF0D1117;
   private static final int CARD_COLOR = 0xFF161B22;
   private static final int CARD_BORDER = 0xFF30363D;
   private static final int ACCENT = 0xFF58A6FF;
   private static final int ACCENT_HOVER = 0xFF79B8FF;
   private static final int TEXT_PRIMARY = 0xFFE6EDF3;
   private static final int TEXT_SECONDARY = 0xFF8B949E;
   private static final int INPUT_BG = 0xFF0D1117;
   private static final int INPUT_BORDER = 0xFF30363D;
   private static final int INPUT_BORDER_FOCUS = 0xFF58A6FF;
   private static final int SUCCESS = 0xFF3FB950;
   private static final int ERROR = 0xFFF85149;

   private static final int CARD_WIDTH = 320;
   private static final int CARD_HEIGHT = 300;
   private static final int CARD_RADIUS = 12;
   private static final int INPUT_HEIGHT = 36;
   private static final int INPUT_RADIUS = 6;
   private static final int BTN_HEIGHT = 38;
   private static final int BTN_RADIUS = 6;

   private EditBox usernameInput;
   private EditBox passwordInput;
   private String statusMessage = "";
   private int statusColor = TEXT_SECONDARY;
   private boolean verifying = false;
   private int animTick = 0;
   private float fadeAlpha = 0.0F;
   private boolean showSuccess = false;
   private int successTimer = 0;

   public LoginScreen() {
      super(Component.nullToEmpty("Naven Login"));
   }

   protected void init() {
      int cardX = (this.width - CARD_WIDTH) / 2;
      int cardY = (this.height - CARD_HEIGHT) / 2;
      int inputX = cardX + 30;
      int inputWidth = CARD_WIDTH - 60;

      this.usernameInput = new EditBox(
         this.font, inputX, cardY + 95, inputWidth, INPUT_HEIGHT,
         Component.nullToEmpty("Username")
      );
      this.usernameInput.setMaxLength(64);
      this.usernameInput.setHint(Component.nullToEmpty("Username"));

      this.passwordInput = new EditBox(
         this.font, inputX, cardY + 145, inputWidth, INPUT_HEIGHT,
         Component.nullToEmpty("Password")
      );
      this.passwordInput.setMaxLength(128);
      this.passwordInput.setHint(Component.nullToEmpty("Password"));

      this.addWidget(this.usernameInput);
      this.addWidget(this.passwordInput);
      this.setInitialFocus(this.usernameInput);
   }

   public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      PoseStack stack = g.pose();
      this.animTick++;
      this.fadeAlpha = Math.min(1.0F, this.fadeAlpha + 0.05F);

      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();

      g.fill(0, 0, this.width, this.height, BG_COLOR);

      int cardX = (this.width - CARD_WIDTH) / 2;
      int cardY = (this.height - CARD_HEIGHT) / 2;

      RenderUtils.drawRoundedRect(stack, cardX, cardY, CARD_WIDTH, CARD_HEIGHT, CARD_RADIUS, CARD_COLOR);
      drawBorder(stack, cardX, cardY, CARD_WIDTH, CARD_HEIGHT, CARD_RADIUS, CARD_BORDER);

      RenderUtils.drawRoundedRect(stack, cardX + CARD_WIDTH / 2 - 20, cardY + 25, 40, 4, 2, ACCENT);

      String title = "Naven";
      int titleWidth = this.font.width(title);
      this.font.draw(stack, title,
         (float)(this.width / 2 - titleWidth / 2),
         (float)(cardY + 42), TEXT_PRIMARY);

      String subtitle = "Sign in to continue";
      int subtitleWidth = this.font.width(subtitle);
      this.font.draw(stack, subtitle,
         (float)(this.width / 2 - subtitleWidth / 2),
         (float)(cardY + 58), TEXT_SECONDARY);

      int inputX = cardX + 30;
      int inputWidth = CARD_WIDTH - 60;

      this.font.draw(stack, "Username", (float)inputX, (float)(cardY + 82), TEXT_SECONDARY);
      if (this.usernameInput != null) {
         boolean focusU = this.usernameInput.isFocused();
         RenderUtils.drawRoundedRect(stack, inputX, cardY + 95, inputWidth, INPUT_HEIGHT, INPUT_RADIUS, INPUT_BG);
         drawBorder(stack, inputX, cardY + 95, inputWidth, INPUT_HEIGHT, INPUT_RADIUS,
            focusU ? INPUT_BORDER_FOCUS : INPUT_BORDER);
         this.usernameInput.render(g, mouseX, mouseY, partialTick);
      }

      this.font.draw(stack, "Password", (float)inputX, (float)(cardY + 132), TEXT_SECONDARY);
      if (this.passwordInput != null) {
         boolean focusP = this.passwordInput.isFocused();
         RenderUtils.drawRoundedRect(stack, inputX, cardY + 145, inputWidth, INPUT_HEIGHT, INPUT_RADIUS, INPUT_BG);
         drawBorder(stack, inputX, cardY + 145, inputWidth, INPUT_HEIGHT, INPUT_RADIUS,
            focusP ? INPUT_BORDER_FOCUS : INPUT_BORDER);
         this.passwordInput.render(g, mouseX, mouseY, partialTick);
      }

      int btnX = cardX + 30;
      int btnY = cardY + 200;
      int btnWidth = CARD_WIDTH - 60;
      boolean hoveringBtn = mouseX >= btnX && mouseX <= btnX + btnWidth
         && mouseY >= btnY && mouseY <= btnY + BTN_HEIGHT;

      int btnBg;
      if (this.showSuccess) {
         btnBg = SUCCESS;
      } else if (this.verifying) {
         btnBg = 0xFF21262D;
      } else {
         btnBg = hoveringBtn ? ACCENT_HOVER : ACCENT;
      }

      RenderUtils.drawRoundedRect(stack, btnX, btnY, btnWidth, BTN_HEIGHT, BTN_RADIUS, btnBg);

      String btnText = this.showSuccess ? "Welcome!" : (this.verifying ? "Signing in..." : "Sign In");
      int btnTextWidth = this.font.width(btnText);
      this.font.draw(stack, btnText,
         (float)(this.width / 2 - btnTextWidth / 2),
         (float)(btnY + 12), TEXT_PRIMARY);

      if (!this.statusMessage.isEmpty()) {
         int statusWidth = this.font.width(this.statusMessage);
         this.font.draw(stack, this.statusMessage,
            (float)(this.width / 2 - statusWidth / 2),
            (float)(cardY + 250), this.statusColor);
      }

      String version = "Naven v" + Naven.CLIENT_NAME;
      int versionWidth = this.font.width(version);
      this.font.draw(stack, version,
         (float)(this.width / 2 - versionWidth / 2),
         (float)(cardY + CARD_HEIGHT + 10), 0xFF484F58);

      if (this.showSuccess) {
         this.successTimer++;
         if (this.successTimer > 40) {
            Minecraft.getInstance().setScreen(null);
         }
      }

      RenderSystem.disableBlend();
   }

   private void drawBorder(PoseStack stack, float x, float y, float w, float h, float r, int color) {
      float a = (float)(color >> 24 & 0xFF) / 255.0F;
      if (a <= 0.0F) return;
      float red = (float)(color >> 16 & 0xFF) / 255.0F;
      float green = (float)(color >> 8 & 0xFF) / 255.0F;
      float blue = (float)(color & 0xFF) / 255.0F;
      RenderSystem.setShaderColor(red, green, blue, a);
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      net.minecraft.client.renderer.GameRenderer.getPositionShader();
      com.mojang.blaze3d.vertex.Tesselator tesselator = com.mojang.blaze3d.vertex.Tesselator.getInstance();
      com.mojang.blaze3d.vertex.BufferBuilder bb = tesselator.getBuilder();
      bb.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.DEBUG_STRIP, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION);
      float segments = 16;
      for (int i = 0; i <= segments; i++) {
         float angle = (float)i / segments * 6.2831855F;
         bb.vertex(stack.last().pose(), x + r + (float)Math.cos(angle) * r, y + r + (float)Math.sin(angle) * r, 0).endVertex();
      }
      for (int i = 0; i <= segments; i++) {
         float angle = (float)i / segments * 6.2831855F;
         bb.vertex(stack.last().pose(), x + w - r + (float)Math.cos(angle) * r, y + r + (float)Math.sin(angle) * r, 0).endVertex();
      }
      for (int i = 0; i <= segments; i++) {
         float angle = (float)i / segments * 6.2831855F;
         bb.vertex(stack.last().pose(), x + r + (float)Math.cos(angle) * r, y + h - r + (float)Math.sin(angle) * r, 0).endVertex();
      }
      for (int i = 0; i <= segments; i++) {
         float angle = (float)i / segments * 6.2831855F;
         bb.vertex(stack.last().pose(), x + w - r + (float)Math.cos(angle) * r, y + h - r + (float)Math.sin(angle) * r, 0).endVertex();
      }
      tesselator.end();
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == 0) {
         int cardX = (this.width - CARD_WIDTH) / 2;
         int cardY = (this.height - CARD_HEIGHT) / 2;
         int btnX = cardX + 30;
         int btnY = cardY + 200;
         int btnWidth = CARD_WIDTH - 60;

         if (mouseX >= btnX && mouseX <= btnX + btnWidth
            && mouseY >= btnY && mouseY <= btnY + BTN_HEIGHT) {
            this.doVerify();
            return true;
         }
      }
      return super.mouseClicked(mouseX, mouseY, button);
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode == 257 || keyCode == 335) {
         this.doVerify();
         return true;
      }
      return super.keyPressed(keyCode, scanCode, modifiers);
   }

   private void doVerify() {
      if (this.verifying || this.showSuccess) return;

      String username = this.usernameInput != null ? this.usernameInput.getValue().trim() : "";
      String password = this.passwordInput != null ? this.passwordInput.getValue().trim() : "";

      if (username.isEmpty()) {
         this.statusMessage = "Please enter username";
         this.statusColor = ERROR;
         return;
      }
      if (password.isEmpty()) {
         this.statusMessage = "Please enter password";
         this.statusColor = ERROR;
         return;
      }

      this.verifying = true;
      this.statusMessage = "Verifying...";
      this.statusColor = TEXT_SECONDARY;

      new Thread(() -> {
         VerifyManager.verify(username, password);
         this.statusMessage = Naven.verifyStatus;
         this.statusColor = Naven.verified ? SUCCESS : ERROR;
         this.verifying = false;
         if (Naven.verified) {
            this.showSuccess = true;
         }
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
