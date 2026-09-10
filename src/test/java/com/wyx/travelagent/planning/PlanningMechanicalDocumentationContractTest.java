package com.wyx.travelagent.planning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 对 planning 新增公开 API 执行可机械验证的源码文档契约。
 * 该契约只检查 Javadoc 附着、签名标签、可识别边界标记、枚举值注释和禁用公开类型，
 * 不自动证明全部业务语义或 helper 间接抛出的运行时异常；后两者由逐文件人工审查负责。
 */
class PlanningMechanicalDocumentationContractTest {
    private static final Set<String> PRE_TASK_ELEVEN_MAPPER_FILES = Set.of(
            "AiRecommendationsMapper.java", "UserMapper.java");

    @Test
    void recursivelyDiscoversFutureProductionJavaSourcesAndExcludesMetadataFiles(
            @TempDir Path planningRoot) throws IOException {
        Files.writeString(planningRoot.resolve("RootApi.java"), "class RootApi {}");
        Path nested = Files.createDirectories(planningRoot.resolve("memory/internal"));
        Files.writeString(nested.resolve("FutureMemoryService.java"), "class FutureMemoryService {}");
        Files.writeString(planningRoot.resolve("package-info.java"), "package synthetic;");
        Files.writeString(nested.resolve("module-info.java"), "module synthetic {}");
        Files.writeString(nested.resolve("notes.txt"), "not Java source");

        assertThat(discoverProductionJavaSources(planningRoot))
                .extracting(path -> planningRoot.relativize(path).toString().replace('\\', '/'))
                .containsExactly("RootApi.java", "memory/internal/FutureMemoryService.java");
    }

    @Test
    void publicPlanningApisSatisfyTheMechanicallyVerifiableDocumentationContract() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : discoverProductionJavaSources(
                Path.of("src/main/java/com/wyx/travelagent/planning"))) {
            violations.addAll(PlanningSourceMechanicalDocumentationVerifier.verifyPath(source));
        }
        for (Path source : discoverTaskElevenMapperSources(
                Path.of("src/main/java/com/wyx/travelagent/mapper"))) {
            violations.addAll(PlanningSourceMechanicalDocumentationVerifier.verifyPath(source));
        }

        assertThat(violations)
                .as("机械契约只覆盖文档附着、签名标签、可识别边界、枚举注释和禁用类型；其余业务语义须人工审查")
                .isEmpty();
    }

    @Test
    void mapperDiscoveryExemptsOnlyFilesThatPredateTaskEleven(@TempDir Path mapperRoot) throws IOException {
        for (String legacy : PRE_TASK_ELEVEN_MAPPER_FILES) Files.writeString(mapperRoot.resolve(legacy), "class Legacy {}");
        Files.writeString(mapperRoot.resolve("UserTravelPreferenceMapper.java"), "class Current {}");
        Files.writeString(mapperRoot.resolve("FuturePlanningMapper.java"), "class Future {}");
        assertThat(discoverTaskElevenMapperSources(mapperRoot))
                .extracting(path -> path.getFileName().toString())
                .containsExactly("FuturePlanningMapper.java", "UserTravelPreferenceMapper.java");
    }

    private static List<Path> discoverTaskElevenMapperSources(Path mapperRoot) throws IOException {
        return discoverProductionJavaSources(mapperRoot).stream()
                .filter(path -> !PRE_TASK_ELEVEN_MAPPER_FILES.contains(path.getFileName().toString()))
                .toList();
    }

    private static List<Path> discoverProductionJavaSources(Path planningRoot) throws IOException {
        try (var sources = Files.walk(planningRoot)) {
            return sources
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                    .filter(path -> !path.getFileName().toString().equals("module-info.java"))
                    .sorted(Comparator.comparing(path -> planningRoot.relativize(path)
                            .toString().replace('\\', '/')))
                    .toList();
        }
    }
}
