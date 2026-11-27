package dev.noclip.backrooms.frontrooms;

import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.LevelExits;
import dev.noclip.backrooms.core.ending.Endings;
import dev.noclip.backrooms.core.people.Person;
import dev.noclip.backrooms.monsters.Monsters;
import dev.noclip.backrooms.monsters.faceling.Faceling;
import dev.noclip.backrooms.monsters.smiler.Smiler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The nights of Level 398, "a cruel prank" (docs/project/1-gameplay/README.md, "前室的夜晚：398"; after 狐霖's
 * Frontrooms): once someone in the Frontrooms knows the truth of the B2 files, the town's nights are 398's.
 * <ul>
 * <li>a title at nightfall; the picnic's people and the shopkeeper are gone, and people without faces (the Faceling)
 * stand on the lawns;</li>
 * <li>the nightmare: the friend's Smiler, stronger, one for each person in the town, until dawn;</li>
 * <li>the two bad choices of 398, either of which is the bad ending "never left": waiting under a streetlight for the
 * morning, or going into a house and shutting the door. You wake in Level 0.</li>
 * </ul>
 * At dawn everything of the night is gone again.
 */
public final class Nights398 {
	static final String TAG = "noclip_backrooms_398";
	/** How long under a lamp, and how long behind a shut door, before the night has you (ticks). */
	static int lampTicks = 60 * 20, houseTicks = 10 * 20;
	private static final Map<UUID, Integer> UNDER_LAMP = new HashMap<>();
	private static final Map<UUID, Integer> BEHIND_DOOR = new HashMap<>();
	/** Those the night has taken, and the seconds left before they wake in Level 0. */
	private static final Map<UUID, Integer> TAKEN = new HashMap<>();
	private static final AABB NEAR_TOWN = new AABB(FrontroomsTown.MIN_X - 24, FrontroomsTown.G - 4, FrontroomsTown.MIN_Z - 24,
			FrontroomsTown.MAX_X + 24, FrontroomsTown.G + 20, FrontroomsTown.MAX_Z + 24);

