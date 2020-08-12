package com.vmware.demery;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class CheckReservations {
  private static boolean log = true;

  public static void main(String[] args) {
    Consumer<ServerSocket> beforeBinding = CheckReservations::doNothing;
    Consumer<ServerSocket> afterBinding = CheckReservations::doNothing;

    if (args.length < 1) {
      usage();
    }

    int nPorts = Integer.parseInt(args[0]);
    for (int i = 1; i < args.length; i++) {
      switch (args[i]) {
        case "reuse":
          beforeBinding = beforeBinding.andThen(CheckReservations::enableReuseAddress);
          break;
        case "no-reuse":
          beforeBinding = beforeBinding.andThen(CheckReservations::disableReuseAddress);
          break;
        case "connect":
          afterBinding = afterBinding.andThen(CheckReservations::connect);
          break;
        case "timeout":
          if (args.length < i + 1) {
            usage();
          }
          i++;
          int timeout = Integer.parseInt(args[i]);
          afterBinding = afterBinding.andThen(setTimeout(timeout));
          break;
        case "quiet":
          log = false;
          break;
        default:
          usage();
      }
    }

    Set<Integer> uniquePortNumbers = new HashSet<>();
    List<Integer> duplicates = new ArrayList<>();

    for (int i = 0; i < nPorts; i++) {
      int port = reserve(beforeBinding, afterBinding);
      if (uniquePortNumbers.contains(port)) {
        log(" DUPLICATE");
        duplicates.add(port);
      }
      log("\n");
      uniquePortNumbers.add(port);
    }

    System.out.format("%nSystem picked %d duplicate ports in %d reservations%n", duplicates.size(),
        nPorts);
    if (duplicates.size() != 0) {
      Collections.sort(duplicates);
      System.out.println(duplicates);
    }
  }

  private static void usage() {
    System.out.format("Usage: %s nports [options]%n", CheckReservations.class.getSimpleName());
    System.out.println("Options:");
    System.out.println("    connect      Connect after binding");
    System.out.println("    [no-]reuse   Enable/Disable SO_REUSEADDR before binding");
    System.out.println("    timeout ms   Set socket timeout to <ms> after binding");
    System.exit(1);
  }

  public static int reserve(Consumer<ServerSocket> before, Consumer<ServerSocket> after) {
    int port = -1;
    try (ServerSocket socket = new ServerSocket()) {
      before.accept(socket);
      socket.bind(new InetSocketAddress(0));
      port = socket.getLocalPort();
      log(port);
      after.accept(socket);
    } catch (Throwable e) {
      throw new RuntimeException("Port " + port, e);
    }
    return port;
  }

  public static void doNothing(ServerSocket ignored) {
  }

  public static void enableReuseAddress(ServerSocket socket) {
    try {
      socket.setReuseAddress(true);
      log("+ ");
    } catch (SocketException e) {
      throw new RuntimeException(e);
    }
  }

  public static void disableReuseAddress(ServerSocket socket) {
    try {
      socket.setReuseAddress(false);
      log("- ");
    } catch (SocketException e) {
      throw new RuntimeException(e);
    }
  }

  public static Consumer<ServerSocket> setTimeout(int ms) {
    return s -> {
      try {
        s.setSoTimeout(ms);
        log(" " + ms + "ms");
      } catch (SocketException e) {
        throw new RuntimeException(e);
      }
    };
  }

  public static void connect(ServerSocket socket) {
    try (Socket client = new Socket(socket.getInetAddress(), socket.getLocalPort())) {
      log(" C");
      try (Socket server = socket.accept()) {
        log(" S");
      } catch (SocketTimeoutException thrown) {
        log(" TIMEOUT ");
        throw thrown;
      } finally {
        log(" s");
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      log(" c");
    }
  }

  private static void log(int i) {
    if (log) {
      System.out.print(i);
    }
  }

  private static void log(String msg) {
    if (log) {
      System.out.print(msg);
    }
  }
}
