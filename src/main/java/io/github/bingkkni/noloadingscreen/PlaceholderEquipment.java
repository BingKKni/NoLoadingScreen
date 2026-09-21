package io.github.bingkkni.noloadingscreen;

import java.util.EnumMap;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Reconcile only changed local equipment, preserving the outgoing server's other attributes/effects. */
public final class PlaceholderEquipment {
	private static final EnumMap<EquipmentSlot, ItemStack> previous = new EnumMap<>(EquipmentSlot.class);
	private PlaceholderEquipment() {}
	public static void clear() { previous.clear(); }
	public static void reset(LocalPlayer player) {
		clear();
		for (EquipmentSlot slot : EquipmentSlot.values()) previous.put(slot, player.getItemBySlot(slot).copy());
	}
	public static void update(LocalPlayer player) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack current = player.getItemBySlot(slot);
			ItemStack old = previous.get(slot);
			if (old == null || ItemStack.matches(old, current)) continue;
			// Vanilla's stack API includes enchantment attributes (e.g. Efficiency), components
			// and loader hooks. No ServerLevel enchantment callbacks or player tick is invoked.
			old.forEachModifier(slot, (attribute, modifier) -> {
				var instance = player.getAttribute(attribute);
				if (instance != null) instance.removeModifier(modifier.id());
			});
			current.forEachModifier(slot, (attribute, modifier) -> {
				var instance = player.getAttribute(attribute);
				if (instance != null) instance.addOrUpdateTransientModifier(modifier);
			});
			previous.put(slot, current.copy());
		}
	}
}
