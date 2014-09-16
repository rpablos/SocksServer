//  Author: Ronald Pablos
//  Year: 2014

package SocksServer.auth;

import SocksServer.tcp.ChannelRelay;
import SocksServer.udp.UDPChannelRelay;

/**
 *
 * @author Ronald
 */
public interface AuthenticationEncapsulation extends ChannelRelay.ChannelRelayTransformation, UDPChannelRelay.ChannelRelayTransformation {
    
}
