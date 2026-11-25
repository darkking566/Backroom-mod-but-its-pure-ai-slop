package dev.noclip.backrooms.props;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Shared lightweight BlockEntity for all Blender-rendered Backrooms props (both multi-block
 * master/filler structures and sculpted single-block noclipped furniture).
 */
public final class PropBlockEntity extends BlockEntity {
	private BlockPos masterPos;

	public PropBlockEntity(BlockPos pos, BlockState state) {
		super(BackroomsProps.PROP_BLOCK_ENTITY, pos, state);
		this.masterPos = pos;
	}

	public boolean isMaster() {
		return !(getBlockState().getBlock() instanceof PropPartBlock);
	}

	public BlockPos masterPos() {
		return isMaster() ? worldPosition : (masterPos != null ? masterPos : worldPosition);
	}

	public void setMasterPos(BlockPos pos) {
		this.masterPos = pos;
		setChanged();
	}

	@Override
	protected void saveAdditional(ValueOutput out) {
		super.saveAdditional(out);
		BlockPos m = masterPos();
		out.putInt("mx", m.getX());
		out.putInt("my", m.getY());
		out.putInt("mz", m.getZ());
	}

	@Override
	protected void loadAdditional(ValueInput in) {
		super.loadAdditional(in);
		int x = in.getIntOr("mx", worldPosition.getX());
		int y = in.getIntOr("my", worldPosition.getY());
		int z = in.getIntOr("mz", worldPosition.getZ());
		this.masterPos = new BlockPos(x, y, z);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
