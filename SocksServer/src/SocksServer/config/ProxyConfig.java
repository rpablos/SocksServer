//  Author: Ronald Pablos
//  Year: 2014

package SocksServer.config;

import SocksServer.auth.UserPasswordAuthenticationMethod;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.regex.MatchResult;

/**
 *
 * @author USUARIO
 */
public class ProxyConfig {
    final static String listeningAddressStr = "ListeningAddress";
    final static String externalAddressStr = "ExternalAddress";
    final static String sourceAddressStr = "SourceAddresses";
    final static String destinationAddressStr = "DestinationAddresses";
    final static String userStr = "User";
    final static String logfileStr = "logfile";

    
    String logfile=null;
    List<SocketAddress> listeningAddresses;
    List<RuleSet> rules;
    int numThreadForNegotiation;
    public ProxyConfig(List<SocketAddress> listeningAddresses, List<RuleSet> rules, String logfile) {
        this.listeningAddresses = listeningAddresses;
        this.rules = rules;
        this.numThreadForNegotiation = 10;
        this.logfile = logfile;
    }

    public int getNumThreadForNegotiation() {
        return numThreadForNegotiation;
    }

    public List<SocketAddress> getListeningAddresses() {
        return listeningAddresses;
    }

    public List<RuleSet> getRules() {
        return rules;
    }

    public String getLogfile() {
        return logfile;
    }
    
    
    
