package co.stann.tabletop;

import co.stann.tabletop.client.Controls;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Tabletop's own table markers: the small blind, big blind and dealer button. Token behaviour comes from the
 * {@link co.stann.tabletop.api.TabletopApi#TOKENS} tag; this class only adds the controls tooltip.
 */
public class TokenItem extends Item {
    public TokenItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Controls.append(tooltip, "token");
    }
}
