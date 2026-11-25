package dev.noclip.backrooms.core.guild;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The guild hall's materials, matched to the village next to it. All stone and brick (the timber hall looked too
 * plain): stone bricks under a slate roof in the plains, tuff in the taiga, stone with a black roof in the snow, red
 * brick in the savanna, sandstone in the desert. Only the door, the signs and the bed frames are wood.
 */
public enum GuildPalette {
	PLAINS(Blocks.POLISHED_DEEPSLATE, Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS,
			Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.STONE_BRICK_WALL, Blocks.POLISHED_DEEPSLATE,
			Blocks.SMOOTH_STONE_SLAB, Blocks.POLISHED_ANDESITE_STAIRS),
	DESERT(Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SANDSTONE, Blocks.CUT_SANDSTONE,
			Blocks.CHISELED_SANDSTONE, Blocks.CUT_RED_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE_STAIRS, Blocks.SANDSTONE_WALL, Blocks.CUT_RED_SANDSTONE,
			Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.SMOOTH_SANDSTONE_STAIRS),
	SAVANNA(Blocks.POLISHED_DEEPSLATE, Blocks.POLISHED_ANDESITE, Blocks.BRICKS, Blocks.BRICKS, Blocks.MUD_BRICKS,
			Blocks.STONE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.BRICK_WALL, Blocks.POLISHED_DEEPSLATE,
			Blocks.SMOOTH_STONE_SLAB, Blocks.POLISHED_ANDESITE_STAIRS),
	SNOWY(Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_DIORITE, Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.CALCITE,
			Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.POLISHED_BLACKSTONE_WALL,
			Blocks.POLISHED_BLACKSTONE, Blocks.SMOOTH_STONE_SLAB, Blocks.POLISHED_ANDESITE_STAIRS),
	TAIGA(Blocks.POLISHED_DEEPSLATE, Blocks.POLISHED_ANDESITE, Blocks.TUFF_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CHISELED_TUFF_BRICKS,
			Blocks.POLISHED_TUFF, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.TUFF_BRICK_WALL, Blocks.POLISHED_DEEPSLATE,
			Blocks.SMOOTH_STONE_SLAB, Blocks.POLISHED_ANDESITE_STAIRS);

	/** The band at the foot of the walls and the front step; the floor inside. */
	final Block foundation, floor;
	/** The wall, and two weathered stones scattered through it. */
	final Block wall, worn, aged;
	/** The corner and bay columns; the band at the top of the walls and the beams across the hall. */
	final Block pillar, beam;
	final Block roof, post, counter, tableSlab, chair;
	final Block door = Blocks.DARK_OAK_DOOR, wallSign = Blocks.DARK_OAK_WALL_SIGN, sign = Blocks.DARK_OAK_SIGN;

	GuildPalette(Block foundation, Block floor, Block wall, Block worn, Block aged, Block pillar, Block beam, Block roof, Block post,
			Block counter, Block tableSlab, Block chair) {
		this.foundation = foundation;
		this.floor = floor;
		this.wall = wall;
		this.worn = worn;
		this.aged = aged;
		this.pillar = pillar;
		this.beam = beam;
		this.roof = roof;
		this.post = post;
		this.counter = counter;
		this.tableSlab = tableSlab;
		this.chair = chair;
	}

	/** The wall block at a cell: mostly plain, here and there a weathered stone (fixed by position, so chunks agree). */
	Block wallAt(int x, int y, int z) {
		int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
		h ^= h >>> 13;
		h *= 0x5bd1e995;
		int roll = (h >>> 16) & 15;
		return roll == 0 ? worn : roll == 1 ? aged : wall;
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
