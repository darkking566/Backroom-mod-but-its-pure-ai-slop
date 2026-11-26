package dev.noclip.backrooms.monsters.windowentity;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AnimationState;

/** Renders Entity 2 ("Window") with faint ice-white emissive pinprick eyes visible through foggy glass. */
public final class WindowEntityRenderer extends MobRenderer<WindowEntity, WindowEntityRenderer.State, WindowEntityModel> {
	private static final Identifier TEXTURE = BackroomsMod.id("textures/entity/window_entity.png");
	private static final RenderType EYES = RenderTypes.eyes(BackroomsMod.id("textures/entity/window_entity_eyes.png"));

	public WindowEntityRenderer(EntityRendererProvider.Context context) {
		super(context, new WindowEntityModel(context.bakeLayer(WindowEntityModel.LAYER)), 0.45F);
		addLayer(new EyesLayer<>(this) {
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
		state.beckoning = mob.isBeckoning();
		state.lunge.copyFrom(mob.lungeAnimation);
		state.banish.copyFrom(mob.banishAnimation);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	public static final class State extends LivingEntityRenderState {
		boolean beckoning;
		final AnimationState lunge = new AnimationState();
		final AnimationState banish = new AnimationState();
	}
}
