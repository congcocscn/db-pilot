package com.dbpilot.infrastructure.adapter.knowledge;

import com.dbpilot.core.model.BusinessRule;
import com.dbpilot.core.model.TableMetadata;
import com.dbpilot.core.model.UserHabit;
import com.dbpilot.core.port.out.KnowledgeStoreAdapter;
import com.dbpilot.infrastructure.persistence.entity.BusinessRuleEntity;
import com.dbpilot.infrastructure.persistence.entity.SchemaMetadataEntity;
import com.dbpilot.infrastructure.persistence.entity.UserHabitEntity;
import com.dbpilot.infrastructure.persistence.repository.BusinessRuleRepository;
import com.dbpilot.infrastructure.persistence.repository.SchemaMetadataRepository;
import com.dbpilot.infrastructure.persistence.repository.UserHabitRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * {@link KnowledgeStoreAdapter} implementation using an embedded H2 database
 * via Spring Data JPA.
 *
 * <p>Provides local-first storage for:</p>
 * <ul>
 *   <li>Business rules (Global + User scoped)</li>
 *   <li>User habits (query frequency tracking)</li>
 *   <li>Schema cache (introspected metadata)</li>
 * </ul>
 *
 * <p>Data is stored at {@code ~/.db-pilot/knowledge} (H2 file-based).</p>
 *
 * @author DB-Pilot
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelationalKnowledgeStore implements KnowledgeStoreAdapter {

    private final BusinessRuleRepository ruleRepository;
    private final UserHabitRepository habitRepository;
    private final SchemaMetadataRepository schemaRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /** Default schema cache TTL: 24 hours. */
    private static final long CACHE_TTL_MINUTES = 1440;

    // ==================== Global Scope ====================

    @Override
    @Transactional
    public void saveGlobalRule(BusinessRule rule) {
        BusinessRuleEntity entity = toEntity(rule);
        entity.setScope(BusinessRuleEntity.Scope.GLOBAL);
        ruleRepository.save(entity);
        log.info("Saved global rule: {}", rule.getRuleText());
    }

    @Override
    public List<BusinessRule> getGlobalRules() {
        return ruleRepository.findByScopeOrderByCreatedAtDesc(BusinessRuleEntity.Scope.GLOBAL)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<BusinessRule> getGlobalRules(String databaseAlias) {
        // Get rules specific to this database + universal global rules
        var specific = ruleRepository.findByScopeAndDatabaseAlias(
                BusinessRuleEntity.Scope.GLOBAL, databaseAlias);
        var universal = ruleRepository.findByScopeAndDatabaseAlias(
                BusinessRuleEntity.Scope.GLOBAL, null);
        var combined = new java.util.ArrayList<>(specific);
        combined.addAll(universal);
        return combined.stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void cacheSchema(String databaseAlias, List<TableMetadata> tables) {
        try {
            String json = MAPPER.writeValueAsString(tables);
            SchemaMetadataEntity entity = SchemaMetadataEntity.builder()
                    .databaseAlias(databaseAlias)
                    .schemaJson(json)
                    .tableCount(tables.size())
                    .cachedAt(Instant.now())
                    .cacheTtlMinutes(CACHE_TTL_MINUTES)
                    .build();
            schemaRepository.save(entity);
            log.info("Cached schema for '{}': {} tables", databaseAlias, tables.size());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize schema for '{}': {}", databaseAlias, e.getMessage());
        }
    }

    @Override
    public List<TableMetadata> getCachedSchema(String databaseAlias) {
        return schemaRepository.findById(databaseAlias)
                .map(entity -> {
                    try {
                        return MAPPER.readValue(entity.getSchemaJson(),
                                new TypeReference<List<TableMetadata>>() {});
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialize schema for '{}': {}", databaseAlias, e.getMessage());
                        return List.<TableMetadata>of();
                    }
                })
                .orElse(List.of());
    }

    @Override
    public boolean isSchemaCached(String databaseAlias) {
        return schemaRepository.findById(databaseAlias)
                .map(entity -> {
                    Instant expiry = entity.getCachedAt()
                            .plus(entity.getCacheTtlMinutes(), ChronoUnit.MINUTES);
                    boolean fresh = Instant.now().isBefore(expiry);
                    if (!fresh) {
                        log.debug("Schema cache for '{}' is stale (cached at {})", databaseAlias, entity.getCachedAt());
                    }
                    return fresh;
                })
                .orElse(false);
    }

    // ==================== User Scope ====================

    @Override
    @Transactional
    public void saveUserHabit(String userId, UserHabit habit) {
        UserHabitEntity entity = UserHabitEntity.builder()
                .id(habit.getId() != null ? habit.getId() : UUID.randomUUID().toString())
                .userId(userId)
                .databaseAlias(habit.getDatabaseAlias())
                .tableName(habit.getTableName())
                .queryCount(habit.getQueryCount())
                .queryPattern(habit.getQueryPattern())
                .lastUsedAt(habit.getLastUsedAt() != null ? habit.getLastUsedAt() : Instant.now())
                .firstUsedAt(habit.getFirstUsedAt() != null ? habit.getFirstUsedAt() : Instant.now())
                .build();
        habitRepository.save(entity);
    }

    @Override
    public List<UserHabit> getUserHabits(String userId) {
        return habitRepository.findByUserIdOrderByQueryCountDesc(userId)
                .stream().map(this::habitToDomain).toList();
    }

    @Override
    public List<UserHabit> getUserHabits(String userId, String databaseAlias) {
        return habitRepository.findByUserIdAndDatabaseAlias(userId, databaseAlias)
                .stream().map(this::habitToDomain).toList();
    }

    @Override
    @Transactional
    public void recordTableAccess(String userId, String databaseAlias, String tableName) {
        var existing = habitRepository.findByUserIdAndDatabaseAliasAndTableName(
                userId, databaseAlias, tableName);

        if (existing.isPresent()) {
            var habit = existing.get();
            habit.setQueryCount(habit.getQueryCount() + 1);
            habit.setLastUsedAt(Instant.now());
            habitRepository.save(habit);
        } else {
            habitRepository.save(UserHabitEntity.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(userId)
                    .databaseAlias(databaseAlias)
                    .tableName(tableName)
                    .queryCount(1)
                    .lastUsedAt(Instant.now())
                    .firstUsedAt(Instant.now())
                    .build());
        }
    }

    @Override
    @Transactional
    public void saveUserRule(String userId, BusinessRule rule) {
        BusinessRuleEntity entity = toEntity(rule);
        entity.setScope(BusinessRuleEntity.Scope.USER);
        entity.setUserId(userId);
        ruleRepository.save(entity);
        log.info("Saved user rule for '{}': {}", userId, rule.getRuleText());
    }

    @Override
    public List<BusinessRule> getUserRules(String userId) {
        return ruleRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toDomain).toList();
    }

    // ==================== Maintenance ====================

    @Override
    public String healthCheck() {
        try {
            long ruleCount = ruleRepository.count();
            long habitCount = habitRepository.count();
            long schemaCount = schemaRepository.count();
            return "✅ Knowledge Store: %d rules, %d habits, %d cached schemas".formatted(
                    ruleCount, habitCount, schemaCount);
        } catch (Exception e) {
            return "❌ Knowledge Store error: " + e.getMessage();
        }
    }

    // ==================== Mappers ====================

    private BusinessRuleEntity toEntity(BusinessRule rule) {
        return BusinessRuleEntity.builder()
                .id(rule.getId() != null ? rule.getId() : UUID.randomUUID().toString())
                .ruleText(rule.getRuleText())
                .scope(rule.getScope() == BusinessRule.Scope.GLOBAL
                        ? BusinessRuleEntity.Scope.GLOBAL
                        : BusinessRuleEntity.Scope.USER)
                .userId(rule.getUserId())
                .databaseAlias(rule.getDatabaseAlias())
                .createdAt(rule.getCreatedAt() != null ? rule.getCreatedAt() : Instant.now())
                .applicationCount(rule.getApplicationCount())
                .confidence(rule.getConfidence())
                .build();
    }

    private BusinessRule toDomain(BusinessRuleEntity entity) {
        return BusinessRule.builder()
                .id(entity.getId())
                .ruleText(entity.getRuleText())
                .scope(entity.getScope() == BusinessRuleEntity.Scope.GLOBAL
                        ? BusinessRule.Scope.GLOBAL
                        : BusinessRule.Scope.USER)
                .userId(entity.getUserId())
                .databaseAlias(entity.getDatabaseAlias())
                .createdAt(entity.getCreatedAt())
                .applicationCount(entity.getApplicationCount())
                .confidence(entity.getConfidence())
                .build();
    }

    private UserHabit habitToDomain(UserHabitEntity entity) {
        return UserHabit.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .databaseAlias(entity.getDatabaseAlias())
                .tableName(entity.getTableName())
                .queryCount(entity.getQueryCount())
                .queryPattern(entity.getQueryPattern())
                .lastUsedAt(entity.getLastUsedAt())
                .firstUsedAt(entity.getFirstUsedAt())
                .build();
    }
}
