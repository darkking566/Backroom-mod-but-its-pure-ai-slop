package dev.noclip.backrooms.level4;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Level 4's ambient and interactive sounds; sounds.json maps each to vanilla audio. */
final class Level4Sounds {
	static final SoundEvent RAIN = register("level4.rain");
	static final SoundEvent HUM = register("level4.hum");
	static final SoundEvent WATER_FILL = register("level4.water_fill");
	static final SoundEvent WATER_DRINK = register("level4.water_drink");
	static final SoundEvent VENDING = register("level4.vending");
	static final SoundEvent WINDOW_KNOCK = register("level4.window_knock");
	static final SoundEvent PHOTOCOPIER = register("level4.photocopier");
	static final SoundEvent PHONE_RING = register("level4.phone_ring");
	static final SoundEvent VANISH = register("level4.vanish");
	static final SoundEvent ELEVATOR = register("level4.elevator");

	private Level4Sounds() {
	}

	static void register() {
		// Triggers static field initialization before registries freeze.
	}

	private static SoundEvent register(String path) {
		Identifier id = Level4.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
