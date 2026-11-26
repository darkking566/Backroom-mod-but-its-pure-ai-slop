package dev.noclip.backrooms.core.field;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.Home;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The investigator's field kit (docs/project/1-gameplay/README.md, "後室進階玩法與創新機制"):
 * <ul>
 * <li>for marking the way: chalk (arrows, crosses, "EXIT" on floors and walls; click a mark again to change it), glow
 * sticks, and the red guide line, laid behind you while the spool is in hand and switched on;</li>
 * <li>for holding out: barricade planks that whatever wants through has to batter for a few seconds, tripwire alarms
 * that tell everyone near which way something came through, battery lamps that steady the mind for three minutes;</li>
 * <li>for M.E.G.'s quota: the camera and the mapping beacon (core.field.Evidence).</li>
 * </ul>
 * All of it is for getting through the moment, not for settling in.
 */
public final class FieldKit {
	public static final Block CHALK_MARK = block("chalk_mark", Properties.of().mapColor(MapColor.SNOW).instabreak().noCollision().noOcclusion()
			.sound(SoundType.CALCITE).noLootTable().replaceable().pushReaction(PushReaction.DESTROY), FieldBlocks.ChalkMark::new);
	public static final Block GLOW_STICK_BLOCK = block("glow_stick", Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN).instabreak().noCollision()
			.noOcclusion().lightLevel(s -> 9).sound(SoundType.CANDLE).noLootTable().pushReaction(PushReaction.DESTROY), FieldBlocks.GlowStick::new);
	public static final Block RED_LINE = block("red_line", Properties.of().mapColor(MapColor.COLOR_RED).instabreak().noCollision().noOcclusion()
			.sound(SoundType.WOOL).noLootTable().replaceable().pushReaction(PushReaction.DESTROY), FieldBlocks.RedLine::new);
	public static final Block BARRICADE = block("barricade", Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F).noOcclusion()
			.sound(SoundType.WOOD), FieldBlocks.Barricade::new);
	public static final Block TRIPWIRE_BLOCK = block("tripwire_alarm", Properties.of().mapColor(MapColor.METAL).instabreak().noCollision()
			.noOcclusion().sound(SoundType.CHAIN), FieldBlocks.Tripwire::new);
	public static final Block LAMP_BLOCK = block("emergency_lamp", Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.5F).noOcclusion()
			.sound(SoundType.LANTERN).lightLevel(s -> s.getValue(FieldBlocks.Lamp.LIT) ? 14 : 0), FieldBlocks.Lamp::new);
	public static final Block BEACON_BLOCK = block("mapping_beacon", Properties.of().mapColor(MapColor.COLOR_CYAN).strength(1.0F).noOcclusion()
			.sound(SoundType.METAL).lightLevel(s -> 7), FieldBlocks.Beacon::new);

	public static final Item CAMERA = item("meg_camera", p -> new Evidence.Camera(p.stacksTo(1).durability(24)));
	public static final Item PHOTO = item("evidence_photo", p -> new Evidence.Photo(p.stacksTo(1)));
	public static final Item TAPE = item("topology_tape", p -> new Evidence.Tape(p.stacksTo(1)));
	public static final Item BEACON = item("mapping_beacon", p -> new Evidence.BeaconItem(BEACON_BLOCK, p.stacksTo(1).useBlockDescriptionPrefix()));
	public static final Item CHALK = item("chalk", p -> new Chalk(p.durability(64)));
	public static final Item GLOW_STICK = item("glow_stick", p -> new BlockItem(GLOW_STICK_BLOCK, p.stacksTo(16)));
	public static final Item GUIDE_LINE = item("guide_line", p -> new GuideLine(p.durability(256)));
	public static final Item BARRICADE_PLANKS = item("barricade_planks", p -> new BlockItem(BARRICADE, p.stacksTo(16)));
	public static final Item TRIPWIRE_ALARM = item("tripwire_alarm", p -> new BlockItem(TRIPWIRE_BLOCK, p.stacksTo(8).useBlockDescriptionPrefix()));
	public static final Item EMERGENCY_LAMP = item("emergency_lamp", p -> new BlockItem(LAMP_BLOCK, p.stacksTo(8).useBlockDescriptionPrefix()));

	/** How long a barricade holds against something battering it, in seconds. */
	static final int BARRICADE_HITS = 5;
	private static final Map<GlobalPos, Integer> BATTERED = new HashMap<>();
	private static final Map<GlobalPos, Integer> TRIPPED = new HashMap<>();
	private static final Map<GlobalPos, Integer> LAMPS = new HashMap<>();
	private static final Set<UUID> LAYING = new HashSet<>();

	private FieldKit() {
	}

	public static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			for (Item item : new Item[] {CAMERA, BEACON, CHALK, GLOW_STICK, GUIDE_LINE, BARRICADE_PLANKS, TRIPWIRE_ALARM, EMERGENCY_LAMP}) {
				output.accept(item);
			}
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			BATTERED.clear();
			TRIPPED.clear();
			LAMPS.clear();
			LAYING.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(FieldKit::tick);
		Evidence.register();
		Drift.register();
	}

	private static Block block(String name, Properties properties, Function<Properties, Block> factory) {
		return Blocks.register(ResourceKey.create(Registries.BLOCK, BackroomsMod.id(name)), factory, properties);
	}

	private static Item item(String name, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BackroomsMod.id(name));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
		if (item instanceof BlockItem blockItem) blockItem.registerBlocks(Item.BY_BLOCK, item);
		return item;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 == 0) bash(server);
		if (server.getTickCount() % 4 == 0) lay(server);
	}

	/** Something hostile up against a barricade, after someone: a blow a second, and it gives after a few. */
	private static void bash(MinecraftServer server) {
		Set<GlobalPos> hit = new HashSet<>();
		for (ServerLevel level : server.getAllLevels()) {
			for (ServerPlayer player : level.players()) {
				for (Monster monster : level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(32), m -> m.getTarget() != null)) {
					BlockPos.betweenClosedStream(monster.getBoundingBox().inflate(1.0)).forEach(pos -> {
						if (level.getBlockState(pos).is(BARRICADE)) hit.add(GlobalPos.of(level.dimension(), pos.immutable()));
					});
				}
			}
		}
		for (Iterator<GlobalPos> it = BATTERED.keySet().iterator(); it.hasNext(); ) if (!hit.contains(it.next())) it.remove();
		for (GlobalPos at : hit) {
			ServerLevel level = server.getLevel(at.dimension());
			if (level == null) continue;
			int blows = BATTERED.merge(at, 1, Integer::sum);
			level.playSound(null, at.pos(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 1.0F, 0.8F + blows * 0.05F);
			level.destroyBlockProgress(at.pos().hashCode(), at.pos(), Math.min(9, blows * 10 / BARRICADE_HITS));
			if (blows >= BARRICADE_HITS) {
				BATTERED.remove(at);
				level.destroyBlock(at.pos(), false);
				level.playSound(null, at.pos(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 1.0F, 1.0F);
			}
		}
	}

	/** The tripwire rattles; once every few seconds at most. */
	static void trip(ServerLevel level, BlockPos pos, Entity entity) {
		GlobalPos at = GlobalPos.of(level.dimension(), pos.immutable());
		int now = level.getServer().getTickCount();
		Integer last = TRIPPED.get(at);
		if (last != null && now - last < 60 && now >= last) return;
		TRIPPED.put(at, now);
		FieldBlocks.warn(level, pos, entity);
		if (entity instanceof Mob mob && mob.getTarget() == null) {
			// It heard the cans too: for a moment it does not know where to go.
			mob.getNavigation().stop();
		}
	}

	/** How long a battery lamp has been burning (counted in the lamp's own ticks, a step at a time). */
	static int lampAge(ServerLevel level, BlockPos pos, int step) {
		return LAMPS.merge(GlobalPos.of(level.dimension(), pos.immutable()), step, Integer::sum);
	}

	/** Lays the guide line under whoever holds a spool that is switched on. */
	private static void lay(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!LAYING.contains(player.getUUID())) continue;
			InteractionHand hand = player.getMainHandItem().is(GUIDE_LINE) ? InteractionHand.MAIN_HAND
					: player.getOffhandItem().is(GUIDE_LINE) ? InteractionHand.OFF_HAND : null;
			if (hand == null || !player.onGround()) continue;
			ServerLevel level = (ServerLevel) player.level();
			BlockPos feet = player.blockPosition();
			if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)) continue;
			Direction.Axis axis = Math.abs(player.getDeltaMovement().x) > Math.abs(player.getDeltaMovement().z) ? Direction.Axis.X : Direction.Axis.Z;
			level.setBlockAndUpdate(feet, RED_LINE.defaultBlockState().setValue(FieldBlocks.RedLine.AXIS, axis));
			ItemStack spool = player.getItemInHand(hand);
			if (!player.isCreative()) spool.hurtAndBreak(1, player, hand);
		}
	}

	static boolean laying(Player player) {
		return LAYING.contains(player.getUUID());
	}

	/** Chalk: a mark where you click (floor or wall), its arrow the way you look; on a mark, the next symbol. */
	static final class Chalk extends Item {
		Chalk(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			Level level = context.getLevel();
			BlockPos clicked = context.getClickedPos();
			BlockState there = level.getBlockState(clicked);
			if (there.is(CHALK_MARK)) {
				if (!level.isClientSide()) {
					var next = FieldBlocks.ChalkMark.Symbol.values()[(there.getValue(FieldBlocks.ChalkMark.SYMBOL).ordinal() + 1) % 3];
					level.setBlockAndUpdate(clicked, there.setValue(FieldBlocks.ChalkMark.SYMBOL, next));
				}
				return InteractionResult.SUCCESS;
			}
			Direction side = context.getClickedFace();
			if (side == Direction.DOWN) return InteractionResult.PASS;
			BlockPos at = clicked.relative(side);
			if (!level.getBlockState(at).canBeReplaced()) return InteractionResult.FAIL;
			BlockState mark = side == Direction.UP
					? CHALK_MARK.defaultBlockState().setValue(FieldBlocks.ChalkMark.FACE, FieldBlocks.ChalkMark.Face.FLOOR)
							.setValue(FieldBlocks.ChalkMark.FACING, context.getHorizontalDirection())
					: CHALK_MARK.defaultBlockState().setValue(FieldBlocks.ChalkMark.FACE, FieldBlocks.ChalkMark.Face.WALL)
							.setValue(FieldBlocks.ChalkMark.FACING, side);
			if (!level.isClientSide()) {
				level.setBlockAndUpdate(at, mark);
				level.playSound(null, at, SoundEvents.CALCITE_HIT, SoundSource.PLAYERS, 0.6F, 1.6F);
				Player player = context.getPlayer();
				if (player != null && !player.isCreative()) context.getItemInHand().hurtAndBreak(1, player, context.getHand());
				Drift.marked((ServerLevel) level, at);
			}
			return InteractionResult.SUCCESS;
		}
	}

	/** The guide-line spool: use it to start or stop laying the line behind you. */
	static final class GuideLine extends Item {
		GuideLine(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!level.isClientSide()) {
				boolean on = LAYING.add(player.getUUID()) || !LAYING.remove(player.getUUID());
				if (player instanceof ServerPlayer serverPlayer) {
					serverPlayer.sendOverlayMessage(Component.translatable(on ? "message.noclip_backrooms.field.line_on" : "message.noclip_backrooms.field.line_off")
							.withStyle(ChatFormatting.RED));
				}
			}
			return InteractionResult.SUCCESS;
		}
	}

	/** Whether this player is in the Backrooms (where the kit is meant for), for messages. */
	static boolean inBackrooms(Player player) {
		return Home.inBackrooms(player.level().dimension());
	}

	/** For tests: whether the tripwire at this spot has gone off. */
	public static boolean tripped(ServerLevel level, BlockPos pos) {
		return TRIPPED.containsKey(GlobalPos.of(level.dimension(), pos));
	}

	/** For tests: whether a barricade at this spot is being battered. */
	public static int blows(ServerLevel level, BlockPos pos) {
		return BATTERED.getOrDefault(GlobalPos.of(level.dimension(), pos), 0);
	}
}
