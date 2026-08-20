package com.heypixel.heypixelmod.mixin.O;

import com.heypixel.heypixelmod.obsoverlay.nel.NelManager;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({TitleScreen.class})
public abstract class MixinTitleScreenNEL extends Screen {

    protected MixinTitleScreenNEL() {
        super(null);
    }

    @Shadow
    protected abstract Button addRenderableWidget(Button $$0);

    @Inject(
       method = {"init"},
       at = {@At("TAIL")}
    )
    private void onInit(CallbackInfo ci) {
       int centerX = this.width / 2;
       int rightX = centerX + 105;

       Button nelButton = Button.builder(
          net.minecraft.network.chat.Component.literal("NEL"),
          button -> NelManager.getInstance().openNelScreen()
       ).bounds(rightX, this.height / 4 + 48 + 72, 98, 20).build();

       this.addRenderableWidget(nelButton);
    }
}
