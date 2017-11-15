/**
 * CS 380.01 - Computer Networks
 * Professor: NDavarpanah
 *
 * Project 5
 * UdpClient
 *
 * Justin Galloway
 */

import java.io.*;
import java.util.Random;
import java.net.*;

import javax.xml.bind.DatatypeConverter;

// UDPClient - Handles main class, all calculations and receiving.
public class UdpClient {
    public static void main(String[] args) throws Exception {
        try(Socket socket = new Socket("18.221.102.182", 38005)) {
            InetAddress ip = InetAddress.getByName("18.221.102.182");
            InputStream is = socket.getInputStream();
            OutputStream os = socket.getOutputStream();

            // Hardcode to "DEADBEEF"
            Ipv4 ipv4 = new Ipv4("DEADBEEF", ip.getAddress());
            byte[] packets = ipv4.makePackets();
            os.write(packets);

            byte[] fromServer = new byte[4];
            int bit;
            for(int i = 0; i < 4 ; i++) {
                bit = is.read();
                fromServer[i] = (byte)bit;
            }

            String handshake = DatatypeConverter.printHexBinary(fromServer);
            System.out.println("Handshake response: 0x" + handshake);
            int port = (is.read() << 8) ^ is.read();
            System.out.println("Port number received: " + port + "\n");

            int size = 2;
            int RTTTotal = 0;
            for(int j = 0; j < 12; j++){
                System.out.println("Sending packet with " + size + " bytes of data");
                UDP udp = new UDP(port, ip.getAddress(), size);
                byte[] udpArray = udp.makePackets();
                String udpHex = DatatypeConverter.printHexBinary(udpArray);

                Ipv4 udp_ipv4 = new Ipv4(udpHex, ip.getAddress());
                byte[] packetList = udp_ipv4.makePackets();
                os.write(packetList);

                // Start timer.
                long start = System.currentTimeMillis();

                byte[] serverRes = new byte[4];
                for(int k = 0; k < 4 ; k++) {
                    bit = is.read();
                    serverRes[k] = (byte)bit;
                }

                // End timer -> Calculate
                long stop = System.currentTimeMillis();
                long rtt =  stop - start;
                RTTTotal += rtt;

                // Look for serverCheck to = CAFEBABE
                String serverCheck = DatatypeConverter.printHexBinary(serverRes);
                System.out.println("Response: 0x" + serverCheck);
                System.out.println("Estimated RTT: " + rtt + "ms\n");
                size *= 2;
            }
            System.out.println("Average RTT: " + RTTTotal/12 + "ms");
        }
    }
}

// Ipv4 Class - Handles packets, checksum
class Ipv4 {
    String data;
    byte[] bytes;
    int size;

    // Initialize
    public Ipv4(String d, byte[] b) {
        data = d;
        bytes = b;
        size = data.length()/2;
    }

    // Simple checksum
    public short checkSum (int[] b){
        long sum = 0;
        for(int i = 0; i < b.length; i++) {
            sum += b[i];
            if((sum & 0xFFFF0000)!=0){
                sum &= 0xFFFF;
                sum ++;
            }
        }
        return (short)~(sum & 0xFFFF);
    }

    public byte[] makePackets(){
        int length = 20 + size;
        byte[] header = new byte[length];
        int version = 4;
        int header_len = 5;
        short flag = 2;
        int combine = (version << 4) ^ header_len;

        header[0] = (byte)combine;
        header[1] = 0;
        header[2] = (byte)((length >> 8) & 0xFF);
        header[3] = (byte)(length & 0xFF);
        header[4] = 0;
        header[5] = 0;
        header[6] = (byte)(flag << 5);
        header[7] = 0;
        header[8] = 50;
        header[9] = 17;
        header[10] = 0;
        header[11] = 0;
        header[12] = 0;
        header[13] = 0;
        header[14] = 0;
        header[15] = 0;

        int b = 0;
        for (int i = 16; i <= 19; i++) {
            Byte byteArray = bytes[b];
            int bTo_i = byteArray.intValue();
            header[i] = (byte)(bTo_i & 0xFF);
            b++;
        }

        int[] checkArray = new int[10];
        int count = 0;
        for(int j = 0; j <= 9; j++) {
            int left = header[count];
            int right = header[count+1];
            checkArray[j] = (((left & 0xFF) << 8) | (right & 0xFF));
            count = count + 2;
        }

        int check = checkSum(checkArray);
        header[10] = (byte)((check >> 8) & 0xFF);
        header[11] = (byte)(check & 0xFF);
        byte[] hexToBin = new byte[size];
        hexToBin = DatatypeConverter.parseHexBinary(data);
        int hold = 0;
        for(int k = 1; k <= size; k++) {
            header[19 + k] = hexToBin[hold];
            hold++;
        }
        return header;
    }
}

// UDP class - Handles UDP actions, checksum
class UDP {
    int port;
    byte[] bytes;
    int size;

    // Initialize...
    public UDP(int p, byte[] b, int s) {
        port = p;
        bytes = b;
        size = s;
    }

    // Simple Checksum...
    public short checkSum (int[] b){
        long sum = 0;
        for(int i = 0; i < b.length; i++) {
            sum += b[i];
            if((sum & 0xFFFF0000)!=0){
                sum &= 0xFFFF;
                sum ++;
            }
        }
        return (short)~(sum & 0xFFFF);
    }

    public byte[] makePackets() {
        byte[] header;
        int length = 8 + size;

        header = new byte[length];
        header[0] = 0;
        header[1] = 0;
        header[2] = (byte)((port >> 8) & 0xFF);
        header[3] = (byte)(port & 0xFF);
        header[4] = (byte)((length >> 8) & 0xFF);
        header[5] = (byte)(length & 0xFF);
        header[6] = 0;
        header[7] = 0;

        Random r = new Random();
        for(int i = 1; i <= size; i++) {
            header[7+i] = (byte)r.nextInt();
        }
        byte[] pseudo = new byte[12];
        pseudo[0] = 0;
        pseudo[1] = 17;
        pseudo[2] = 0;
        pseudo[3] = 0;
        pseudo[4] = 0;
        pseudo[5] = 0;

        int b = 0;
        for (int j = 6; j <= 9; j++) {
            Byte byteArray = bytes[b];
            int bTo_i = byteArray.intValue();
            pseudo[j] = (byte)(bTo_i & 0xFF);
            b++;
        }
        pseudo[10] = header[4];
        pseudo[11] = header[5];

        int count = 0;
        int[] array = new int[(length + 12) / 2];
        for(int k = 0; k < (length / 2); k++) {
            int first = header[count];
            int second = header[count + 1];
            array[k] = (((first & 0xFF) << 8) | (second & 0xFF));
            count = count + 2;
        }
        // Reset...
        count = 0;
        for(int l = length/2; l < array.length; l++) {
            int first = pseudo[count];
            int second = pseudo[count + 1];
            array[l] = (((first & 0xFF) << 8) | (second & 0xFF));
            count = count + 2;
        }
        int check = checkSum(array);
        header[6] = (byte)((check >> 8) & 0xFF);
        header[7] = (byte)(check & 0xFF);
        return header;
    }
}