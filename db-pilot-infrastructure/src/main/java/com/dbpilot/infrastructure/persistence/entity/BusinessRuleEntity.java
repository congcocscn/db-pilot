package com.dbpilot.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity for persisting {@link com.dbpilot.core.model.BusinessRule} in H2.
 *
 * @author DB-Pilot
 */
@Entity
@Table(name = "business_rules", indexes = {
        @Index(name = "idx_br_scope", columnList = "scope"),
        @Index(name = "idx_br_user", columnList = "userId"),
        @Index(name = "idx_br_db", columnList = "databaseAlias")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessRuleEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 1000)
    private String ruleText;

    @Column(nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private Scope scope;

    @Column(length = 100)
    private String userId;

    @Column(length = 100)
    private String databaseAlias;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private int applicationCount;

    @Column(nullable = false)
    private double confidence;

    public enum Scope {
        GLOBAL, USER
    }
}
