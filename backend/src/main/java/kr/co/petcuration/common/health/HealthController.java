package kr.co.petcuration.common.health;

import java.time.Instant;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    ResponseEntity<HealthEnvelope> health() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(new HealthEnvelope(new HealthResponse("UP", "UP", Instant.now())));
        } catch (DataAccessException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new HealthEnvelope(new HealthResponse("DOWN", "DOWN", Instant.now())));
        }
    }

    record HealthEnvelope(HealthResponse data) {
    }

    record HealthResponse(String status, String database, Instant timestamp) {
    }
}
