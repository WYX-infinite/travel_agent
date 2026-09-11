package com.wyx.travelagent.planning;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.doctree.ThrowsTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;

import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 使用 JDK AST 对 planning 公开源码执行有限、可重复的机械文档检查。
 * 仅验证 Javadoc 附着、签名标签、可可靠识别的边界标记、枚举值中文注释以及禁用的公开类型名。
 * 本验证器不证明描述的业务语义真实，也不推断 helper 调用间接抛出的运行时异常；这些内容必须逐文件人工审查。
 */
final class PlanningSourceMechanicalDocumentationVerifier {
    private static final List<BoundaryMarker> TYPE_BOUNDARY_MARKERS = List.of(
            marker("业务目的", "业务(?:问题|目的|目标)|解决"),
            marker("输入", "输入|接收|给定"),
            marker("输出", "输出|返回|产生"),
            marker("外部访问", externalAccessPattern()),
            marker("认证", authenticationPattern()),
            marker("任务边界", "任务\\s*(?:十|10|十二|12|十三|13)|第(?:十|十二|十三)项任务"),
            marker("非职责", "不(?:负责|承担)"));
    private static final Pattern CHINESE_TEXT = Pattern.compile("[\\p{IsHan}]");
    private static final Pattern EXTERNAL_ACCESS_BOUNDARY = Pattern.compile(externalAccessPattern());
    private static final Pattern AUTHENTICATION_BOUNDARY = Pattern.compile(authenticationPattern());
    private static final Pattern REFERENCE_PARAMETER_SEMANTICS =
            Pattern.compile("null|空|缺失|显式|非空|可选|必填|必须提供", Pattern.CASE_INSENSITIVE);
    private static final Pattern ESCAPED_UNICODE = Pattern.compile("\\\\u([0-9a-fA-F]{4})");

    private PlanningSourceMechanicalDocumentationVerifier() {
    }

    static List<String> verifyPath(Path sourcePath) throws IOException {
        return verifySource(sourcePath.getFileName().toString(), Files.readString(sourcePath));
    }

