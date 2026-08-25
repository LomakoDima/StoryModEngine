package com.dimalab.storymodengine.common.logging;

import com.dimalab.storymodengine.api.logging.LogLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * How a channel's chat prefix — {@code [Name]: message} — is assembled. Every visual segment
 * (opening bracket, name, closing bracket, separator, message) is built as its own
 * {@link Component} carrying its own {@link Style} and joined via {@code MutableComponent.append}
 * — the color/bold/italic/underline of each piece lives in {@link Style}, structurally, never in
 * the text itself the way {@code "§6[§7Name§6]: "} would. That's what lets
 * {@link #withBracketStyle}/{@link #withNameStyle}/{@link #withSeparatorStyle}/
 * {@link #withMessageStyle} change one segment's appearance without touching the others, or the
 * text.
 *
 * <p>A {@link Style} here can carry a legacy {@link ChatFormatting} color
 * ({@code Style.EMPTY.withColor(ChatFormatting.GOLD)}) or a true 24-bit RGB color
 * ({@code Style.EMPTY.withColor(0xFF8800)}, equivalently
 * {@code Style.EMPTY.withColor(TextColor.fromRgb(0xFF8800))}) — nothing in this class or
 * {@link EngineLogger} limits a segment to the 16 legacy colors; that choice is entirely up to
 * whatever {@code Style} is passed in. Bold/italic/underlined compose the same way
 * ({@code Style.EMPTY.withColor(...).withBold(true).withItalic(true)}).
 *
 * <p>Immutable — every {@code with*} method returns a new instance, leaving {@link #DEFAULT} (or
 * whatever template you started from) untouched. Attach a customized one to a channel with
 * {@link EngineLogger#setChatTemplate}.
 */
public final class ChatTemplate {

    /** {@code [} and {@code ]} in gold, the name in gray, {@code ": "} in gold, message in the level's color unless overridden — the format asked for by default. */
    public static final ChatTemplate DEFAULT = new ChatTemplate(
            "[", "]", ": ",
            Style.EMPTY.withColor(ChatFormatting.GOLD),
            Style.EMPTY.withColor(ChatFormatting.GRAY),
            Style.EMPTY.withColor(ChatFormatting.GOLD),
            Style.EMPTY);

    private final String openBracket;
    private final String closeBracket;
    private final String separator;
    private final Style bracketStyle;
    private final Style nameStyle;
    private final Style separatorStyle;
    private final Style messageStyle;

    private ChatTemplate(String openBracket, String closeBracket, String separator,
                          Style bracketStyle, Style nameStyle, Style separatorStyle, Style messageStyle) {
        this.openBracket = openBracket;
        this.closeBracket = closeBracket;
        this.separator = separator;
        this.bracketStyle = bracketStyle;
        this.nameStyle = nameStyle;
        this.separatorStyle = separatorStyle;
        this.messageStyle = messageStyle;
    }

    public ChatTemplate withBrackets(String open, String close) {
        return new ChatTemplate(open, close, separator, bracketStyle, nameStyle, separatorStyle, messageStyle);
    }

    public ChatTemplate withSeparator(String separator) {
        return new ChatTemplate(openBracket, closeBracket, separator, bracketStyle, nameStyle, separatorStyle, messageStyle);
    }

    /** Style for both {@code [} and {@code ]}. */
    public ChatTemplate withBracketStyle(Style style) {
        return new ChatTemplate(openBracket, closeBracket, separator, style, nameStyle, separatorStyle, messageStyle);
    }

    /** Style for the channel name text between the brackets. */
    public ChatTemplate withNameStyle(Style style) {
        return new ChatTemplate(openBracket, closeBracket, separator, bracketStyle, style, separatorStyle, messageStyle);
    }

    /** Style for the {@code ": "} between {@code ]} and the message. */
    public ChatTemplate withSeparatorStyle(Style style) {
        return new ChatTemplate(openBracket, closeBracket, separator, bracketStyle, nameStyle, style, messageStyle);
    }

    /**
     * Base style for the message segment. Bold/italic/underlined here always apply; a color set
     * here always wins over {@link LogLevel#color()} — leave the color unset (the {@link #DEFAULT}
     * template does) to keep the per-level coloring (white INFO, yellow WARNING, red ERROR, ...).
     */
    public ChatTemplate withMessageStyle(Style style) {
        return new ChatTemplate(openBracket, closeBracket, separator, bracketStyle, nameStyle, separatorStyle, style);
    }

    /**
     * Assembles {@code [name]: message} as one {@link Component} tree, one {@link Style} per
     * segment. If {@code message} already carries its own style (or has styled children, e.g. from
     * {@code Component.literal(...).append(styledSpan)}), those explicit choices are preserved —
     * this template's message style only fills in what {@code message} left unset
     * ({@code Style#applyTo}), it never overwrites a caller's own styling.
     */
    MutableComponent render(String channelName, LogLevel level, Component message) {
        Style resolvedMessageStyle = messageStyle.getColor() != null
                ? messageStyle
                : messageStyle.withColor(level.color());

        return Component.empty()
                .append(Component.literal(openBracket).withStyle(bracketStyle))
                .append(Component.literal(channelName).withStyle(nameStyle))
                .append(Component.literal(closeBracket).withStyle(bracketStyle))
                .append(Component.literal(separator).withStyle(separatorStyle))
                .append(message.copy().withStyle(message.getStyle().applyTo(resolvedMessageStyle)));
    }
}
