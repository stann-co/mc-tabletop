package co.stann.tabletop;

import co.stann.tabletop.api.TabletopApi;
import co.stann.tabletop.client.Controls;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Tabletop's own chips. Chip behaviour comes from the {@link TabletopApi#CHIPS} tag and values from
 * {@link TabletopApi#CHIP_VALUE}, so chips from other mods or KubeJS work the same.
 */
public class ChipItem extends Item {
    /** Values of the chips Tabletop adds, cheapest first; each is item tabletop:chip_{value}. */
    public static final int[] VALUES = {1, 5, 25, 100, 500};

    public ChipItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int value = TabletopApi.chipValue(stack.getItem());
        if (value > 0) {
            tooltip.add(Component.translatable("tooltip.tabletop.chip.value", value).withStyle(ChatFormatting.GOLD));
        }
        CardItem.appendLabel(stack, tooltip);
        Controls.append(tooltip, "chip");
    }
}
