package dev.jordy.jordylab.gamecatalog.domain.repository;

import dev.jordy.jordylab.gamecatalog.domain.SyncReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SyncReportRepository extends JpaRepository<SyncReport, UUID> {
}
