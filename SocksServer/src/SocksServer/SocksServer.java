//  Author: Ronald Pablos
//  Year: 2014

package SocksServer;

import SocksServer.auth.AuthenticationMethod;
import SocksServer.auth.AuthenticationMethodNull;
import SocksServer.auth.UserPasswordAuthenticationMethod;
import SocksServer.config.ProxyConfig;
import SocksServer.config.RuleSet;
import SocksServer.tcp.SelectableChannelRelayManager;
import SocksServer.udp.UDPRelayServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 *
 * @author USUARIO
 */
public class SocksServer {
    static Logger logger = Logger.getLogger(SocksServer.class.getName());
    
    SelectableChannelRelayManager scrm;
    UDPRelayServer urs;
    ExecutorService executor;
    boolean finish = false;
    Selector selector;
    List<ServerSocketChannel> serversockets;
    ProxyConfig config;
    
    public SocksServer(ProxyConfig config) throws IOException {
        this.config = config;
        //executor = Executors.newFixedThreadPool(config.getNumThreadForNegotiation());
        executor = new ThreadPoolExecutor(config.getNumThreadForNegotiation(), Integer.MAX_VALUE,
                                      60L, TimeUnit.SECONDS,
                                      new SynchronousQueue<Runnable>());
        scrm = new SelectableChannelRelayManager();
        urs = new UDPRelayServer();
        selector = Selector.open();
        serversockets = new ArrayList<ServerSocketChannel>(config.getListeningAddresses().size());
        for (SocketAddress socketaddress: config.getListeningAddresses()) {
            ServerSocketChannel serversocket = ServerSocketChannel.open();
            serversockets.add(serversocket);
            serversocket.configureBlocking(false);
            serversocket.bind(socketaddress);
            logger.info("Socks Server listening in "+serversocket.getLocalAddress());
            //System.out.println("Socks server listening in "+serversocket.getLocalAddress());
            serversocket.register(selector, SelectionKey.OP_ACCEPT);
        }
    }

    
//    public SocksServer(List<SocketAddress> listeningAddresses) throws IOException {
//        this(listeningAddresses,15);
//    }
//
//    public SocksServer(List<SocketAddress> listeningAddresses,int numThreadForSocksNegotiation) throws IOException {
//        executor = Executors.newFixedThreadPool(numThreadForSocksNegotiation);
//        scrm = new SelectableChannelRelayManager();
//        urs = new UDPRelayServer();
//        selector = Selector.open();
//        serversockets = new ArrayList<ServerSocketChannel>(listeningAddresses.size());
//        for (SocketAddress socketaddress: listeningAddresses) {
//            ServerSocketChannel serversocket = ServerSocketChannel.open();
//            serversockets.add(serversocket);
//            serversocket.configureBlocking(false);
//            serversocket.bind(socketaddress);
//            System.out.println("Socks server listening in "+serversocket.getLocalAddress());
//            serversocket.register(selector, SelectionKey.OP_ACCEPT);
//        }
//    }
    public void start() throws IOException {
        finish = false;
        while (!finish) {
            int numkeys = selector.select();
            if (numkeys == 0)
                continue;
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                if (!key.isValid())
                    continue;
                if (key.isAcceptable()) {
                    ServerSocketChannel ss = (ServerSocketChannel)key.channel();
                    SocketChannel clientsocket = ss.accept();
                    //clientsocket.configureBlocking(false);
                    //System.out.println("Socket connected from: "+clientsocket.getRemoteAddress());
                    logger.info("Client connected from: "+clientsocket.getRemoteAddress());
                    SocksContext context = getSocksContext(clientsocket);
                    if (context != null)
                        executor.execute(new SocksSession(context));
                    else {
                        logger.info("No ruleset found for client "+clientsocket.getRemoteAddress()+". Closing.");
                        clientsocket.close();
                    }
                }
            }
        }
        
    }
    public void stop() {
        finish = true;
        selector.wakeup();
    }
    public void close() throws IOException {
        selector.close();
        IOException exc = null;
        for (ServerSocketChannel socket: serversockets) {
            try {
                socket.close();
            } catch( IOException ex) {
                if (exc == null) exc = ex;
            }
        }
        if (exc != null)
            throw exc;
    }
    SocksContext getSocksContext(SocketChannel clientsocket) throws IOException {
        InetSocketAddress iaddress = (InetSocketAddress) clientsocket.getRemoteAddress();
        SocksContext context = new SocksContext();
        context.clientSocketChannel = clientsocket;
        context.scrm = scrm;
        context.urs = urs;
        RuleSet ruleset = getRuleSet(clientsocket.getRemoteAddress());
        if (ruleset == null)
            return null;
        Map<String, UserPasswordAuthenticationMethod.PasswordAndExternalAddress> users = ruleset.getUsers();
        if (users == null)
            context.methods = Arrays.asList(new AuthenticationMethod[] { new AuthenticationMethodNull(ruleset.getExternalAddress())});
        else {
            context.methods = Arrays.asList(new AuthenticationMethod[] {
                new UserPasswordAuthenticationMethod(users, clientsocket)
            });
        }
        return context;
    }
    RuleSet getRuleSet(SocketAddress address) {
        for (RuleSet rule: config.getRules()) {
            if (rule.getSources().contains((InetSocketAddress) address))
                return rule;
        }
        return null;
    }
}