    public static ProxyConfig loadConfig(InputStream in) throws ParsingException {
        int linenumber = 0;
        String logfile=null;
        List<RuleSet> rules = new LinkedList<RuleSet>();
        List<SocketAddress> listeningAddresses = new LinkedList<SocketAddress>();
        Scanner scannerlines = new Scanner(in);
        try {
            while (scannerlines.hasNextLine()) {
                String line = scannerlines.nextLine();
                line = line.trim();
                linenumber++;
                if (line.startsWith("#") || (line.length() == 0))
                    continue;
                Scanner scannerline = new Scanner(line);
                String str = scannerline.findInLine("\\Aruleset\\p{javaWhitespace}*\\{");
                if (str != null) {
                    boolean end = false;
                    RuleSet ruleset = new RuleSet();
                    while (scannerlines.hasNextLine()) {
                        
                        String rulesetline = scannerlines.nextLine().trim();
                        linenumber++;
                        if (rulesetline.startsWith("#") || (rulesetline.length() == 0))
                            continue;
                        if (rulesetline.equals("}")) {
                            end = true;
                            break;
                        }
                        Scanner scannerrulesetline = new Scanner(rulesetline);
                        scannerrulesetline.findInLine("\\A([^\\p{javaWhitespace}:=]+)\\p{javaWhitespace}*[:=]\\p{javaWhitespace}*([^\\p{javaWhitespace}]+)");
                        MatchResult match = scannerrulesetline.match();
                        String property = match.group(1);
                        String propertyvalue = match.group(2);
                        if (property.equalsIgnoreCase(externalAddressStr))
                            ruleset.externalAddress = parseSocketAddress(propertyvalue,linenumber);
                        else if (property.equalsIgnoreCase(sourceAddressStr)) {
                            if (ruleset.sources == null)
                                ruleset.sources = new InetAddressRanges();
                            ruleset.sources.add(InetAddressRanges.InetAddressRange.parse(propertyvalue));
                        } else if (property.equalsIgnoreCase(destinationAddressStr)) {
                            if (ruleset.destinations == null)
                                ruleset.destinations = new InetAddressRanges();
                            ruleset.destinations.add(InetAddressRanges.InetAddressRange.parse(propertyvalue));
                        } else if (property.equalsIgnoreCase(userStr)) {
                            if (ruleset.users == null)
                                ruleset.users = new HashMap<String,UserPasswordAuthenticationMethod.PasswordAndExternalAddress>();
                            StringTokenizer userpasswordexternalst = new StringTokenizer(propertyvalue,":");
                            if (userpasswordexternalst.countTokens() < 2)
                                throw new ParsingException("Incorrect user:password[:external] syntax");
                            String user = userpasswordexternalst.nextToken();
                            String password = userpasswordexternalst.nextToken();
                            InetSocketAddress userexternal;
                            if (userpasswordexternalst.hasMoreTokens()) {
                                userexternal = parseSocketAddress(userpasswordexternalst.nextToken(" \t").substring(1),linenumber);
                            } else {
                                userexternal = null;
                            }
                            ruleset.users.put(user, new UserPasswordAuthenticationMethod.PasswordAndExternalAddress(password,userexternal));
                        }
                        else 
                            throw new ParsingException("Unknown property in line "+linenumber);
                    }
                    if (end) {
                        if (ruleset.sources == null) 
                            throw new ParsingException("Property '"+sourceAddressStr+"' not specified");
                        if (ruleset.destinations == null) 
                            throw new ParsingException("Property '"+destinationAddressStr+"' not specified");
                        if (ruleset.externalAddress == null) 
                            ruleset.externalAddress = new InetSocketAddress(0);
                        if (ruleset.users != null)
                            for (Map.Entry<String, UserPasswordAuthenticationMethod.PasswordAndExternalAddress> entrySet : ruleset.users.entrySet()) {
                                UserPasswordAuthenticationMethod.PasswordAndExternalAddress value = entrySet.getValue();
                                if (value.getExternalAddress() == null)
                                    value.setExternalAddress((InetSocketAddress) ruleset.externalAddress);
                            }
                        
                        rules.add(ruleset);
                        continue;
                    }
                    else
                        throw new ParsingException("Unexpected end of input");
                }
                scannerline.findInLine("\\A([^\\p{javaWhitespace}:=]+)\\p{javaWhitespace}*[:=]\\p{javaWhitespace}*([^\\p{javaWhitespace}]+)");
                MatchResult match = scannerline.match();
                String property = match.group(1);
                String propertyvalue = match.group(2);
                if (property.equalsIgnoreCase(listeningAddressStr))
                    listeningAddresses.add(parseSocketAddress(propertyvalue,linenumber));
                else if (property.equalsIgnoreCase(logfileStr))
                    logfile = propertyvalue;
                else 
                    throw new ParsingException("Unknown property in line "+linenumber);
                
            }
        } catch (ParsingException ex) {
            throw ex;
        }catch (Exception ex) {  
            throw new ParsingException("Error parsing config in line "+linenumber);
        } 
        return new ProxyConfig(listeningAddresses, rules,logfile);
    }
    static private InetSocketAddress parseSocketAddress(String value,int linenumber) throws ParsingException {
        
        int pos = -1;
        if (!value.endsWith("]"))
                pos = value.lastIndexOf(':');
        String addressStr = (pos < 0)?value:value.substring(0, pos);
        String portStr = (pos < 0)?null:value.substring(pos+1);
        if (portStr != null && addressStr.startsWith("[") && !addressStr.endsWith("]")) //ipv6 con puerto
            throw new ParsingException("Error parsing ipv6 address in line "+linenumber);
        try {
//        StringTokenizer st = new StringTokenizer(value,":");
//        try {
//           
//            InetAddress address = InetAddress.getByName(st.nextToken());
//            int port  = 0;
//            if (st.hasMoreTokens())
//                port = Integer.parseInt(st.nextToken());
            InetAddress address = InetAddress.getByName(addressStr);
            int port = (portStr == null)?0:Integer.parseInt(portStr);
            return new InetSocketAddress(address,port);
        } catch (UnknownHostException ex) {
            throw new ParsingException("Error parsing address in line "+linenumber);
        } catch (NumberFormatException ex) {
            throw new ParsingException("Error parsing port in line "+linenumber);
        } catch (Exception ex) {
            throw new ParsingException("Error parsing address in line "+linenumber,ex);
        }
    }
    
    
}
