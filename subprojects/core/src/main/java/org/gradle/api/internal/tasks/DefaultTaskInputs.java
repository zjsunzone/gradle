/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.tasks;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.Describable;
import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInputsInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.tasks.TaskInputPropertyBuilder;
import org.gradle.api.tasks.TaskInputs;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.gradle.api.internal.tasks.DefaultInputPropertyRegistration.RUNTIME_INPUT_DIRECTORY_VALIDATOR;
import static org.gradle.api.internal.tasks.DefaultInputPropertyRegistration.RUNTIME_INPUT_FILE_VALIDATOR;

@NonNullApi
public class DefaultTaskInputs implements TaskInputsInternal {

    private final FileCollection allInputFiles;
    private final FileCollection allSourceFiles;
    private final TaskMutator taskMutator;
    private final Runnable discoverInputsAndOutputs;
    private final TaskInputs deprecatedThis;
    private InputPropertyRegistrationInternal inputPropertyRegistration;

    public DefaultTaskInputs(FileResolver resolver, TaskInternal task, TaskMutator taskMutator, Runnable discoverInputsAndOutputs) {
        this.taskMutator = taskMutator;
        this.discoverInputsAndOutputs = discoverInputsAndOutputs;
        String taskName = task.getName();
        Supplier<List<DeclaredTaskInputFileProperty>> declaredFilePropertiesSupplier = new Supplier<List<DeclaredTaskInputFileProperty>>() {
            @Override
            public List<DeclaredTaskInputFileProperty> get() {
                DefaultTaskInputs.this.discoverInputsAndOutputs.run();
                return getInputPropertyRegistration().getFilePropertiesInternal();
            }
        };
        this.allInputFiles = new TaskInputUnionFileCollection(taskName, "input", false, declaredFilePropertiesSupplier);
        this.allSourceFiles = new TaskInputUnionFileCollection(taskName, "source", true, declaredFilePropertiesSupplier);
        this.deprecatedThis = new LenientTaskInputsDeprecationSupport(this);
    }

    public void setInputPropertyRegistration(InputPropertyRegistrationInternal inputPropertyRegistration) {
        this.inputPropertyRegistration = inputPropertyRegistration;
    }

    @Override
    public boolean getHasInputs() {
        return getInputPropertyRegistration().getHasInputs();
    }

    public InputPropertyRegistrationInternal getInputPropertyRegistration() {
        discoverInputsAndOutputs.run();
        return inputPropertyRegistration;
    }

    @Override
    public FileCollection getFiles() {
        return allInputFiles;
    }

    @Override
    public ImmutableSortedSet<TaskInputFilePropertySpec> getFileProperties() {
        return getInputPropertyRegistration().getFileProperties();
    }

    @Override
    public TaskInputFilePropertyBuilderInternal files(final Object... paths) {
        return taskMutator.mutate("TaskInputs.files(Object...)", new Callable<TaskInputFilePropertyBuilderInternal>() {
            @Override
            public TaskInputFilePropertyBuilderInternal call() {
                return registerFiles(new StaticValue(unpackVarargs(paths)));
            }
        });
    }

    private static Object unpackVarargs(Object[] args) {
        if (args.length == 1) {
            return args[0];
        }
        return args;
    }

    @Override
    public TaskInputFilePropertyBuilderInternal registerFiles(ValidatingValue paths) {
        return getInputPropertyRegistration().registerFiles(paths);
    }

    @Override
    public TaskInputFilePropertyBuilderInternal file(final Object path) {
        return taskMutator.mutate("TaskInputs.file(Object)", new Callable<TaskInputFilePropertyBuilderInternal>() {
            @Override
            public TaskInputFilePropertyBuilderInternal call() {
                return getInputPropertyRegistration().registerFile(new StaticValue(path), RUNTIME_INPUT_FILE_VALIDATOR);
            }
        });
    }

