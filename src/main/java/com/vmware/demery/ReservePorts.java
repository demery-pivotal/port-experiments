package com.vmware.demery;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketOptions;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReservePorts {
  private static final int PERSISTENCE = 100;
  private static final int ACCEPT_TIMEOUT_MS = 100;

  public static void main(String[] args) throws IOException {
    if (args.length < 1) {
      throw new RuntimeException("too few arguments");
    }

    int nPorts = Integer.parseInt(args[0]);

    Set<Integer> uniquePortNumbers = new HashSet<>();
    List<Integer> duplicates = new ArrayList<>();

    for (int i = 0; i < nPorts; i++) {
      int port = reserve();
      if (uniquePortNumbers.contains(port)) {
        duplicates.add(port);
      }
      uniquePortNumbers.add(port);
    }

    System.out.format("Attempted %d reservations%n", nPorts);
    System.out.format("    %d ports were already reserved%n", duplicates.size());
    if (duplicates.size() != 0) {
      Collections.sort(duplicates);
      System.out.println(duplicates);
    }
  }

  /**
   * We "reserve" a port by binding a server socket to an ephemeral port, connecting a client to
   * it, then closing the connection. This leaves the connection {@code TIME_WAIT} state for an
   * OS-specified duration. While the connection is in {@code TIME_WAIT} state, its ports can be
   * bound only by sockets with {@link SocketOptions#SO_REUSEADDR} enabled.
   */
  public static int reserve() throws IOException {
    for (int i = 0; i < PERSISTENCE; i++) {
      try (ServerSocket socket = new ServerSocket()) {
        socket.setReuseAddress(false); // Disallow binding to a reserved port.
        socket.bind(new InetSocketAddress((InetAddress) null, 0));
        int port = socket.getLocalPort();
        try (Socket client = new Socket()) {
          client.connect(socket.getLocalSocketAddress(), ACCEPT_TIMEOUT_MS);
          socket.setSoTimeout(ACCEPT_TIMEOUT_MS);
          socket.accept();
          return port;
        } catch (SocketTimeoutException timeout) {
          // The server timed out accepting the connection. One reason this might happen is that the
          // system chose an ephemeral port that is not available to us. For example, macOS
          // sometimes chooses an ephemeral port that some other socket is listening on. To account
          // for this possibility, we continue the loop and try again with the next ephemeral port.
        }
      }
    }
    throw new RuntimeException("Could not get port in " + PERSISTENCE + "tries");
  }
}
