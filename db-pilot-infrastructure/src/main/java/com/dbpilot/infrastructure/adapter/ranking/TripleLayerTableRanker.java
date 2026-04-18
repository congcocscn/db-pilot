package com.dbpilot.infrastructure.adapter.ranking;

import com.dbpilot.core.model.ForeignKeyRelation;
import com.dbpilot.core.model.RankedTable;
import com.dbpilot.core.model.TableMetadata;
import com.dbpilot.core.model.UserHabit;
import com.dbpilot.core.port.out.TableRanker;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Triple-layer {@link TableRanker} implementation.
 *
 * <p>Ranking formula: {@code score = α×semantic + β×relation + γ×frequency}</p>
 * <ul>
 *   <li><strong>Layer 1 — Semantic Match (α=0.5):</strong> Keyword/embedding similarity
 *       between user intent and table/column names.</li>
 *   <li><strong>Layer 2 — Relation Expansion (β=0.3):</strong> Tables with FK links
 *       to semantically matched tables get boosted.</li>
 *   <li><strong>Layer 3 — Frequency Weighting (γ=0.2):</strong> Tables frequently
 *       queried by this user are prioritized.</li>
 * </ul>
 *
 * @author DB-Pilot
 */
@Slf4j
public class TripleLayerTableRanker implements TableRanker {

    /** Weighting coefficients (configurable). */
    private final double alpha; // Semantic weight
    private final double beta;  // Relation weight
    private final double gamma; // Frequency weight

    /**
     * Creates a ranker with default weights: α=0.5, β=0.3, γ=0.2.
     */
    public TripleLayerTableRanker() {
        this(0.5, 0.3, 0.2);
    }

    /**
     * Creates a ranker with custom weights.
     *
     * @param alpha semantic match weight
     * @param beta  relation expansion weight
     * @param gamma frequency weighting
     */
    public TripleLayerTableRanker(double alpha, double beta, double gamma) {
        this.alpha = alpha;
        this.beta = beta;
        this.gamma = gamma;
    }

    @Override
    public List<RankedTable> rank(String userIntent,
                                   List<TableMetadata> allTables,
                                   List<UserHabit> userHabits,
                                   int maxResults) {

        log.debug("Ranking {} tables for intent: '{}'", allTables.size(), userIntent);

        // Extract keywords from user intent
        Set<String> keywords = extractKeywords(userIntent);

        // Build frequency map from user habits
        Map<String, Integer> frequencyMap = buildFrequencyMap(userHabits);
        int maxFrequency = frequencyMap.values().stream().mapToInt(Integer::intValue).max().orElse(1);

        // Build FK adjacency map for relation expansion
        Map<String, Set<String>> fkAdjacency = buildFkAdjacency(allTables);

        // Layer 1: Compute semantic scores
        Map<String, Double> semanticScores = new HashMap<>();
        for (TableMetadata table : allTables) {
            double score = computeSemanticScore(table, keywords);
            semanticScores.put(table.getTableName(), score);
        }

        // Layer 2: Compute relation scores (boost tables linked to high-semantic tables)
        Map<String, Double> relationScores = new HashMap<>();
        for (TableMetadata table : allTables) {
            double score = computeRelationScore(table.getTableName(), semanticScores, fkAdjacency);
            relationScores.put(table.getTableName(), score);
        }

        // Layer 3: Compute frequency scores
        Map<String, Double> frequencyScores = new HashMap<>();
        for (TableMetadata table : allTables) {
            int freq = frequencyMap.getOrDefault(table.getTableName(), 0);
            frequencyScores.put(table.getTableName(), (double) freq / maxFrequency);
        }

        // Combine scores and rank
        List<RankedTable> ranked = allTables.stream()
                .map(table -> {
                    String name = table.getTableName();
                    double semantic = semanticScores.getOrDefault(name, 0.0);
                    double relation = relationScores.getOrDefault(name, 0.0);
                    double frequency = frequencyScores.getOrDefault(name, 0.0);
                    double composite = alpha * semantic + beta * relation + gamma * frequency;

                    return RankedTable.builder()
                            .table(table)
                            .score(composite)
                            .semanticScore(semantic)
                            .relationScore(relation)
                            .frequencyScore(frequency)
                            .rankingReason(buildReason(semantic, relation, frequency))
                            .build();
                })
                .sorted(Comparator.comparingDouble(RankedTable::getScore).reversed())
                .limit(maxResults)
                .collect(Collectors.toList());

        log.debug("Top ranked: {}", ranked.stream()
                .limit(3)
                .map(r -> "%s(%.3f)".formatted(r.getTable().getTableName(), r.getScore()))
                .collect(Collectors.joining(", ")));

        return ranked;
    }

