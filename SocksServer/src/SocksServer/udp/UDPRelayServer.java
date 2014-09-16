//  Author: Ronald Pablos
//  Year: 2014

package SocksServer.udp;



import SocksServer.SocksException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Ronald
 */
public class UDPRelayServer {
    final int BUFFER_SIZE = 64*1024;
    final List<UDPChannelRelay> relays = new ArrayList<UDPChannelRelay>();
    Thread channelRelayManagerThread = null;
    UDPChannelRelayManagerTask channelRelayManagerRunnable = null;
    Selector selector;
    final Object selectorlock = new Object();
    
    public UDPRelayServer() throws IOException {
        selector = Selector.open();
    }
    public void close() throws IOException{
        if (channelRelayManagerRunnable != null)
            channelRelayManagerRunnable.terminate();
        selector.close();
    }
    
    public UDPChannelRelay createChannelRelay(SocketChannel sessionSocket,DatagramChannel sc1, DatagramChannel sc2) {
        return createChannelRelay(sessionSocket,sc1, sc2,nullTransformation);
    }
    public UDPChannelRelay createChannelRelay(SocketChannel sessionSocket,DatagramChannel sc1, DatagramChannel sc2,UDPChannelRelay.ChannelRelayTransformation transformation) {
        UDPRelayServer.UDPChannelRelay_impl result = new UDPRelayServer.UDPChannelRelay_impl(sessionSocket,sc1, sc2,transformation);
        synchronized(relays){
            relays.add(result);
        }
        return result;
    }
    public void deleteChannelRelay(UDPChannelRelay channelrelay){
        
        synchronized(relays){
            relays.remove(channelrelay);
            if (relays.isEmpty() && channelRelayManagerThread.isAlive()){ 
                channelRelayManagerThread = null;
                channelRelayManagerRunnable.terminate();
            }
            
        }
    }
    public List<UDPChannelRelay> getChannelRelays(){
        return Collections.unmodifiableList(relays);
    }
    private UDPChannelRelay.ChannelRelayTransformation nullTransformation = new NullTransformation();
    private class NullTransformation implements UDPChannelRelay.ChannelRelayTransformation{
        @Override
        public int transform(ByteBuffer src, ByteBuffer dst,boolean b) {
            int count = Math.min(dst.remaining(),src.remaining());
            byte[] ba = new byte[count];
            src.get(ba);
            dst.put(ba);
            return count;
        }
    }
    
    // UDPChannelRelay_impl
    private class UDPChannelRelay_impl implements UDPChannelRelay {
        DatagramChannel sc1;
        DatagramChannel sc2;
        SelectionKey sc1key;
        SelectionKey sc2key;
        SocketChannel sessionSocket;
        boolean stopped = true;
        UDPRelayServer.RelayState attachment12;
        UDPRelayServer.RelayState attachment21;
        UDPChannelRelay.ChannelRelayTransformation transformation;
        
        
        public UDPChannelRelay_impl(SocketChannel sessionSocket,DatagramChannel sc1, DatagramChannel sc2,UDPChannelRelay.ChannelRelayTransformation transformation) {
            this.sessionSocket = sessionSocket;
            this.sc1 = sc1;
            this.sc2 = sc2;
            ByteBuffer bufferread1 = ByteBuffer.allocate(BUFFER_SIZE);
            ByteBuffer bufferread2 = ByteBuffer.allocate(BUFFER_SIZE);
            ByteBuffer bufferwrite1 = ByteBuffer.allocate(BUFFER_SIZE);
            ByteBuffer bufferwrite2 = ByteBuffer.allocate(BUFFER_SIZE);
            attachment12 = new UDPRelayServer.RelayState(sc1,sc2,this,bufferread1,bufferread2,bufferwrite1,bufferwrite2);
            attachment21 = new UDPRelayServer.RelayState(sc2,sc1,this,bufferread2,bufferread1,bufferwrite2,bufferwrite1);
            this.transformation = transformation;
        }

        @Override
        public DatagramChannel getInternalChannel() {
            return sc1;
        }

        @Override
        public DatagramChannel getExternalChannel() {
            return sc2;
        }
        public SocketChannel getSessionSocketChannel() {
            return sessionSocket;
        }
        @Override
        public UDPChannelRelay.ChannelRelayTransformation getTransformation() {
            return transformation;
        }
        @Override
        public UDPRelayServer getRelayManager() {
            return UDPRelayServer.this;
        }
                
        @Override
        public void start() throws IOException {
            synchronized (selectorlock) {
                selector.wakeup();
                sessionSocket.register(selector, sessionSocket.validOps() & SelectionKey.OP_READ,attachment12);
                sc1key = sc1.register(selector, sc1.validOps() & SelectionKey.OP_READ, attachment12);
                sc2key = sc2.register(selector, sc2.validOps() & SelectionKey.OP_READ, attachment21);
                System.out.println("Registro. Memoria libre: "+Runtime.getRuntime().freeMemory());
                
            }
            stopped = false;
            synchronized(relays){
                if (channelRelayManagerThread == null || !channelRelayManagerThread.isAlive()){
                    channelRelayManagerRunnable = new UDPRelayServer.UDPChannelRelayManagerTask();
                    channelRelayManagerThread = new Thread(channelRelayManagerRunnable);
                    channelRelayManagerThread.start();
                    System.out.println("Comienzo del thread principal del UDP relay server: "+channelRelayManagerThread.getName());
                }
            }
        }

