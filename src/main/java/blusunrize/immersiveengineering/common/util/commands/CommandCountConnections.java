package blusunrize.immersiveengineering.common.util.commands;

import blusunrize.immersiveengineering.api.energy.wires.IImmersiveConnectable;
import blusunrize.immersiveengineering.api.energy.wires.ImmersiveNetHandler;
import blusunrize.immersiveengineering.common.Config;
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

import java.lang.ref.SoftReference;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CommandCountConnections extends CommandBase {

    private static final String HELP_MESSAGE = "/ie count-connections [valid] [invalid] [force-cache-update] [paging [page size] <page number>]";

    private static final Long CACHE_TTL = TimeUnit.MINUTES.toMillis(5);

    private static final int DEFAULT_PAGE_SIZE = 20;

    private volatile SoftReference<CachedInfo> cachedInfo = new SoftReference<>(null);

    @Override
    public int getRequiredPermissionLevel() {
        return Config.IEConfig.countConnectionsCommandPermissionLevel;
    }

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
        boolean forcedCacheUpdate = contains(args, "force-cache-update");

        int paginationArgIndex = indexOf(args, "paging");
        boolean paginationEnabled = paginationArgIndex != -1;
        int pageSize = -1;
        int page = -1;
        if (paginationEnabled) {
            try {
                pageSize = Integer.parseInt(args[paginationArgIndex + 1]);
            } catch (Exception e) {
                sender.sendMessage(new TextComponentString(HELP_MESSAGE));
                return;
            }
            try {
                page = Integer.parseInt(args[paginationArgIndex + 2]);
            } catch (Exception e) {
                page = pageSize;
                pageSize = DEFAULT_PAGE_SIZE;
            }
        }

        try {
            CachedInfo cache = getAndUpdateConnectionsInfo(server, forcedCacheUpdate);
            AllConnectionsInfo allConnectionsInfo = cache.allConnectionsInfo;

            StringBuilder sb = new StringBuilder();

            print_valid:
            {
                if (contains(args, "valid")) {
                    sb.append("\n\n===== VALID CONNECTIONS =====");

                    if (allConnectionsInfo.validConnections.isEmpty()) {
                        sb.append("\nNo valid connections found.");
                        break print_valid;
                    }

                    appendDetailedConnections(
                        sb,
                        allConnectionsInfo.validConnections,
                        paginationEnabled,
                        pageSize,
                        page
                    );
                }
            }

            print_invalid:
            {
                if (contains(args, "invalid")) {
                    sb.append("\n\n===== INVALID CONNECTIONS =====");

                    if (allConnectionsInfo.invalidConnections.isEmpty()) {
                        sb.append("\nNo invalid connections found.");
                        break print_invalid;
                    }

                    appendDetailedConnections(
                        sb,
                        allConnectionsInfo.invalidConnections,
                        paginationEnabled,
                        pageSize,
                        page
                    );
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

            sb.append("\nData fetched ")
                .append(String.format("%.3f", (MinecraftServer.getCurrentTimeMillis() - cache.updatedAt) / 1000.0))
                .append(" seconds ago.");

            if (args.length == 0 || !contains(args, "valid") && !contains(args, "invalid")) {
                sb.append("\nFor detailed info use optional args: ").append(HELP_MESSAGE);
            }

            sender.sendMessage(new TextComponentString(sb.toString()));
        } catch (Exception e) {
            throw new CommandException("Internal error", e);
        }
    }

    private void appendDetailedConnections(
        StringBuilder sb,
        List<ConnectionInfo> connections,
        boolean paginationEnabled,
        int pageSize,
        int page
    ) {
        int start = paginationEnabled ? (page - 1) * pageSize : 0;
        int end = paginationEnabled ? page * pageSize : connections.size();

        if (start != 0) {
            sb.append("\n...")
                .append(Math.min(start, connections.size()))
                .append(" records left behind...");
        }

        for (int i = start; i < end; i++) {
            appendConnection(i, sb, connections.get(i));
        }

        if (end < connections.size()) {
            sb.append("\n...")
                .append(connections.size() - end)
                .append(" records are ahead...");
        }
    }

    private void appendConnection(int index, StringBuilder sb, ConnectionInfo connectionInfo) {
        Integer dimensionId = connectionInfo.dimensionId;

        sb.append("\n").append(index).append(": dimension [").append(dimensionId).append("], connection [");
        appendBlockPos(sb, connectionInfo.start).append("]-[");
        appendBlockPos(sb, connectionInfo.end).append("]");
    }

    private StringBuilder appendBlockPos(StringBuilder sb, BlockPos bp) {
        sb.append("x:").append(bp.getX())
            .append(" y:").append(bp.getY())
            .append(" z:").append(bp.getZ());
        return sb;
    }

    private int indexOf(Object[] array, Object value) {
        for (int i = 0, arrayLength = array.length; i < arrayLength; i++) {
            Object val = array[i];
            if (Objects.equals(val, value)) {
                return i;
            }
        }
        return -1;
    }

    private boolean contains(Object[] array, Object value) {
        return indexOf(array, value) != -1;
    }

    private CachedInfo getAndUpdateConnectionsInfo(MinecraftServer server, boolean forceUpdate) {
        Predicate<CachedInfo> shouldUpdate =
            cache -> cache == null || forceUpdate || cache.updatedAt + CACHE_TTL < MinecraftServer.getCurrentTimeMillis();
        // Provide strong reference first
        CachedInfo cache = cachedInfo.get();
        if (shouldUpdate.test(cache)) {
            synchronized (this) {
                if (shouldUpdate.test(cachedInfo.get())) {
                    cache = new CachedInfo(getConnectionsInfo(server));
                    cachedInfo = new SoftReference<>(cache);
                }
            }
        }
        assert cache != null;
        return cache;
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
                ConnectionInfo connectionInfo = new ConnectionInfo(dimensionId, connection.start, connection.end);
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
        private final BlockPos start;
        private final BlockPos end;

        private ConnectionInfo(
            int dimensionId,
            BlockPos start,
            BlockPos end
        ) {
            this.dimensionId = dimensionId;
            this.start = start;
            this.end = end;
        }
    }

    private static class AllConnectionsInfo {
        private final List<ConnectionInfo> validConnections = new ArrayList<>();
        private final List<ConnectionInfo> invalidConnections = new ArrayList<>();
    }

    private static class CachedInfo {
        private final AllConnectionsInfo allConnectionsInfo;
        private final Long updatedAt;

        private CachedInfo(AllConnectionsInfo allConnectionsInfo) {
            this.allConnectionsInfo = allConnectionsInfo;
            this.updatedAt = MinecraftServer.getCurrentTimeMillis();
        }
    }
}
