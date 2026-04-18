package com.dbpilot.infrastructure.persistence.repository;

import com.dbpilot.infrastructure.persistence.entity.SchemaMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link SchemaMetadataEntity}.
 *
 * @author DB-Pilot
 */
@Repository
public interface SchemaMetadataRepository extends JpaRepository<SchemaMetadataEntity, String> {
}
