import static java.net.StandardSocketOptions.SO_REUSEPORT;
import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

public class HoldPorts implements Runnable {
  static private final Set<InetAddress> ALL_ADDRESSES;

  static {
    try {
      ALL_ADDRESSES = Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
        .flatMap(NetworkInterface::inetAddresses)
        .filter(HoldPorts::isReachable)
        .collect(toSet());
    } catch (SocketException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static boolean isReachable(InetAddress addr) {
    try {
      return addr.isReachable(100);
    } catch (IOException e) {
      return false;
    }
  }

  private static final int[] TCP_PORTS = new int[]{
    1099, // JMX management
    7070, // HTTP server e.g. Pulse
    8080, // REST API
    40404, // Cache server
  };

  public static void main(String[] ignored) {
    Executor executor = Executors.newFixedThreadPool(TCP_PORTS.length * ALL_ADDRESSES.size());
    Arrays.stream(TCP_PORTS)
      .boxed()
      .flatMap(HoldPorts::allSocketAddresses)
      .map(HoldPorts::new)
      .forEach(executor::execute);
  }

  private static Stream<InetSocketAddress> allSocketAddresses(int port) {
    return ALL_ADDRESSES.stream()
      .map(i -> new InetSocketAddress(i, port));
  }

  private final InetSocketAddress addr;

  HoldPorts(InetSocketAddress addr) {
    this.addr = addr;
  }

  @Override
  public void run() {
    try (ServerSocket server = bind(addr)) {
      try (Socket client = connect(server)) {
        try {
          server.accept();
        } catch (IOException e) {
          System.out.printf("Cannot accept %s:%d (client %s:%d): %s%n",
            server.getInetAddress(), server.getLocalPort(),
            client.getLocalAddress(), client.getLocalPort(),
            e
          );
          return;
        }
        System.out.printf("Holding %s:%d%n",
          server.getInetAddress(), server.getLocalPort()
          );
        while (true) {
          try {
            Thread.sleep(60_000);
          } catch (InterruptedException e) {
          }
        }
      } catch (IOException e) {
        System.out.printf("Cannot connect to %s:%d: %s%n",
          server.getInetAddress(), server.getLocalPort(),
          e
        );
      }
    } catch (IOException e) {
      System.out.printf("Cannot bind %s:%d: %s%n",
        addr.getAddress(), addr.getPort(),
        e
      );
    }
  }

  private Socket connect(ServerSocket server) throws IOException {
    Socket client = new Socket();
    client.connect(server.getLocalSocketAddress(), server.getLocalPort());
    return client;
  }

  private static ServerSocket bind(InetSocketAddress addr) throws IOException {
    ServerSocket server = new ServerSocket();
    server.setReuseAddress(true);
    server.setOption(SO_REUSEPORT, false);
    server.setSoTimeout(120_000);
    server.bind(addr);
    return server;
  }
}
