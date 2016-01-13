package mcjty.rftoolsdim.items.parts;

import mcjty.rftoolsdim.RFToolsDim;
import mcjty.rftoolsdim.items.GenericRFToolsItem;
import mcjty.rftoolsdim.items.ModItems;
import mcjty.rftoolsdim.dimlets.types.DimletType;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.util.List;

public class DimletTypeControllerItem extends GenericRFToolsItem {

    public DimletTypeControllerItem() {
        super("dimlet_type_controller");
        setMaxStackSize(64);
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack itemStack, EntityPlayer player, List list, boolean whatIsThis) {
        super.addInformation(itemStack, player, list, whatIsThis);
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            list.add(EnumChatFormatting.WHITE + "Every dimlet needs a type specific controller. You can");
            list.add(EnumChatFormatting.WHITE + "get this by deconstructing other dimlets in the Dimlet");
            list.add(EnumChatFormatting.WHITE + "Workbench. In that same workbench you can also use");
            list.add(EnumChatFormatting.WHITE + "this item to make new dimlets.");
        } else {
            list.add(EnumChatFormatting.WHITE + RFToolsDim.SHIFT_MESSAGE);
        }
    }

    @Override
    public String getUnlocalizedName(ItemStack itemStack) {
        return super.getUnlocalizedName(itemStack) + DimletType.values()[itemStack.getItemDamage()].dimletType.getName();
    }

    @Override
    public void getSubItems(Item item, CreativeTabs creativeTabs, List list) {
        for (DimletType type : DimletType.values()) {
//            if (icons.containsKey(type)) {
                list.add(new ItemStack(ModItems.dimletTypeControllerItem, 1, type.ordinal()));
//            }
        }
    }
}
