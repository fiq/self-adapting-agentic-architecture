package com.dreamthought.saaa.cli;

import com.dreamthought.saaa.adapters.mcp.EvolveMcpServer;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

@Command(
        name = "mcp",
        description = "Start the SAAA MCP server over stdio."
)
public final class McpCommand implements Callable<Integer> {
    @Override
    public Integer call() throws InterruptedException {
        AutoCloseable server = EvolveMcpServer.startStdio();
        try {
            Thread.currentThread().join();
            return 0;
        } finally {
            close(server);
        }
    }

    private static void close(AutoCloseable server) {
        try {
            server.close();
        } catch (Exception exception) {
            throw new IllegalStateException("failed to close MCP server", exception);
        }
    }
}
