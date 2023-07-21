package mcjty.rftoolsdim.dimensions;

import mcjty.lib.varia.Logging;
import mcjty.lib.varia.MathTools;
import mcjty.rftoolsdim.config.DimletRules;
import mcjty.rftoolsdim.config.GeneralConfiguration;
import mcjty.rftoolsdim.config.PowerConfiguration;
import mcjty.rftoolsdim.dimensions.description.DimensionDescriptor;
import mcjty.rftoolsdim.dimensions.world.GenericWorldProvider;
import mcjty.rftoolsdim.items.ModItems;
import mcjty.rftoolsdim.items.PhasedFieldGeneratorItem;
import mcjty.rftoolsdim.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ClassInheritanceMultiMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.File;
import java.util.*;

public class RfToolsDimensionManager extends WorldSavedData {
    public static final String DIMMANAGER_NAME = "RFToolsDimensionManager";
    private static RfToolsDimensionManager instance = null;
    private static RfToolsDimensionManager clientInstance = null;

    private final Map<Integer, DimensionDescriptor> dimensions = new HashMap<>();
    private final Map<DimensionDescriptor, Integer> dimensionToID = new HashMap<>();
    private final Map<Integer, DimensionInformation> dimensionInformation = new HashMap<>();

    private final Set<Integer> reclaimedIds = new HashSet<>();

    @SideOnly(Side.CLIENT)
    public void syncFromServer(Map<Integer, DimensionDescriptor> dims, Map<Integer, DimensionInformation> dimInfo) {
        for (Map.Entry<Integer, DimensionDescriptor> entry : dims.entrySet()) {
            int id = entry.getKey();
            DimensionDescriptor descriptor = entry.getValue();
            if (dimensions.containsKey(id)) {
                dimensionToID.remove(dimensions.get(id));
            }
            dimensions.put(id, descriptor);
            dimensionToID.put(descriptor, id);
        }

        WorldClient world = Minecraft.getMinecraft().world;
        GenericWorldProvider provider = (world != null && world.provider instanceof GenericWorldProvider) ? (GenericWorldProvider) world.provider : null;
        for (Map.Entry<Integer, DimensionInformation> entry : dimInfo.entrySet()) {
            int id = entry.getKey();
            DimensionInformation info = entry.getValue();
            dimensionInformation.put(id, info);
            if (provider != null && provider.getDimension() == id) {
                provider.setDimensionInformation(info);
            }
        }
    }

    public RfToolsDimensionManager(String identifier) {
        super(identifier);
    }

    public static void clearInstance() {
        if (instance != null) {
            instance.dimensions.clear();
            instance.dimensionToID.clear();
            instance.dimensionInformation.clear();
            instance.reclaimedIds.clear();
            instance = null;
        }
    }

    public static void cleanupDimensionInformation() {
        if (instance != null) {
            Logging.log("Cleaning up RFTools dimensions");
            unregisterDimensions();
            instance.getDimensions().clear();
            instance.dimensionToID.clear();
            instance.dimensionInformation.clear();
            instance.reclaimedIds.clear();
            instance = null;
        }
    }

    public static void unregisterDimensions() {
        for (Map.Entry<Integer, DimensionDescriptor> me : instance.getDimensions().entrySet()) {
            int id = me.getKey();
            if (DimensionManager.isDimensionRegistered(id)) {
                Logging.log("    Unregister dimension: " + id);
                try {
                    DimensionManager.unregisterDimension(id);
                } catch (Exception e) {
                    // We ignore this error.
                    Logging.log("        Could not unregister dimension: " + id);
                }
            } else {
                Logging.log("    Already unregistered! Dimension: " + id);
            }
        }
    }

    public void save(World world) {
        world.getMapStorage().setData(DIMMANAGER_NAME, this);
        markDirty();

        syncDimInfoToClients(world);
    }

    public void reclaimId(int id) {
        reclaimedIds.add(id);
    }