    /**
     * Computes semantic similarity between user keywords and table/column names.
     * Phase 1: TF-IDF-like keyword matching.
     * Phase 2: Will be upgraded to embedding-based similarity.
     */
    private double computeSemanticScore(TableMetadata table, Set<String> keywords) {
        if (keywords.isEmpty()) return 0.0;

        int matchCount = 0;
        int totalFields = 1; // table name itself

        // Check table name
        if (containsAnyKeyword(table.getTableName(), keywords)) matchCount++;

        // Check description
        if (table.getDescription() != null && containsAnyKeyword(table.getDescription(), keywords)) {
            matchCount++;
        }

        // Check column names
        if (table.getColumns() != null) {
            totalFields += table.getColumns().size();
            for (var col : table.getColumns()) {
                if (containsAnyKeyword(col.getName(), keywords)) matchCount++;
                if (col.getDescription() != null && containsAnyKeyword(col.getDescription(), keywords)) matchCount++;
            }
        }

        return Math.min(1.0, (double) matchCount / Math.max(1, keywords.size()));
    }

    /**
     * Computes relation expansion score: tables linked via FK to highly-ranked tables.
     */
    private double computeRelationScore(String tableName,
                                         Map<String, Double> semanticScores,
                                         Map<String, Set<String>> fkAdjacency) {

        Set<String> neighbors = fkAdjacency.getOrDefault(tableName, Set.of());
        if (neighbors.isEmpty()) return 0.0;

        // Max semantic score among FK neighbors
        return neighbors.stream()
                .mapToDouble(n -> semanticScores.getOrDefault(n, 0.0))
                .max()
                .orElse(0.0);
    }

    /**
     * Builds a bidirectional FK adjacency map.
     */
    private Map<String, Set<String>> buildFkAdjacency(List<TableMetadata> tables) {
        Map<String, Set<String>> adj = new HashMap<>();
        for (TableMetadata table : tables) {
            if (table.getForeignKeys() == null) continue;
            for (ForeignKeyRelation fk : table.getForeignKeys()) {
                adj.computeIfAbsent(fk.getSourceTable(), k -> new HashSet<>()).add(fk.getTargetTable());
                adj.computeIfAbsent(fk.getTargetTable(), k -> new HashSet<>()).add(fk.getSourceTable());
            }
        }
        return adj;
    }

    /**
     * Builds a frequency map from user habits.
     */
    private Map<String, Integer> buildFrequencyMap(List<UserHabit> habits) {
        if (habits == null) return Map.of();
        return habits.stream()
                .collect(Collectors.toMap(UserHabit::getTableName, UserHabit::getQueryCount,
                        Integer::sum));
    }

    /**
     * Extracts keywords from user intent (simple tokenization + normalization).
     */
    private Set<String> extractKeywords(String intent) {
        if (intent == null || intent.isBlank()) return Set.of();

        // Remove stop words and normalize
        Set<String> stopWords = Set.of(
                "show", "me", "all", "the", "from", "in", "with", "where",
                "get", "find", "list", "select", "and", "or", "of", "for",
                "that", "which", "have", "has", "are", "is", "was", "were",
                "a", "an", "to", "by", "on", "at", "it", "do", "how", "many"
        );

        return Arrays.stream(intent.toLowerCase().split("[\\s,;.!?()]+"))
                .filter(w -> w.length() > 1)
                .filter(w -> !stopWords.contains(w))
                .collect(Collectors.toSet());
    }

    /**
     * Checks if a text contains any of the keywords (case-insensitive).
     */
    private boolean containsAnyKeyword(String text, Set<String> keywords) {
        String lower = text.toLowerCase().replace('_', ' ');
        return keywords.stream().anyMatch(lower::contains);
    }

    private String buildReason(double semantic, double relation, double frequency) {
        List<String> reasons = new ArrayList<>();
        if (semantic > 0.3) reasons.add("semantic match");
        if (relation > 0.3) reasons.add("FK relation");
        if (frequency > 0.3) reasons.add("frequently used");
        return reasons.isEmpty() ? "low relevance" : String.join(" + ", reasons);
    }
}
