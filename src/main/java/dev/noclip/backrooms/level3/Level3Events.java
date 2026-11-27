package dev.noclip.backrooms.level3;

import dev.noclip.backrooms.core.floors.FloorDifficulty;
import dev.noclip.backrooms.level3.Level3Plan.Hall;
import dev.noclip.backrooms.level3.Level3Plan.HallKind;
import dev.noclip.backrooms.monsters.Monsters;
import dev.noclip.backrooms.monsters.faceling.Faceling;
import dev.noclip.backrooms.monsters.hound.Hound;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What happens in Level 3 over time, after the Wikidot article: flickering lights that shatter over your head,
 * generators that overload and explode, the paranoia of the dark narrow stretches, hound packs that ambush in the
 * dark, and the sanctums' angels that entities will not stay near.
 */
final class Level3Events {
	static final String HOUND_TAG = "noclip_backrooms_level3_hound";
	private static final int SHATTER_CHANCE = 4, OVERLOAD_CHANCE = 3, OVERLOAD_FUSE = 60, OVERLOAD_COOLDOWN = 20 * 120;
	private static final int GENERATOR_REACH = 7, ANGEL_REACH = 14, SANCTUM_KEEP_CLEAR = 24;
	private static final int PACK_INTERVAL = 100, PACK_CHANCE = 12, DARK = 4, MAX_NEAR = 3, NEAR = 48, NEAREST = 14, FARTHEST = 26;
	private static final Map<UUID, Integer> NEXT_WHISPER = new HashMap<>();
	private static final Map<UUID, Integer> NEXT_CRACKLE = new HashMap<>();
	private static final Map<BlockPos, Integer> OVERLOADS = new HashMap<>();
	private static final Map<BlockPos, Integer> COOLING = new HashMap<>();
	private static int houndsHeldOffUntil;

