package io.jcc.cli.highlight;

import java.util.List;

public interface CodeHighlighter {
    List<Token> tokenize(String source);
}
