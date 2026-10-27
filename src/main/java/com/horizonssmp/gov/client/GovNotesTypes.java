package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovDashboardScreen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.NoteKeys;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.network.GovActionResultPayload;
import com.horizonssmp.gov.network.GovNetwork;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class GovNotesTypes {
    static final GovRect EMPTY = new GovRect(0, 0, 0, 0);
    static final List<NoteRow> NO_NOTES = List.of();
    static final List<DisplayRow> NO_DISPLAY = List.of();
    static final List<NoteHit> NO_HITS = List.of();
    static final List<ScopeHit> NO_SCOPE_HITS = List.of();
    static final List<FormattedCharSequence> NO_LINES = List.of();
    static final List<ScopeRow> NO_SCOPES = List.of();

    static final int MAX_SCOPES = 6;
    static final int MAX_TEXT_LINES = 3;
    static final int ROW_BUTTON_H = 10;
    static final long ARM_FLOOR_MS = 600L;
    static final long ARM_EXPIRY_MS = 5000L;
    static final long MUTATION_TIMEOUT_MS = 30_000L;

    enum Load {
        NONE, LOADING, READY, ERROR
    }

    enum RequestKind {
        SCOPES, LIST, MUTATE
    }

    enum HitKind {
        ROW, EDIT, DELETE
    }

    record Pending(RequestKind kind, OpCode op, View view, String target, int page) {
    }

    record NoteRow(String id, String targetUuid, String targetName, String author,
            String text, long createdAt, long editedAt) {
    }

    record DisplayRow(NoteRow note, String meta, List<FormattedCharSequence> textLines,
            boolean canEdit, boolean canDelete) {
    }

    record NoteHit(GovRect rect, NoteRow note, HitKind kind) {
    }

    record ScopeRow(String type, String id, String label, String role,
            boolean canPost, boolean canModerate, Component boldLabel) {
    }

    record ScopeHit(GovRect rect, ScopeRow scope) {
    }

    static final class View {
        final int mode;
        final String scopeType;
        final String scopeId;
        final boolean personal;

        Load state = Load.NONE;
        String error = "";
        List<NoteRow> notes = NO_NOTES;
        List<DisplayRow> display = NO_DISPLAY;
        List<NoteHit> hits = NO_HITS;
        List<FormattedCharSequence> errorLines = NO_LINES;
        int total;
        int page;
        int pages = 1;
        boolean canPost;
        boolean canModerate;
        String target = "";
        String scopeLabel = "";
        String latestListReqId = "";
        boolean listPending;
        long listSentAt;
        boolean stale;
        int scroll;
        int scrollMax;
        int rowPitch;
        int textLines;
        int textX;
        int textW;
        int barW;
        int editW;
        int deleteW;
        String countText = "";
        String pageText = "";

        View(final int mode, final String scopeType, final String scopeId,
                final boolean personal) {
            this.mode = mode;
            this.scopeType = scopeType;
            this.scopeId = scopeId;
            this.personal = personal;
        }

        void clearData() {
            this.state = Load.NONE;
            this.error = "";
            this.notes = NO_NOTES;
            this.display = NO_DISPLAY;
            this.hits = NO_HITS;
            this.errorLines = NO_LINES;
            this.total = 0;
            this.page = 0;
            this.pages = 1;
            this.canPost = false;
            this.canModerate = false;
            this.target = "";
            this.scopeLabel = "";
            this.latestListReqId = "";
            this.listPending = false;
            this.listSentAt = 0L;
            this.stale = false;
            this.scroll = 0;
            this.scrollMax = 0;
            this.rowPitch = 0;
            this.textLines = 0;
            this.textX = 0;
            this.textW = 0;
            this.barW = 0;
            this.editW = 0;
            this.deleteW = 0;
            this.countText = "";
            this.pageText = "";
        }
    }

    static final class BusinessState {
        final String id;
        String label;
        final View board;
        final View personal;
        boolean personalMode;
        String target = "";

        BusinessState(final String id, final String label) {
            this.id = id;
            this.label = label;
            this.board = new View(2, "business", id, false);
            this.personal = new View(2, "business", id, true);
        }

        View current() {
            return this.personalMode ? this.personal : this.board;
        }
    }
}
