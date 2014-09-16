//  Author: Ronald Pablos
//  Year: 2014

package SocksServer.auth;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 *
 * @author Ronald
 */
public interface AuthenticationMethod {
    public int getMethodId();
    public boolean AuthenticationNegotiation() throws IOException;
    public AuthenticationEncapsulation getAuthenticationEncapsulation();
    public AuthenticationEncapsulation getAuthenticationEncapsulationForUDP();
    public InetSocketAddress getLocalInetSocketAddressForConnect(InetSocketAddress remote);
    public InetSocketAddress getLocalInetSocketAddressForUDP();
}
