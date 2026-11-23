package dev.noclip.backrooms.level1;

import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Smilers: a face in the dark, all eyes and teeth, that only shows itself during a Flickering.
 * It is drawn to light and noise: hold a light, sprint or jump and it comes for you fast; keep still and dark
 * and it barely moves. If it reaches you it bites and leaves you in the dark. It is gone when the lights return,
 * and never comes inside a settlement.
 */
final class Smilers {
	static final String TAG = "noclip_backrooms_smiler";
	private static final double LOUD_SPEED = 0.17, QUIET_SPEED = 0.02, REACH = 1.2;
	private static final float BITE = 6.0F;
	private static final int NEAREST = 10, FARTHEST = 20;

	private record Smiler(UUID id, UUID target) {
	}

	private static final List<Smiler> ACTIVE = new ArrayList<>();
	private static boolean wasDark;

	private Smilers() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Smilers::tick);
		// One saved mid-blackout is gone when its chunk loads again.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(TAG)) entity.discard();
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ACTIVE.clear();
			wasDark = false;
		});
	}

	static int count() {
		return ACTIVE.size();
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level1.LEVEL_1);
		if (level == null) return;
		boolean dark = Blackouts.phase() == Blackouts.Phase.DARK;
		if (!dark) {
			if (!ACTIVE.isEmpty()) vanishAll(level);
			wasDark = false;
			return;
		}
		if (!wasDark) {
			wasDark = true;
			for (ServerPlayer player : level.players()) {
				if (player.isCreative() || player.isSpectator()) continue;
				int count = 1 + level.getRandom().nextInt(2);
				for (int i = 0; i < count; i++) spawnNear(player);
			}
		}
		if (server.getTickCount() % 2 == 0) move(level);
	}

	/** A smiler in the dark somewhere around the player; returns it, or null if nowhere would do. */
	static Display.ItemDisplay spawnNear(ServerPlayer player) {
		ServerLevel level = player.level();
		long seed = Level1ChunkGenerator.planSeed(level.getChunkSource().randomState());
		RandomSource random = level.getRandom();
		for (int attempt = 0; attempt < 30; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2, distance = NEAREST + random.nextDouble() * (FARTHEST - NEAREST);
			double x = player.getX() + Math.cos(angle) * distance, z = player.getZ() + Math.sin(angle) * distance;
			int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
			if (!Level1Layout.tile(seed, bx, bz).walkable() || Marauders.nearSettlement(seed, bx, bz)) continue;
			BlockPos feet = new BlockPos(bx, Level1ChunkGenerator.FLOOR_Y + 1, bz);
			if (!level.isLoaded(feet) || !level.getBlockState(feet.above()).isAir()) continue;
			Display.ItemDisplay face = create(level, new Vec3(x, Level1ChunkGenerator.FLOOR_Y + 2.6, z));
			level.addFreshEntity(face);
			face.addTag(TAG);
			ACTIVE.add(new Smiler(face.getUUID(), player.getUUID()));
			level.playSound(null, x, face.getY(), z, Level1Sounds.SMILER, SoundSource.HOSTILE, 0.6F, 0.6F);
			return face;
		}
		return null;
	}

	private static Display.ItemDisplay create(ServerLevel level, Vec3 pos) {
		Display.ItemDisplay display = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
		CompoundTag tag = new CompoundTag();
		ItemStack.CODEC.encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), new ItemStack(Level1Items.SMILER_FACE))
				.result().ifPresent(item -> tag.put("item", item));
		Transformation.EXTENDED_CODEC.encodeStart(NbtOps.INSTANCE, new Transformation(new Vector3f(), new Quaternionf(),
				new Vector3f(1.1F, 1.1F, 0.05F), new Quaternionf())).result().ifPresent(t -> tag.put(Display.TAG_TRANSFORMATION, t));
		// Lit by nothing: it shines on its own.
		Brightness.CODEC.encodeStart(NbtOps.INSTANCE, Brightness.FULL_BRIGHT).result().ifPresent(b -> tag.put(Display.TAG_BRIGHTNESS, b));
		tag.putString(Display.TAG_BILLBOARD, "center");
		tag.putFloat(Display.TAG_SHADOW_RADIUS, 0.0F);
		tag.putFloat(Display.TAG_VIEW_RANGE, 2.0F);
		tag.putInt(Display.TAG_POS_ROT_INTERPOLATION_DURATION, 2);
		display.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
		display.setPos(pos.x, pos.y, pos.z);
		return display;
	}

	private static void move(ServerLevel level) {
		long seed = Level1ChunkGenerator.planSeed(level.getChunkSource().randomState());
		for (Iterator<Smiler> it = ACTIVE.iterator(); it.hasNext(); ) {
			Smiler smiler = it.next();
			Entity face = level.getEntity(smiler.id());
			if (face == null) {
				it.remove();
				continue;
			}
			ServerPlayer target = level.getServer().getPlayerList().getPlayer(smiler.target());
			if (target == null || target.level() != level || !target.isAlive() || target.isCreative() || target.isSpectator()
					|| Marauders.nearSettlement(seed, face.getBlockX(), face.getBlockZ())) {
				vanish(level, face);
				it.remove();
				continue;
			}
			Vec3 eye = target.getEyePosition();
			Vec3 to = eye.subtract(face.position());
			double distance = to.length();
			if (distance < REACH) {
				bite(level, target, face);
				it.remove();
				continue;
			}
			double speed = drawn(target) ? LOUD_SPEED * 2 : QUIET_SPEED * 2;
			Vec3 step = to.scale(Math.min(1, speed / distance));
			face.setPos(face.getX() + step.x, face.getY() + step.y, face.getZ() + step.z);
		}
	}

	/** Light in hand, sprinting, or off the ground: all draw a smiler in. */
	static boolean drawn(ServerPlayer player) {
		return player.isSprinting() || !player.onGround() || lightSource(player.getMainHandItem()) || lightSource(player.getOffhandItem());
	}

	private static boolean lightSource(ItemStack stack) {
		if (stack.getItem() instanceof BlockItem block && block.getBlock().defaultBlockState().getLightEmission() > 0) return true;
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals("flashlight");
	}

	private static void bite(ServerLevel level, ServerPlayer player, Entity face) {
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), Level1Sounds.SMILER_BITE, SoundSource.HOSTILE, 1.0F, 0.7F);
		player.hurtServer(level, level.damageSources().magic(), BITE);
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0));
		face.discard();
	}

	private static void vanish(ServerLevel level, Entity face) {
		level.sendParticles(ParticleTypes.SMOKE, face.getX(), face.getY(), face.getZ(), 8, 0.2, 0.2, 0.2, 0.01);
		face.discard();
	}

	private static void vanishAll(ServerLevel level) {
		for (Smiler smiler : ACTIVE) {
			Entity face = level.getEntity(smiler.id());
			if (face != null) vanish(level, face);
		}
		ACTIVE.clear();
	}
}
