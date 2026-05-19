package dev.adrian.goral.localhiveagent.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Optional;

public final class NetworkUtils {

    private NetworkUtils() {
    }

    public static String findPrimaryIpv4Address() {
        try {
            return findUsableIpv4Address()
                    .map(InetAddress::getHostAddress)
                    .orElse("127.0.0.1");
        } catch (SocketException exception) {
            return "127.0.0.1";
        }
    }

    private static Optional<InetAddress> findUsableIpv4Address() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

        return Collections.list(interfaces).stream()
                .filter(NetworkUtils::isUsableInterface)
                .sorted(Comparator.comparing(NetworkInterface::getName))
                .flatMap(networkInterface -> Collections.list(networkInterface.getInetAddresses()).stream())
                .filter(NetworkUtils::isUsableIpv4Address)
                .findFirst();
    }

    private static boolean isUsableInterface(NetworkInterface networkInterface) {
        try {
            return networkInterface.isUp()
                    && !networkInterface.isLoopback()
                    && !networkInterface.isVirtual();
        } catch (SocketException exception) {
            return false;
        }
    }

    private static boolean isUsableIpv4Address(InetAddress address) {
        return address instanceof Inet4Address
                && !address.isLoopbackAddress()
                && !address.isLinkLocalAddress();
    }
}