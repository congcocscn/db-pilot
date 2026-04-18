package com.dbpilot.infrastructure.persistence.repository;

import com.dbpilot.infrastructure.persistence.entity.BusinessRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data repository for {@link BusinessRuleEntity}.
 *
 * @author DB-Pilot
 */
@Repository
public interface BusinessRuleRepository extends JpaRepository<BusinessRuleEntity, String> {

    /** Find all GLOBAL scope rules. */
    List<BusinessRuleEntity> findByScopeOrderByCreatedAtDesc(BusinessRuleEntity.Scope scope);

    /** Find GLOBAL rules for a specific database. */
    List<BusinessRuleEntity> findByScopeAndDatabaseAlias(BusinessRuleEntity.Scope scope, String databaseAlias);

    /** Find all rules for a specific user. */
    List<BusinessRuleEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    /** Find user rules for a specific database. */
    List<BusinessRuleEntity> findByUserIdAndDatabaseAlias(String userId, String databaseAlias);
}
