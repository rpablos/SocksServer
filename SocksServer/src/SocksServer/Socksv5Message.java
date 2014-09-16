//  Author: Ronald Pablos
//  Year: 2014

package SocksServer;


import SocksServer.auth.AuthenticationEncapsulation;
import java.io.EOFException;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;

/**
 *
 * @author Ronald
 */
public class Socksv5Message {
    final static public int CONNECT = 1;
    final static public int BIND = 2;
    final static public int UDPASSOCIATE = 3;
    public enum Replies {SUCCEEDED,SERVERFAILURE,CONNECTIONNOTALLOWED,NETWORKUNREACHABLE,
                        HOSTUNREACHABLE,CONNECTIONREFUSED,TTLEXPIRED,
                        COMMANDNOTSUPPORTED,ADDRESSTYPENOTSUPPORTED}
    public enum Commands {CONNECT, BIND, UDPASSOCIATE}
    public enum AddressType { IPV4,IPV6,HOSTNAME};
    AddressType addressType = null;
    byte[] addressBytes;
    int version;
    int cmdrep;
    int port;
    
    protected Socksv5Message(int version, int cmdrep,
            AddressType addressType, byte[] addressBytes, int port) {
        this.version = version;
        this.cmdrep = cmdrep;
        this.addressType = addressType;
        this.addressBytes = addressBytes;
        this.port = port;
    } 
    public static Socksv5Message readSocksv5Message(SocketChannel clientSocket,
        AuthenticationEncapsulation ae,ByteBuffer readbuffer, ByteBuffer readclearbuffer) throws IOException, SocksException {
        ByteBuffer srcb=readclearbuffer.slice();
        readFully(clientSocket,readbuffer,srcb,ae,4);
        int version = readclearbuffer.get() & 0xff;
        int cmdrep = readclearbuffer.get() & 0xff;
        readclearbuffer.get();
        int addresstypecode = readclearbuffer.get() & 0xff;
        AddressType addressType = null;
        switch (addresstypecode) {
            case 1: addressType = AddressType.IPV4;
                break;
            case 3: addressType = AddressType.HOSTNAME;
                    break;
            case 4: addressType = AddressType.IPV6;
                break;
            default:
                throw new AddressTypeNotSupported();
        }
        byte[] addressBytes = null;
        int hostnamelen = 0;
        switch (addressType) {
            case IPV4:
                if (srcb.position() < 8)
                    readFully(clientSocket,readbuffer,srcb,ae,8-srcb.position()+2);
                addressBytes = new byte[4];
                break;
            case IPV6:
                if (srcb.position() < 20)
                    readFully(clientSocket,readbuffer,srcb,ae,20-srcb.position()+2);
                addressBytes = new byte[16];
                break;
            case HOSTNAME:
                if (srcb.position() < 5)
                    readFully(clientSocket,readbuffer,srcb,ae,1);
                hostnamelen = readclearbuffer.get() & 0xff;
                if (srcb.position() < 5+hostnamelen)
                    readFully(clientSocket,readbuffer,srcb,ae,5+hostnamelen-srcb.position()+2);
                addressBytes = new byte[hostnamelen];
        }
        readclearbuffer.get(addressBytes);
        int dstport = readclearbuffer.getShort() & 0xFFFF;
        
        return new Socksv5Message(version,cmdrep,addressType,addressBytes,dstport);
    }
    
    static void readFully(SocketChannel ch,ByteBuffer readb,ByteBuffer writeb,
            AuthenticationEncapsulation ae, int length) throws IOException {
        if (writeb.remaining() < length)
            throw new BufferUnderflowException();
        ByteBuffer swb = writeb.slice();
        swb.limit(length);
        while (swb.remaining() >0) {
            //int available = ch.socket().getInputStream().available();
            readFully(ch, readb,1 /*Math.max(1,available)*/);
            readb.flip();
            ae.transform(readb, swb, true);
            readb.compact();
        }
        writeb.position(writeb.position()+length);
    }
    public static void readFully(ReadableByteChannel ch,ByteBuffer bb, int length) throws IOException{
        if (bb.remaining() < length)
            throw new BufferUnderflowException();
        ByteBuffer bb2 = bb.slice();
        bb2.limit(length);
        while (bb2.remaining() > 0) {
            int count = ch.read(bb2);
            if (count < 0)
                throw new EOFException();
        }
        bb.position(bb.position()+length);
    }
}
