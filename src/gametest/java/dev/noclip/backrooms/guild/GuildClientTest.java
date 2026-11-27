package dev.noclip.backrooms.guild;

import dev.noclip.backrooms.core.guild.GuildPalette;
import dev.noclip.backrooms.core.guild.GuildPiece;
import dev.noclip.backrooms.core.guild.GuildRemoval;
import dev.noclip.backrooms.core.guild.Guilds;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The stone guilds beside the villages are gone (docs/企劃書/1-遊戲玩法/README.md, 2026-11-26: the M.E.G. institute in
 * the Frontrooms took their place): a world with villages has no guild beside them, and pulling one down
 * (core.guild.GuildRemoval, which does it to older worlds' guilds as they load) leaves grass where its floor was and
 * nothing above it. Run alone with gradlew runClientGameTest -Plevels=guild
 */
public final class GuildClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext game = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(settings -> {
			settings.setSeed("noclip-guild");
			settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
			settings.setGenerateStructures(true);
		}).create()) {
			var server = game.getServer();
			server.runCommand("gamerule send_command_feedback false");
			server.runCommand("time set 6000");

			BlockPos village = server.computeOnServer(s -> s.overworld().findNearestMapStructure(StructureTags.VILLAGE, BlockPos.ZERO, 100, false));
			check(village != null, "no village to check beside");
			check(server.computeOnServer(s -> s.overworld().findNearestMapStructure(Guilds.GUILDS, village, 8, false)) == null,
					"a guild still generates beside a village");

			// A hall pulled down the way an older world's guild is.
			int x = village.getX() + 300, z = village.getZ();
			server.runCommand("forceload add " + (x - 32) + " " + (z - 32) + " " + (x + 32) + " " + (z + 32));
			int ground = server.computeOnServer(s -> {
				s.overworld().getChunk(x >> 4, z >> 4);
				return s.overworld().getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
			});
			server.runCommand("execute in minecraft:overworld run tp @a " + x + " " + (ground + 6) + " " + (z - 14) + " 0 20");
			int[] box = server.computeOnServer(s -> {
				ServerPlayer player = player(s);
				GuildPiece hall = GuildPiece.placeNow(player.level(), new BlockPos(x, ground, z - 8), Direction.NORTH, GuildPalette.PLAINS);
				BoundingBox b = hall.getBoundingBox();
				return new int[] {b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()};
			});
			context.waitTicks(20);
			context.takeScreenshot("guild_before_removal");
			server.runOnServer(s -> GuildRemoval.demolish(s.overworld(), new BoundingBox(box[0], box[1], box[2], box[3], box[4], box[5]), false));
			context.waitTicks(20);
			context.takeScreenshot("guild_removed");
			server.runOnServer(s -> {
				var level = s.overworld();
				for (int bx = box[0]; bx <= box[3]; bx += 3) {
					for (int bz = box[2]; bz <= box[5]; bz += 3) {
						var floor = level.getBlockState(new BlockPos(bx, box[1], bz));
						check(floor.is(Blocks.GRASS_BLOCK) || floor.isAir(), "the floor did not turn to grass at " + bx + ", " + bz + ": " + floor);
						for (int by = box[1] + 1; by <= box[4]; by++) {
							check(level.getBlockState(new BlockPos(bx, by, bz)).isAir(), "something of the hall is left at " + bx + ", " + by + ", " + bz);
						}
					}
				}
			});
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(boolean ok, String message) {
		if (!ok) throw new AssertionError(message);
	}
}
