package io.jcc.cli;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import java.util.function.Function;

public final class Style {

    public static final Style PLAIN = new Style(false);

    private final boolean color;

    private Style(boolean color) {
        this.color = color;
    }

    public static Style colored() {
        return new Style(true);
    }

    public static Style detect() {
        return detect(System::getenv);
    }

    static Style detect(Function<String, String> env) {
        if (env.apply("NO_COLOR") != null) return PLAIN;
        if (env.apply("FORCE_COLOR") != null) return colored();
        if ("dumb".equals(env.apply("TERM"))) return PLAIN;
        if (System.console() == null) return PLAIN;
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

    boolean isColor() { return color; }

    private String wrap(String s, AttributedStyle style) {
        if (!color || s == null || s.isEmpty()) return s;
        return new AttributedString(s, style).toAnsi();
    }
}
