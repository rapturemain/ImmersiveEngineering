package blusunrize.immersiveengineering.common.util.commands;

import blusunrize.immersiveengineering.api.energy.wires.IImmersiveConnectable;
import blusunrize.immersiveengineering.api.energy.wires.ImmersiveNetHandler;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IntHashMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import org.apache.commons.lang3.tuple.Triple;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CommandCountInvalidConnection extends CommandBase {

    private static final String HELP_MESSAGE = "/ie count-connections [valid] [invalid]";

    @Override
    public String getName() {
        return "count-connections";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return HELP_MESSAGE;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        try {
            server.logInfo("[IMMERSIVE ENGINEERING] High-Load command executed: /ie count-connections");
            AllConnectionsInfo allConnectionsInfo = getConnectionsInfo(server);

            StringBuilder sb = new StringBuilder();

            if (contains(args, "valid")) {
                sb.append("\n\n===== VALID CONNECTIONS =====");
                for (int i = 0; i < allConnectionsInfo.validConnections.size(); i++) {
                    appendConnection(i, sb, allConnectionsInfo.validConnections.get(i));
                }
                if (allConnectionsInfo.validConnections.size() == 0) {
                    sb.append("\n No valid connections found.");
                }
            }

            if (contains(args, "invalid")) {
                sb.append("\n\n===== INVALID CONNECTIONS =====");
                for (int i = 0; i < allConnectionsInfo.invalidConnections.size(); i++) {
                    appendConnection(i, sb, allConnectionsInfo.invalidConnections.get(i));
                }
                if (allConnectionsInfo.invalidConnections.size() == 0) {
                    sb.append("\n No invalid connections found.");
                }
            }

            if (sb.length() != 0) {
                sb.append("\n\n===== TOTAL =====");
            }

            sb.append("\nTotal: valid connections - ")
                .append(allConnectionsInfo.validConnections.size())
                .append(", invalid connections - ")
                .append(allConnectionsInfo.invalidConnections.size())
                .append(" found.");

            if (args.length == 0 || !contains(args, "valid") && !contains(args, "invalid")) {
                sb.append("\nFor detailed info use optional args: ").append(HELP_MESSAGE);
            }

            sender.sendMessage(new TextComponentString(sb.toString()));
        } catch (Exception e) {
            throw new CommandException("Internal error", e);
        }
    }

    private void appendConnection(int index, StringBuilder sb, ConnectionInfo connectionInfo) {
        Integer dimensionId = connectionInfo.dimensionId;
        ImmersiveNetHandler.Connection connection = connectionInfo.connection;

        sb.append("\n").append(index).append(": dimension [").append(dimensionId).append("], connection [");
        appendBlockPos(sb, connection.start).append("]-[");
        appendBlockPos(sb, connection.end).append("]\n");
    }

    private StringBuilder appendBlockPos(StringBuilder sb, BlockPos bp) {
        sb.append("x:").append(bp.getX())
            .append(" y:").append(bp.getY())
            .append(" z:").append(bp.getZ());
        return sb;
    }

    private boolean contains(Object[] array, Object value) {
        for (Object val : array) {
            if (Objects.equals(val, value)) {
                return true;
            }
        }
        return false;
    }

    private AllConnectionsInfo getConnectionsInfo(MinecraftServer server) {
        AllConnectionsInfo allConnectionsInfo = new AllConnectionsInfo();

        IntHashMap<Map<BlockPos, ImmersiveNetHandler.BlockWireInfo>> blockWires = ImmersiveNetHandler.INSTANCE.blockWireMap;

        Function<Integer, Set<ImmersiveNetHandler.Connection>> getDimensionConnections = dimensionId ->
            Optional.ofNullable(blockWires.lookup(dimensionId))
                .orElse(Collections.emptyMap())
                .values()
                .stream()
                .collect(
                    HashSet::new,
                    (set, it) -> {
                        set.addAll(it.in.stream().map(Triple::getLeft).collect(Collectors.toSet()));
                        set.addAll(it.near.stream().map(Triple::getLeft).collect(Collectors.toSet()));
                    },
                    HashSet::addAll
                );

        int[] dimensionIds = DimensionManager.getRegisteredDimensions()
            .values()
            .stream()
            .flatMap(Collection::stream)
            .mapToInt(Integer::intValue)
            .toArray();

        for (int dimensionId : dimensionIds) {
            Set<ImmersiveNetHandler.Connection> connections = getDimensionConnections.apply(dimensionId);
            if (connections.isEmpty()) {
                continue;
            }

            WorldServer world = server.getWorld(dimensionId);

            for (ImmersiveNetHandler.Connection connection : connections) {
                ConnectionInfo connectionInfo = new ConnectionInfo(dimensionId, connection);
                if (isIEConnectable(world, connection.start) && isIEConnectable(world, connection.end)) {
                    allConnectionsInfo.validConnections.add(connectionInfo);
                } else {
                    allConnectionsInfo.invalidConnections.add(connectionInfo);
                }
            }
        }

        return allConnectionsInfo;
    }

    private boolean isIEConnectable(WorldServer world, BlockPos blockPos) {
        TileEntity tileEntity = world.getTileEntity(blockPos);
        return tileEntity instanceof IImmersiveConnectable;
    }

    private static class ConnectionInfo {
        private final int dimensionId;
        private final ImmersiveNetHandler.Connection connection;

        private ConnectionInfo(int dimensionId, ImmersiveNetHandler.Connection connection) {
            this.dimensionId = dimensionId;
            this.connection = connection;
        }
    }

    private static class AllConnectionsInfo {
        private final List<ConnectionInfo> validConnections = new ArrayList<>();
        private final List<ConnectionInfo> invalidConnections = new ArrayList<>();
    }
}