        @Override
        public void stop() {
            stopped = true;
            synchronized (selectorlock) {
                sc1key.cancel();
                sc2key.cancel();
            }
        }
        public boolean isStopped() {
            return stopped;
        }
        Set<UDPChannelRelay.ChannelRelayListener> listeners = null;
        @Override
        public void addChannelRelayListener(UDPChannelRelay.ChannelRelayListener listener) {
            if (listener == null)
                return;
            if (listeners == null)
                listeners = new LinkedHashSet<UDPChannelRelay.ChannelRelayListener>();
            listeners.add(listener);
        }

        @Override
        public void removeChannelRelayListener(UDPChannelRelay.ChannelRelayListener listener) {
            if (listeners != null)
                listeners.remove(listener);
        }

        void notifyEOFListeners(SelectableChannel closingChannel) {
            if (listeners != null) {
                for (UDPChannelRelay.ChannelRelayListener listener: listeners) {
                    listener.onEOF(this,closingChannel);
                }
            }
        }
        void notifyIOExceptionListeners(IOException ex) {
            if (listeners != null) {
                for (UDPChannelRelay.ChannelRelayListener listener: listeners) {
                    listener.onIOException(this,ex);
                }
            }
        }
    }
    // relay state
    private class RelayState {
        SelectableChannel sc1, sc2;
        UDPChannelRelay cr;
        ByteBuffer readbuffer;
        ByteBuffer peerreadbuffer;
        ByteBuffer writebuffer, peerwritebuffer;

