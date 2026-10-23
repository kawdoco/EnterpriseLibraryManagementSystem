package com.library.service;

import com.library.dto.BorrowResult;
import com.library.dto.ReturnResult;

public interface BorrowService {

    /** Both member and book must already be identified. Never creates a loan otherwise. */
    BorrowResult borrow(Long memberId, String bookTagUid, String actor);

    /** Uses the book's active transaction to find the borrower. */
    ReturnResult returnBook(String bookTagUid, String actor);
}
