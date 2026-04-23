package io.jcc.cli;

import io.jcc.cli.highlight.TokenType;
import org.jline.utils.AttributedCharSequence;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import java.util.function.Function;

public final class Style {

    public static final Style PLAIN = new Style(false, false);

    private final boolean color;
    private final boolean truecolor;

    private Style(boolean color, boolean truecolor) {
        this.color = color;
        this.truecolor = truecolor;
    }

    public static Style colored() {
        return new Style(true, false);
    }

    public static Style truecolor() {
        return new Style(true, true);
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
    public String keyword(String s)    { return wrap(s, AttributedStyle.BOLD.foregroundRgb(0xCC7832)); }
    public String stringLit(String s)  { return wrap(s, AttributedStyle.DEFAULT.foregroundRgb(0x6A8759)); }
    public String comment(String s)    { return wrap(s, AttributedStyle.DEFAULT.foregroundRgb(0x808080).italic()); }
    public String number(String s)     { return wrap(s, AttributedStyle.DEFAULT.foregroundRgb(0x6897BB)); }
    public String annotation(String s) { return wrap(s, AttributedStyle.DEFAULT.foregroundRgb(0xBBB529)); }
    public String operator(String s)   { return wrap(s, AttributedStyle.DEFAULT.foregroundRgb(0xA9B7C6)); }

    public String forToken(TokenType type, String text) {
        return switch (type) {
            case KEYWORD -> keyword(text);
            case STRING -> stringLit(text);
            case COMMENT -> comment(text);
            case NUMBER -> number(text);
            case ANNOTATION -> annotation(text);
            case OPERATOR -> operator(text);
            case IDENTIFIER, OTHER -> text;
        };
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
