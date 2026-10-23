package com.library.service.impl;

import com.library.dto.MemberForm;
import com.library.entity.LibraryMember;
import com.library.exception.ErrorCode;
import com.library.exception.LibraryException;
import com.library.hardware.UidNormalizer;
import com.library.model.AuditAction;
import com.library.model.TransactionStatus;
import com.library.repository.BookRepository;
import com.library.repository.BorrowTransactionRepository;
import com.library.repository.LibraryMemberRepository;
import com.library.service.AuditService;
import com.library.service.MemberService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Service
public class MemberServiceImpl implements MemberService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();

    private final LibraryMemberRepository memberRepo;
    private final BookRepository bookRepo;
    private final BorrowTransactionRepository txRepo;
    private final AuditService audit;

    public MemberServiceImpl(LibraryMemberRepository memberRepo, BookRepository bookRepo,
                             BorrowTransactionRepository txRepo, AuditService audit) {
        this.memberRepo = memberRepo;
        this.bookRepo = bookRepo;
        this.txRepo = txRepo;
        this.audit = audit;
    }

    @Override
    @Transactional
    public LibraryMember register(MemberForm form, String actor) {
        String code = blank(form.getMemberCode()) ? generateCode() : form.getMemberCode().trim().toUpperCase();
        if (memberRepo.existsByMemberCode(code)) {
            throw LibraryException.duplicate("Member code " + code + " is already in use.");
        }
        checkEmailFree(form.getEmail(), null);
        String uid = normUid(form.getRfidCardUid());
        ensureUidFree(uid, null);

        LibraryMember m = new LibraryMember();
        m.setMemberCode(code);
        apply(m, form);
        m.setRfidCardUid(uid);
        m = saveChecked(m);
        audit.record(AuditAction.MEMBER_REGISTERED, actor, "LibraryMember", m.getId(), m.getMemberCode() + " " + m.getFullName());
        if (uid != null) {
            audit.record(AuditAction.MEMBER_CARD_ASSIGNED, actor, "LibraryMember", m.getId(), "Card " + uid);
        }
        return m;
    }

    @Override
    @Transactional
    public LibraryMember update(Long id, MemberForm form, String actor) {
        LibraryMember m = get(id);
        String code = blank(form.getMemberCode()) ? m.getMemberCode() : form.getMemberCode().trim().toUpperCase();
        if (!code.equals(m.getMemberCode()) && memberRepo.existsByMemberCodeAndIdNot(code, id)) {
            throw LibraryException.duplicate("Member code " + code + " is already in use.");
        }
        checkEmailFree(form.getEmail(), id);
        String uid = normUid(form.getRfidCardUid());
        boolean uidChanged = (uid == null ? m.getRfidCardUid() != null : !uid.equals(m.getRfidCardUid()));
        if (uidChanged) ensureUidFree(uid, id);

        m.setMemberCode(code);
        apply(m, form);
        if (uidChanged) m.setRfidCardUid(uid);
        m = saveChecked(m);
        audit.record(AuditAction.MEMBER_UPDATED, actor, "LibraryMember", m.getId(), m.getMemberCode());
        if (uidChanged) {
            audit.record(AuditAction.MEMBER_CARD_ASSIGNED, actor, "LibraryMember", m.getId(), "Card " + uid);
        }
        return m;
    }

    @Override
    @Transactional
    public LibraryMember assignCard(Long id, String rawUid, String actor) {
        String uid = normUid(rawUid);
        if (uid == null) {
            throw new LibraryException(ErrorCode.INVALID_UID, "A card UID is required.");
        }
        LibraryMember m = get(id);
        ensureUidFree(uid, id);
        m.setRfidCardUid(uid);
        m = saveChecked(m);
        audit.record(AuditAction.MEMBER_CARD_ASSIGNED, actor, "LibraryMember", m.getId(), "Card " + uid);
        return m;
    }

    @Override
    @Transactional
    public void deactivate(Long id, String actor) {
        LibraryMember m = memberRepo.findByIdForUpdate(id).orElseThrow(() -> LibraryException.notFound("Member"));
        if (txRepo.countByMemberIdAndStatus(id, TransactionStatus.BORROWED) > 0) {
            throw new LibraryException(ErrorCode.INVALID_STATE,
                    "This member still has borrowed books. Return them before deactivating.");
        }
        m.setActive(false);
        memberRepo.save(m);
        audit.record(AuditAction.MEMBER_DEACTIVATED, actor, "LibraryMember", id, m.getMemberCode());
    }

    @Override
    @Transactional
    public void activate(Long id, String actor) {
        LibraryMember m = get(id);
        m.setActive(true);
        memberRepo.save(m);
        audit.record(AuditAction.MEMBER_ACTIVATED, actor, "LibraryMember", id, m.getMemberCode());
    }

    @Override
    @Transactional(readOnly = true)
    public LibraryMember get(Long id) {
        return memberRepo.findById(id).orElseThrow(() -> LibraryException.notFound("Member"));
    }

    @Override
    @Transactional(readOnly = true)
    public LibraryMember getByCardUid(String normalizedUid) {
        return memberRepo.findByRfidCardUid(normalizedUid).orElseThrow(() -> LibraryException.notFound("Member"));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LibraryMember> search(String keyword, Pageable pageable) {
        return memberRepo.search(keyword == null ? "" : keyword.trim(), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LibraryMember> blacklisted(Pageable pageable) {
        return memberRepo.findByBlacklistedTrue(pageable);
    }

    // ---------- helpers ----------

    private void apply(LibraryMember m, MemberForm f) {
        m.setFullName(f.getFullName().trim());
        m.setEmail(blank(f.getEmail()) ? null : f.getEmail().trim());
        m.setPhone(blank(f.getPhone()) ? null : f.getPhone().trim());
        m.setAddress(blank(f.getAddress()) ? null : f.getAddress().trim());
    }

    private String normUid(String raw) {
        try {
            return UidNormalizer.normalizeOrNull(raw);
        } catch (IllegalArgumentException e) {
            throw new LibraryException(ErrorCode.INVALID_UID, e.getMessage());
        }
    }

    /** A UID may identify a member OR a book, never both. */
    private void ensureUidFree(String uid, Long selfId) {
        if (uid == null) return;
        boolean usedByMember = selfId == null
                ? memberRepo.existsByRfidCardUid(uid)
                : memberRepo.existsByRfidCardUidAndIdNot(uid, selfId);
        if (usedByMember) {
            throw LibraryException.duplicate("This card is already assigned to another member.");
        }
        if (bookRepo.existsByRfidTagUid(uid)) {
            throw LibraryException.duplicate("This UID is already registered as a book tag.");
        }
    }

    private void checkEmailFree(String email, Long selfId) {
        if (blank(email)) return;
        boolean taken = selfId == null
                ? memberRepo.existsByEmailIgnoreCase(email.trim())
                : memberRepo.existsByEmailIgnoreCaseAndIdNot(email.trim(), selfId);
        if (taken) throw LibraryException.duplicate("A member with this email already exists.");
    }

    private LibraryMember saveChecked(LibraryMember m) {
        try {
            return memberRepo.saveAndFlush(m);
        } catch (DataIntegrityViolationException e) {
            throw new LibraryException(ErrorCode.DUPLICATE,
                    "Member code, email or card UID already exists.", e);
        }
    }

    private String generateCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder("MBR-");
            for (int i = 0; i < 6; i++) sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            if (!memberRepo.existsByMemberCode(sb.toString())) return sb.toString();
        }
        throw new LibraryException(ErrorCode.INVALID_STATE, "Could not generate a unique member code. Try again.");
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