    static List<String> verifySource(String fileName, String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return List.of(fileName + ": 当前运行时没有 JDK compiler");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<String> violations = new ArrayList<>();
        JavaFileObject sourceObject = new StringJavaFileObject(fileName, source);
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics, Locale.ROOT, null)) {
            JavacTask task = (JavacTask) compiler.getTask(
                    null, fileManager, diagnostics, List.of("-proc:none", "--release", "21"),
                    null, List.of(sourceObject));
            Iterable<? extends CompilationUnitTree> units = task.parse();
            DocumentationScanner scanner = new DocumentationScanner(
                    fileName, DocTrees.instance(task), violations);
            for (CompilationUnitTree unit : units) {
                scanner.scan(unit, null);
            }
            if (fileName.equals("RequirementValidator.java") && !scanner.hasRequirementValidationError()) {
                violations.add(fileName + ": 缺少 public RequirementValidationError 类型");
            }
        } catch (IOException exception) {
            violations.add(fileName + ": 源码解析失败");
        }
        diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                .forEach(diagnostic -> violations.add(fileName + ": 编译器解析错误 line "
                        + diagnostic.getLineNumber()));
        return List.copyOf(violations);
    }

    private static final class DocumentationScanner extends TreePathScanner<Void, Void> {
        private final String fileName;
        private final DocTrees docTrees;
        private final List<String> violations;
        private boolean requirementValidationError;

        private DocumentationScanner(String fileName, DocTrees docTrees, List<String> violations) {
            this.fileName = fileName;
            this.docTrees = docTrees;
            this.violations = violations;
        }

        @Override
        public Void visitClass(ClassTree node, Void unused) {
            if (isCheckedType(node) && isPublic(node.getModifiers().getFlags())) {
                String typeName = node.getSimpleName().toString();
                if (typeName.equals("RequirementValidationError")) {
                    requirementValidationError = true;
                }
                DocCommentTree documentation = docTrees.getDocCommentTree(getCurrentPath());
                if (documentation == null) {
                    violations.add(fileName + ": " + typeName + " 缺少类型 Javadoc");
                } else {
                    verifyTypeDocumentation(node, typeName, documentation);
                }
            }
            return super.visitClass(node, unused);
        }

        @Override
        public Void visitMethod(MethodTree node, Void unused) {
            Tree parent = getCurrentPath().getParentPath().getLeaf();
            if (parent instanceof ClassTree enclosing
                    && isPublic(enclosing.getModifiers().getFlags())
                    && isPublic(node.getModifiers().getFlags())) {
                String methodName = node.getName().toString();
                DocCommentTree documentation = docTrees.getDocCommentTree(getCurrentPath());
                if (documentation == null) {
                    violations.add(fileName + ": " + methodName + " 缺少方法 Javadoc");
                } else {
                    verifyMethodDocumentation(node, documentation);
                }
            }
            return super.visitMethod(node, unused);
        }

        @Override
        public Void visitVariable(VariableTree node, Void unused) {
            Tree parent = getCurrentPath().getParentPath().getLeaf();
            if (parent instanceof ClassTree type
                    && isPublic(type.getModifiers().getFlags())
                    && isEnumConstant(type, node)) {
                DocCommentTree documentation = docTrees.getDocCommentTree(getCurrentPath());
                String constantName = node.getName().toString();
                if (documentation == null
                        || !CHINESE_TEXT.matcher(decodeUnicodeEscapes(documentation.toString())).find()) {
                    violations.add(fileName + ": " + constantName + " 缺少附着中文枚举常量 Javadoc");
                }
            }
            return super.visitVariable(node, unused);
        }

        private void verifyTypeDocumentation(
                ClassTree type, String typeName, DocCommentTree documentation) {
            String text = decodeUnicodeEscapes(documentation.toString());
            for (BoundaryMarker marker : TYPE_BOUNDARY_MARKERS) {
                if (!marker.pattern().matcher(text).find()) {
                    violations.add(fileName + ": " + typeName
                            + " 类型 Javadoc 缺少可识别边界语义 " + marker.label());
                }
            }
            verifyTypeParameterTags(type, typeName, documentation);
        }

        private void verifyMethodDocumentation(MethodTree method, DocCommentTree documentation) {
            String text = decodeUnicodeEscapes(documentation.toString());
            if (!EXTERNAL_ACCESS_BOUNDARY.matcher(text).find()) {
                violations.add(fileName + ": " + method.getName() + " Javadoc 缺少外部访问边界");
            }
            if (!AUTHENTICATION_BOUNDARY.matcher(text).find()) {
                violations.add(fileName + ": " + method.getName() + " Javadoc 缺少认证边界");
            }
            verifyParameterTags(method, documentation);
            verifyReferenceParameterSemantics(method, documentation);
            verifyReturnTag(method, documentation);
            verifyDeclaredThrowsClauseTags(method, documentation);
        }

        private void verifyParameterTags(MethodTree method, DocCommentTree documentation) {
            Tree parent = getCurrentPath().getParentPath().getLeaf();
            if (method.getReturnType() == null && parent.getKind() == Tree.Kind.RECORD) {
                // javac 为紧凑构造器补出 record 组件参数；组件 @param 属于 record 类级 Javadoc，避免重复要求。
                return;
            }
            Set<String> expectedParameters = new LinkedHashSet<>();
            method.getParameters().forEach(parameter -> expectedParameters.add(parameter.getName().toString()));
            Set<String> expectedTypeParameters = new LinkedHashSet<>();
            method.getTypeParameters().forEach(parameter -> expectedTypeParameters.add(parameter.getName().toString()));

            List<String> documentedParameters = new ArrayList<>();
            List<String> documentedTypeParameters = new ArrayList<>();
            documentation.getBlockTags().stream()
                    .filter(tag -> tag.getKind() == DocTree.Kind.PARAM)
                    .map(ParamTree.class::cast)
                    .forEach(tag -> (tag.isTypeParameter()
                            ? documentedTypeParameters : documentedParameters).add(tag.getName().toString()));
            if (!matchesSignature(expectedParameters, documentedParameters)) {
                violations.add(fileName + ": " + method.getName() + " @param 与参数签名不一致");
            }
            if (!matchesSignature(expectedTypeParameters, documentedTypeParameters)) {
                violations.add(fileName + ": " + method.getName() + " 类型参数 @param 与签名不一致");
            }
        }

        private void verifyTypeParameterTags(
                ClassTree type, String typeName, DocCommentTree documentation) {
            Set<String> expectedParameters = new LinkedHashSet<>();
            if (type.getKind() == Tree.Kind.RECORD) {
                type.getMembers().stream()
                        .filter(member -> member.getKind() == Tree.Kind.VARIABLE)
                        .map(VariableTree.class::cast)
                        .filter(member -> !member.getModifiers().getFlags().contains(Modifier.STATIC))
                        .forEach(member -> expectedParameters.add(member.getName().toString()));
            }
            Set<String> expectedTypeParameters = new LinkedHashSet<>();
            type.getTypeParameters().forEach(parameter ->
                    expectedTypeParameters.add(parameter.getName().toString()));

            List<String> documentedParameters = new ArrayList<>();
            List<String> documentedTypeParameters = new ArrayList<>();
            documentation.getBlockTags().stream()
                    .filter(tag -> tag.getKind() == DocTree.Kind.PARAM)
                    .map(ParamTree.class::cast)
                    .forEach(tag -> (tag.isTypeParameter()
                            ? documentedTypeParameters : documentedParameters).add(tag.getName().toString()));
            if (!matchesSignature(expectedParameters, documentedParameters)) {
                violations.add(fileName + ": " + typeName + " 类型 @param 与签名不一致");
            }
            if (!matchesSignature(expectedTypeParameters, documentedTypeParameters)) {
                violations.add(fileName + ": " + typeName + " 类型参数 @param 与签名不一致");
            }
        }

        private void verifyReferenceParameterSemantics(
                MethodTree method, DocCommentTree documentation) {
            List<ParamTree> parameterTags = documentation.getBlockTags().stream()
                    .filter(tag -> tag.getKind() == DocTree.Kind.PARAM)
                    .map(ParamTree.class::cast)
                    .filter(tag -> !tag.isTypeParameter())
                    .toList();
            for (VariableTree parameter : method.getParameters()) {
                if (parameter.getType().getKind() == Tree.Kind.PRIMITIVE_TYPE) {
                    continue;
                }
                parameterTags.stream()
                        .filter(tag -> tag.getName().toString().equals(parameter.getName().toString()))
                        .findFirst()
                        .ifPresent(tag -> {
                            String description = decodeUnicodeEscapes(tag.getDescription().toString());
                            if (!REFERENCE_PARAMETER_SEMANTICS.matcher(description).find()) {
                                violations.add(fileName + ": " + method.getName() + " 参数 "
                                        + parameter.getName() + " @param 缺少 null/空/缺失/显式语义");
                            }
                        });
            }
        }

        private void verifyReturnTag(MethodTree method, DocCommentTree documentation) {
            long returnTags = documentation.getBlockTags().stream()
                    .filter(tag -> tag.getKind() == DocTree.Kind.RETURN)
                    .count();
            boolean returnsValue = method.getReturnType() != null
                    && !method.getReturnType().toString().equals("void");
            if (returnsValue && returnTags != 1) {
                violations.add(fileName + ": " + method.getName() + " @return 与返回签名不一致");
            }
            if (!returnsValue && returnTags != 0) {
                violations.add(fileName + ": " + method.getName() + " 不应声明 @return");
            }
        }

        private void verifyDeclaredThrowsClauseTags(MethodTree method, DocCommentTree documentation) {
            Set<String> documented = new LinkedHashSet<>();
            documentation.getBlockTags().stream()
                    .filter(tag -> tag.getKind() == DocTree.Kind.THROWS
                            || tag.getKind() == DocTree.Kind.EXCEPTION)
                    .map(ThrowsTree.class::cast)
                    .forEach(tag -> documented.add(simpleName(tag.getExceptionName().toString())));
            for (Tree declared : method.getThrows()) {
                if (!documented.contains(simpleName(declared.toString()))) {
                    violations.add(fileName + ": " + method.getName() + " 缺少已声明异常 @throws");
                }
            }
        }

        private boolean hasRequirementValidationError() {
            return requirementValidationError;
        }

        private static boolean isCheckedType(ClassTree node) {
            return node.getKind() == Tree.Kind.CLASS
                    || node.getKind() == Tree.Kind.RECORD
                    || node.getKind() == Tree.Kind.INTERFACE
                    || node.getKind() == Tree.Kind.ENUM;
        }

        private static boolean isPublic(Set<Modifier> modifiers) {
            return modifiers.contains(Modifier.PUBLIC);
        }

        private static boolean isEnumConstant(ClassTree parent, VariableTree variable) {
            Set<Modifier> modifiers = variable.getModifiers().getFlags();
            return parent.getKind() == Tree.Kind.ENUM
                    && modifiers.containsAll(Set.of(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL))
                    && variable.getType().toString().equals(parent.getSimpleName().toString())
                    && variable.getInitializer() != null
                    && variable.getInitializer().getKind() == Tree.Kind.NEW_CLASS;
        }

        private static String simpleName(String typeName) {
            int separator = typeName.lastIndexOf('.');
            return separator < 0 ? typeName : typeName.substring(separator + 1);
        }

        private static boolean matchesSignature(Set<String> expected, List<String> documented) {
            return expected.size() == documented.size()
                    && expected.equals(new LinkedHashSet<>(documented));
        }

        private static String decodeUnicodeEscapes(String text) {
            Matcher matcher = ESCAPED_UNICODE.matcher(text);
            StringBuilder decoded = new StringBuilder(text.length());
            while (matcher.find()) {
                char value = (char) Integer.parseInt(matcher.group(1), 16);
                matcher.appendReplacement(decoded, Matcher.quoteReplacement(String.valueOf(value)));
            }
            matcher.appendTail(decoded);
            return decoded.toString();
        }
    }

    private static BoundaryMarker marker(String label, String pattern) {
        return new BoundaryMarker(label, Pattern.compile(pattern));
    }

    private static String externalAccessPattern() {
        return "不(?:访问|读取|写入|调用|接触).{0,20}(?:数据库|模型|缓存|文件|网络|外部)"
                + "|(?:无|不产生|不做).{0,8}(?:外部访问|外部查询)"
                + "|(?:仅|只)(?:使用|处理|进行|执行|做).{0,12}(?:内存|本地)";
    }

    private static String authenticationPattern() {
        return "不(?:处理|负责|涉及|执行).{0,6}(?:认证|鉴权|身份校验)"
                + "|不(?:访问|读取|接触).{0,8}(?:认证信息|认证数据)"
                + "|不访问外部资源(?:，|或)处理认证"
                + "|(?:认证|鉴权|身份校验).{0,12}(?:由|在|属于|交给).{0,12}(?:调用|上层|入口|认证层)"
                + "|无.{0,4}(?:认证|鉴权).{0,4}(?:职责|处理|语义)";
    }

    private record BoundaryMarker(String label, Pattern pattern) {
    }

    private static final class StringJavaFileObject extends SimpleJavaFileObject {
        private final String source;

        private StringJavaFileObject(String fileName, String source) {
            super(URI.create("string:///" + fileName.replace('\\', '/')), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }
}
