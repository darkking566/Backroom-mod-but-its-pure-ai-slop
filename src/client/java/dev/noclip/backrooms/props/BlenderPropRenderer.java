package dev.noclip.backrooms.props;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.weapons.BlenderWeaponMesh;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Renders full-scale multi-block (e.g. 2x3 fridge, 2x2 vending machine, 2x1 sofa, 1x2 water cooler,
 * 2x2x2 boiler) and half-sliced noclipped Blender 3D furniture meshes seamlessly in the world.
 */
public final class BlenderPropRenderer implements BlockEntityRenderer<PropBlockEntity, BlenderPropRenderer.PropRenderState> {
	private static final Identifier WHITE_TEX = BackroomsMod.id("textures/item/blender_white.png");
	private static final String PROP_OBJ_PREFIX = "/assets/noclip_backrooms/models/blender_props/";
	private static final int FULL_BRIGHT = 0xF000F0;

	public static final class PropRenderState extends BlockEntityRenderState {
		public String propId = null;
		public Direction facing = Direction.NORTH;
		/** Set while the realistic items pack (core.look.ItemLook) redraws this prop from its block model. */
		public net.minecraft.client.renderer.block.MovingBlockRenderState trial = null;
		/** Or from a Blender mesh of its own, when the pack has one. */
		public dev.noclip.backrooms.core.look.MeshModel trialMesh = null;
		public net.minecraft.resources.Identifier trialTexture = null;
		/** The mesh is centred on the block like the Blender props (the film props), not laid out 0..1 like a block model. */
		public boolean trialCentred = false;
	}

	public BlenderPropRenderer(BlockEntityRendererProvider.Context ctx) {
	}

	@Override
	public PropRenderState createRenderState() {
		return new PropRenderState();
	}

	@Override
	public void extractRenderState(PropBlockEntity entity, PropRenderState state, float partialTicks,
			Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
		BlockEntityRenderState.extractBase(entity, state, crumblingOverlay);
		BlockState bs = entity.getBlockState();
		Block block = bs.getBlock();
		if (block instanceof PropPartBlock) {
			state.propId = null;
			return;
		}
		state.propId = BuiltInRegistries.BLOCK.getKey(block).getPath();
		state.facing = bs.hasProperty(HorizontalDirectionalBlock.FACING)
				? bs.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
		state.trial = null;
		state.trialMesh = null;
		state.trialCentred = false;
		// The film's Level 0 objects: a Blender mesh with a baked texture (level0.film.FilmLook).
		if (state.propId.startsWith("film_") || state.propId.startsWith("inst_") || state.propId.startsWith("town_")) {
			var film = dev.noclip.backrooms.level0.film.FilmLook.mesh(state.propId);
			if (!film.isEmpty()) {
				state.trialMesh = film;
				state.trialTexture = dev.noclip.backrooms.level0.film.FilmLook.texture(state.propId);
				state.trialCentred = true;
				return;
			}
		}
		if (dev.noclip.backrooms.core.look.ItemLook.replaces(state.propId)) {
			var mesh = dev.noclip.backrooms.core.look.ItemLook.propMesh(state.propId);
			if (!mesh.isEmpty()) {
				state.trialMesh = mesh;
				state.trialTexture = dev.noclip.backrooms.core.look.ItemLook.propTexture(state.propId);
				return;
			}
		}
		if (dev.noclip.backrooms.core.look.ItemLook.replaces(state.propId)
				&& entity.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel level) {
			var trial = new net.minecraft.client.renderer.block.MovingBlockRenderState();
			trial.randomSeedPos = entity.getBlockPos();
			trial.blockPos = entity.getBlockPos();
			trial.blockState = bs;
			trial.biome = level.getBiome(entity.getBlockPos());
			trial.cardinalLighting = level.cardinalLighting();
			trial.lightEngine = level.getLightEngine();
			state.trial = trial;
		}
	}

