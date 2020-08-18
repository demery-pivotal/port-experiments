package com.vmware.demery;

import static java.net.StandardSocketOptions.SO_REUSEADDR;
import static java.net.StandardSocketOptions.SO_REUSEPORT;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PortTool {
  private static boolean accept = false;
  private static int acceptTimeout = 0;
  private static InetAddress bindAddress = null;
  private static int bindPort = 0;
  private static boolean connect = false;
  private static long connectionHoldDuration = 0;
  private static int delayBetweenConnectAndAccept = 0;
  private static boolean log = true;
  private static int nPorts = 1;
  private static boolean reuseAddress = false;
  private static boolean reusePort = false;
  private static boolean setReuseAddress = false;
  private static boolean setReusePort = false;
  private static final List<Integer> unacceptablePorts = new ArrayList<>();

  public static void main(String[] args) throws IOException, InterruptedException {
    parseOptions(args);

    Set<Integer> uniquePortNumbers = new HashSet<>();
    List<Integer> duplicates = new ArrayList<>();

    for (int i = 0; i < nPorts; i++) {
      logf("%8d", i + 1);
      int port = reserve();
      if (uniquePortNumbers.contains(port)) {
        log(" DUPLICATE");
        duplicates.add(port);
      }
      uniquePortNumbers.add(port);
      log("\n");
    }

    int exitCode = 0;
    if (nPorts > 1) {
      if (duplicates.size() != 0) {
        Collections.sort(duplicates);
        System.err.format("%d duplicate ephemeral ports: %s%n", duplicates.size(), duplicates);
        exitCode = 1;
      }
    }
    if (accept) {
      if (unacceptablePorts.size() != 0) {
        Collections.sort(unacceptablePorts);
        System.err.format("%d ports failed to accept: %s%n", unacceptablePorts.size(), unacceptablePorts);
        exitCode = 1;
      }
    }
    System.exit(exitCode);
  }

  public static int reserve() throws IOException, InterruptedException {
    try (ServerSocket socket = new ServerSocket()) {
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
    InetSocketAddress socketAddress = new InetSocketAddress(bindAddress, bindPort);
    logf(" B%s%s…", socketAddress, userOptionFlags());
    setReuseAddress(socket);
    setReusePort(socket);
    socket.bind(socketAddress);
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
    socket.setSoTimeout(acceptTimeout);
    logf(" A%d…", acceptTimeout);
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
      socket.setOption(SO_REUSEADDR, reuseAddress);
    }
  }

  private static void setReusePort(ServerSocket socket) throws IOException {
    if (setReusePort) {
      socket.setOption(SO_REUSEPORT, reusePort);
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
        case "help":
          usage(0);
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
        case "pick":
          nPorts = integerArg(args, ++i);
          bindPort = 0;
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
        case "wildcard":
          bindAddress = null;
          break;
        default:
          System.out.format("Unrecognized arg %d %s%n", i, args[i]);
          usage(1);
      }
    }
  }

  private static int integerArg(String[] args, int i) {
    if (args.length < i) {
      usage(1);
    }
    return Integer.parseInt(args[i]);
  }

  private static String stringArg(String[] args, int i) {
    if (args.length < i) {
      usage(1);
    }
    return args[i];
  }

  private static void usage(int exitCode) {
    System.out.format("Usage: %s [options]%n", PortTool.class.getSimpleName());
    System.out.println();
    System.out.println("\tAddress options (default: wildcard)");
    System.out.println();
    System.out.println("\t\taddress name Bind to the named address");
    System.out.println("\t\thost         Bind to the address of the local host");
    System.out.println("\t\tloopback     Bind to the loopback address");
    System.out.println("\t\twildcard     Bind to the wildcard address");
    System.out.println();
    System.out.println("\tBind options (default: pick 1)");
    System.out.println();
    System.out.println("\t\tpick n       Bind to n ephemeral ports");
    System.out.println("\t\tport p       Bind to port p");
    System.out.println("\t\t[no-]ra      Enable/Disable SO_REUSEADDR before binding");
    System.out.println("\t\t[no-]rp      Enable/Disable SO_REUSEPORT before binding");
    System.out.println();
    System.out.println("\tConnect options (default: none)");
    System.out.println();
    System.out.println("\t\taccept       Accept connections");
    System.out.println("\t\tconnect      Connect to a client after binding"
        + " (enables accept)");
    System.out.println("\t\thold t       Remain connected for t ms"
        + " (enables connect)");
    System.out.println("\t\tsleep t      Sleep for t ms between client connect and server accept"
        + " (enables connect)");
    System.out.println("\t\ttimeout t    Abandon accept after t ms"
        + " (enables accept)");
    System.out.println();
    System.out.println("\tOther options (default: none)");
    System.out.println();
    System.out.println("\t\thelp         Display this help message");
    System.out.println("\t\tquiet        Do not print details for each reservation");
    System.exit(exitCode);
  }

  private static String userOptionFlags() {
    String raFlag = setReuseAddress ? optionFlag(reuseAddress) : "?";
    String rpFlag = setReusePort ? optionFlag(reusePort) : "?";
    return String.format("[%sRA%sRP]", raFlag, rpFlag);
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
