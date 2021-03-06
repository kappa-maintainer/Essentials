package com.Da_Technomancer.essentials.blocks;

import com.Da_Technomancer.essentials.tileentities.SpeedHopperTileEntity;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.List;

public class SpeedHopper extends SortingHopper{

	protected SpeedHopper(){
		super(Properties.create(Material.IRON).sound(SoundType.METAL).hardnessAndResistance(2));
		String name = "speed_hopper";
		setRegistryName(name);
		ESBlocks.toRegister.add(this);
		ESBlocks.blockAddQue(this);
	}

	@Override
	public TileEntity createNewTileEntity(IBlockReader world){
		return new SpeedHopperTileEntity();
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void addInformation(ItemStack stack, @Nullable IBlockReader world, List<ITextComponent> tooltip, ITooltipFlag advanced){
		tooltip.add(new TranslationTextComponent("tt.essentials.speed_hopper.sort"));
		tooltip.add(new TranslationTextComponent("tt.essentials.speed_hopper.desc"));
	}
}
