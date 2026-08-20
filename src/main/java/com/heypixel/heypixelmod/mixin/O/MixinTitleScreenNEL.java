package com.heypixel.heypixelmod.mixin.O;

import com.heypixel.heypixelmod.obsoverlay.nel.NelManager;
import java.lang.reflect.Method;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({TitleScreen.class})
public class MixinTitleScreenNEL {

   @Unique
   private static Method naven_addRenderableWidget;

   static {
      try {
         naven_addRenderableWidget = Screen.class.getDeclaredMethod("addRenderableWidget", net.minecraft.client.gui.components.GuiEventListener.class);
         naven_addRenderableWidget.setAccessible(true);
      } catch (NoSuchMethodException e) {
         e.printStackTrace();
      }
   }

   @Inject(
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void onInit(CallbackInfo ci) {
      Screen screen = (Screen) (Object) this;
      int centerX = screen.width / 2;
      int rightX = centerX + 105;

      Button nelButton = Button.builder(
         net.minecraft.network.chat.Component.literal("NEL"),
         button -> NelManager.getInstance().openNelScreen()
      ).bounds(rightX, screen.height / 4 + 48 + 72, 98, 20).build();

      try {
         if (naven_addRenderableWidget != null) {
            naven_addRenderableWidget.invoke(screen, nelButton);
         }
      } catch (Exception e) {
         screen.addWidget(nelButton);
      }
   }
}