	private Nights398() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 != 0) return;
			ServerLevel level = server.getLevel(Home.FRONTROOMS);
			if (level != null) tick(level);
		});
	}

	/** Whether the Frontrooms' night is 398's: it is dark, and someone there knows the truth. */
	public static boolean active(ServerLevel level) {
		if (!level.isDarkOutside()) return false;
		for (ServerPlayer player : level.players()) if (Endings.truthKnown(player)) return true;
		return false;
	}

	static boolean nearTown(Entity entity) {
		return NEAR_TOWN.contains(entity.position());
	}

	static void tick(ServerLevel level) {
		for (var it = TAKEN.entrySet().iterator(); it.hasNext(); ) {
			var entry = it.next();
			if (entry.getValue() > 1) {
				entry.setValue(entry.getValue() - 1);
				continue;
			}
			it.remove();
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
			if (player != null && player.level().dimension() == Home.FRONTROOMS) LevelExits.fallInto(player, "0");
		}
		if (!active(level)) {
			// Morning: nothing of the night stays.
			for (Entity entity : nightThings(level)) entity.discard();
			UNDER_LAMP.clear();
			BEHIND_DOOR.clear();
			return;
		}
		// The town's own people are not there at night.
		for (Entity entity : level.getEntitiesOfClass(Person.class, NEAR_TOWN, p -> p.entityTags().contains(TownLife.TAG))) entity.discard();
		faceless(level);
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !nearTown(player)) continue;
			if (TownLife.onceToday(player, "398_night")) title(player);
			nightmare(level, player);
			choices(level, player);
		}
	}

	private static List<Entity> nightThings(ServerLevel level) {
		List<Entity> things = new ArrayList<>();
		for (Entity entity : level.getAllEntities()) if (entity.entityTags().contains(TAG)) things.add(entity);
		return things;
	}

	private static void title(ServerPlayer player) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(30, 100, 40));
		player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("title.noclip_backrooms.398.subtitle")
				.withStyle(ChatFormatting.GRAY)));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("title.noclip_backrooms.398")
				.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
	}

	/** People without faces on every front lawn, looking at the street. */
	private static void faceless(ServerLevel level) {
		for (FrontroomsTown.House house : FrontroomsTown.houses()) {
			boolean south = house.doorZ() > FrontroomsTown.MIRROR / 2;
			int x = house.x0(), z = south ? house.doorZ() - 4 : house.doorZ() + 4;
			BlockPos at = new BlockPos(x, FrontroomsTown.G + 1, z);
			if (!level.isLoaded(at)) continue;
			String spot = TAG + "_lawn_" + x + "_" + z;
			if (!level.getEntitiesOfClass(Faceling.class, new AABB(at).inflate(12), f -> f.entityTags().contains(spot)).isEmpty()) continue;
			Faceling faceling = Monsters.FACELING.create(level, EntitySpawnReason.EVENT);
			if (faceling == null) continue;
			float yaw = south ? 180 : 0;
			faceling.snapTo(x + 0.5, at.getY(), z + 0.5, yaw, 0);
			faceling.setYHeadRot(yaw);
			faceling.setHostile(false);
			level.addFreshEntity(faceling);
			faceling.addTag(TAG);
			faceling.addTag(spot);
		}
	}

	/** One nightmare for each person in the town: the Smiler, stronger, coming from a lamp at the edge of their sight. */
	private static void nightmare(ServerLevel level, ServerPlayer player) {
		String mine = TAG + "_nightmare_" + player.getUUID();
		if (!level.getEntitiesOfClass(Smiler.class, player.getBoundingBox().inflate(96), s -> s.entityTags().contains(mine)).isEmpty()) return;
		List<int[]> far = new ArrayList<>();
		for (int[] lamp : FrontroomsTown.lamps()) {
			double d = Math.hypot(lamp[0] + 0.5 - player.getX(), lamp[1] + 0.5 - player.getZ());
			if (d > 16 && d < 40) far.add(lamp);
		}
		if (far.isEmpty()) return;
		int[] lamp = far.get(player.getRandom().nextInt(far.size()));
		BlockPos at = new BlockPos(lamp[0] + 1, FrontroomsTown.G + 1, lamp[1]);
		if (!level.isLoaded(at) || !level.getBlockState(at).isAir()) return;
		Smiler smiler = Monsters.SMILER.create(level, EntitySpawnReason.EVENT);
		if (smiler == null) return;
		smiler.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		var health = smiler.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) health.setBaseValue(health.getBaseValue() * 2);
		var speed = smiler.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) speed.setBaseValue(speed.getBaseValue() * 1.15);
		smiler.setHealth(smiler.getMaxHealth());
		smiler.setTarget(player);
		level.addFreshEntity(smiler);
		smiler.addTag(TAG);
		smiler.addTag(mine);
		level.playSound(null, at, SoundEvents.AMBIENT_CAVE.value(), SoundSource.HOSTILE, 1.0F, 0.6F);
	}

	/** The two choices 398 punishes: waiting under a lamp for morning, shutting yourself into a house. */
	private static void choices(ServerLevel level, ServerPlayer player) {
		UUID id = player.getUUID();
		boolean underLamp = false;
		for (int[] lamp : FrontroomsTown.lamps()) {
			if (Math.abs(player.getX() - (lamp[0] + 0.5)) < 1.8 && Math.abs(player.getZ() - (lamp[1] + 0.5)) < 1.8) underLamp = true;
		}
		int lamp = underLamp ? UNDER_LAMP.merge(id, 20, Integer::sum) : 0;
		if (!underLamp) UNDER_LAMP.remove(id);
		if (lamp == 20 * 20) player.sendSystemMessage(Component.translatable("message.noclip_backrooms.398.lamp").withStyle(ChatFormatting.GOLD));
		if (lamp >= lampTicks) {
			neverLeft(player);
			return;
		}
		boolean shutIn = false;
		for (FrontroomsTown.House house : FrontroomsTown.houses()) {
			if (!house.inside(player.getX(), player.getY(), player.getZ())) continue;
			BlockState door = level.getBlockState(new BlockPos(house.doorX(), FrontroomsTown.G + 1, house.doorZ()));
			shutIn = door.getBlock() instanceof DoorBlock && !door.getValue(DoorBlock.OPEN);
		}
		Integer before = BEHIND_DOOR.get(id);
		int shut = shutIn ? BEHIND_DOOR.merge(id, 20, Integer::sum) : 0;
		if (!shutIn) BEHIND_DOOR.remove(id);
		if (shutIn && before == null) player.sendSystemMessage(Component.translatable("message.noclip_backrooms.398.house").withStyle(ChatFormatting.GOLD));
		if (shut >= houseTicks) neverLeft(player);
	}

	/** The bad ending of 398, "never left": the screen goes, and you wake in Level 0, under its lights. */
	public static void neverLeft(ServerPlayer player) {
		UNDER_LAMP.remove(player.getUUID());
		BEHIND_DOOR.remove(player.getUUID());
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0, false, false));
		player.connection.send(new ClientboundSetTitlesAnimationPacket(20, 100, 40));
		player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("ending.noclip_backrooms.never_left.subtitle")
				.withStyle(ChatFormatting.GRAY)));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("ending.noclip_backrooms.never_left.title")
				.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.398.never_left").withStyle(ChatFormatting.DARK_RED));
		TAKEN.put(player.getUUID(), 3);
	}

	/** Whether the night has taken someone and is about to wake them in Level 0. */
	public static boolean taken(ServerPlayer player) {
		return TAKEN.containsKey(player.getUUID());
	}
}
