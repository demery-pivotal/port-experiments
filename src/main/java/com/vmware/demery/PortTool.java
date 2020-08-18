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
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PortTool {
  private static boolean accept = false;
  private static InetAddress bindAddress = null;
  private static int bindPort = 0;
  private static boolean log = true;
  private static boolean connect = false;
  private static long connectionHoldDuration = 0;
  private static int nPorts = 1;
  private static int delayBetweenConnectAndAccept = 0;
  private static boolean reuseAddress = false;
  private static boolean reusePort = false;
  private static boolean setReuseAddress = false;
  private static boolean setReusePort = false;
  private static int acceptTimeout = 0;
  private static final List<Integer> unacceptablePorts = new ArrayList<>();

  public static void main(String[] args) throws IOException, InterruptedException {
    if (args.length < 1) {
      usage();
    }
    parseOptions(args);

    Set<Integer> uniquePortNumbers = new HashSet<>();
    List<Integer> duplicates = new ArrayList<>();

    for (int i = 0; i < nPorts; i++) {
      logf("%8d", i + 1);
      int port = -1;
      port = reserve();
      if (uniquePortNumbers.contains(port)) {
        log(" DUPLICATE");
        duplicates.add(port);
      }
      uniquePortNumbers.add(port);
      log("\n");
    }

    if (nPorts > 1) {
      System.out.format("%d duplicate ephemeral ports%n", duplicates.size());
      if (duplicates.size() != 0) {
        Collections.sort(duplicates);
        System.out.println(duplicates);
      }
    }
    if (accept) {
      System.out.format("%d ports failed to accept%n", unacceptablePorts.size());
      if (unacceptablePorts.size() != 0) {
        Collections.sort(unacceptablePorts);
        System.out.println(unacceptablePorts);
      }
    }
  }

  public static int reserve() throws IOException, InterruptedException {
    try (ServerSocket socket = new ServerSocket()) {
      setReuseAddress(socket);
      setReusePort(socket);
      int port = bind(socket);
      if (connect) {
        connect(socket);
      } else if (accept) {
        accept(socket);
      }
      return port;
    }
  }

  private static int bind(ServerSocket socket) throws IOException {
    socket.bind(new InetSocketAddress(bindAddress, bindPort));
    logf(" B%s…", optionFlags(socket));
    int port = socket.getLocalPort();
    logf("%s%s", socket.getLocalSocketAddress(), optionFlags(socket));
    return port;
  }

  public static void connect(ServerSocket socket) throws IOException, InterruptedException {
    try (Socket client = new Socket()) {
      logf(" C%s…", optionFlags(client));
      client.connect(socket.getLocalSocketAddress(), socket.getLocalPort());
      logf("%s%s", client.getLocalSocketAddress(), optionFlags(client));
      pauseAfterConnecting();
      accept(socket);
    } finally {
      log(" c");
    }
  }

  private static void accept(ServerSocket socket) throws IOException, InterruptedException {
    setAcceptTimeout(socket);
    log(" A…");
    try (Socket ignored = socket.accept()) {
      holdConnection();
    } catch (SocketTimeoutException ignored) {
      log(" X");
      unacceptablePorts.add(socket.getLocalPort());
    } finally {
      log(" a");
    }
  }

  private static void holdConnection() throws InterruptedException {
    if (connectionHoldDuration > 0) {
      logf(" h%s…", connectionHoldDuration);
      Thread.sleep(connectionHoldDuration);
      log("h");
    }
  }

  private static void pauseAfterConnecting() throws InterruptedException {
    if (delayBetweenConnectAndAccept > 0) {
      logf(" z%s…", delayBetweenConnectAndAccept);
      Thread.sleep(delayBetweenConnectAndAccept);
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

  private static void setAcceptTimeout(ServerSocket socket) throws SocketException {
    if (acceptTimeout > 0) {
      socket.setSoTimeout(acceptTimeout);
      log(" t" + acceptTimeout);
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

  private static void parseOptions(String[] args) throws UnknownHostException {
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "accept":
          accept = true;
          break;
        case "address":
          bindAddress = InetAddress.getByName(stringArg(args, ++i));
          break;
        case "connect":
          connect = true;
          accept = true;
          break;
        case "ep":
          nPorts = integerArg(args, ++i);
          bindPort = 0;
          break;
        case "help":
          usage();
          break;
        case "hold":
          connectionHoldDuration = integerArg(args, ++i);
          connect = true;
          break;
        case "host":
          bindAddress = InetAddress.getLocalHost();
          break;
        case "loopback":
          bindAddress = InetAddress.getLoopbackAddress();
          break;
        case "no-ra":
          reuseAddress = false;
          setReuseAddress = true;
          break;
        case "no-rp":
          reusePort = false;
          setReusePort = true;
          break;
        case "port":
          bindPort = integerArg(args, ++i);
          nPorts = 1;
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
          delayBetweenConnectAndAccept = integerArg(args, ++i);
          connect = true;
          break;
        case "timeout":
          acceptTimeout = integerArg(args, ++i);
          accept = true;
          break;
        default:
          System.out.format("Unrecognized arg %d %s%n", i, args[i]);
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

  private static String stringArg(String[] args, int i) {
    if (args.length < i) {
      usage();
    }
    return args[i];
  }

  private static void usage() {
    System.out.format("Usage: %s [options]%n", PortTool.class.getSimpleName());
    System.out.println("Options:");
    System.out.println("    accept       Accept after binding");
    System.out.println("    address addr Bind to the specified address");
    System.out.println("    connect      Connect to a client after binding"
        + " (enables accept)");
    System.out.println("    ep n         Bind to n ephemeral ports");
    System.out.println("    help         Display this help message");
    System.out.println("    hold t       Remain connected for t ms"
        + " (enables connect)");
    System.out.println("    host         Bind to the address of the local host");
    System.out.println("    loopback     Bind to the loopback address");
    System.out.println("    port p       Bind to the specified port p");
    System.out.println("    quiet        Do not print details for each reservation");
    System.out.println("    [no-]ra      Enable/Disable SO_REUSEADDR before binding");
    System.out.println("    [no-]rp      Enable/Disable SO_REUSEPORT before binding");
    System.out.println("    sleep t      Sleep for t ms between client connect and server accept"
        + " (enables connect)");
    System.out.println("    timeout t    Abandon accept after t ms"
        + " (enables accept)");
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
