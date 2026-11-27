package dev.noclip.backrooms.monsters.windowentity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AnimationState;

/** Renders Entity 2 ("Window") peeking from behind misty glass, with glowing pinprick eyes at close range. */
public final class WindowEntityRenderer extends MobRenderer<WindowEntity, WindowEntityRenderer.State, WindowEntityModel> {
	private static final Identifier TEXTURE = BackroomsMod.id("textures/entity/window_entity.png");
	private static final RenderType EYES = RenderTypes.eyes(BackroomsMod.id("textures/entity/window_entity_eyes.png"));

	public WindowEntityRenderer(EntityRendererProvider.Context context) {
		super(context, new WindowEntityModel(context.bakeLayer(WindowEntityModel.LAYER)), 0.0F);
		addLayer(new EyesLayer<>(this) {
			@Override
			public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, State state,
					float yRot, float xRot) {
				if (state.windowBound && state.camDist > 9.0F) return;
				super.submit(poseStack, collector, light, state, yRot, xRot);
			}

			@Override
			public RenderType renderType() {
				return EYES;
			}
		});
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(WindowEntity mob, State state, float partialTick) {
		super.extractRenderState(mob, state, partialTick);
		state.windowBound = mob.isWindowBound();
		state.beckoning = mob.isBeckoning();
		state.lunge.copyFrom(mob.lungeAnimation);
		state.banish.copyFrom(mob.banishAnimation);
		state.camDist = mob.isWindowBound()
				? (float) Minecraft.getInstance().gameRenderer.mainCamera().position().distanceTo(mob.position())
				: 0.0F;
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	public static final class State extends LivingEntityRenderState {
		boolean windowBound;
		boolean beckoning;
		float camDist;
		final AnimationState lunge = new AnimationState();
		final AnimationState banish = new AnimationState();
	}
}
