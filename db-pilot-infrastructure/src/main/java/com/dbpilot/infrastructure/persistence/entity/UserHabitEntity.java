package com.dbpilot.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity for persisting {@link com.dbpilot.core.model.UserHabit} in H2.
 *
 * @author DB-Pilot
 */
@Entity
@Table(name = "user_habits", indexes = {
        @Index(name = "idx_uh_user", columnList = "userId"),
        @Index(name = "idx_uh_user_db", columnList = "userId, databaseAlias")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_uh_user_db_table",
                columnNames = {"userId", "databaseAlias", "tableName"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserHabitEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 100)
    private String userId;

    @Column(nullable = false, length = 100)
    private String databaseAlias;

    @Column(nullable = false, length = 200)
    private String tableName;

    @Column(nullable = false)
    private int queryCount;

    @Column(length = 500)
    private String queryPattern;

    @Column(nullable = false)
    private Instant lastUsedAt;

    @Column(nullable = false)
    private Instant firstUsedAt;
}
