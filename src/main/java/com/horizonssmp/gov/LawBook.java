package com.horizonssmp.gov;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

/**
 * Builds the vanilla written book handed out on first join and by {@code /law}.
 *
 * <p>No custom item is registered. The stack is a normal {@link Items#WRITTEN_BOOK} with the
 * standard {@link DataComponents#WRITTEN_BOOK_CONTENT} component, so a completely vanilla client
 * can read it, copy it, and put it in a lectern.
 *
 * <p>Vanilla's 1.21.1 {@link WrittenBookContent#PAGE_LENGTH} is the protocol-level maximum
 * (32767 characters), not a readable page. A single page that large would spill far outside the
 * book GUI. Pages are therefore split against a smaller readable target, still capped by
 * {@code PAGE_LENGTH}. The split is greedy and happens at word boundaries.
 */
public final class LawBook {

    /** Title stored in the written-book component. Must fit {@code TITLE_MAX_LENGTH}. */
    public static final String TITLE = "Season 10 Law";

    /** Author stored in the written-book component. */
    public static final String AUTHOR = "Horizons SMP";

    /**
     * Characters per page chosen for the vanilla book GUI. The GUI fits roughly 14 lines of
     * about 19 characters; 240 leaves room for word wrapping and short chapter headings.
     */
    private static final int READABLE_PAGE_LENGTH = Math.min(WrittenBookContent.PAGE_LENGTH, 240);

    private LawBook() {
    }

    /**
     * Creates a fresh copy of the law book.
     *
     * @return a written book stack with the complete Season 10 Law
     */
    public static ItemStack create() {
        final LawData data = LawData.get();
        final List<String> pageTexts = paginate(buildBlocks(data));
        final List<Filterable<Component>> pages = new ArrayList<>(pageTexts.size());
        for (final String pageText : pageTexts) {
            pages.add(Filterable.passThrough(Component.literal(pageText)));
        }

        final WrittenBookContent content = new WrittenBookContent(
                Filterable.passThrough(TITLE),
                AUTHOR,
                0,
                pages,
                true);

        final ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
        return book;
    }

    private static List<String> buildBlocks(final LawData data) {
        final List<String> blocks = new ArrayList<>();
        blocks.add("SEASON 10 LAW\nHorizons SMP\n"
                + data.version() + " - updated " + data.lastUpdated());

        for (final LawData.Chapter chapter : data.chapters()) {
            blocks.add("CHAPTER " + chapter.numeral() + " - " + chapter.title()
                    + "\n" + chapter.summary());
            for (final LawData.Article article : chapter.articles()) {
                final StringBuilder block = new StringBuilder();
                block.append(article.id()).append("  ").append(article.title())
                        .append('\n').append(article.body());
                if (article.hasPenalty()) {
                    block.append("\nPenalty: ").append(article.penalty());
                }
                blocks.add(block.toString());
            }
        }
        return blocks;
    }

    private static List<String> paginate(final List<String> blocks) {
        final List<String> pages = new ArrayList<>();
        final StringBuilder page = new StringBuilder();

        for (final String block : blocks) {
            String remaining = block.strip();
            while (!remaining.isEmpty()) {
                final int separator = page.length() == 0 ? 0 : 2;
                final int room = READABLE_PAGE_LENGTH - page.length() - separator;
                if (room <= 0) {
                    flushPage(pages, page);
                    continue;
                }

                if (remaining.length() <= room) {
                    appendBlock(page, remaining);
                    remaining = "";
                    continue;
                }

                final int cut = findBreak(remaining, room);
                final String part = remaining.substring(0, cut).stripTrailing();
                appendBlock(page, part);
                flushPage(pages, page);
                remaining = remaining.substring(cut).stripLeading();
            }
        }

        flushPage(pages, page);
        if (pages.isEmpty()) {
            throw new IllegalStateException("The law book produced no pages");
        }
        return pages;
    }

    private static int findBreak(final String text, final int room) {
        final int cutoff = Math.min(room, text.length());
        final int newline = text.lastIndexOf('\n', cutoff - 1);
        if (newline > 0) {
            return newline;
        }
        final int space = text.lastIndexOf(' ', cutoff - 1);
        if (space > 0) {
            return space;
        }
        return cutoff;
    }

    private static void appendBlock(final StringBuilder page, final String block) {
        if (block.isEmpty()) {
            return;
        }
        if (page.length() > 0) {
            page.append("\n\n");
        }
        page.append(block);
    }

    private static void flushPage(final List<String> pages, final StringBuilder page) {
        final String text = page.toString().strip();
        if (!text.isEmpty()) {
            pages.add(text);
        }
        page.setLength(0);
    }
}
