package com.horizonssmp.gov.client;

import net.minecraft.client.gui.GuiGraphics;
import java.util.List;
import org.lwjgl.glfw.GLFW;

final class GovDashboardInput {
    private GovDashboardInput() {}

    static boolean mouseClicked(final GovDashboardScreen s, final double mouseX, final double mouseY, final int button) {
        if (button != 0) {
            return s.superMouseClicked(mouseX, mouseY, button);
        }
        if (s.closeRect.contains(mouseX, mouseY)) {
            closeDashboard(s);
            return true;
        }
        for (int index = 0; index < s.visibleTabs.size(); index++) {
            final GovRect rect = s.railRects[index];
            if (rect != null && rect.contains(mouseX, mouseY)) {
                s.selectTab(s.visibleTabs.get(index));
                return true;
            }
        }
        if (s.subtabOverflow) {
            if (s.subtabPrevEnabled && s.subtabPrevRect.contains(mouseX, mouseY)) {
                s.subtabScroll = Math.max(0, s.subtabScroll - 1);
                GovDashboardGeometry.rebuildSubtabRects(s);
                return true;
            }
            if (s.subtabNextEnabled && s.subtabNextRect.contains(mouseX, mouseY)) {
                final int next = Math.min(s.subtabScroll + 1,
                        s.visibleSubtabs[s.activeTab.ordinal()].size() - 1);
                if (next > s.subtabs[s.activeTab.ordinal()]) {
                    // The selected tab would leave the window; let the selection follow it.
                    GovDashboardTabs.onSubtab(s, s.activeTab, next);
                } else {
                    s.subtabScroll = next;
                    GovDashboardGeometry.rebuildSubtabRects(s);
                }
                return true;
            }
        }
        final List<Integer> visibleClick = s.visibleSubtabs[s.activeTab.ordinal()];
        for (int slot = 0; slot < s.subtabRects.length; slot++) {
            if (s.subtabRects[slot].contains(mouseX, mouseY)) {
                GovDashboardTabs.onSubtab(s, s.activeTab, visibleClick.get(slot));
                return true;
            }
        }
        if (s.superMouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        switch (s.activeTab) {
            case LAW -> {
                return s.lawPanel.mouseClicked(mouseX, mouseY, button);
            }
            case JOBS -> {
                return s.jobsPanel.mouseClicked(mouseX, mouseY, button);
            }
            case PEOPLE -> {
                return s.peoplePanel.mouseClicked(mouseX, mouseY, button);
            }
            case NOTES -> {
                return s.notesPanel.mouseClicked(mouseX, mouseY, button);
            }
            case FINES -> {
                return s.finesPanel.mouseClicked(mouseX, mouseY, button);
            }
            case VEHICLES -> {
                return s.vehiclesPanel.mouseClicked(mouseX, mouseY, button);
            }
            case WEAPONS -> {
                return s.weaponsPanel.mouseClicked(mouseX, mouseY, button);
            }
            case BUSINESSES -> {
                return s.businessesPanel.mouseClicked(mouseX, mouseY, button);
            }
            case JAIL -> {
                return s.jailPanel.mouseClicked(mouseX, mouseY, button);
            }
            case AUDIT -> {
                return s.auditPanel.mouseClicked(mouseX, mouseY, button);
            }
            case TREASURY -> {
                return s.treasuryPanel.mouseClicked(mouseX, mouseY, button);
            }
            case STORE -> {
                return s.treasuryPanel.mouseClicked(mouseX, mouseY, button);
            }
            default -> {
                if (s.cardState != GovDashboardScreen.CardState.LOADING
                        && s.refreshRect.contains(mouseX, mouseY)) {
                    GovDashboardCard.requestCard(s);
                    return true;
                }
                if (s.cardState == GovDashboardScreen.CardState.ERROR
                        && s.retryRect.contains(mouseX, mouseY)) {
                    GovDashboardCard.requestCard(s);
                    return true;
                }
                return false;
            }
        }
    }
    static boolean mouseScrolled(final GovDashboardScreen s, final double mouseX, final double mouseY, final double scrollX,
            final double scrollY) {
        // Law is page-based: it never consumes a wheel event and never scrolls.
        if (s.activeTab == GovDashboardScreen.Tab.LAW) {
            return false;
        }
        return switch (s.activeTab) {
            case JOBS -> s.jobsPanel.mouseScrolled(mouseX, mouseY, scrollY);
            case PEOPLE -> s.peoplePanel.mouseScrolled(mouseX, mouseY, scrollY);
            case NOTES -> s.notesPanel.mouseScrolled(mouseX, mouseY, scrollY);
            case FINES -> s.finesPanel.mouseScrolled(mouseX, mouseY, scrollY);
            case VEHICLES -> s.vehiclesPanel.mouseScrolled(mouseX, mouseY, scrollY);
            case WEAPONS -> s.weaponsPanel.mouseScrolled(mouseX, mouseY, scrollY);
            case BUSINESSES -> s.businessesPanel.mouseScrolled(mouseX, mouseY, scrollY);
            case JAIL -> s.jailPanel.mouseScrolled(mouseX, mouseY, scrollY);
            case AUDIT -> s.auditPanel.mouseScrolled(mouseX, mouseY, scrollY);
            case TREASURY -> s.treasuryPanel.mouseScrolled(mouseX, mouseY, scrollY);
            case STORE -> s.treasuryPanel.mouseScrolled(mouseX, mouseY, scrollY);
            default -> false;
        };
    }
    static boolean keyPressed(final GovDashboardScreen s, final int keyCode, final int scanCode, final int modifiers) {
        // Panels get the first chance at Escape so they can close one of their own layers.
        if (s.peoplePanel.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (s.notesPanel.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (s.jobsPanel.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (s.finesPanel.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (s.vehiclesPanel.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (s.weaponsPanel.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (s.businessesPanel.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (s.jailPanel.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (s.auditPanel.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (s.treasuryPanel.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // A visible field owns the first Escape: leave the field instead of losing the whole
            // dashboard in one keystroke. Hidden widgets can still hold focus, so check visibility.
            if (s.visibleFieldFocused()) {
                s.setFocused(null);
                return true;
            }
            s.onClose();
            return true;
        }
        if (s.lawPanel.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        // A focused field owns every remaining key, including K, '[' and ']'.
        if (s.visibleFieldFocused()) {
            return s.superKeyPressed(keyCode, scanCode, modifiers);
        }
        if (GovClient.isOpenKey(keyCode, scanCode)) {
            closeDashboard(s);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT_BRACKET) {
            cycleSection(s, -1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT_BRACKET) {
            cycleSection(s, 1);
            return true;
        }
        return s.superKeyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Moves to the next/previous visible section. Formerly housed in the deleted
     * TASK-019 scale settings; kept here because the bracket keys that drive it live here.
     */
    static void cycleSection(final GovDashboardScreen screen, final int delta) {
        int index = 0;
        boolean found = false;
        for (int slot = 0; slot < screen.visibleTabs.size(); slot++) {
            if (screen.visibleTabs.get(slot) == screen.activeTab
                    || (screen.activeTab == GovDashboardState.Tab.STORE
                            && screen.visibleTabs.get(slot)
                                    == GovDashboardState.Tab.TREASURY)) {
                index = slot;
                found = true;
                break;
            }
        }
        if (!found) {
            index = 0;
        }
        if (screen.visibleTabs.isEmpty()) {
            screen.selectTab(GovDashboardState.Tab.DASHBOARD);
            return;
        }
        final int next = Math.floorMod(index + delta, screen.visibleTabs.size());
        screen.selectTab(screen.visibleTabs.get(next));
    }

    /**
     * The shared close path for X and K. Suppression stops the same K press
     * reopening the dashboard.
     */
    static void closeDashboard(final GovDashboardScreen screen) {
        GovClient.suppressOpen(250);
        screen.onClose();
    }
}
