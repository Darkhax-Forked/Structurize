package com.structurize.coremod.management;

import com.structurize.api.configuration.Configurations;
import com.structurize.api.util.ChangeStorage;
import com.structurize.api.util.Log;
import com.structurize.api.util.Shape;
import com.structurize.coremod.Structurize;
import com.structurize.coremod.network.messages.SendStructureMessage;
import com.structurize.coremod.util.ScanToolOperation;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.template.Template;
import net.minecraft.world.gen.structure.template.TemplateManager;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.Math.abs;
import static java.lang.Math.sin;

/**
 * Singleton class that links colonies to minecraft.
 */
public final class Manager
{
    /**
     * Indicate if a schematic have just been downloaded.
     * Client only
     */
    private static          boolean schematicDownloaded = false;

    /**
     * List of the last changes to the world.
     */
    private static LinkedList<ChangeStorage> changeQueue = new LinkedList<>();

    /**
     * List of scanTool operations.
     */
    private static LinkedList<ScanToolOperation> scanToolOperationPool = new LinkedList<ScanToolOperation>();

    /**
     * Pseudo unique id for the server
     */
    private static volatile UUID    serverUUID          = null;

    private Manager()
    {
        //Hides default constructor.
    }

    /**
     * Method called on world tick to run cached operations.
     * @param world the world which is ticking.
     */
    public static void onWorldTick(final WorldServer world)
    {
        if (!scanToolOperationPool.isEmpty())
        {
            final ScanToolOperation operation = scanToolOperationPool.peek();
            if (operation != null && operation.apply(world))
            {
                scanToolOperationPool.pop();
                if (!operation.isUndo())
                {
                    addToUndoCache(operation.getChangeStorage());
                }
            }
        }
    }

    /**
     * Add a new item to the scanTool operation queue.
     * @param operation the operation to add.
     */
    public static void addToQueue(final ScanToolOperation operation)
    {
        scanToolOperationPool.push(operation);
    }

    /**
     * Add a new item to the queue.
     * @param storage the storage to add.
     */
    public static void addToUndoCache(final ChangeStorage storage)
    {
        if (changeQueue.size() >= Configurations.gameplay.maxCachedChanges)
        {
            changeQueue.pop();
        }
        changeQueue.push(storage);
    }

    /**
     * Just returns a cube for now, I can tinker this later.
     * @param worldServer the server world.
     * @param width the width.
     * @param length the length.
     * @param height the height.
     * @param shape the shape.
     * @param inputBlock the input block.
     * @param hollow if hollow or not.
     */
    public static void getStructureFromFormula(
      final WorldServer worldServer,
      final int width,
      final int length,
      final int height,
      final int frequency,
      final Shape shape,
      final ItemStack inputBlock, final boolean hollow, final EntityPlayer player)
    {
        final TemplateManager templatemanager = worldServer.getStructureTemplateManager();
        templatemanager.remove(new ResourceLocation("shape" + player.getName() + ".nbt"));
        final Template template = templatemanager.getTemplate(worldServer.getMinecraftServer(), new ResourceLocation("shape" + player.getName() + ".nbt"));

        final IBlockState block = inputBlock.getItem() instanceof ItemBlock ?  ((ItemBlock) inputBlock.getItem()).getBlock().getStateFromMeta(inputBlock.getItemDamage()) : Blocks.GOLD_BLOCK.getDefaultState();

        if (shape == Shape.SPHERE || shape == Shape.HALF_SPHERE || shape == Shape.BOWL)
        {
            generateSphere(template, height/2, block, hollow, shape);
        }
        else if (shape == Shape.CUBE)
        {
            generateCube(template, height, width, length, block, hollow);
        }
        else if (shape == Shape.WAVE)
        {
            generateWave(template, height, width, length, frequency, block, true);
        }
        else if (shape == Shape.WAVE_3D)
        {
            generateWave(template, height, width, length, frequency, block, false);
        }
        Structurize.getNetwork().sendTo(new SendStructureMessage(template.writeToNBT(new NBTTagCompound())), (EntityPlayerMP) player);
    }

