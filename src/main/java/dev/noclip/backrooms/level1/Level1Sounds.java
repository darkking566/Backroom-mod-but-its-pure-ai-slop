package dev.noclip.backrooms.level1;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Level 1 sound events. sounds.json points each one at existing sounds, with its own subtitle. */
final class Level1Sounds {
	static final SoundEvent LAMP_HUM = register("level1.lamp_hum");
	static final SoundEvent PIPE_CLANG = register("level1.pipe_clang");
	static final SoundEvent PIPE_DRIP = register("level1.pipe_drip");
	static final SoundEvent RUMBLE = register("level1.rumble");
	static final SoundEvent POWER_FAILING = register("level1.power_failing");
	static final SoundEvent POWER_OFF = register("level1.power_off");
	static final SoundEvent POWER_ON = register("level1.power_on");
	static final SoundEvent CRATE_OPEN = register("level1.crate_open");
	static final SoundEvent WALL_FLICKER = register("level1.wall_flicker");

	private Level1Sounds() {
	}

	static void register() {
		// Triggers static field initialization before registries freeze.
	}

	private static SoundEvent register(String path) {
		Identifier id = Level1.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
