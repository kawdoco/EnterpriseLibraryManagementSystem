package com.library.service;

import com.library.dto.MemberForm;
import com.library.entity.LibraryMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MemberService {
    LibraryMember register(MemberForm form, String actor);
    LibraryMember update(Long id, MemberForm form, String actor);
    LibraryMember assignCard(Long id, String rawUid, String actor);
    void deactivate(Long id, String actor);
    void activate(Long id, String actor);
    LibraryMember get(Long id);
    LibraryMember getByCardUid(String normalizedUid);
    Page<LibraryMember> search(String keyword, Pageable pageable);
    Page<LibraryMember> blacklisted(Pageable pageable);
}
