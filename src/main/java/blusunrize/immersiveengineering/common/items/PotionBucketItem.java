package blusunrize.immersiveengineering.common.items;

import blusunrize.immersiveengineering.api.utils.CapabilityUtils;
import blusunrize.immersiveengineering.common.fluids.PotionFluid;
import blusunrize.immersiveengineering.common.register.IEItems.Misc;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class PotionBucketItem extends IEBaseItem
{
	public PotionBucketItem()
	{
		super(new Properties().stacksTo(1), CreativeModeTab.TAB_BREWING);
	}

	public static ItemStack forPotion(Potion type)
	{
		if(type==Potions.WATER||type==null)
			return new ItemStack(Items.WATER_BUCKET);
		ItemStack result = new ItemStack(Misc.POTION_BUCKET);
		result.getOrCreateTag().putString("Potion", type.getRegistryName().toString());
		return result;
	}

	public static Potion getPotion(ItemStack stack)
	{
		return PotionFluid.fromTag(stack.getTag());
	}

	@Override
	public void fillItemCategory(@Nonnull CreativeModeTab group, @Nonnull NonNullList<ItemStack> items)
	{
		if(!allowdedIn(group))
			return;
		List<Potion> sortedPotions = new ArrayList<>(ForgeRegistries.POTIONS.getValues());
		sortedPotions.sort(Comparator.comparing(e -> getPotionName(e).getString()));
		for(Potion p : sortedPotions)
			if(p!=Potions.WATER)
				items.add(forPotion(p));
	}

	@Nullable
	@Override
	public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt)
	{
		return new FluidHandler(stack);
	}

	@Nonnull
	@Override
	public Component getName(@Nonnull ItemStack stack)
	{
		return new TranslatableComponent(
				"item.immersiveengineering.potion_bucket", getPotionName(getPotion(stack))
		);
	}

	private static Component getPotionName(Potion potion)
	{
		String potionKey = potion.getName(Items.POTION.getDescriptionId()+".effect.");
		return new TranslatableComponent(potionKey);
	}

	@Nonnull
	@Override
	public InteractionResultHolder<ItemStack> use(
			@Nonnull Level worldIn, @Nonnull Player playerIn, @Nonnull InteractionHand handIn
	)
	{
		HitResult rayTraceResult = getPlayerPOVHitResult(worldIn, playerIn, ClipContext.Fluid.NONE);
		ItemStack stack = playerIn.getItemInHand(handIn);
		InteractionResultHolder<ItemStack> forgeResult = ForgeEventFactory.onBucketUse(playerIn, worldIn, stack, rayTraceResult);
		if(forgeResult!=null)
			return forgeResult;
		else
			return InteractionResultHolder.pass(stack);
	}

	@Override
	public void appendHoverText(
			@Nonnull ItemStack stack, @Nullable Level worldIn, @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flagIn
	)
	{
		PotionUtils.addPotionTooltip(stack, tooltip, 1.0F);
	}

	private static class FluidHandler implements IFluidHandlerItem, ICapabilityProvider
	{
		private final ItemStack stack;
		private boolean empty = false;

		private FluidHandler(ItemStack stack)
		{
			this.stack = stack;
		}

		private FluidStack getFluid()
		{
			if(empty)
				return FluidStack.EMPTY;
			else
				return PotionFluid.getFluidStackForType(getPotion(stack), FluidAttributes.BUCKET_VOLUME);
		}

		@Nonnull
		@Override
		public ItemStack getContainer()
		{
			return empty?new ItemStack(Items.BUCKET): stack;
		}

		@Override
		public int getTanks()
		{
			return 1;
		}

		@Nonnull
		@Override
		public FluidStack getFluidInTank(int tank)
		{
			if(tank==0)
				return getFluid();
			else
				return FluidStack.EMPTY;
		}

		@Override
		public int getTankCapacity(int tank)
		{
			return tank==0?FluidAttributes.BUCKET_VOLUME: 0;
		}

		@Override
		public boolean isFluidValid(int tank, @Nonnull FluidStack stack)
		{
			return false;
		}

		@Override
		public int fill(FluidStack resource, FluidAction action)
		{
			return 0;
		}

		@Nonnull
		@Override
		public FluidStack drain(FluidStack resource, FluidAction action)
		{
			FluidStack fluid = getFluid();
			if(!fluid.isFluidEqual(resource)||!Objects.equals(fluid.getTag(), resource.getTag()))
				return FluidStack.EMPTY;
			return drain(resource.getAmount(), action);
		}

		@Nonnull
		@Override
		public FluidStack drain(int maxDrain, FluidAction action)
		{
			if(empty||stack.getCount() > 1||maxDrain < FluidAttributes.BUCKET_VOLUME)
				return FluidStack.EMPTY;

			FluidStack potion = getFluid();
			if(action.execute())
				empty = true;
			return potion;
		}

		private final LazyOptional<IFluidHandlerItem> lazyOpt = CapabilityUtils.constantOptional(this);

		@Nonnull
		@Override
		public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side)
		{
			if(cap==CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY)
				return lazyOpt.cast();
			else
				return LazyOptional.empty();
		}
	}
}
