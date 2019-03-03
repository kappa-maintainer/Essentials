package com.Da_Technomancer.essentials.blocks;

import com.Da_Technomancer.essentials.EssentialsConfig;
import com.Da_Technomancer.essentials.WorldBuffer;
import com.Da_Technomancer.essentials.items.EssentialsItems;
import net.minecraft.block.Block;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

import java.util.ArrayList;
import java.util.LinkedHashSet;

/**
 * Notable differences from a normal piston include:
 * 15 block head range, distance controlled by signal strength,
 * No quasi-connectivity,
 * Redstone can be placed on top of the piston,
 * Piston extension and retraction is instant, no 2-tick delay or rendering of block movement.
 * Can move up to 64 blocks at a time instead of 12
 */
public class MultiPistonBase extends Block{

	private static final int DIST_LIMIT = 15;
	private static final int PUSH_LIMIT = 64;
	private final boolean sticky;


	protected MultiPistonBase(boolean sticky){
		super(Material.PISTON);
		String name = "multi_piston" + (sticky ? "_sticky" : "");
		setTranslationKey(name);
		setRegistryName(name);
		this.sticky = sticky;
		setHardness(0.5F);
		setCreativeTab(EssentialsItems.TAB_ESSENTIALS);
		setDefaultState(getDefaultState().withProperty(EssentialsProperties.FACING, EnumFacing.NORTH).withProperty(EssentialsProperties.EXTENDED, false));
		EssentialsBlocks.toRegister.add(this);
		EssentialsBlocks.blockAddQue(this);
	}

	@Override
	public IBlockState getStateForPlacement(World worldIn, BlockPos pos, EnumFacing blockFaceClickedOn, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer){
		return getDefaultState().withProperty(EssentialsProperties.FACING, EnumFacing.getDirectionFromEntityLiving(pos, placer));
	}

