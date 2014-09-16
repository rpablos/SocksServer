//  Author: Ronald Pablos
//  Year: 2014

package SocksServer.config;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author USUARIO
 */
public class InetAddressRanges {
    List<InetAddressRange> ranges = new LinkedList<InetAddressRange>();

    public void add(InetAddressRange range) {
        ranges.add(range);
    }
    public void remove(InetAddressRange range) {
        ranges.remove(range);
    }
    public boolean contains(InetAddress address) {
        byte[] addressBytes = address.getAddress();
        for (InetAddressRange range:ranges) {
            if (range.contains(addressBytes))
                    return true;
        }
        return false;
    }
    public boolean contains(InetSocketAddress address) {
        byte[] addressBytes = address.getAddress().getAddress();
        for (InetAddressRange range:ranges) {
            if (range.contains(addressBytes,address.getPort()))
                    return true;
        }
        return false;
    }
    
    
    public static class InetAddressRange {
        byte[] networkaddress;
        int mask;
        int lowerPort = 0;
        int upperPort = 0;
        public InetAddressRange(byte[] networkaddress, int mask) {
            this(networkaddress, mask, 0, 0);
        }
        public InetAddressRange(InetAddress networkaddress, int mask) {
            this(networkaddress.getAddress(),mask);
        }
        public InetAddressRange(byte[] networkaddress, int mask, int port) {
            this (networkaddress, mask, port, port);
        }
        public InetAddressRange(byte[] networkaddress, int mask, int lowerport, int upperport) {
            this.mask = Math.max(0,Math.min(mask,networkaddress.length*8));
            this.networkaddress = getNetworkAddress(networkaddress,mask);
            this.lowerPort = lowerport;
            this.upperPort = Math.max(lowerport, upperport);
        }
        boolean contains(byte[] address) {
            return contains(address, 0);
        }
        boolean contains(byte[] address,int port) {
            byte[] netAddress = getNetworkAddress(address,mask);
            return Arrays.equals(netAddress, networkaddress) && ((lowerPort == 0) || (port >=lowerPort && port <= upperPort));
        }
        private byte[] getNetworkAddress(byte[] address, int mask) {
            byte[] netaddress = address.clone();
            int maskoctets = mask/8;
            int maskRemainingBits = mask % 8;
            if (maskRemainingBits != 0) {
                netaddress[maskoctets] &= ~0 << (8-maskRemainingBits);
                maskoctets++;
            }
            for (int i = maskoctets; i < netaddress.length; i++)
                netaddress[i] = 0;
            return netaddress;
        }
        @Override
        public String toString() {
            try {
                return InetAddress.getByAddress(networkaddress).getHostAddress()+"/"+mask;
            } catch (UnknownHostException ex) {
                return Arrays.toString(networkaddress)+"/"+mask;
            }
        }
        static public InetAddressRange parse(String network) {
            StringTokenizer st = new StringTokenizer(network,"/");
            if (st.countTokens() != 2)
                throw new IllegalArgumentException();
            String addressStr = st.nextToken();
            String maskandportsStr = st.nextToken();
            try {
                InetAddress address = InetAddress.getByName(addressStr);
                StringTokenizer stmaskandports = new StringTokenizer(maskandportsStr,":");
                int mask = Integer.parseInt(stmaskandports.nextToken());
                int lowerport = 0, upperport = 0;
                if (stmaskandports.countTokens() > 0) {
                    StringTokenizer stports = new StringTokenizer(stmaskandports.nextToken(),"-");
                    lowerport = Integer.parseInt(stports.nextToken());
                    if (stports.hasMoreTokens())
                        upperport = Integer.parseInt(stports.nextToken());
                }

                return new InetAddressRange(address.getAddress(), mask,lowerport,upperport);
            } catch (UnknownHostException ex) {
                throw new IllegalArgumentException(ex);
            }
        }

        
    }
}
