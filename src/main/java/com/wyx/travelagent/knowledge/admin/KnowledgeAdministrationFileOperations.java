package com.wyx.travelagent.knowledge.admin;

import java.io.IOException;
import java.nio.file.Path;

/** 受控单文件隔离删除所需的最小文件系统边界。 */
interface KnowledgeAdministrationFileOperations {
    void createDirectories(Path path) throws IOException;

    boolean exists(Path path);

    boolean isRegularFile(Path path);

    void moveAtomically(Path source, Path target) throws IOException;

    void deleteIfExists(Path path) throws IOException;
}
