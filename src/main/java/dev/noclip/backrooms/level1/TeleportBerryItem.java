package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.core.LevelExits;
import dev.noclip.backrooms.level1.Level1Layout.Tile;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A "teleport berry": a last resort. Eaten in Level 1 it throws you a few hundred blocks away to somewhere quiet,
 * and for a long while afterwards no exit will take you anywhere.
 */
final class TeleportBerryItem extends Item {
	private static final int LOCK = 10 * 60 * 20;

	TeleportBerryItem(Properties properties) {
		super(properties);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
		ItemStack result = super.finishUsingItem(stack, level, user);
		if (level instanceof ServerLevel server && user instanceof ServerPlayer player && server.dimension() == Level1.LEVEL_1) {
			if (scatter(server, player)) {
				LevelExits.lock(player, LOCK);
				player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.berry_jump"));
			}
		}
		return result;
	}

	private static boolean scatter(ServerLevel level, ServerPlayer player) {
		long seed = Level1ChunkGenerator.planSeed(level.getChunkSource().randomState());
		RandomSource random = level.getRandom();
		for (int attempt = 0; attempt < 80; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2, distance = 150 + random.nextDouble() * 250;
			int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
			int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
			Tile tile = Level1Layout.tile(seed, x, z);
			if (tile != Tile.FLOOR && tile != Tile.LINE && tile != Tile.DAMP) continue;
			Vec3 landing = new Vec3(x + 0.5, Level1ChunkGenerator.FLOOR_Y + 1, z + 0.5);
			level.getChunk(BlockPos.containing(landing));
			if (!level.noCollision(player.getDimensions(player.getPose()).makeBoundingBox(landing))) continue;
			player.teleportTo(level, landing.x, landing.y, landing.z, Set.of(), player.getYRot(), 0, true);
			player.resetFallDistance();
			return true;
		}
		return false;
	}
}