    /**
     * Generates a cube with the specific size and adds it to the template provided.
     * @param template the provided template.
     * @param height the height.
     * @param width the width.
     * @param length the length.
     * @param block the block to use.
     * @param hollow if full.
     */
    private static void generateCube(final Template template, final int height, final int width, final int length, final IBlockState block, final boolean hollow)
    {
        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                for (int z = 0; z < length; z++)
                {
                    if (!hollow || ((x == 0 || x == width-1) || (y == 0 || y == height-1) || (z == 0 || z == length-1)))
                    {
                        template.blocks.add(new Template.BlockInfo(new BlockPos(x, y, z), block, null));
                    }
                }
            }
        }
        template.size = new BlockPos(width,height,length);
    }

    /**
     * Generates a hollow sphere with the specific size and adds it to the template provided.
     * @param template the provided template.
     * @param height the height.
     * @param block the block to use.
     * @param hollow if hollow.
     * @param shape the type of shape.
     */
    private static void generateSphere(final Template template, final int height, final IBlockState block, final boolean hollow, final Shape shape)
    {
        final List<BlockPos> posList = new ArrayList<>();
        for (int y = 0; y <= height+1; y++)
        {
            for (int x = 0; x <= height+1; x++)
            {
                for (int z = 0; z <= height+1; z++)
                {
                    int sum = x * x + z * z + y * y;
                    if (sum < height * height && (!hollow || sum > height * height - 2* height))
                    {
                        if (shape == Shape.HALF_SPHERE || shape == Shape.SPHERE)
                        {
                            addPosToList(new BlockPos(x, y, z), posList);
                            addPosToList(new BlockPos(x, y, -z), posList);
                            addPosToList(new BlockPos(-x, y, z), posList);
                            addPosToList(new BlockPos(-x, y, -z), posList);
                        }
                        if (shape == Shape.BOWL || shape == Shape.SPHERE)
                        {
                            addPosToList(new BlockPos(x, -y, z), posList);
                            addPosToList(new BlockPos(x, -y, -z), posList);
                            addPosToList(new BlockPos(-x, -y, z), posList);
                            addPosToList(new BlockPos(-x, -y, -z), posList);
                        }
                    }
                }
            }
        }
        template.size = new BlockPos(height*2,height*2,height*2);
        template.blocks.addAll(posList.stream().map(pos -> new Template.BlockInfo(pos, block, null)).collect(Collectors.toList()));
    }


    /**
     * Generates a wave with the specific size and adds it to the template provided.
     * @param template the provided template.
     * @param height the height.
     * @param width the width.
     * @param length the length.
     * @param block the block to use.
     */
    private static void generateWave(final Template template, final int height, final int width, final int length, final int frequency, final IBlockState block, final boolean flat)
    {
        final List<BlockPos> posList = new ArrayList<>();

        for (int x = 0; x < length; x++)
        {
            for (int z = 0; z < width; z++)
            {
                final double yVal = (flat ? 0 : z) + (double) frequency * Math.sin( x / (double) height);
                addPosToList(new BlockPos(x, yVal, z), posList);
                if (!flat)
                {
                    addPosToList(new BlockPos(x, yVal, -z), posList);
                    addPosToList(new BlockPos(x, yVal + width - 1, z - width + 1), posList);
                    addPosToList(new BlockPos(x, yVal + width - 1, -z + width - 1), posList);
                }
            }
        }

        template.size = new BlockPos(length,height*length+1,width*2+1);
        template.blocks.addAll(posList.stream().map(pos -> new Template.BlockInfo(pos, block, null)).collect(Collectors.toList()));
    }

    public static void generateRandomShape(final Template template, final int height, final int width, final int length)
    {
        final double radiusX = 20;
        final double radiusY = 26;
        final double radiusZ = 5;
        final List<BlockPos> posList = new ArrayList<>();

        for (double x = 0 ; x <= radiusX; x++)
        {
            for (double y = 0; y <= radiusY; y++)
            {
                for (double z = 0; z <= radiusZ; z++)
                {

                }
            }
        }

        template.blocks.addAll(posList.stream().map(pos -> new Template.BlockInfo(pos, Blocks.GOLD_BLOCK.getDefaultState(), null)).collect(Collectors.toList()));
    }

    /**
     * Add the position to list if not already.
     * @param blockPos the pos to add.
     * @param posList the list to add it to.
     */
    private static void addPosToList(final BlockPos blockPos, final List<BlockPos> posList)
    {
        if (!posList.contains(blockPos))
        {
            posList.add(blockPos);
        }
    }

    /**
     * Undo a change to the world made by a player.
     * @param player the player who made it.
     */
    public static void undo(final EntityPlayer player)
    {
        final Iterable<ChangeStorage> iterable = () -> changeQueue.iterator();
        final Stream<ChangeStorage> storageStream = StreamSupport.stream(iterable.spliterator(), false);
        final Optional<ChangeStorage> theStorage = storageStream.filter(storage -> storage.isOwner(player)).findFirst();
        if (theStorage.isPresent())
        {
            addToQueue(new ScanToolOperation(theStorage.get(), player));
            changeQueue.remove(theStorage.get());
        }
    }

    /**
     * Get the Universal Unique ID for the server.
     *
     * @return the server Universal Unique ID for ther
     */
    public static UUID getServerUUID()
    {
        if (serverUUID == null)
        {
            return generateOrRetrieveUUID();
        }
        return serverUUID;
    }

    /**
     * Generate or retrieve the UUID of the server.
     * @return the UUID.
     */
    private static UUID generateOrRetrieveUUID()
    {
        final MapStorage storage = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(0).getMapStorage();
        UUIDStorage instance = (UUIDStorage) storage.getOrLoadData(UUIDStorage.class, UUIDStorage.DATA_NAME);

        if (instance == null)
        {
            if (serverUUID == null)
            {
                Manager.setServerUUID(UUID.randomUUID());
                Log.getLogger().info(String.format("New Server UUID %s", serverUUID));
            }
            storage.setData(UUIDStorage.DATA_NAME, new UUIDStorage());
        }
        return serverUUID;
    }

    /**
     * Set the server UUID.
     *
     * @param uuid the universal unique id
     */
    public static void setServerUUID(final UUID uuid)
    {
        serverUUID = uuid;
    }

    /**
     * Whether or not a new schematic have been downloaded.
     *
     * @return True if a new schematic have been received.
     */
    public static boolean isSchematicDownloaded()
    {
        return schematicDownloaded;
    }

    /**
     * Set the schematic downloaded
     *
     * @param downloaded True if a new schematic have been received.
     */
    public static void setSchematicDownloaded(final boolean downloaded)
    {
        schematicDownloaded = downloaded;
    }
}
