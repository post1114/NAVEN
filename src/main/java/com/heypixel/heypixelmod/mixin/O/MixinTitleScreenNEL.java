package com.heypixel.heypixelmod.mixin.O;

import com.heypixel.heypixelmod.obsoverlay.nel.NelManager;
import java.lang.reflect.Field;
import java.util.List;
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
   private static Field naven_renderables;
   @Unique
   private static Field naven_children;
   @Unique
   private static Field naven_narratables;

   static {
      try {
         naven_renderables = Screen.class.getDeclaredField("renderables");
         naven_renderables.setAccessible(true);
         naven_children = Screen.class.getDeclaredField("children");
         naven_children.setAccessible(true);
         naven_narratables = Screen.class.getDeclaredField("narratables");
         naven_narratables.setAccessible(true);
      } catch (NoSuchFieldException e) {
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
         ((List) naven_renderables.get(screen)).add(nelButton);
         ((List) naven_children.get(screen)).add(nelButton);
         ((List) naven_narratables.get(screen)).add(nelButton);
      } catch (IllegalAccessException e) {
         e.printStackTrace();
      }
   }
}
