package dev.noclip.backrooms.guild;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.economy.Economy;
import dev.noclip.backrooms.core.economy.Wallet;
import dev.noclip.backrooms.core.guild.GuildPalette;
import dev.noclip.backrooms.core.guild.GuildPiece;
import dev.noclip.backrooms.core.guild.Guilds;
import dev.noclip.backrooms.core.people.Person;
import dev.noclip.backrooms.core.trade.ShopOffer;
import dev.noclip.backrooms.core.trade.ShopRole;
import dev.noclip.backrooms.core.trade.ShopScreen;
import dev.noclip.backrooms.core.trade.Shops;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * The overworld base (docs/guild.md) in a real world with villages: finds a guild, checks a village starts at the same
 * place and the guild stands outside it, walks the path, checks the hall (door, signal fire, sign text, the four
 * people), opens the buyer's shop by right-clicking, and trades: selling, banking coins, buying, being too far, short
 * of credits or of a material, and keeping the account through death. Run alone with
 * gradlew runClientGameTest -Plevels=guild
 */
public final class GuildClientTest implements FabricClientGameTest {
	/** /tp ... facing aims from the feet; aiming this much lower puts the eyes' line on the target. */
	private static final double EYE = 1.62;

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
			server.runCommand("gamemode creative @a");

			// Where the nearest guild starts (the chunk its village starts in), and its hall.
			BlockPos start = server.computeOnServer(s -> s.overworld().findNearestMapStructure(Guilds.GUILDS, BlockPos.ZERO, 100, false));
			check(start != null, "no guild within 100 chunks of spawn");
			BlockPos village = server.computeOnServer(s -> s.overworld().findNearestMapStructure(StructureTags.VILLAGE, start, 2, false));
			check(village != null && village.atY(0).distSqr(start.atY(0)) < 32 * 32, "no village where the guild's village should be: " + start + " / " + village);
			int[] box = server.computeOnServer(s -> {
				StructureStart guild = guildStart(s, start);
				var b = guild.getPieces().getFirst().getBoundingBox();
				return new int[] {b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()};
			});
			double out = Math.max(Math.max(start.getX() - box[3], box[0] - start.getX() - 15), Math.max(start.getZ() - box[5], box[2] - start.getZ() - 15));
			check(out > 72, "the hall stands inside the village's reach: " + out + " blocks out");
			System.out.println("[guild] village start " + start + ", hall " + box[0] + "," + box[1] + "," + box[2] + " .. " + box[3] + "," + box[5]);

			// Stand out front, down the path, and look at the hall.
			BlockPos front = server.computeOnServer(s -> hall(s, start).at(12, 1, -14));
			BlockPos door = server.computeOnServer(s -> hall(s, start).at(12, 1, 1));
			server.runOnServer(s -> {
				player(s).getAbilities().flying = true;
				player(s).onUpdateAbilities();
			});
			server.runCommand(String.format("tp @a %d %d %d facing %d %d %d", front.getX(), front.getY() + 4, front.getZ(), door.getX(), door.getY() + 4, door.getZ()));
			server.waitFor(s -> s.overworld().isLoaded(door) && s.overworld().getBlockState(door).getBlock() instanceof DoorBlock, 600);
			game.getConnection().waitForChunksRender();
			context.waitTicks(40);
			context.takeScreenshot("guild_outside");

			// The hall: door, signal fire, the buyer's sign, the people.
			check(server.computeOnServer(s -> {
				BlockPos fire = hall(s, start).at(21, 12, 17);
				var state = s.overworld().getBlockState(fire);
				return state.is(Blocks.CAMPFIRE) && state.getValue(CampfireBlock.SIGNAL_FIRE);
			}), "no signal fire on the chimney");
			check(server.computeOnServer(s -> s.overworld().getBlockEntity(hall(s, start).at(2, 3, 5)) instanceof SignBlockEntity sign
					&& sign.getFrontText().getMessage(1, false).getContents() instanceof TranslatableContents t
					&& t.getKey().equals("guild.noclip_backrooms.role.buyer")), "the buyer's sign has no text");
			server.waitFor(s -> people(s, start).size() == 4, 400);
			check(server.computeOnServer(s -> people(s, start).stream().map(ShopRole::of).distinct().count()) == 4, "not one of each role in the hall");

