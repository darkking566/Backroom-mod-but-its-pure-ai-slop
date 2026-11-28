package dev.noclip.backrooms.field;

import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.LevelExits;
import dev.noclip.backrooms.core.ending.Endings;
import dev.noclip.backrooms.core.field.Evidence;
import dev.noclip.backrooms.core.field.FieldKit;
import dev.noclip.backrooms.core.field.PhotoScreen;
import dev.noclip.backrooms.core.field.Photos;
import dev.noclip.backrooms.core.locker.Lockers;
import dev.noclip.backrooms.core.locker.Processing;
import dev.noclip.backrooms.core.locker.Research;
import dev.noclip.backrooms.core.people.People;
import dev.noclip.backrooms.core.people.Person;
import dev.noclip.backrooms.core.quota.Quota;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.core.trade.ShopRole;
import dev.noclip.backrooms.core.trade.Shops;
import dev.noclip.backrooms.core.trip.Anomalies;
import dev.noclip.backrooms.core.trip.AnomalyClient;
import dev.noclip.backrooms.core.trip.MissionReportScreen;
import dev.noclip.backrooms.core.trip.Report;
import dev.noclip.backrooms.core.trip.Trips;
import dev.noclip.backrooms.crafting.BackroomsCrafting;
import dev.noclip.backrooms.crafting.BackroomsRecipeRegistry;
import dev.noclip.backrooms.crafting.BackroomsStationMenu;
import dev.noclip.backrooms.crafting.StationType;
import dev.noclip.backrooms.items.BackroomsItems;
import dev.noclip.backrooms.level0.Level0;
import dev.noclip.backrooms.level1.Blackouts;
import dev.noclip.backrooms.monsters.Monsters;
import dev.noclip.backrooms.monsters.hound.Hound;
import dev.noclip.backrooms.monsters.skinstealer.SkinStealer;
import dev.noclip.backrooms.props.BackroomsProps;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What a trip into the Backrooms brings now (docs/企劃書/1-遊戲玩法/README.md, "後室進階玩法與創新機制"): the trip's
 * anomaly (red mist, mimics, absolute silence), the field kit (chalk, glow sticks, the guide line, a battery lamp, a
 * tripwire alarm, a barricade a hound batters down), evidence (a photo of a smiler and a mapping beacon's tape, sold to
 * the buyer), the institute's machines working while the player was away, and the weapons' horror balance.
 * Run alone with gradlew runClientGameTest -Plevels=field
 */
public final class FieldClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext game = context.worldBuilder().create()) {
			var server = game.getServer();
			// Not a newcomer: the first trip's cable (core.story.FirstTrip) belongs to the story test.
			server.runOnServer(s -> s.getPlayerList().getPlayers().forEach(p -> p.setAttached(dev.noclip.backrooms.core.story.FirstTrip.DONE, true)));
			server.runCommand("gamerule send_command_feedback false");
			server.runCommand("gamerule noclip_backrooms:edge_shift false");
			server.runCommand("gamerule do_mob_spawning false");
			server.runCommand("gamemode survival @a");
			server.runCommand("effect give @a resistance infinite 4 true");
			server.runCommand("effect give @a night_vision infinite 0 true");

