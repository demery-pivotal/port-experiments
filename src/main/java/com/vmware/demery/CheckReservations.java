package com.vmware.demery;

import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public class CheckReservations {
  public static void main(String[] args) {
    Consumer<ServerSocket> beforeBinding = doNothing();
    Consumer<ServerSocket> afterBinding = doNothing();

    if (args.length < 1 || args.length > 3) {
      usage();
    }

    int nPorts = Integer.parseInt(args[0]);
    for (int i = 1; i < args.length; i++) {
      switch (args[i]) {
        case "reuse":
          System.out.println("Enabling SO_REUSEADDR");
          beforeBinding = enableReuseAddress();
          break;
        case "no-reuse":
          System.out.println("Disabling SO_REUSEADDR");
          beforeBinding = disableReuseAddress();
          break;
        case "connect":
          System.out.println("Connecting each reserved port");
          afterBinding = connect();
          break;
        default:
          usage();
      }
    }

    Consumer<ServerSocket> before = beforeBinding;
    Consumer<ServerSocket> after = afterBinding;

    Set<Integer> uniquePortNumbers = IntStream.range(0, nPorts)
        .mapToObj(i -> reserve(before, after))
        .collect(toSet());

    int nDuplicates = nPorts - uniquePortNumbers.size();

    System.out.format("System picked %d/%d duplicate ports%n", nDuplicates, nPorts);
  }

  private static void usage() {
    System.err
        .format("Usage: %s n [[no-]reuse] [connect]", CheckReservations.class.getSimpleName());
    System.exit(1);
  }

  public static int reserve(Consumer<ServerSocket> before, Consumer<ServerSocket> after) {
    try (ServerSocket socket = new ServerSocket()) {
      before.accept(socket);
      socket.bind(new InetSocketAddress(0));
      after.accept(socket);
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Consumer<ServerSocket> doNothing() {
    return s -> {
    };
  }

  public static Consumer<ServerSocket> enableReuseAddress() {
    return setReuseAddress(true);
  }

  public static Consumer<ServerSocket> disableReuseAddress() {
    return setReuseAddress(false);
  }

  public static Consumer<ServerSocket> connect() {
    return s -> {
      try {
        Socket client = new Socket(s.getInetAddress(), s.getLocalPort());
        Socket server = s.accept();
        client.close();
        server.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    };
  }

  private static Consumer<ServerSocket> setReuseAddress(boolean reuse) {
    return s -> {
      try {
        s.setReuseAddress(reuse);
      } catch (SocketException e) {
        throw new RuntimeException(e);
      }
    };
  }
}
