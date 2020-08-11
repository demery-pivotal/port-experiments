package com.vmware.demery;

import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

public class EphemeralReuseCurrentlyBoundPorts {
  public static void main(String[] args) {
    List<ServerSocket> sockets = new ArrayList<>();
    int nPorts = 500;
    Set<Integer> uniquePortNumbers = IntStream.range(0, nPorts)
        .mapToObj(i -> bindSocket())
        .filter(Objects::nonNull)
        .peek(sockets::add)
        .map(ServerSocket::getLocalPort)
        .collect(toSet());

    sockets.forEach(EphemeralReuseCurrentlyBoundPorts::close);

    int nDuplicates = sockets.size() - uniquePortNumbers.size();
    System.out.format("With SO_REUSEADDR enabled, system picked %d/%d currently bound ports%n",
        nDuplicates, nPorts);
  }

  private static void close(ServerSocket socket) {
    try {
      socket.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static ServerSocket bindSocket() {
    try {
      ServerSocket socket = new ServerSocket();
      socket.setReuseAddress(true);
      socket.bind(new InetSocketAddress(0));
      return socket;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }
}