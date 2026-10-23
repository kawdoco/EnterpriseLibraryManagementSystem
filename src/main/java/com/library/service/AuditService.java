package com.library.service;

import com.library.entity.AuditLog;
import com.library.model.AuditAction;
import com.library.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** Joins the caller's transaction so an audit row never survives a rolled-back action. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuditAction action, String actor, String entityType, Long entityId, String details) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setActor(actor == null || actor.isBlank() ? "SYSTEM" : actor);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        if (details != null) {
            log.setDetails(details.length() > 500 ? details.substring(0, 500) : details);
        }
        repository.save(log);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> recent(Pageable pageable) {
        return repository.findAllByOrderByCreatedAtDesc(pageable);
    }
}
