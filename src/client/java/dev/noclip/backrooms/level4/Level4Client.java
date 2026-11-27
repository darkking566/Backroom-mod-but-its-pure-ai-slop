package dev.noclip.backrooms.level4;

import dev.noclip.backrooms.monsters.Monsters;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * 100% client-side Level 4 Smiler rumor ("One person claimed to have seen a Smiler, though there is no proof of this."):
 * spawns a local-only phantom Smiler in a dark office corner that vanishes when approached or stared at, never
 * existing on the server so teammates cannot see or confirm it.
 */
public final class Level4Client {
	private static final int PHANTOM_ID = -744001;
	private static ResourceKey<Level> lastDimension;
	private static Monster activeSmiler;
	private static int ttlTicks;
	private static int staredTicks;
	private static int cooldownTicks = 400;

	private Level4Client() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(Level4Client::tick);
	}

	public static boolean hasActiveRumor() {
		return activeSmiler != null;
	}

	public static boolean triggerRumorForTest(Minecraft client) {
		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		if (level == null || player == null || level.dimension() != Level4.LEVEL_4) return false;
		clear(level, false);
		Vec3 look = player.getLookAngle().multiply(1, 0, 1);
		if (look.lengthSqr() < 1e-4) look = new Vec3(0, 0, 1);
		look = look.normalize();
		Vec3 spot = new Vec3(player.getX() + look.x * 10.0, player.getY(), player.getZ() + look.z * 10.0);
		return spawnLocalSmiler(level, player, spot);
	}

	public static void clearForTest(Minecraft client) {
		clear(client.level, false);
	}

	private static void tick(Minecraft client) {
		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		if (level == null || player == null || !player.isAlive() || player.isSpectator()
				|| level.dimension() != Level4.LEVEL_4) {
			clear(level, false);
			lastDimension = level == null ? null : level.dimension();
			return;
		}
		if (lastDimension != level.dimension()) {
			clear(level, false);
			lastDimension = level.dimension();
			cooldownTicks = 400;
		}
		level.setRainLevel(1.0F);
		level.setThunderLevel(0.0F);
		spawnWindowRainParticles(level, player);

		if (activeSmiler != null) {
			facePlayer(activeSmiler, player);
			ttlTicks--;
			Vec3 toMob = activeSmiler.getEyePosition().subtract(player.getEyePosition());
			double dist = toMob.length();
			boolean staring = dist > 0.5 && player.getLookAngle().normalize().dot(toMob.normalize()) > 0.88;
			if (staring) staredTicks++;
			else staredTicks = Math.max(0, staredTicks - 1);

			if (ttlTicks <= 0 || dist < 7.5 || staredTicks > 22) {
				player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.smiler_rumor"));
				clear(level, true);
			}
			return;
		}

		if (--cooldownTicks > 0) return;
		RandomSource rng = level.getRandom();
		cooldownTicks = 600 + rng.nextInt(400);
		if (rng.nextInt(100) >= 12) return;

		for (int attempt = 0; attempt < 16; attempt++) {
			double angle = rng.nextDouble() * Math.PI * 2.0;
			double dist = 11.0 + rng.nextDouble() * 7.0;
			int x = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
			int z = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
			BlockPos pos = new BlockPos(x, player.getBlockY(), z);
			if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) continue;
			if (level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) continue;
			if (level.getBrightness(LightLayer.BLOCK, pos) > 2) continue;
			spawnLocalSmiler(level, player, new Vec3(x + 0.5, pos.getY(), z + 0.5));
			break;
		}
	}

	private static boolean spawnLocalSmiler(ClientLevel level, LocalPlayer player, Vec3 spot) {
		Monster mob = Monsters.SMILER.create(level, EntitySpawnReason.EVENT);
		if (mob == null) return false;
		mob.setId(PHANTOM_ID);
		mob.setNoGravity(true);
		mob.setSilent(true);
		mob.setPos(spot.x, spot.y, spot.z);
		mob.xo = spot.x;
		mob.yo = spot.y;
		mob.zo = spot.z;
		facePlayer(mob, player);
		level.addEntity(mob);
		activeSmiler = mob;
		ttlTicks = 90;
		staredTicks = 0;
		return true;
	}

	private static void facePlayer(Monster mob, LocalPlayer player) {
		float yaw = (float) (Mth.atan2(player.getZ() - mob.getZ(), player.getX() - mob.getX()) * Mth.RAD_TO_DEG) - 90.0F;
		mob.setYRot(yaw);
		mob.yRotO = yaw;
		mob.yBodyRot = yaw;
		mob.yBodyRotO = yaw;
		mob.yHeadRot = yaw;
		mob.yHeadRotO = yaw;
	}

	private static void clear(ClientLevel level, boolean puff) {
		if (activeSmiler != null && level != null) {
			if (puff) {
				level.addParticle(ParticleTypes.SMOKE, activeSmiler.getX(), activeSmiler.getY() + 1.1, activeSmiler.getZ(),
						0.0, 0.03, 0.0);
			}
			level.removeEntity(PHANTOM_ID, Entity.RemovalReason.DISCARDED);
		}
		activeSmiler = null;
		ttlTicks = 0;
		staredTicks = 0;
	}

	private static void spawnWindowRainParticles(ClientLevel level, LocalPlayer player) {
		RandomSource rng = level.getRandom();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int px = player.getBlockX(), py = player.getBlockY(), pz = player.getBlockZ();
		int baseY = Math.max(Level4ChunkGenerator.FLOOR_Y + 1, py - 1);
		for (int i = 0; i < 18; i++) {
			int x = px + rng.nextInt(29) - 14;
			int z = pz + rng.nextInt(29) - 14;
			if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) > Level4ChunkGenerator.FLOOR_Y + 1) continue;
			int y = baseY + rng.nextInt(12);
			pos.set(x, y, z);
			if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) continue;
			level.addParticle(ParticleTypes.FALLING_WATER, x + rng.nextDouble(), y + rng.nextDouble(), z + rng.nextDouble(),
					0.0, -0.55, 0.0);
		}
		// Add falling raindrops just outside nearby exterior office windows
		for (int dx = -8; dx <= 8; dx++) {
			for (int dz = -8; dz <= 8; dz++) {
				int wx = px + dx, wz = pz + dz;
				pos.set(wx, Level4ChunkGenerator.FLOOR_Y + 2, wz);
				if (!level.isLoaded(pos)) continue;
				var state = level.getBlockState(pos);
				if (!state.is(Level4Blocks.OFFICE_RAIN_WINDOW) && !state.is(Level4Blocks.OFFICE_TRAP_WINDOW)) continue;
				for (int[] off : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					int ox = wx + off[0], oz = wz + off[1];
					if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, ox, oz) > Level4ChunkGenerator.FLOOR_Y) continue;
					BlockPos out = new BlockPos(ox, Level4ChunkGenerator.FLOOR_Y + 2, oz);
					if (!level.isLoaded(out) || !level.getBlockState(out).isAir()) continue;
					if (rng.nextInt(4) == 0) {
						double ry = Level4ChunkGenerator.FLOOR_Y + 2.0 + rng.nextDouble() * 6.0;
						level.addParticle(ParticleTypes.FALLING_WATER,
								ox + 0.2 + rng.nextDouble() * 0.6, ry, oz + 0.2 + rng.nextDouble() * 0.6,
								0.0, -0.55, 0.0);
					}
					break;
				}
			}
		}
	}
}
