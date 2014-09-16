//  Author: Ronald Pablos
//  Year: 2014

package SocksServer.tcp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Ronald
 */
public class SelectableChannelRelayManager {
    static final Logger logger = Logger.getLogger(SelectableChannelRelayManager.class.getName());
    
    final int BUFFER_SIZE = 64*1024;
    final List<ChannelRelay> relays = new ArrayList<ChannelRelay>();
    Thread channelRelayManagerThread = null;
    ChannelRelayManagerTask channelRelayManagerRunnable = null;
    Selector selector;
    final Object selectorlock = new Object();

    public SelectableChannelRelayManager() throws IOException {
        selector = Selector.open();
    }
    public void close() throws IOException{
        if (channelRelayManagerRunnable != null)
            channelRelayManagerRunnable.terminate();
        selector.close();
    }
    public ChannelRelay createChannelRelay(SelectableChannel sc1, SelectableChannel sc2) {
        return createChannelRelay(sc1, sc2,nullTransformation);
    }
    public ChannelRelay createChannelRelay(SelectableChannel sc1, SelectableChannel sc2,ChannelRelay.ChannelRelayTransformation transformation) {
        ChannelRelay_impl result = new ChannelRelay_impl(sc1, sc2,transformation);
        synchronized(relays){
            relays.add(result);
        }
        return result;
    }
    public void deleteChannelRelay(ChannelRelay channelrelay){
        synchronized(relays){
            relays.remove(channelrelay);
            if (relays.isEmpty() && channelRelayManagerThread.isAlive()){ 
                channelRelayManagerThread = null;
                channelRelayManagerRunnable.terminate();
            }
            
        }
    }
    public List<ChannelRelay> getChannelRelays(){
        return Collections.unmodifiableList(relays);
    }
    private ChannelRelay.ChannelRelayTransformation nullTransformation = new NullTransformation();
    private class NullTransformation implements ChannelRelay.ChannelRelayTransformation{
        @Override
        public int transform(ByteBuffer src, ByteBuffer dst,boolean b) {
            int count = Math.min(dst.remaining(),src.remaining());
            byte[] ba = new byte[count];
            src.get(ba);
            dst.put(ba);
            return count;
        }
    }
    private class ChannelRelay_impl implements ChannelRelay {
        SelectableChannel sc1;
        SelectableChannel sc2;
        SelectionKey sc1key;
        SelectionKey sc2key;
        boolean stopped = true;
        RelayState attachment12;
        RelayState attachment21;
        ChannelRelay.ChannelRelayTransformation transformation;
        
        
        public ChannelRelay_impl(SelectableChannel sc1, SelectableChannel sc2,ChannelRelay.ChannelRelayTransformation transformation) {
            this.sc1 = sc1;
            this.sc2 = sc2;
            ByteBuffer bufferread1 = ByteBuffer.allocate(BUFFER_SIZE);
            ByteBuffer bufferread2 = ByteBuffer.allocate(BUFFER_SIZE);
            ByteBuffer bufferwrite1 = ByteBuffer.allocate(BUFFER_SIZE);
            ByteBuffer bufferwrite2 = ByteBuffer.allocate(BUFFER_SIZE);
            attachment12 = new RelayState(sc1,sc2,this,bufferread1,bufferread2,bufferwrite1,bufferwrite2);
            attachment21 = new RelayState(sc2,sc1,this,bufferread2,bufferread1,bufferwrite2,bufferwrite1);
            this.transformation = transformation;
        }

        @Override
        public SelectableChannel getChannel1() {
            return sc1;
        }

        @Override
        public SelectableChannel getChannel2() {
            return sc2;
        }
        @Override
        public ChannelRelay.ChannelRelayTransformation getTransformation() {
            return transformation;
        }
        @Override
        public SelectableChannelRelayManager getRelayManager() {
            return SelectableChannelRelayManager.this;
        }
                
