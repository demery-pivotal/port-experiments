import static java.net.StandardSocketOptions.SO_REUSEPORT;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class HoldPorts implements Runnable {
  private static int[] TCP_PORTS = new int[]{
      1099, // JMX management
      7070, // HTTP server e.g. Pulse
      8080, // REST API
      40404, // Cache server
  };

  public static void main(String[] ignored) {
    Executor executor = Executors.newFixedThreadPool(TCP_PORTS.length);
    Arrays.stream(TCP_PORTS)
        .mapToObj(HoldPorts::new)
        .forEach(executor::execute);
  }

  private final int port;

  HoldPorts(int port) {
    this.port = port;
  }

  @Override
  public void run() {
    try (ServerSocket server = bind(port)) {
      try (Socket client = connect(server)) {
        server.accept();
        System.out.printf("Holding TCP port %d%n", port);
        while (true) {
          Thread.sleep(120_000);
        }
      }
    } catch (InterruptedException e) {
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private Socket connect(ServerSocket server) {
    Socket client = new Socket();
    try {
      client.connect(server.getLocalSocketAddress(), server.getLocalPort());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return client;
  }

  private static ServerSocket bind(int port) {
    try {
      ServerSocket server = new ServerSocket();
      server.setReuseAddress(true);
      server.setOption(SO_REUSEPORT, true);
      server.setSoTimeout(120_000);
      InetSocketAddress address = new InetSocketAddress((InetAddress) null, port);
      server.bind(address);
      return server;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
