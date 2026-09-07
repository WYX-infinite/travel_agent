package com.wyx.travelagent.knowledge.index;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 任务九内部的原子移动边界，便于测试文件系统不支持原子替换的场景。 */
@FunctionalInterface
interface AtomicIndexFileMover {
    void move(Path temporaryFile, Path formalFile) throws IOException;

    static AtomicIndexFileMover nio() {
        return (temporaryFile, formalFile) -> Files.move(
                temporaryFile,
                formalFile,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }
}
