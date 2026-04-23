package io.jcc.cli;

import io.jcc.cli.highlight.CodeHighlighter;
import io.jcc.cli.highlight.HighlighterRegistry;
import io.jcc.cli.highlight.Token;

import java.io.PrintStream;
import java.util.List;
import java.util.Optional;

public final class MarkdownTextFilter {

    private enum Mode { PROSE, FENCE_TAG, IN_CODE }

    private final PrintStream out;
    private final Style style;
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
        if (h.isPresent()) {
            List<Token> tokens = h.get().tokenize(code);
            for (Token t : tokens) {
                out.print(style.forToken(t.type(), t.text()));
            }
        } else {
            out.print(code);
        }
        out.print('\n');
        currentLang = null;
    }

    private static String stripTrailingNewline(String s) {
        if (s.endsWith("\n")) return s.substring(0, s.length() - 1);
        return s;
    }
}
