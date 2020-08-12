package com.vmware.demery;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class CheckReservations {
  public static void main(String[] args) {
    Consumer<ServerSocket> beforeBinding = CheckReservations::doNothing;
    Consumer<ServerSocket> afterBinding = CheckReservations::doNothing;

    if (args.length < 1 || args.length > 3) {
      usage();
    }

    int nPorts = Integer.parseInt(args[0]);
    for (int i = 1; i < args.length; i++) {
      switch (args[i]) {
        case "reuse":
          System.out.println("Enable SO_REUSEADDR before binding");
          beforeBinding = CheckReservations::enableReuseAddress;
          break;
        case "no-reuse":
          System.out.println("Disable SO_REUSEADDR before binding");
          beforeBinding = CheckReservations::disableReuseAddress;
          break;
        case "connect":
          System.out.println("Connect after binding");
          afterBinding = CheckReservations::connect;
          break;
        default:
          usage();
      }
    }

    Set<Integer> uniquePortNumbers = new HashSet<>();
    List<Integer> duplicates = new ArrayList<>();

    for (int i = 0; i < nPorts; i++) {
      int port = reserve(beforeBinding, afterBinding);
      System.out.print(port);
      if (uniquePortNumbers.contains(port)) {
        System.out.print("!!!");
        duplicates.add(port);
      }
      System.out.println();
      uniquePortNumbers.add(port);
    }

    System.out.format("%nSystem picked %d/%d duplicate ports%n", duplicates.size(), nPorts);
    if (duplicates.size() != 0) {
      Collections.sort(duplicates);
      System.out.println(duplicates);
    }
  }

  private static void usage() {
    System.err.format("Usage: %s n [options]%n", CheckReservations.class.getSimpleName());
    System.err.println("Options:");
    System.err.println("    reuse:    Enable SO_REUSEADDR before binding");
    System.err.println("    no-reuse: Disable SO_REUSEADDR before binding");
    System.err.println("    connect:  Connect after binding");
    System.exit(1);
  }

  public static int reserve(Consumer<ServerSocket> before, Consumer<ServerSocket> after) {
    try (ServerSocket socket = new ServerSocket()) {
      before.accept(socket);
      socket.bind(new InetSocketAddress(0));
      int port = socket.getLocalPort();
      after.accept(socket);
      return port;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void doNothing(ServerSocket ignored) {
  }

  public static void enableReuseAddress(ServerSocket socket) {
    try {
      socket.setReuseAddress(true);
    } catch (SocketException e) {
      throw new RuntimeException(e);
    }
  }

  public static void disableReuseAddress(ServerSocket socket) {
    try {
      socket.setReuseAddress(false);
    } catch (SocketException e) {
      throw new RuntimeException(e);
    }
  }

  public static void connect(ServerSocket socket) {
    try {
      Socket client = new Socket(socket.getInetAddress(), socket.getLocalPort());
      Socket server = socket.accept();
      client.close();
      server.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