			// Falling in the way the team does opens a new stretch, and draws its anomaly.
			server.runOnServer(s -> LevelExits.fallInto(player(s), "0"));
			server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0, 400);
			check(server.computeOnServer(s -> Anomalies.current(s)) != null, "falling in drew no anomaly");
			context.waitFor(client -> !AnomalyClient.affix().isEmpty(), 100);
			game.getConnection().waitForChunksRender();
			server.runCommand("execute as @a run backrooms quota start");
			server.runCommand("kill @e[type=!player]");

			// Red mist: localized to Red Mist enclaves (safe outside, sanity drains + red haze inside, with guaranteed vault chest).
			server.runCommand("execute as @a run backrooms anomaly set red_mist");
			context.waitFor(client -> AnomalyClient.affix().equals("red_mist"), 60);
			BlockPos clearOut = stand(server);
			check(!server.computeOnServer(s -> Anomalies.isInRedMistZone(level(s), clearOut)), "normal hallway should not be a red mist zone");
			context.waitTicks(45);
			check(!AnomalyClient.inZone(), "red mist overlay should stay off outside red mist enclaves");
			BlockPos earlyEnclave = server.computeOnServer(s -> {
				ServerLevel sl = level(s);
				BlockPos at = Anomalies.infestRedRoomAt(sl, clearOut.north(2));
				if (at == null) at = Anomalies.infestRedRoomAt(sl, clearOut);
				if (at == null) at = Anomalies.infestRedRoomNear(sl, clearOut);
				return at;
			});
			check(earlyEnclave != null, "failed to form red mist enclave near player at " + clearOut);
			server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s "
					+ (earlyEnclave.getX() + 0.5) + " " + earlyEnclave.getY() + " " + (earlyEnclave.getZ() + 2.5) + " 180 12");
			check(server.computeOnServer(s -> {
				for (Direction d : Direction.Plane.HORIZONTAL) {
					if (level(s).getBlockState(earlyEnclave.relative(d)).is(BackroomsProps.ANOMALY_VAULT_CHEST)) return true;
				}
				return false;
			}), "red mist enclave did not spawn an anomaly vault chest");
			context.waitFor(client -> AnomalyClient.inZone(), 80);
			float sanity = server.computeOnServer(s -> PlayerRpg.get(player(s)).sanity());
			context.waitTicks(80);
			context.takeScreenshot("field_red_mist");
			check(server.computeOnServer(s -> PlayerRpg.get(player(s)).sanity()) < sanity - 1.0F, "standing inside a red mist enclave took nothing from the mind");

			// Mimics: some unopened crates are not crates.
			server.runCommand("execute as @a run backrooms anomaly set mimics");
			BlockPos feet = server.computeOnServer(s -> player(s).blockPosition());
			int mimics = server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = (ServerLevel) p.level();
				int bit = 0;
				for (int i = 0; i < 24; i++) {
					BlockPos at = feet.above(3).east(i % 6 - 3).south(i / 6 - 2);
					level.setBlockAndUpdate(at, Blocks.CHEST.defaultBlockState());
					if (level.getBlockEntity(at) instanceof ChestBlockEntity chest) {
						chest.setLootTable(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
								Identifier.parse("noclip_backrooms:chests/level2_supplies")));
					}
					p.gameMode.useItemOn(p, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
							new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
					p.closeContainer();
					if (!level.getBlockState(at).is(Blocks.CHEST)) bit++;
				}
				return bit;
			});
			check(mimics > 0, "no crate out of 24 was a mimic");
			server.runCommand("kill @e[type=!player]");
			server.runCommand("execute as @a at @s run fill ~-4 ~2 ~-4 ~4 ~4 ~4 minecraft:air replace minecraft:chest");

			// Absolute silence: walking is heard from twenty blocks.
			server.runCommand("execute as @a run backrooms anomaly set silence");
			stand(server);
			int hound = spawnHound(server, 14, 0, false);
			// A few steps (moved a block a second, as walking would).
			for (int step = 0; step < 3; step++) {
				server.runCommand("execute as @a at @s run tp @s ~ ~ ~0.8");
				context.waitTicks(22);
			}
			check(server.computeOnServer(s -> houndOf(s, hound) != null && houndOf(s, hound).getTarget() == player(s)), "walking in silence was not heard");
			server.runCommand("kill @e[type=!player]");

			// The field kit.
			BlockPos here = stand(server);
			BlockPos chalkAt = here.north(2);
			server.runOnServer(s -> useOn(player(s), new ItemStack(FieldKit.CHALK), chalkAt.below(), Direction.UP));
			check(server.computeOnServer(s -> level(s).getBlockState(chalkAt).is(FieldKit.CHALK_MARK)), "no chalk mark");
			server.runOnServer(s -> useOn(player(s), new ItemStack(FieldKit.CHALK), chalkAt, Direction.UP));
			server.runOnServer(s -> useOn(player(s), new ItemStack(FieldKit.GLOW_STICK), here.north(3).below(), Direction.UP));
			check(server.computeOnServer(s -> level(s).getBlockState(here.north(3)).is(FieldKit.GLOW_STICK_BLOCK)), "no glow stick");
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 180 50");
			context.waitTicks(10);
			context.takeScreenshot("field_chalk_and_glow_stick");

			// The guide line lays itself while walking.
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FieldKit.GUIDE_LINE));
				p.gameMode.useItem(p, p.level(), p.getMainHandItem(), InteractionHand.MAIN_HAND);
			});
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
			context.getInput().holdKeyFor(options -> options.keyUp, 30);
			int lines = server.computeOnServer(s -> count(level(s), player(s).blockPosition(), FieldKit.RED_LINE));
			String where = server.computeOnServer(s -> player(s).position() + " onGround=" + player(s).onGround()
					+ " hand=" + player(s).getMainHandItem());
			check(lines >= 2, "the guide line laid " + lines + " blocks (player at " + where + ")");

			// A battery lamp steadies the mind.
			BlockPos here1 = stand(server);
			BlockPos lamp = here1.east();
			server.runOnServer(s -> useOn(player(s), new ItemStack(FieldKit.EMERGENCY_LAMP), lamp.below(), Direction.UP));
			server.runCommand("execute as @a run backrooms anomaly set static");
			server.runOnServer(s -> PlayerRpg.set(player(s), PlayerRpg.get(player(s)).withSanity(5.0F)));
			context.waitTicks(80);
			check(server.computeOnServer(s -> level(s).getBlockState(lamp).is(FieldKit.LAMP_BLOCK)), "no lamp: " + server.computeOnServer(s -> level(s).getBlockState(lamp)));
			check(server.computeOnServer(s -> PlayerRpg.get(player(s)).sanity()) > 5.5F, "the lamp did not steady the mind: sanity "
					+ server.computeOnServer(s -> PlayerRpg.get(player(s)).sanity()) + ", lamp " + server.computeOnServer(s -> level(s).getBlockState(lamp))
					+ ", player " + server.computeOnServer(s -> player(s).blockPosition()) + ", lamp at " + lamp);

			// A tripwire alarm rattles when something walks through.
			BlockPos wire = here1.west(2);
			server.runOnServer(s -> useOn(player(s), new ItemStack(FieldKit.TRIPWIRE_ALARM), wire.below(), Direction.UP));
			check(server.computeOnServer(s -> level(s).getBlockState(wire).is(FieldKit.TRIPWIRE_BLOCK)), "no tripwire");
			int walker = server.computeOnServer(s -> {
				Hound h = Monsters.HOUND.create(level(s), EntitySpawnReason.COMMAND);
				h.snapTo(wire.getX() + 0.5, wire.getY(), wire.getZ() + 0.5, 0, 0);
				h.setNoAi(true);
				level(s).addFreshEntity(h);
				return h.getId();
			});
			context.waitTicks(10);
			check(server.computeOnServer(s -> FieldKit.tripped(level(s), wire)), "the tripwire did not go off");
			server.runCommand("kill @e[type=!player]");

			// A barricade holds a hound for a few seconds, then gives.
			BlockPos here2 = stand(server);
			BlockPos plank = here2.south(2);
			server.runOnServer(s -> useOn(player(s), new ItemStack(FieldKit.BARRICADE_PLANKS), plank.below(), Direction.UP));
			check(server.computeOnServer(s -> level(s).getBlockState(plank).is(FieldKit.BARRICADE)), "no barricade");
			// The hound shut in a stone cell whose only way out is the barricade (as behind a barricaded door).
			server.runOnServer(s -> {
				BlockPos cell = plank.south();
				for (int dy = 0; dy < 3; dy++) {
					for (BlockPos w : new BlockPos[] {cell.east(), cell.west(), cell.south(), plank.east(), plank.west()}) {
						level(s).setBlockAndUpdate(w.above(dy), Blocks.STONE.defaultBlockState());
					}
					if (dy > 0) level(s).setBlockAndUpdate(plank.above(dy), Blocks.STONE.defaultBlockState());
				}
				level(s).setBlockAndUpdate(cell.above(2), Blocks.STONE.defaultBlockState());
			});
			int batterer = spawnHoundAt(server, plank.south(), true);
			server.waitFor(s -> !level(s).getBlockState(plank).is(FieldKit.BARRICADE), 400);
			server.runCommand("kill @e[type=!player]");

			// The camera: a creature, close, is worth something.
			BlockPos here3 = stand(server);
			BlockPos front = here3.north(3);
			server.runOnServer(s -> {
				// A hound, held still (a smiler keeps to the dark and drifts off in a lit hall).
				Hound subject = Monsters.HOUND.create(level(s), EntitySpawnReason.COMMAND);
				subject.snapTo(front.getX() + 0.5, front.getY(), front.getZ() + 0.5, 0, 0);
				subject.setNoAi(true);
				level(s).addFreshEntity(subject);
			});
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 180 22");
			context.waitTicks(5);
			server.runCommand("clear @a");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FieldKit.CAMERA));
				p.gameMode.useItem(p, p.level(), p.getMainHandItem(), InteractionHand.MAIN_HAND);
			});
			long photo = server.computeOnServer(s -> {
				var inventory = player(s).getInventory();
				for (int i = 0; i < inventory.getContainerSize(); i++) {
					if (inventory.getItem(i).is(FieldKit.PHOTO)) return Evidence.value(s, inventory.getItem(i));
				}
				return -1L;
			});
			check(photo >= 60, "a close photo of a hound is worth " + photo + "; " + server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				StringBuilder b = new StringBuilder("player " + p.position() + " yaw " + p.getYRot() + " pitch " + p.getXRot() + "; near:");
				for (var e : level(s).getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, p.getBoundingBox().inflate(16), e -> e != p)) {
					b.append(' ').append(e.getType().toShortString()).append('@').append(e.blockPosition()).append(p.hasLineOfSight(e) ? " seen" : " hidden");
				}
				return b.toString();
			}));
			context.takeScreenshot("field_camera");

			// The picture: sent up by the client, kept in the save, looked at, put in the album and taken out again.
			java.util.UUID picture = server.computeOnServer(s -> Photos.id(carried(player(s), FieldKit.PHOTO)));
			check(picture != null, "the photo has no picture id");
			server.waitFor(s -> Photos.load(s, picture) != null, 200);
			check(context.computeOnClient(client -> !client.gui.hud.isHidden()), "the shutter left the HUD off");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				// Copies of it, to fill more than one spread of the book.
				for (int n = 0; n < 8; n++) p.getInventory().add(carried(p, FieldKit.PHOTO).copy());
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FieldKit.ALBUM));
				p.setShiftKeyDown(true);
				p.gameMode.useItem(p, p.level(), p.getMainHandItem(), InteractionHand.MAIN_HAND);
				p.setShiftKeyDown(false);
			});
			check(server.computeOnServer(s -> Photos.contents(player(s).getMainHandItem()).size() == 9 && carried(player(s), FieldKit.PHOTO).isEmpty()),
					"the album did not take the photo");
			server.runOnServer(s -> player(s).gameMode.useItem(player(s), player(s).level(), player(s).getMainHandItem(), InteractionHand.MAIN_HAND));
			context.waitFor(client -> client.gui.screen() instanceof PhotoScreen screen && screen.showingPicture(), 200);
			context.waitTicks(5);
			context.takeScreenshot("field_album");
			context.clickScreenButton("photo.noclip_backrooms.next_page");
			context.waitTicks(2);
			context.takeScreenshot("field_album_page2");
			context.clickScreenButton("photo.noclip_backrooms.prev_page");
			// A real click on the print holds it up; "take out" gives the photo back and leaves the book open.
			double[] print = context.computeOnClient(client -> {
				int[] at = ((PhotoScreen) client.gui.screen()).firstPrint();
				double gui = client.getWindow().getGuiScale();
				return new double[] {at[0] * gui, at[1] * gui};
			});
			context.getInput().setCursorPos(print[0], print[1]);
			context.getInput().pressMouse(0);
			context.waitTicks(5);
			check(context.computeOnClient(client -> ((PhotoScreen) client.gui.screen()).holdingUp()), "clicking a print did not hold it up");
			context.takeScreenshot("field_album_detail");
			context.clickScreenButton("photo.noclip_backrooms.take");
			context.waitFor(client -> client.gui.screen() instanceof PhotoScreen screen && !screen.holdingUp(), 100);
			context.runOnClient(client -> client.gui.screen().onClose());
			check(server.computeOnServer(s -> Photos.contents(player(s).getMainHandItem()).size() == 8 && !carried(player(s), FieldKit.PHOTO).isEmpty()),
					"the photo did not come back out of the album");
			server.runCommand("kill @e[type=!player]");
			spawnBuyer(server);
			long before = server.computeOnServer(s -> Quota.data(s).delivered());
			check(server.computeOnServer(s -> sell(s, FieldKit.PHOTO)) == Shops.Outcome.DONE, "the buyer would not take the photo");
			check(server.computeOnServer(s -> Quota.data(s).delivered()) >= before + Math.min(photo, server.computeOnServer(s -> Quota.data(s).required()) - before),
					"the photo did not count toward the quota");
			check(server.computeOnServer(s -> Photos.load(s, picture) == null), "the sold photo's picture is still there");

			// The mapping beacon: refused near the landing, and gives a tape far from it.
			server.runOnServer(s -> player(s).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FieldKit.BEACON)));
			BlockPos near = stand(server).north();
			server.runOnServer(s -> useOn(player(s), player(s).getMainHandItem(), near.below(), Direction.UP));
			check(!server.computeOnServer(s -> level(s).getBlockState(near).is(FieldKit.BEACON_BLOCK)), "a beacon went down near the landing");
			BlockPos entry = server.computeOnServer(s -> player(s).getAttached(Trips.ENTRY).pos());
			server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s " + (entry.getX() + 380.5) + " " + entry.getY() + " " + (entry.getZ() + 0.5));
			game.getConnection().waitForChunksDownload();
			context.waitTicks(10);
			BlockPos far = stand(server).north();
			server.runOnServer(s -> {
				player(s).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FieldKit.BEACON));
				useOn(player(s), player(s).getMainHandItem(), far.below(), Direction.UP);
			});
			check(server.computeOnServer(s -> Evidence.scanning(level(s), far)), "the beacon did not start scanning far from the landing");
			context.waitTicks(30);
			context.takeScreenshot("field_beacon");
			server.runOnServer(Evidence::finishScans);
			server.waitFor(s -> !level(s).getEntitiesOfClass(ItemEntity.class, new AABB(far).inflate(3), e -> e.getItem().is(FieldKit.TAPE)).isEmpty(), 60);
			server.runOnServer(s -> {
				for (ItemEntity e : level(s).getEntitiesOfClass(ItemEntity.class, new AABB(far).inflate(3), e -> e.getItem().is(FieldKit.TAPE))) {
					player(s).getInventory().add(e.getItem().copy());
					e.discard();
				}
			});
			spawnBuyer(server);
			check(server.computeOnServer(s -> sell(s, FieldKit.TAPE)) == Shops.Outcome.DONE, "the buyer would not take the tape");
			check(server.computeOnServer(s -> Quota.data(s).mapped("0")), "selling the tape did not map Level 0");

			// The weapons' horror balance: the end-game blade does little to a hound; the plain pipe nearly all.
			float heavy = hit(server, BackroomsItems.WEST_VOID_GREATSWORD), light = hit(server, BackroomsItems.BLUNT_RUSTY_PIPE);
			// Armour and the like take the same share of both, so compare the two: 55% against 90%.
			check(light > 0 && Math.abs(heavy / light - 0.55F / 0.9F) < 0.03F, "balance: a tier 4 blade did " + heavy + " and a pipe " + light + " of 10");

			// The institute's machines: load the recycler + research terminal (yellow/gray/purple packs), take a trip two
			// floors deep, verify dynamic Red Mist enclave + Red Room Altar photo + Mimic Figure Bio-Scanner expose + Level 1
			// Blackout Moment photo + Research unlock + Dual Endings (A & B).
			// 1. Dynamic Red Mist room infestation & Red Room Altar scene photo
			server.runCommand("execute as @a run backrooms anomaly set red_mist");
			BlockPos enclaveSpot = stand(server);
			BlockPos altarAt = server.computeOnServer(s -> {
				ServerLevel sl = level(s);
				BlockPos at = Anomalies.infestRedRoomAt(sl, enclaveSpot.north(3));
				if (at == null) at = Anomalies.infestRedRoomAt(sl, enclaveSpot);
				if (at == null) at = Anomalies.infestRedRoomNear(sl, enclaveSpot);
				return at;
			});
			check(altarAt != null, "red mist failed to condense a Red Room enclave");
			check(server.computeOnServer(s -> level(s).getBlockState(altarAt).is(BackroomsProps.RED_ANOMALY_CLUSTER)), "no red anomaly cluster in red mist enclave");
			server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s "
					+ (altarAt.getX() + 0.5) + " " + altarAt.getY() + " " + (altarAt.getZ() + 3.5) + " 180 12");
			context.waitTicks(5);
			server.runCommand("clear @a");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FieldKit.CAMERA));
				p.gameMode.useItem(p, p.level(), p.getMainHandItem(), InteractionHand.MAIN_HAND);
			});
			long altarPhoto = server.computeOnServer(s -> {
				var inv = player(s).getInventory();
				for (int i = 0; i < inv.getContainerSize(); i++) {
					if (inv.getItem(i).is(FieldKit.PHOTO)) return Evidence.value(s, inv.getItem(i));
				}
				return -1L;
			});
			check(altarPhoto >= 110, "red room altar scene photo was worth only " + altarPhoto);
			context.takeScreenshot("field_red_mist_enclave");

			// 2. Disguised human-figure Skin-Stealer + Bio-Anomaly Scanner expose
			server.runCommand("execute as @a run backrooms anomaly set mimics");
			server.runCommand("kill @e[type=!player]");
			BlockPos figAt = enclaveSpot.south(5);
			server.runOnServer(s -> {
				ServerLevel sl = level(s);
				sl.setBlockAndUpdate(figAt, Blocks.AIR.defaultBlockState());
				sl.setBlockAndUpdate(figAt.above(), Blocks.AIR.defaultBlockState());
				sl.setBlockAndUpdate(figAt.below(), Blocks.STONE.defaultBlockState());
				Person fig = Anomalies.spawnMimicFigure(sl, figAt);
				check(fig != null && fig.entityTags().contains(Anomalies.MIMIC_FIGURE_TAG), "failed to spawn mimic figure");
			});
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FieldKit.BIO_SCANNER));
				p.gameMode.useItem(p, p.level(), p.getMainHandItem(), InteractionHand.MAIN_HAND);
			});
			check(server.computeOnServer(s -> level(s).getEntitiesOfClass(Person.class, new AABB(figAt).inflate(4),
					p -> p.entityTags().contains(Anomalies.MIMIC_FIGURE_TAG)).isEmpty()), "bio-scanner did not remove mimic figure");
			check(server.computeOnServer(s -> !level(s).getEntitiesOfClass(SkinStealer.class, new AABB(figAt).inflate(6),
					st -> st.hasEffect(MobEffects.GLOWING) && st.hasEffect(MobEffects.SLOWNESS)).isEmpty()), "bio-scanner did not expose & stun Skin-Stealer");
			server.runCommand("kill @e[type=!player]");

			// 3. Research pack locking before unlock: 9x9 wretch_cure cannot craft yet in survival
			server.runOnServer(s -> {
				Research.reset(s);
				ServerPlayer p = player(s);
				var cureRecipe = BackroomsRecipeRegistry.byId(Identifier.parse("noclip_backrooms:ultimate_wretch_cure"));
				check(cureRecipe != null && !Research.canCraft(p, cureRecipe), "wretch_cure should be locked before Purple research");
				var menu = new BackroomsStationMenu(BackroomsCrafting.menuType(StationType.ULTIMATE_SPIDERWEB), 0, p.getInventory(), StationType.ULTIMATE_SPIDERWEB);
				int gw = StationType.ULTIMATE_SPIDERWEB.gridWidth();
				for (int y = 0; y < cureRecipe.patternHeight(); y++) {
					for (int x = 0; x < cureRecipe.patternWidth(); x++) {
						var cell = cureRecipe.cellAt(x, y);
						if (cell != null) menu.inputGrid().setItem((cureRecipe.centeredOffsetY() + y) * gw + cureRecipe.centeredOffsetX() + x,
								new ItemStack(cell.resolveItem(), cell.count()));
					}
				}
				check(menu.getSlot(menu.outputSlotStart()).getItem().isEmpty(), "locked 9x9 cure recipe still produced an output");
			});

			// 4. Load recycler + research terminal (yellow, gray, purple packs), travel to Level 1, snap a Blackout Moment photo, then extract!
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.setAttached(Processing.LOADED, Map.of(
						"scrap_recycler", List.of(new ItemStack(item("noclip_backrooms:scrap_metal"), 12)),
						"anomaly_research_terminal", List.of(
								new ItemStack(BackroomsItems.SCIENCE_PACK_YELLOW, 1),
								new ItemStack(BackroomsItems.SCIENCE_PACK_GRAY, 1),
								new ItemStack(BackroomsItems.SCIENCE_PACK_PURPLE, 1))));
				LevelExits.use(p, "1");
			});
			server.waitFor(s -> player(s).level().dimension() != Level0.LEVEL_0, 400);
			// Wait past the camera's 40-tick cooldown from the red room altar shot, then snap a Level 1 Blackout Moment photo
			context.waitTicks(45);
			server.runCommand("kill @e[type=!player]");
			server.runCommand("clear @a");
			server.runOnServer(s -> {
				Blackouts.startNow();
				ServerPlayer p = player(s);
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FieldKit.CAMERA));
				p.gameMode.useItem(p, p.level(), p.getMainHandItem(), InteractionHand.MAIN_HAND);
				Blackouts.endNow();
			});
			long blackoutPhoto = server.computeOnServer(s -> {
				var inv = player(s).getInventory();
				for (int i = 0; i < inv.getContainerSize(); i++) {
					if (inv.getItem(i).is(FieldKit.PHOTO)) return Evidence.value(s, inv.getItem(i));
				}
				return -1L;
			});
			check(blackoutPhoto >= 85, "Level 1 blackout moment photo was worth only " + blackoutPhoto);

			server.runOnServer(s -> Trips.settle(player(s), LevelData.RespawnData.of(player(s).level().dimension(), player(s).blockPosition(), 0, 0)));
			check(server.computeOnServer(s -> Trips.extract(player(s))), "no pickup");
			server.runCommand("execute as @a run backrooms extract now");
			server.waitFor(s -> player(s).level().dimension() == Home.FRONTROOMS, 200);
			context.waitFor(client -> client.gui.screen() instanceof MissionReportScreen, 200);
			context.takeScreenshot("field_report_with_processing");
			context.runOnClient(client -> client.gui.screen().onClose());
			check(server.computeOnServer(s -> Report.last(player(s)).notes().size()) >= 2, "the report has no word of recycler & research unlocks");
			check(server.computeOnServer(s -> Lockers.count(player(s), Items.IRON_INGOT)) >= 4, "the recycler made no iron");
			check(server.computeOnServer(s -> Lockers.count(player(s), item("noclip_backrooms:battery_cell"))) >= 1, "two floors deep gave no battery");
			check(server.computeOnServer(s -> Research.isUnlocked(s, Research.Tier.YELLOW)
					&& Research.isUnlocked(s, Research.Tier.GRAY)
					&& Research.isUnlocked(s, Research.Tier.PURPLE)), "research terminal did not unlock yellow/gray/purple tiers");

			// 5. Unlocked 9x9 wretch_cure now crafts cleanly; Ending A (Cure & Contract Release) + Ending B (B2 Truth & True Escape)
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				var cureRecipe = BackroomsRecipeRegistry.byId(Identifier.parse("noclip_backrooms:ultimate_wretch_cure"));
				check(Research.canCraft(p, cureRecipe), "wretch_cure should be unlocked after Purple research");
				var menu = new BackroomsStationMenu(BackroomsCrafting.menuType(StationType.ULTIMATE_SPIDERWEB), 0, p.getInventory(), StationType.ULTIMATE_SPIDERWEB);
				int gw = StationType.ULTIMATE_SPIDERWEB.gridWidth();
				for (int y = 0; y < cureRecipe.patternHeight(); y++) {
					for (int x = 0; x < cureRecipe.patternWidth(); x++) {
						var cell = cureRecipe.cellAt(x, y);
						if (cell != null) menu.inputGrid().setItem((cureRecipe.centeredOffsetY() + y) * gw + cureRecipe.centeredOffsetX() + x,
								new ItemStack(cell.resolveItem(), cell.count()));
					}
				}
				check(menu.getSlot(menu.outputSlotStart()).getItem().is(BackroomsItems.WRETCH_CURE), "unlocked 9x9 wretch_cure did not appear in result slot");

				// Drink Wretch Cure -> cured and healed to full; max health stays halved (CLAUDE.md, 2026-11-28).
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BackroomsItems.WRETCH_CURE));
				p.gameMode.useItem(p, p.level(), p.getMainHandItem(), InteractionHand.MAIN_HAND);
				check(Endings.isCured(p), "Wretch Cure did not mark player cured");
				check(p.getHealth() >= p.getMaxHealth() - 0.01F && p.getMaxHealth() < 20.0F,
						"Wretch Cure should heal to full and keep max health halved: " + p.getHealth() + " / " + p.getMaxHealth());

				// Ending A: grant clearance 5 & call contract release -> real Overworld
				while (Quota.clearance(s) < Quota.DEEP_CLEARANCE) {
					Quota.data(s).items().forEach((id, need) -> Quota.data(s).handed().put(id, need));
					Quota.addPoints(p, Quota.data(s).required() + Quota.data(s).debt() + 100);
				}
				check(Endings.tryContractEnding(p, false), "Ending A contract release failed");
			});
			server.waitFor(s -> player(s).level().dimension() == Level.OVERWORLD, 200);
			check(server.computeOnServer(s -> Endings.isFree(player(s)) && Endings.ENDING_CONTRACT.equals(Endings.of(player(s)).ending())),
					"Ending A did not grant freedom in Overworld");

			// Ending B: reset ending, collect all 6 B2 archive files, override B1 transport pad with T3 hacker -> real Overworld
			server.runCommand("execute as @a run backrooms ending reset");
			server.runOnServer(s -> dev.noclip.backrooms.frontrooms.Institute.receive(player(s)));
			server.waitFor(s -> player(s).level().dimension() == Home.FRONTROOMS, 200);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				for (int page = 0; page < Endings.TOTAL_ARCHIVE_FILES; page++) Endings.recordFile(p, page);
				check(Endings.truthKnown(p), "reading all 6 archive files did not unlock truth");
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BackroomsItems.KEYPAD_HACKER_T3));
				check(Endings.tryTrueEscape(p, true), "Ending B transport pad override failed");
			});
			server.waitFor(s -> player(s).level().dimension() == Level.OVERWORLD, 200);
			check(server.computeOnServer(s -> Endings.isFree(player(s)) && Endings.ENDING_TRUE_ESCAPE.equals(Endings.of(player(s)).ending())),
					"Ending B did not complete True Escape into Overworld");
		}
	}

	/** Damage a hound takes from a hit of 10 with this weapon in hand. */
	private static float hit(TestServerContext server, Item weapon) {
		return server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(weapon));
			Hound h = Monsters.HOUND.create(level(s), EntitySpawnReason.COMMAND);
			h.snapTo(p.getX() + 2, p.getY(), p.getZ(), 0, 0);
			h.setNoAi(true);
			level(s).addFreshEntity(h);
			float before = h.getHealth();
			h.invulnerableTime = 0;
			h.hurtServer(level(s), level(s).damageSources().playerAttack(p), 10.0F);
			float taken = before - h.getHealth();
			h.discard();
			return taken;
		});
	}

	private static Item item(String id) {
		return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
	}

	private static ItemStack carried(ServerPlayer player, net.minecraft.world.item.Item item) {
		var inventory = player.getInventory();
		for (int n = 0; n < inventory.getContainerSize(); n++) if (inventory.getItem(n).is(item)) return inventory.getItem(n);
		return ItemStack.EMPTY;
	}

	private static Shops.Outcome sell(MinecraftServer server, Item item) {
		var offers = Shops.offers(ShopRole.BUYER);
		for (int i = 0; i < offers.size(); i++) {
			if (offers.get(i).fromPlayer() && offers.get(i).goods().is(item)) return Shops.trade(player(server), ShopRole.BUYER, i, 1);
		}
		throw new AssertionError("the buyer does not take " + item);
	}

	private static void spawnBuyer(TestServerContext server) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Person buyer = People.type("trader").create(p.level(), EntitySpawnReason.COMMAND);
			buyer.snapTo(p.getX() + 1, p.getY(), p.getZ() + 1, 0, 0);
			buyer.setNoAi(true);
			p.level().addFreshEntity(buyer);
			buyer.addTag(ShopRole.BUYER.tag());
		});
	}

	private static int spawnHound(TestServerContext server, int dx, int dz, boolean hunt) {
		return server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Hound h = Monsters.HOUND.create(level(s), EntitySpawnReason.COMMAND);
			h.snapTo(p.getX() + dx, p.getY(), p.getZ() + dz, 0, 0);
			level(s).addFreshEntity(h);
			if (hunt) h.setTarget(p);
			return h.getId();
		});
	}

	private static int spawnHoundAt(TestServerContext server, BlockPos at, boolean hunt) {
		return server.computeOnServer(s -> {
			Hound h = Monsters.HOUND.create(level(s), EntitySpawnReason.COMMAND);
			h.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
			level(s).addFreshEntity(h);
			if (hunt) h.setTarget(player(s));
			return h.getId();
		});
	}

	private static Hound houndOf(MinecraftServer server, int id) {
		return level(server).getEntity(id) instanceof Hound h ? h : null;
	}

	/** A clear spot of floor near the player, three blocks around (in Level 0's halls); returns where they stand. */
	private static BlockPos stand(TestServerContext server) {
		return server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = level(s);
			BlockPos start = p.blockPosition();
			for (int r = 0; r < 40; r++) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
						BlockPos at = start.offset(dx, 0, dz);
						if (clear(level, at)) {
							p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
							return at;
						}
					}
				}
			}
			return start;
		});
	}

	private static boolean clear(ServerLevel level, BlockPos at) {
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				BlockPos c = at.offset(dx, 0, dz);
				if (!level.getBlockState(c).isAir() || !level.getBlockState(c.above()).isAir()) return false;
				if (!level.getBlockState(c.below()).isFaceSturdy(level, c.below(), Direction.UP)) return false;
			}
		}
		return true;
	}

	private static int count(ServerLevel level, BlockPos around, net.minecraft.world.level.block.Block block) {
		int n = 0;
		for (BlockPos pos : BlockPos.betweenClosed(around.offset(-6, -1, -6), around.offset(6, 1, 6))) if (level.getBlockState(pos).is(block)) n++;
		return n;
	}

	private static void useOn(ServerPlayer player, ItemStack stack, BlockPos target, Direction face) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		Vec3 hit = Vec3.atCenterOf(target).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
		player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, target, false));
	}

	private static ServerLevel level(MinecraftServer server) {
		return (ServerLevel) player(server).level();
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(boolean ok, String message) {
		if (!ok) throw new AssertionError(message);
	}
}
