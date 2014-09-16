//  Author: Ronald Pablos
//  Year: 2014

package SocksServer.tcp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;

/**
 *
 * @author Ronald
 */
public interface ChannelRelay {
    public SelectableChannel getChannel1();
    public SelectableChannel getChannel2();
    public ChannelRelayTransformation getTransformation();
    public SelectableChannelRelayManager getRelayManager();
    public void start() throws IOException;
    public void stop();
    public boolean isStopped();
    
    public void addChannelRelayListener(ChannelRelay.ChannelRelayListener listener);
    public void removeChannelRelayListener(ChannelRelay.ChannelRelayListener listener);

    public interface ChannelRelayListener {
        public void onEOF(ChannelRelay cr, SelectableChannel ClosingChannel);
        public void onIOException(ChannelRelay cr, SelectableChannel channel,IOException ex);
    }
    public interface ChannelRelayTransformation {
        public int transform(ByteBuffer src, ByteBuffer dst,boolean fromChannel1to2);
    }
}
