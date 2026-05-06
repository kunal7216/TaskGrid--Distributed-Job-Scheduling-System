package com.kunal.jobscheduler.repository;

import com.kunal.jobscheduler.entity.LeaderLock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaderLockRepository extends JpaRepository<LeaderLock, String> {
}
