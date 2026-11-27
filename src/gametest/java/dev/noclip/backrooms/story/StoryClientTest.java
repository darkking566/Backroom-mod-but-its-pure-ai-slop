package dev.noclip.backrooms.story;

import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.LevelExits;
import dev.noclip.backrooms.core.ending.Endings;
import dev.noclip.backrooms.core.story.FirstTrip;
import dev.noclip.backrooms.core.story.MainQuest;
import dev.noclip.backrooms.core.story.StoryBlocks;
import dev.noclip.backrooms.frontrooms.FrontroomsTown;
import dev.noclip.backrooms.frontrooms.Nights398;
import dev.noclip.backrooms.frontrooms.TownLife;
import dev.noclip.backrooms.level0.Level0;
import dev.noclip.backrooms.level0.Level0Guide;
import dev.noclip.backrooms.level0.Level0Space;
import dev.noclip.backrooms.level1.Level1;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * The story (docs/企劃書/1-遊戲玩法/README.md, "M.E.G. 任務主線"). First trip: a newcomer falls into Level 0, finds a
 * cable from where they landed to a glowing door 120 to 150 blocks away, writing on the walls beside it and what the
 * researchers left; every other way out says there is nothing there; the door takes them to the institute; after that,
 * Level 0's exits open as they do for everyone, and falling in again lays no cable. Run alone with
 * gradlew runClientGameTest -Plevels=story
 */
