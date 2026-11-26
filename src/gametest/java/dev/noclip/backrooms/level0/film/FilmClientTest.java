package dev.noclip.backrooms.level0.film;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.look.Look;
import dev.noclip.backrooms.level0.Level0;
import dev.noclip.backrooms.props.MultiPartPropBlock;
import dev.noclip.backrooms.props.PropBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;

/**
 * The film's Level 0 objects (level0.film): every prop is registered with the props' block entity, has its mesh and
 * texture, stands in a lit room of Level 0 (photographed row by row, with and without the camcorder look), and the
 * VHS tapes drop as labelled tapes the buyer takes. Run alone with gradlew runClientGameTest -Plevels=film
 */
public final class FilmClientTest implements FabricClientGameTest {
	private static final int FLOOR = 32;
	/** How wide a row of props gets before the next row (one photo each). */
	private static final int ROW = 13;
	private static final int GAP = 12;

	private record Spot(String id, BlockPos at) {
	}

	private record Row(int x0, int x1, int z) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext game = context.worldBuilder().create()) {
			var server = game.getServer();
			server.runCommand("gamerule send_command_feedback false");
			server.runCommand("gamerule noclip_backrooms:edge_shift false");
			server.runCommand("gamerule noclip_backrooms:stage_loop false");
			server.runCommand("time set noon");
			server.runCommand("gamemode creative @a");
			server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 6.5 " + (FLOOR + 1) + " 8.5 180 15");
			server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0, 200);
			List<Row> rows = new ArrayList<>();
			List<Spot> spots = server.computeOnServer(s -> layout(s, rows));

			// Every prop: its block entity (drawn by props.BlenderPropRenderer), its mesh and texture on the client.
			List<String> missing = server.computeOnServer(s -> {
				ServerLevel level = s.getLevel(Level0.LEVEL_0);
				List<String> out = new ArrayList<>();
				for (Spot spot : spots) if (!(level.getBlockEntity(spot.at()) instanceof PropBlockEntity)) out.add(spot.id());
				return out;
			});
			check(missing.isEmpty(), "no prop block entity at " + missing);
			List<String> noMesh = context.computeOnClient(client -> FilmProps.all().keySet().stream()
					.filter(id -> FilmLook.mesh(id).isEmpty()
							|| client.getResourceManager().getResource(FilmLook.texture(id)).isEmpty()).toList());
			check(noMesh.isEmpty(), "no mesh or texture for " + noMesh);

			// Photographs, row by row: once as the game looks (camcorder), once plain.
			for (boolean camcorder : new boolean[] {true, false}) {
				context.runOnClient(client -> Look.setEnabled(camcorder));
				for (int r = 0; r < rows.size(); r++) {
					Row row = rows.get(r);
					double x = (row.x0() + row.x1()) / 2.0 + 0.5;
					server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s " + x + " " + (FLOOR + 1) + " " + (row.z() + 8.5) + " 180 12");
					game.getConnection().waitForChunksRender();
					context.waitTicks(10);
					context.takeScreenshot("film_row_" + r + (camcorder ? "_look" : "_plain"));
				}
			}
			// A staged display room, as the store (and the Backrooms' copy of it) set them out, seen up close.
			server.runOnServer(s -> showroom(s.getLevel(Level0.LEVEL_0)));
			for (boolean camcorder : new boolean[] {true, false}) {
				context.runOnClient(client -> Look.setEnabled(camcorder));
				server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 7.5 " + (FLOOR + 1) + " 25.5 180 14");
				game.getConnection().waitForChunksRender();
				context.waitTicks(10);
				context.takeScreenshot("film_showroom" + (camcorder ? "_look" : "_plain"));
				server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 12.5 " + (FLOOR + 1) + " 23.5 145 18");
				context.waitTicks(10);
				context.takeScreenshot("film_showroom_side" + (camcorder ? "_look" : "_plain"));
			}
			context.runOnClient(client -> Look.setEnabled(true));

			// Breaking a pile of tapes gives labelled tapes; breaking a sofa gives its stuffing.
			server.runCommand("gamemode survival @a");
			BlockPos pile = new BlockPos(-6, FLOOR + 1, 30), sofa = new BlockPos(-6, FLOOR + 1, 36);
			server.runOnServer(s -> {
				ServerLevel level = s.getLevel(Level0.LEVEL_0);
				level.setBlockAndUpdate(pile, FilmProps.VHS_PILE_SMALL.defaultBlockState());
				place(level, "film_sofa_tan", sofa);
				player(s).teleportTo(level, -5.5, FLOOR + 1, 33.5, java.util.Set.of(), 0, 0, false);
			});
			context.waitTicks(5);
			int[] tapes = server.computeOnServer(s -> {
				ServerLevel level = s.getLevel(Level0.LEVEL_0);
				level.destroyBlock(pile, true, player(s));
				int count = 0, labelled = 0;
				for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(pile).inflate(3))) {
					if (!item.getItem().is(FilmProps.VHS_TAPE)) continue;
					count += item.getItem().getCount();
					var lore = item.getItem().get(DataComponents.LORE);
					if (lore != null && !lore.lines().isEmpty()) labelled += item.getItem().getCount();
				}
				return new int[] {count, labelled};
			});
			check(tapes[0] >= 6 && tapes[0] <= 10, "a small pile of tapes gave " + tapes[0] + " tapes");
			check(tapes[1] == tapes[0], "only " + tapes[1] + " of " + tapes[0] + " tapes have a label");
			boolean[] sofaGone = server.computeOnServer(s -> {
				ServerLevel level = s.getLevel(Level0.LEVEL_0);
				BlockPos part = sofa.west(2);
				boolean partBefore = !level.getBlockState(part).isAir();
				// A player breaking the far cell, as in survival: the whole sofa goes, its stuffing drops once.
				player(s).gameMode.destroyBlock(part);
				boolean rags = level.getEntitiesOfClass(ItemEntity.class, new AABB(sofa).inflate(4)).stream()
						.anyMatch(e -> BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).getPath().equals("rags"));
				return new boolean[] {partBefore, level.getBlockState(sofa).isAir() && level.getBlockState(part).isAir(), rags};
			});
			check(sofaGone[0], "the sofa's far cell was never filled");
			check(sofaGone[1], "breaking the sofa left part of it standing");
			check(sofaGone[2], "the sofa gave no rags");
			// The buyer takes tapes.
			boolean bought = server.computeOnServer(s -> dev.noclip.backrooms.core.trade.Shops.offers(dev.noclip.backrooms.core.trade.ShopRole.BUYER)
					.stream().anyMatch(offer -> offer.goods().is(FilmProps.VHS_TAPE)));
			check(bought, "the buyer does not take VHS tapes");
		}
	}

	/** A lit Level 0 hall with every film prop in rows facing south, the camera's side. */
	private static List<Spot> layout(MinecraftServer server, List<Row> rows) {
		ServerLevel level = server.getLevel(Level0.LEVEL_0);
		Map<String, Block> all = FilmProps.all();
		// Rows: props side by side until ROW wide; each row GAP blocks north of the last.
		List<List<String>> grouped = new ArrayList<>();
		List<String> current = new ArrayList<>();
		int width = 0;
		for (String id : all.keySet()) {
			int w = size(all.get(id))[0];
			if (width + w > ROW && !current.isEmpty()) {
				grouped.add(current);
				current = new ArrayList<>();
				width = 0;
			}
			current.add(id);
			width += w + 1;
		}
		grouped.add(current);
		int depthAll = grouped.size() * GAP + 12;
		hall(level, -10, 24, -depthAll, 40);
		List<Spot> spots = new ArrayList<>();
		for (int r = 0; r < grouped.size(); r++) {
			int z = -r * GAP;
			int x = 0;
			for (String id : grouped.get(r)) {
				Block block = all.get(id);
				int[] size = size(block);
				// Facing south the prop's width runs west of its master block and its depth north of it.
				int masterX = x + size[0] - 1;
				BlockPos at = new BlockPos(masterX, FLOOR + 1, z);
				if (block instanceof FilmPropBlock film && film.mount() == FilmPropBlock.Mount.CEILING) at = at.above(4);
				if (block instanceof FilmPropBlock film && film.mount() == FilmPropBlock.Mount.WALL) {
					for (int dx = -size[0] + 1; dx <= 0; dx++) {
						for (int y = FLOOR + 1; y <= FLOOR + 5; y++) level.setBlockAndUpdate(new BlockPos(masterX + dx, y, z - 1), block("wallpaper").defaultBlockState());
					}
				}
				place(level, id, at);
				spots.add(new Spot(id, at));
				x += size[0] + 1;
			}
			rows.add(new Row(0, x - 2, z));
		}
		return spots;
	}

	/** Width, height, depth: a multi-block prop's own size; the one-block props count as one (their meshes may hang over). */
	private static int[] size(Block block) {
		if (block instanceof MultiPartPropBlock multi) return new int[] {multi.width(), multi.height(), multi.depth()};
		String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
		return switch (id) {
			case "film_oriental_rug" -> new int[] {2, 1, 2};
			case "film_bare_mattress" -> new int[] {1, 1, 2};
			case "film_banner_no_credit" -> new int[] {3, 1, 1};
			case "film_sign_pirate" -> new int[] {2, 2, 1};
			default -> new int[] {1, 1, 1};
		};
	}

	/** A living-room display south of the rows: a rug, a sofa, recliners, lamps, a sale sign, carpet rolls behind. */
	private static void showroom(ServerLevel level) {
		int y = FLOOR + 1;
		place(level, "film_oriental_rug", new BlockPos(8, y, 17));
		place(level, "film_coffee_table_glass", new BlockPos(7, y, 16));
		place(level, "film_sofa_tan", new BlockPos(9, y, 14));
		place(level, "film_end_table_lamp", new BlockPos(10, y, 14));
		place(level, "film_torchiere", new BlockPos(5, y, 14));
		place(level, "film_recliner_mauve", new BlockPos(4, y, 16), Direction.EAST);
		place(level, "film_swivel_chair_teal", new BlockPos(11, y, 17), Direction.WEST);
		place(level, "film_tv_tray", new BlockPos(10, y, 17));
		place(level, "film_sign_sale75", new BlockPos(7, y + 4, 17));
		place(level, "film_carpet_rolls", new BlockPos(5, y, 11));
		place(level, "film_armoire", new BlockPos(8, y, 11));
		place(level, "film_ladderback_chair", new BlockPos(12, y, 12), Direction.WEST);
		place(level, "film_vhs_scatter", new BlockPos(6, y, 19));
		place(level, "film_ottoman_diamond", new BlockPos(3, y, 18), Direction.EAST);
	}

	private static void place(ServerLevel level, String id, BlockPos at) {
		place(level, id, at, Direction.SOUTH);
	}

	/** Places a prop facing a way; a multi-block one fills its other cells the way a player's placing would. */
	private static void place(ServerLevel level, String id, BlockPos at, Direction facing) {
		Block block = block(id);
		var state = block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
		level.setBlockAndUpdate(at, state);
		if (block instanceof MultiPartPropBlock multi) multi.setPlacedBy(level, at, state, null, block.asItem().getDefaultInstance());
	}

	/** A Level 0 hall: carpet, wallpaper walls, a ceiling five blocks up with a grid of lights. */
	private static void hall(ServerLevel level, int x0, int x1, int z0, int z1) {
		Block carpet = block("carpet"), wall = block("wallpaper"), ceiling = block("ceiling_tile"), light = block("fluorescent_light");
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				level.setBlockAndUpdate(new BlockPos(x, FLOOR, z), carpet.defaultBlockState());
				boolean lamp = Math.floorMod(x, 4) == 1 && Math.floorMod(z, 4) == 1;
				level.setBlockAndUpdate(new BlockPos(x, FLOOR + 6, z), (lamp ? light : ceiling).defaultBlockState());
				for (int y = FLOOR + 1; y <= FLOOR + 5; y++) {
					level.setBlockAndUpdate(new BlockPos(x, y, z), edge ? wall.defaultBlockState() : Blocks.AIR.defaultBlockState());
				}
			}
		}
	}

	private static Block block(String id) {
		return BuiltInRegistries.BLOCK.getValue(BackroomsMod.id(id));
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(boolean ok, String message) {
		if (!ok) throw new AssertionError(message);
	}
}
