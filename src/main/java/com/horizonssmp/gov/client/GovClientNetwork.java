package com.horizonssmp.gov.client;

import com.horizonssmp.gov.network.GovActionResultPayload;
import com.horizonssmp.gov.network.GovClientInbox;
import com.horizonssmp.gov.network.GovOpenScreenPayload;

import net.minecraft.client.Minecraft;

/**
 * The client end of the network layer: drains the inbox and opens the dashboard.
 *
 * <p>The common payload handlers cannot reference this class (a common class that names a client
 * class fails the project's dedicated-server bytecode gate), so they enqueue into
 * {@link GovClientInbox} and this class polls. See the inbox for the full reasoning.
 *
 * <p>Everything here runs on the client thread, from {@link GovClient}'s tick handler.
 */
public final class GovClientNetwork {

    private GovClientNetwork() {
    }

    /**
     * Drains what the server sent since the last tick.
     *
     * <p>An open-screen request is applied even when no Gov screen is open: that is the whole
     * point of {@code /gov dashboard}. An action result is only delivered to a screen that is
     * waiting for it; with no screen open there is nobody to match, and the reply is dropped.
     */
    public static void drainInbox(final Minecraft minecraft) {
        GovOpenScreenPayload open;
        while ((open = GovClientInbox.pollOpen()) != null) {
            openScreen(minecraft, GovDashboardScreen.Tab.fromId(open.tab()));
        }
        GovActionResultPayload result;
        while ((result = GovClientInbox.pollResult()) != null) {
            if (minecraft.screen instanceof GovDashboardScreen screen) {
                screen.onActionResult(result);
            }
        }
    }

    /**
     * Opens the dashboard on a tab, or switches the open dashboard to it.
     *
     * <p>A different screen being open is not interrupted: the player may be in a chest or a chat
     * box, and yanking that away because a command completed would be rude. The command can be
     * repeated once the screen is closed.
     */
    public static void openScreen(final Minecraft minecraft, final GovDashboardScreen.Tab tab) {
        if (minecraft.screen instanceof GovDashboardScreen dashboard) {
            dashboard.selectTab(tab);
            return;
        }
        if (minecraft.screen == null) {
            minecraft.setScreen(new GovDashboardScreen(tab));
        }
    }
}
