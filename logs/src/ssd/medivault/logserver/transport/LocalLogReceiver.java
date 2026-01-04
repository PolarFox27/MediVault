package ssd.medivault.logserver.transport;

import ssd.medivault.logserver.service.LogChainService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;

public class LocalLogReceiver {
    private final Path socketPath;
    private final LogChainService chainService;

    public LocalLogReceiver(Path socketPath, LogChainService chainService) {
        this.socketPath = socketPath;
        this.chainService = chainService;
    }

    public void start() throws Exception {
        UnixDomainSocketAddress address = UnixDomainSocketAddress.of(socketPath);
        try (ServerSocketChannel server = ServerSocketChannel.open()) {
            server.bind(address);
            while (true) {
                SocketChannel client = server.accept();
                handleClient(client);
            }
        }
    }

    private void handleClient(SocketChannel client) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(client.socket().getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                chainService.appendRaw(line);
            }
        }
    }
}

