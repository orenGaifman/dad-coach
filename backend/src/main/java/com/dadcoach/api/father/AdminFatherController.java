package com.dadcoach.api.father;

import com.dadcoach.common.MaskingUtils;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherDeletionService;
import com.dadcoach.domain.father.FatherRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The operator API (DADCOACH_ADMIN_API_KEY, its own security chain): list fathers, read one, delete one for good.
 * Phones are masked. Father ids are the external UUIDs ({@code new UUID(0, id)}), as the platform person ref.
 * The dashboard's admin (WS-B, /api/admin/**) supersedes it; it stays the scriptable operator door.
 */
@RestController
@RequestMapping("/api/v1/admin/fathers")
public class AdminFatherController {

    private final FatherRepository fathers;
    private final FatherDeletionService deletions;

    public AdminFatherController(FatherRepository fathers, FatherDeletionService deletions) {
        this.fathers = fathers;
        this.deletions = deletions;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Map<String, Object> list() {
        List<Map<String, Object>> items = fathers.findAll().stream().map(AdminFatherController::summary).toList();
        return Map.of("items", items, "count", items.size());
    }

    @GetMapping("/{fatherId}")
    @Transactional(readOnly = true)
    public Map<String, Object> get(@PathVariable UUID fatherId) {
        Father father = fathers.findById(fatherId.getLeastSignificantBits())
                .orElseThrow(() -> new ResourceNotFoundException("Father", fatherId));
        Map<String, Object> m = summary(father);
        m.put("timezone", father.getTimezone());
        m.put("currentBelt", father.getCurrentBelt() == null ? null : father.getCurrentBelt().name());
        m.put("calendarConnected", father.hasGoogleCalendarConfigured());
        return m;
    }

    @DeleteMapping("/{fatherId}")
    public ResponseEntity<Void> delete(@PathVariable UUID fatherId) {
        deletions.deleteByOperator(fatherId.getLeastSignificantBits());
        return ResponseEntity.noContent().build();
    }

    private static Map<String, Object> summary(Father father) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", new UUID(0L, father.getId()).toString());
        m.put("displayName", father.getDisplayName());
        m.put("phone", MaskingUtils.maskPhone(father.getPhone()));
        m.put("status", father.getStatus().name());
        m.put("createdAt", father.getCreatedAt());
        return m;
    }
}
