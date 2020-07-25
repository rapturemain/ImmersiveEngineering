/*
 * BluSunrize
 * Copyright (c) 2020
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.api.wires.proxy;

import blusunrize.immersiveengineering.api.wires.Connection;
import blusunrize.immersiveengineering.api.wires.ConnectionPoint;
import blusunrize.immersiveengineering.api.wires.IImmersiveConnectable;
import com.google.common.collect.Lists;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.BlockPos;

import java.util.Collection;

public interface IICProxyProvider
{
	CompoundNBT toNBT(IImmersiveConnectable proxy);

	IImmersiveConnectable fromNBT(CompoundNBT nbt);

	IImmersiveConnectable create(BlockPos pos, Collection<Connection> internal, Collection<ConnectionPoint> points);

	default IImmersiveConnectable createFor(IImmersiveConnectable iic)
	{
		BlockPos pos = iic.getPosition();
		Collection<Connection> internal = Lists.newArrayList(iic.getInternalConnections());
		Collection<ConnectionPoint> points = iic.getConnectionPoints();
		return create(pos, internal, points);
	}
}
