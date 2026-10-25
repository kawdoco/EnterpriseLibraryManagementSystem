package com.library.service;

import com.library.config.LibraryProperties;
import com.library.exception.ErrorCode;
import com.library.exception.LibraryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CoverImageServiceTest {

    @TempDir
    Path tmp;

    private CoverImageService service;

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};

    @BeforeEach
    void setUp() {
        LibraryProperties props = new LibraryProperties();
        props.getUploads().setDir(tmp.toString());
        props.getUploads().setMaxCoverMb(1);
        service = new CoverImageService(props);
    }

    @Test
    void nothingUploadedMeansNoPicture() {
        assertNull(service.store(null));
        assertNull(service.store(new MockMultipartFile("coverImage", new byte[0])));
    }

    @Test
    void storesPngUnderRandomName() throws Exception {
        String name = service.store(new MockMultipartFile("coverImage", "../../evil.exe", "text/plain", PNG));
        assertTrue(name.matches("[0-9a-f]{32}\\.png"), name);
        assertTrue(Files.exists(service.directory().resolve(name)));
    }

    @Test
    void detectsTypeFromBytesNotFromName() {
        String name = service.store(new MockMultipartFile("coverImage", "photo.png", "image/png", JPG));
        assertTrue(name.endsWith(".jpg"));
    }

    @Test
    void rejectsNonImages() {
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes();
        LibraryException e = assertThrows(LibraryException.class,
                () -> service.store(new MockMultipartFile("coverImage", "x.svg", "image/svg+xml", svg)));
        assertEquals(ErrorCode.INVALID_IMAGE, e.getCode());
    }

    @Test
    void rejectsTooLarge() {
        byte[] big = new byte[1024 * 1024 + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        assertThrows(LibraryException.class,
                () -> service.store(new MockMultipartFile("coverImage", "big.png", "image/png", big)));
    }

    @Test
    void deleteOnlyTouchesGeneratedNames() throws Exception {
        Path other = tmp.resolve("covers").resolve("keep.txt");
        Files.createDirectories(other.getParent());
        Files.writeString(other, "x");
        service.delete("keep.txt");
        service.delete("../covers/keep.txt");
        assertTrue(Files.exists(other));

        String name = service.store(new MockMultipartFile("coverImage", "a.png", "image/png", PNG));
        service.delete(name);
        assertFalse(Files.exists(service.directory().resolve(name)));
    }
}
