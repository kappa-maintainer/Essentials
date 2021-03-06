package com.Da_Technomancer.essentials.tileentities.redstone;

import com.Da_Technomancer.essentials.Essentials;
import com.Da_Technomancer.essentials.blocks.BlockUtil;
import com.Da_Technomancer.essentials.blocks.ESBlocks;
import com.Da_Technomancer.essentials.blocks.ESProperties;
import com.Da_Technomancer.essentials.blocks.redstone.AbstractCircuit;
import com.Da_Technomancer.essentials.blocks.redstone.IRedstoneHandler;
import com.Da_Technomancer.essentials.blocks.redstone.RedstoneUtil;
import com.Da_Technomancer.essentials.packets.IFloatReceiver;
import com.Da_Technomancer.essentials.packets.SendFloatToClient;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.world.TickPriority;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.registries.ObjectHolder;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class CircuitTileEntity extends TileEntity implements IFloatReceiver{

	@ObjectHolder(Essentials.MODID + ":circuit")
	private static TileEntityType<CircuitTileEntity> TYPE = null;

	public boolean builtConnections = false;
	private final ArrayList<WeakReference<LazyOptional<IRedstoneHandler>>> dependents = new ArrayList<>(1);
	private final ArrayList<Pair<WeakReference<LazyOptional<IRedstoneHandler>>, Orient>> sources = new ArrayList<>(4);

	private LazyOptional<IRedstoneHandler> hanOptional = LazyOptional.of(RedsHandler::new);
	private WeakReference<LazyOptional<IRedstoneHandler>> hanReference = new WeakReference<>(hanOptional);

	private float output = 0;

	public CircuitTileEntity(){
		this(TYPE);
	}

	protected CircuitTileEntity(TileEntityType<?> type){
		super(type);
	}

	/**
	 * Subclasses are encouraged to override this to directly return the block, instead of going through the blockstate
	 * @return The block associated with this tile entity
	 */
	@Nonnull
	protected AbstractCircuit getOwner(){
		Block b = getBlockState().getBlock();
		if(b instanceof AbstractCircuit){
			return (AbstractCircuit) b;
		}
		remove();
		return ESBlocks.consCircuit;
	}

	private Direction getFacing(){
		BlockState s = getBlockState();
		if(s.hasProperty(ESProperties.HORIZ_FACING)){
			return s.get(ESProperties.HORIZ_FACING);
		}
		remove();
		return Direction.NORTH;
	}

	public float getOutput(){
		buildConnections();
		return output;
	}

	/**
	 * Sets the current circuit output to the new power effective immediately, and performs necessary updates
	 * @param newPower The new output power
	 */
	private void setPower(float newPower){
		if(RedstoneUtil.didChange(output, newPower)){
			Direction facing = getFacing();
			//If no dependents, assume we're outputting to vanilla redstone
			if(dependents.isEmpty() && RedstoneUtil.clampToVanilla(output) != RedstoneUtil.clampToVanilla(newPower)){
				output = newPower;
				world.neighborChanged(pos.offset(facing), getOwner(), pos.offset(facing.getOpposite()));
			}
			output = newPower;
			BlockUtil.sendClientPacketAround(world, pos, new SendFloatToClient(0, output, pos));
			for(int i = 0; i < dependents.size(); i++){
				WeakReference<LazyOptional<IRedstoneHandler>> dependent = dependents.get(i);
				IRedstoneHandler handler;
				if(dependent == null || (handler = BlockUtil.get(dependent.get())) == null){
					//Entry is no longer valid- remove for faster future checks
					dependents.remove(i);
					i--;
					continue;
				}

				handler.notifyInputChange(hanReference);
			}

			markDirty();
		}
	}

	/**
	 * Calculates the received & sanitized redstone signal strengths on each of the 3 possible input sides
	 * Sides that don't accept input will be 0 in the output array
	 * Virtual server side only
	 * The result is not cached- do not call this method more than needed
	 * @param owner The circuit block, as returned by getOwner()
	 * @return A size 3 float array of redstone inputs, in order CCW, BACK, CW
	 */
	protected float[] getInputs(AbstractCircuit owner){
		buildConnections();//Can be needed when reloading

		float[] inputs = new float[3];
		boolean[] hasSrc = new boolean[3];

		for(int i = 0; i < sources.size(); i++){
			Pair<WeakReference<LazyOptional<IRedstoneHandler>>, Orient> src = sources.get(i);
			WeakReference<LazyOptional<IRedstoneHandler>> ref;
			IRedstoneHandler handl;
			//Remove invalid entries to speed up future checks
			if(src == null || (ref = src.getLeft()) == null || (handl = BlockUtil.get(ref.get())) == null){
				sources.remove(i);
				i--;
				continue;
			}

			int ind = src.getRight().ordinal();
			if(ind > 2){
				IndexOutOfBoundsException e = new IndexOutOfBoundsException("Input into redstone device on the front! Pos: " + pos.toString() + "; Dim: " + world.getDimensionKey().toString() + "Type: " + getOwner().getRegistryName().toString());
				Essentials.logger.catching(e);
				//Invalid state- remove this input and skip
				sources.remove(i);
				i--;
				continue;
			}

			float newInput = RedstoneUtil.sanitize(handl.getOutput());
			inputs[ind] = RedstoneUtil.chooseInput(inputs[ind], newInput);
			hasSrc[ind] = true;
		}

		Direction facing = getFacing();

		//Any input without a circuit input uses vanilla redstone instead
		for(int i = 0; i < 3; i++){
			if(!hasSrc[i] && owner.useInput(Orient.values()[i])){
				Direction dir = Orient.values()[i].getFacing(facing);
				inputs[i] = RedstoneUtil.getRedstoneOnSide(world, pos, dir);
			}
		}

		return inputs;
	}

	/**
	 * Forces the circuit to recalculate the power output, and propagate any changes to the world and dependents
	 * Virtual server side ONLY
	 */
	public void recalculateOutput(){
		AbstractCircuit owner = getOwner();
		float[] inputs = getInputs(owner);

		float newOutput;
		try{
			newOutput = owner.getOutput(inputs[0], inputs[1], inputs[2], this);
		}catch(ArithmeticException e){
			newOutput = 0;
		}
		setPower(RedstoneUtil.sanitize(newOutput));
	}

	public void buildConnections(){
		if(!builtConnections && !world.isRemote){
			builtConnections = true;
			dependents.clear();
			sources.clear();
			AbstractCircuit own = getOwner();
			Direction dir = getFacing();
			for(Orient or : Orient.INPUTS){
				if(own.useInput(or)){
					Direction checkDir = or.getFacing(dir);
					TileEntity te = world.getTileEntity(pos.offset(checkDir));
					IRedstoneHandler otherHandler;
					if(te != null && (otherHandler = BlockUtil.get(te.getCapability(RedstoneUtil.REDSTONE_CAPABILITY, checkDir.getOpposite()))) != null){
						otherHandler.requestSrc(hanReference, 0, checkDir.getOpposite(), checkDir);
					}
				}
			}

			TileEntity te = world.getTileEntity(pos.offset(dir));
			IRedstoneHandler otherHandler;
			if(te != null && (otherHandler = BlockUtil.get(te.getCapability(RedstoneUtil.REDSTONE_CAPABILITY, dir.getOpposite()))) != null){
				otherHandler.findDependents(hanReference, 0, dir.getOpposite(), dir);
			}

			handleInputChange(TickPriority.NORMAL);
		}
	}

	@Override
	public void updateContainingBlockInfo(){
		super.updateContainingBlockInfo();

		output = 0;
		builtConnections = false;
		dependents.clear();
		sources.clear();
		hanOptional.invalidate();
		hanOptional = LazyOptional.of(RedsHandler::new);
		hanReference = new WeakReference<>(hanOptional);
		if(world != null && !world.isRemote){
			BlockUtil.sendClientPacketAround(world, pos, new SendFloatToClient(0, output, pos));
			buildConnections();
			markDirty();
		}
	}

	@Override
	public void remove(){
		super.remove();
		hanOptional.invalidate();
	}

	@Override
	public void receiveFloat(byte id, float value, @Nullable ServerPlayerEntity sender){
		if(id == 0 && world.isRemote){
			output = value;
		}
	}

	@Override
	public void read(BlockState state, CompoundNBT nbt){
		super.read(state, nbt);
		output = nbt.getFloat("pow");
	}

	@Override
	public CompoundNBT write(CompoundNBT nbt){
		super.write(nbt);
		nbt.putFloat("pow", output);
		return nbt;
	}

	@Override
	public CompoundNBT getUpdateTag(){
		return write(new CompoundNBT());
	}

	@Nonnull
	@Override
	@SuppressWarnings("unchecked")
	public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side){
		if(cap == RedstoneUtil.REDSTONE_CAPABILITY){
			Direction dir = getFacing();
			if(side == null || side.getAxis() != Direction.Axis.Y && (dir == side || getOwner().useInput(Orient.getOrient(side, dir)))){
				return (LazyOptional<T>) hanOptional;
			}
		}
		return super.getCapability(cap, side);
	}

	/**
	 * Called when the received input strength may have changed
	 * @param priority The priority this tick should be scheduled with
	 */
	public void handleInputChange(TickPriority priority){
		world.getPendingBlockTicks().scheduleTick(pos, getOwner(), RedstoneUtil.DELAY, priority);
	}

	private class RedsHandler implements IRedstoneHandler{

		@Override
		public float getOutput(){
			return output;
		}

		@Override
		public void findDependents(WeakReference<LazyOptional<IRedstoneHandler>> src, int dist, Direction fromSide, Direction nominalSide){
			LazyOptional<IRedstoneHandler> srcOption = src.get();
			Orient or = Orient.getOrient(fromSide, getFacing());
			if(getOwner().useInput(or) && srcOption != null && srcOption.isPresent()){
				IRedstoneHandler srcHandler = BlockUtil.get(srcOption);
				srcHandler.addDependent(hanReference, nominalSide);
				Pair<WeakReference<LazyOptional<IRedstoneHandler>>, Orient> toAdd = Pair.of(src, or);
				if(!sources.contains(toAdd)){
					sources.add(toAdd);
				}
			}
		}

		@Override
		public void requestSrc(WeakReference<LazyOptional<IRedstoneHandler>> dependency, int dist, Direction toSide, Direction nominalSide){
			LazyOptional<IRedstoneHandler> depenOption;
			if(Orient.getOrient(toSide, getFacing()) == Orient.FRONT && (depenOption = dependency.get()) != null && depenOption.isPresent()){
				IRedstoneHandler depHandler = BlockUtil.get(depenOption);
				depHandler.addSrc(hanReference, nominalSide);
				if(!dependents.contains(dependency)){
					dependents.add(dependency);
				}
			}
		}

		@Override
		public void addSrc(WeakReference<LazyOptional<IRedstoneHandler>> src, Direction fromSide){
			Orient or = Orient.getOrient(fromSide, getFacing());
			if(or != null && or != Orient.FRONT && getOwner().useInput(or)){
				Pair<WeakReference<LazyOptional<IRedstoneHandler>>, Orient> toAdd = Pair.of(src, or);
				if(!sources.contains(toAdd)){
					sources.add(toAdd);
					notifyInputChange(src);
				}
			}
		}

		@Override
		public void addDependent(WeakReference<LazyOptional<IRedstoneHandler>> dependent, Direction toSide){
			Orient or = Orient.getOrient(toSide, getFacing());
			if(or == Orient.FRONT && !dependents.contains(dependent)){
				dependents.add(dependent);
			}
		}

		@Override
		public void notifyInputChange(WeakReference<LazyOptional<IRedstoneHandler>> src){
			handleInputChange(TickPriority.HIGH);
		}
	}

	public enum Orient{

		CCW(),//Counter-clockwise of front (input)
		BACK(),//Rear input
		CW(),//Clockwise of front (input)
		FRONT();//Output

		public static final Orient[] INPUTS = {CCW, BACK, CW};

		public static Orient getOrient(Direction dir, Direction front){
			if(front != null){
				if(front == dir){
					return FRONT;
				}else if(front.getOpposite() == dir){
					return BACK;
				}else if(front.rotateY() == dir){
					return CW;
				}else if(front.rotateYCCW() == dir){
					return CCW;
				}
			}
			throw new IllegalArgumentException(String.format("front &/or dir are vertical/null. Front: %s; Dir: %s", front == null ? "NULL" : front.toString(), dir == null ? "NULL" : dir.toString()));
		}

		public Direction getFacing(Direction front){
			switch(this){
				case FRONT:
					return front;
				case BACK:
					return front.getOpposite();
				case CW:
					return front.rotateY();
				case CCW:
					return front.rotateYCCW();
				default:
					throw new IllegalStateException("Unhandled Orientation: " + name());
			}
		}
	}
}
