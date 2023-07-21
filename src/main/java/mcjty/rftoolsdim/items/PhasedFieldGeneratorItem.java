package mcjty.rftoolsdim.items;

import mcjty.lib.varia.IEnergyItem;
import mcjty.lib.varia.ItemCapabilityProvider;
import mcjty.rftoolsdim.config.PowerConfiguration;
import mcjty.rftoolsdim.dimensions.DimensionTickEvent;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

public class PhasedFieldGeneratorItem extends GenericRFToolsItem implements IEnergyItem {

    private long capacity;
    private long maxReceive;
    private long maxExtract;

    public PhasedFieldGeneratorItem() {
        super("phased_field_generator");
        setMaxStackSize(1);

        capacity = PowerConfiguration.PHASEDFIELD_MAXENERGY;
        maxReceive = PowerConfiguration.PHASEDFIELD_RECEIVEPERTICK;
        maxExtract = PowerConfiguration.PHASEDFIELD_CONSUMEPERTICK * DimensionTickEvent.MAXTICKS;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void initModel() {
        for (int i = 0 ; i <= 8 ; i++) {
            ResourceLocation registryName = getRegistryName();
            registryName = new ResourceLocation(registryName.getNamespace(), registryName.getPath() + i);
            ModelBakery.registerItemVariants(this, new ModelResourceLocation(registryName, "inventory"));
//            ModelBakery.addVariantName(this, getRegistryName() + i);
        }

        ModelLoader.setCustomMeshDefinition(this, stack -> {
            NBTTagCompound tagCompound = stack.getTagCompound();
            long energy = 0;
            if (tagCompound != null) {
                energy = tagCompound.getLong("Energy");
            }
            long level = (9*energy) / PowerConfiguration.PHASEDFIELD_MAXENERGY;
            if (level < 0) {
                level = 0;
            } else if (level > 8) {
                level = 8;
            }
            ResourceLocation registryName = getRegistryName();
            registryName = new ResourceLocation(registryName.getNamespace(), registryName.getPath() + (8 - level));
            return new ModelResourceLocation(registryName, "inventory");
        });
    }


    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (oldStack.isEmpty() != newStack.isEmpty()) {
            return true;
        }
        return oldStack.getItem() != newStack.getItem();
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, NBTTagCompound nbt) {
        return new ItemCapabilityProvider(stack, this);
    }



    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return 1;
    }

    @Override
    public void addInformation(ItemStack itemStack, World player, List<String> list, ITooltipFlag whatIsThis) {
        super.addInformation(itemStack, player, list, whatIsThis);
        NBTTagCompound tagCompound = itemStack.getTagCompound();
        if (tagCompound != null) {
            list.add(TextFormatting.BLUE + "Energy: " + tagCompound.getLong("Energy") + " RF");
        }
        list.add("This RF/charged module gives a temporary");
        list.add("protection while visiting an unpowered dimension.");
        list.add("Use at your own risk and don't let power run out!");
    }

    @Override
    public long receiveEnergyL(ItemStack container, long maxReceive, boolean simulate) {
        if (container.getTagCompound() == null) {
            container.setTagCompound(new NBTTagCompound());
        }
        long energy = container.getTagCompound().getLong("Energy");
        long energyReceived = Math.min(capacity - energy, Math.min(this.maxReceive, maxReceive));

        if (!simulate) {
            energy += energyReceived;
            container.getTagCompound().setLong("Energy", energy);
        }
        return energyReceived;
    }

    @Override
    public long extractEnergyL(ItemStack container, long maxExtract, boolean simulate) {
        if (container.getTagCompound() == null || !container.getTagCompound().hasKey("Energy")) {
            return 0;
        }
        long energy = container.getTagCompound().getLong("Energy");
        long energyExtracted = Math.min(energy, Math.min(this.maxExtract, maxExtract));

        if (!simulate) {
            energy -= energyExtracted;
            container.getTagCompound().setLong("Energy", energy);
        }
        return energyExtracted;
    }

    @Override
    public long getEnergyStoredL(ItemStack container) {
        if (container.getTagCompound() == null || !container.getTagCompound().hasKey("Energy")) {
            return 0;
        }
        return container.getTagCompound().getLong("Energy");
    }

    @Override
    public long getMaxEnergyStoredL(ItemStack container) {
        return capacity;
    }
}
