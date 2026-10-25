package com.library.hardware;

import com.library.dto.ReaderStatus;

public record ReaderStatusChangedEvent(ReaderStatus status) {
}
