package org.transparent.emerald;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.compile.JavaCompile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.objectweb.asm.ClassReader.SKIP_DEBUG;
import static org.objectweb.asm.ClassReader.SKIP_FRAMES;

public final class EmeraldPlugin implements Plugin<Project> {
    private static final String SPI = "META-INF/services/com.sun.source.util.Plugin";

    @Override
    public void apply(@Nonnull Project project) {
        project.getPluginManager().apply(JavaPlugin.class);

        final ConfigurationContainer container = project.getConfigurations();
        container.getByName("annotationProcessor", config ->
                config.extendsFrom(container.create("compilerPlugin")));

        project.afterEvaluate(project2 -> {
            final Set<File> files = project2
                    .getConfigurations()
                    .getByName("compilerPlugin")
                    .resolve();
            final List<String> names = new ArrayList<>();
            for (File file : files) {
                try (FileSystem fs = getFileSystem(file)) {
                    getServicesFile(fs).ifPresent(path ->
                            names.addAll(getPluginNames(fs, path, file)));
                } catch (IOException e) {
                    throw new RuntimeException("Could not initialize file system for jar", e);
                }
            }

            project.getTasks().withType(JavaCompile.class, task -> {
                final List<String> args = task
                        .getOptions()
                        .getCompilerArgs();
                names.forEach(name -> args.add("-Xplugin:" + name));

                final JavaPluginConvention convention = project2
                        .getConvention()
                        .getPlugin(JavaPluginConvention.class);
                // Offers extra Java 9 support.
                // This setup is suggested by Manifold.
                if (convention.getSourceCompatibility().isJava9Compatible()
                        && containsModuleDescriptor(convention)) {
                    args.add("--module-path");
                    args.add(task.getClasspath().getAsPath());
                }
            });
        });
    }

    private FileSystem getFileSystem(File file) throws IOException {
        return FileSystems.newFileSystem(
                file.toPath(), null);
    }

    private Optional<Path> getServicesFile(FileSystem fs) {
        final Path path = fs.getPath(SPI);
        if (Files.exists(path))
            return Optional.of(path);
        return Optional.empty();
    }

    private List<String> getPluginNames(FileSystem fs, Path path, File file) {
        try {
            return Files.lines(path)
                    .map(name -> getPluginName(fs, name, file))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getClassFilePath(String s) {
        return "/"
                + s.replaceAll("\\.", "/")
                + ".class";
    }

    private InputStream toInputStream(Path path) {
        if (Files.exists(path)) {
            try {
                return Files.newInputStream(path);
            } catch (IOException e) {
                throw new RuntimeException("Could not convert path to input stream", e);
            }
        }
        return null;
    }

    private String getPluginName(FileSystem fs, String name, File file) {
        final Path path = fs.getPath(getClassFilePath(name));
        try (final InputStream stream = toInputStream(path)) {
            if (stream != null) {
                final ClassReader reader = new ClassReader(stream);
                final NameFinder finder = new NameFinder(Opcodes.ASM9);
                reader.accept(finder, SKIP_DEBUG | SKIP_FRAMES);
                return (finder.name == null)
                        ? getPluginName(file, name)
                        : finder.name;
            }
        } catch(IOException e) {
            throw new RuntimeException("Could not get compiler plugin name (Bytecode)", e);
        }
        return "";
    }

    private String getPluginName(File file, String name) {
        try {
            return ((com.sun.source.util.Plugin) Class
                    .forName(name, false,
                            new URLClassLoader(new URL[]{
                                    file.toURI().toURL()
                            }))
                    .newInstance())
                    .getName();
        } catch (IOException | ReflectiveOperationException e) {
            throw new RuntimeException("Could not get compiler plugin name (Classloader)", e);
        }
    }


    private boolean containsModuleDescriptor(JavaPluginConvention convention) {
        return convention.getSourceSets()
                .getByName("main")
                .getAllJava()
                .getFiles()
                .contains(new File("module-info.java"));
    }
}