package com.vmware.demery;

import static java.net.StandardSocketOptions.SO_REUSEADDR;
import static java.net.StandardSocketOptions.SO_REUSEPORT;

import java.io.IOException;
import java.net.InetAddress;
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

public class CheckReservations {
  private static boolean log = true;
  private static boolean connect = false;
  private static long connectionHoldDuration = 0;
  private static int preConnectDelay = 0;
  private static boolean reuseAddress = false;
  private static boolean setReuseAddress = false;
  private static boolean reusePort = false;
  private static boolean setReusePort = false;
  private static int timeout = 0;


  public static void main(String[] args) throws IOException, InterruptedException {
    if (args.length < 1) {
      usage();
    }

    int nPorts = Integer.parseInt(args[0]);
    parseOptions(args);

    Set<Integer> uniquePortNumbers = new HashSet<>();
    List<Integer> duplicates = new ArrayList<>();
    List<Integer> unavailable = new ArrayList<>();

    for (int i = 0; i < nPorts; i++) {
      logf("%8d", i + 1);
      int port = -1;
      try {
        port = reserve();
        if (uniquePortNumbers.contains(port)) {
          log(" DUPLICATE");
          duplicates.add(port);
        }
        uniquePortNumbers.add(port);
      } catch (SocketTimeoutException ignored) {
        // The server timed out accepting the connection. This may mean that the system gave us an
        // ephemeral port that is already in use. The SO_REUSEPORT socket option enables this 
        // behavior, as long as our socket and all existing uses are associated with the same user
        // and all have SO_REUSEPORT enabled. On macOS, SO_REUSEPORT is enabled by default for each 
        // socket. Java 8 offers no way to override this socket option, so we have to account for
        // the possibility that the system could give us an ephemeral port that is already in use.
        log(" X");
        unavailable.add(port);
      }
      log("\n");
    }

    System.out.format("Attempted %d reservations%n", nPorts);
    System.out.format("    %d ports were already in use%n", unavailable.size());
    if (duplicates.size() != 0) {
      Collections.sort(duplicates);
      System.out.println(duplicates);
    }
    System.out.format("    %d ports were already reserved%n", duplicates.size());
    if (duplicates.size() != 0) {
      Collections.sort(duplicates);
      System.out.println(duplicates);
    }
  }

  public static int reserve() throws IOException, InterruptedException {
    try (ServerSocket socket = new ServerSocket()) {
      setReuseAddress(socket);
      setReusePort(socket);
      int port = bind(socket);
      connect(socket);
      return port;
    }
  }

  private static int bind(ServerSocket socket) throws IOException {
    socket.bind(new InetSocketAddress((InetAddress) null, 0));
    logf(" B%s…", optionFlags(socket));
    int port = socket.getLocalPort();
    logf("%d%s", port, optionFlags(socket));
    return port;
  }

  public static void connect(ServerSocket socket) throws IOException, InterruptedException {
    if (!connect) {
      return;
    }
    try (Socket client = new Socket()) {
      logf(" C%s…", optionFlags(client));
      client.connect(socket.getLocalSocketAddress(), socket.getLocalPort());
      logf("%d%s", client.getLocalPort(), optionFlags(client));
      accept(socket);
    } finally {
      log(" c");
    }
  }

  private static void accept(ServerSocket socket) throws IOException, InterruptedException {
    setTimeout(socket);
    sleep();
    log(" S…");
    try (Socket server = socket.accept()) {
      logf("%d%s", server.getLocalPort(), optionFlags(server));
      hold();
    } finally {
      log(" s");
    }
  }

  private static void hold() throws InterruptedException {
    if (connectionHoldDuration > 0) {
      logf(" h%s…", connectionHoldDuration);
      Thread.sleep(connectionHoldDuration);
      log("h");
    }
  }

  private static void sleep() throws InterruptedException {
    if (preConnectDelay > 0) {
      logf(" z%s…", preConnectDelay);
      Thread.sleep(preConnectDelay);
      log("z");
    }
  }

  private static void setReuseAddress(ServerSocket socket) throws IOException {
    if (setReuseAddress) {
      logf(" %sRA", optionFlag(reuseAddress));
      socket.setOption(SO_REUSEADDR, reuseAddress);
    }
  }

  private static void setReusePort(ServerSocket socket) throws IOException {
    if (setReusePort) {
      logf(" %sRP", optionFlag(reusePort));
      socket.setOption(SO_REUSEPORT, reusePort);
    }
  }

  private static void setTimeout(ServerSocket socket) throws SocketException {
    if (timeout > 0) {
      socket.setSoTimeout(timeout);
      log(" t" + timeout);
    }
  }

  private static void log(String msg) {
    if (log) {
      System.out.print(msg);
    }
  }

  private static void logf(String format, Object... obj) {
    if (log) {
      System.out.printf(format, obj);
    }
  }

  private static void parseOptions(String[] args) {
    for (int i = 1; i < args.length; i++) {
      switch (args[i]) {
        case "connect":
          connect = true;
          break;
        case "hold":
          connect = true;
          connectionHoldDuration = integerArg(args, ++i);
          break;
        case "no-ra":
          reuseAddress = false;
          setReuseAddress = true;
          break;
        case "no-rp":
          reusePort = false;
          setReusePort = true;
          break;
        case "quiet":
          log = false;
          break;
        case "ra":
          reuseAddress = true;
          setReuseAddress = true;
          break;
        case "rp":
          reusePort = true;
          setReusePort = true;
          break;
        case "sleep":
          connect = true;
          preConnectDelay = integerArg(args, ++i);
          break;
        case "timeout":
          connect = true;
          timeout = integerArg(args, ++i);
          break;
        default:
          usage();
      }
    }
  }

  private static int integerArg(String[] args, int i) {
    if (args.length < i) {
      usage();
    }
    return Integer.parseInt(args[i]);
  }

  private static void usage() {
    System.out.format("Usage: %s nports [options]%n", CheckReservations.class.getSimpleName());
    System.out.println("Options:");
    System.out.println("    [no-]ra      Enable/Disable SO_REUSEADDR before binding");
    System.out.println("    [no-]rp      Enable/Disable SO_REUSEPORT before binding");
    System.out.println("    connect      Connect after binding");
    System.out.println("    timeout ms   Set socket timeout to ms before server accept"
        + "(enables connect)");
    System.out.println("    sleep ms     Sleep for ms between client connect and server accept"
        + "(enables connect)");
    System.out.println("    retry ms     Sleep for ms between attempts to accept"
        + "(enables connect)");
    System.out.println("    hold ms      Hold the connection for ms before closing"
        + "(enables connect)");
    System.out.println("    quiet        Do not print details for each reservation");
    System.exit(1);
  }

  private static String optionFlags(Socket socket) throws IOException {
    boolean ra = socket.getOption(SO_REUSEADDR);
    boolean rp = socket.getOption(SO_REUSEPORT);
    return String.format("[%s]", optionFlags(ra, rp));
  }

  private static String optionFlags(ServerSocket socket) throws IOException {
    boolean ra = socket.getOption(SO_REUSEADDR);
    boolean rp = socket.getOption(SO_REUSEPORT);
    return String.format("[%s]", optionFlags(ra, rp));
  }

  private static String optionFlags(boolean ra, boolean rp) {
    String raFlag = optionFlag(ra);
    String rpFlag = optionFlag(rp);
    return String.format("%sRA%sRP", raFlag, rpFlag);
  }

  private static String optionFlag(boolean enabled) {
    return enabled ? "+" : "-";
  }
}
