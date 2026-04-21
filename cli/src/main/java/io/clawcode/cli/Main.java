package io.clawcode.cli;

import picocli.CommandLine;

public final class Main {

    private Main() {}

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ClawCommand()).execute(args);
        System.exit(exitCode);
    }
}