    @Override
    public TaskInputFilePropertyBuilderInternal registerFile(ValidatingValue value) {
        return getInputPropertyRegistration().registerFile(value);
    }

    public TaskInputFilePropertyBuilderInternal registerFile(ValidatingValue value, ValidationAction validator) {
        return getInputPropertyRegistration().registerFile(value, validator);
    }

    @Override
    public TaskInputFilePropertyBuilderInternal dir(final Object dirPath) {
        return taskMutator.mutate("TaskInputs.dir(Object)", new Callable<TaskInputFilePropertyBuilderInternal>() {
            @Override
            public TaskInputFilePropertyBuilderInternal call() {
                return registerDir(new StaticValue(dirPath), RUNTIME_INPUT_DIRECTORY_VALIDATOR);
            }
        });
    }

    @Override
    public TaskInputFilePropertyBuilderInternal registerDir(final ValidatingValue dirPath) {
        return getInputPropertyRegistration().registerDir(dirPath);
    }

    public TaskInputFilePropertyBuilderInternal registerDir(ValidatingValue dirPath, ValidationAction validator) {
        return getInputPropertyRegistration().registerDir(dirPath, validator);
    }

    @Override
    public boolean getHasSourceFiles() {
        return getInputPropertyRegistration().getHasSourceFiles();
    }

    @Override
    public FileCollection getSourceFiles() {
        return allSourceFiles;
    }

    @Override
    public void validate(TaskValidationContext context) {
        getInputPropertyRegistration().validate(context);
    }

    @Override
    public List<DeclaredTaskInputFileProperty> getFilePropertiesInternal() {
        return getInputPropertyRegistration().getFilePropertiesInternal();
    }

    public Map<String, Object> getProperties() {
        return getInputPropertyRegistration().getProperties();
    }

    @Override
    public TaskInputPropertyBuilder property(final String name, @Nullable final Object value) {
        return taskMutator.mutate("TaskInputs.property(String, Object)", new Callable<TaskInputPropertyBuilder>() {
            @Override
            public TaskInputPropertyBuilder call() {
                return registerProperty(name, new StaticValue(value));
            }
        });
    }

    @Override
    public TaskInputs properties(final Map<String, ?> newProps) {
        taskMutator.mutate("TaskInputs.properties(Map)", new Runnable() {
            @Override
            public void run() {
                for (Map.Entry<String, ?> entry : newProps.entrySet()) {
                    registerProperty(entry.getKey(), new StaticValue(entry.getValue()));
                }
            }
        });
        return deprecatedThis;
    }

    @Override
    public TaskInputPropertyBuilder registerProperty(String name, ValidatingValue value) {
        return getInputPropertyRegistration().registerProperty(name, value);
    }

    @Override
    public TaskInputPropertyBuilder registerNested(String name, ValidatingValue value) {
        return getInputPropertyRegistration().registerNested(name, value);
    }

    private static class TaskInputUnionFileCollection extends CompositeFileCollection implements Describable {
        private final boolean skipWhenEmptyOnly;
        private final String taskName;
        private final String type;
        private final Supplier<List<DeclaredTaskInputFileProperty>> filePropertiesInternal;

        public TaskInputUnionFileCollection(String taskName, String type, boolean skipWhenEmptyOnly, Supplier<List<DeclaredTaskInputFileProperty>> filePropertiesInternal) {
            this.taskName = taskName;
            this.type = type;
            this.skipWhenEmptyOnly = skipWhenEmptyOnly;
            this.filePropertiesInternal = filePropertiesInternal;
        }

        @Override
        public String getDisplayName() {
            return "task '" + taskName + "' " + type + " files";
        }

        @Override
        public void visitContents(FileCollectionResolveContext context) {
            for (TaskInputFilePropertySpec fileProperty : filePropertiesInternal.get()) {
                if (!skipWhenEmptyOnly || fileProperty.isSkipWhenEmpty()) {
                    context.add(fileProperty.getPropertyFiles());
                }
            }
        }
    }
}
