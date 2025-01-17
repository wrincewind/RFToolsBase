package mcjty.rftoolsbase.modules.tablet.items;

import mcjty.lib.builder.TooltipBuilder;
import mcjty.lib.gui.ManualEntry;
import mcjty.lib.tooltips.ITooltipSettings;
import mcjty.lib.varia.NBTTools;
import mcjty.rftoolsbase.RFToolsBase;
import mcjty.rftoolsbase.api.various.IItemCycler;
import mcjty.rftoolsbase.api.various.ITabletSupport;
import mcjty.rftoolsbase.modules.tablet.TabletModule;
import mcjty.rftoolsbase.tools.ManualHelper;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Lazy;
import net.minecraftforge.fml.network.NetworkHooks;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static mcjty.lib.builder.TooltipBuilder.*;
import static mcjty.rftoolsbase.modules.tablet.items.TabletContainer.NUM_SLOTS;

import net.minecraft.item.Item.Properties;

public class TabletItem extends Item implements IItemCycler, ITooltipSettings {

    public static final ManualEntry MANUAL = ManualHelper.create("rftoolsbase:tools/tablet");

    private final Lazy<TooltipBuilder> tooltipBuilder = () -> new TooltipBuilder()
            .info(key("message.rftoolsbase.shiftmessage"))
            .infoShift(header(), gold());

    @Override
    public ManualEntry getManualEntry() {
        return MANUAL;
    }

    public TabletItem() {
        super(new Properties()
                .stacksTo(1)
                .tab(RFToolsBase.setup.getTab()));
    }

    public static int getCurrentSlot(ItemStack stack) {
        return NBTTools.getTag(stack).map(tag -> tag.getInt("Current")).orElse(0);
    }

    public static void setCurrentSlot(PlayerEntity player, ItemStack stack, int current) {
        stack.getOrCreateTag().putInt("Current", current);
        ItemStack containingItem = getContainingItem(stack, current);
        ItemStack newTablet = deriveNewItemstack(current, containingItem, stack, current);
        player.inventory.items.set(player.inventory.selected, newTablet);
//        player.setHeldItem(getHand(player), newTablet);
    }

    public static Hand getHand(PlayerEntity player) {
        return player.getUsedItemHand() == null ? Hand.MAIN_HAND : player.getUsedItemHand();
    }

    @Override
    public Collection<ItemGroup> getCreativeTabs() {
        if (this == TabletModule.TABLET.get()) {
            return super.getCreativeTabs();
        }
        return Collections.emptyList();
    }

    @Override
    public void cycle(PlayerEntity player, ItemStack stack, boolean next) {
        int currentItem = getCurrentSlot(stack);
        int tries = NUM_SLOTS+1;
        while (tries > 0) {
            if (next) {
                currentItem = (currentItem + 1) % NUM_SLOTS;
            } else {
                currentItem = (currentItem + NUM_SLOTS - 1) % NUM_SLOTS;
            }
            ItemStack containingItem = getContainingItem(stack, currentItem);
            if (!containingItem.isEmpty()) {
                setCurrentSlot(player, stack, currentItem);
                player.displayClientMessage(new StringTextComponent("Switched item"), false);
                return;
            }
            tries--;
        }
    }

    public static ItemStack getContainingItem(ItemStack stack, int slot) {
        return NBTTools.getTag(stack).map(tag -> ItemStack.of(tag.getCompound("Item" + slot))).orElse(ItemStack.EMPTY);
    }

    public static void setContainingItem(PlayerEntity player, Hand hand, int slot, ItemStack containingItem) {
        ItemStack stack = player.getItemInHand(hand);
        CompoundNBT tag = stack.getOrCreateTag();
        if (containingItem.isEmpty()) {
            tag.remove("Item" + slot);
        } else {
            CompoundNBT compound = new CompoundNBT();
            containingItem.save(compound);
            tag.put("Item" + slot, compound);
        }

        int current = getCurrentSlot(stack);
        ItemStack newTablet = deriveNewItemstack(slot, containingItem, stack, current);
        player.inventory.items.set(player.inventory.selected, newTablet);
//        player.setHeldItem(hand, newTablet);
    }

    private static ItemStack deriveNewItemstack(int slot, ItemStack containingItem, ItemStack stack, int current) {
        ItemStack newTablet;
        if (slot == current) {
            if (containingItem.isEmpty()) {
                newTablet = new ItemStack(TabletModule.TABLET.get());
            } else {
                newTablet = new ItemStack(((ITabletSupport) containingItem.getItem()).getInstalledTablet());
            }
            newTablet.setTag(stack.getTag());
        } else {
            newTablet = stack;
        }
        return newTablet;
    }

    @Override
    public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!world.isClientSide) {
            if (player.isShiftKeyDown()) {
                openTabletGui(player);
            } else {
                ItemStack containingItem = getContainingItem(stack, getCurrentSlot(stack));
                if (containingItem.isEmpty()) {
                    openTabletGui(player);
                } else {
                    if (containingItem.getItem() instanceof ITabletSupport) {
                        ((ITabletSupport) containingItem.getItem()).openGui(player, stack, containingItem);
                    }
                }
            }

            return new ActionResult<>(ActionResultType.SUCCESS, stack);
        }
        return new ActionResult<>(ActionResultType.SUCCESS, stack);
    }

    private void openTabletGui(PlayerEntity player) {
        NetworkHooks.openGui((ServerPlayerEntity)player, new INamedContainerProvider() {
            @Override
            public ITextComponent getDisplayName() {
                return new StringTextComponent("Tablet");
            }

            @Override
            public Container createMenu(int id, PlayerInventory playerInventory, PlayerEntity player) {
                TabletContainer container = new TabletContainer(id, player.blockPosition(), player);
                container.setupInventories(new TabletItemHandler(player), playerInventory);
                return container;
            }
        });
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack itemStack, World world, List<ITextComponent> list, ITooltipFlag flags) {
        super.appendHoverText(itemStack, world, list, flags);
        tooltipBuilder.get().makeTooltip(getRegistryName(), itemStack, list, flags);
    }
}