        public RelayState(SelectableChannel sc1, SelectableChannel sc2,UDPChannelRelay cr,
                ByteBuffer readbuffer, ByteBuffer peerreadbuffer,
                ByteBuffer writebuffer, ByteBuffer peerwritebuffer) {
            this.sc1 = sc1;
            this.sc2 = sc2;
            this.cr = cr;
            this.readbuffer = readbuffer;
            this.peerreadbuffer = peerreadbuffer;
            this.writebuffer = writebuffer;
            this.peerwritebuffer = peerwritebuffer;
        }
        ByteBuffer getPeerReadBuffer() {
            return peerreadbuffer;
        }
        ByteBuffer getReadBuffer() {
            return readbuffer;
        }
        ByteBuffer getWriteBuffer() {
            return writebuffer;
        }
        ByteBuffer getPeerWriteBuffer() {
            return peerwritebuffer;
        }
        SelectableChannel getChannel1() {
            return sc1;
        }
        SelectableChannel getChannel2() {
            return sc2;
        }
        UDPChannelRelay getChannelRelay() {
            return cr;
        }
    }
    // main thread for select
    private class UDPChannelRelayManagerTask implements Runnable {
        boolean fin = false;
        @Override
        public void run() {
            try {
                while (!fin) {
                    int numkeys = selector.select();
                    synchronized (selectorlock) {
                        if (numkeys == 0)
                            continue;
                        Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                        while (iterator.hasNext()) {
                            SelectionKey key = iterator.next();
                            iterator.remove();
                            if (!key.isValid())
                                continue;
                            try {
                                if (key.isReadable()) {
                                    RelayState state = (RelayState) key.attachment();
                                    UDPChannelRelay_impl cr = (UDPChannelRelay_impl) state.getChannelRelay();
                                    if (key.channel() == cr.getSessionSocketChannel()) {
                                        SocketChannel sc = (SocketChannel) key.channel();
                                        ByteBuffer bb = ByteBuffer.allocate(1024);
                                        int count = sc.read(bb);
                                        if (count < 0){ //EOF-->cierra el canal y notifica
                                            sc.close();
                                            cr.getInternalChannel().close();
                                            cr.getExternalChannel().close();
                                            //informar por listener
                                            cr.notifyEOFListeners(key.channel());
                                        }
                                    } else {
                                        DatagramChannel dc = (DatagramChannel) key.channel();
                                        ByteBuffer buffer = state.getReadBuffer();
                                        SocketAddress receiveAddress = dc.receive(buffer);
                                        boolean drop = false;
                                        if (cr.getInternalChannel() == dc) {
                                            if (!dc.isConnected())
                                                dc.connect(receiveAddress);

                                        } else { // external channel
                                            if (!cr.getInternalChannel().isConnected()) 
                                                drop = true;
                                            else 
                                                addUDPencapsulation(buffer,receiveAddress);
                                        }
                                        if (!drop) {
                                            ByteBuffer writeBuffer = state.getPeerWriteBuffer();
                                            buffer.flip();
                                            cr.getTransformation().transform(buffer, writeBuffer,cr.getInternalChannel()==dc);
                                            buffer.compact();
                                            InetSocketAddress toaddress = null;
                                            if (cr.getInternalChannel() == dc) {
                                                toaddress = removeUDPencapsulation(writeBuffer);
                                            } else {
                                                toaddress = (InetSocketAddress) ((DatagramChannel)cr.getInternalChannel()).getRemoteAddress();
                                            }
                                            
                                            if (toaddress != null) {
                                                writeBuffer.flip();
                                                if (toaddress.isUnresolved()) {
                                                    byte[] data = new byte[writeBuffer.limit()];
                                                    writeBuffer.get(data);
                                                    sendUDPdomainexecutor.execute(new sendDomainNameDatagram(cr,toaddress, (DatagramChannel)state.getChannel2(), data));
                                                } else
                                                    ((DatagramChannel)state.getChannel2()).send(writeBuffer, toaddress);
                                            }
                                            writeBuffer.clear();
                                        }
                                    }
                                }
                            }catch (IOException ex) {
                                UDPRelayServer.RelayState state = (UDPRelayServer.RelayState) key.attachment();
                                UDPRelayServer.UDPChannelRelay_impl cr = (UDPRelayServer.UDPChannelRelay_impl) state.getChannelRelay();
                                cr.notifyIOExceptionListeners(ex);
                                cr.getInternalChannel().close();
                                cr.getExternalChannel().close();
                                cr.getSessionSocketChannel().close();

                            } catch (SocksException ex) {
                                Logger.getLogger(UDPRelayServer.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    }
                }
            } catch (IOException ex) {
                Logger.getLogger(UDPRelayServer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        public void terminate() {
            fin = true;
            selector.wakeup();
        }
        
        private InetSocketAddress removeUDPencapsulation(ByteBuffer buffer) throws SocksException, IOException {
            buffer.flip();
            buffer.getShort(); //reserved
            buffer.get(); //FRAG
            byte addressType = buffer.get();
            byte[] addressBytes = null;
            switch (addressType) {
                case 1:
                    addressBytes = new byte[4];
                    break;
                case 3:
                    addressBytes = new byte[buffer.get() & 0xFF];
                    break;
                case 4:
                    addressBytes = new byte[16];
                    break;
                default:
                    throw new SocksException();
                
            }
            buffer.get(addressBytes);
            int port = buffer.getShort() & 0xffff;
            buffer.compact();
            try {
                InetSocketAddress result = (addressType == 3)?InetSocketAddress.createUnresolved(new String(addressBytes,"iso-8859-1"),port)
                        :new InetSocketAddress(InetAddress.getByAddress(addressBytes), port);
                return result;
            } catch (UnknownHostException ex) {
                System.out.println(ex);
            }
            return null;
        }
        private void addUDPencapsulation(ByteBuffer buffer, SocketAddress address) {
            InetSocketAddress iaddress = (InetSocketAddress) address;
            byte[] addressBytes = iaddress.getAddress().getAddress();
            byte[] array = buffer.array();
            System.arraycopy(array, 0, array, 6+addressBytes.length, buffer.position());
            array[0] = 0;
            array[1] = 0;
            array[2] = 0;
            array[3] = (byte) ((addressBytes.length == 4)?1:4);
            System.arraycopy(addressBytes, 0, array, 4, addressBytes.length);
            array[4+addressBytes.length] = (byte) ((iaddress.getPort() >>> 8) & 0xff);
            array[5+addressBytes.length] = (byte) (iaddress.getPort() & 0xff);
            buffer.position(buffer.position()+6+addressBytes.length);
        }
        
        ExecutorService sendUDPdomainexecutor = Executors.newFixedThreadPool(10);
        private class sendDomainNameDatagram implements Runnable {
            InetSocketAddress address;
            DatagramChannel dc;
            byte[] data;
            UDPChannelRelay_impl cr;
            public sendDomainNameDatagram(UDPChannelRelay_impl cr,InetSocketAddress address, DatagramChannel dc,byte[] data) {
                this.address = address;
                this.dc = dc;
                this.data = data;
                this.cr = cr;
            }
            
            @Override
            public void run() {
                try {
                    InetSocketAddress iaddress = new InetSocketAddress(
                            InetAddress.getByName(address.getHostName()),address.getPort());
                    dc.send(ByteBuffer.wrap(data), iaddress);
                    
                } catch (UnknownHostException ex) {
                    Logger.getLogger(UDPRelayServer.class.getName()).log(Level.INFO, address.getHostName(), ex);
                } catch (IOException ex) {
                        Logger.getLogger(UDPRelayServer.class.getName()).log(Level.SEVERE, null, ex);
                        cr.notifyIOExceptionListeners(ex);
                    try {
                        cr.getInternalChannel().close();
                        cr.getExternalChannel().close();
                        cr.getSessionSocketChannel().close();
                    } catch (IOException ex1) {
                        Logger.getLogger(UDPRelayServer.class.getName()).log(Level.SEVERE, null, ex1);
                    }
                }
                
            }
        }
    }
}
