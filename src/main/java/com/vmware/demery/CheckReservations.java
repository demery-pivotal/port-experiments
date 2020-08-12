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

public class CheckReservations {
  private static boolean log = true;
  private static boolean connect = false;
  private static boolean reuse;
  private static boolean setReuse = false;
  private static int sleep = 0;
  private static int timeout = 0;


  public static void main(String[] args) throws IOException, InterruptedException {
    if (args.length < 1) {
      usage();
    }

    int nPorts = Integer.parseInt(args[0]);
    parseOptions(args);

    Set<Integer> uniquePortNumbers = new HashSet<>();
    List<Integer> duplicates = new ArrayList<>();

    for (int i = 0; i < nPorts; i++) {
      int port = reserve();
      if (uniquePortNumbers.contains(port)) {
        log(" DUPLICATE");
        duplicates.add(port);
      }
      log("\n");
      uniquePortNumbers.add(port);
    }

    System.out.format("System picked %d duplicate ports in %d reservations%n", duplicates.size(),
        nPorts);
    if (duplicates.size() != 0) {
      Collections.sort(duplicates);
      System.out.println(duplicates);
    }
  }

  public static int reserve() throws IOException, InterruptedException {
    int port = -1;
    try (ServerSocket socket = new ServerSocket()) {
      setReuse(socket);
      port = bind(socket);
      connect(socket);
      return port;
    }
  }

  private static int bind(ServerSocket socket) throws IOException {
    socket.bind(new InetSocketAddress(0));
    int port = socket.getLocalPort();
    log(port);
    return port;
  }

  private static void setReuse(ServerSocket socket) throws SocketException {
    if (setReuse) {
      socket.setReuseAddress(reuse);
    }
  }

  private static void setTimeout(ServerSocket socket) throws SocketException {
    if (timeout > 0) {
      log(" t" + timeout);
      socket.setSoTimeout(timeout);
    }
  }

  public static void connect(ServerSocket socket) throws IOException, InterruptedException {
    if (!connect) {
      return;
    }
    try (Socket ignored = new Socket(socket.getInetAddress(), socket.getLocalPort())) {
      log(" C");
      sleep();
      accept(socket);
    } finally {
      log(" c");
    }
  }

  private static void sleep() throws InterruptedException {
    if (sleep > 0) {
      log(" z" + sleep);
      Thread.sleep(sleep);
    }
  }

  private static void accept(ServerSocket socket) throws IOException {
    setTimeout(socket);
    try (Socket ignored = socket.accept()) {
      log(" S");
    } finally {
      log(" s");
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

  private static void parseOptions(String[] args) {
    for (int i = 1; i < args.length; i++) {
      switch (args[i]) {
        case "connect":
          connect = true;
          break;
        case "no-reuse":
          reuse = false;
          setReuse = true;
          break;
        case "quiet":
          log = false;
          break;
        case "reuse":
          reuse = true;
          setReuse = true;
          break;
        case "sleep":
          if (args.length < i + 1) {
            usage();
          }
          i++;
          sleep = Integer.parseInt(args[i]);
          connect = true;
          break;
        case "timeout":
          if (args.length < i + 1) {
            usage();
          }
          i++;
          timeout = Integer.parseInt(args[i]);
          connect = true;
          break;
        default:
          usage();
      }
    }
  }

  private static void usage() {
    System.out.format("Usage: %s nports [options]%n", CheckReservations.class.getSimpleName());
    System.out.println("Options:");
    System.out.println("    [no-]reuse   Enable/Disable SO_REUSEADDR before binding");
    System.out.println(
        "    timeout ms   Set socket timeout to ms before server accept (sets connect true)");
    System.out.println(
        "    sleep ms     Sleep for ms between client connect and server accept (sets connect "
            + "true)");
    System.out.println("    connect      Connect after binding");
    System.out.println("    quiet        Do not print details for each reservation");
    System.exit(1);
  }
}
