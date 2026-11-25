package dev.noclip.backrooms.level3;

import dev.noclip.backrooms.level3.Level3Layout.Kind;
import dev.noclip.backrooms.level3.Level3Layout.Tile;
import dev.noclip.backrooms.level3.Level3Plan.HallKind;
import dev.noclip.backrooms.monsters.Monsters;
import dev.noclip.backrooms.monsters.faceling.Faceling;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;

/**
 * High-intelligence entity ecology of Level 3 (after latest:level-3), alongside {@link Level3Events} hound packs:
 * hostile, armed Facelings (frequent around Assembly Lines), aggressive Deathmoth swarms, Smilers in dark stretches,
 * Skin-Stealers, Clumps, and rare Wretches. All avoid Base Gamma and Sanctums.
 */
final class Level3Monsters {
	static final String TAG = "noclip_backrooms_level3_monster";
	private static final int INTERVAL = 20 * 14;
	private static final int DARK = 4;
	private static final int NEAR = 44, MIN_DIST = 14, MAX_DIST = 28;
	private static final int MAX_MONSTERS_NEAR = 6;
	private static int heldOffUntil;

	record Entry(EntityType<? extends Mob> type, int weight, boolean darkOnly, int minClearance) {
	}

	static final List<Entry> POOL = List.of(
			new Entry(Monsters.FACELING, 26, false, 3),
			new Entry(Monsters.DEATHMOTH, 22, false, 2),
			new Entry(Monsters.SMILER, 22, true, 3),
			new Entry(Monsters.CLUMP, 14, true, 1),
			new Entry(Monsters.SKIN_STEALER, 10, false, 3),
			new Entry(Monsters.WRETCH, 6, false, 2)
	);

	private Level3Monsters() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Level3Monsters::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> heldOffUntil = 0);
	}

	/** Pauses natural spawns for a while, for tests. */
	static void holdOff(MinecraftServer server, int ticks) {
		heldOffUntil = server.getTickCount() + ticks;
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % INTERVAL != 0 || now < heldOffUntil) return;
		ServerLevel level = server.getLevel(Level3.LEVEL_3);
		if (level == null || level.getDifficulty() == Difficulty.PEACEFUL) return;
		long seed = Level3ChunkGenerator.planSeed(level.getChunkSource().randomState());
		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.players()) {
			if (player.isCreative() || player.isSpectator()) continue;
			int near = countNear(level, player);
			if (near >= MAX_MONSTERS_NEAR) continue;
			Tile under = Level3Layout.tile(seed, player.getBlockX(), player.getBlockZ());
			boolean inAssembly = under.hall() != null && under.hall().kind() == HallKind.ASSEMBLY;
			boolean inDark = level.getBrightness(LightLayer.BLOCK, player.blockPosition()) < DARK;
			int chance = inAssembly ? 55 : inDark ? 42 : 20;
			if (random.nextInt(100) >= chance) continue;
			Entry picked = inAssembly
					? (random.nextBoolean() ? POOL.get(0) : POOL.get(1))
					: pick(random, inDark);
			if (picked == null) continue;
			Mob spawned = spawnOne(player, picked.type());
			if (spawned != null && picked.type() == Monsters.DEATHMOTH) {
				int extra = Math.min(1 + random.nextInt(2), MAX_MONSTERS_NEAR - near - 1);
				for (int i = 0; i < extra; i++) spawnOne(player, Monsters.DEATHMOTH);
			}
		}
	}

	private static int countNear(ServerLevel level, ServerPlayer player) {
		AABB box = new AABB(player.blockPosition()).inflate(NEAR);
		int count = 0;
		for (Entry entry : POOL) {
			count += level.getEntities(entry.type(), box, Entity::isAlive).size();
		}
		return count;
	}

	private static Entry pick(RandomSource random, boolean inDark) {
		int total = 0;
		for (Entry entry : POOL) {
			if (entry.darkOnly() && !inDark) continue;
			total += entry.weight();
		}
		if (total <= 0) return null;
		int roll = random.nextInt(total);
		for (Entry entry : POOL) {
			if (entry.darkOnly() && !inDark) continue;
			roll -= entry.weight();
			if (roll < 0) return entry;
		}
		return null;
	}

	static int minClearance(EntityType<?> type) {
		for (Entry entry : POOL) {
			if (entry.type() == type) return entry.minClearance();
		}
		return 2;
	}

	/** Spawns one entity of the given type in a walkable Level 3 spot near the player, away from safety. */
	static <T extends Mob> T spawnOne(ServerPlayer player, EntityType<T> type) {
		ServerLevel level = player.level();
		long seed = Level3ChunkGenerator.planSeed(level.getChunkSource().randomState());
		RandomSource random = level.getRandom();
		T mob = type.create(level, EntitySpawnReason.NATURAL);
		if (mob == null) return null;
		int reqClearance = minClearance(type);
		for (int attempt = 0; attempt < 90; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = MIN_DIST + random.nextDouble() * (MAX_DIST - MIN_DIST);
			int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
			int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
			Tile tile = Level3Layout.tile(seed, x, z);
			if ((tile.kind() != Kind.FLOOR && tile.kind() != Kind.HALL) || tile.clearance() < reqClearance) continue;
			if (Level3Events.nearSafety(seed, x, z)) continue;
			BlockPos spot = new BlockPos(x, Level3ChunkGenerator.FLOOR_Y + 1, z);
			if (!level.isLoaded(spot) || !level.getBlockState(spot).isAir()) continue;
			mob.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360, 0);
			if (!level.noCollision(mob)) continue;
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.NATURAL, null);
			configureMob(mob, random);
			mob.addTag(TAG);
			level.addFreshEntity(mob);
			return mob;
		}
		mob.discard();
		return null;
	}

	private static void configureMob(Mob mob, RandomSource random) {
		if (mob instanceof Faceling faceling) {
			faceling.setHostile(true);
			ItemStack weapon = switch (random.nextInt(3)) {
				case 0 -> new ItemStack(Items.STONE_SWORD);
				case 1 -> new ItemStack(Items.STONE_AXE);
				default -> new ItemStack(Items.STONE_PICKAXE);
			};
			faceling.setItemSlot(EquipmentSlot.MAINHAND, weapon);
			faceling.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
			if (random.nextInt(100) < 35) {
				faceling.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
				faceling.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
			}
		}
	}
}
