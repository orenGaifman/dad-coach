package com.dadcoach.ai.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ToolWishlist entity.
 * 
 * <p>Provides methods to query and manage tool wishes for the
 * machine-learning style feedback loop.</p>
 */
@Repository
public interface ToolWishlistRepository extends JpaRepository<ToolWishlist, UUID> {
    
    /**
     * Find all wishes with a specific status.
     */
    List<ToolWishlist> findByStatus(ToolWishlist.WishStatus status);
    
    /**
     * Find all wishes with a specific status, ordered by occurrence count (most popular first).
     */
    List<ToolWishlist> findByStatusOrderByOccurrenceCountDesc(ToolWishlist.WishStatus status);
    
    /**
     * Find existing wish by suggested name (for deduplication).
     * Only considers wishes that are NEW or REVIEWING.
     */
    @Query("SELECT w FROM ToolWishlist w WHERE w.suggestedName = :name AND w.status IN ('NEW', 'REVIEWING')")
    Optional<ToolWishlist> findActiveBySuggestedName(@Param("name") String suggestedName);
    
    /**
     * Find all NEW wishes ordered by creation date (oldest first for FIFO review).
     */
    List<ToolWishlist> findByStatusOrderByCreatedAtAsc(ToolWishlist.WishStatus status);
    
    /**
     * Count wishes by status.
     */
    long countByStatus(ToolWishlist.WishStatus status);
    
    /**
     * Find top N most requested wishes (by occurrence count).
     */
    @Query("SELECT w FROM ToolWishlist w WHERE w.status IN ('NEW', 'REVIEWING') ORDER BY w.occurrenceCount DESC")
    List<ToolWishlist> findTopRequested();
    
    /**
     * Get aggregated statistics for dashboard.
     */
    @Query("""
        SELECT w.status, COUNT(w), SUM(w.occurrenceCount)
        FROM ToolWishlist w
        GROUP BY w.status
        """)
    List<Object[]> getWishlistStatistics();
    
    /**
     * Find wishes by father ID (for debugging/support).
     */
    List<ToolWishlist> findByFatherIdOrderByCreatedAtDesc(Long fatherId);
    
    /**
     * Search wishes by keyword in suggested name or user need.
     */
    @Query("""
        SELECT w FROM ToolWishlist w 
        WHERE LOWER(w.suggestedName) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(w.userNeed) LIKE LOWER(CONCAT('%', :keyword, '%'))
        ORDER BY w.occurrenceCount DESC
        """)
    List<ToolWishlist> searchByKeyword(@Param("keyword") String keyword);
}
