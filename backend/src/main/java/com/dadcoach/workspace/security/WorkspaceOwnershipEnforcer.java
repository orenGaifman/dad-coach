package com.dadcoach.workspace.security;

import com.dadcoach.workspace.ResourceNotFoundException;
import com.dadcoach.workspace.aggregation.ChildDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Enforces ownership checks at the service layer.
 *
 * <p>Cross-father access attempts return 404 (not 403) to prevent resource enumeration.
 * This component ensures that a father can only access their own resources.</p>
 */
@Component
public class WorkspaceOwnershipEnforcer {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceOwnershipEnforcer.class);

    private final ChildDataService childDataService;

    public WorkspaceOwnershipEnforcer(ChildDataService childDataService) {
        this.childDataService = childDataService;
    }

    /**
     * Verifies that the authenticated father is accessing their own resource.
     *
     * @param authenticatedFatherId the ID from the authentication token
     * @param targetFatherId        the ID of the resource being accessed
     * @throws ResourceNotFoundException if the IDs do not match (prevents enumeration)
     */
    public void verifyFatherOwnership(UUID authenticatedFatherId, UUID targetFatherId) {
        if (!authenticatedFatherId.equals(targetFatherId)) {
            log.warn("Cross-father access attempt: authenticated={}, target={}",
                    authenticatedFatherId, targetFatherId);
            throw new ResourceNotFoundException("father", targetFatherId);
        }
    }

    /**
     * Verifies that the specified child belongs to the authenticated father.
     *
     * @param fatherId the authenticated father's ID
     * @param childId  the child ID being accessed
     * @throws ResourceNotFoundException if the child does not belong to the father
     */
    public void verifyChildBelongsToFather(UUID fatherId, UUID childId) {
        if (!childDataService.childBelongsToFather(fatherId, childId)) {
            log.warn("Cross-father child access attempt: fatherId={}, childId={}",
                    fatherId, childId);
            throw new ResourceNotFoundException("child", childId);
        }
    }
}
