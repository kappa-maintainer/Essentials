package com.Da_Technomancer.essentials.blocks.redstone;

import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.LazyOptional;

import java.lang.ref.WeakReference;
import java.util.HashSet;

public class DefaultRedstoneHandler implements IRedstoneHandler{

	@Override
	public float getOutput(){
		return 0;
	}

	@Override
	public void findDependents(WeakReference<LazyOptional<IRedstoneHandler>> src, int dist, Direction fromSide, Direction nominalSide){

	}

	@Override
	public void requestSrc(WeakReference<LazyOptional<IRedstoneHandler>> dependency, int dist, Direction toSide, Direction nominalSide){

	}

	@Override
	public void addSrc(WeakReference<LazyOptional<IRedstoneHandler>> src, Direction fromSide){

	}

	@Override
	public void addDependent(WeakReference<LazyOptional<IRedstoneHandler>> dependent, Direction toSide){

	}

	@Override
	public void notifyInputChange(WeakReference<LazyOptional<IRedstoneHandler>> src){

	}
}
