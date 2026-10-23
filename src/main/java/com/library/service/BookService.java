package com.library.service;

import com.library.dto.BookForm;
import com.library.entity.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BookService {
    Book register(BookForm form, String actor);
    Book update(Long id, BookForm form, String actor);
    Book assignTag(Long id, String rawUid, String actor);
    void deactivate(Long id, String actor);
    void activate(Long id, String actor);
    Book get(Long id);
    Page<Book> search(String keyword, Pageable pageable);
}
