package com.dadcoach.memory.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests verifying the append-only design of the {@link MemoryAuditRepository}.
 *
 * <p>From SPEC-004 Requirement 24 (REQ-24):
 * The audit log is append-only. Audit entries are never modified or deleted
 * except by the retention cleanup job after 2 years.
 *
 * <p>This test verifies that the repository interface:
 * <ul>
 *   <li>Only exposes save operations (no update semantics for existing entries)</li>
 *   <li>Does NOT expose delete methods (delete*, remove*, etc.)</li>
 *   <li>Does NOT extend JpaRepository or CrudRepository directly</li>
 *   <li>Only extends the base Repository interface</li>
 * </ul>
 *
 * <p><strong>Validates: Requirements REQ-24, Task 10 - Audit log is append-only</strong>
 *
 * @see MemoryAuditRepository
 * @see MemoryAuditRetentionRepository for retention cleanup (separate interface)
 */
@DisplayName("MemoryAuditRepository Append-Only Design Tests")
class MemoryAuditRepositoryAppendOnlyTest {

    /**
     * List of method name patterns that indicate update/delete operations.
     * These should NOT appear in the append-only repository.
     */
    private static final List<String> FORBIDDEN_METHOD_PATTERNS = List.of(
            "delete",
            "remove",
            "deleteById",
            "deleteAll",
            "deleteAllById",
            "deleteAllInBatch",
            "deleteAllByIdInBatch",
            "deleteInBatch",
            "flush",          // flush can be used to force updates
            "saveAndFlush"    // implies immediate persistence which could update
    );

