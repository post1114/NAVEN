package com.heypixel.heypixelmod.mixin.O;

import com.heypixel.heypixelmod.obsoverlay.nel.NelManager;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({TitleScreen.class})
public class MixinTitleScreenNEL {

   @Inject(
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void onInit(CallbackInfo ci) {
      TitleScreen screen = (TitleScreen) (Object) this;
      int centerX = screen.width / 2;
      int rightX = centerX + 105;

      screen.addRenderableWidget(Button.builder(
         net.minecraft.network.chat.Component.literal("NEL"),
         button -> NelManager.getInstance().openNelScreen()
      ).bounds(rightX, screen.height / 4 + 48 + 72, 98, 20).build());
   }
}