			// The path from the door toward the village.
			int paved = server.computeOnServer(s -> {
				int count = 0;
				for (int z = -2; z >= -12; z--) {
					for (int x = 12; x <= 13; x++) {
						BlockPos column = hall(s, start).at(x, 0, z);
						BlockPos top = s.overworld().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below();
						var state = s.overworld().getBlockState(top);
						if (state.is(Blocks.DIRT_PATH) || state.is(Blocks.SMOOTH_SANDSTONE)) count++;
					}
				}
				return count;
			});
			check(paved >= 8, "hardly any path in front of the door: " + paved + " of 22 cells");

			// Inside, at the buyer's counter: right-click opens the shop.
			Vec3 buyer = server.computeOnServer(s -> person(s, start, ShopRole.BUYER).getEyePosition());
			BlockPos stand = server.computeOnServer(s -> hall(s, start).at(7, 1, 5));
			server.runCommand(String.format("tp @a %d.5 %d %d.5 facing %.2f %.2f %.2f", stand.getX(), stand.getY(), stand.getZ(), buyer.x, buyer.y - 0.3 - EYE, buyer.z));
			context.waitTicks(20);
			game.getConnection().waitForChunksRender();
			context.takeScreenshot("guild_inside");
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			context.waitFor(client -> client.gui.screen() instanceof ShopScreen shop && shop.role() == ShopRole.BUYER, 100);

