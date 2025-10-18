
package portscanner;


import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class PortScanner {

   
/**
 * Basit Port Tarayıcı (Java)
 * Özellikler:
 *  - Belirli bir IP/hostname için port aralığını tarar.
 *  - Timeout ayarı yapılabilir.
 *  - Açık portları ve bilinen servis isimlerini listeler.
 *  - Sonuçları zaman damgalı bir .txt dosyasına kaydeder.
 *
 * Kullanım (CLI argümanları ile):
 *   java PortScanner <host> <startPort> <endPort> [timeoutMs]
 * Örnek:
 *   java PortScanner 127.0.0.1 1 1024 200
 *
 * Eğer argüman vermezsen interaktif olarak sorar.
 */


    private static final Map<Integer, String> WELL_KNOWN_SERVICES = createServiceMap();

    private static Map<Integer, String> createServiceMap() {
        Map<Integer, String> m = new HashMap<>();
        m.put(20, "FTP-Data");
        m.put(21, "FTP");
        m.put(22, "SSH");
        m.put(23, "Telnet");
        m.put(25, "SMTP");
        m.put(53, "DNS");
        m.put(67, "DHCP-Server");
        m.put(68, "DHCP-Client");
        m.put(69, "TFTP");
        m.put(80, "HTTP");
        m.put(110, "POP3");
        m.put(123, "NTP");
        m.put(135, "MSRPC");
        m.put(137, "NetBIOS-NS");
        m.put(138, "NetBIOS-DGM");
        m.put(139, "NetBIOS-SSN");
        m.put(143, "IMAP");
        m.put(161, "SNMP");
        m.put(389, "LDAP");
        m.put(443, "HTTPS");
        m.put(445, "SMB");
        m.put(465, "SMTPS");
        m.put(587, "Submission");
        m.put(636, "LDAPS");
        m.put(993, "IMAPS");
        m.put(995, "POP3S");
        m.put(1433, "MSSQL");
        m.put(1521, "Oracle");
        m.put(2049, "NFS");
        m.put(2375, "Docker");
        m.put(2376, "Docker TLS");
        m.put(3306, "MySQL");
        m.put(3389, "RDP");
        m.put(5432, "PostgreSQL");
        m.put(5672, "RabbitMQ");
        m.put(5900, "VNC");
        m.put(6379, "Redis");
        m.put(8000, "HTTP-Alt");
        m.put(8080, "HTTP-Alt");
        m.put(8443, "HTTPS-Alt");
        m.put(9000, "SonarQube/Alt");
        m.put(9200, "Elasticsearch");
        m.put(9300, "Elasticsearch-Node");
        m.put(27017, "MongoDB");
        return m;
    }

    public static void main(String[] args) {
        String host;
        int startPort;
        int endPort;
        int timeoutMs = 200; // varsayılan timeout

        if (args.length >= 3) {
            host = args[0];
            startPort = parseIntOrExit(args[1], "startPort");
            endPort = parseIntOrExit(args[2], "endPort");
            if (args.length >= 4) {
                timeoutMs = parseIntOrExit(args[3], "timeoutMs");
            }
        } else {
            Scanner sc = new Scanner(System.in);
            System.out.println("=== Basit Port Tarayıcı ===");
            System.out.print("Hedef (IP/hostname): ");
            host = sc.nextLine().trim();
            System.out.print("Başlangıç portu (örn: 1): ");
            startPort = Integer.parseInt(sc.nextLine().trim());
            System.out.print("Bitiş portu (örn: 1024): ");
            endPort = Integer.parseInt(sc.nextLine().trim());
            System.out.print("Timeout (ms) [varsayılan 200]: ");
            String t = sc.nextLine().trim();
            if (!t.isEmpty()) {
                timeoutMs = Integer.parseInt(t);
            }
        }

        validatePorts(startPort, endPort, timeoutMs);

        // Host doğrulama ve IP çözümleme
        InetAddress resolved;
        try {
            resolved = InetAddress.getByName(host);
        } catch (IOException e) {
            System.err.println("Hata: Hedef çözümlenemedi: " + host + " (" + e.getMessage() + ")");
            return;
        }

        System.out.printf(Locale.ROOT,
                "Hedef: %s (%s), Aralık: %d-%d, Timeout: %dms%n",
                host, resolved.getHostAddress(), Math.min(startPort, endPort), Math.max(startPort, endPort), timeoutMs);

        int from = Math.min(startPort, endPort);
        int to = Math.max(startPort, endPort);

        List<Integer> openPorts = new ArrayList<>();
        Instant t0 = Instant.now();

        for (int port = from; port <= to; port++) {
            if (isPortOpen(host, port, timeoutMs)) {
                openPorts.add(port);
                String svc = WELL_KNOWN_SERVICES.getOrDefault(port, "-");
                System.out.printf("  [+] %5d açık  | Servis: %s%n", port, svc);
            } else {
                // istersen sessiz tarama için bu satırı kapatabilirsin
                // System.out.printf("  [-] %5d kapalı%n", port);
            }

            // basit ilerleme göstergesi
            if ((port - from) % 50 == 0 && port != from) {
                System.out.printf("... %d porta kadar tarandı%n", port);
            }
        }

        Duration elapsed = Duration.between(t0, Instant.now());
        System.out.println("\n=== Tarama Bitti ===");
        if (openPorts.isEmpty()) {
            System.out.println("Açık port bulunamadı.");
        } else {
            System.out.println("Açık Portlar:");
            for (int p : openPorts) {
                String svc = WELL_KNOWN_SERVICES.getOrDefault(p, "-");
                System.out.printf("  %5d  %s%n", p, svc);
            }
        }
        System.out.printf("Toplam süre: %d ms%n", elapsed.toMillis());

        // Sonuçları dosyaya kaydet
        try {
            Path outPath = saveResults(host, resolved.getHostAddress(), from, to, timeoutMs, openPorts, elapsed);
            System.out.println("Sonuç kaydedildi: " + outPath.toAbsolutePath());
        } catch (IOException ioe) {
            System.err.println("Sonuç kaydedilemedi: " + ioe.getMessage());
        }
    }

    private static boolean isPortOpen(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static int parseIntOrExit(String s, String name) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException nfe) {
            System.err.println("Geçersiz sayı (" + name + "): " + s);
            System.exit(1);
            return -1; // Unreachable
        }
    }

    private static void validatePorts(int startPort, int endPort, int timeoutMs) {
        if (startPort < 1 || startPort > 65535 || endPort < 1 || endPort > 65535) {
            System.err.println("Portlar 1 ile 65535 arasında olmalıdır.");
            System.exit(1);
        }
        if (timeoutMs < 10 || timeoutMs > 10000) {
            System.err.println("timeoutMs 10 ile 10000 arasında olmalıdır (önerilen: 100-500).");
            System.exit(1);
        }
    }

    private static Path saveResults(String hostInput,
                                    String resolvedIp,
                                    int from,
                                    int to,
                                    int timeoutMs,
                                    List<Integer> openPorts,
                                    Duration elapsed) throws IOException {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String safeHost = hostInput.replaceAll("[^a-zA-Z0-9.-]", "_");
        Path out = Paths.get(String.format("scan-%s-%s.txt", safeHost, ts));

        List<String> lines = new ArrayList<>();
        lines.add("=== Basit Port Tarayıcı Sonuçları ===");
        lines.add("Tarih       : " + ts);
        lines.add("Hedef       : " + hostInput + " (" + resolvedIp + ")");
        lines.add("Aralık      : " + from + "-" + to);
        lines.add("Timeout(ms) : " + timeoutMs);
        lines.add("Süre(ms)    : " + elapsed.toMillis());
        lines.add("");
        lines.add("Açık Portlar:");
        if (openPorts.isEmpty()) {
            lines.add("  (yok)");
        } else {
            for (int p : openPorts) {
                String svc = WELL_KNOWN_SERVICES.getOrDefault(p, "-");
                lines.add(String.format("  %5d  %s", p, svc));
            }
        }
        Files.write(out, lines, StandardCharsets.UTF_8);
        return out;
    }
}

   
    

