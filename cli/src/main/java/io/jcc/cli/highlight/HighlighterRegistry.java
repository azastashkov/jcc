package io.jcc.cli.highlight;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class HighlighterRegistry {

    private final Map<String, CodeHighlighter> highlighters = new HashMap<>();

    public HighlighterRegistry() {}

    public static HighlighterRegistry defaults() {
        HighlighterRegistry r = new HighlighterRegistry();
        r.register("java", new JavaHighlighter());
        BashHighlighter bash = new BashHighlighter();
        r.register("bash", bash);
        r.register("sh", bash);
        r.register("shell", bash);
        r.register("json", new JsonHighlighter());
        return r;
    }

    public void register(String language, CodeHighlighter highlighter) {
        highlighters.put(language.toLowerCase(Locale.ROOT), highlighter);
    }

    public Optional<CodeHighlighter> get(String language) {
        if (language == null || language.isEmpty()) return Optional.empty();
        return Optional.ofNullable(highlighters.get(language.toLowerCase(Locale.ROOT)));
    }
}