			// Sell four scrap metal through the screen.
			int scrap = index(ShopRole.BUYER, "scrap_metal");
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				Wallet.set(player, 0);
				player.getInventory().clearContent();
				player.getInventory().setItem(9, new ItemStack(item("scrap_metal"), 8)); // not in hand: the coins go there
			});
			context.waitTicks(5);
			context.takeScreenshot("guild_shop_buyer");
			context.runOnClient(client -> ((ShopScreen) client.gui.screen()).press(scrap, 1));
			server.waitFor(s -> Wallet.of(player(s)) == 8, 60);
			check(server.computeOnServer(s -> Shops.count(player(s).getInventory(), new ItemStack(item("scrap_metal")))) == 4, "the buyer did not take four scrap metal");
			context.waitFor(client -> client.player != null && Wallet.of(client.player) == 8, 60);
			context.takeScreenshot("guild_shop_sold");
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);

			// Coins go into the account with a right-click.
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Economy.LOST_COIN, 5));
				player.getMainHandItem().use(player.level(), player, InteractionHand.MAIN_HAND);
			});
			check(server.computeOnServer(s -> Wallet.of(player(s))) == 58, "five coins did not bank 50 credits");
			check(server.computeOnServer(s -> player(s).getMainHandItem().isEmpty()), "the banked coins stayed in hand");

			// At the quartermaster's counter the supplier is across the hall: too far to trade with.
			server.runOnServer(s -> {
				BlockPos at = hall(s, start).at(18, 1, 5);
				player(s).teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			});
			int bandage = index(ShopRole.SUPPLIER, "bandage");
			check(trade(server, ShopRole.SUPPLIER, bandage) == Shops.Outcome.TOO_FAR, "traded with the supplier from across the hall");

			// The quartermaster's pipe pistol needs 220 credits and six scrap metal (four are left from before).
			int pistol = index(ShopRole.QUARTERMASTER, "gun_pipe_pistol");
			check(trade(server, ShopRole.QUARTERMASTER, pistol) == Shops.Outcome.NO_CREDITS, "bought a gun without the credits");
			server.runOnServer(s -> Wallet.add(player(s), 300));
			check(trade(server, ShopRole.QUARTERMASTER, pistol) == Shops.Outcome.NO_GOODS, "bought a gun without enough scrap metal");
			server.runOnServer(s -> player(s).getInventory().add(new ItemStack(item("scrap_metal"), 2)));
			check(trade(server, ShopRole.QUARTERMASTER, pistol) == Shops.Outcome.DONE, "could not buy the gun with credits and scrap");
			check(server.computeOnServer(s -> Wallet.of(player(s))) == 138, "the gun did not cost 220");
			check(server.computeOnServer(s -> Shops.count(player(s).getInventory(), new ItemStack(item("scrap_metal")))) == 0, "the gun did not take the scrap");
			check(server.computeOnServer(s -> Shops.count(player(s).getInventory(), new ItemStack(item("gun_pipe_pistol")))) == 1, "no gun");

			// At the supplier's own counter: two bandages for 20.
			server.runOnServer(s -> {
				BlockPos at = hall(s, start).at(7, 1, 13);
				player(s).teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			});
			check(trade(server, ShopRole.SUPPLIER, bandage) == Shops.Outcome.DONE, "could not buy bandages at the supplier's counter");
			check(server.computeOnServer(s -> Wallet.of(player(s))) == 118, "bandages did not cost 20");
			check(server.computeOnServer(s -> Shops.count(player(s).getInventory(), new ItemStack(item("bandage")))) == 2, "no bandages");

			// Death empties the pockets but not the account.
			server.runCommand("gamemode survival @a");
			server.runCommand("gamerule keep_inventory false");
			server.runCommand("kill @a");
			context.waitFor(client -> client.gui.screen() instanceof net.minecraft.client.gui.screens.DeathScreen, 100);
			context.waitTicks(25);
			context.clickScreenButton("deathScreen.respawn");
			server.waitFor(s -> player(s).isAlive(), 100);
			check(server.computeOnServer(s -> Wallet.of(player(s))) == 118, "the account was lost on death");
			context.waitTicks(10);
			context.getInput().pressKey(GLFW.GLFW_KEY_E);
			context.waitTicks(5);
			context.takeScreenshot("guild_inventory_balance");
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);

			// Each village's style, built with /backrooms guild place, seen from the front.
			server.runCommand("gamemode creative @a");
			for (GuildPalette style : GuildPalette.values()) {
				// On a lawn high in the air, so hills, trees and the sea stay out of the picture.
				int x = start.getX() + 300 + style.ordinal() * 48, z = start.getZ() + 300, y = 180;
				server.runCommand(String.format("tp @a %d %d %d 180 0", x, y + 1, z));
				server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:grass_block", x - 22, y, z - 30, x + 22, y, z + 24));
				GuildPiece built = server.computeOnServer(s -> {
					ServerPlayer player = player(s);
					player.teleportTo(x + 0.5, y + 1, z + 0.5);
					return Guilds.place(player, style);
				});
				BlockPos eye = built.at(12, 7, -15), aim = built.at(12, 6, 9);
				server.runOnServer(s -> {
					player(s).getAbilities().flying = true;
					player(s).onUpdateAbilities();
				});
				server.runCommand(String.format("tp @a %d.5 %d %d.5 facing %d.5 %.2f %d.5", eye.getX(), eye.getY(), eye.getZ(), aim.getX(), aim.getY() - EYE + 1.62, aim.getZ()));
				context.waitTicks(20);
				game.getConnection().waitForChunksRender();
				context.waitTicks(10);
				context.takeScreenshot("guild_style_" + style.name().toLowerCase(java.util.Locale.ROOT));
			}
		}
	}

	private static Structure guild(MinecraftServer server) {
		return server.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(BackroomsMod.id("guild"));
	}

	private static StructureStart guildStart(MinecraftServer server, BlockPos start) {
		ServerLevel level = server.overworld();
		var chunk = level.getChunk(start.getX() >> 4, start.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS, true);
		StructureStart guild = chunk.getStartForStructure(guild(server));
		check(guild != null && guild.isValid(), "no guild started in chunk " + (start.getX() >> 4) + "," + (start.getZ() >> 4));
		return guild;
	}

	private static GuildPiece hall(MinecraftServer server, BlockPos start) {
		return (GuildPiece) guildStart(server, start).getPieces().getFirst();
	}

	private static List<Person> people(MinecraftServer server, BlockPos start) {
		var b = guildStart(server, start).getPieces().getFirst().getBoundingBox();
		AABB area = new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
		return server.overworld().getEntitiesOfClass(Person.class, area, p -> ShopRole.of(p) != null);
	}

	private static Person person(MinecraftServer server, BlockPos start, ShopRole role) {
		return people(server, start).stream().filter(p -> ShopRole.of(p) == role).findFirst().orElseThrow();
	}

	private static Shops.Outcome trade(TestServerContext server, ShopRole role, int index) {
		return server.computeOnServer(s -> Shops.trade(player(s), role, index, 1));
	}

	private static Item item(String name) {
		return BuiltInRegistries.ITEM.getValue(BackroomsMod.id(name));
	}

	/** The place of an item in a merchant's list. */
	private static int index(ShopRole role, String name) {
		List<ShopOffer> offers = Shops.offers(role);
		for (int i = 0; i < offers.size(); i++) {
			if (offers.get(i).goods().is(item(name))) return i;
		}
		throw new AssertionError(role.id() + " has no offer of " + name);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(boolean ok, String message) {
		if (!ok) throw new AssertionError(message);
	}
}
