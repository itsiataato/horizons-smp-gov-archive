package com.horizonssmp.gov.client;

import com.horizonssmp.gov.Gov;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Client-only keybind and screen opener.
 *
 * <p>The annotation carries {@code value = Dist.CLIENT} without a {@code bus} attribute. NeoForge
 * 21.1's {@code AutomaticEventSubscriber} routes each method to the mod bus or game bus from the
 * event type, so the key-registration event reaches the mod bus and the tick event reaches the
 * game bus without using the deprecated explicit bus selector.
 *
 * <p>No common class references this class. Dedicated servers never load it.
 *
 * <p>K opens the dashboard's Law tab, which is where the old standalone law reader moved to.
 * {@code /gov dashboard} opens the Dashboard tab instead, through the server's open-screen
 * payload; the tick handler also drains that payload out of the network inbox.
 */
@EventBusSubscriber(modid = Gov.MODID, value = Dist.CLIENT)
public final class GovClient {

    /** K opens the dashboard on the Law tab. */
    private static final KeyMapping OPEN_DASHBOARD = new KeyMapping(
            "key.gov.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.gov");

    /** Wall-clock deadline before which a queued open click is discarded. */
    private static volatile long openSuppressedUntil;

    private GovClient() {
    }

    /** True when the key event is the dashboard open key. */
    public static boolean isOpenKey(final int keyCode, final int scanCode) {
        return OPEN_DASHBOARD.matches(keyCode, scanCode);
    }

    /**
     * Discards open clicks for a short window.
     *
     * <p>The screen calls this when K closes it. The same key press can leave a click queued in
     * the key mapping, so without a deadline the dashboard would close and immediately reopen.
     * A deadline, rather than a boolean, means a missed reset cannot leave the key dead.
     */
    public static void suppressOpen(final long millis) {
        openSuppressedUntil = System.currentTimeMillis() + Math.max(0L, millis);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(final RegisterKeyMappingsEvent event) {
        event.register(OPEN_DASHBOARD);
    }

    @SubscribeEvent
    public static void onClientTick(final ClientTickEvent.Post event) {
        final Minecraft minecraft = Minecraft.getInstance();
        boolean clicked = false;
        // Drain every queued click even when the open is suppressed. Leaving one in the mapping
        // would make it open the dashboard a tick or two later, which looks like a stuck key.
        while (OPEN_DASHBOARD.consumeClick()) {
            clicked = true;
        }
        final boolean suppressed = System.currentTimeMillis() < openSuppressedUntil;
        if (clicked && !suppressed && minecraft.screen == null) {
            GovClientNetwork.openScreen(minecraft, GovDashboardScreen.Tab.LAW);
        }
        GovClientNetwork.drainInbox(minecraft);
    }
}
