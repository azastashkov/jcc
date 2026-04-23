package io.jcc.cli;

import io.jcc.cli.highlight.TokenType;
import org.jline.utils.AttributedCharSequence;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import java.util.function.Function;

public final class Style {

    public static final Style PLAIN = new Style(false, false, null);

    private final boolean color;
    private final boolean truecolor;
    private final Integer codeBackgroundRgb;

    private Style(boolean color, boolean truecolor, Integer codeBackgroundRgb) {
        this.color = color;
        this.truecolor = truecolor;
        this.codeBackgroundRgb = codeBackgroundRgb;
    }

    public static Style colored() {
        return new Style(true, false, null);
    }

    public static Style truecolor() {
        return new Style(true, true, null);
    }

    public Style withCodeBackground(int rgb) {
        if (!color) return this;
        return new Style(color, truecolor, rgb);
    }

    public static Style detect() {
        return detect(System::getenv);
    }

    static Style detect(Function<String, String> env) {
        if (env.apply("NO_COLOR") != null) return PLAIN;
        boolean colorOn;
        if (env.apply("FORCE_COLOR") != null) {
            colorOn = true;
        } else if ("dumb".equals(env.apply("TERM"))) {
            return PLAIN;
        } else if (System.console() == null) {
            return PLAIN;
        } else {
            colorOn = true;
        }
        if (!colorOn) return PLAIN;
        String ct = env.apply("COLORTERM");
        if ("truecolor".equalsIgnoreCase(ct) || "24bit".equalsIgnoreCase(ct)) {
            return truecolor();
        }
        return colored();
    }

    public String toolMarker(String s) { return wrap(s, AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN)); }
    public String toolName(String s)   { return wrap(s, AttributedStyle.BOLD); }
    public String toolArgs(String s)   { return wrap(s, AttributedStyle.DEFAULT.faint()); }
    public String toolResult(String s) { return wrap(s, AttributedStyle.DEFAULT.faint()); }
    public String toolError(String s)  { return wrap(s, AttributedStyle.BOLD.foreground(AttributedStyle.RED)); }
    public String progress(String s)   { return wrap(s, AttributedStyle.DEFAULT.faint()); }
    public String stopReason(String s) { return wrap(s, AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW)); }
    public String tokenFooter(String s){ return wrap(s, AttributedStyle.DEFAULT.faint()); }
    public String dim(String s)        { return wrap(s, AttributedStyle.DEFAULT.faint()); }
    public String bold(String s)       { return wrap(s, AttributedStyle.BOLD); }

    // JetBrains IDEA Darcula palette for code highlighting.
    public String keyword(String s)    { return wrap(s, withBg(AttributedStyle.BOLD.foregroundRgb(0xCC7832))); }
    public String stringLit(String s)  { return wrap(s, withBg(AttributedStyle.DEFAULT.foregroundRgb(0x6A8759))); }
    public String comment(String s)    { return wrap(s, withBg(AttributedStyle.DEFAULT.foregroundRgb(0x808080).italic())); }
    public String number(String s)     { return wrap(s, withBg(AttributedStyle.DEFAULT.foregroundRgb(0x6897BB))); }
    public String annotation(String s) { return wrap(s, withBg(AttributedStyle.DEFAULT.foregroundRgb(0xBBB529))); }
    public String operator(String s)   { return wrap(s, withBg(AttributedStyle.DEFAULT.foregroundRgb(0xA9B7C6))); }
    public String variable(String s)   { return wrap(s, withBg(AttributedStyle.DEFAULT.foregroundRgb(0x9876AA))); }

    public String forToken(TokenType type, String text) {
        return switch (type) {
            case KEYWORD -> keyword(text);
            case STRING -> stringLit(text);
            case COMMENT -> comment(text);
            case NUMBER -> number(text);
            case ANNOTATION -> annotation(text);
            case OPERATOR -> operator(text);
            case VARIABLE -> variable(text);
            case IDENTIFIER, OTHER ->
                codeBackgroundRgb != null ? wrap(text, withBg(AttributedStyle.DEFAULT)) : text;
        };
    }

    /** Returns an escape that sets the code background and erases to end of line, then resets. */
    public String codeBackgroundEraseEol() {
        if (!color || codeBackgroundRgb == null) return "";
        return setCodeBg() + "\033[K\033[0m";
    }

    private AttributedStyle withBg(AttributedStyle base) {
        return codeBackgroundRgb != null ? base.backgroundRgb(codeBackgroundRgb) : base;
    }

    private String setCodeBg() {
        int r = (codeBackgroundRgb >> 16) & 0xFF;
        int g = (codeBackgroundRgb >> 8) & 0xFF;
        int b = codeBackgroundRgb & 0xFF;
        if (truecolor) {
            return String.format("\033[48;2;%d;%d;%dm", r, g, b);
        }
        int gray = 232 + Math.min(23, Math.max(0, (r + g + b) / 30));
        return "\033[48;5;" + gray + "m";
    }

    boolean isColor() { return color; }

    private String wrap(String s, AttributedStyle style) {
        if (!color || s == null || s.isEmpty()) return s;
        AttributedString as = new AttributedString(s, style);
        return truecolor
            ? as.toAnsi(0x1000000, AttributedCharSequence.ForceMode.ForceTrueColors)
            : as.toAnsi();
    }
}
