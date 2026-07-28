package com.dadcoach.workspace;

import com.dadcoach.workspace.aggregation.QuickActionsService;
import com.dadcoach.workspace.aggregation.WorkspaceSummaryService;
import com.dadcoach.workspace.dto.response.PartialResponse;
import com.dadcoach.workspace.dto.response.QuickActionsResponse;
import com.dadcoach.workspace.dto.response.WorkspaceSummaryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for workspace-level endpoints.
 *
 * <p>Provides the workspace summary endpoint that aggregates data from multiple
 * domain services with partial degradation support, and the quick actions endpoint
 * for contextual suggestions.</p>
 */
@RestController
@RequestMapping("/api/v1/workspace")
public class WorkspaceController {

    private final WorkspaceSummaryService workspaceSummaryService;
    private final QuickActionsService quickActionsService;

    public WorkspaceController(WorkspaceSummaryService workspaceSummaryService,
                               QuickActionsService quickActionsService) {
        this.workspaceSummaryService = workspaceSummaryService;
        this.quickActionsService = quickActionsService;
    }

    /**
     * Returns the workspace summary for the authenticated father.
     *
     * <p>Aggregates data from father profile, growth system, children, goals,
     * missions, and notifications. Supports partial degradation: if any source
     * is unavailable, the response still returns with available data and lists
     * the degraded sections.</p>
     *
     * @param principal the authenticated user
     * @return 200 OK with the workspace summary wrapped in PartialResponse
     */
    @GetMapping("/summary")
    public ResponseEntity<PartialResponse<WorkspaceSummaryResponse>> getSummary(Principal principal) {
        UUID fatherId = extractFatherId(principal);
        PartialResponse<WorkspaceSummaryResponse> response = workspaceSummaryService.getSummary(fatherId);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns contextual quick action suggestions for the authenticated father.
     *
     * <p>Computes up to 5 priority-ordered suggestions based on current state signals:
     * active missions, unread notifications, streak at risk, goals nearing completion.</p>
     *
     * @param principal the authenticated user
     * @return 200 OK with quick actions response
     */
    @GetMapping("/quick-actions")
    public ResponseEntity<QuickActionsResponse> getQuickActions(Principal principal) {
        UUID fatherId = extractFatherId(principal);
        List<QuickActionsService.QuickActionItem> actions = quickActionsService.getQuickActions(fatherId);

        List<QuickActionsResponse.QuickActionItem> items = actions.stream()
                .map(a -> new QuickActionsResponse.QuickActionItem(
                        a.actionId(),
                        a.actionType(),
                        a.title(),
                        a.description(),
                        a.priority(),
                        a.actionMetadata()
                ))
                .toList();

        return ResponseEntity.ok(new QuickActionsResponse(items));
    }

    /**
     * Extracts the father's UUID from the security context.
     *
     * <p>TODO: Integrate with production authentication infrastructure.
     * Currently uses the principal name as a UUID string.</p>
     */
    private UUID extractFatherId(Principal principal) {
        if (principal == null) {
            return UUID.fromString("00000000-0000-0000-0000-000000000001");
        }
        return UUID.fromString(principal.getName());
    }
}