    /**
     * Allowed methods that the repository SHOULD have.
     */
    private static final Set<String> ALLOWED_METHODS = Set.of(
            "save",
            "saveAll",
            "findById",
            "findByFatherIdOrderByCreatedAtDesc",
            "findByFatherIdAndTimeRange",
            "findByMemoryIdOrderByCreatedAtAsc",
            "findMostRecentByMemoryId",
            "findByFatherIdAndEventTypeOrderByCreatedAtDesc",
            "findByEventTypeOrderByCreatedAtDesc",
            "findByFatherIdAndActorTypeOrderByCreatedAtDesc",
            "countByMemoryId",
            "countByFatherId",
            "count",
            "existsById",
            "findByCreatedAtBefore"
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Repository Does Not Expose Delete Methods
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Forbidden Methods Tests")
    class ForbiddenMethodsTests {

        @Test
        @DisplayName("Repository should NOT have any delete methods")
        void repositoryShouldNotHaveAnyDeleteMethods() {
            // Arrange
            Method[] methods = MemoryAuditRepository.class.getDeclaredMethods();
            
            // Act
            List<String> forbiddenMethods = Arrays.stream(methods)
                    .map(Method::getName)
                    .filter(name -> FORBIDDEN_METHOD_PATTERNS.stream()
                            .anyMatch(pattern -> name.toLowerCase().contains(pattern.toLowerCase())))
                    .collect(Collectors.toList());

            // Assert
            assertThat(forbiddenMethods)
                    .as("Repository should not expose any delete/update methods")
                    .isEmpty();
        }

        @Test
        @DisplayName("Repository should NOT inherit from JpaRepository")
        void repositoryShouldNotInheritFromJpaRepository() {
            // Assert
            Class<?>[] interfaces = MemoryAuditRepository.class.getInterfaces();
            
            List<String> interfaceNames = Arrays.stream(interfaces)
                    .map(Class::getSimpleName)
                    .collect(Collectors.toList());

            assertThat(interfaceNames)
                    .as("Should not extend JpaRepository directly")
                    .doesNotContain("JpaRepository");
            
            assertThat(interfaceNames)
                    .as("Should not extend CrudRepository directly")
                    .doesNotContain("CrudRepository");
            
            assertThat(interfaceNames)
                    .as("Should not extend PagingAndSortingRepository directly")
                    .doesNotContain("PagingAndSortingRepository");
        }

        @Test
        @DisplayName("Repository should extend base Repository interface")
        void repositoryShouldExtendBaseRepositoryInterface() {
            // Assert
            Class<?>[] interfaces = MemoryAuditRepository.class.getInterfaces();
            
            List<String> interfaceNames = Arrays.stream(interfaces)
                    .map(Class::getSimpleName)
                    .collect(Collectors.toList());

            assertThat(interfaceNames)
                    .as("Should extend base Repository interface")
                    .contains("Repository");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Repository Exposes Only Allowed Methods
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Allowed Methods Tests")
    class AllowedMethodsTests {

        @Test
        @DisplayName("Repository should have save method")
        void repositoryShouldHaveSaveMethod() throws NoSuchMethodException {
            // Assert - save method should exist
            Method saveMethod = MemoryAuditRepository.class.getDeclaredMethod(
                    "save", MemoryAuditLog.class);
            assertThat(saveMethod).isNotNull();
        }

        @Test
        @DisplayName("Repository should have saveAll method")
        void repositoryShouldHaveSaveAllMethod() throws NoSuchMethodException {
            // Assert - saveAll method should exist
            Method saveAllMethod = MemoryAuditRepository.class.getDeclaredMethod(
                    "saveAll", Iterable.class);
            assertThat(saveAllMethod).isNotNull();
        }

        @Test
        @DisplayName("Repository should have findById method")
        void repositoryShouldHaveFindByIdMethod() throws NoSuchMethodException {
            // Assert
            Method method = MemoryAuditRepository.class.getDeclaredMethod(
                    "findById", java.util.UUID.class);
            assertThat(method).isNotNull();
        }

        @Test
        @DisplayName("Repository should have count methods")
        void repositoryShouldHaveCountMethods() throws NoSuchMethodException {
            // Assert
            Method countByMemoryId = MemoryAuditRepository.class.getDeclaredMethod(
                    "countByMemoryId", java.util.UUID.class);
            Method countByFatherId = MemoryAuditRepository.class.getDeclaredMethod(
                    "countByFatherId", java.util.UUID.class);
            Method count = MemoryAuditRepository.class.getDeclaredMethod("count");
            
            assertThat(countByMemoryId).isNotNull();
            assertThat(countByFatherId).isNotNull();
            assertThat(count).isNotNull();
        }

        @Test
        @DisplayName("Repository should have all expected query methods")
        void repositoryShouldHaveAllExpectedQueryMethods() {
            // Arrange
            Method[] declaredMethods = MemoryAuditRepository.class.getDeclaredMethods();
            Set<String> actualMethodNames = Arrays.stream(declaredMethods)
                    .map(Method::getName)
                    .collect(Collectors.toSet());

            // Assert - all allowed methods should be present
            for (String allowedMethod : ALLOWED_METHODS) {
                assertThat(actualMethodNames)
                        .as("Repository should have method: " + allowedMethod)
                        .contains(allowedMethod);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Retention Repository Separation
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Retention Repository Separation Tests")
    class RetentionRepositorySeparationTests {

        @Test
        @DisplayName("Retention repository should be a separate interface")
        void retentionRepositoryShouldBeSeparateInterface() {
            // Assert - verify MemoryAuditRetentionRepository exists as a separate class
            assertThat(MemoryAuditRetentionRepository.class)
                    .as("Retention repository should be a separate interface")
                    .isNotSameAs(MemoryAuditRepository.class);
        }

        @Test
        @DisplayName("Retention repository should have delete method")
        void retentionRepositoryShouldHaveDeleteMethod() {
            // Arrange
            Method[] methods = MemoryAuditRetentionRepository.class.getDeclaredMethods();
            
            // Act
            List<String> methodNames = Arrays.stream(methods)
                    .map(Method::getName)
                    .collect(Collectors.toList());

            // Assert - retention repository should have delete method
            assertThat(methodNames)
                    .as("Retention repository should have deleteByCreatedAtBefore method")
                    .contains("deleteByCreatedAtBefore");
        }

        @Test
        @DisplayName("Retention repository should extend JpaRepository")
        void retentionRepositoryShouldExtendJpaRepository() {
            // Assert
            Class<?>[] interfaces = MemoryAuditRetentionRepository.class.getInterfaces();
            
            List<String> interfaceNames = Arrays.stream(interfaces)
                    .map(Class::getSimpleName)
                    .collect(Collectors.toList());

            assertThat(interfaceNames)
                    .as("Retention repository should extend JpaRepository for delete capability")
                    .contains("JpaRepository");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test: Method Count Verification
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Method Count Verification Tests")
    class MethodCountVerificationTests {

        @Test
        @DisplayName("Repository should have limited number of methods (no inherited delete methods)")
        void repositoryShouldHaveLimitedNumberOfMethods() {
            // Arrange
            Method[] methods = MemoryAuditRepository.class.getDeclaredMethods();

            // Assert - verify the exact number of methods matches expected
            // This ensures no unexpected methods are added
            assertThat(methods.length)
                    .as("Repository should have exactly the allowed methods")
                    .isEqualTo(ALLOWED_METHODS.size());
        }
    }
}
