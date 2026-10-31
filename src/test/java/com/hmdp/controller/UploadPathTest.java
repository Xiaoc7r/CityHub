package com.hmdp.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class UploadPathTest {
    @TempDir Path root;

    @Test void resolvesGeneratedUrlsInsideUploadRoot() throws Exception {
        String name = "blogs/0/15/123e4567-e89b-12d3-a456-426614174000.png";
        Path expected = root.resolve(name).toFile().getCanonicalFile().toPath();
        assertEquals(expected, UploadController.resolveBlogImage(root.toFile(), name).toPath());
        assertEquals(expected, UploadController.resolveBlogImage(root.toFile(), "/" + name).toPath());
    }

    @Test void rejectsTraversalAbsolutePathsAndDirectories() {
        for (String name : new String[]{null, "", "../outside.txt", "/etc/passwd", "C:\\outside.txt",
                "blogs/../../outside.txt", "blogs\\..\\outside.txt", "blogs/0/1", "//server/share",
                "/blogs/0/1/123e4567-e89b-12d3-a456-426614174000.png:stream"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> UploadController.resolveBlogImage(root.toFile(), name), String.valueOf(name));
        }
    }
}
