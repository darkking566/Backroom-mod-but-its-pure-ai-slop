package dev.noclip.backrooms.frontrooms;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.guild.Guilds;
import dev.noclip.backrooms.core.people.People;
import dev.noclip.backrooms.core.people.Person;
import dev.noclip.backrooms.core.quota.Quota;
import dev.noclip.backrooms.core.quota.QuotaMath;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.core.trade.ShopRole;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

/**
 * The staff town as it lives (docs/project/1-gameplay/README.md, "前室的夜晚：398"), after 狐霖's Frontrooms:
 * <ul>
 * <li>a mailbox: leafing through the stack of letters gives one letter from home (and now and then a coin), once a day;</li>
 * <li>the picnic in the park: sitting down with them fills you and steadies you, once a day;</li>
 * <li>the street clock tells the day of the cycle, and runs slow;</li>
 * <li>its people: three at the picnic, and the corner store's shopkeeper, who cannot sleep (the supplier's goods).</li>
 * </ul>
 * The nights of Level 398 come later (the same section of the plan).
 */
public final class TownLife {
	static final String TAG = "noclip_backrooms_town";
	private static final String PICNIC_TAG = "noclip_backrooms_town_picnic";
	/** The letters from home (item.noclip_backrooms.home_letter.text.N). */
	public static final int LETTERS = 12;
	public static final Item HOME_LETTER = registerItem("home_letter");
	private static final AABB TOWN = new AABB(FrontroomsTown.MIN_X, FrontroomsTown.G, FrontroomsTown.MIN_Z,
			FrontroomsTown.MAX_X + 1, FrontroomsTown.G + 12, FrontroomsTown.MAX_Z + 1);
	private static final String[] PICNIC_LOOKS = {"colonist", "trader", "aiko"};
	private static final float[] PICNIC_YAWS = {-90, 90, 180};

	private TownLife() {
	}

