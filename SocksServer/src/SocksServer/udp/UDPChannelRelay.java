//  Author: Ronald Pablos
//  Year: 2014

package SocksServer.udp;


import SocksServer.tcp.ChannelRelay;
import java.io.IOException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;

/**
 *
 * @author Ronald
 */
public interface UDPChannelRelay {
    public DatagramChannel getInternalChannel();
    public DatagramChannel getExternalChannel();
    public ChannelRelayTransformation getTransformation();
    public UDPRelayServer getRelayManager();
    public void start() throws IOException;
    public void stop();
    public boolean isStopped();
    
    public void addChannelRelayListener(UDPChannelRelay.ChannelRelayListener listener);
    public void removeChannelRelayListener(UDPChannelRelay.ChannelRelayListener listener);

    public interface ChannelRelayListener {
        public void onEOF(UDPChannelRelay cr, SelectableChannel ClosingChannel);
        public void onIOException(UDPChannelRelay cr, IOException ex);
    }
    public interface ChannelRelayTransformation extends ChannelRelay.ChannelRelayTransformation {
        //public int transform(ByteBuffer src, ByteBuffer dst,boolean fromInternalChannel);
    }
}
