import it.auties.whatsapp.api.ErrorHandler;
import it.auties.whatsapp.api.PairingCodeHandler;
import it.auties.whatsapp.api.QrHandler;
import it.auties.whatsapp.api.Whatsapp;
import it.auties.whatsapp.controller.ControllerSerializer;
import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Pair the support account via QR code or 8-digit Phone Pairing Code, and sync contacts to database. */
public class PairWhatsapp {
    public static void main(String[] args) throws Exception {
        var directory = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(directory);
        var qrPath = directory.resolve("pairing.jpg");
        var artifactQr = args.length > 1 && !args[1].startsWith("--") ? Path.of(args[1]).toAbsolutePath() : null;
        var webPublicQr = Path.of("c:/VibeCode/EvenBetterHelp/frontend/public/pairing.jpg").toAbsolutePath();
        if (artifactQr != null && artifactQr.getParent() != null) {
            Files.createDirectories(artifactQr.getParent());
        }
        if (webPublicQr.getParent() != null) {
            Files.createDirectories(webPublicQr.getParent());
        }

        Long phoneNumber = null;
        for (String arg : args) {
            if (arg.startsWith("--phone=")) {
                phoneNumber = parsePhone(arg.substring(8));
            } else if (arg.matches("^\\+?[0-9]{8,16}$")) {
                phoneNumber = parsePhone(arg);
            }
        }

        var qrCount = new AtomicInteger(0);

        QrHandler fileSaver = QrHandler.toFile(qrPath, path -> {
            try {
                int count = qrCount.incrementAndGet();
                System.out.println("QR_GENERATED #" + count + ": " + path);
                if (artifactQr != null) {
                    Files.copy(path, artifactQr, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("QR_COPIED_TO_ARTIFACT: " + artifactQr);
                }
                Files.copy(path, webPublicQr, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("QR_COPIED_TO_WEB: " + webPublicQr);

                if (count == 1) {
                    try {
                        new ProcessBuilder("cmd.exe", "/c", "start", "http://localhost:3000/qr.html").start();
                        System.out.println("BROWSER_OPENED_QR_PAGE: http://localhost:3000/qr.html");
                    } catch (Exception ex) {
                        System.out.println("BROWSER_OPEN_FAILED: " + ex.getMessage());
                    }
                    try {
                        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                            Desktop.getDesktop().open(path.toFile());
                            System.out.println("DESKTOP_OPENED_QR: " + path);
                        } else {
                            new ProcessBuilder("cmd.exe", "/c", "start", "\"\"", path.toString()).start();
                            System.out.println("CMD_OPENED_QR: " + path);
                        }
                    } catch (Exception ex) {
                        System.out.println("DESKTOP_OPEN_FAILED: " + ex.getMessage());
                    }
                }
            } catch (Exception ex) {
                System.out.println("QR_COPY_FAILED: " + ex.getMessage());
            }
        });

        QrHandler combinedHandler = qrString -> {
            fileSaver.accept(qrString);
            System.out.println("\n========================================================");
            System.out.println("  WHATSAPP PAIRING QR CODE READY - SCAN WITH YOUR PHONE");
            System.out.println("  Open http://localhost:3000/qr.html or scan pairing.jpg");
            System.out.println("========================================================\n");
            try {
                var matrix = QrHandler.createMatrix(qrString, 45, 1);
                var sb = new StringBuilder();
                for (int y = 0; y < matrix.getHeight(); y++) {
                    for (int x = 0; x < matrix.getWidth(); x++) {
                        sb.append(matrix.get(x, y) ? "##" : "  ");
                    }
                    sb.append("\n");
                }
                System.out.println(sb.toString());
            } catch (Exception e) {
                try { QrHandler.toTerminal().accept(qrString); } catch (Exception ignored) {}
            }
            System.out.println("\n========================================================\n");
        };

        PairingCodeHandler codeHandler = code -> {
            System.out.println("\n========================================================");
            System.out.println("  WHATSAPP 8-CHARACTER PAIRING CODE:  " + code);
            System.out.println("  1. Open WhatsApp on your phone");
            System.out.println("  2. Tap Settings -> Linked devices -> Link a device");
            System.out.println("  3. Tap 'Link with phone number instead' at bottom");
            System.out.println("  4. Enter this 8-character code: " + code);
            System.out.println("========================================================\n");
            try {
                var html = "<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'><title>WhatsApp Pairing Code</title>" +
                           "<style>body{background:#0b141a;color:#e9edef;font-family:-apple-system,BlinkMacSystemFont,sans-serif;display:flex;justify-content:center;align-items:center;min-height:100vh;margin:0;}" +
                           ".card{background:#111b21;padding:2.5rem;border-radius:1rem;text-align:center;box-shadow:0 4px 20px rgba(0,0,0,0.5);max-width:440px;}" +
                           "h1{color:#00a884;font-size:1.6rem;margin-top:0;}" +
                           ".code{font-size:3rem;letter-spacing:8px;font-weight:700;color:#25d366;margin:1.5rem 0;background:#202c33;padding:1.2rem;border-radius:0.75rem;font-family:monospace;border:2px dashed #00a884;}" +
                           "ol{text-align:left;color:#aebac1;line-height:1.9;font-size:1rem;margin:1.5rem 0;}</style></head><body>" +
                           "<div class='card'><h1>Link EvenBetterHelp</h1><p>Enter this code on your phone:</p>" +
                           "<div class='code'>" + code + "</div>" +
                           "<ol><li>Open <b>WhatsApp</b> on your phone</li><li>Tap <b>Settings</b> &rarr; <b>Linked devices</b></li>" +
                           "<li>Tap <b>Link a device</b></li><li>Tap <b>Link with phone number instead</b></li><li>Type the code shown above</li></ol></div></body></html>";
                Files.writeString(Path.of("c:/VibeCode/EvenBetterHelp/frontend/public/code.html"), html);
                new ProcessBuilder("cmd.exe", "/c", "start", "http://localhost:3000/code.html").start();
                System.out.println("BROWSER_OPENED_CODE_PAGE: http://localhost:3000/code.html");
            } catch (Exception ex) {
                System.out.println("CODE_PAGE_WRITE_FAILED: " + ex.getMessage());
            }
        };

        boolean paired = false;
        int cycle = 0;
        while (!paired) {
            cycle++;
            System.out.println("PAIRING_CYCLE #" + cycle + ": Waiting for phone scan or code entry (timeout in 3m)...");
            Whatsapp client = null;
            try {
                var builder = Whatsapp.webBuilder()
                    .serializer(ControllerSerializer.toProtobuf(directory))
                    .newConnection("helpdesk")
                    .name("EvenBetterHelp")
                    .automaticMessageReceipts(false)
                    .errorHandler((api, location, error) -> {
                        System.out.println("PAIRING_ERROR " + location + " " + error.getClass().getSimpleName());
                        boolean isTimeout = false;
                        for (Throwable c = error; c != null; c = c.getCause()) {
                            if (c instanceof it.auties.whatsapp.exception.RequestException || (c.getMessage() != null && c.getMessage().contains("timed out"))) {
                                isTimeout = true;
                                break;
                            }
                        }
                        if (isTimeout) {
                            System.out.println("DISCARDING_LOGIN_TIMEOUT");
                            return ErrorHandler.Result.DISCARD;
                        }
                        return location == ErrorHandler.Location.MESSAGE ? ErrorHandler.Result.DISCARD : ErrorHandler.Result.DISCONNECT;
                    });

                if (phoneNumber != null) {
                    System.out.println("REQUESTING_PAIRING_CODE for +" + phoneNumber + "...");
                    client = builder.unregistered(phoneNumber, codeHandler);
                } else {
                    client = builder.unregistered(combinedHandler);
                }

                client.connect().get(3, TimeUnit.MINUTES);
                System.out.println("LOGIN_SUCCESS: Authenticated as " + client.store().jid().orElse(null));
                paired = true;

                System.out.println("Waiting 10s for chat history to sync...");
                Thread.sleep(10000);

                var chats = client.store().chats();
                var contacts = client.store().contacts();
                System.out.println("SYNC_COMPLETE: Found " + chats.size() + " chats, " + contacts.size() + " contacts");

                // Sync chats to PostgreSQL
                try {
                    Class.forName("org.postgresql.Driver");
                    try (Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/helpdesk", "helpdesk", "helpdesk_secure_dev_pass_2026")) {
                        String queueId = "22222222-2222-4222-8222-222222222222";
                        String sql = "INSERT INTO conversations(customer_jid, customer_name, queue_id, status, tags, preview, waiting_since, updated_at) " +
                                     "VALUES (?, ?, ?::uuid, 'unassigned', ARRAY['WhatsApp'], 'WhatsApp conversation', now(), now()) " +
                                     "ON CONFLICT (customer_jid) WHERE status <> 'resolved' DO UPDATE SET customer_name=EXCLUDED.customer_name";
                        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                            int count = 0;
                            for (var chat : chats) {
                                String jid = chat.jid().toString();
                                if (jid.endsWith("@s.whatsapp.net")) {
                                    String name = chat.name();
                                    if (name == null || name.isBlank()) {
                                        name = chat.jid().user();
                                    }
                                    stmt.setString(1, jid);
                                    stmt.setString(2, name);
                                    stmt.setString(3, queueId);
                                    stmt.addBatch();
                                    count++;
                                }
                            }
                            if (count > 0) {
                                stmt.executeBatch();
                                System.out.println("POSTGRES_SYNCED_CHATS: " + count + " conversations added/updated");
                            }
                        }
                    }
                } catch (Exception ex) {
                    System.out.println("POSTGRES_SYNC_WARNING: " + ex.getMessage());
                }

                client.store().serialize(false);
                client.keys().serialize(false);
                Files.deleteIfExists(qrPath);
                System.out.println("WHATSAPP_PAIRED");
            } catch (Exception ex) {
                System.out.println("PAIRING_CYCLE_EXPIRED: " + ex.getMessage() + " - Restarting with fresh session...");
                if (client != null) {
                    try { client.disconnect().get(5, TimeUnit.SECONDS); } catch (Exception ignored) {}
                }
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            } finally {
                if (paired && client != null) {
                    try { client.disconnect().get(10, TimeUnit.SECONDS); } catch (Exception ignored) {}
                }
            }
        }
    }

    private static Long parsePhone(String input) {
        if (input == null) return null;
        String digits = input.replaceAll("[^0-9]", "");
        if (digits.length() < 7) return null;
        return Long.parseLong(digits);
    }
}
