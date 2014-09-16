//  Author: Ronald Pablos
//  Year: 2014

package SocksServer.auth;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 *
 * @author Ronald
 */
public class AuthenticationMethodNull implements AuthenticationMethod {
    InetSocketAddress address;

    public AuthenticationMethodNull() {
        address = new InetSocketAddress(0);
    }
    public AuthenticationMethodNull(InetSocketAddress address) {
        this.address = address;
    }
    
    @Override
    public int getMethodId() {
        return 0;
    }
    //public static AuthenticationMethod instance = new AuthenticationMethodNull();

    @Override
    public boolean AuthenticationNegotiation() throws IOException {
        return true;
    }

    static private AuthenticationEncapsulation _authEncap = new AuthenticationEncapsulation() {

        @Override
        public int transform(ByteBuffer src, ByteBuffer dst,boolean b) {
                int count = Math.min(dst.remaining(),src.remaining());
                byte[] ba = new byte[count];
                src.get(ba);
                dst.put(ba);
                return count;
        }
    };
            
    @Override
    public AuthenticationEncapsulation getAuthenticationEncapsulation() {
        return _authEncap;
    }
    @Override
    public AuthenticationEncapsulation getAuthenticationEncapsulationForUDP() {
        return getAuthenticationEncapsulation();
    }
    
    @Override
    public InetSocketAddress getLocalInetSocketAddressForConnect(InetSocketAddress remote) {
        return address;
    }

    @Override
    public InetSocketAddress getLocalInetSocketAddressForUDP() {
        return address;
    }

    
}
