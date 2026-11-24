package dev.noclip.backrooms.level0p1;

import dev.noclip.backrooms.core.LevelExits;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** What it takes to find Zenith Station on purpose: the B.N.T.G.'s way in. */
public final class ZenithItems {
	/**
	 * A B.N.T.G. beacon: the traders' secret, a way into Zenith Station that works every time. Used in Level 0 it opens
	 * a seam in the air and takes you through; the seam closes behind you and the beacon is spent.
	 */
	public static final Item BNTG_BEACON = register("bntg_beacon");

	private ZenithItems() {
	}

	static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(BNTG_BEACON));
	}

	private static Item register(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Level0p1.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new BeaconItem(new Item.Properties().setId(key).stacksTo(4)));
	}

	private static final class BeaconItem extends Item {
		BeaconItem(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
			if (level.dimension() != dev.noclip.backrooms.level0.Level0.LEVEL_0) {
				serverPlayer.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0p1.beacon_dead"));
				return InteractionResult.FAIL;
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.6F);
			if (!player.getAbilities().instabuild) stack.shrink(1);
			LevelExits.use(serverPlayer, Level0p1.KEY);
			return InteractionResult.SUCCESS;
		}
	}
}
