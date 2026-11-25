package dev.noclip.backrooms.core.guild;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** The guild hall's materials, matched to the village next to it: oak in the plains, sandstone in the desert, and so on. */
public enum GuildPalette {
	PLAINS(Blocks.COBBLESTONE, Blocks.SPRUCE_PLANKS, Blocks.OAK_PLANKS, Blocks.OAK_LOG, Blocks.STRIPPED_OAK_LOG, Blocks.DARK_OAK_STAIRS,
			Blocks.OAK_FENCE, Blocks.SPRUCE_DOOR, Blocks.STRIPPED_SPRUCE_LOG, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_STAIRS, Blocks.OAK_WALL_SIGN, Blocks.OAK_SIGN),
	DESERT(Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.CHISELED_SANDSTONE, Blocks.STRIPPED_JUNGLE_LOG,
			Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.JUNGLE_FENCE, Blocks.JUNGLE_DOOR, Blocks.STRIPPED_JUNGLE_LOG, Blocks.JUNGLE_SLAB,
			Blocks.JUNGLE_STAIRS, Blocks.JUNGLE_WALL_SIGN, Blocks.JUNGLE_SIGN),
	SAVANNA(Blocks.COBBLESTONE, Blocks.ACACIA_PLANKS, Blocks.ACACIA_PLANKS, Blocks.ACACIA_LOG, Blocks.STRIPPED_ACACIA_LOG, Blocks.DARK_OAK_STAIRS,
			Blocks.ACACIA_FENCE, Blocks.ACACIA_DOOR, Blocks.STRIPPED_ACACIA_LOG, Blocks.ACACIA_SLAB, Blocks.ACACIA_STAIRS, Blocks.ACACIA_WALL_SIGN, Blocks.ACACIA_SIGN),
	SNOWY(Blocks.STONE_BRICKS, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.STRIPPED_SPRUCE_LOG, Blocks.SPRUCE_LOG, Blocks.SPRUCE_STAIRS,
			Blocks.SPRUCE_FENCE, Blocks.SPRUCE_DOOR, Blocks.STRIPPED_SPRUCE_LOG, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_WALL_SIGN, Blocks.SPRUCE_SIGN),
	TAIGA(Blocks.MOSSY_COBBLESTONE, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_LOG, Blocks.DARK_OAK_STAIRS,
			Blocks.SPRUCE_FENCE, Blocks.SPRUCE_DOOR, Blocks.STRIPPED_SPRUCE_LOG, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_WALL_SIGN, Blocks.SPRUCE_SIGN);

	final Block foundation, floor, wall, pillar, beam, roof, fence, door, counter, tableSlab, chair, wallSign, sign;

	GuildPalette(Block foundation, Block floor, Block wall, Block pillar, Block beam, Block roof, Block fence, Block door, Block counter,
			Block tableSlab, Block chair, Block wallSign, Block sign) {
		this.foundation = foundation;
		this.floor = floor;
		this.wall = wall;
		this.pillar = pillar;
		this.beam = beam;
		this.roof = roof;
		this.fence = fence;
		this.door = door;
		this.counter = counter;
		this.tableSlab = tableSlab;
		this.chair = chair;
		this.wallSign = wallSign;
		this.sign = sign;
	}

	private static TagKey<Biome> village(String kind) {
		return TagKey.create(Registries.BIOME, Identifier.withDefaultNamespace("has_structure/village_" + kind));
	}

	private static final TagKey<Biome> DESERT_TAG = village("desert"), SAVANNA_TAG = village("savanna"), SNOWY_TAG = village("snowy"), TAIGA_TAG = village("taiga");

	static GuildPalette of(Holder<Biome> biome) {
		if (biome.is(DESERT_TAG)) return DESERT;
		if (biome.is(SAVANNA_TAG)) return SAVANNA;
		if (biome.is(SNOWY_TAG)) return SNOWY;
		if (biome.is(TAIGA_TAG)) return TAIGA;
		return PLAINS;
	}
}
