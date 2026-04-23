package io.jcc.cli;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.jline.utils.Status;

import java.util.List;

public final class StatusBar {

    private final Status status;

    public StatusBar(Terminal terminal) {
        Status s;
        try {
            s = Status.getStatus(terminal, true);
        } catch (Throwable t) {
            s = null;
        }
        this.status = s;
    }

    public void waiting()              { update("✻ Waiting for response…"); }
    public void streaming()            { update("✻ Receiving…"); }
    public void runningTool(String n)  { update("✻ Calling " + n + "…"); }
    public void clear()                { hide(); }

    private void update(String text) {
        if (status == null) return;
        AttributedString line = new AttributedString(text, AttributedStyle.DEFAULT.faint());
        status.update(List.of(line));
    }

    private void hide() {
        if (status == null) return;
        status.update(List.of());
    }
}
