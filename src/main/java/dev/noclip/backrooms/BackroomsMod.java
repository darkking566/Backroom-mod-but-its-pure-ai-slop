package dev.noclip.backrooms;

import dev.noclip.backrooms.level0.Level0;
import dev.noclip.backrooms.level1.Level1;
import dev.noclip.backrooms.monsters.Monsters;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

/**
 * The mod's entry point. Each level lives in its own package (level0, level1, ...) and registers itself;
 * things that cross between levels live in core, and monsters (which can turn up on several levels) in monsters.
 * See README.md for where things go.
 */
public final class BackroomsMod implements ModInitializer {
	public static final String MOD_ID = "noclip_backrooms";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		Monsters.register();
		Level0.register();
		Level1.register();
	}
}
