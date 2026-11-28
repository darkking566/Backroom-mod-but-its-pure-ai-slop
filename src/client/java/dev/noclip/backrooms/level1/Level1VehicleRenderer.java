package dev.noclip.backrooms.level1;

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
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Renders full-scale Level 1 sedan and cargo van OBJ meshes anchored at their master block. */
public final class Level1VehicleRenderer implements BlockEntityRenderer<Level1VehicleBlockEntity, Level1VehicleRenderer.VehicleRenderState> {
	private static final Identifier WHITE_TEX = BackroomsMod.id("textures/item/blender_white.png");
	private static final String OBJ_PREFIX = "/assets/noclip_backrooms/models/blender_props/";
	private static final int FULL_BRIGHT = 0xF000F0;

	public static final class VehicleRenderState extends BlockEntityRenderState {
		public String modelId = null;
		public Direction facing = Direction.NORTH;
	}

	public Level1VehicleRenderer(BlockEntityRendererProvider.Context ctx) {
	}

	@Override
	public VehicleRenderState createRenderState() {
		return new VehicleRenderState();
	}

	@Override
	public void extractRenderState(Level1VehicleBlockEntity entity, VehicleRenderState state, float partialTicks,
			Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
		BlockEntityRenderState.extractBase(entity, state, crumblingOverlay);
		BlockState bs = entity.getBlockState();
		if (bs.is(Level1Blocks.ABANDONED_VAN)) state.modelId = "abandoned_van";
		else if (bs.is(Level1Blocks.ABANDONED_CAR)) state.modelId = "abandoned_car";
		else state.modelId = null;
		state.facing = bs.hasProperty(HorizontalDirectionalBlock.FACING)
				? bs.getValue(HorizontalDirectionalBlock.FACING)
				: Direction.NORTH;
	}

	@Override
	public void submit(VehicleRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.modelId == null) return;
		BlenderWeaponMesh mesh = BlenderWeaponMesh.getFromResource(OBJ_PREFIX + state.modelId);
		if (mesh.isEmpty()) return;

		poseStack.pushPose();
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
			collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(WHITE_TEX), (pose, vc) -> {
				emitColored(pose, vc, mesh.emitMain, 90, 184, 210);
				emitColored(pose, vc, mesh.emitHot, 244, 236, 200);
				emitColored(pose, vc, mesh.emitAlt, 215, 42, 42);
			});
			collector.submitCustomGeometry(poseStack, RenderTypes.eyes(WHITE_TEX), (pose, vc) -> {
				emitColored(pose, vc, mesh.emitMain, 45, 92, 105);
				emitColored(pose, vc, mesh.emitHot, 130, 125, 105);
				emitColored(pose, vc, mesh.emitAlt, 110, 22, 22);
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
