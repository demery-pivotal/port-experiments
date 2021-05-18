import static java.net.StandardSocketOptions.SO_REUSEPORT;
import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
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
        .collect(toSet());
    } catch (SocketException e) {
      throw new UncheckedIOException(e);
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
        server.accept();
        System.out.printf("Holding %d on %s%n", addr.getPort(), addr.getAddress());
        while (true) {
          Thread.sleep(120_000);
        }
      }
    } catch (InterruptedException | SocketTimeoutException | ConnectException e) {
    } catch (IOException e) {
      System.out.printf("Could not hold %s: %s%n", addr, e);
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
    server.setOption(SO_REUSEPORT, true);
    server.setSoTimeout(120_000);
    server.bind(addr);
    return server;
  }
}
