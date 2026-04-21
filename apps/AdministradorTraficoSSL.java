
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLServerSocket;
import java.io.*;
import java.net.Socket;
import java.lang.Exception;
import java.security.KeyStore;
import java.util.regex.Matcher;
import java.sql.PreparedStatement;
import java.sql.DriverManager;
import java.net.SocketTimeoutException;
import java.util.regex.Pattern;
import java.sql.ResultSet;
import java.sql.Connection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;


public class AdministradorTraficoSSL {

    static String host1, host2;
    static int port1, port2, localPort;
    static String keystorePath, keystorePass;

    // BD VM1 (local)
    static final String DB_URL_VM1  = "jdbc:mysql://localhost:3306/servicio_web";
    // BD VM2 (via tunel SSH localhost:3307 -> VM2:3306)
    static final String DB_URL_VM2  = "jdbc:mysql://localhost:3307/servicio_web";
    static final String DB_USER = "miguel";
    static final String DB_PASS = "miguel";

    public static void main(String[] args) throws Exception {
        if (args.length != 7) {
            System.err.println("Uso: sudo java AdministradorTraficoSSL <host1> <port1> <host2> <port2> <localPort> <keystorePath> <keystorePass>");
            System.exit(1);
        }
        host1        = args[0];
        port1        = Integer.parseInt(args[1]);
        host2        = args[2];
        port2        = Integer.parseInt(args[3]);
        localPort    = Integer.parseInt(args[4]);
        keystorePath = args[5];
        keystorePass = args[6];

        // Configurar SSL
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(new FileInputStream(keystorePath), keystorePass.toCharArray());
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, keystorePass.toCharArray());
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);

        SSLServerSocketFactory factory = ctx.getServerSocketFactory();
        SSLServerSocket server = (SSLServerSocket) factory.createServerSocket(localPort);

        System.out.println("AdministradorTraficoSSL escuchando en puerto " + localPort);
        for (;;) {
            Socket cliente = server.accept();
            System.out.println("Cliente: " + cliente.getInetAddress());
            new Worker(cliente).start();
        }
    }

    static class Worker extends Thread {
        Socket cliente;
        Worker(Socket c) { this.cliente = c; }

        public void run() {
            try (Socket cliente = this.cliente) {
                cliente.setSoTimeout(30000);
                InputStream  clientIn  = cliente.getInputStream();
                OutputStream clientOut = cliente.getOutputStream();

                Socket vm1 = new Socket(host1, port1);
                Socket vm2 = new Socket(host2, port2);
                vm1.setSoTimeout(30000);
                vm2.setSoTimeout(30000);

                new Drainer(vm2.getInputStream()).start();
                new Forwarder(vm1.getInputStream(), clientOut).start();

                while (true) {
                    ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
                    if (!readHeaders(clientIn, headerBuf)) break;

                    byte[] headerBytes = headerBuf.toByteArray();
                    String headers     = new String(headerBytes, "ISO-8859-1");

                    int contentLength = 0;
                    Matcher clm = Pattern.compile("Content-Length:\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(headers);
                    if (clm.find()) contentLength = Integer.parseInt(clm.group(1));

                    byte[] body = new byte[contentLength];
                    if (contentLength > 0) readExact(clientIn, body);

                    String bodyStr = new String(body, "ISO-8859-1");
                    System.out.println(">> " + headers.split("\r\n")[0]);

                    byte[] headersForVM2 = headerBytes;
                    byte[] bodyForVM2    = body;

                    if (headers.contains("token=") || bodyStr.contains("token=")) {
                        String fullReq   = headers + bodyStr;
                        String idUsuario = extract(fullReq, "[?&]id_usuario=(\\d+)");
                        String replicaToken = getTokenFromReplica(idUsuario);
                        if (replicaToken != null) {
                            String modHeaders = headers.replaceAll("token=[^& \\r\\n]+", "token=" + replicaToken);
                            String modBody    = bodyStr.replaceAll("token=[^& \\r\\n\"]+", "token=" + replicaToken);
                            headersForVM2 = modHeaders.getBytes("ISO-8859-1");
                            bodyForVM2    = modBody.getBytes("ISO-8859-1");
                            if (contentLength > 0) {
                                String fixedHeaders = new String(headersForVM2, "ISO-8859-1")
                                        .replaceAll("Content-Length:\\s*\\d+", "Content-Length: " + bodyForVM2.length);
                                headersForVM2 = fixedHeaders.getBytes("ISO-8859-1");
                            }
                            System.out.println("Token reemplazado id=" + idUsuario + " -> " + replicaToken);
                        }
                    }

                    vm1.getOutputStream().write(headerBytes);
                    if (body.length > 0) vm1.getOutputStream().write(body);
                    vm1.getOutputStream().flush();

                    vm2.getOutputStream().write(headersForVM2);
                    if (bodyForVM2.length > 0) vm2.getOutputStream().write(bodyForVM2);
                    vm2.getOutputStream().flush();

                    String email = extract(headers + bodyStr, "\"email\"\\s*:\\s*\"([^\"]+)\"");
                    if (email == null) email = extract(headers, "[?&]email=([^& \\r\\n]+)");
                    if (email != null) {
                        Thread.sleep(300);
                        syncTokenByEmail(email);
                    }
                }

                vm1.close();
                vm2.close();
            } catch (Exception e) {}
        }
    }

    static boolean readHeaders(InputStream in, ByteArrayOutputStream out) throws IOException {
        int[] last4 = {0, 0, 0, 0};
        int b;
        try {
            while ((b = in.read()) != -1) {
                out.write(b);
                last4[0] = last4[1]; last4[1] = last4[2]; last4[2] = last4[3]; last4[3] = b;
                if (last4[0]=='\r' && last4[1]=='\n' && last4[2]=='\r' && last4[3]=='\n') return true;
            }
        } catch (SocketTimeoutException e) {}
        return false;
    }

    static void readExact(InputStream in, byte[] buf) throws IOException {
        int offset = 0;
        while (offset < buf.length) {
            int n = in.read(buf, offset, buf.length - offset);
            if (n == -1) break;
            offset += n;
        }
    }

    static class Forwarder extends Thread {
        InputStream in; OutputStream out;
        Forwarder(InputStream in, OutputStream out) { this.in = in; this.out = out; setDaemon(true); }
        public void run() {
            try {
                byte[] buf = new byte[65536]; int n;
                while ((n = in.read(buf)) != -1) { out.write(buf, 0, n); out.flush(); }
            } catch (IOException e) {}
        }
    }

    static class Drainer extends Thread {
        InputStream in;
        Drainer(InputStream in) { this.in = in; setDaemon(true); }
        public void run() {
            try { byte[] buf = new byte[65536]; while (in.read(buf) != -1) {} } catch (IOException e) {}
        }
    }

    static String getTokenFromReplica(String idUsuario) {
        if (idUsuario == null) return null;
        try (Connection c = DriverManager.getConnection(DB_URL_VM2, DB_USER, DB_PASS)) {
            PreparedStatement ps = c.prepareStatement("SELECT token FROM usuarios WHERE id_usuario = ?");
            ps.setInt(1, Integer.parseInt(idUsuario));
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("token") : null;
        } catch (Exception e) {
            System.err.println("getToken error: " + e.getMessage());
            return null;
        }
    }

    static void syncTokenByEmail(String email) {
        try {
            String tokenVM1 = null;
            try (Connection c = DriverManager.getConnection(DB_URL_VM1, DB_USER, DB_PASS)) {
                PreparedStatement ps = c.prepareStatement("SELECT token FROM usuarios WHERE email = ?");
                ps.setString(1, email);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) tokenVM1 = rs.getString("token");
            }
            if (tokenVM1 == null) return;

            try (Connection c = DriverManager.getConnection(DB_URL_VM2, DB_USER, DB_PASS)) {
                PreparedStatement ps = c.prepareStatement("UPDATE usuarios SET token = ? WHERE email = ?");
                ps.setString(1, tokenVM1);
                ps.setString(2, email);
                int rows = ps.executeUpdate();
                System.out.println("Token sincronizado (" + rows + " fila/s) email=" + email);
            }
        } catch (Exception e) {
            System.err.println("syncToken error: " + e.getMessage());
        }
    }

    static String extract(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1) : null;
    }
}