package com.heypixel.heypixelmod.obsoverlay.events.impl;

import com.heypixel.heypixelmod.obsoverlay.events.api.events.Event;
import net.minecraft.world.entity.Entity;

public class EventAttack implements Event {
   private final Entity target;

   public Entity getTarget() {
      return this.target;
   }

   public EventAttack(Entity target) {
      this.target = target;
   }
}
