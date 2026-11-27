package dev.noclip.backrooms.props;

import dev.noclip.backrooms.core.look.Look;
import dev.noclip.backrooms.level0.Level0;
import dev.noclip.backrooms.level0.film.FilmProps;
import dev.noclip.backrooms.level1.Level1Blocks;
import dev.noclip.backrooms.level2.Level2Blocks;
import dev.noclip.backrooms.level3.Level3Blocks;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Verifies model-derived VoxelShape selection outlines and unified multi-block outlines on the real client.
 * Run alone with: gradlew runClientGameTest -Plevels=props
 */
public final class PropsClientTest implements FabricClientGameTest {
	private static final int FLOOR_Y = 32;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext game = context.worldBuilder().create()) {
			var server = game.getServer();
			server.runCommand("gamerule send_command_feedback false");
			server.runCommand("gamerule noclip_backrooms:edge_shift false");
			server.runCommand("gamerule noclip_backrooms:stage_loop false");
			server.runCommand("time set noon");
			server.runCommand("gamemode creative @a");
			server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 0.5 " + (FLOOR_Y + 1) + " 6.5 180 15");
			server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0, 200);

			BlockPos chairPos = new BlockPos(0, FLOOR_Y + 1, 0);
			BlockPos lampPos = new BlockPos(3, FLOOR_Y + 1, 0);
			BlockPos lampTopPos = lampPos.above();
			BlockPos sofaPos = new BlockPos(6, FLOOR_Y + 1, 0);
			BlockPos sofaPartPos = sofaPos.east();
			BlockPos benchPos = new BlockPos(10, FLOOR_Y + 1, 0);
			BlockPos cratePos = new BlockPos(13, FLOOR_Y + 1, 0);
			BlockPos mannequinPos = new BlockPos(-3, FLOOR_Y + 1, 0);
			BlockPos mannequinTopPos = mannequinPos.above();
			BlockPos reclinerPos = new BlockPos(-6, FLOOR_Y + 1, 0);

			// Level 1 / 2 / 3 stray non-full-cube blocks
			BlockPos shelfPos = new BlockPos(-8, FLOOR_Y + 1, -2);
			BlockPos debrisPos = new BlockPos(-5, FLOOR_Y + 1, -2);
			BlockPos valvePipesPos = new BlockPos(-2, FLOOR_Y + 1, -2);
			BlockPos smallPipesPos = new BlockPos(0, FLOOR_Y + 1, -2);
			BlockPos conduitTrayPos = new BlockPos(2, FLOOR_Y + 1, -2);
			BlockPos tablePos = new BlockPos(4, FLOOR_Y + 1, -2);
			BlockPos apparatusPos = new BlockPos(6, FLOOR_Y + 1, -2);
			BlockPos angel0Pos = new BlockPos(8, FLOOR_Y + 1, -2);
			BlockPos angel1Pos = angel0Pos.above();
			BlockPos car0Pos = new BlockPos(10, FLOOR_Y + 1, -2);
			BlockPos gen1Pos = new BlockPos(12, FLOOR_Y + 1, -2);
			BlockPos checkpoint0Pos = new BlockPos(14, FLOOR_Y + 1, -2);

			server.runOnServer(s -> {
				ServerLevel level = s.getLevel(Level0.LEVEL_0);
				for (int x = -10; x <= 16; x++) {
					for (int z = -4; z <= 10; z++) {
						level.setBlock(new BlockPos(x, FLOOR_Y, z), Blocks.SMOOTH_QUARTZ.defaultBlockState(), Block.UPDATE_ALL);
						for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 5; y++) {
							level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
						}
						level.setBlock(new BlockPos(x, FLOOR_Y + 6, z),
								(Math.abs(x) % 4 == 0 && Math.abs(z) % 4 == 0)
										? Blocks.SEA_LANTERN.defaultBlockState()
										: Blocks.SMOOTH_QUARTZ.defaultBlockState(),
								Block.UPDATE_ALL);
					}
				}
				placeSingle(level, chairPos, BackroomsProps.PROP_WOOD_CHAIR, Direction.SOUTH);
				placeMulti(level, lampPos, BackroomsProps.PROP_FLOOR_LAMP, Direction.SOUTH);
				placeMulti(level, sofaPos, BackroomsProps.PROP_SOFA, Direction.NORTH);
				placeSingle(level, benchPos, BackroomsProps.SALVAGE_BENCH, Direction.SOUTH);
				placeSingle(level, cratePos, BackroomsProps.LOCKED_ARMORY_CRATE, Direction.SOUTH);
				placeMulti(level, mannequinPos, FilmProps.MANNEQUIN, Direction.SOUTH);
				placeSingle(level, reclinerPos, FilmProps.RECLINER_LEATHER, Direction.SOUTH);

				level.setBlock(shelfPos, Level1Blocks.METAL_SHELF.defaultBlockState(), Block.UPDATE_ALL);
				level.setBlock(debrisPos, Level1Blocks.DEBRIS_PILE.defaultBlockState(), Block.UPDATE_ALL);
				level.setBlock(valvePipesPos, Level2Blocks.VALVE_PIPES.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), Block.UPDATE_ALL);
				level.setBlock(smallPipesPos, Level2Blocks.SMALL_PIPES.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), Block.UPDATE_ALL);
				level.setBlock(conduitTrayPos, Level2Blocks.CONDUIT_TRAY.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), Block.UPDATE_ALL);
				level.setBlock(tablePos, Level3Blocks.TABLE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), Block.UPDATE_ALL);
				level.setBlock(apparatusPos, Level3Blocks.UEC_APPARATUS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), Block.UPDATE_ALL);
				placeL3Part(level, angel0Pos, Level3Blocks.ANGEL, Direction.SOUTH, 0);
				placeL3Part(level, angel1Pos, Level3Blocks.ANGEL, Direction.SOUTH, 1);
				placeL3Part(level, car0Pos, Level3Blocks.CAR, Direction.SOUTH, 0);
				placeL3Part(level, gen1Pos, Level3Blocks.GENERATOR, Direction.SOUTH, 1);
				placeL3Part(level, checkpoint0Pos, Level3Blocks.CHECKPOINT_DOOR, Direction.SOUTH, 0);
			});

			context.runOnClient(client -> Look.setEnabled(false));
			game.getConnection().waitForChunksRender();
			context.waitTicks(10);

			String error = context.computeOnClient(client -> {
				var level = client.level;
				if (level == null) return "client.level is null";

				for (BlockPos pos : List.of(chairPos, benchPos, cratePos, reclinerPos,
						shelfPos, debrisPos, valvePipesPos, smallPipesPos, conduitTrayPos,
						tablePos, apparatusPos, angel0Pos, angel1Pos, car0Pos, gen1Pos, checkpoint0Pos)) {
					VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
					if (shape.equals(Shapes.block())) {
						return "Prop still has full-block shape at " + pos + ": " + level.getBlockState(pos);
					}
				}

				VoxelShape cpCol = level.getBlockState(checkpoint0Pos).getCollisionShape(level, checkpoint0Pos);
				if (cpCol.isEmpty() || !cpCol.bounds().contains(0.5, 0.5, 0.5)) {
					return "CHECKPOINT_DOOR collision should block center gap, got " + cpCol;
				}

				String sofaCheck = checkMultiOutline(level, sofaPos, sofaPartPos, "prop_sofa");
				if (sofaCheck != null) return sofaCheck;
				String lampCheck = checkMultiOutline(level, lampPos, lampTopPos, "prop_floor_lamp");
				if (lampCheck != null) return lampCheck;
				String mannequinCheck = checkMultiOutline(level, mannequinPos, mannequinTopPos, "film_mannequin");
				if (mannequinCheck != null) return mannequinCheck;

				return null;
			});
			if (error != null) {
				throw new AssertionError(error);
			}

			aimAndCapture(context, server, chairPos, 0.5, FLOOR_Y + 1, 2.5, 180, 18, "prop_outline_chair");
			aimAndCapture(context, server, sofaPartPos, 7.5, FLOOR_Y + 1, 2.8, 180, 27, "prop_outline_sofa_part");
			aimAndCapture(context, server, lampTopPos, 3.5, FLOOR_Y + 1, 2.8, 180, 3, "prop_outline_lamp_part");
			aimAndCapture(context, server, benchPos, 10.5, FLOOR_Y + 1, 2.5, 180, 28, "prop_outline_bench");
			aimAndCapture(context, server, debrisPos, -4.5, FLOOR_Y + 1, 0.2, 180, 38, "prop_outline_l1_debris");
			aimAndCapture(context, server, valvePipesPos, -1.5, FLOOR_Y + 1, 0.2, 180, 22, "prop_outline_l2_pipes");
			aimAndCapture(context, server, angel1Pos, 8.5, FLOOR_Y + 1, 0.5, 180, 8, "prop_outline_l3_angel");

			context.runOnClient(client -> Look.setEnabled(true));
		}
	}

	private static void aimAndCapture(ClientGameTestContext context, TestServerContext server, BlockPos target,
			double px, double py, double pz, float yaw, float pitch, String screenshotName) {
		server.runCommand(String.format(Locale.ROOT,
				"execute as @a in noclip_backrooms:level_0 run tp @s %.2f %.2f %.2f %.1f %.1f",
				px, py, pz, yaw, pitch));
		context.waitTicks(6);
		String hitError = context.computeOnClient(client -> {
			if (client.hitResult instanceof BlockHitResult bhr && bhr.getBlockPos().equals(target)) {
				return null;
			}
			return screenshotName + " expected hit on " + target + ", got " + client.hitResult;
		});
		if (hitError != null) {
			throw new AssertionError(hitError);
		}
		context.takeScreenshot(screenshotName);
	}

	private static String checkMultiOutline(net.minecraft.client.multiplayer.ClientLevel level, BlockPos masterPos,
			BlockPos partPos, String name) {
		BlockState masterState = level.getBlockState(masterPos);
		BlockState partState = level.getBlockState(partPos);
		if (!(partState.getBlock() instanceof PropPartBlock)) {
			return name + " expected PropPartBlock at " + partPos + ", got " + partState;
		}
		AABB masterBounds = masterState.getShape(level, masterPos).bounds().move(masterPos);
		AABB partBounds = partState.getShape(level, partPos).bounds().move(partPos);
		if (!approxEqual(masterBounds, partBounds, 1e-5)) {
			return name + " part outline does not match master: master=" + masterBounds + ", part=" + partBounds;
		}
		VoxelShape partCol = partState.getCollisionShape(level, partPos);
		if (!partCol.isEmpty()) {
			AABB cb = partCol.bounds();
			if (cb.minX < -1e-6 || cb.maxX > 1.0 + 1e-6 || cb.minY < -1e-6 || cb.maxY > 1.0 + 1e-6
					|| cb.minZ < -1e-6 || cb.maxZ > 1.0 + 1e-6) {
				return name + " part collision out of [0,1]^3: " + cb;
			}
		}
		return null;
	}

	private static boolean approxEqual(AABB a, AABB b, double eps) {
		return Math.abs(a.minX - b.minX) < eps
				&& Math.abs(a.minY - b.minY) < eps
				&& Math.abs(a.minZ - b.minZ) < eps
				&& Math.abs(a.maxX - b.maxX) < eps
				&& Math.abs(a.maxY - b.maxY) < eps
				&& Math.abs(a.maxZ - b.maxZ) < eps;
	}

	private static void placeSingle(ServerLevel level, BlockPos pos, Block block, Direction facing) {
		level.setBlock(pos, block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing), Block.UPDATE_ALL);
	}

	private static void placeL3Part(ServerLevel level, BlockPos pos, Block block, Direction facing, int part) {
		BlockState state = block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
		if (state.getBlock().getStateDefinition().getProperty("part") instanceof IntegerProperty partProp) {
			state = state.setValue(partProp, part);
		}
		level.setBlock(pos, state, Block.UPDATE_ALL);
	}

	private static void placeMulti(ServerLevel level, BlockPos masterPos, Block block, Direction facing) {
		BlockState masterState = block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
		level.setBlock(masterPos, masterState, Block.UPDATE_ALL);
		if (block instanceof MultiPartPropBlock multi) {
			BlockState partState = BackroomsProps.PROP_PART.defaultBlockState();
			for (BlockPos cell : multi.occupiedPositions(masterPos, facing)) {
				if (cell.equals(masterPos)) continue;
				level.setBlock(cell, partState, Block.UPDATE_ALL);
				if (level.getBlockEntity(cell) instanceof PropBlockEntity partBe) {
					partBe.setMasterPos(masterPos);
				}
			}
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}
}
