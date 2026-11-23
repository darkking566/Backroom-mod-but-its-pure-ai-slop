package dev.noclip.backrooms.level0;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

final class ModSounds {
	static final SoundEvent FLUORESCENT_HUM = register("ambient.fluorescent_hum");
	static final SoundEvent WALL_SCRATCH = register("ambient.wall_scratch");
	static final SoundEvent DISTANT_WHISPER = register("ambient.distant_whisper");
	static final SoundEvent MANILA_PIANO = register("ambient.manila_piano");

	private ModSounds() {
	}

	static void register() {
		// Triggers static field initialization before registries freeze.
	}

	private static SoundEvent register(String path) {
		Identifier id = BackroomsMod.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
