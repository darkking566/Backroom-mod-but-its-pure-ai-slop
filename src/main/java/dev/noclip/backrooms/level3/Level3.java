package dev.noclip.backrooms.level3;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.LevelExits;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Level 3, "Electrical Station" (Wikidot): brick hallways of wildly inconsistent size, thick with antiquated
 * electrical equipment that runs by itself, pipes of scalding black sludge, bars nothing can break, and halls:
 * generator rooms, boiler rooms, assembly lines, sanctums. Everything for this level lives in this package; the
 * rest of the mod only calls {@link #register()} and refers to {@link #LEVEL_3}.
 */
public final class Level3 {
	public static final String MOD_ID = BackroomsMod.MOD_ID;
	public static final ResourceKey<Level> LEVEL_3 = ResourceKey.create(Registries.DIMENSION, id("level_3"));

	private Level3() {
	}

	public static Identifier id(String path) {
		return BackroomsMod.id(path);
	}

	public static void register() {
		Level3Blocks.register();
		Level3Sounds.register();
		Registry.register(BuiltInRegistries.CHUNK_GENERATOR, id("level_3"), Level3ChunkGenerator.CODEC);
		// How you arrive here through an exit from another level (see core.LevelExits): Level 2's doors marked "3".
		LevelExits.open("3", Level3Travel::enter);
		Level3Events.register();
		Level3Monsters.register();
	}
}
