//  Author: Ronald Pablos
//  Year: 2014

package SocksServer;

import SocksServer.config.ParsingException;
import SocksServer.config.ProxyConfig;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 *
 * @author Ronald
 */
public class Main {
    static Logger logger = Logger.getLogger(Main.class.getPackage().getName());
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException, ParsingException {
        
        if (args.length < 1) {
            System.out.println("Usage: java -jar JSocksServer <configfile>");
            System.exit(0);
        }
        
        
        ProxyConfig config = ProxyConfig.loadConfig(new FileInputStream(args[0]));
        logger.setUseParentHandlers(false);
        Handler handler = (config.getLogfile()==null)?new ConsoleHandler():new FileHandler(config.getLogfile());
        handler.setFormatter(new SimpleTextFormatter());
        logger.addHandler(handler);
        SocksServer ss = null;
        
        while (true) {
            try {
                try {
                    ss = new SocksServer(config);
                    ss.start();
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, "Error creating Socks Server", ex);
                    if (ss != null)
                        ss.close();
                    Thread.sleep(1000);
                }
            } catch (Exception ex) {}
        }
    }
    private static class SimpleTextFormatter extends Formatter {
        final static String format = "%1$tF %1$tT.%1$tL [%2$s] %3$s%4$s%n";
        @Override
        public String format(LogRecord record) {
            Throwable thrown = record.getThrown();
            String message = record.getMessage();
            return String.format(format, record.getMillis(),record.getLevel(),(message==null)?"":message,(thrown==null)?"":"-->"+thrown);
        }
    }

}
