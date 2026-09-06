import it.auties.whatsapp.api.ClientType;
import it.auties.whatsapp.controller.ControllerSerializer;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class SyncAllContactsToDb {
    public static void main(String[] args) throws Exception {
        var directory = Path.of(args[0]).toAbsolutePath();
        var serializer = ControllerSerializer.toProtobuf(directory);
        var store = serializer.deserializeStore(ClientType.WEB, "helpdesk").orElseThrow();
        var contacts = store.contacts();
        System.out.println("FOUND_CONTACTS: " + contacts.size());

        Class.forName("org.postgresql.Driver");
        try (Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/helpdesk", "helpdesk", "helpdesk_secure_dev_pass_2026")) {
            String queueId = "22222222-2222-4222-8222-222222222222";
            String sql = "INSERT INTO conversations(customer_jid, customer_name, queue_id, status, tags, preview, waiting_since, updated_at) " +
                         "VALUES (?, ?, ?::uuid, 'unassigned', ARRAY['WhatsApp'], 'WhatsApp contact', now(), now()) " +
                         "ON CONFLICT (customer_jid) WHERE status <> 'resolved' " +
                         "DO UPDATE SET customer_name = CASE WHEN EXCLUDED.customer_name <> '' THEN EXCLUDED.customer_name ELSE conversations.customer_name END";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                int count = 0;
                for (var contact : contacts) {
                    var jidStr = contact.jid().toString();
                    if (!jidStr.endsWith("@s.whatsapp.net")) continue;
                    var name = contact.fullName().orElse(contact.name());
                    if (name == null || name.isBlank()) {
                        name = contact.shortName().orElse(contact.chosenName().orElse(contact.jid().user()));
                    }
                    if (name == null || name.isBlank()) {
                        name = contact.jid().user();
                    }

                    stmt.setString(1, jidStr);
                    stmt.setString(2, name);
                    stmt.setString(3, queueId);
                    stmt.addBatch();
                    count++;
                }
                int[] results = stmt.executeBatch();
                System.out.println("IMPORTED_CONTACTS_COUNT: " + results.length + " (processed " + count + ")");
            }
        }
    }
}
