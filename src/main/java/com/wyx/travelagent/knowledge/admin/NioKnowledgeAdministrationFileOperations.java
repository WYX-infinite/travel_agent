package com.wyx.travelagent.knowledge.admin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 只执行单文件原子移动与删除的 NIO 实现；不提供递归操作。 */
final class NioKnowledgeAdministrationFileOperations
        implements KnowledgeAdministrationFileOperations {
    @Override
    public void createDirectories(Path path) throws IOException {
        Files.createDirectories(path);
    }

    @Override
    public boolean exists(Path path) {
        return Files.exists(path);
    }

    @Override
    public boolean isRegularFile(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    public void moveAtomically(Path source, Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    }

    @Override
    public void deleteIfExists(Path path) throws IOException {
        Files.deleteIfExists(path);
    }
}
