package com.dadcoach.memory.contradiction;

import com.dadcoach.memory.Memory;
import com.dadcoach.memory.MemoryCategory;
import com.dadcoach.memory.MemoryRepository;
import com.dadcoach.memory.MemoryState;
import com.dadcoach.memory.MemorySubjectType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Service for detecting contradictions between memories of the same subject.
 *
 * <p>From SPEC-004 Requirement 7 (Memory Conflicts and Contradiction Resolution):
 * <ul>
 *   <li>Detects conflicts by comparing new memory content against existing ACTIVE and CONFIRMED
 *       memories of the same category and subject</li>
 *   <li>Uses Semantic_Similarity > 0.7 combined with contradiction detection</li>
 *   <li>Contradiction indicators: negation patterns, mutually exclusive values, differing quantities</li>
 * </ul>
 *
 * <p>This service implements the first step of the contradiction resolution workflow:
 * detecting potential contradictions. The resolution logic (supersession, confidence reduction,
 * conflict grouping) is handled by the caller.
 *
 * @see Contradiction
 * @see ContradictionType
 */
@Service
public class ContradictionDetectionService {

    private static final Logger log = LoggerFactory.getLogger(ContradictionDetectionService.class);

    /**
     * Minimum semantic similarity threshold for contradiction detection (Req 7 criteria 1).
     */
    public static final double SEMANTIC_SIMILARITY_THRESHOLD = 0.70;

    /**
     * High confidence threshold for contradiction detection.
     */
    public static final double HIGH_CONFIDENCE_THRESHOLD = 0.8;

    /**
     * Medium confidence threshold for contradiction detection.
     */
    public static final double MEDIUM_CONFIDENCE_THRESHOLD = 0.6;

    /**
     * Low confidence threshold for potential contradictions.
     */
    public static final double LOW_CONFIDENCE_THRESHOLD = 0.4;

    private final MemoryRepository memoryRepository;

    // ─── Negation Patterns ───────────────────────────────────────────────

    /**
     * Patterns indicating negation in memory content.
     */
    private static final List<Pattern> NEGATION_PATTERNS = List.of(
            // Direct negation patterns
            Pattern.compile("\\b(doesn't|does not|don't|do not)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(isn't|is not|aren't|are not|wasn't|was not|weren't|were not)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(won't|will not|wouldn't|would not|can't|cannot|couldn't|could not)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(never|no longer|not anymore|stopped|quit|gave up)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(hates?|dislikes?|avoids?|refuses?)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bnot\\s+\\w+\\b", Pattern.CASE_INSENSITIVE)
    );

    /**
     * Patterns indicating positive affirmation.
     */
    private static final List<Pattern> AFFIRMATION_PATTERNS = List.of(
            Pattern.compile("\\b(likes?|loves?|enjoys?|prefers?|wants?)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(always|usually|often|regularly|frequently)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(is|are|was|were)\\s+\\w+\\b", Pattern.CASE_INSENSITIVE)
    );

    /**
     * Correction language patterns that indicate explicit correction (Req 3 criteria 11).
     */
    private static final List<Pattern> CORRECTION_PATTERNS = List.of(
            Pattern.compile("\\b(actually|in fact|to be correct)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(no,?\\s*it'?s?|no,?\\s*he'?s?|no,?\\s*she'?s?)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(i was wrong|i made a mistake|let me correct)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(correction|correcting|correct that)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(not\\s+\\w+,\\s+(but|rather))\\b", Pattern.CASE_INSENSITIVE)
    );

    // ─── Time/Value Patterns ─────────────────────────────────────────────