    /**
     * Freeze a dimension: avoid ticking all tile entities and remove all
     * active entities (they are still there but will not do anything).
     * Entities that are within range of a player having a PFG will be kept
     * active (but not tile entities).
     */
    public static void freezeDimension(World world) {
        // First find all players that have a valid PFG.
        List<BlockPos> pfgList = new ArrayList<>();
        int radius = PowerConfiguration.phasedFieldGeneratorRange;
        if (radius > 0) {
            for (EntityPlayer player : world.playerEntities) {
                // Check if this player has a valid PFG but don't consume energy.
                int cost = 0;
                if (PowerConfiguration.dimensionDifficulty != -1) {
                    DimensionInformation information = ((GenericWorldProvider) world.provider).getDimensionInformation();
                    cost = information.getActualRfCost();
                    if (cost == 0) {
                        DimensionDescriptor descriptor = information.getDescriptor();
                        cost = descriptor.getRfMaintainCost();
                    }
                }
                if (checkValidPhasedFieldGenerator(player, false, cost)) {
                    pfgList.add(new BlockPos((int) player.posX, (int) player.posY, (int) player.posZ));
                }
            }
        }

        // If there are players with a valid PFG then we check if there are entities we want to keep.
        List<Entity> tokeep = new ArrayList<>();
        tokeep.addAll(world.playerEntities);    // We want to keep all players for sure.
        // Add all entities that are within range of a PFG.
        for (BlockPos coordinate : pfgList) {
            getEntitiesInSphere(world, coordinate, radius, tokeep);
        }

        world.loadedEntityList.clear();
        world.loadedEntityList.addAll(tokeep);

        world.loadedTileEntityList.clear();
    }

    private static void getEntitiesInSphere(World world, BlockPos c, float radius, List<Entity> tokeep) {
        int i = MathTools.floor((c.getX() - radius) / 16.0D);
        int j = MathTools.floor((c.getX() + 1 + radius) / 16.0D);
        int k = MathTools.floor((c.getZ() - radius) / 16.0D);
        int l = MathTools.floor((c.getZ() + 1 + radius) / 16.0D);

        for (int i1 = i; i1 <= j; ++i1) {
            for (int j1 = k; j1 <= l; ++j1) {
                if (world.getChunkProvider().getLoadedChunk(i1, j1) != null) {
                    Chunk chunk = world.getChunk(i1, j1);
                    getEntitiesInSphere(chunk, c, radius, tokeep);
                }
            }
        }
    }

    private static void getEntitiesInSphere(Chunk chunk, BlockPos c, float radius, List<Entity> entities) {
        float squaredRange = radius * radius;
        int i = MathTools.floor((c.getY() - radius) / 16.0D);
        int j = MathTools.floor((c.getY() + 1 + radius) / 16.0D);

        i = i < 0 ? 0 : (i > chunk.getEntityLists().length - 1 ? chunk.getEntityLists().length - 1 : i);
        j = j < 0 ? 0 : (j > chunk.getEntityLists().length - 1 ? chunk.getEntityLists().length - 1 : j);

        for (int k = i; k <= j; ++k) {
            ClassInheritanceMultiMap<Entity> entityList = chunk.getEntityLists()[k];
            for (Entity entity : entityList) {
                if (!(entity instanceof EntityPlayer)) {
                    float sqdist = squaredDistance(c, new BlockPos((int) entity.posX, (int) entity.posY, (int) entity.posZ));
                    if (sqdist < squaredRange) {
                        entities.add(entity);
                        break;
                    }
                }
            }
        }
    }

    public static float squaredDistance(BlockPos p1, BlockPos c) {
        return ((c.getX() - p1.getX()) * (c.getX() - p1.getX()) + (c.getY() - p1.getY()) * (c.getY() - p1.getY()) + (c.getZ() - p1.getZ()) * (c.getZ() - p1.getZ()));
    }


    public static void unfreezeDimension(World world) {
        WorldServer worldServer = (WorldServer) world;
        for (Chunk chunk : worldServer.getChunkProvider().getLoadedChunks()) {
            unfreezeChunk(chunk);
        }
    }

    public static void unfreezeChunk(Chunk chunk) {
        chunk.getWorld().addTileEntities(chunk.getTileEntityMap().values());
        for (ClassInheritanceMultiMap<Entity> entityList : chunk.getEntityLists()) {
            chunk.getWorld().loadedEntityList.addAll(entityList);
        }
    }