	@Override
	public void submit(PropRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.propId == null) return;
		if (state.trial != null) {
			collector.submitMovingBlock(poseStack, state.trial, 0);
			return;
		}
		if (state.trialMesh != null) {
			var mesh = state.trialMesh;
			int light = state.lightCoords;
			poseStack.pushPose();
			poseStack.translate(0.5F, 0.0F, 0.5F);
			poseStack.mulPose(Axis.YP.rotationDegrees(switch (state.facing) {
				case WEST -> 90.0F;
				case SOUTH -> 180.0F;
				case EAST -> -90.0F;
				default -> 0.0F;
			}));
			if (!state.trialCentred) poseStack.translate(-0.5F, 0.0F, -0.5F);
			collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(state.trialTexture),
					(pose, vc) -> mesh.emit(pose, vc, light, OverlayTexture.NO_OVERLAY));
			poseStack.popPose();
			return;
		}
		BlenderWeaponMesh mesh = BlenderWeaponMesh.getFromResource(PROP_OBJ_PREFIX + state.propId);
		if (mesh.isEmpty()) return;

		poseStack.pushPose();
		// Anchor at bottom-center of master block; Blender prop meshes have origin at master cell bottom-center
		// and extend along +X (right) and +Z (back) when facing NORTH (front facing -Z in MC world)!
		poseStack.translate(0.5F, 0.0F, 0.5F);
		float yaw = switch (state.facing) {
			case NORTH -> 0.0F;
			case WEST -> 90.0F;
			case SOUTH -> 180.0F;
			case EAST -> -90.0F;
			default -> 0.0F;
		};
		poseStack.mulPose(Axis.YP.rotationDegrees(yaw));

		final int packedLight = state.lightCoords;
		if (mesh.shaded.length > 0) {
			final float[] buf = mesh.shaded;
			collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(WHITE_TEX), (pose, vc) -> {
				for (int i = 0; i < buf.length; i += 27) {
					emitShaded(pose, vc, buf, i, packedLight);
					emitShaded(pose, vc, buf, i + 9, packedLight);
					emitShaded(pose, vc, buf, i + 18, packedLight);
					emitShaded(pose, vc, buf, i + 18, packedLight);
				}
			});
		}

		if (mesh.emitMain.length > 0 || mesh.emitHot.length > 0 || mesh.emitAlt.length > 0) {
			int[] cols = propEmissivePalette(state.propId);
			final int mR = (cols[0] >> 16) & 0xFF, mG = (cols[0] >> 8) & 0xFF, mB = cols[0] & 0xFF;
			final int hR = (cols[1] >> 16) & 0xFF, hG = (cols[1] >> 8) & 0xFF, hB = cols[1] & 0xFF;
			final int aR = (cols[2] >> 16) & 0xFF, aG = (cols[2] >> 8) & 0xFF, aB = cols[2] & 0xFF;

			collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(WHITE_TEX), (pose, vc) -> {
				emitColored(pose, vc, mesh.emitMain, mR, mG, mB);
				emitColored(pose, vc, mesh.emitHot, hR, hG, hB);
				emitColored(pose, vc, mesh.emitAlt, aR, aG, aB);
			});

			final int bmR = (int) (mR * 0.42F), bmG = (int) (mG * 0.42F), bmB = (int) (mB * 0.42F);
			final int bhR = (int) (hR * 0.50F), bhG = (int) (hG * 0.50F), bhB = (int) (hB * 0.50F);
			final int baR = (int) (aR * 0.42F), baG = (int) (aG * 0.42F), baB = (int) (aB * 0.42F);
			collector.submitCustomGeometry(poseStack, RenderTypes.eyes(WHITE_TEX), (pose, vc) -> {
				emitColored(pose, vc, mesh.emitMain, bmR, bmG, bmB);
				emitColored(pose, vc, mesh.emitHot, bhR, bhG, bhB);
				emitColored(pose, vc, mesh.emitAlt, baR, baG, baB);
			});
		}

		poseStack.popPose();
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 96;
	}

	private static int[] propEmissivePalette(String id) {
		if (id.contains("red_anomaly")) return new int[]{0xFF223C, 0xFF98A8, 0xCC1828};
		if (id.contains("crystal_vein")) return new int[]{0x3CE4FF, 0xD0FAFF, 0xA848FF};
		if (id.contains("neon_sign") || id.contains("arcade")) return new int[]{0xFF38B8, 0xFFF0FA, 0x2CE8FF};
		if (id.contains("boiler") || id.contains("chandelier") || id.contains("floor_lamp")) return new int[]{0xFFB844, 0xFFF4C8, 0xFF6622};
		if (id.contains("vending") || id.contains("water_cooler")) return new int[]{0x56E4FF, 0xE0FAFF, 0xFF4448};
		if (id.contains("computer") || id.contains("crt_tv") || id.contains("phone")) return new int[]{0x48E898, 0xD8FFF0, 0x44A8FF};
		if (id.contains("noclipped")) return new int[]{0x8C58FF, 0xE6D4FF, 0x28D8FF};
		return new int[]{0x66D8FF, 0xE4F8FF, 0xFF5252};
	}

	private static void emitShaded(PoseStack.Pose pose, VertexConsumer vc, float[] b, int o, int light) {
		int r = Math.max(0, Math.min(255, (int) (b[o + 6] * 255.0F)));
		int g = Math.max(0, Math.min(255, (int) (b[o + 7] * 255.0F)));
		int bl = Math.max(0, Math.min(255, (int) (b[o + 8] * 255.0F)));
		vc.addVertex(pose, b[o], b[o + 1], b[o + 2])
				.setColor(r, g, bl, 255)
				.setUv(0.5F, 0.5F)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, b[o + 3], b[o + 4], b[o + 5]);
	}

	private static void emitColored(PoseStack.Pose pose, VertexConsumer vc, float[] buf, int r, int g, int b) {
		for (int i = 0; i < buf.length; i += 18) {
			emitEmissive(pose, vc, buf, i, r, g, b);
			emitEmissive(pose, vc, buf, i + 6, r, g, b);
			emitEmissive(pose, vc, buf, i + 12, r, g, b);
			emitEmissive(pose, vc, buf, i + 12, r, g, b);
		}
	}

	private static void emitEmissive(PoseStack.Pose pose, VertexConsumer vc, float[] buf, int o, int r, int g, int b) {
		vc.addVertex(pose, buf[o], buf[o + 1], buf[o + 2])
				.setColor(r, g, b, 255)
				.setUv(0.5F, 0.5F)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(FULL_BRIGHT)
				.setNormal(pose, buf[o + 3], buf[o + 4], buf[o + 5]);
	}
}