        @Override
        public void start() throws IOException {
            synchronized (selectorlock) {
                selector.wakeup();
                sc1key = sc1.register(selector, sc1.validOps() & SelectionKey.OP_READ, attachment12);
                sc2key = sc2.register(selector, sc2.validOps() & SelectionKey.OP_READ, attachment21);
                //System.out.println("Registro. Memoria libre: "+Runtime.getRuntime().freeMemory());
                
            }
            stopped = false;
            synchronized (relays) {
                if (channelRelayManagerThread == null || !channelRelayManagerThread.isAlive()){
                    channelRelayManagerRunnable = new ChannelRelayManagerTask();
                    channelRelayManagerThread = new Thread(channelRelayManagerRunnable);
                    channelRelayManagerThread.start();
                    //System.out.println("Comienzo del thread principal del Manager: "+channelRelayManagerThread.getName());
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
        Set<ChannelRelay.ChannelRelayListener> listeners = null;
        @Override
        public void addChannelRelayListener(ChannelRelay.ChannelRelayListener listener) {
            if (listener == null)
                return;
            if (listeners == null)
                listeners = new LinkedHashSet<ChannelRelay.ChannelRelayListener>();
            listeners.add(listener);
        }

        @Override
        public void removeChannelRelayListener(ChannelRelay.ChannelRelayListener listener) {
            if (listeners != null)
                listeners.remove(listener);
        }

        void notifyEOFListeners(SelectableChannel closingChannel) {
            if (listeners != null) {
                for (ChannelRelay.ChannelRelayListener listener: listeners) {
                    listener.onEOF(this,closingChannel);
                }
            }
        }
        void notifyIOExceptionListeners(IOException ex,SelectableChannel channel) {
            if (listeners != null) {
                for (ChannelRelay.ChannelRelayListener listener: listeners) {
                    listener.onIOException(this,channel,ex);
                }
            }
        }
    }
    
    private class RelayState {
        SelectableChannel sc1, sc2;
        ChannelRelay cr;
        ByteBuffer readbuffer;
        ByteBuffer peerreadbuffer;
        ByteBuffer writebuffer, peerwritebuffer;
        public RelayState(SelectableChannel sc1, SelectableChannel sc2,ChannelRelay cr,
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
        ChannelRelay getChannelRelay() {
            return cr;
        }
    }
    private class ChannelRelayManagerTask implements Runnable {
        boolean fin = false;
        @Override
        public void run() {
            try {
                while (!fin) {
                    int numkeys = selector.select();
                    synchronized (selectorlock) {
                        try {
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
                                        ReadableByteChannel sc = (ReadableByteChannel) key.channel();
                                        RelayState state = (RelayState) key.attachment();
                                        ByteBuffer buffer = state.getReadBuffer();
                                        int count=sc.read(buffer);
                                        //System.out.println("Read: "+count);
                                        if (count < 0){ //EOF
                                            //informar por listener
                                            ChannelRelay_impl cr = (ChannelRelay_impl) state.getChannelRelay();
                                            cr.notifyEOFListeners(key.channel());
                                            //-->cierra el canal y tambien el peer
                                            key.channel().close();
                                            state.getChannel2().close();

                                        } else if (count > 0) {
                                            //no leer hasta escribir
                                            key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
                                            //transformar el buffer de lectura en el de escritura
                                            ByteBuffer writeBuffer = state.getPeerWriteBuffer();
                                            buffer.flip();
                                            ChannelRelay_impl cr = (ChannelRelay_impl) state.getChannelRelay();
                                            cr.getTransformation().transform(buffer, writeBuffer,cr.getChannel1()==sc);
                                            buffer.compact();
                                            // activar escritura en peer
                                            SelectionKey sKey = state.getChannel2().keyFor(selector);
                                            sKey.interestOps(sKey.interestOps() | SelectionKey.OP_WRITE);
                                        }
                                    }
                                    if (key.isValid() && key.isWritable()){
                                        WritableByteChannel sc = (WritableByteChannel) key.channel();
                                        RelayState state = (RelayState) key.attachment();
                                        ByteBuffer buffer = state.getWriteBuffer();
                                        buffer.flip();
                                        int count;
                                        do {
                                              count = sc.write(buffer);
                                        } while ((count >0) && (buffer.remaining() > 0));
    //                                    System.out.println("Write: "+buffer.position());
                                        if (buffer.remaining() == 0) {
                                            //todo escrito
                                            buffer.clear();
                                            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                                            // activar lectura en peer
                                            SelectionKey sKey = state.getChannel2().keyFor(selector);
                                            sKey.interestOps(sKey.interestOps() | SelectionKey.OP_READ);
                                        } else {
                                            // Nada: sigue con OP_WRITE hasta que se termine
                                        }
                                    }
                                }catch (IOException ex) {
                                    RelayState state = (RelayState) key.attachment();
                                    ChannelRelay_impl cr = (ChannelRelay_impl) state.getChannelRelay();
                                    cr.notifyIOExceptionListeners(ex,key.channel());
                                    cr.getChannel1().close();
                                    cr.getChannel2().close();
                                }
                            }
                        } catch (ConcurrentModificationException ex) {
                            // no debería, pero continua, en el futuro ira bien
                            //System.out.println("----------------------"+ex);
                            logger.log(Level.SEVERE, "Error in TCP relay", ex);
                        }
                    }
                }
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error in TCP relay", ex);
            }
        }
        public void terminate() {
            fin = true;
            selector.wakeup();
        }
    }
}
