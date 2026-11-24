package dev.noclip.backrooms.monsters.bonethief;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AnimationState;

public final class BoneThiefRenderer extends MobRenderer<BoneThief, BoneThiefRenderer.State, BoneThiefModel> {
	private static final Identifier TEXTURE = BackroomsMod.id("textures/entity/bone_thief.png");
	private static final RenderType EYES = RenderTypes.eyes(BackroomsMod.id("textures/entity/bone_thief_eyes.png"));

	public BoneThiefRenderer(EntityRendererProvider.Context context) {
		super(context, new BoneThiefModel(context.bakeLayer(BoneThiefModel.LAYER)), 0.78F);
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
	public void extractRenderState(BoneThief mob, State state, float partialTick) {
		super.extractRenderState(mob, state, partialTick);
		state.aiState = mob.aiState();
		state.chomp.copyFrom(mob.chompAnimation);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	public static final class State extends LivingEntityRenderState {
		int aiState;
		final AnimationState chomp = new AnimationState();
	}
}
