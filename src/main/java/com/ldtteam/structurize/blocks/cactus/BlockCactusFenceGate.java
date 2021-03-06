package com.ldtteam.structurize.blocks.cactus;

import com.ldtteam.structurize.blocks.AbstractBlockStructurizeFenceGate;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

public class BlockCactusFenceGate extends AbstractBlockStructurizeFenceGate<BlockCactusFenceGate>
{

    private static final String BLOCK_NAME = "blockcactusfencegate";

    public BlockCactusFenceGate()
    {
        super(Block.Properties.from(Blocks.OAK_DOOR));
        setRegistryName(BLOCK_NAME);
    }
}
