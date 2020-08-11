package com.vmware.demery;

import static java.util.stream.Collectors.toSet;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Set;
import java.util.stream.IntStream;

public class EphemeralNoReuseRecentlyBoundPorts {
  public static void main(String[] args) {
    int nPorts = 500;
    Set<Integer> uniquePortNumbers = IntStream.range(0, nPorts)
        .mapToObj(i -> reservePort())
        .collect(toSet());

    int nDuplicates = nPorts - uniquePortNumbers.size();
    System.out.format("With SO_REUSEADDR disabled, system picked %d/%d recently bound ports%n",
        nDuplicates, nPorts);
  }

  private static int reservePort() {
    try (ServerSocket socket = new ServerSocket()) {
      socket.bind(new InetSocketAddress(0));
      return socket.getLocalPort();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
