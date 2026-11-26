package dev.noclip.backrooms.level0;

import dev.noclip.backrooms.core.rpg.PlayerRpg;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Pitfalls escapable lower-level landing + Chasm abyss lethal void:
 * - Falling into a {@link Maze.Zone#PITS} opening cushions lethal fall damage into a survivable jolt
 *   and drops the player into the dim Room 14D undercroft with an objective hint to find the escape ladder.
 * - Falling deep into a {@link Maze.Zone#CHASM} sheer abyss (y < -20) remains fatal.
 * - Standing near a pit or chasm edge reveals eerie faint sparks and whispers from below.
 */
final class PitEffects {
	private static final int ABYSS_CHECK_TICKS = 5;
	private static final int LURE_CHECK_TICKS = 20;
	private static final double LETHAL_Y = -20.0;
	private static final Set<UUID> NOTIFIED_UNDERCROFT = new HashSet<>();

	private PitEffects() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(PitEffects::tick);
		// Cushion fall damage in PITS lower level so falling into a pit is a thrilling setback instead of instant death.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player) || !source.is(DamageTypes.FALL)) return true;
			if (player.level().dimension() != Level0.LEVEL_0) return true;
			if (player.getY() > PitfallsPlan.LOWER_CEIL_Y + 2) return true;
			long seed = Level0ChunkGenerator.mazeSeed(player.level().getChunkSource().randomState());
			if (Maze.zoneAt(seed, player.getBlockX(), player.getBlockZ()) != Maze.Zone.PITS) return true;
			if (amount > 4.0F) {
				player.hurtServer(player.level(), player.level().damageSources().generic(), 4.0F);
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 90, 1, true, false, true));
				player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, true, false, true));
				PlayerRpg.drainSanity(player, 3.0F);
				player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.pitfalls.fell")
						.withStyle(ChatFormatting.YELLOW));
				return false;
			}
			return true;
		});
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % ABYSS_CHECK_TICKS != 0) return;

		ServerLevel level = server.getLevel(Level0.LEVEL_0);
		if (level == null) return;

		long seed = Level0ChunkGenerator.mazeSeed(level.getChunkSource().randomState());
		RandomSource random = level.getRandom();
		boolean checkLure = now % LURE_CHECK_TICKS == 0;

		for (ServerPlayer player : level.players()) {
			UUID id = player.getUUID();
			if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
				NOTIFIED_UNDERCROFT.remove(id);
				continue;
			}

			if (player.getY() < LETHAL_Y && Maze.isAbyss(seed, player.getBlockX(), player.getBlockZ())) {
				player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, true, false, true));
				player.hurtServer(level, level.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
				continue;
			}

			Maze.Zone zone = Maze.zoneAt(seed, player.getBlockX(), player.getBlockZ());
			if (zone == Maze.Zone.PITS && player.getY() <= PitfallsPlan.LOWER_CEIL_Y) {
				if (NOTIFIED_UNDERCROFT.add(id)) {
					player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.pitfalls.undercroft")
							.withStyle(ChatFormatting.GRAY));
					level.playSound(null, player.getX(), player.getY(), player.getZ(),
							SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 0.5F, 0.75F);
				}
			} else {
				NOTIFIED_UNDERCROFT.remove(id);
			}

			if (checkLure && (zone == Maze.Zone.PITS || zone == Maze.Zone.CHASM) && player.getY() >= Level0ChunkGenerator.FLOOR_Y) {
				emitPitLure(level, seed, player, random);
			}
		}
	}

	private static void emitPitLure(ServerLevel level, long seed, ServerPlayer player, RandomSource random) {
		int px = player.getBlockX(), pz = player.getBlockZ();
		for (int attempt = 0; attempt < 8; attempt++) {
			int tx = px + random.nextInt(13) - 6;
			int tz = pz + random.nextInt(13) - 6;
			if (!Maze.isAbyss(seed, tx, tz) && !Maze.isPit(seed, tx, tz)) continue;

			double py = player.getY() - 8.0 - random.nextDouble() * 6.0;
			level.sendParticles(player, ParticleTypes.SOUL_FIRE_FLAME, false, false,
					tx + 0.5, py, tz + 0.5, 2, 0.3, 0.6, 0.3, 0.005);
			if (random.nextInt(36) == 0) {
				level.playSound(null, tx + 0.5, py + 2.0, tz + 0.5,
						ModSounds.DISTANT_WHISPER, SoundSource.AMBIENT, 0.20F, 0.72F + random.nextFloat() * 0.12F);
			}
			return;
		}
	}
}
