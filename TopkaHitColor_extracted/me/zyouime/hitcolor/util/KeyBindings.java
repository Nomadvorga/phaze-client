package me.zyouime.hitcolor.util;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.class_304;
import net.minecraft.class_3675.class_307;

public class KeyBindings {
   public static class_304 openGui;

   public static void register() {
      openGui = KeyBindingHelper.registerKeyBinding(new class_304("Open Gui", class_307.field_1668, 344, "TopkaHitColor"));
   }
}