	private static Item registerItem(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BackroomsMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(16)));
	}

	static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || level.dimension() != Home.FRONTROOMS || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}
			Block block = level.getBlockState(hit.getBlockPos()).getBlock();
			String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
			BlockPos at = hit.getBlockPos();
			return switch (id) {
				case "town_mailbox" -> {
					mailbox(serverPlayer);
					yield InteractionResult.SUCCESS;
				}
				case "town_picnic_table" -> {
					picnic(serverPlayer, (ServerLevel) level, at);
					yield InteractionResult.SUCCESS;
				}
				case "town_street_clock" -> {
					clock(serverPlayer);
					yield InteractionResult.SUCCESS;
				}
				default -> {
					// A picnic table or clock is several blocks: the other blocks answer for it.
					if (block == dev.noclip.backrooms.props.BackroomsProps.PROP_PART
							&& level.getBlockEntity(at) instanceof dev.noclip.backrooms.props.PropBlockEntity part) {
						String master = BuiltInRegistries.BLOCK.getKey(level.getBlockState(part.masterPos()).getBlock()).getPath();
						if (master.equals("town_picnic_table")) {
							picnic(serverPlayer, (ServerLevel) level, part.masterPos());
							yield InteractionResult.SUCCESS;
						}
						if (master.equals("town_street_clock")) {
							clock(serverPlayer);
							yield InteractionResult.SUCCESS;
						}
					}
					yield InteractionResult.PASS;
				}
			};
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 40 != 0) return;
			ServerLevel level = server.getLevel(Home.FRONTROOMS);
			if (level != null) people(level);
		});
	}

	/** Which day it is in the Frontrooms (one a day: letters, the picnic). */
	private static long today(ServerPlayer player) {
		return player.level().getGameTime() / 24000L;
	}

	/** Whether the player has already done a thing today; marks it done if not. Kept as a tag, so it lasts. */
	static boolean onceToday(ServerPlayer player, String what) {
		String prefix = "noclip_backrooms_" + what + "_";
		String tag = prefix + today(player);
		if (player.entityTags().contains(tag)) return false;
		for (String old : List.copyOf(player.entityTags())) if (old.startsWith(prefix)) player.removeTag(old);
		player.addTag(tag);
		return true;
	}

	/** "把整疊信都翻一遍": one letter from home (and now and then a coin), once a day. */
	static void mailbox(ServerPlayer player) {
		if (!onceToday(player, "mail")) {
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.town.mailbox_empty").withStyle(ChatFormatting.GRAY));
			return;
		}
		int which = player.getRandom().nextInt(LETTERS);
		ItemStack letter = new ItemStack(HOME_LETTER);
		letter.set(DataComponents.LORE, new ItemLore(List.of(
				Component.translatable("item.noclip_backrooms.home_letter.text." + which).withStyle(ChatFormatting.GRAY))));
		if (!player.getInventory().add(letter)) player.drop(letter, false);
		if (player.getRandom().nextBoolean()) {
			Item coin = BuiltInRegistries.ITEM.getValue(BackroomsMod.id("lost_coin"));
			ItemStack stack = new ItemStack(coin);
			if (!player.getInventory().add(stack)) player.drop(stack, false);
		}
		player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.town.mailbox").withStyle(ChatFormatting.YELLOW));
	}

	/** "過去一起吃": the picnic's people make room; once a day it fills you and steadies you. */
	static void picnic(ServerPlayer player, ServerLevel level, BlockPos table) {
		int around = level.getEntitiesOfClass(Person.class, new AABB(table).inflate(6), p -> p.entityTags().contains(PICNIC_TAG)).size();
		if (around < 2) {
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.town.picnic_alone").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!onceToday(player, "picnic")) {
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.town.picnic_done").withStyle(ChatFormatting.GRAY));
			return;
		}
		player.getFoodData().eat(6, 0.6F);
		PlayerRpg.restoreSanity(player, 15.0F);
		level.playSound(null, table, SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.town.picnic").withStyle(ChatFormatting.GREEN));
	}

	/** The clock: the day of the cycle, and that the days are getting slower. */
	static void clock(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		int day = QuotaMath.day(Quota.data(server).homeTicks());
		String key = Quota.rejection(server) > 0 ? "message.noclip_backrooms.town.clock_slow" : "message.noclip_backrooms.town.clock";
		player.sendSystemMessage(Component.translatable(key, day).withStyle(ChatFormatting.GOLD));
	}

	/** Keeps the town's people where they belong, once their part of the town and its entities are loaded. */
	static void people(ServerLevel level) {
		for (int i = 0; i < FrontroomsTown.PICNIC.length; i++) {
			int[] spot = FrontroomsTown.PICNIC[i];
			keep(level, "noclip_backrooms_town_picnic_" + i, PICNIC_LOOKS[i], spot, PICNIC_YAWS[i], null, true);
		}
		keep(level, "noclip_backrooms_town_shopkeeper", ShopRole.SUPPLIER.look(), FrontroomsTown.SHOPKEEPER, 0, ShopRole.SUPPLIER, false);
	}

	private static void keep(ServerLevel level, String tag, String look, int[] spot, float yaw, ShopRole role, boolean picnic) {
		BlockPos at = new BlockPos(spot[0], FrontroomsTown.G + 1, spot[1]);
		if (!level.isLoaded(at) || !level.areEntitiesLoaded(ChunkPos.pack(at))) return;
		if (!level.getEntitiesOfClass(Person.class, TOWN, p -> p.entityTags().contains(tag)).isEmpty()) return;
		Person person = People.type(look).create(level, EntitySpawnReason.STRUCTURE);
		if (person == null) return;
		person.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0);
		person.setYHeadRot(yaw);
		person.yBodyRot = yaw;
		person.setNoAi(true);
		person.setInvulnerable(true);
		person.setPersistenceRequired();
		person.addTag(tag);
		person.addTag(TAG);
		if (picnic) person.addTag(PICNIC_TAG);
		if (role != null) {
			person.addTag(role.tag());
			person.addTag(Guilds.MEMBER_TAG);
			person.setCustomName(Component.translatable("entity.noclip_backrooms.town_shopkeeper"));
			person.setCustomNameVisible(true);
		}
		level.addFreshEntity(person);
	}
}