public final class StoryClientTest implements FabricClientGameTest {
	private static final int STAND_Y = 33;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext game = context.worldBuilder().create()) {
			var server = game.getServer();
			server.runCommand("gamerule send_command_feedback false");
			server.runCommand("gamerule noclip_backrooms:edge_shift false");
			server.runCommand("gamemode survival @a");
			server.runCommand("time set noon");
			check(!server.computeOnServer(s -> FirstTrip.done(player(s))), "a new player counts as having been to the institute");

			// Falling in for the first time: a cable from the landing.
			server.runOnServer(s -> LevelExits.fallInto(player(s), "0"));
			server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0 && player(s).hasAttached(FirstTrip.DOOR), 600);
			Vec3 landing = server.computeOnServer(s -> player(s).position());
			BlockPos door = server.computeOnServer(s -> player(s).getAttached(FirstTrip.DOOR));
			double away = Math.hypot(Level0Space.nearest(door.getX() - landing.x), Level0Space.nearest(door.getZ() - landing.z));
			check(away >= 115 && away <= 155, String.format(Locale.ROOT, "the door is %.0f blocks from the landing", away));
			check(server.computeOnServer(s -> level0(s).getBlockState(door).is(StoryBlocks.GUIDE_DOOR)
					&& level0(s).getBlockState(door.above()).is(StoryBlocks.GUIDE_DOOR)), "no door where the cable ends");
			int cable = server.computeOnServer(s -> count(level0(s), BlockPos.containing(landing), 6));
			check(cable > 0, "no cable by the landing");
			Level0Guide.Laid laid = Level0Guide.last();
			check(laid.cable() >= 120 && laid.writing() >= 8 && laid.relics() == 5, "the cable laid: " + laid);
			check(server.computeOnServer(s -> FirstTrip.following(player(s))), "the newcomer is not following the cable");

			game.getConnection().waitForChunksRender();
			context.waitTicks(20);
			context.takeScreenshot("story_first_trip_landing");

			// Every other way out: nothing on the other side.
			server.runOnServer(s -> LevelExits.use(player(s), "1"));
			context.waitTicks(40);
			check(server.computeOnServer(s -> player(s).level().dimension() == Level0.LEVEL_0), "an exit opened on the first trip");

			// The writing nearest the landing, read from in front of it.
			Vec3[] words = server.computeOnServer(s -> nearestWriting(level0(s), landing));
			check(words != null, "no writing near the landing");
			// tp's "facing" aims from the feet; turn by hand so the eyes look level at the wall.
			server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_0 run tp @s %.2f %d %.2f %.1f 0",
					words[1].x, STAND_Y, words[1].z, yaw(words[1], words[0])));
			game.getConnection().waitForChunksRender();
			context.waitTicks(20);
			context.takeScreenshot("story_first_trip_writing");

			// The glowing door, from down the corridor; then through it.
			BlockPos front = server.computeOnServer(s -> inFront(level0(s), door));
			Vec3 corridor = server.computeOnServer(s -> backFrom(level0(s), door, front, 7));
			server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_0 run tp @s %.2f %d %.2f %.1f -5",
					corridor.x, STAND_Y, corridor.z, yaw(corridor, Vec3.atCenterOf(door))));
			game.getConnection().waitForChunksRender();
			context.waitTicks(20);
			context.takeScreenshot("story_first_trip_door");
			server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_0 run tp @s %.1f %d %.1f %.1f 5",
					front.getX() + 0.5, STAND_Y, front.getZ() + 0.5, yaw(Vec3.atCenterOf(front), Vec3.atCenterOf(door))));
			context.waitTicks(10);
			context.takeScreenshot("story_first_trip_door_close");
			String aim = context.computeOnClient(client -> client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
					? client.level.getBlockState(hit.getBlockPos()) + " at " + hit.getBlockPos() : String.valueOf(client.hitResult));
			check(aim.contains("guide_door"), "the crosshair is not on the door: " + aim);
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			server.waitFor(s -> player(s).level().dimension() == Home.FRONTROOMS, 400);
			check(server.computeOnServer(s -> FirstTrip.done(player(s)) && !player(s).hasAttached(FirstTrip.DOOR)),
					"through the door, the player still counts as on their first trip");
			context.waitTicks(40);
			context.takeScreenshot("story_first_trip_institute");
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);

			// The main quest: the commander's first assignment (a mapping job, with a beacon handed over).
			check(server.computeOnServer(s -> MainQuest.chapterIndex(s)) == 0, "the main quest did not start at its first assignment");
			check(server.computeOnServer(s -> player(s).getInventory().contains(new net.minecraft.world.item.ItemStack(dev.noclip.backrooms.core.field.FieldKit.BEACON))),
					"no mapping beacon for the first assignment");
			check(server.computeOnServer(s -> !MainQuest.assigned(s).contains("0.1")), "Level 0.1 assigned before the Lobby was mapped");
			// Each assignment reported unlocks the next level; the smiler photo puts one in B1's first containment cell.
			for (int i = 0; i < 3; i++) {
				int before = i;
				server.runCommand("execute as @a run backrooms story next");
				server.waitFor(s -> MainQuest.chapterIndex(s) == before + 1, 40);
			}
			check(server.computeOnServer(s -> MainQuest.assigned(s).containsAll(java.util.List.of("0", "0.1", "1", "2"))),
					"the reported assignments did not open their levels to M.E.G.'s sending");
			BlockPos cell = server.computeOnServer(s -> dev.noclip.backrooms.frontrooms.Containment.cells().getFirst());
			check(server.computeOnServer(s -> dev.noclip.backrooms.frontrooms.Containment.cells().size()) == 3, "B1 should have three containment cells");
			// B1's corridor runs in front of the cells' observation glass (z 9): stand there, looking in.
			int glass = 7;
			server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:frontrooms run tp @s %d %d %d",
					cell.getX(), cell.getY(), glass));
			server.waitFor(s -> !s.getLevel(Home.FRONTROOMS).getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new AABB(cell).inflate(4),
					e -> e.entityTags().contains(dev.noclip.backrooms.frontrooms.Containment.TAG + "_0")).isEmpty(), 300);
			check(server.computeOnServer(s -> s.getLevel(Home.FRONTROOMS).getEntitiesOfClass(dev.noclip.backrooms.monsters.smiler.Smiler.class,
					new AABB(cell).inflate(4), e -> e.entityTags().contains(dev.noclip.backrooms.frontrooms.Containment.TAG)).size()) == 1,
					"the first containment cell does not hold one smiler");
			server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:frontrooms run tp @s %.1f %d %.1f 0 10",
					cell.getX() + 0.5, cell.getY(), glass + 0.5));
			game.getConnection().waitForChunksRender();
			context.waitTicks(20);
			context.takeScreenshot("story_containment_cell");

			// 398's good ending: someone who knows the truth and has had every letter from home walks past every person
			// without a face on a night of 398; a lit door stands at the street's end; through it, home.
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.setAttached(Endings.STATE, new Endings.State(false, "", Endings.FULL_FILES_MASK));
				p.setAttached(TownLife.LETTERS_HAD, (1 << TownLife.LETTERS) - 1);
			});
			server.runCommand("gamemode creative @a");
			int ground = Nights398.DOOR.getY();
			server.runCommand("execute as @a in noclip_backrooms:frontrooms run tp @s -60 " + ground + " 80.5");
			server.runCommand("time set midnight");
			server.waitFor(s -> Nights398.active(s.getLevel(Home.FRONTROOMS)), 200);
			int houses = FrontroomsTown.houses().size();
			server.waitFor(s -> faceless(s).size() >= houses, 600);
			check(!server.computeOnServer(s -> s.getLevel(Home.FRONTROOMS).getBlockState(Nights398.DOOR).is(StoryBlocks.GUIDE_DOOR)),
					"the door at the street's end is lit before anyone has walked past the people without faces");
			// They stroll about the lawns, so find each one where it stands now, just before walking past it.
			for (int i = 0; i < houses; i++) {
				int which = i;
				Vec3 face = server.computeOnServer(s -> {
					var all = faceless(s);
					return which < all.size() ? all.get(which) : null;
				});
				if (face == null) continue;
				server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:frontrooms run tp @s %.2f %.2f %.2f",
						face.x + 1.2, face.y, face.z));
				context.waitTicks(25);
			}
			server.waitFor(s -> s.getLevel(Home.FRONTROOMS).getBlockState(Nights398.DOOR).is(StoryBlocks.GUIDE_DOOR), 60);
			server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:frontrooms run tp @s %.1f %d %.1f 90 0",
					Nights398.DOOR.getX() + 6.5, ground, Nights398.DOOR.getZ() + 0.5));
			game.getConnection().waitForChunksRender();
			context.waitTicks(20);
			context.takeScreenshot("story_398_door");
			server.runOnServer(s -> Nights398.throughDoor(player(s)));
			server.waitFor(s -> player(s).level().dimension() == net.minecraft.world.level.Level.OVERWORLD && Endings.isFree(player(s)), 200);
			context.waitTicks(10);
			context.takeScreenshot("story_398_home");

			mirroredContents(server);

			// The second trip: no cable, and the exits open.
			server.runOnServer(s -> LevelExits.fallInto(player(s), "0"));
			server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0, 400);
			context.waitTicks(10);
			check(!server.computeOnServer(s -> player(s).hasAttached(FirstTrip.DOOR)), "a cable on the second trip");
			server.runOnServer(s -> LevelExits.use(player(s), "1"));
			server.waitFor(s -> player(s).level().dimension() == Level1.LEVEL_1, 400);
		}
	}

	/** The yaw that looks from {@code from} toward {@code to}. */
	private static double yaw(Vec3 from, Vec3 to) {
		return Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
	}

	/** Up to {@code steps} blocks straight out from the door over open floor, to see it from down the corridor. */
	private static Vec3 backFrom(ServerLevel level, BlockPos door, BlockPos front, int steps) {
		var out = level.getBlockState(door).getValue(StoryBlocks.Door.FACING);
		BlockPos at = front;
		for (int d = 1; d < steps; d++) {
			BlockPos next = front.relative(out, d);
			if (!level.getBlockState(next).getCollisionShape(level, next).isEmpty()
					|| !level.getBlockState(next.above()).getCollisionShape(level, next.above()).isEmpty()) break;
			at = next;
		}
		return Vec3.atBottomCenterOf(at);
	}

	private static BlockPos inFront(ServerLevel level, BlockPos door) {
		var facing = level.getBlockState(door).getValue(StoryBlocks.Door.FACING);
		return door.relative(facing);
	}

	/** The writing nearest {@code from}, and where to stand to read it: a few steps out from its wall, on open floor. */
	private static Vec3[] nearestWriting(ServerLevel level, Vec3 from) {
		Entity best = null;
		for (Display.TextDisplay text : level.getEntitiesOfClass(Display.TextDisplay.class, new AABB(from, from).inflate(60),
				e -> e.entityTags().contains(Level0Guide.WRITING_TAG))) {
			if (best == null || text.distanceToSqr(from) < best.distanceToSqr(from)) best = text;
		}
		if (best == null) return null;
		var out = net.minecraft.core.Direction.fromYRot(best.getYRot());
		Vec3 stand = best.position();
		for (int d = 1; d <= 3; d++) {
			BlockPos p = BlockPos.containing(best.getX() + out.getStepX() * d, STAND_Y, best.getZ() + out.getStepZ() * d);
			if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty() || !level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) break;
			stand = Vec3.atBottomCenterOf(p);
		}
		return new Vec3[] {best.position(), stand};
	}

	/**
	 * Level 0's copies hold what the Stage's block entities hold (level0.Level0Mirror): a chest filled in the Stage is
	 * filled in a copy already loaded, and a sign written in the Stage reads the same in a copy loaded afterwards.
	 */
	private static void mirroredContents(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
		BlockPos chest = new BlockPos(15, STAND_Y, 15), sign = new BlockPos(17, STAND_Y, 15);
		BlockPos chestCopy = chest.offset(Level0Space.PERIOD, 0, 0), signCopy = sign.offset(0, 0, Level0Space.PERIOD);
		server.runOnServer(s -> {
			ServerLevel level = level0(s);
			level.getChunk(chest);
			level.getChunk(chestCopy);
			level.setBlockAndUpdate(chest, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
			level.setBlockAndUpdate(sign, net.minecraft.world.level.block.Blocks.OAK_SIGN.defaultBlockState());
		});
		server.runOnServer(s -> {
			ServerLevel level = level0(s);
			((net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(chest))
					.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 3));
			((net.minecraft.world.level.block.entity.SignBlockEntity) level.getBlockEntity(sign)).setText(
					new net.minecraft.world.level.block.entity.SignText().setMessage(0, net.minecraft.network.chat.Component.literal("LOOK UP")), true);
		});
		check(server.computeOnServer(s -> level0(s).getBlockEntity(chestCopy) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity copy
				&& copy.getItem(0).is(net.minecraft.world.item.Items.DIAMOND) && copy.getItem(0).getCount() == 3),
				"the chest's copy across the Stage's edge does not hold what the chest holds");
		check(server.computeOnServer(s -> {
			ServerLevel level = level0(s);
			level.getChunk(signCopy);
			return level.getBlockEntity(signCopy) instanceof net.minecraft.world.level.block.entity.SignBlockEntity copy
					&& copy.getFrontText().getMessage(0, false).getString().equals("LOOK UP");
		}), "the sign's copy loaded later does not read what the sign says");
	}

	/** Where the people without faces of a night of 398 stand, in the order of their lawns. */
	private static java.util.List<Vec3> faceless(MinecraftServer server) {
		return server.getLevel(Home.FRONTROOMS).getEntitiesOfClass(dev.noclip.backrooms.monsters.faceling.Faceling.class,
				new AABB(-130, 50, 40, 0, 90, 130), f -> f.entityTags().contains(Nights398.TAG)).stream()
				.sorted(java.util.Comparator.comparing(f -> f.entityTags().stream().filter(t -> t.startsWith(Nights398.TAG + "_lawn_"))
						.findFirst().orElse("")))
				.map(Entity::position).toList();
	}

	private static int count(ServerLevel level, BlockPos around, int r) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(around.offset(-r, -1, -r), around.offset(r, 1, r))) {
			if (level.getBlockState(p).is(StoryBlocks.GUIDE_CABLE)) n++;
		}
		return n;
	}

	private static ServerLevel level0(MinecraftServer server) {
		return server.getLevel(Level0.LEVEL_0);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(boolean ok, String message) {
		if (!ok) throw new AssertionError(message);
	}
}
