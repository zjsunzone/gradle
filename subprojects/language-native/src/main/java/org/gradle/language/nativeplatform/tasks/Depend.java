/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.language.nativeplatform.tasks;

import com.google.common.collect.ImmutableList;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.NonNullApi;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.changedetection.state.FileSystemSnapshotter;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.language.nativeplatform.internal.incremental.CompilationStateCacheFactory;
import org.gradle.language.nativeplatform.internal.incremental.DefaultHeaderDependenciesCollector;
import org.gradle.language.nativeplatform.internal.incremental.HeaderDependenciesCollector;
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.CSourceParser;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

/**
 * Responsible for detecting headers which are inputs to a native compilation task.
 *
 * @since 4.3
 */
@NonNullApi
@Incubating
public class Depend extends DefaultTask {

    private final ConfigurableFileCollection includes;
    private final ConfigurableFileCollection source;
    private final HeaderDependenciesCollector headerDependenciesCollector;
    private ImmutableList<String> includePaths;
    private Property<Boolean> importsAreIncludes;
    private final RegularFileProperty headerDependenciesFile;

    private CSourceParser sourceParser;
    private final FileSystemSnapshotter fileSystemSnapshotter;
    private final CompilationStateCacheFactory compilationStateCacheFactory;

    @Inject
    public Depend(FileSystemSnapshotter fileSystemSnapshotter, CompilationStateCacheFactory compilationStateCacheFactory, DirectoryFileTreeFactory directoryFileTreeFactory, CSourceParser sourceParser) {
        this.fileSystemSnapshotter = fileSystemSnapshotter;
        this.compilationStateCacheFactory = compilationStateCacheFactory;
        this.includes = getProject().files();
        this.source = getProject().files();
        this.sourceParser = sourceParser;
        this.headerDependenciesFile = newOutputFile();
        ObjectFactory objectFactory = getProject().getObjects();
        this.importsAreIncludes = objectFactory.property(Boolean.class);
        this.headerDependenciesCollector = new DefaultHeaderDependenciesCollector(directoryFileTreeFactory);
        dependsOn(includes);
    }

    @TaskAction
    public void detectHeaders(IncrementalTaskInputs incrementalTaskInputs) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Input
    protected Collection<String> getIncludePaths() {
        if (includePaths == null) {
            ImmutableList.Builder<String> builder = ImmutableList.builder();
            Set<File> roots = includes.getFiles();
            for (File root : roots) {
                builder.add(root.getAbsolutePath());
            }
            includePaths = builder.build();
        }
        return includePaths;
    }

    /**
     * Returns the set of directory where the compiler should search for header files.
     *
     * @since 4.4
     */
    @Internal("tracked through getIncludePaths()")
    public ConfigurableFileCollection getIncludes() {
        return includes;
    }

    /**
     * Add directories where the compiler should search for header files.
     */
    public void includes(Object includeRoots) {
        includes.from(includeRoots);
    }

    /**
     * Returns the source files to be compiled.
     */
    @InputFiles
    @SkipWhenEmpty
    public ConfigurableFileCollection getSource() {
        return source;
    }

    /**
     * Adds a set of source files to be compiled. The provided sourceFiles object is evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     */
    public void source(Object sourceFiles) {
        source.from(sourceFiles);
    }

    @OutputFile
    public RegularFileProperty getHeaderDependenciesFile() {
        return headerDependenciesFile;
    }

    @Input
    public Property<Boolean> getImportsAreIncludes() {
        return importsAreIncludes;
    }
}
