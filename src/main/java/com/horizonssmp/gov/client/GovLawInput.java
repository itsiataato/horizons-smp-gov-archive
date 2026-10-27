package com.horizonssmp.gov.client;

import org.lwjgl.glfw.GLFW;

final class GovLawInput {

    private GovLawInput() {
    }

    static boolean mouseClicked(final GovLawPanel p, final double mouseX, final double mouseY,
            final int button) {
        if (!p.active || button != 0) {
            return false;
        }
        if (p.subtab == 1) {
            if (p.searchFrameRect.contains(mouseX, mouseY)) {
                GovLawActions.focusSearch(p);
                return true;
            }
            if (p.resultPage > 0 && p.searchPrevRect.contains(mouseX, mouseY)) {
                GovLawActions.goResultPage(p, -1);
                return true;
            }
            if (p.searchCache != null
                    && p.resultPage < p.searchCache.pageCount() - 1
                    && p.searchNextRect.contains(mouseX, mouseY)) {
                GovLawActions.goResultPage(p, 1);
                return true;
            }
            for (int index = 0; index < p.resultHits.length; index++) {
                if (!p.resultHits[index].contains(mouseX, mouseY)) {
                    continue;
                }
                if (p.searchCache != null
                        && p.resultHitRows[index] < p.searchCache.rows().size()) {
                    GovLawActions.selectSearchResult(p,
                            p.searchCache.rows().get(p.resultHitRows[index]));
                }
                return true;
            }
            return false;
        }

        for (int index = 0; index < p.tocHits.length; index++) {
            if (p.tocHits[index] != null && p.tocHits[index].contains(mouseX, mouseY)) {
                GovLawActions.selectChapter(p, index);
                return true;
            }
        }
        if (p.pageIndex > 0 && p.chapterPrevRect.contains(mouseX, mouseY)) {
            GovLawActions.goToPage(p, -1);
            return true;
        }
        if (p.chapterCache != null
                && p.pageIndex < p.chapterCache.pageCount() - 1
                && p.chapterNextRect.contains(mouseX, mouseY)) {
            GovLawActions.goToPage(p, 1);
            return true;
        }
        return false;
    }

    static boolean keyPressed(final GovLawPanel p, final int keyCode, final int scanCode,
            final int modifiers) {
        if (!p.active) {
            return false;
        }
        if (p.searchBox != null && p.searchBox.isFocused()) {
            return false;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> {
                if (p.subtab == 1) {
                    p.setSubtab(0);
                }
                GovLawActions.switchChapter(p, -1);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                if (p.subtab == 1) {
                    p.setSubtab(0);
                }
                GovLawActions.switchChapter(p, 1);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (p.subtab == 1) {
                    GovLawActions.goResultPage(p, -1);
                } else {
                    GovLawActions.goToPage(p, -1);
                }
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (p.subtab == 1) {
                    GovLawActions.goResultPage(p, 1);
                } else {
                    GovLawActions.goToPage(p, 1);
                }
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                if (p.subtab == 1) {
                    GovLawActions.goToResultPage(p, 0);
                } else {
                    GovLawActions.goToPageIndex(p, 0);
                }
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                if (p.subtab == 1) {
                    GovLawActions.goToResultPage(p, p.searchCache == null ? 0
                            : p.searchCache.pageCount() - 1);
                } else {
                    GovLawActions.goToPageIndex(p, p.chapterCache == null ? 0
                            : p.chapterCache.pageCount() - 1);
                }
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                if (p.subtab == 1) {
                    p.setSubtab(0);
                }
                GovLawActions.switchChapter(p, -1);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                if (p.subtab == 1) {
                    p.setSubtab(0);
                }
                GovLawActions.switchChapter(p, 1);
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
