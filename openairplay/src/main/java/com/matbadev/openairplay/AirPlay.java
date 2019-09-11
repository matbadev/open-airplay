package com.matbadev.openairplay;

import jargs.gnu.CmdLineParser;

import javax.imageio.ImageIO;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class AirPlay {

    protected String hostname;
    protected String name;
    protected int port;
    protected PhotoThread photothread;
    protected String password;
    protected Map params;
    protected String authorization;
    protected Auth auth;
    protected int appletv_width = Config.APPLETV_WIDTH;
    protected int appletv_height = Config.APPLETV_HEIGHT;
    protected float appletv_aspect = Config.APPLETV_ASPECT;

    public AirPlay(Service service) {
        this(service.hostname, service.port, service.name);
    }

    public AirPlay(String hostname) {
        this(hostname, Config.PORT);
    }

    public AirPlay(String hostname, int port) {
        this(hostname, port, hostname);
    }

    public AirPlay(String hostname, int port, String name) {
        this.hostname = hostname;
        this.port = port;
        this.name = name;
    }

    public void setScreenSize(int width, int height) {
        appletv_width = width;
        appletv_height = height;
        appletv_aspect = (float) width / height;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    protected String md5Digest(String input) {
        //Get byte according by specified coding.
        byte[] source = input.getBytes(StandardCharsets.UTF_8);
        String result = null;
        char[] hexDigits = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(source);
            //The result should be one 128 integer
            byte[] temp = md.digest();
            char[] str = new char[16 * 2];
            int k = 0;
            for (int i = 0; i < 16; i++) {
                byte byte0 = temp[i];
                str[k++] = hexDigits[byte0 >>> 4 & 0xf];
                str[k++] = hexDigits[byte0 & 0xf];
            }
            result = new String(str);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;

    }

    protected String makeAuthorization(Map params, String password, String method, String uri) {
        String realm = (String) params.get("realm");
        String nonce = (String) params.get("nonce");
        String ha1 = md5Digest(Config.USERNAME + ":" + realm + ":" + password);
        String ha2 = md5Digest(method + ":" + uri);
        String response = md5Digest(ha1 + ":" + nonce + ":" + ha2);
        authorization = "Digest username=\"" + Config.USERNAME + "\", "
            + "realm=\"" + realm + "\", "
            + "nonce=\"" + nonce + "\", "
            + "uri=\"" + uri + "\", "
            + "response=\"" + response + "\"";
        return authorization;
    }

    protected Map getAuthParams(String authString) {
        Map params = new HashMap();
        int firstSpace = authString.indexOf(' ');
        String digest = authString.substring(0, firstSpace);
        String rest = authString.substring(firstSpace + 1).replaceAll("\r\n", " ");
        String[] lines = rest.split("\", ");
        for (String line : lines) {
            int split = line.indexOf("=\"");
            String key = line.substring(0, split);
            String value = line.substring(split + 2);
            if (value.charAt(value.length() - 1) == '"') {
                value = value.substring(0, value.length() - 1);
            }
            params.put(key, value);
        }
        return params;
    }

    protected String setPassword() throws IOException {
        if (password != null) {
            return password;
        } else {
            if (auth != null) {
                password = auth.getPassword(hostname, name);
                return password;
            } else {
                throw new IOException("Authorisation requied");
            }
        }
    }

    protected String doHTTP(String method, String uri) throws IOException {
        return doHTTP(method, uri, null);
    }

    protected String doHTTP(String method, String uri, ByteArrayOutputStream os) throws IOException {
        return doHTTP(method, uri, os, null);
    }

    protected String doHTTP(String method, String uri, ByteArrayOutputStream os, Map headers) throws IOException {
        return doHTTP(method, uri, os, new HashMap(), true);
    }

    protected String doHTTP(String method, String uri, ByteArrayOutputStream os, Map headers, boolean repeat) throws IOException {
        URL url = null;
        try {
            url = new URL("http://" + hostname + ":" + port + uri);
        } catch (MalformedURLException e) {
        }
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setUseCaches(false);
        conn.setDoOutput(true);
        conn.setRequestMethod(method);

        if (params != null) {
            //Try to reuse password if already set
            headers.put("Authorization", makeAuthorization(params, password, method, uri));
        }
        if (headers.size() > 0) {
            conn.setRequestProperty("User-Agent", "MediaControl/1.0");
            Object[] keys = headers.keySet().toArray();
            for (Object key : keys) {
                conn.setRequestProperty((String) key, (String) headers.get(key));
            }
        }

        if (os != null) {
            byte[] data = os.toByteArray();
            conn.setRequestProperty("Content-Length", "" + data.length);
        }
        conn.connect();
        if (os != null) {
            os.writeTo(conn.getOutputStream());
            os.flush();
            os.close();
        }

        if (conn.getResponseCode() == 401) {
            if (repeat) {
                String authstring = conn.getHeaderFields().get("WWW-Authenticate").get(0);
                if (setPassword() != null) {
                    params = getAuthParams(authstring);
                    return doHTTP(method, uri, os, headers, false);
                } else {
                    return null;
                }
            } else {
                throw new IOException("Incorrect password");
            }
        } else {
            //TODO: Only readback Content-Length? - right now not doing, seems to work different than PHP
            //conn.getHeaderField("Content-Length");
            InputStream is = conn.getInputStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            String line;
            StringBuilder response = new StringBuilder();
            while ((line = rd.readLine()) != null) {
                response.append(line);
                response.append("\r\n");
            }
            rd.close();
            return response.toString();
        }
    }

    public void stop() {
        try {
            stopPhotoThread();
            doHTTP("POST", "/stop");
            params = null;
        } catch (Exception e) {
        }
    }

    protected BufferedImage scaleImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= appletv_width && height <= appletv_height) {
            return image;
        } else {
            int scaledheight;
            int scaledwidth;
            float image_aspect = (float) width / height;
            if (image_aspect > appletv_aspect) {
                scaledheight = new Float(appletv_width / image_aspect).intValue();
                scaledwidth = appletv_width;
            } else {
                scaledheight = appletv_height;
                scaledwidth = new Float(appletv_height * image_aspect).intValue();
            }
            BufferedImage scaledimage = new BufferedImage(scaledwidth, scaledheight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaledimage.createGraphics();
            g.drawImage(image, 0, 0, scaledwidth, scaledheight, null);
            g.dispose();
            return scaledimage;
        }
    }

    public void photo(String filename) throws IOException {
        this.photo(filename, Config.NONE);
    }

    public void photo(String filename, String transition) throws IOException {
        this.photo(new File(filename), transition);
    }

    public void photo(File imagefile) throws IOException {
        this.photo(imagefile, Config.NONE);
    }

    public void photo(File imagefile, String transition) throws IOException {
        BufferedImage image = ImageIO.read(imagefile);
        photo(image, transition);
    }

    public void photo(BufferedImage image) throws IOException {
        this.photo(image, Config.NONE);
    }

    public void photo(BufferedImage image, String transition) throws IOException {
        stopPhotoThread();
        BufferedImage scaledimage = scaleImage(image);
        photoRaw(scaledimage, transition);
        photothread = new PhotoThread(this, scaledimage, 5000);
        photothread.start();
    }

    protected void photoRawCompress(BufferedImage image, String transition) throws IOException {
        BufferedImage scaledimage = scaleImage(image);
        photoRaw(scaledimage, transition);
    }

    protected void photoRaw(BufferedImage image, String transition) throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Apple-Transition", transition);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        boolean resultWrite = ImageIO.write(image, "jpg", os);
        /* TODO: Could adjust quality
         * http://www.java.net/node/689678
         * http://www.exampledepot.com/egs/javax.imageio/JpegWrite.html
         * http://www.sussmanprejza.com/ar/card/DataUpload.java
         */
        doHTTP("PUT", "/photo", os, headers);
    }

    public static BufferedImage captureScreen() throws AWTException {
        Toolkit tk = Toolkit.getDefaultToolkit();
        Dimension dim = tk.getScreenSize();
        Rectangle rect = new Rectangle(dim);
        Robot robot = new Robot();
        return robot.createScreenCapture(rect);
    }

    public void stopPhotoThread() {
        if (photothread != null) {
            photothread.interrupt();
            while (photothread.isAlive()) ;
            photothread = null;
        }
    }

    public void desktop() {
        stopPhotoThread();
        photothread = new PhotoThread(this);
        photothread.start();
    }

    protected static Service[] formatSearch(ServiceInfo[] services) {
        Service[] results = new Service[services.length];
        for (int i = 0; i < services.length; i++) {
            ServiceInfo service = services[i];
            Inet4Address[] addresses = service.getInet4Addresses();
            results[i] = new Service(addresses[0].getHostAddress(), service.getPort(), service.getName());
        }
        return results;
    }

    public static java.util.List<Service> search() throws IOException {
        return search(1000);
    }

    /**
     * List all valid inet addresses of this machine
     * will include IPv4 and IPv6 addresses
     *
     * @return list of inetaddresses
     */
    public static java.util.List<InetAddress> listNetworkAddresses() {
        ArrayList<InetAddress> validAddresses = new ArrayList<>();

        Enumeration<NetworkInterface> netInter;
        try {
            netInter = NetworkInterface.getNetworkInterfaces();

            while (netInter.hasMoreElements()) {
                NetworkInterface ni = netInter.nextElement();

                for (InetAddress iaddress : Collections.list(ni
                    .getInetAddresses())) {
                    if (iaddress.isLoopbackAddress()) {
                        continue;
                    }
                    validAddresses.add(iaddress);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return validAddresses;
    }


    /**
     * Search for existing apple tv services on all network interfaces
     *
     * @param timeout for each interface query
     * @return list of available services
     */
    public static java.util.List<Service> search(int timeout) throws IOException {
        java.util.List<InetAddress> networkAddresses = listNetworkAddresses();

        java.util.List<Service> availableServices = new ArrayList<>();

        //iterate over all existing addresses and search for apple tv devices
        for (InetAddress address : networkAddresses) {
            final JmDNS jmdns = JmDNS.create(address);
            Service[] tmpResults = formatSearch(jmdns.list(Config.DNSSD_TYPE, timeout));
            for (Service service : tmpResults) {
                if (!availableServices.contains(service)) {
                    availableServices.add(service);
                }
            }
            jmdns.close();
        }

        return availableServices;
    }

    public static AirPlay searchDialog(Window parent) throws IOException {
        return searchDialog(parent, 6000);
    }

    public static AirPlay searchDialog(Window parent, int timeout) throws IOException {
        JDialog search = new JDialog(parent, "Searching...");
        search.setVisible(true);
        search.setBounds(0, 0, 200, 100);
        search.setLocationRelativeTo(parent);
        search.toFront();
        search.setVisible(true);
        java.util.List<Service> services = AirPlay.search();
        search.setVisible(false);
        if (!services.isEmpty()) {
            //Choose AppleTV
            String[] choices = new String[services.size()];
            for (int i = 0; i < services.size(); i++) {
                Service service = services.get(i);
                choices[i] = service.name + " (" + service.hostname + ")";
            }
            String input = (String) JOptionPane.showInputDialog(parent, "", "Select AppleTV", JOptionPane.PLAIN_MESSAGE, null, choices, choices[0]);
            if (input != null) {
                int index = -1;
                for (int i = 0; i < choices.length; i++) {
                    if (input.equals(choices[i])) {
                        index = i;
                        break;
                    }
                }
                AirPlay airplay = new AirPlay(services.get(index));
                airplay.setAuth(new AuthDialog(parent));
                return airplay;
            }
            return null;
        } else {
            JOptionPane.showMessageDialog(parent, "No AppleTVs found", "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    /**
     * Display a dialog for selecting apple tv's resolution
     * TODO future improvement would be reading the resolution automatically from the apple tv. I guess this is possible somehow
     *
     * @param parent  (a parent window, can be null)
     * @param airplay the airplay instance of which you want to adjust the resolution
     */
    public static void selectResolutionDialog(Window parent, AirPlay airplay) {
        String[] choices = new String[]{
            "Full HD  - 1080p - 1920x1080",
            "HD Ready - 720p -1280 × 720"
        };

        String input = (String) JOptionPane.showInputDialog(parent, "", "Select AppleTV Resolution", JOptionPane.PLAIN_MESSAGE, null, choices, choices[0]);
        if (input != null) {
            if (input.equals(choices[0])) {
                airplay.setScreenSize(1920, 1080);
            } else if (input.equals(choices[1])) {
                airplay.setScreenSize(1280, 720);
            }
        }
    }

    // Command line functions
    public static void usage() {
        System.out.println("commands: -s {stop} | -p file {photo} | -d {desktop} | -?");
        System.out.println("java -jar airplay.jar -h hostname[:port] [-a password] command");
    }

    public static void main(String[] args) {
        try {
            CmdLineParser cmd = new CmdLineParser();
            CmdLineParser.Option hostopt = cmd.addStringOption('h', "hostname");
            CmdLineParser.Option stopopt = cmd.addBooleanOption('s', "stop");
            CmdLineParser.Option photoopt = cmd.addStringOption('p', "photo");
            CmdLineParser.Option desktopopt = cmd.addBooleanOption('d', "desktop");
            CmdLineParser.Option passopt = cmd.addStringOption('a', "password");
            CmdLineParser.Option helpopt = cmd.addBooleanOption('?', "help");
            cmd.parse(args);

            String hostname = (String) cmd.getOptionValue(hostopt);

            Boolean showHelp = (Boolean) cmd.getOptionValue(helpopt);

            if (null != showHelp && showHelp) {
                usage();
            } else if (hostname == null) { //show select dialog if no host address is given
                AirPlay airplay = searchDialog(null);
                if (null != airplay) {
                    selectResolutionDialog(null, airplay);
                    airplay.desktop();
                }
            } else {
                AirPlay airplay;
                String[] hostport = hostname.split(":", 2);
                if (hostport.length > 1) {
                    airplay = new AirPlay(hostport[0], Integer.parseInt(hostport[1]));
                } else {
                    airplay = new AirPlay(hostport[0]);
                }
                airplay.setAuth(new AuthConsole());
                String password = (String) cmd.getOptionValue(passopt);
                airplay.setPassword(password);
                String photo;
                if (cmd.getOptionValue(stopopt) != null) {
                    airplay.stop();
                } else if ((photo = (String) cmd.getOptionValue(photoopt)) != null) {
                    System.out.println("Press ctrl-c to quit");
                    airplay.photo(photo);
                } else if (cmd.getOptionValue(desktopopt) != null) {
                    System.out.println("Press ctrl-c to quit");
                    airplay.desktop();
                } else {
                    usage();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
