package org.transparent.emerald;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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

        final Provider<Configuration> configuration = project
                .getConfigurations()
                .register("compilerPlugin");

        project.afterEvaluate(project2 -> {
            project2.getConfigurations()
                    .getByName("annotationProcessor", config ->
                            config.extendsFrom(configuration.get()));

            final Set<File> files = project2
                    .getConfigurations()
                    .getByName("compilerPlugin")
                    .resolve();
            final List<String> names = new ArrayList<>();
            for (File file : files) {
                try (FileSystem fs = getFileSystem(file)) {
                    getServicesFile(fs).ifPresent(path -> {
                        getPluginStreams(fs, path)
                                .stream()
                                .map(this::getPluginName)
                                .forEach(names::add);
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            project.getTasks().withType(JavaCompile.class, task -> names
                    .forEach(name -> task
                            .getOptions()
                            .getCompilerArgs()
                            .add("-Xplugin:" + name)
                    ));
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

    private List<InputStream> getPluginStreams(FileSystem fs, Path path) {
        try {
            return Files.lines(path)
                    .map(this::getClassFilePath)
                    .map(fs::getPath)
                    .map(this::toInputStream)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Collections.emptyList();
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
                e.printStackTrace();
            }
        }
        return null;
    }

    private String getPluginName(InputStream stream) {
        if (stream != null) {
            try {
                final ClassReader reader = new ClassReader(stream);
                final NameFinder finder = new NameFinder(Opcodes.ASM9);
                reader.accept(finder, SKIP_DEBUG | SKIP_FRAMES);
                return finder.name;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return "";
    }
}