package com.horizonssmp.gov.client;

import net.minecraft.network.chat.Component;

import com.horizonssmp.gov.common.OpCode;

/**
 * TASK-037 button matrix for the law tab: the only network shot on this tab is
 * {@code requestArticle} (LAW_ARTICLE); chapter/page/search navigation is local.
 * Runs via {@code main()}.
 */
final class ButtonMatrixLawTest {
    private ButtonMatrixLawTest() {
    }

    private static GovLawPanel fresh() {
        final GovLawPanel p = new GovLawPanel(null);
        p.active = true;
        return p;
    }

    public static void main(final String[] args) {
        ButtonMatrix.install();
        try {
            final GovLawPanel pArticle = fresh();
            ButtonMatrix.fire("LAW", "ARTICLE", () -> {
                pArticle.requestArticle("art-1");
            }, OpCode.LAW_ARTICLE, "article");

            final GovLawPanel pChapter = fresh();
            ButtonMatrix.check(!pChapter.law().chapters().isEmpty(), "law has no chapters");
            final int last = pChapter.law().chapters().size() - 1;
            ButtonMatrix.fireNone("LAW", "SELECT_CHAPTER",
                    () -> GovLawActions.selectChapter(pChapter, last),
                    () -> ButtonMatrix.check(pChapter.selectedChapter == last,
                            "chapter not selected, got " + pChapter.selectedChapter));

            final GovLawPanel pSwitch = fresh();
            GovLawActions.selectChapter(pSwitch, 0);
            final int expect = Math.min(1, pSwitch.law().chapters().size() - 1);
            ButtonMatrix.fireNone("LAW", "SWITCH_CHAPTER",
                    () -> GovLawActions.switchChapter(pSwitch, 1),
                    () -> ButtonMatrix.check(pSwitch.selectedChapter == expect,
                            "switch landed on " + pSwitch.selectedChapter));

            final GovLawPanel pPage = fresh();
            ButtonMatrix.fireNone("LAW", "PAGE_NO_CACHE",
                    () -> GovLawActions.goToPage(pPage, 1),
                    () -> ButtonMatrix.check(pPage.pageIndex == 0, "page moved without layout"));
            ButtonMatrix.fireNone("LAW", "PAGE_INDEX_NO_CACHE",
                    () -> GovLawActions.goToPageIndex(pPage, 3),
                    () -> ButtonMatrix.check(pPage.pageIndex == 0, "page index moved without layout"));

            final GovLawPanel pResult = fresh();
            pResult.subtab = 1;
            ButtonMatrix.fireNone("LAW", "SELECT_SEARCH_RESULT", () -> GovLawActions
                    .selectSearchResult(pResult, new SearchRow(0, "a", Component.empty(), "m")),
                    () -> ButtonMatrix.check(pResult.subtab == 0, "result did not return to reader"));

            final GovLawPanel pResPage = fresh();
            ButtonMatrix.fireNone("LAW", "RESULT_PAGE_NO_CACHE",
                    () -> GovLawActions.goResultPage(pResPage, 1),
                    () -> ButtonMatrix.check(pResPage.resultPage == 0,
                            "result page moved without layout"));
            ButtonMatrix.fireNone("LAW", "GOTO_RESULT_PAGE_NO_CACHE",
                    () -> GovLawActions.goToResultPage(pResPage, 2),
                    () -> ButtonMatrix.check(pResPage.resultPage == 0,
                            "goto result page moved without layout"));

            // Focus/visibility helpers are null-box safe headless (no host touch).
            final GovLawPanel pFocus = fresh();
            pFocus.subtab = 1;
            ButtonMatrix.fireNone("LAW", "FOCUS_SEARCH_NO_BOX",
                    () -> GovLawActions.focusSearch(pFocus),
                    () -> ButtonMatrix.check(pFocus.subtab == 1, "subtab moved"));
            ButtonMatrix.fireNone("LAW", "CLEAR_SEARCH_FOCUS_NO_BOX",
                    () -> GovLawActions.clearSearchFocus(pFocus),
                    () -> ButtonMatrix.check(pFocus.subtab == 1, "subtab moved"));
            ButtonMatrix.fireNone("LAW", "SYNC_SEARCH_VIS_NO_BOX",
                    () -> GovLawActions.syncSearchVisibility(pFocus),
                    () -> ButtonMatrix.check(pFocus.subtab == 1, "subtab moved"));

            // Key navigation runs the same local actions (search box null: no consume).
            final GovLawPanel pKeys = fresh();
            GovLawActions.selectChapter(pKeys, 0);
            ButtonMatrix.fireNone("LAW", "KEY_DOWN",
                    () -> GovLawInput.keyPressed(pKeys, org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN, 0, 0),
                    () -> ButtonMatrix.check(pKeys.selectedChapter == expect,
                            "key down landed on " + pKeys.selectedChapter));
            ButtonMatrix.fireNone("LAW", "KEY_UP",
                    () -> GovLawInput.keyPressed(pKeys, org.lwjgl.glfw.GLFW.GLFW_KEY_UP, 0, 0),
                    () -> ButtonMatrix.check(pKeys.selectedChapter == 0, "key up did not return"));
            final boolean[] clicked = new boolean[1];
            final GovLawPanel pMouse = fresh();
            ButtonMatrix.fireNone("LAW", "MOUSE_EMPTY",
                    () -> clicked[0] = GovLawInput.mouseClicked(pMouse, 5, 5, 0),
                    () -> ButtonMatrix.check(!clicked[0], "empty click consumed"));
        } finally {
            ButtonMatrix.uninstall();
        }
        ButtonMatrix.summary("LAW");
    }
}
