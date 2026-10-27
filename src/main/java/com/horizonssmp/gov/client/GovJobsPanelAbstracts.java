package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovDashboardScreen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.JobKeys;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.network.GovActionResultPayload;
import com.horizonssmp.gov.network.GovNetwork;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import static com.horizonssmp.gov.client.GovJobsTypes.*;
import static com.horizonssmp.gov.client.GovJobsConstants.*;

abstract class GovJobsPanelAbstracts extends GovJobsPanelBase {

    GovJobsPanelAbstracts(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    abstract EditBox createNameBox(Font font);
    abstract List<EditBox> createWidgets(Font font);
    abstract void setActive(boolean active);
    abstract void reset();
    abstract void setSubtab(int index);
    abstract void layoutRoster();
    abstract void layoutMembers();
    abstract void layoutCapabilities();
    abstract void layoutBar();
    abstract void layoutModals();
    abstract void positionBox(EditBox box, GovRect rect);
    abstract void syncCreateBox();
    abstract void clearFocus();
    abstract boolean blurVisibleField();
    abstract void requestListIfNeeded();
    abstract void requestList();
    abstract void requestInfo(String jobId);
    abstract void mutate(OpCode op, Map<String, String> payload);
    abstract boolean mutationPending();
    abstract void onActionResult(GovActionResultPayload payload);
    abstract void applyList(String delta);
    abstract void applyInfo(String delta);
    abstract void selectJob(String jobId);
    abstract void cycleJob(int delta);
    abstract void changePage(int delta);
    abstract void rebuildCaches();
    abstract void rebuildModalChrome();
    abstract void rebuildRetryRects();
    abstract void rebuildRosterViews();
    abstract void rebuildDetail();
    abstract void addText(String text);
    abstract void rebuildMemberViews();
    abstract void rebuildCapViews();
    abstract void rebuildHeaderTexts();
    abstract String rosterSummary();
    abstract void rebuildStatusCache();
    abstract GovRect statusBar();
    abstract String defaultStatus();
    abstract void clampScrolls();
    abstract void rebuildMemberRemoveRects();
    abstract void rebuildCapButtonRects();
    abstract void rebuildPickerRects();
    abstract void rebuildHits();
    abstract void addRefreshHit();
    abstract void addRosterHits();
    abstract void addMemberHits();
    abstract void addCapHits();
    abstract void render(GuiGraphics graphics, int mouseX, int mouseY);
    abstract void drawRoster(GuiGraphics graphics, int mouseX, int mouseY);
    abstract void drawDetailLine(GuiGraphics graphics, DetailLine line, int y);
    abstract void drawMembers(GuiGraphics graphics, int mouseX, int mouseY);
    abstract void drawBottomBar(GuiGraphics graphics, int mouseX, int mouseY);
    abstract void drawModal(GuiGraphics graphics, int mouseX, int mouseY);
    abstract boolean mouseClicked(double mouseX, double mouseY, int button);
    abstract void handleHit(Hit hit);
    abstract boolean mouseScrolled(double mouseX, double mouseY, double scrollY);
    abstract boolean keyPressed(int keyCode, int scanCode, int modifiers);
    abstract boolean charTyped(char codePoint, int modifiers);
    abstract boolean submitEnter();
    abstract void openCreateModal();
    abstract void openPicker();
    abstract void closeModal();
    abstract void createJob();
    abstract void deleteJob();
    abstract void removeMember(String uuid);
    abstract void assignTo(String playerName);
    abstract void grantCapability(String capability);
    abstract void removeCapability(String capability);
    abstract boolean armDelete();
    abstract boolean armMember(String uuid);
    abstract boolean armCap(String capability);
    abstract boolean armAssign(String playerName);
    abstract boolean isDeleteArmed();
    abstract boolean isMemberArmed(String uuid);
    abstract boolean isCapArmed(String capability);
    abstract boolean isAssignArmed(String playerName);
    abstract boolean anyArmed();
    abstract void disarmAll();
    abstract void resetArmStatus();
    abstract boolean createEnabled();
    abstract boolean deleteEnabled();
    abstract boolean memberActionsEnabled();
    abstract void setStatus(String text, boolean error);
    abstract String typeLabel(String type);


    abstract void layout(final int left, final int top, final int right, final int bottom,
            final net.minecraft.client.gui.Font font);
    abstract void drawCapabilities(final net.minecraft.client.gui.GuiGraphics graphics, final int mouseX,
            final int mouseY);
    abstract void drawRosterRow(final net.minecraft.client.gui.GuiGraphics graphics, final RosterView view, final int y,
            final int mouseX, final int mouseY);
    abstract void drawRosterDetail(final net.minecraft.client.gui.GuiGraphics graphics, final int mouseX,
            final int mouseY);
    abstract void addDetail(final String label, final String value, final int valueX,
            final int valueWidth);
}