	private Level3Events() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Level3Events::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			NEXT_WHISPER.clear();
			NEXT_CRACKLE.clear();
			OVERLOADS.clear();
			COOLING.clear();
			houndsHeldOffUntil = 0;
		});
	}

	/** No packs for a while, for tests. */
	static void holdOffHounds(MinecraftServer server, int ticks) {
		houndsHeldOffUntil = server.getTickCount() + ticks;
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level3.LEVEL_3);
		if (level == null) return;
		int now = server.getTickCount();
		long seed = Level3ChunkGenerator.planSeed(level.getChunkSource().randomState());
		RandomSource random = level.getRandom();
		tickOverloads(level, now);
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) continue;
			if (now >= NEXT_CRACKLE.computeIfAbsent(player.getUUID(), k -> now + 100 + random.nextInt(200))) {
				crackle(level, player, random);
				NEXT_CRACKLE.put(player.getUUID(), now + 160 + random.nextInt(260));
			}
			if (player.isCreative() || now % 20 != 0) continue;
			if (random.nextInt(100) < SHATTER_CHANCE) shatterOver(level, player);
			if (random.nextInt(100) < OVERLOAD_CHANCE) overloadNear(level, seed, player, now);
			paranoia(level, seed, player, now, random);
		}
		if (now % 20 == 0) scareOff(level, seed);
		if (now % PACK_INTERVAL == 0 && now >= houndsHeldOffUntil && level.getDifficulty() != Difficulty.PEACEFUL) {
			for (ServerPlayer player : level.players()) {
				if (player.isCreative() || player.isSpectator()) continue;
				if (level.getBrightness(LightLayer.BLOCK, player.blockPosition()) >= DARK) continue;
				if (random.nextInt(100) < FloorDifficulty.scaledChance(player, PACK_CHANCE)) spawnPack(player);
			}
		}
	}

	/** A flickering light right over a player gives out and showers them with glass. */
	static boolean shatterOver(ServerLevel level, ServerPlayer player) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int y = Level3ChunkGenerator.FLOOR_Y + 2; y <= Level3ChunkGenerator.ROOF_Y - 1; y++) {
					BlockState state = level.getBlockState(pos.set(player.getBlockX() + dx, y, player.getBlockZ() + dz));
					if (!state.is(Level3Blocks.FLICKERING_STATION_LIGHT)) continue;
					level.setBlock(pos, Level3Blocks.BROKEN_STATION_LIGHT.defaultBlockState().setValue(Level3AxisBlock.AXIS, state.getValue(Level3AxisBlock.AXIS)), 3);
					level.playSound(null, pos, Level3Sounds.SHATTER, SoundSource.BLOCKS, 1.0F, 1.1F);
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 16, 0.3, 0.1, 0.3, 0.1);
					for (ServerPlayer under : level.players()) {
						if (Math.abs(under.getX() - pos.getX() - 0.5) < 2 && Math.abs(under.getZ() - pos.getZ() - 0.5) < 2 && Level3HazardBlocks.exposed(under)) {
							under.hurtServer(level, level.damageSources().generic(), 2.0F);
						}
					}
					return true;
				}
			}
		}
		return false;
	}

	/** A generator near a player starts to overload; a few seconds later it explodes. */
	static boolean overloadNear(ServerLevel level, long seed, ServerPlayer player, int now) {
		for (Placed placed : hallsNear(seed, player.getBlockX(), player.getBlockZ(), GENERATOR_REACH)) {
			if (placed.hall().kind() != HallKind.GENERATOR) continue;
			BlockPos core = placed.generator();
			if (player.distanceToSqr(Vec3.atCenterOf(core)) > GENERATOR_REACH * GENERATOR_REACH) continue;
			if (OVERLOADS.containsKey(core) || now < COOLING.getOrDefault(core, 0)) continue;
			if (!level.getBlockState(core).is(Level3Blocks.GENERATOR)) continue;
			OVERLOADS.put(core, now + OVERLOAD_FUSE);
			level.playSound(null, core, Level3Sounds.OVERLOAD, SoundSource.BLOCKS, 1.6F, 0.8F);
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level3.overload"));
			return true;
		}
		return false;
	}

	private static void tickOverloads(ServerLevel level, int now) {
		if (OVERLOADS.isEmpty()) return;
		List<BlockPos> due = new ArrayList<>();
		OVERLOADS.forEach((pos, at) -> {
			if (now % 5 == 0) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 8, 0.6, 0.6, 0.6, 0.2);
			if (now >= at) due.add(pos);
		});
		for (BlockPos pos : due) {
			OVERLOADS.remove(pos);
			COOLING.put(pos, now + OVERLOAD_COOLDOWN);
			level.explode(null, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 2.5F, Level.ExplosionInteraction.NONE);
		}
	}

	/** Whether a generator is about to blow, for tests. */
	static boolean overloading() {
		return !OVERLOADS.isEmpty();
	}

	/** In the dark, narrow stretches you start to feel watched, and to hear things. */
	private static void paranoia(ServerLevel level, long seed, ServerPlayer player, int now, RandomSource random) {
		if (!Level3Layout.hotspot(seed, player.getBlockX(), player.getBlockZ())) {
			NEXT_WHISPER.remove(player.getUUID());
			return;
		}
		Integer next = NEXT_WHISPER.get(player.getUUID());
		if (next == null) {
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level3.watched"));
			NEXT_WHISPER.put(player.getUUID(), now + 60 + random.nextInt(100));
			return;
		}
		if (now < next) return;
		Vec3 behind = player.position().subtract(player.getLookAngle().multiply(1, 0, 1).normalize().scale(4 + random.nextDouble() * 4));
		level.playSound(null, behind.x, player.getEyeY(), behind.z, Level3Sounds.PARANOIA, SoundSource.HOSTILE, 0.7F, 0.8F + random.nextFloat() * 0.3F);
		if (random.nextInt(5) == 0) player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, true, false, false));
		NEXT_WHISPER.put(player.getUUID(), now + 120 + random.nextInt(160));
	}

	/** Whether a player is feeling watched, for tests. */
	static boolean watched(ServerPlayer player) {
		return NEXT_WHISPER.containsKey(player.getUUID());
	}

	private static void crackle(ServerLevel level, ServerPlayer player, RandomSource random) {
		double angle = random.nextDouble() * Math.PI * 2, distance = 4 + random.nextDouble() * 10;
		level.playSound(null, player.getX() + Math.cos(angle) * distance, player.getEyeY() + 1, player.getZ() + Math.sin(angle) * distance,
				random.nextBoolean() ? Level3Sounds.CRACKLE : Level3Sounds.HUM, SoundSource.AMBIENT, 0.5F + random.nextFloat() * 0.3F, 0.7F + random.nextFloat() * 0.4F);
	}

	/** Entities that come in sight of a sanctum's angel are afraid, and leave. */
	private static void scareOff(ServerLevel level, long seed) {
		for (Mob mob : level.getEntities(EntityTypeTest.forClass(Mob.class), m -> m.isAlive() && (m instanceof Monster || m instanceof Faceling))) {
			for (Placed placed : hallsNear(seed, mob.getBlockX(), mob.getBlockZ(), ANGEL_REACH)) {
				if (placed.hall().kind() != HallKind.SANCTUM) continue;
				if (mob.position().distanceTo(placed.angel()) > ANGEL_REACH) continue;
				level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.5, mob.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
				mob.discard();
				break;
			}
		}
	}

	/** Two or three hounds in the passages behind a player, away from sanctums and Base Gamma. */
	static int spawnPack(ServerPlayer player) {
		ServerLevel level = player.level();
		long seed = Level3ChunkGenerator.planSeed(level.getChunkSource().randomState());
		if (level.getEntitiesOfClass(Hound.class, new AABB(player.blockPosition()).inflate(NEAR)).size() >= FloorDifficulty.scaledCap(player, MAX_NEAR)) return 0;
		RandomSource random = level.getRandom();
		for (int attempt = 0; attempt < 120; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2, distance = NEAREST + random.nextDouble() * (FARTHEST - NEAREST);
			int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance), z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
			Level3Layout.Tile tile = Level3Layout.tile(seed, x, z);
			if (tile.kind() != Level3Layout.Kind.FLOOR || tile.clearance() < 2 || nearSafety(seed, x, z)) continue;
			BlockPos at = new BlockPos(x, Level3ChunkGenerator.FLOOR_Y + 1, z);
			if (!level.isLoaded(at) || !level.getBlockState(at).isAir()) continue;
			int size = 2 + random.nextInt(2), came = 0;
			for (int i = 0; i < size; i++) {
				Hound hound = Monsters.HOUND.create(level, EntitySpawnReason.EVENT);
				if (hound == null) continue;
				hound.snapTo(x + 0.5, at.getY(), z + 0.5, random.nextFloat() * 360, 0);
				hound.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
				hound.addTag(HOUND_TAG);
				level.addFreshEntity(hound);
				came++;
			}
			return came;
		}
		return 0;
	}

	static boolean nearSafety(long seed, int x, int z) {
		for (Placed placed : hallsNear(seed, x, z, SANCTUM_KEEP_CLEAR)) {
			HallKind kind = placed.hall().kind();
			if (kind == HallKind.SANCTUM || kind == HallKind.GAMMA || kind == HallKind.BASE || kind == HallKind.BNTG_STORAGE || kind == HallKind.BLUE_SALVATION) {
				return true;
			}
		}
		return false;
	}

	/** A hall and the world position of its corner. */
	record Placed(Hall hall, int x0, int z0) {
		/** The angel on its pedestal in the middle of a sanctum. */
		Vec3 angel() {
			return new Vec3(x0 + 1 + Level3Halls.width(hall) / 2 - 1 + 0.5, Level3ChunkGenerator.FLOOR_Y + 2.5, z0 + 1 + Level3Halls.depth(hall) / 2 - 1 + 0.5);
		}

		/** One block of the generator in the middle of a generator room. */
		BlockPos generator() {
			return new BlockPos(x0 + 1 + Level3Halls.width(hall) / 2 - 1, Level3ChunkGenerator.FLOOR_Y + 1, z0 + 1 + Level3Halls.depth(hall) / 2 - 1);
		}
	}

	/** Halls within reach of (x, z). */
	static List<Placed> hallsNear(long seed, int x, int z, int reach) {
		List<Placed> found = new ArrayList<>();
		for (int rx = Math.floorDiv(x - reach, Level3Plan.SIZE); rx <= Math.floorDiv(x + reach, Level3Plan.SIZE); rx++) {
			for (int rz = Math.floorDiv(z - reach, Level3Plan.SIZE); rz <= Math.floorDiv(z + reach, Level3Plan.SIZE); rz++) {
				for (Hall hall : Level3Plan.of(seed, rx, rz).halls) {
					int x0 = rx * Level3Plan.SIZE + hall.x0(), z0 = rz * Level3Plan.SIZE + hall.z0();
					int x1 = rx * Level3Plan.SIZE + hall.x1(), z1 = rz * Level3Plan.SIZE + hall.z1();
					int dx = Math.max(0, Math.max(x0 - x, x - x1)), dz = Math.max(0, Math.max(z0 - z, z - z1));
					if (dx <= reach && dz <= reach) found.add(new Placed(hall, x0, z0));
				}
			}
		}
		return found;
	}
}
