package dev.iyanz.cherry.gradle;

import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builds an in-memory {@link Project} (via {@link ProjectBuilder}, no external Gradle process) and
 * applies {@link CherryGradlePlugin} directly, to check the extension/task/dependency wiring
 * {@code CherryGradlePlugin.apply} performs - the actual generated-file content is covered separately
 * by {@link ManifestGeneratorTest}/{@link MixinConfigGeneratorTest}, and end-to-end jar contents are
 * verified by building {@code example-plugin} (see the repository README).
 */
class CherryGradlePluginFunctionalTest {

    @Test
    void appliesJavaPluginAndCreatesExtensionAndUniversalTasks(@TempDir Path projectDir) {
        Project project = newProject(projectDir);
        project.getPluginManager().apply(CherryGradlePlugin.class);

        assertTrue(project.getPluginManager().hasPlugin("java"));
        assertNotNull(project.getExtensions().findByType(CherryExtension.class));
        assertNotNull(project.getTasks().findByName("generateCherryManifest"));
        assertNotNull(project.getTasks().findByName("stageCherryAccessTransformers"));
        assertNotNull(project.getTasks().findByName("stageCherryAccessWideners"));
    }

    @Test
    void pluginNameDefaultsToProjectName(@TempDir Path projectDir) {
        Project project = newProject(projectDir);
        project.getPluginManager().apply(CherryGradlePlugin.class);

        CherryExtension ext = project.getExtensions().getByType(CherryExtension.class);
        assertEquals(project.getName(), ext.getPluginName().get());
    }

    @Test
    void mixinPackageWiresMixinTaskAndPinnedDependenciesAfterEvaluate(@TempDir Path projectDir) {
        Project project = newProject(projectDir);
        project.getPluginManager().apply(CherryGradlePlugin.class);

        CherryExtension ext = project.getExtensions().getByType(CherryExtension.class);
        ext.getMixinPackage().set("com.example.mixin");

        ((ProjectInternal) project).evaluate();

        assertNotNull(project.getTasks().findByName("generateCherryMixinConfig"));

        assertTrue(hasDependency(project, "compileOnly", "sponge-mixin", CherryGradlePlugin.MIXIN_VERSION),
            "expected net.fabricmc:sponge-mixin:" + CherryGradlePlugin.MIXIN_VERSION + " on compileOnly");
        assertTrue(hasDependency(project, "compileOnly", "mixinextras-common", CherryGradlePlugin.MIXIN_EXTRAS_VERSION),
            "expected io.github.llamalad7:mixinextras-common:" + CherryGradlePlugin.MIXIN_EXTRAS_VERSION + " on compileOnly");
        assertTrue(hasDependency(project, "annotationProcessor", "sponge-mixin", CherryGradlePlugin.MIXIN_VERSION),
            "expected net.fabricmc:sponge-mixin:" + CherryGradlePlugin.MIXIN_VERSION + " on annotationProcessor");
    }

    @Test
    void withoutMixinPackageNoMixinTaskOrDependenciesAreAdded(@TempDir Path projectDir) {
        Project project = newProject(projectDir);
        project.getPluginManager().apply(CherryGradlePlugin.class);

        ((ProjectInternal) project).evaluate();

        assertNull(project.getTasks().findByName("generateCherryMixinConfig"));
        assertFalse(hasDependency(project, "compileOnly", "sponge-mixin", null));
    }

    @Test
    void accessWidenerWithoutMixinPackageFailsFastAtConfigurationTime(@TempDir Path projectDir) throws IOException {
        Project project = newProject(projectDir);
        project.getPluginManager().apply(CherryGradlePlugin.class);

        Path widener = projectDir.resolve("test.accesswidener");
        Files.writeString(widener, "accessWidener v2 named\n");

        CherryExtension ext = project.getExtensions().getByType(CherryExtension.class);
        ext.getAccessWideners().from(widener.toFile());

        Exception e = assertThrows(Exception.class, () -> ((ProjectInternal) project).evaluate());
        assertTrue(chainContains(e, "mixinPackage"), "expected the failure to mention mixinPackage, was: " + e);
    }

    @Test
    void serverApiHelperAddsCompileOnlyDependency(@TempDir Path projectDir) {
        Project project = newProject(projectDir);
        project.getPluginManager().apply(CherryGradlePlugin.class);

        CherryExtension ext = project.getExtensions().getByType(CherryExtension.class);
        ext.serverApi("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT");

        assertTrue(hasDependency(project, "compileOnly", "paper-api", null));
    }

    private static Project newProject(Path projectDir) {
        return ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
    }

    private static boolean hasDependency(Project project, String configuration, String artifactName, String version) {
        return project.getConfigurations().getByName(configuration).getDependencies().stream()
            .anyMatch(d -> artifactName.equals(d.getName()) && (version == null || version.equals(d.getVersion())));
    }

    private static boolean chainContains(Throwable throwable, String needle) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(needle)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
