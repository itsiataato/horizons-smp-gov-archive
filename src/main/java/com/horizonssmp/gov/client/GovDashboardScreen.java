package com.horizonssmp.gov.client;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.SelfCardKeys;
import com.horizonssmp.gov.network.GovActionResultPayload;
import com.horizonssmp.gov.network.GovNetwork;
import com.horizonssmp.gov.network.GovOpenScreenPayload;
import com.horizonssmp.gov.server.GovGlobalGuard;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

public final class GovDashboardScreen extends GovDashboardState {

    /** Opens on the Dashboard tab. */
    public GovDashboardScreen() {
        super();
    }

    /** Opens on a specific tab. */
    public GovDashboardScreen(final Tab tab) {
        super(tab);
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void init() {
        super.init();
        GovDashboardInit.initScreen((GovDashboardScreen) this);
    }

    @Override
    protected void setInitialFocus() {
        // A field is focused only when the player clicks it or opens Search.
    }


    /** Applies each section's stored sub-category to the panel that owns it. */

    // ------------------------------------------------------------------ geometry

    // -------------------------------------------------------------------- tabs

    /** Switches to a section. Called by the keybind, the rail and the server payload. */
    public void selectTab(final Tab tab) {
        if (tab == null) {
            return;
        }
        this.setFocused(null);
        this.setDragging(false);
        this.activeTab = tab;
        GovDashboardTabs.applyActivePanels((GovDashboardScreen) this);
        GovDashboardTabs.applyPanelSubtabs((GovDashboardScreen) this);
        GovDashboardGeometry.rebuildSubtabRects((GovDashboardScreen) this);
        if (tab == Tab.DASHBOARD) {
            GovDashboardCard.requestCardIfNeeded((GovDashboardScreen) this);
        }
    }

    /**
     * Called by the Law panel when it changes sub-category itself, for example when a search
     * result returns the reader to the article's page in Chapters.
     */
    void onLawSubtabChanged(final int index) {
        final int next = index == 1 ? 1 : 0;
        if (this.subtabs[Tab.LAW.ordinal()] == next) {
            return;
        }
        this.subtabs[Tab.LAW.ordinal()] = next;
        if (this.activeTab == Tab.LAW) {
            GovDashboardGeometry.rebuildSubtabRects((GovDashboardScreen) this);
        }
    }

    /**
     * Called by the People panel when it changes sub-category itself: selecting a search row,
     * moving the selection with the arrow keys, or returning to Search. Without this the strip
     * keeps highlighting the old sub-tab and the stored index disagrees with the visible panel.
     */
    void onPeopleSubtabChanged(final int index) {
        final int next = GovMetrics.clamp(index, 0,
                Math.max(0, SUBTAB_COUNTS[Tab.PEOPLE.ordinal()] - 1));
        if (this.subtabs[Tab.PEOPLE.ordinal()] == next) {
            return;
        }
        this.subtabs[Tab.PEOPLE.ordinal()] = next;
        if (this.activeTab == Tab.PEOPLE) {
            GovDashboardGeometry.rebuildSubtabRects((GovDashboardScreen) this);
        }
    }

    /**
     * Opens a person's card in the People section. Called by Notes when a note row is clicked.
     */
    public void openPerson(final String uuid, final String name) {
        this.selectTab(Tab.PEOPLE);
        this.peoplePanel.openPerson(uuid, name);
    }

    // --------------------------------------------------------------- self card

    /**
     * Consumes one action result. Only this screen's request id is applied to the card; every
     * panel matches its own request ids and receives the rest.
     */
    public void onActionResult(final GovActionResultPayload payload) {
        if (GovGlobalGuard.safeClientResult("card",
                () -> GovDashboardCard.handleCardResult((GovDashboardScreen) this, payload))) {
            return;
        }
        GovGlobalGuard.safeClient("law", () -> this.lawPanel.onActionResult(payload));
        GovGlobalGuard.safeClient("jobs", () -> this.jobsPanel.onActionResult(payload));
        GovGlobalGuard.safeClient("people", () -> this.peoplePanel.onActionResult(payload));
        GovGlobalGuard.safeClient("notes", () -> this.notesPanel.onActionResult(payload));
        GovGlobalGuard.safeClient("fines", () -> this.finesPanel.onActionResult(payload));
        GovGlobalGuard.safeClient("vehicles", () -> this.vehiclesPanel.onActionResult(payload));
        GovGlobalGuard.safeClient("weapons", () -> this.weaponsPanel.onActionResult(payload));
        GovGlobalGuard.safeClient(
                "businesses", () -> this.businessesPanel.onActionResult(payload));
        GovGlobalGuard.safeClient("jail", () -> this.jailPanel.onActionResult(payload));
        GovGlobalGuard.safeClient("audit", () -> this.auditPanel.onActionResult(payload));
        GovGlobalGuard.safeClient("treasury", () -> this.treasuryPanel.onActionResult(payload));
    }

    @Override
    public void removed() {
        this.pendingReqId = "";
        this.jobsPanel.reset();
        this.peoplePanel.reset();
        this.notesPanel.reset();
        this.finesPanel.reset();
        this.vehiclesPanel.reset();
        this.weaponsPanel.reset();
        this.businessesPanel.reset();
        this.jailPanel.reset();
        this.auditPanel.reset();
        this.treasuryPanel.reset();
        super.removed();
    }

    // ------------------------------------------------------------- card model

    // ------------------------------------------------------------------ render

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY,
            final float partialTick) {
        this.hoveredRail = -1;
        if (this.layout == null) {
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        GovDashboardShell.paint((GovDashboardScreen) this, graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        GovDashboardShell.tooltip((GovDashboardScreen) this, graphics, mouseX, mouseY);
    }

    @Override
    public void renderBackground(final GuiGraphics graphics, final int mouseX, final int mouseY,
            final float partialTick) {
        // The shell paints its own flat backdrop; no panorama, blur or menu texture.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }











    // ------------------------------------------------------------- dashboard card















    // ------------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        return GovDashboardInput.mouseClicked((GovDashboardScreen) this, mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX,
            final double scrollY) {
        return GovDashboardInput.mouseScrolled((GovDashboardScreen) this, mouseX, mouseY, scrollX,
                scrollY);
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button,
            final double dragX, final double dragY) {
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        return GovDashboardInput.keyPressed((GovDashboardScreen) this, keyCode, scanCode, modifiers);
    }


    /**
     * True when a real, visible field owns the keyboard. Panels hide widgets by mode, and a hidden
     * widget can still be the screen's focused child; treating it as focused would swallow K and
     * Escape while nothing is actually editable.
     */
     boolean visibleFieldFocused() {
        return this.getFocused() instanceof EditBox box && box.isVisible();
    }

     boolean superMouseClicked(final double mouseX, final double mouseY, final int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

     boolean superKeyPressed(final int keyCode, final int scanCode, final int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------------ helpers

    public void setSubtabVisibility(final Tab tab, final boolean[] allowed) {
        GovDashboardTabs.setSubtabVisibility((GovDashboardScreen) this, tab, allowed);
    }


    /** Package-private bridge for widget creation in helper classes. */
    void addGovWidget(final EditBox box) {
        this.addRenderableWidget(box);
    }

}
