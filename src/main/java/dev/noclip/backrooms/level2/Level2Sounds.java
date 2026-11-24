package dev.noclip.backrooms.level2;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Level 2's sounds; sounds.json points each at vanilla sounds, pitched down. */
final class Level2Sounds {
	static final SoundEvent MACHINE = register("level2.machine");
	static final SoundEvent STEAM = register("level2.steam");
	static final SoundEvent DRIP = register("level2.drip");
	static final SoundEvent PIPE_KNOCK = register("level2.pipe_knock");
	static final SoundEvent CIRCUIT_DOWN = register("level2.circuit_down");
	static final SoundEvent LOCKED = register("level2.locked");
	static final SoundEvent WINDOW = register("level2.window");
	static final SoundEvent HUM = register("level2.hum");

	private Level2Sounds() {
	}

	static void register() {
		// Triggers static field initialization before registries freeze.
	}

	private static SoundEvent register(String path) {
		Identifier id = Level2.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