	@Override
	public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ){
		if(EssentialsConfig.isWrench(playerIn.getHeldItem(hand), worldIn.isRemote) && !state.getValue(EssentialsProperties.EXTENDED)){
			if(!worldIn.isRemote){
				IBlockState endState = state.cycleProperty(EssentialsProperties.FACING);
				worldIn.setBlockState(pos, endState);
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean isOpaqueCube(IBlockState state){
		return !state.getValue(EssentialsProperties.EXTENDED);
	}

	@Override
	public boolean isFullCube(IBlockState state){
		return !state.getValue(EssentialsProperties.EXTENDED);
	}

	@Override
	public boolean isSideSolid(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side){
		return side != state.getValue(EssentialsProperties.FACING) || !state.getValue(EssentialsProperties.EXTENDED);
	}

	@Override
	public BlockFaceShape getBlockFaceShape(IBlockAccess worldIn, IBlockState state, BlockPos pos, EnumFacing face){
		return face == state.getValue(EssentialsProperties.FACING) && state.getValue(EssentialsProperties.EXTENDED) ? BlockFaceShape.UNDEFINED : BlockFaceShape.SOLID;
	}

	private static final AxisAlignedBB[] BB = new AxisAlignedBB[] {new AxisAlignedBB(0, 5D / 16D, 0, 1, 1, 1), new AxisAlignedBB(0, 0, 0, 1, 11D / 16D, 1), new AxisAlignedBB(0, 0, 5D / 16D, 1, 1, 1), new AxisAlignedBB(0, 0, 0, 1, 1, 11D / 16D), new AxisAlignedBB(5D / 16D, 0, 0, 1, 1, 1), new AxisAlignedBB(0, 0, 0, 11D / 16D, 1, 1)};

	@Override
	public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos){
		if(state.getValue(EssentialsProperties.EXTENDED)){
			return BB[state.getValue(EssentialsProperties.FACING).getIndex()];
		}else{
			return FULL_BLOCK_AABB;
		}
	}

	@Override
	public void breakBlock(World world, BlockPos pos, IBlockState state){
		if(state.getValue(EssentialsProperties.EXTENDED)){
			world.setBlockToAir(pos.offset(state.getValue(EssentialsProperties.FACING)));
		}
	}

	@Override
	public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack){
		neighborChanged(world.getBlockState(pos), world, pos, null, null);
	}

	@Override
	public boolean shouldCheckWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side){
		return true;
	}

	@Override
	public void neighborChanged(IBlockState state, World worldIn, BlockPos pos, Block blockIn, BlockPos fromPos){
		if(worldIn.isRemote || changingWorld){
			return;
		}
		activate(worldIn, pos, state);
	}

	@Override
	protected BlockStateContainer createBlockState(){
		return new BlockStateContainer(this, EssentialsProperties.FACING, EssentialsProperties.EXTENDED);
	}

	@Override
	public IBlockState getStateFromMeta(int meta){
		return this.getDefaultState().withProperty(EssentialsProperties.FACING, EnumFacing.byIndex(meta & 7)).withProperty(EssentialsProperties.EXTENDED, (meta & 8) == 8);
	}

	@Override
	public int getMetaFromState(IBlockState state){
		return state.getValue(EssentialsProperties.FACING).getIndex() + (state.getValue(EssentialsProperties.EXTENDED) ? 8 : 0);
	}

	@Override
	public EnumPushReaction getPushReaction(IBlockState state){
		//If extended, this can not be moved. Otherwise it can be moved
		return state.getValue(EssentialsProperties.EXTENDED) ? EnumPushReaction.BLOCK : EnumPushReaction.NORMAL;
	}

	//While true, a multipiston is actively changing the world around it and should ignore incoming block updates
	protected static boolean changingWorld = false;

	private void activate(World world, BlockPos pos, IBlockState state){
		int target = 0;

		EnumFacing facing = state.getValue(EssentialsProperties.FACING);

		for(EnumFacing dir : EnumFacing.VALUES){
			//Don't measure redstone power from the front, as otherwise we end up in an infinite loop just by placing a redstone block there
			if(dir != facing){
				target = Math.max(target, world.getRedstonePower(pos.offset(dir), dir));
			}
		}

		//A fairly common bug in mods and newly added vanilla blocks is ways to get redstone signal strengths over 15.
		target = Math.min(target, DIST_LIMIT);

		if(facing == EnumFacing.UP){
			//Account for world height limits when pointed up
			target = Math.min(target, world.getHeight() - pos.getY() - 1);
		}else if(facing == EnumFacing.DOWN){
			//Check for world floor
			target = Math.min(target, pos.getY());
		}

		int currentExtension = 0;

		if(state.getValue(EssentialsProperties.EXTENDED)){
			BlockPos checkPos = pos.offset(facing);
			IBlockState curState = world.getBlockState(checkPos);
			Block tarBlock = sticky ? EssentialsBlocks.multiPistonExtendSticky : EssentialsBlocks.multiPistonExtend;

			//Find the current extension
			//The distance limit check is in case people mess around with setblock commands
			EnumFacing.AxisDirection dir;
			while(curState.getBlock() == tarBlock && curState.getValue(EssentialsProperties.AXIS) == facing.getAxis() && (dir = MultiPistonExtend.getDirFromHead(curState.getValue(EssentialsProperties.HEAD))) != facing.getOpposite().getAxisDirection() && currentExtension != DIST_LIMIT){
				currentExtension++;
				checkPos = checkPos.offset(facing);
				curState = world.getBlockState(checkPos);
				if(dir != null){
					//Sanity check to make sure we don't have an invalid extension line
					break;
				}
			}
		}

		if(currentExtension == target){
			//No work needs to be done
			return;
		}

		changingWorld = true;

		WorldBuffer wBuf = new WorldBuffer(world);

		if(currentExtension < target){
			for(int i = currentExtension; i < target; i++){
				if(shiftExtension(wBuf, pos, facing, i, true)){
					target = i - 1;
					break;
				}
			}
		}else{
			for(int i = currentExtension; i > target; i--){
				shiftExtension(wBuf, pos, facing, i, false);
			}
		}
		wBuf.applyChanges();


		if(currentExtension == 0 ^ target == 0){
			world.setBlockState(pos, state.withProperty(EssentialsProperties.EXTENDED, target != 0));
		}
		changingWorld = false;
	}

	/**
	 * Adjusts the extension of the multipiston by one block in a WorldBuffer, and moves entities in the path
	 * @param world The WorldBuffer to read/write changes from
	 * @param pos The multipiston position
	 * @param facing The multipiston facing
	 * @param currentExtension The current extension of the multipiston (in the WorldBuffer)
	 * @param out Increase the extension if true, decrease otherwise
	 * @return true if this action was blocked
	 */
	private boolean shiftExtension(WorldBuffer world, BlockPos pos, EnumFacing facing, int currentExtension, boolean out){
		EnumFacing moveDir = out ? facing : facing.getOpposite();
		LinkedHashSet<BlockPos> movedBlocks = new LinkedHashSet<>(PUSH_LIMIT + 1);
		BlockPos prevHeadPos = pos.offset(facing, currentExtension);
		Block extendBlock = sticky ? EssentialsBlocks.multiPistonExtendSticky : EssentialsBlocks.multiPistonExtend;
		if(!out){
			//Temporarily add the piston head to prevent it blocking movement paths
			movedBlocks.add(prevHeadPos);
		}
		//Only build a moveset if we are moving out or we are retracting with a sticky head
		if((out || sticky) && buildMoveset(pos, world, prevHeadPos.offset(facing), moveDir, movedBlocks, !out)){
			//Something is in the way
			if(!out){
				//If retracting, leave attached blocks behind but finish retracting
				movedBlocks.clear();
			}else{
				//If extending, stop here
				return true;
			}
		}
		if(!out){
			movedBlocks.remove(prevHeadPos);
		}

		//Change the current head
		if(currentExtension != 0){
			world.addChange(prevHeadPos, out ? extendBlock.getDefaultState().withProperty(EssentialsProperties.AXIS, facing.getAxis()) : Blocks.AIR.getDefaultState());
		}

		for(BlockPos changePos : movedBlocks){

			//Move blocks forward
			IBlockState prevState = world.getBlockState(changePos);
			if(prevState.getPushReaction() == EnumPushReaction.DESTROY){
				world.getWorld().destroyBlock(changePos, true);//Destroy the block in the actual world to drop items
			}else{
				world.addChange(changePos.offset(moveDir), prevState);
			}
			world.addChange(changePos, Blocks.AIR.getDefaultState());

			//Find and move entities
			moveEnts(world.getWorld(), changePos, moveDir, isStickyBlock(prevState));
		}

		//Find and move entities at the piston head itself
		moveEnts(world.getWorld(), prevHeadPos, moveDir, false);

		if(out){
			//Add the extended head
			world.addChange(pos.offset(facing, currentExtension + 1), extendBlock.getDefaultState().withProperty(EssentialsProperties.AXIS, facing.getAxis()).withProperty(EssentialsProperties.HEAD, facing.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE ? 1 : 2));
		}else if(currentExtension != 1){
			//Add the retracted head
			world.addChange(pos.offset(facing, currentExtension - 1), extendBlock.getDefaultState().withProperty(EssentialsProperties.AXIS, facing.getAxis()).withProperty(EssentialsProperties.HEAD, facing.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE ? 1 : 2));
		}

		return false;
	}

	private void moveEnts(World world, BlockPos activePos, EnumFacing moveDir, boolean sticky){
		AxisAlignedBB BB = new AxisAlignedBB(activePos.offset(moveDir));
		ArrayList<Entity> movingEnts = new ArrayList<>(4);
		getEntitiesMultiChunk(BB, world, movingEnts);
		for(Entity ent : movingEnts){
			if(ent.getPushReaction() != EnumPushReaction.IGNORE){
				ent.setPositionAndUpdate(ent.posX + (double) moveDir.getXOffset(), ent.posY + (double) moveDir.getYOffset(), ent.posZ + (double) moveDir.getZOffset());
				//If the entity is on a "sticky" block, bounce them
				if(sticky){
					ent.addVelocity(moveDir.getXOffset(), moveDir.getYOffset(), moveDir.getZOffset());
					ent.velocityChanged = true;
				}
			}
		}
		movingEnts.clear();
	}

	private boolean buildMoveset(BlockPos pistonPos, WorldBuffer world, BlockPos curPos, EnumFacing moveDir, LinkedHashSet<BlockPos> movedBlocks, boolean dragging){
		if(movedBlocks.contains(curPos)){
			return false;
		}
		IBlockState state = world.getBlockState(curPos);
		EnumPushReaction reaction = state.getPushReaction();
		if(state.getBlock().isAir(state, world.getWorld(), curPos)){
			reaction = EnumPushReaction.IGNORE;//Vanilla marks air as normal. This is an impressively stupid decision- it means we have to special case it
		}else if(state.getBlock() == Blocks.OBSIDIAN || state.getBlock().hasTileEntity(state) || pistonPos.equals(curPos)){
			reaction = EnumPushReaction.BLOCK;//Guess what else is marked as normal? That's right, obsidian. You know, the quintessential unmovable block. It's special cased. whhhhyyyyyyyyyy?
		}

		boolean blocked = false;
		switch(reaction){
			case PUSH_ONLY:
				if(dragging){
					break;
				}
			case NORMAL:
				//Check for world height
				if(moveDir == EnumFacing.UP && curPos.getY() == world.getWorld().getHeight() || moveDir == EnumFacing.DOWN && curPos.getY() == 0){
					blocked = true;
					break;
				}

				blocked = movedBlocks.size() > PUSH_LIMIT || buildMoveset(pistonPos, world, curPos.offset(moveDir), moveDir, movedBlocks, false);

				movedBlocks.add(curPos);

				//Do blocks behind this if sticky
				if(isStickyBlock(state)){
					blocked = blocked || buildMoveset(pistonPos, world, curPos.offset(moveDir.getOpposite()), moveDir, movedBlocks, true) || blocked;
				}

				//Do blocks on the sides if sticky
				if(isStickyBlock(state)){
					for(EnumFacing side : EnumFacing.VALUES){
						if(side.getAxis() != moveDir.getAxis()){
							blocked = blocked || buildMoveset(pistonPos, world, curPos.offset(side), moveDir, movedBlocks, true) || blocked;
						}
					}
				}

				break;
			case DESTROY:
				if(!dragging){
					movedBlocks.add(curPos);
				}
				break;
			case BLOCK:
				blocked = !dragging;
				break;
			case IGNORE:
				break;
		}

		return blocked || movedBlocks.size() > PUSH_LIMIT;
	}

	/**
	 * An alternate version of World#getEntitiesWithinAABBExcludingEntity that checks a 3x3x3 cube of mini chunks (16x16x16 cubes within chunks) for entities.
	 * This is less efficient than the standard method, but necessary to fix a vanilla bug whereby repeated getEntity calls break if entities were moved between chunks in the same tick.
	 */
	private static void getEntitiesMultiChunk(AxisAlignedBB checkBox, World worldIn, ArrayList<Entity> entList){
		int i = MathHelper.floor((checkBox.minX - World.MAX_ENTITY_RADIUS) / 16.0D) - 1;
		int j = MathHelper.floor((checkBox.maxX + World.MAX_ENTITY_RADIUS) / 16.0D) + 1;
		int k = MathHelper.floor((checkBox.minZ - World.MAX_ENTITY_RADIUS) / 16.0D) - 1;
		int l = MathHelper.floor((checkBox.maxZ + World.MAX_ENTITY_RADIUS) / 16.0D) + 1;

		int yMin = MathHelper.clamp(MathHelper.floor((checkBox.minY - World.MAX_ENTITY_RADIUS) / 16.0D) - 1, 0, 15);
		int yMax = MathHelper.clamp(MathHelper.floor((checkBox.maxY + World.MAX_ENTITY_RADIUS) / 16.0D) + 1, 0, 15);

		for(int iLoop = i; iLoop <= j; iLoop++){
			for(int kLoop = k; kLoop <= l; kLoop++){
				if(((ChunkProviderServer) worldIn.getChunkProvider()).chunkExists(iLoop, kLoop)){
					Chunk chunk = worldIn.getChunk(iLoop, kLoop);
					for(int yLoop = yMin; yLoop <= yMax; yLoop++){
						if(!chunk.getEntityLists()[yLoop].isEmpty()){
							for(Entity entity : chunk.getEntityLists()[yLoop]){
								if(entity.getEntityBoundingBox().intersects(checkBox)){
									entList.add(entity);
								}
							}
						}
					}
				}
			}
		}
	}
}