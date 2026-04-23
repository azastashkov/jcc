package io.jcc.cli;

import io.jcc.cli.highlight.CodeHighlighter;
import io.jcc.cli.highlight.HighlighterRegistry;
import io.jcc.cli.highlight.Token;
import io.jcc.cli.highlight.TokenType;

import java.io.PrintStream;
import java.util.List;
import java.util.Optional;

public final class MarkdownTextFilter {

    private enum Mode { PROSE, FENCE_TAG, IN_CODE }

    // JetBrains New UI Dark editor background.
    private static final int CODE_BG_RGB = 0x1E1F22;

    private final PrintStream out;
    private final Style style;
    private final Style codeStyle;
    private final HighlighterRegistry registry;

    private Mode mode = Mode.PROSE;
    private int proseBacktickRun;
    private int codeBacktickRun;
    private final StringBuilder fenceTagBuffer = new StringBuilder();
    private final StringBuilder codeBuffer = new StringBuilder();
    private String currentLang;

    public MarkdownTextFilter(PrintStream out, Style style, HighlighterRegistry registry) {
        this.out = out;
        this.style = style;
        this.codeStyle = style.withCodeBackground(CODE_BG_RGB);
        this.registry = registry;
    }

    public void appendText(String chunk) {
        if (chunk == null || chunk.isEmpty()) return;
        for (int i = 0; i < chunk.length(); i++) {
            char c = chunk.charAt(i);
            switch (mode) {
                case PROSE -> handleProse(c);
                case FENCE_TAG -> handleFenceTag(c);
                case IN_CODE -> handleCode(c);
            }
        }
        out.flush();
    }

    public void flush() {
        if (proseBacktickRun > 0) {
            for (int k = 0; k < proseBacktickRun; k++) out.print('`');
            proseBacktickRun = 0;
        }
        if (mode == Mode.FENCE_TAG) {
            out.print("```");
            out.print(fenceTagBuffer);
            fenceTagBuffer.setLength(0);
            mode = Mode.PROSE;
        }
        if (mode == Mode.IN_CODE) {
            out.print("```");
            if (currentLang != null && !currentLang.isEmpty()) out.print(currentLang);
            out.print('\n');
            out.print(codeBuffer);
            for (int k = 0; k < codeBacktickRun; k++) out.print('`');
            codeBuffer.setLength(0);
            codeBacktickRun = 0;
            currentLang = null;
            mode = Mode.PROSE;
        }
        out.flush();
    }

    private void handleProse(char c) {
        if (c == '`') {
            proseBacktickRun++;
            if (proseBacktickRun == 3) {
                proseBacktickRun = 0;
                mode = Mode.FENCE_TAG;
            }
            return;
        }
        if (proseBacktickRun > 0) {
            for (int k = 0; k < proseBacktickRun; k++) out.print('`');
            proseBacktickRun = 0;
        }
        out.print(c);
    }

    private void handleFenceTag(char c) {
        if (c == '\n') {
            currentLang = fenceTagBuffer.toString().trim();
            fenceTagBuffer.setLength(0);
            mode = Mode.IN_CODE;
            return;
        }
        fenceTagBuffer.append(c);
    }

    private void handleCode(char c) {
        if (c == '`') {
            codeBacktickRun++;
            if (codeBacktickRun == 3) {
                codeBacktickRun = 0;
                emitHighlightedBlock();
                mode = Mode.PROSE;
            }
            return;
        }
        if (codeBacktickRun > 0) {
            for (int k = 0; k < codeBacktickRun; k++) codeBuffer.append('`');
            codeBacktickRun = 0;
        }
        codeBuffer.append(c);
    }

    private void emitHighlightedBlock() {
        String code = stripTrailingNewline(codeBuffer.toString());
        codeBuffer.setLength(0);
        Optional<CodeHighlighter> h = registry.get(currentLang);
        String eol = codeStyle.codeBackgroundEraseEol();

        if (h.isPresent()) {
            for (Token t : h.get().tokenize(code)) {
                emitTokenSegments(t, eol);
            }
        } else {
            String[] lines = code.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) out.print('\n');
                if (!lines[i].isEmpty()) {
                    out.print(codeStyle.forToken(TokenType.OTHER, lines[i]));
                }
                out.print(eol);
            }
        }
        if (h.isPresent()) {
            out.print(eol);
        }
        out.print('\n');
        currentLang = null;
    }

    private void emitTokenSegments(Token t, String eol) {
        String text = t.text();
        int from = 0;
        int nl;
        while ((nl = text.indexOf('\n', from)) >= 0) {
            String chunk = text.substring(from, nl);
            if (!chunk.isEmpty()) {
                out.print(codeStyle.forToken(t.type(), chunk));
            }
            out.print(eol);
            out.print('\n');
            from = nl + 1;
        }
        if (from < text.length()) {
            out.print(codeStyle.forToken(t.type(), text.substring(from)));
        }
    }

    private static String stripTrailingNewline(String s) {
        if (s.endsWith("\n")) return s.substring(0, s.length() - 1);
        return s;
    }
}
