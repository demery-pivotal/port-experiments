package com.vmware.demery;

import static java.util.stream.Collectors.toSet;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.stream.IntStream;

public class EphemeralReuseRecentlyConnectedPorts {
  public static void main(String[] args) {
    int nPorts = 500;
    Set<Integer> uniquePortNumbers = IntStream.range(0, nPorts)
        .mapToObj(i -> reservePortByConnecting())
        .collect(toSet());

    int nDuplicates = nPorts - uniquePortNumbers.size();
    System.out.format("With SO_REUSEADDR enabled, system picked %d/%d recently bound ports%n",
        nDuplicates, nPorts);
  }

  private static int reservePortByConnecting() {
    try (ServerSocket server = new ServerSocket()) {
      server.bind(new InetSocketAddress(0));
      int port = server.getLocalPort();
      Socket clientConnection = new Socket(server.getInetAddress(), port);
      Socket serverConnection = server.accept();
      clientConnection.close();
      serverConnection.close();
      return port;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
