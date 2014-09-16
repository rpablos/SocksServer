//  Author: Ronald Pablos
//  Year: 2014

package SocksServer;


import SocksServer.auth.AuthenticationEncapsulation;
import SocksServer.auth.AuthenticationMethod;
import SocksServer.tcp.ChannelRelay;
import SocksServer.udp.UDPChannelRelay;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Ronald
 */
public class SocksSession implements Runnable {
    static final Logger logger = Logger.getLogger(SocksSession.class.getName());
    
    static Charset charset = Charset.forName("iso-8859-1");
    ByteBuffer readbuffer = ByteBuffer.allocate(1024);
    ByteBuffer readclearbuffer = ByteBuffer.allocate(1024);
    ByteBuffer writebuffer = ByteBuffer.allocate(1024);
    SocketChannel clientSocket;
    SocksContext context;
    
    public SocksSession(SocksContext context) {
        this.clientSocket = context.clientSocketChannel;
        this.context = context;
    }
    public void start() {
        new Thread(this).start();
    }
    
    @Override
    public void run() {
        try {
            Socksv5Message.readFully(clientSocket, readbuffer, 1);
            int version = readbuffer.get(0);
            if (version == 5) 
                handleSocksv5session();
            return;
        } catch (IOException ex) {
            Logger.getLogger(SocksSession.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SocksException ex) {
            Logger.getLogger(SocksSession.class.getName()).log(Level.SEVERE, null, ex);
        } 
        try {
            clientSocket.close();
        } catch (IOException ex) {
            Logger.getLogger(SocksSession.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
    
    
    
    private static void writeFully(WritableByteChannel ch,ByteBuffer bb, int length) throws IOException{
        if (bb.remaining() < length)
            throw new BufferUnderflowException();
        ByteBuffer bb2 = bb.slice();
        bb2.limit(length);
        while (bb2.remaining() > 0) {
            ch.write(bb2);
            
        }
    }
    
    private void write(byte[] ba) throws IOException {
        writebuffer.clear();
        writebuffer.put(ba);
        writebuffer.flip();
        readclearbuffer.clear();
        if (context.ae != null)
            context.ae.transform(writebuffer, readclearbuffer, false);
        else
            readclearbuffer.put(writebuffer);
        readclearbuffer.flip();
        writeFully(clientSocket,readclearbuffer,readclearbuffer.remaining());
    }
    private byte[] readMethods() throws IOException {
        readbuffer.mark();
        Socksv5Message.readFully(clientSocket, readbuffer, 1);
        readbuffer.reset();
        int nmethods = readbuffer.get() & 0xff;
        readbuffer.mark();
        Socksv5Message.readFully(clientSocket, readbuffer, nmethods);
        readbuffer.reset();
        byte[] methods = new byte[nmethods];
        readbuffer.get(methods);
        return methods;
    }
    private void handleSocksv5session() throws IOException, SocksException {
        byte[] methods = readMethods();
        AuthenticationMethod am = getAuthenticationMethod(methods);
        byte[] methodResponse = new byte[2];
        methodResponse[0] = 5;
        methodResponse[1] = (am == null)?(byte)0xFF:(byte)am.getMethodId();
        write(methodResponse);
        if ((am == null) || (!am.AuthenticationNegotiation())) {
            Logger.getLogger(SocksSession.class.getName()).info("Authentication failed "+context.clientSocketChannel);
            context.clientSocketChannel.close();
            return;
        }
        context.am = am;
        AuthenticationEncapsulation ae = am.getAuthenticationEncapsulation();
        context.ae = ae;
        readbuffer.clear();
        writebuffer.clear();
        readclearbuffer.clear();
        try {
            Socksv5Message msg = Socksv5Message.readSocksv5Message(clientSocket,ae,readbuffer,readclearbuffer);
        
            if (msg.version != 5)
                sendSocksv5ErrorResponse(Socksv5Message.Replies.SERVERFAILURE);
            else if ( (msg.cmdrep <1) || (msg.cmdrep >3))
                sendSocksv5ErrorResponse(Socksv5Message.Replies.COMMANDNOTSUPPORTED);
            else {
                switch (msg.cmdrep) {
                    case Socksv5Message.CONNECT:
                        doConnect(msg);
                        break;
                    case Socksv5Message.UDPASSOCIATE:
                        doUDPAssociate(msg);
                        break;
                    default:
                        clientSocket.close();
                        throw new UnsupportedOperationException();
                }
            }
        } catch (AddressTypeNotSupported ex) {
            sendSocksv5ErrorResponse(Socksv5Message.Replies.ADDRESSTYPENOTSUPPORTED);
        } catch (UnknownHostException ex) {
            sendSocksv5ErrorResponse(Socksv5Message.Replies.HOSTUNREACHABLE);
        } catch (ConnectException ex) {
            sendSocksv5ErrorResponse(Socksv5Message.Replies.CONNECTIONREFUSED);
        }
    }
    private void doConnect(Socksv5Message msg) throws IOException {
        InetSocketAddress remoteInetSocketAddress = getSocketAddress(msg);
        //System.out.println("SOCKS CONNECT to "+remoteInetSocketAddress);
        logger.log(Level.INFO, "SOCKS CONNECT to "+remoteInetSocketAddress);
        InetSocketAddress localInetSocketAddress = context.am.getLocalInetSocketAddressForConnect(remoteInetSocketAddress);
        context.remoteSocketChannel = SocketChannel.open();
        context.remoteSocketChannel.bind(localInetSocketAddress);
        context.remoteSocketChannel.connect(remoteInetSocketAddress);
        
        sendSocksv5ConnectSucceeded();
        //prepare the relay
        context.remoteSocketChannel.configureBlocking(false);
        context.clientSocketChannel.configureBlocking(false);
        ChannelRelay channelRelay = context.scrm.createChannelRelay(clientSocket, context.remoteSocketChannel, context.ae);
        channelRelay.addChannelRelayListener(listener);
        channelRelay.start();
        
    }
    InetSocketAddress getSocketAddress(Socksv5Message msg) throws IOException{
        InetAddress InetAddress = null;
        switch (msg.addressType) {
            case IPV4: case IPV6:
                InetAddress = InetAddress.getByAddress(msg.addressBytes);
                break;
            case HOSTNAME:
                InetAddress = InetAddress.getByName(new String(msg.addressBytes,charset));
        }
        return new InetSocketAddress(InetAddress,msg.port);
    }
    private void doUDPAssociate(Socksv5Message msg) throws IOException {
        InetSocketAddress clientUDPaddress = getSocketAddress(msg);
        InetSocketAddress localInetSocketAddress = context.am.getLocalInetSocketAddressForUDP();
        DatagramChannel internaldc = DatagramChannel.open();
        DatagramChannel externaldc = DatagramChannel.open();
        externaldc.bind(localInetSocketAddress);
        internaldc.bind(new InetSocketAddress(((InetSocketAddress)context.clientSocketChannel.getLocalAddress()).getAddress(),0));
        if (!clientUDPaddress.getAddress().isAnyLocalAddress())
            internaldc.connect(clientUDPaddress);
        logger.log(Level.INFO, "SOCKS UDP ASSOCIATE. UDP remote client "+clientUDPaddress+". UDP relay (internal: "+internaldc.getLocalAddress()+" external: "+externaldc.getLocalAddress()+")");
        internaldc.configureBlocking(false);
        externaldc.configureBlocking(false);
        context.clientSocketChannel.configureBlocking(false);
        context.udpae = context.am.getAuthenticationEncapsulationForUDP();
        UDPChannelRelay channelRelay = context.urs.createChannelRelay(context.clientSocketChannel,internaldc, externaldc, context.udpae);
        channelRelay.addChannelRelayListener(listenerudp);
        channelRelay.start();
        sendSocksv5UDPSucceeded((InetSocketAddress) internaldc.getLocalAddress());
    }
    private void sendSocksv5ErrorResponse(Socksv5Message.Replies reply) throws IOException {
        byte[] response = new byte[10]; // response con ipv4 todo ceros
        response[0] = 5;
        response[1] = (byte) reply.ordinal();
        response[3] = 1;
        write(response);
    }
    private void sendSocksv5Succeded(InetSocketAddress remotesocketaddress) throws IOException {
        byte[] rawaddress = remotesocketaddress.getAddress().getAddress();
        byte[] response = new byte[6+rawaddress.length];
        response[0] = 5;
        response[1] = 0;
        response[3] = (byte) ((rawaddress.length == 4)?1:4);
        System.arraycopy(rawaddress, 0, response, 4, rawaddress.length);
        response[response.length-2] = (byte) ((remotesocketaddress.getPort() >>> 8) &0xFF);
        response[response.length-1] = (byte) (remotesocketaddress.getPort() &0xFF);
        write(response);
    }
    private void sendSocksv5ConnectSucceeded() throws IOException {
        InetSocketAddress remotesocketaddress = (InetSocketAddress) context.remoteSocketChannel.getLocalAddress();
        sendSocksv5Succeded(remotesocketaddress);
    }
    private void sendSocksv5UDPSucceeded(InetSocketAddress relayaddress) throws IOException {
        sendSocksv5Succeded(relayaddress);
    }
    private AuthenticationMethod getAuthenticationMethod(byte[] methods) {
        if (context.methods == null)
            return null;
        for (byte method: methods) {
            for (AuthenticationMethod authMethod: context.methods) {
                if (method == authMethod.getMethodId())
                    return authMethod;
            }
        }
        return null;
    }
    
    
    static ChannelRelay.ChannelRelayListener listener = new ChannelRelay.ChannelRelayListener() {

        @Override
        public void onEOF(ChannelRelay cr, SelectableChannel ClosingChannel) {
                cr.getRelayManager().deleteChannelRelay(cr);
                StringBuilder sb = new StringBuilder("Connection closed by: ");
                sb.append((ClosingChannel == cr.getChannel1())?"internal":"external");
                sb.append(" . External socket: "+cr.getChannel2());
                logger.info(sb.toString());
        }

        @Override
        public void onIOException(ChannelRelay cr, SelectableChannel channel,IOException ex) {
                cr.getRelayManager().deleteChannelRelay(cr);
                logger.log(Level.WARNING, "IOException in TCP relay: "+channel, ex);
//                System.out.println("IOException in TCP relay: "+ex);
        }
    };
    static UDPChannelRelay.ChannelRelayListener listenerudp = new UDPChannelRelay.ChannelRelayListener() {

        @Override
        public void onEOF(UDPChannelRelay cr, SelectableChannel ClosingChannel) {
            cr.getRelayManager().deleteChannelRelay(cr);
            logger.log(Level.INFO, "Socks TCP socket closed. UDP association terminated.");
//            System.out.print("Socks TCP socket closed. UDP association terminated.");
        }

        @Override
        public void onIOException(UDPChannelRelay cr, IOException ex) {
            cr.getRelayManager().deleteChannelRelay(cr);
//            System.out.println("IOException in UDP channel relay: "+ex);
            logger.log(Level.WARNING, "IOException in UDP channel relay: ", ex);
        }
    };
}
