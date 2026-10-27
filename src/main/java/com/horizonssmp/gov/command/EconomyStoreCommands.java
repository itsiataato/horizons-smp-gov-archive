package com.horizonssmp.gov.command;

import java.util.Map;

import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.network.GovNetwork;
import com.horizonssmp.gov.network.GovOpenScreenPayload;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

final class EconomyStoreCommands {

    private EconomyStoreCommands() {
    }

    static int store(final CommandContext<CommandSourceStack> context) {
        final ServerPlayer player = context.getSource().getPlayer();
        if (player != null) {
            GovNetwork.openScreen(player, GovOpenScreenPayload.store());
            return 1;
        }
        final Map<String, String> fields = EconomyCommandHelpers.read(context, "/gov store", OpCode.STORE_LIST,
                Map.of(EconomyKeys.KEY_PAGE, "0"));
        if (fields == null) {
            return 0;
        }
        EconomyCommandHelpers.send(context, "Store balance: " + EconomyCommandHelpers.value(fields, "store.balance"));
        EconomyCommandHelpers.send(context, "Page: " + EconomyCommandHelpers.pageText(fields, "store.page", "store.pages")
                + "  Items: " + EconomyCommandHelpers.value(fields, "store.total"));
        int index = 0;
        while (fields.containsKey(EconomyCommands.STORE_ROW + index + ".id")) {
            final String row = EconomyCommands.STORE_ROW + index + ".";
            EconomyCommandHelpers.send(context, (index + 1) + ". " + EconomyCommandHelpers.value(fields, row + "name")
                    + " [" + EconomyCommandHelpers.value(fields, row + "id") + "]"
                    + "  tier " + EconomyCommandHelpers.value(fields, row + "tier")
                    + "  price " + EconomyCommandHelpers.value(fields, row + "price")
                    + "  stack " + EconomyCommandHelpers.value(fields, row + "stack")
                    + "  can buy " + EconomyCommandHelpers.yesNo(fields.get(row + "canBuy")));
            index++;
        }
        if (index == 0) {
            EconomyCommandHelpers.send(context, "The store catalogue is empty.");
        }
        return 1;
    }
}
