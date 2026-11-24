package dev.noclip.backrooms.level0p1;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Zenith Station's sounds; sounds.json points each at vanilla sounds, pitched to suit. */
final class ZenithSounds {
	static final SoundEvent HUM = register("level0p1.hum");
	static final SoundEvent ALARM = register("level0p1.alarm");
	static final SoundEvent CHIME = register("level0p1.chime");
	static final SoundEvent CREAK = register("level0p1.creak");
	static final SoundEvent SPARK = register("level0p1.spark");
	static final SoundEvent DOOR = register("level0p1.door");

	private ZenithSounds() {
	}

	static void register() {
		// Triggers static field initialization before registries freeze.
	}

	private static SoundEvent register(String path) {
		Identifier id = Level0p1.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
