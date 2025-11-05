package pt.isec.common.messages;
import java.net.InetAddress;

public record UdpMessage(InetAddress addr, int port, byte[] data, int length) { }
