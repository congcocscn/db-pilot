package com.dbpilot.infrastructure.persistence.repository;

import com.dbpilot.infrastructure.persistence.entity.UserHabitEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link UserHabitEntity}.
 *
 * @author DB-Pilot
 */
@Repository
public interface UserHabitRepository extends JpaRepository<UserHabitEntity, String> {

    /** Find all habits for a user. */
    List<UserHabitEntity> findByUserIdOrderByQueryCountDesc(String userId);

    /** Find habits for a user on a specific database. */
    List<UserHabitEntity> findByUserIdAndDatabaseAlias(String userId, String databaseAlias);

    /** Find a specific habit by user + database + table. */
    Optional<UserHabitEntity> findByUserIdAndDatabaseAliasAndTableName(
            String userId, String databaseAlias, String tableName);
}
