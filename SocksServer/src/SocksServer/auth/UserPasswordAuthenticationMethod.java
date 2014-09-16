//  Author: Ronald Pablos
//  Year: 2014

package SocksServer.auth;

import SocksServer.Socksv5Message;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Map;

/**
 *
 * @author USUARIO
 */
public class UserPasswordAuthenticationMethod extends AuthenticationMethodNull {
    Map<String,PasswordAndExternalAddress> users;
    InetSocketAddress externalAddress;
    SocketChannel socket;
    public UserPasswordAuthenticationMethod(Map<String,PasswordAndExternalAddress> users, SocketChannel socket) {
        this.users = users;
        this.socket = socket;
    }

    @Override
    public int getMethodId() {
        return 2;
    }

    @Override
    public boolean AuthenticationNegotiation() throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(512);
        Socksv5Message.readFully(socket, bb, 2);
        bb.flip();
        if (bb.get() != 1) { //version
            sendResponse(1);
            return false;
        }
        byte[] userdata = new byte[bb.get() & 0xFF];
        bb.compact();
        Socksv5Message.readFully(socket, bb, userdata.length+1);
        bb.flip();
        bb.get(userdata);
        byte[] pwdata = new byte[bb.get() & 0xFF];
        bb.compact();
        Socksv5Message.readFully(socket, bb, pwdata.length);
        bb.flip();
        bb.get(pwdata);
        String user = new String(userdata,"iso-8859-1");
        String password = new String(pwdata,"iso-8859-1");
        PasswordAndExternalAddress pwAndExternal = users.get(user);
        if ((pwAndExternal == null) || !pwAndExternal.password.equals(password)) {
            sendResponse(2);
            return false;
        }
        externalAddress = pwAndExternal.externalAddress;
        sendResponse(0);
        return true;
    }

    void sendResponse(int status) throws IOException {
        socket.write(ByteBuffer.wrap(new byte[] {1,(byte)status}));
    }

    @Override
    public InetSocketAddress getLocalInetSocketAddressForConnect(InetSocketAddress remote) {
        return externalAddress;
    }

    @Override
    public InetSocketAddress getLocalInetSocketAddressForUDP() {
        return externalAddress;
    }
    public static class PasswordAndExternalAddress {
        String password;
        InetSocketAddress externalAddress;

        public PasswordAndExternalAddress(String password, InetSocketAddress externalAddress) {
            this.password = password;
            this.externalAddress = externalAddress;
        }

        public InetSocketAddress getExternalAddress() {
            return externalAddress;
        }

        public void setExternalAddress(InetSocketAddress externalAddress) {
            this.externalAddress = externalAddress;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
        
    }
}
