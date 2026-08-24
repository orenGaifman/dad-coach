package com.dadcoach.memory.sensitive;

import com.dadcoach.memory.Memory;
import com.dadcoach.memory.MemoryCategory;
import com.dadcoach.memory.MemoryRepository;
import com.dadcoach.memory.MemorySourceType;
import com.dadcoach.memory.MemoryState;
import com.dadcoach.memory.MemorySubjectType;
import com.dadcoach.memory.dto.RetrievalResultDto;
import com.dadcoach.memory.retrieval.CompositeScoreCalculator;
import com.dadcoach.memory.retrieval.MemoryRetriever;
import com.dadcoach.memory.mapper.MemoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests verifying the architectural isolation of safety events from normal memory retrieval.
 *
 * <p><b>Validates: SPEC-004 Task 12.5 - Safety events must be stored in a separate table
 * and never mixed into normal memory retrieval.</b>
 *
 * <p>This is a critical architectural constraint for data isolation and compliance:
 * <ul>
 *   <li>Safety events are stored in `safety_event_records` table (not `memories`)</li>
 *   <li>SafetyEventRepository is independent from MemoryRepository</li>
 *   <li>MemoryRetriever never returns SafetyEventRecord objects</li>
 *   <li>MemoryFacadeService.retrieveRanked() only returns Memory-based DTOs</li>
 * </ul>
 *
 * <p>This isolation ensures:
 * <ul>
 *   <li>Safety events are not accidentally injected into coaching prompts</li>
 *   <li>Safety events have different retention/deletion policies (7 years, not GDPR-deletable)</li>
 *   <li>Clear separation of concerns between coaching context and safety compliance</li>
 * </ul>
 *
 * @see SafetyEventRecord
 * @see SafetyEventRepository
 * @see Memory
 * @see MemoryRepository
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Safety Event Isolation Tests (Task 12.5)")
class SafetyEventIsolationTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Table Separation Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Table Separation")
    class TableSeparationTests {

        @Test
        @DisplayName("SafetyEventRecord should map to 'safety_event_records' table")
        void safetyEventRecordShouldMapToSeparateTable() {
            // Get the @Table annotation from SafetyEventRecord
            jakarta.persistence.Table tableAnnotation = SafetyEventRecord.class
                    .getAnnotation(jakarta.persistence.Table.class);

            assertThat(tableAnnotation).isNotNull();
            assertThat(tableAnnotation.name()).isEqualTo("safety_event_records");
        }

        @Test
        @DisplayName("Memory should map to 'memories' table (different from safety events)")
        void memoryShouldMapToDifferentTable() {
            // Get the @Table annotation from Memory
            jakarta.persistence.Table tableAnnotation = Memory.class
                    .getAnnotation(jakarta.persistence.Table.class);

            assertThat(tableAnnotation).isNotNull();
            assertThat(tableAnnotation.name()).isEqualTo("memories");

            // Confirm they are different tables
            assertThat(tableAnnotation.name()).isNotEqualTo("safety_event_records");
        }

        @Test
        @DisplayName("SafetyEventRecord and Memory should use different table names")
        void entitiesShouldUseDifferentTables() {
            String safetyTable = SafetyEventRecord.class
                    .getAnnotation(jakarta.persistence.Table.class).name();
            String memoryTable = Memory.class
                    .getAnnotation(jakarta.persistence.Table.class).name();

            assertThat(safetyTable)
                    .as("Safety events and memories must be in separate tables")
                    .isNotEqualTo(memoryTable);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Repository Independence Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Repository Independence")
    class RepositoryIndependenceTests {

        @Test
        @DisplayName("SafetyEventRepository should NOT extend MemoryRepository")
        void safetyEventRepositoryShouldNotExtendMemoryRepository() {
            // Get all interfaces implemented by SafetyEventRepository
            Class<?>[] interfaces = SafetyEventRepository.class.getInterfaces();

            // Should extend JpaRepository, not MemoryRepository
            boolean extendsJpaRepository = false;
            boolean extendsMemoryRepository = false;

            for (Class<?> iface : interfaces) {
                if (iface == JpaRepository.class) {
                    extendsJpaRepository = true;
                }
                if (iface == MemoryRepository.class) {
                    extendsMemoryRepository = true;
                }
            }

            assertThat(extendsJpaRepository)
                    .as("SafetyEventRepository should extend JpaRepository")
                    .isTrue();
            assertThat(extendsMemoryRepository)
                    .as("SafetyEventRepository must NOT extend MemoryRepository")
                    .isFalse();
        }

        @Test
        @DisplayName("SafetyEventRepository should be typed for SafetyEventRecord, not Memory")
        void safetyEventRepositoryShouldBeTypedForSafetyEventRecord() {
            // Get the generic type parameter of JpaRepository<T, ID>
            Type[] genericInterfaces = SafetyEventRepository.class.getGenericInterfaces();

            boolean foundCorrectGenericType = false;
            for (Type type : genericInterfaces) {
                if (type instanceof ParameterizedType paramType) {
                    Type[] typeArgs = paramType.getActualTypeArguments();
                    if (typeArgs.length > 0 && typeArgs[0] == SafetyEventRecord.class) {
                        foundCorrectGenericType = true;
                        break;
                    }
                }
            }

            assertThat(foundCorrectGenericType)
                    .as("SafetyEventRepository should be typed for SafetyEventRecord")
                    .isTrue();
        }

        @Test
        @DisplayName("MemoryRepository should NOT have any SafetyEventRecord-related methods")
        void memoryRepositoryShouldNotHaveSafetyEventMethods() {
            Method[] methods = MemoryRepository.class.getDeclaredMethods();

            for (Method method : methods) {
                // Check return types
                assertThat(method.getReturnType())
                        .as("MemoryRepository method '%s' should not return SafetyEventRecord", method.getName())
                        .isNotEqualTo(SafetyEventRecord.class);

                // Check parameter types
                for (Class<?> paramType : method.getParameterTypes()) {
                    assertThat(paramType)
                            .as("MemoryRepository method '%s' should not accept SafetyEventRecord", method.getName())
                            .isNotEqualTo(SafetyEventRecord.class);
                }

                // Check method names don't mention safety events
                assertThat(method.getName().toLowerCase())
                        .as("MemoryRepository should not have safety-related methods")
                        .doesNotContain("safety")
                        .doesNotContain("safetyevent");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Retrieval Isolation Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Retrieval Isolation")
    class RetrievalIsolationTests {

        @Mock
        private MemoryRepository memoryRepository;

        @Mock
        private CompositeScoreCalculator scoreCalculator;

        @Mock
        private MemoryMapper memoryMapper;

        private MemoryRetriever memoryRetriever;

        @BeforeEach
        void setUp() {
            memoryRetriever = new MemoryRetriever(memoryRepository, scoreCalculator, memoryMapper);
        }

        @Test
        @DisplayName("MemoryRetriever.retrieveRanked() should only return Memory-based results")
        void memoryRetrieverShouldOnlyReturnMemoryBasedResults() {
            UUID fatherId = UUID.randomUUID();
            Collection<MemoryState> states = EnumSet.of(MemoryState.ACTIVE, MemoryState.CONFIRMED);

            // Create test memories
            Memory memory = new Memory(
                    fatherId,
                    MemoryCategory.IDENTITY,
                    MemorySubjectType.FATHER,
                    "Test memory content",
                    8,
                    new BigDecimal("0.9"),
                    MemorySourceType.ONBOARDING
            );
            memory.setId(UUID.randomUUID());

            when(memoryRepository.findRetrievableMemories(
                    eq(fatherId),
                    any(),
                    any(BigDecimal.class)))
                    .thenReturn(List.of(memory));

            when(scoreCalculator.calculate(any(Memory.class), any(Float.class)))
                    .thenReturn(0.85);
            when(scoreCalculator.calculateRecencyFactor(any(Memory.class)))
                    .thenReturn(0.9);

            // The return type from retrieveRanked is List<RetrievalResultDto>
            // which contains MemoryDto, NOT SafetyEventRecord
            List<RetrievalResultDto> results = memoryRetriever.retrieveRanked(fatherId, null, null, 10);

            // Verify the result type at compile time - if this compiles, it proves
            // the method returns Memory-based DTOs, not SafetyEventRecords
            for (RetrievalResultDto result : results) {
                // RetrievalResultDto contains MemoryDto, not SafetyEventRecord
                assertThat(result.getClass())
                        .as("Results should be RetrievalResultDto (containing MemoryDto)")
                        .isEqualTo(RetrievalResultDto.class);
            }
        }

        @Test
        @DisplayName("RetrievalResultDto should NOT have any SafetyEventRecord fields")
        void retrievalResultDtoShouldNotContainSafetyEventFields() {
            // Check all fields in RetrievalResultDto
            java.lang.reflect.Field[] fields = RetrievalResultDto.class.getDeclaredFields();

            for (java.lang.reflect.Field field : fields) {
                assertThat(field.getType())
                        .as("RetrievalResultDto field '%s' should not be SafetyEventRecord", field.getName())
                        .isNotEqualTo(SafetyEventRecord.class);

                // Also check the field name doesn't mention safety
                assertThat(field.getName().toLowerCase())
                        .as("RetrievalResultDto should not have safety-related fields")
                        .doesNotContain("safety");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Entity Independence Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Entity Independence")
    class EntityIndependenceTests {

        @Test
        @DisplayName("SafetyEventRecord should NOT extend Memory")
        void safetyEventRecordShouldNotExtendMemory() {
            Class<?> superclass = SafetyEventRecord.class.getSuperclass();

            assertThat(superclass)
                    .as("SafetyEventRecord should not extend Memory")
                    .isNotEqualTo(Memory.class);
            assertThat(superclass)
                    .as("SafetyEventRecord should extend Object (no inheritance)")
                    .isEqualTo(Object.class);
        }

        @Test
        @DisplayName("SafetyEventRecord should NOT implement any Memory-related interfaces")
        void safetyEventRecordShouldNotImplementMemoryInterfaces() {
            Class<?>[] interfaces = SafetyEventRecord.class.getInterfaces();

            // SafetyEventRecord should have no interfaces (pure entity)
            // or only marker interfaces, not any Memory-related ones
            for (Class<?> iface : interfaces) {
                assertThat(iface.getName())
                        .as("SafetyEventRecord should not implement Memory-related interfaces")
                        .doesNotContain("Memory");
            }
        }

        @Test
        @DisplayName("Memory entity should NOT have any SafetyEvent-related fields")
        void memoryEntityShouldNotHaveSafetyEventFields() {
            java.lang.reflect.Field[] fields = Memory.class.getDeclaredFields();

            for (java.lang.reflect.Field field : fields) {
                assertThat(field.getType())
                        .as("Memory field '%s' should not be SafetyEventRecord type", field.getName())
                        .isNotEqualTo(SafetyEventRecord.class);

                assertThat(field.getName().toLowerCase())
                        .as("Memory field names should not mention safety events")
                        .doesNotContain("safetyevent");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Service Layer Isolation Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Service Layer Isolation")
    class ServiceLayerIsolationTests {

        @Test
        @DisplayName("SafetyEventService should NOT inject MemoryRepository")
        void safetyEventServiceShouldNotInjectMemoryRepository() {
            // Check constructor parameters of SafetyEventService
            var constructors = SafetyEventService.class.getConstructors();

            for (var constructor : constructors) {
                for (var param : constructor.getParameters()) {
                    assertThat(param.getType())
                            .as("SafetyEventService should not depend on MemoryRepository")
                            .isNotEqualTo(MemoryRepository.class);
                }
            }
        }

        @Test
        @DisplayName("SafetyEventService should NOT have methods returning Memory")
        void safetyEventServiceShouldNotReturnMemory() {
            Method[] methods = SafetyEventService.class.getDeclaredMethods();

            for (Method method : methods) {
                assertThat(method.getReturnType())
                        .as("SafetyEventService method '%s' should not return Memory", method.getName())
                        .isNotEqualTo(Memory.class);

                // Check if return type is a collection of Memory
                Type returnType = method.getGenericReturnType();
                if (returnType instanceof ParameterizedType paramType) {
                    Type[] typeArgs = paramType.getActualTypeArguments();
                    for (Type arg : typeArgs) {
                        if (arg instanceof Class<?> argClass) {
                            assertThat(argClass)
                                    .as("SafetyEventService method '%s' should not return Memory in collection", 
                                        method.getName())
                                    .isNotEqualTo(Memory.class);
                        }
                    }
                }
            }
        }
    }
}
