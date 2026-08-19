package com.heypixel.heypixelmod.obsoverlay.ui;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.utils.RenderUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.VerifyManager;
import com.mojang.blaze3d.vertex.PoseStack;
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
   private static final int WARNING = 0xFFD29922;

   private static final int CARD_WIDTH = 320;
   private static final int CARD_HEIGHT = 300;
   private static final int INPUT_HEIGHT = 36;
   private static final int BTN_HEIGHT = 38;

   private enum Stage { CHECKING, IDENTITY_OK, LOGIN, ERROR }
   private Stage stage = Stage.CHECKING;
   private String statusMessage = "Connecting to server...";
   private int statusColor = TEXT_SECONDARY;

   private EditBox usernameInput;
   private EditBox passwordInput;
   private boolean verifying = false;
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
      this.usernameInput.setVisible(false);
      this.passwordInput.setVisible(false);

      new Thread(this::checkIdentity, "Naven-Identity").start();
   }

   private void checkIdentity() {
      String identity = VerifyManager.fetchServerIdentity();
      if (VerifyManager.verifyIdentity(identity)) {
         this.stage = Stage.IDENTITY_OK;
         this.statusMessage = "Identity verified. Please sign in.";
         this.statusColor = SUCCESS;
         this.usernameInput.setVisible(true);
         this.passwordInput.setVisible(true);
         this.setInitialFocus(this.usernameInput);
      } else {
         this.stage = Stage.ERROR;
         this.statusMessage = "Identity verification failed!";
         this.statusColor = ERROR;
      }
   }

   public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      PoseStack stack = g.pose();

      g.fill(0, 0, this.width, this.height, BG_COLOR);

      int cardX = (this.width - CARD_WIDTH) / 2;
      int cardY = (this.height - CARD_HEIGHT) / 2;
      int inputX = cardX + 30;
      int inputWidth = CARD_WIDTH - 60;

      RenderUtils.drawRoundedRect(stack, cardX, cardY, CARD_WIDTH, CARD_HEIGHT, 12, CARD_BORDER);
      RenderUtils.drawRoundedRect(stack, cardX + 1, cardY + 1, CARD_WIDTH - 2, CARD_HEIGHT - 2, 11, CARD_COLOR);

      RenderUtils.drawRoundedRect(stack, cardX + CARD_WIDTH / 2 - 20, cardY + 25, 40, 4, 2, ACCENT);

      g.drawString(this.font, "Naven",
         this.width / 2 - this.font.width("Naven") / 2,
         cardY + 42, TEXT_PRIMARY, false);

      if (this.stage == Stage.CHECKING) {
         String dots = ".".repeat((int)(System.currentTimeMillis() / 500) % 4);
         g.drawString(this.font, "Connecting" + dots,
            this.width / 2 - this.font.width("Connecting" + dots) / 2,
            cardY + 80, TEXT_SECONDARY, false);
      } else if (this.stage == Stage.ERROR) {
         g.drawString(this.font, "Access Denied",
            this.width / 2 - this.font.width("Access Denied") / 2,
            cardY + 65, ERROR, false);

         int iconY = cardY + 100;
         RenderUtils.drawRoundedRect(stack, this.width / 2 - 24, iconY, 48, 48, 24, 0x20F85149);
         g.drawString(this.font, "!",
            this.width / 2 - this.font.width("!") / 2,
            iconY + 14, ERROR, false);
      } else if (this.stage == Stage.LOGIN || this.stage == Stage.IDENTITY_OK) {
         g.drawString(this.font, "Sign in to continue",
            this.width / 2 - this.font.width("Sign in to continue") / 2,
            cardY + 58, TEXT_SECONDARY, false);

         g.drawString(this.font, "Username", inputX, cardY + 82, TEXT_SECONDARY, false);
         if (this.usernameInput != null && this.usernameInput.isVisible()) {
            RenderUtils.drawRoundedRect(stack, inputX, cardY + 95, inputWidth, INPUT_HEIGHT, 6, INPUT_BG);
            int border = this.usernameInput.isFocused() ? INPUT_BORDER_FOCUS : INPUT_BORDER;
            g.fill(inputX, cardY + 95, inputX + inputWidth, cardY + 96, border);
            g.fill(inputX, cardY + 95 + INPUT_HEIGHT - 1, inputX + inputWidth, cardY + 95 + INPUT_HEIGHT, border);
            g.fill(inputX, cardY + 95, inputX + 1, cardY + 95 + INPUT_HEIGHT, border);
            g.fill(inputX + inputWidth - 1, cardY + 95, inputX + inputWidth, cardY + 95 + INPUT_HEIGHT, border);
            this.usernameInput.render(g, mouseX, mouseY, partialTick);
         }

         g.drawString(this.font, "Password", inputX, cardY + 132, TEXT_SECONDARY, false);
         if (this.passwordInput != null && this.passwordInput.isVisible()) {
            RenderUtils.drawRoundedRect(stack, inputX, cardY + 145, inputWidth, INPUT_HEIGHT, 6, INPUT_BG);
            int border = this.passwordInput.isFocused() ? INPUT_BORDER_FOCUS : INPUT_BORDER;
            g.fill(inputX, cardY + 145, inputX + inputWidth, cardY + 146, border);
            g.fill(inputX, cardY + 145 + INPUT_HEIGHT - 1, inputX + inputWidth, cardY + 145 + INPUT_HEIGHT, border);
            g.fill(inputX, cardY + 145, inputX + 1, cardY + 145 + INPUT_HEIGHT, border);
            g.fill(inputX + inputWidth - 1, cardY + 145, inputX + inputWidth, cardY + 145 + INPUT_HEIGHT, border);
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

         RenderUtils.drawRoundedRect(stack, btnX, btnY, btnWidth, BTN_HEIGHT, 6, btnBg);

         String btnText = this.showSuccess ? "Welcome!" : (this.verifying ? "Signing in..." : "Sign In");
         g.drawString(this.font, btnText,
            this.width / 2 - this.font.width(btnText) / 2,
            btnY + 12, TEXT_PRIMARY, false);
      }

      if (!this.statusMessage.isEmpty()) {
         g.drawString(this.font, this.statusMessage,
            this.width / 2 - this.font.width(this.statusMessage) / 2,
            cardY + 250, this.statusColor, false);
      }

      String version = "Naven v" + Naven.CLIENT_NAME;
      g.drawString(this.font, version,
         this.width / 2 - this.font.width(version) / 2,
         cardY + CARD_HEIGHT + 10, 0xFF484F58, false);

      if (this.showSuccess) {
         this.successTimer++;
         if (this.successTimer > 40) {
            Minecraft.getInstance().setScreen(null);
         }
      }
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
      if (this.verifying || this.showSuccess || this.stage != Stage.IDENTITY_OK) return;

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
      this.statusMessage = "Encrypting credentials...";
      this.statusColor = TEXT_SECONDARY;

      new Thread(() -> {
         VerifyManager.verifyCredentials(username, password);
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
         Minecraft.getInstance().stop();
      }
   }
}
