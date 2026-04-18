package com.dbpilot.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity for caching introspected schema metadata in H2.
 *
 * <p>The full schema is stored as a JSON blob (compact YAML or serialized
 * {@link com.dbpilot.core.model.TableMetadata} list).</p>
 *
 * @author DB-Pilot
 */
@Entity
@Table(name = "schema_metadata", indexes = {
        @Index(name = "idx_sm_alias", columnList = "databaseAlias")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchemaMetadataEntity {

    @Id
    @Column(length = 100)
    private String databaseAlias;

    /** JSON-serialized list of TableMetadata. */
    @Column(nullable = false, columnDefinition = "CLOB")
    private String schemaJson;

    /** Number of tables in the cached schema. */
    @Column(nullable = false)
    private int tableCount;

    /** When the schema was last introspected. */
    @Column(nullable = false)
    private Instant cachedAt;

    /** Schema cache TTL — stale after this duration. */
    @Column(nullable = false)
    private long cacheTtlMinutes;
}
