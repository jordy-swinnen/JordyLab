package dev.jordy.jordylab.mobile.domain.repository;

import dev.jordy.jordylab.mobile.domain.MobileRelease;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MobileReleaseRepository extends JpaRepository<MobileRelease, UUID> {

    /** "Latest" is simply the highest {@code versionCode} — no separate {@code isLatest} flag. */
    Optional<MobileRelease> findTopByOrderByVersionCodeDesc();
}
