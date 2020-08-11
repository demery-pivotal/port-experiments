package com.vmware.demery;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

public class BindRecentlyUsedPortWithREUSEADDRDisabled {
  public static void main(String[] args) throws IOException {
    int port = ephemeralPort();
    try (ServerSocket socket = new ServerSocket()) {
      socket.bind(new InetSocketAddress(port));
      System.out.format("Successfully bound recently bound port %d%n", port);
    }
  }

  private static int ephemeralPort() {
    try (ServerSocket socket = new ServerSocket()) {
      socket.bind(new InetSocketAddress(0));
      return socket.getLocalPort();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
