//  Author: Ronald Pablos
//  Year: 2014

package SocksServer.config;

import SocksServer.auth.UserPasswordAuthenticationMethod;
import java.net.InetSocketAddress;
import java.util.Map;

/**
 *
 * @author Ronald
 */
public class RuleSet {
    InetSocketAddress externalAddress;
    InetAddressRanges sources;
    InetAddressRanges destinations;
    Map<String,UserPasswordAuthenticationMethod.PasswordAndExternalAddress> users;

    public InetAddressRanges getSources() {
        return sources;
    }

    public Map<String, UserPasswordAuthenticationMethod.PasswordAndExternalAddress> getUsers() {
        return users;
    }

    public InetSocketAddress getExternalAddress() {
        return externalAddress;
    }
    
}
