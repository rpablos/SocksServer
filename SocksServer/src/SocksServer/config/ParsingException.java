//  Author: Ronald Pablos
//  Year: 2014

package SocksServer.config;

/**
 *
 * @author Ronald
 */
public class ParsingException extends Exception {

    public ParsingException() {
        super();
    }
    public ParsingException(String msg) {
        super(msg);
    }
    public ParsingException(Throwable t) {
        super(t);
    }
    public ParsingException(String msg, Throwable t) {
        super(msg, t);
    }
}
