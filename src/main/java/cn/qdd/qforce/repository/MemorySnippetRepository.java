package cn.qdd.qforce.repository;

import cn.qdd.qforce.api.dto.EventInput;
import cn.qdd.qforce.domain.MemorySnippet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;

@Repository
public class MemorySnippetRepository {

    private final JdbcTemplate jdbcTemplate;

    public MemorySnippetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MemorySnippet> findAll() {
        return jdbcTemplate.query(
                "SELECT id, content FROM memory_snippet",
                (rs, rowNum) -> new MemorySnippet(
                        rs.getString("id"),
                        rs.getString("content")
                )
        );
    }

    public void upsertFromEvents(List<EventInput> events) {
        for (EventInput event : events) {
            String content = ("[" + event.type() + "] " + event.description()).trim();
            String id = "auto-" + shortSha256(content);
            jdbcTemplate.update(
                    """
                    INSERT INTO memory_snippet (id, content)
                    VALUES (?, ?)
                    ON CONFLICT (id) DO UPDATE
                    SET content = EXCLUDED.content, updated_at = NOW()
                    """,
                    id,
                    content
            );
        }
    }

    private String shortSha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return toHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable.", e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