    /**
     * Pattern to extract time values (e.g., "7pm", "7:30 PM", "19:00").
     * Requires am/pm suffix or colon-separated format to avoid matching bare numbers.
     */
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|AM|PM)\\b|\\b(\\d{1,2}):(\\d{2})\\b"
    );

    /**
     * Pattern to extract age values.
     */
    private static final Pattern AGE_PATTERN = Pattern.compile(
            "\\b(\\d+)\\s*(years?\\s*old|year-old|yo)\\b", Pattern.CASE_INSENSITIVE
    );

    /**
     * Pattern to extract numeric quantities.
     */
    private static final Pattern QUANTITY_PATTERN = Pattern.compile(
            "\\b(\\d+)\\s*(times?|days?|weeks?|hours?|minutes?|nights?)\\b", Pattern.CASE_INSENSITIVE
    );

    // ─── Constructor ─────────────────────────────────────────────────────

    public ContradictionDetectionService(MemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
    }

    // ─── Main Detection Methods ──────────────────────────────────────────

    /**
     * Detects potential contradictions between a new memory and existing memories.
     *
     * <p>This method finds existing memories with the same subject (fatherId, childId, category,
     * subjectType) and analyzes them for contradictions using:
     * <ul>
     *   <li>Negation pattern detection</li>
     *   <li>Value difference detection (times, ages, quantities)</li>
     *   <li>Semantic conflict analysis (when embeddings are available)</li>
     * </ul>
     *
     * @param newMemory the newly created or updated memory to check
     * @return list of detected contradictions, ordered by confidence score descending
     */
    public List<Contradiction> detectContradictions(Memory newMemory) {
        if (newMemory == null) {
            throw new IllegalArgumentException("newMemory cannot be null");
        }

        log.debug("Detecting contradictions for memory {} with category {} and subject {}",
                newMemory.getId(), newMemory.getCategory(), newMemory.getSubjectType());

        // Find existing memories with the same subject
        List<Memory> existingMemories = findMemoriesForContradictionCheck(newMemory);

        if (existingMemories.isEmpty()) {
            log.debug("No existing memories found for contradiction check");
            return Collections.emptyList();
        }

        log.debug("Found {} existing memories to check for contradictions", existingMemories.size());

        // Check each existing memory for contradictions
        List<Contradiction> contradictions = new ArrayList<>();
        for (Memory existing : existingMemories) {
            // Skip if it's the same memory
            if (existing.getId() != null && existing.getId().equals(newMemory.getId())) {
                continue;
            }

            Optional<Contradiction> contradiction = analyzeForContradiction(existing, newMemory);
            contradiction.ifPresent(contradictions::add);
        }

        // Sort by confidence score descending
        contradictions.sort((c1, c2) -> Double.compare(c2.confidenceScore(), c1.confidenceScore()));

        log.info("Detected {} potential contradictions for memory {}", contradictions.size(), newMemory.getId());
        return contradictions;
    }

    /**
     * Detects contradictions for a new memory against a specific set of existing memories.
     * Useful when the caller has already retrieved the candidate memories.
     *
     * @param newMemory         the new memory to check
     * @param existingMemories  the existing memories to compare against
     * @return list of detected contradictions, ordered by confidence score descending
     */
    public List<Contradiction> detectContradictions(Memory newMemory, List<Memory> existingMemories) {
        if (newMemory == null) {
            throw new IllegalArgumentException("newMemory cannot be null");
        }
        if (existingMemories == null) {
            return Collections.emptyList();
        }

        List<Contradiction> contradictions = new ArrayList<>();
        for (Memory existing : existingMemories) {
            if (existing.getId() != null && existing.getId().equals(newMemory.getId())) {
                continue;
            }

            Optional<Contradiction> contradiction = analyzeForContradiction(existing, newMemory);
            contradiction.ifPresent(contradictions::add);
        }

        contradictions.sort((c1, c2) -> Double.compare(c2.confidenceScore(), c1.confidenceScore()));
        return contradictions;
    }

    // ─── Memory Retrieval ────────────────────────────────────────────────

    /**
     * Finds existing memories that should be checked for contradictions.
     *
     * <p>Per SPEC-004 Req 7 criteria 1, contradictions are detected between memories of:
     * <ul>
     *   <li>Same fatherId</li>
     *   <li>Same childId (if subject is CHILD)</li>
     *   <li>Same category</li>
     *   <li>Same subjectType</li>
     *   <li>State is ACTIVE or CONFIRMED</li>
     * </ul>
     */
    private List<Memory> findMemoriesForContradictionCheck(Memory newMemory) {
        Collection<MemoryState> activeStates = EnumSet.of(MemoryState.ACTIVE, MemoryState.CONFIRMED);

        return memoryRepository.findForContradictionDetection(
                newMemory.getFatherId(),
                newMemory.getChildId(),
                newMemory.getCategory(),
                newMemory.getSubjectType(),
                activeStates
        );
    }

    // ─── Contradiction Analysis ──────────────────────────────────────────

    /**
     * Analyzes two memories for potential contradiction.
     *
     * @param existingMemory the older memory
     * @param newMemory      the newer memory
     * @return Optional containing a Contradiction if detected, empty otherwise
     */
    Optional<Contradiction> analyzeForContradiction(Memory existingMemory, Memory newMemory) {
        String existingContent = existingMemory.getContent().toLowerCase();
        String newContent = newMemory.getContent().toLowerCase();

        // Check for explicit correction language first (highest priority)
        if (containsCorrectionLanguage(newContent)) {
            // Semantic similarity check to confirm it's about the same topic
            if (hasSufficientTopicOverlap(existingContent, newContent)) {
                return Optional.of(new Contradiction(
                        existingMemory,
                        newMemory,
                        HIGH_CONFIDENCE_THRESHOLD + 0.15, // 0.95 for explicit corrections
                        ContradictionType.EXPLICIT_CORRECTION,
                        "New memory contains explicit correction language and refers to similar topic"
                ));
            }
        }

        // Check for negation-based contradictions
        Optional<Contradiction> negationContradiction = checkNegationContradiction(existingMemory, newMemory);
        if (negationContradiction.isPresent()) {
            return negationContradiction;
        }

        // Check for value-based contradictions (times, ages, quantities)
        Optional<Contradiction> valueContradiction = checkValueContradiction(existingMemory, newMemory);
        if (valueContradiction.isPresent()) {
            return valueContradiction;
        }

        // Check for semantic contradictions using embeddings (if available)
        if (existingMemory.hasEmbedding() && newMemory.hasEmbedding()) {
            Optional<Contradiction> semanticContradiction = checkSemanticContradiction(existingMemory, newMemory);
            if (semanticContradiction.isPresent()) {
                return semanticContradiction;
            }
        }

        return Optional.empty();
    }

    // ─── Negation Detection ──────────────────────────────────────────────

    /**
     * Checks for negation-based contradictions between two memories.
     *
     * <p>Detects patterns like:
     * <ul>
     *   <li>"Lucas likes broccoli" vs "Lucas doesn't like broccoli"</li>
     *   <li>"He enjoys reading" vs "He hates reading"</li>
     * </ul>
     */
    private Optional<Contradiction> checkNegationContradiction(Memory existingMemory, Memory newMemory) {
        String existingContent = existingMemory.getContent();
        String newContent = newMemory.getContent();

        boolean existingHasNegation = containsNegation(existingContent);
        boolean newHasNegation = containsNegation(newContent);
        boolean existingHasAffirmation = containsAffirmation(existingContent);
        boolean newHasAffirmation = containsAffirmation(newContent);

        // Check for opposite polarity (one negative, one positive) with sufficient topic overlap
        if ((existingHasNegation && newHasAffirmation) || (existingHasAffirmation && newHasNegation)) {
            if (hasSufficientTopicOverlap(existingContent.toLowerCase(), newContent.toLowerCase())) {
                double confidence = calculateNegationConfidence(existingContent, newContent);
                if (confidence >= LOW_CONFIDENCE_THRESHOLD) {
                    return Optional.of(new Contradiction(
                            existingMemory,
                            newMemory,
                            confidence,
                            ContradictionType.NEGATION,
                            "Detected opposite polarity (affirmation vs negation) about similar topic"
                    ));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Checks if text contains negation patterns.
     */
    boolean containsNegation(String text) {
        for (Pattern pattern : NEGATION_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if text contains affirmation patterns.
     */
    boolean containsAffirmation(String text) {
        for (Pattern pattern : AFFIRMATION_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if text contains explicit correction language.
     */
    boolean containsCorrectionLanguage(String text) {
        for (Pattern pattern : CORRECTION_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calculates confidence score for a negation-based contradiction.
     */
    private double calculateNegationConfidence(String existing, String newContent) {
        // Base confidence for negation detection
        double confidence = MEDIUM_CONFIDENCE_THRESHOLD;

        // Boost confidence based on word overlap (excluding stop words)
        double overlap = calculateWordOverlap(existing.toLowerCase(), newContent.toLowerCase());
        confidence += overlap * 0.3;

        // Cap at high confidence (explicit corrections get higher scores)
        return Math.min(confidence, HIGH_CONFIDENCE_THRESHOLD);
    }

    // ─── Value Difference Detection ──────────────────────────────────────

    /**
     * Checks for value-based contradictions (different times, ages, quantities).
     */
    private Optional<Contradiction> checkValueContradiction(Memory existingMemory, Memory newMemory) {
        String existingContent = existingMemory.getContent();
        String newContent = newMemory.getContent();

        // Check for time contradictions (e.g., "bedtime is 7pm" vs "bedtime is 9pm")
        Optional<Contradiction> timeContradiction = checkTimeContradiction(existingMemory, newMemory);
        if (timeContradiction.isPresent()) {
            return timeContradiction;
        }

        // Check for age contradictions
        Optional<Contradiction> ageContradiction = checkAgeContradiction(existingMemory, newMemory);
        if (ageContradiction.isPresent()) {
            return ageContradiction;
        }

        // Check for quantity contradictions
        Optional<Contradiction> quantityContradiction = checkQuantityContradiction(existingMemory, newMemory);
        if (quantityContradiction.isPresent()) {
            return quantityContradiction;
        }

        return Optional.empty();
    }

    /**
     * Checks for time value contradictions.
     */
    private Optional<Contradiction> checkTimeContradiction(Memory existingMemory, Memory newMemory) {
        List<String> existingTimes = extractTimes(existingMemory.getContent());
        List<String> newTimes = extractTimes(newMemory.getContent());

        if (!existingTimes.isEmpty() && !newTimes.isEmpty()) {
            // Check if they're talking about the same thing (e.g., bedtime, wake-up time)
            if (hasSufficientTopicOverlap(existingMemory.getContent().toLowerCase(), 
                                          newMemory.getContent().toLowerCase())) {
                // Check if times are different
                if (!existingTimes.equals(newTimes)) {
                    return Optional.of(new Contradiction(
                            existingMemory,
                            newMemory,
                            HIGH_CONFIDENCE_THRESHOLD,
                            ContradictionType.DIFFERENT_VALUE,
                            String.format("Different time values: %s vs %s", existingTimes, newTimes)
                    ));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Checks for age value contradictions.
     */
    private Optional<Contradiction> checkAgeContradiction(Memory existingMemory, Memory newMemory) {
        List<Integer> existingAges = extractAges(existingMemory.getContent());
        List<Integer> newAges = extractAges(newMemory.getContent());

        if (!existingAges.isEmpty() && !newAges.isEmpty()) {
            if (hasSufficientTopicOverlap(existingMemory.getContent().toLowerCase(), 
                                          newMemory.getContent().toLowerCase())) {
                if (!existingAges.equals(newAges)) {
                    return Optional.of(new Contradiction(
                            existingMemory,
                            newMemory,
                            HIGH_CONFIDENCE_THRESHOLD,
                            ContradictionType.DIFFERENT_VALUE,
                            String.format("Different age values: %s vs %s", existingAges, newAges)
                    ));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Checks for quantity value contradictions.
     */
    private Optional<Contradiction> checkQuantityContradiction(Memory existingMemory, Memory newMemory) {
        Map<String, Integer> existingQuantities = extractQuantities(existingMemory.getContent());
        Map<String, Integer> newQuantities = extractQuantities(newMemory.getContent());

        if (!existingQuantities.isEmpty() && !newQuantities.isEmpty()) {
            // Check for quantities with the same unit but different values
            for (Map.Entry<String, Integer> existingEntry : existingQuantities.entrySet()) {
                String unit = existingEntry.getKey();
                Integer existingValue = existingEntry.getValue();
                Integer newValue = newQuantities.get(unit);

                if (newValue != null && !existingValue.equals(newValue)) {
                    if (hasSufficientTopicOverlap(existingMemory.getContent().toLowerCase(), 
                                                  newMemory.getContent().toLowerCase())) {
                        return Optional.of(new Contradiction(
                                existingMemory,
                                newMemory,
                                MEDIUM_CONFIDENCE_THRESHOLD + 0.1,
                                ContradictionType.DIFFERENT_VALUE,
                                String.format("Different quantity values for '%s': %d vs %d", 
                                        unit, existingValue, newValue)
                        ));
                    }
                }
            }
        }

        return Optional.empty();
    }

    // ─── Semantic Contradiction Detection ────────────────────────────────

    /**
     * Checks for semantic contradictions using vector embeddings.
     */
    private Optional<Contradiction> checkSemanticContradiction(Memory existingMemory, Memory newMemory) {
        double similarity = calculateCosineSimilarity(existingMemory.getEmbedding(), newMemory.getEmbedding());

        // High similarity + opposite polarity indicators suggest contradiction
        if (similarity >= SEMANTIC_SIMILARITY_THRESHOLD) {
            String existingContent = existingMemory.getContent();
            String newContent = newMemory.getContent();

            // Check for conflicting sentiments
            boolean existingPositive = containsAffirmation(existingContent) && !containsNegation(existingContent);
            boolean newPositive = containsAffirmation(newContent) && !containsNegation(newContent);

            if (existingPositive != newPositive) {
                double confidence = (similarity - SEMANTIC_SIMILARITY_THRESHOLD) / 
                                   (1.0 - SEMANTIC_SIMILARITY_THRESHOLD) * 0.3 + MEDIUM_CONFIDENCE_THRESHOLD;
                return Optional.of(new Contradiction(
                        existingMemory,
                        newMemory,
                        confidence,
                        ContradictionType.SEMANTIC_CONFLICT,
                        String.format("High semantic similarity (%.2f) with conflicting sentiment", similarity)
                ));
            }
        }

        return Optional.empty();
    }

    // ─── Value Extraction Helpers ────────────────────────────────────────

    /**
     * Extracts time values from text.
     */
    List<String> extractTimes(String text) {
        List<String> times = new ArrayList<>();
        var matcher = TIME_PATTERN.matcher(text);
        while (matcher.find()) {
            times.add(matcher.group());
        }
        return times;
    }

    /**
     * Extracts age values from text.
     */
    List<Integer> extractAges(String text) {
        List<Integer> ages = new ArrayList<>();
        var matcher = AGE_PATTERN.matcher(text);
        while (matcher.find()) {
            try {
                ages.add(Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // Skip invalid numbers
            }
        }
        return ages;
    }

    /**
     * Extracts quantity-unit pairs from text.
     */
    Map<String, Integer> extractQuantities(String text) {
        Map<String, Integer> quantities = new HashMap<>();
        var matcher = QUANTITY_PATTERN.matcher(text);
        while (matcher.find()) {
            try {
                int value = Integer.parseInt(matcher.group(1));
                String unit = matcher.group(2).toLowerCase();
                // Normalize units
                if (unit.endsWith("s")) {
                    unit = unit.substring(0, unit.length() - 1);
                }
                quantities.put(unit, value);
            } catch (NumberFormatException ignored) {
                // Skip invalid numbers
            }
        }
        return quantities;
    }

    // ─── Similarity Calculation ──────────────────────────────────────────

    /**
     * Calculates cosine similarity between two embedding vectors.
     */
    double calculateCosineSimilarity(float[] embedding1, float[] embedding2) {
        if (embedding1 == null || embedding2 == null || embedding1.length != embedding2.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < embedding1.length; i++) {
            dotProduct += embedding1[i] * embedding2[i];
            norm1 += embedding1[i] * embedding1[i];
            norm2 += embedding2[i] * embedding2[i];
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * Checks if two texts have sufficient word overlap to be about the same topic.
     */
    boolean hasSufficientTopicOverlap(String text1, String text2) {
        double overlap = calculateWordOverlap(text1, text2);
        return overlap >= 0.2; // At least 20% word overlap
    }

    /**
     * Calculates word overlap ratio between two texts.
     * Returns the Jaccard similarity of significant words (excluding stop words).
     */
    double calculateWordOverlap(String text1, String text2) {
        Set<String> words1 = extractSignificantWords(text1);
        Set<String> words2 = extractSignificantWords(text2);

        if (words1.isEmpty() || words2.isEmpty()) {
            return 0.0;
        }

        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);

        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);

        return (double) intersection.size() / union.size();
    }

    /**
     * Extracts significant words from text (excluding common stop words).
     */
    private Set<String> extractSignificantWords(String text) {
        Set<String> stopWords = Set.of(
                "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
                "have", "has", "had", "do", "does", "did", "will", "would", "could", "should",
                "may", "might", "must", "shall", "can", "need", "dare", "ought", "used",
                "to", "of", "in", "for", "on", "with", "at", "by", "from", "up", "about",
                "into", "over", "after", "beneath", "under", "above", "it", "its",
                "and", "but", "or", "nor", "so", "yet", "both", "either", "neither",
                "he", "she", "they", "them", "their", "his", "her", "my", "your", "our",
                "this", "that", "these", "those", "i", "you", "we"
        );

        Set<String> words = new HashSet<>();
        String[] tokens = text.toLowerCase().split("\\W+");
        for (String token : tokens) {
            if (token.length() > 2 && !stopWords.contains(token)) {
                words.add(token);
            }
        }
        return words;
    }
}
