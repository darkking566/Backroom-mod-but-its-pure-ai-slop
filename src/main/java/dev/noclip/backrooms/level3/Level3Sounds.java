package dev.noclip.backrooms.level3;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Level 3's sounds; sounds.json points each at vanilla sounds. */
final class Level3Sounds {
	static final SoundEvent SPARK = register("level3.spark");
	static final SoundEvent SHOCK = register("level3.shock");
	static final SoundEvent HUM = register("level3.hum");
	static final SoundEvent OVERLOAD = register("level3.overload");
	static final SoundEvent SLUDGE = register("level3.sludge");
	static final SoundEvent LOCKPICK = register("level3.lockpick");
	static final SoundEvent GATE_OPEN = register("level3.gate_open");
	static final SoundEvent SHATTER = register("level3.shatter");
	static final SoundEvent PARANOIA = register("level3.paranoia");
	static final SoundEvent ELEVATOR = register("level3.elevator");
	static final SoundEvent CRACKLE = register("level3.crackle");

	private Level3Sounds() {
	}

	static void register() {
		// Triggers static field initialization before registries freeze.
	}

	private static SoundEvent register(String path) {
		Identifier id = Level3.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
