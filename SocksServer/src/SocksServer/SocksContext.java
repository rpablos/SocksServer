//  Author: Ronald Pablos
//  Year: 2014

package SocksServer;

import SocksServer.auth.AuthenticationEncapsulation;
import SocksServer.auth.AuthenticationMethod;
import SocksServer.tcp.SelectableChannelRelayManager;
import SocksServer.udp.UDPRelayServer;
import java.nio.channels.SocketChannel;
import java.util.List;

/**
 *
 * @author Ronald
 */
public class SocksContext {
    public SocketChannel clientSocketChannel;
    public SocketChannel remoteSocketChannel;
    public SelectableChannelRelayManager scrm;
    public UDPRelayServer urs;
    public AuthenticationMethod am;
    public AuthenticationEncapsulation ae;
    public AuthenticationEncapsulation udpae;
    
    public List<AuthenticationMethod> methods;
}