    public static boolean checkValidPhasedFieldGenerator(EntityPlayer player, boolean consume, int tickCost) {
        InventoryPlayer inventory = player.inventory;
        for (int i = 0; i < InventoryPlayer.getHotbarSize(); i++) {
            ItemStack slot = inventory.getStackInSlot(i);
            if (!slot.isEmpty() && slot.getItem() == ModItems.phasedFieldGeneratorItem) {
                PhasedFieldGeneratorItem pfg = (PhasedFieldGeneratorItem) slot.getItem();
                int energyStored = pfg.getEnergyStored(slot);
                int toConsume;
                if (GeneralConfiguration.enableDynamicPhaseCost) {
                    toConsume = (int) (DimensionTickEvent.MAXTICKS * tickCost * GeneralConfiguration.dynamicPhaseCostAmount);
                } else {
                    toConsume = (int) (DimensionTickEvent.MAXTICKS * PowerConfiguration.PHASEDFIELD_CONSUMEPERTICK);
                }
                if (energyStored >= toConsume) {
                    if (consume) {
                        pfg.extractEnergy(slot, toConsume, false);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Sync dimlet rules to the client
     */
    public void syncDimletRules(EntityPlayer player) {
        if (!player.getEntityWorld().isRemote) {
            // Send over dimlet configuration to the client so that the client can check that the id's match.
            Logging.log("Send dimlet rules to the client");
            RFToolsDimMessages.INSTANCE.sendTo(new PacketSyncRules(DimletRules.getRules()), (EntityPlayerMP) player);
        }
    }

    public void syncDimInfoToClients(World world) {
        if (!world.isRemote) {
            // Sync to clients.
            Logging.log("Sync dimension info to clients!");
            RFToolsDimMessages.INSTANCE.sendToAll(new PacketSyncDimensionInfo(dimensions, dimensionInformation));
        }
    }

    public DimensionSyncPacket makeDimensionSyncPacket() {
        return new DimensionSyncPacket(dimensions, dimensionInformation);
    }

    public Map<Integer, DimensionDescriptor> getDimensions() {
        return dimensions;
    }

    public void registerDimensions() {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<Integer, DimensionDescriptor> me : dimensions.entrySet()) {
            int id = me.getKey();
            builder.append(id);
            builder.append(' ');
            registerDimensionToServerAndClient(id);
        }
        Logging.log("Registering RFTools dimensions: " + builder.toString());
    }

    private void registerDimensionToServerAndClient(int id) {
        if (!DimensionManager.isDimensionRegistered(id)) {
            DimensionManager.registerDimension(id, ModDimensions.rftoolsType);
        }
        if (DimensionManager.getWorld(id) == null) {
            File chunkDir = new File(DimensionManager.getCurrentSaveRootDirectory(), DimensionManager.createProviderFor(id).getSaveFolder());
            if (ForgeChunkManager.savedWorldHasForcedChunkTickets(chunkDir)) {
                DimensionManager.initDimension(id);
            }
        }
        RFToolsDimMessages.INSTANCE.sendToAll(new PacketRegisterDimensions(id));
    }

    public static RfToolsDimensionManager getDimensionManagerClient() {
        if (clientInstance == null) {
            clientInstance = new RfToolsDimensionManager(DIMMANAGER_NAME);
        }
        return clientInstance;
    }

    public static RfToolsDimensionManager getDimensionManagerClientNullable(World world) {
        return clientInstance;
    }

    public static RfToolsDimensionManager getDimensionManager(World world) {
        if (instance != null) {
            return instance;
        }
        instance = (RfToolsDimensionManager) world.getMapStorage().getOrLoadData(RfToolsDimensionManager.class, DIMMANAGER_NAME);
        if (instance == null) {
            instance = new RfToolsDimensionManager(DIMMANAGER_NAME);
        }
        return instance;
    }

    public DimensionDescriptor getDimensionDescriptor(int id) {
        return dimensions.get(id);
    }

    public Integer getDimensionID(DimensionDescriptor descriptor) {
        return dimensionToID.get(descriptor);
    }

    public DimensionInformation getDimensionInformation(int id) {
        return dimensionInformation.get(id);
    }

    /**
     * Get a world for a dimension, possibly loading it from the configuration manager.
     */
    public static World getWorldForDimension(World world, int id) {
        World w = DimensionManager.getWorld(id);
        if (w == null) {
            w = world.getMinecraftServer().getWorld(id);
        }
        return w;
    }

    public void removeDimension(int id) {
        DimensionDescriptor descriptor = dimensions.get(id);
        dimensions.remove(id);
        dimensionToID.remove(descriptor);
        dimensionInformation.remove(id);
        if (DimensionManager.isDimensionRegistered(id)) {
            DimensionManager.unregisterDimension(id);
        }
    }

    public void recoverDimension(World world, int id, DimensionDescriptor descriptor, String name, String playerName, UUID player) {
        if (!DimensionManager.isDimensionRegistered(id)) {
            registerDimensionToServerAndClient(id);
        }

        DimensionInformation dimensionInfo = new DimensionInformation(name, descriptor, world, playerName, player);

        dimensions.put(id, descriptor);
        dimensionToID.put(descriptor, id);
        dimensionInformation.put(id, dimensionInfo);

        save(world);
        touchSpawnChunk(world, id);
    }

    public int countOwnedDimensions(UUID player) {
        int cnt = 0;
        for (DimensionInformation information : dimensionInformation.values()) {
            if (player.equals(information.getOwner())) {
                ++cnt;
            }

        }
        return cnt;
    }

    public int createNewDimension(World world, DimensionDescriptor descriptor, String name, String playerName, UUID player) {
        int id = 0;
        while (!reclaimedIds.isEmpty()) {
            int rid = reclaimedIds.iterator().next();
            reclaimedIds.remove(rid);
            if (!DimensionManager.isDimensionRegistered(rid)) {
                id = rid;
                break;
            }
        }
        if (id == 0) {
            id = DimensionManager.getNextFreeDimId();
        }

        registerDimensionToServerAndClient(id);
        Logging.log("id = " + id + " for " + name + ", descriptor = " + descriptor.getDescriptionString());


        dimensions.put(id, descriptor);
        dimensionToID.put(descriptor, id);


        try {
            DimensionInformation dimensionInfo = new DimensionInformation(name, descriptor, world, playerName, player);
            dimensionInformation.put(id, dimensionInfo);
        } catch (Exception e) {
            Logging.logError("Something went wrong during creation of the dimension!");
            e.printStackTrace();
        }

        save(world);

        touchSpawnChunk(world, id);
        return id;
    }

    private void touchSpawnChunk(World world, int id) {
        // Make sure world generation kicks in for at least one chunk so that our matter receiver
        // is generated and registered.
        WorldServer worldServerForDimension = world.getMinecraftServer().getWorld(id);
        ChunkProviderServer providerServer = worldServerForDimension.getChunkProvider();
        if (!providerServer.chunkExists(0, 0)) {
            try {
                providerServer.provideChunk(0, 0);
                providerServer.chunkGenerator.populate(0, 0);
            } catch (Exception e) {
                Logging.logError("Something went wrong during creation of the dimension!");
                e.printStackTrace();
                // We catch this exception to make sure our dimension tab is at least ok.
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tagCompound) {
        dimensions.clear();
        dimensionToID.clear();
        dimensionInformation.clear();
        reclaimedIds.clear();
        NBTTagList lst = tagCompound.getTagList("dimensions", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < lst.tagCount(); i++) {
            NBTTagCompound tc = lst.getCompoundTagAt(i);
            int id = tc.getInteger("id");
            DimensionDescriptor descriptor = new DimensionDescriptor(tc);
            dimensions.put(id, descriptor);
            dimensionToID.put(descriptor, id);

            DimensionInformation dimensionInfo = new DimensionInformation(descriptor, tc);
            dimensionInformation.put(id, dimensionInfo);
        }

        int[] lstIds = tagCompound.getIntArray("reclaimedIds");
        for (int id : lstIds) {
            reclaimedIds.add(id);
        }

    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tagCompound) {
        NBTTagList lst = new NBTTagList();
        for (Map.Entry<Integer, DimensionDescriptor> me : dimensions.entrySet()) {
            NBTTagCompound tc = new NBTTagCompound();

            Integer id = me.getKey();
            tc.setInteger("id", id);
            me.getValue().writeToNBT(tc);
            DimensionInformation dimensionInfo = dimensionInformation.get(id);
            dimensionInfo.writeToNBT(tc);

            lst.appendTag(tc);
        }
        tagCompound.setTag("dimensions", lst);

        List<Integer> ids = new ArrayList<>(reclaimedIds);
        int[] lstIds = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            lstIds[i] = ids.get(i);
        }
        tagCompound.setIntArray("reclaimedIds", lstIds);
        return tagCompound;
    }
}
