/*
 * BluSunrize
 * Copyright (c) 2017
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.common.util.compat.jei.mixer;

import blusunrize.immersiveengineering.api.Lib;
import blusunrize.immersiveengineering.api.crafting.MixerRecipe;
import blusunrize.immersiveengineering.client.utils.GuiHelper;
import blusunrize.immersiveengineering.common.register.IEBlocks;
import blusunrize.immersiveengineering.common.util.compat.jei.IERecipeCategory;
import blusunrize.immersiveengineering.common.util.compat.jei.JEIHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidType;

import java.util.Arrays;

public class MixerRecipeCategory extends IERecipeCategory<MixerRecipe>
{
	public static final ResourceLocation UID = new ResourceLocation(Lib.MODID, "mixer");
	private final IDrawableStatic tankTexture;
	private final IDrawableStatic tankOverlay;
	private final IDrawableStatic arrowDrawable;

	public MixerRecipeCategory(IGuiHelper helper)
	{
		super(MixerRecipe.class, helper, UID, "block.immersiveengineering.mixer");
		setBackground(helper.createBlankDrawable(155, 60));
		setIcon(new ItemStack(IEBlocks.Multiblocks.MIXER));
		ResourceLocation background = new ResourceLocation(Lib.MODID, "textures/gui/mixer.png");
		tankTexture = helper.createDrawable(background, 68, 8, 74, 60);
		tankOverlay = helper.drawableBuilder(background, 177, 31, 20, 51).addPadding(-2, 2, -2, 2).build();
		arrowDrawable = helper.createDrawable(background, 178, 17, 18, 13);
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, MixerRecipe recipe, IFocusGroup focuses)
	{
		builder.addSlot(RecipeIngredientRole.INPUT, 48, 3)
				.setFluidRenderer(4*FluidType.BUCKET_VOLUME, false, 58, 47)
				.addIngredients(VanillaTypes.FLUID, recipe.fluidInput.getMatchingFluidStacks())
				.addTooltipCallback(JEIHelper.fluidTooltipCallback);

		builder.addSlot(RecipeIngredientRole.OUTPUT, 139, 3)
				.setFluidRenderer(4*FluidType.BUCKET_VOLUME, false, 16, 47)
				.setOverlay(tankOverlay, 0, 0)
				.addIngredient(VanillaTypes.FLUID, recipe.fluidOutput)
				.addTooltipCallback(JEIHelper.fluidTooltipCallback);

		for(int i = 0; i < recipe.itemInputs.length; i++)
		{
			int x = (i%2)*18+1;
			int y = i/2*18+1;
			builder.addSlot(RecipeIngredientRole.INPUT, x, y)
					.addItemStacks(Arrays.asList(recipe.itemInputs[i].getMatchingStacks()))
					.setBackground(JEIHelper.slotDrawable, -1, -1);
		}
	}

	@Override
	public void draw(MixerRecipe recipe, IRecipeSlotsView recipeSlotsView, PoseStack transform, double mouseX, double mouseY)
	{
		tankTexture.draw(transform, 40, 0);
		arrowDrawable.draw(transform, 117, 19);
		GuiHelper.drawSlot(139, 18, 16, 47, transform);
	}

}