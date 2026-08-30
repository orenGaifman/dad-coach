package com.dadcoach.ai.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing the tool wishlist.
 * 
 * <p>Handles the creation, deduplication, and review workflow
 * for AI-suggested tools.</p>
 */
@Service
public class ToolWishlistService {
    
    private static final Logger log = LoggerFactory.getLogger(ToolWishlistService.class);
    
    private final ToolWishlistRepository repository;
    
    public ToolWishlistService(ToolWishlistRepository repository) {
        this.repository = repository;
    }
    
    /**
     * Record a new tool wish from the AI.
     * 
     * <p>If a similar wish already exists (same suggested name),
     * increments the occurrence count instead of creating a duplicate.</p>
     * 
     * @param suggestedName the AI-suggested tool name
     * @param userNeed description of what the user needed
     * @param suggestedCapability what the tool should do
     * @param originalMessage the user's original message
     * @param fatherId the father who triggered this
     * @return the created or updated wish
     */
    @Transactional
    public ToolWishlist recordWish(
            String suggestedName,
            String userNeed,
            String suggestedCapability,
            String originalMessage,
            Long fatherId
    ) {
        // Normalize the suggested name
        String normalizedName = normalizeName(suggestedName);
        
        // Check for existing similar wish
        Optional<ToolWishlist> existing = repository.findActiveBySuggestedName(normalizedName);
        
        if (existing.isPresent()) {
            // Increment occurrence count on existing wish
            ToolWishlist wish = existing.get();
            wish.incrementOccurrence();
            log.info("Incremented tool wish occurrence: name={}, count={}", 
                    normalizedName, wish.getOccurrenceCount());
            return repository.save(wish);
        }
        
        // Create new wish
        ToolWishlist wish = ToolWishlist.builder()
                .suggestedName(normalizedName)
                .userNeed(userNeed)
                .suggestedCapability(suggestedCapability)
                .originalMessage(originalMessage)
                .fatherId(fatherId)
                .build();
        
        log.info("Created new tool wish: name={}, fatherId={}", normalizedName, fatherId);
        return repository.save(wish);
    }
    
    /**
     * Get all wishes pending review (NEW status).
     */
    public List<ToolWishlist> getPendingWishes() {
        return repository.findByStatusOrderByOccurrenceCountDesc(ToolWishlist.WishStatus.NEW);
    }
    
    /**
     * Get the top most requested wishes.
     */
    public List<ToolWishlist> getTopRequested(int limit) {
        return repository.findTopRequested().stream()
                .limit(limit)
                .toList();
    }
    
    /**
     * Approve a wish for development.
     */
    @Transactional
    public ToolWishlist approveWish(UUID wishId, Integer priority, String notes) {
        ToolWishlist wish = repository.findById(wishId)
                .orElseThrow(() -> new IllegalArgumentException("Wish not found: " + wishId));
        
        wish.setStatus(ToolWishlist.WishStatus.APPROVED);
        wish.setPriority(priority);
        wish.setReviewNotes(notes);
        wish.setReviewedAt(Instant.now());
        
        log.info("Approved tool wish: id={}, name={}, priority={}", 
                wishId, wish.getSuggestedName(), priority);
        return repository.save(wish);
    }
    
    /**
     * Reject a wish.
     */
    @Transactional
    public ToolWishlist rejectWish(UUID wishId, String reason) {
        ToolWishlist wish = repository.findById(wishId)
                .orElseThrow(() -> new IllegalArgumentException("Wish not found: " + wishId));
        
        wish.setStatus(ToolWishlist.WishStatus.REJECTED);
        wish.setReviewNotes(reason);
        wish.setReviewedAt(Instant.now());
        
        log.info("Rejected tool wish: id={}, name={}, reason={}", 
                wishId, wish.getSuggestedName(), reason);
        return repository.save(wish);
    }
    
    /**
     * Mark a wish as implemented (tool now exists).
     */
    @Transactional
    public ToolWishlist markImplemented(UUID wishId, String notes) {
        ToolWishlist wish = repository.findById(wishId)
                .orElseThrow(() -> new IllegalArgumentException("Wish not found: " + wishId));
        
        wish.setStatus(ToolWishlist.WishStatus.IMPLEMENTED);
        wish.setReviewNotes(notes);
        wish.setReviewedAt(Instant.now());
        
        log.info("Marked tool wish as implemented: id={}, name={}", 
                wishId, wish.getSuggestedName());
        return repository.save(wish);
    }
    
    /**
     * Mark a wish as duplicate of another.
     */
    @Transactional
    public ToolWishlist markDuplicate(UUID wishId, UUID duplicateOfId) {
        ToolWishlist wish = repository.findById(wishId)
                .orElseThrow(() -> new IllegalArgumentException("Wish not found: " + wishId));
        ToolWishlist original = repository.findById(duplicateOfId)
                .orElseThrow(() -> new IllegalArgumentException("Original wish not found: " + duplicateOfId));
        
        // Transfer occurrence count to original
        original.setOccurrenceCount(original.getOccurrenceCount() + wish.getOccurrenceCount());
        repository.save(original);
        
        wish.setStatus(ToolWishlist.WishStatus.DUPLICATE);
        wish.setReviewNotes("Duplicate of: " + duplicateOfId);
        wish.setReviewedAt(Instant.now());
        
        log.info("Marked tool wish as duplicate: id={} -> original={}", wishId, duplicateOfId);
        return repository.save(wish);
    }
    
    /**
     * Get statistics for the wishlist dashboard.
     */
    public WishlistStats getStatistics() {
        long newCount = repository.countByStatus(ToolWishlist.WishStatus.NEW);
        long reviewingCount = repository.countByStatus(ToolWishlist.WishStatus.REVIEWING);
        long approvedCount = repository.countByStatus(ToolWishlist.WishStatus.APPROVED);
        long implementedCount = repository.countByStatus(ToolWishlist.WishStatus.IMPLEMENTED);
        long rejectedCount = repository.countByStatus(ToolWishlist.WishStatus.REJECTED);
        
        return new WishlistStats(newCount, reviewingCount, approvedCount, implementedCount, rejectedCount);
    }
    
    /**
     * Search wishes by keyword.
     */
    public List<ToolWishlist> search(String keyword) {
        return repository.searchByKeyword(keyword);
    }
    
    /**
     * Normalize the suggested tool name for consistency.
     */
    private String normalizeName(String name) {
        if (name == null) return "unknown_tool";
        return name.toLowerCase()
                .trim()
                .replaceAll("\\s+", "_")
                .replaceAll("[^a-z0-9_]", "");
    }
    
    /**
     * Statistics record for the dashboard.
     */
    public record WishlistStats(
        long newCount,
        long reviewingCount,
        long approvedCount,
        long implementedCount,
        long rejectedCount
    ) {
        public long totalPending() {
            return newCount + reviewingCount;
        }
        
        public long totalProcessed() {
            return approvedCount + implementedCount + rejectedCount;
        }
    }
}
