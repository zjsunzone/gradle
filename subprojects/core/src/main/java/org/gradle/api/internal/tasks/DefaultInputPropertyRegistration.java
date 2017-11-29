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

package org.gradle.api.internal.tasks;

import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import groovy.lang.GString;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NonNullApi;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.tasks.TaskInputPropertyBuilder;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.internal.typeconversion.UnsupportedNotationException;
import org.gradle.util.DeprecationLogger;

import javax.annotation.Nullable;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.gradle.api.internal.tasks.TaskPropertyUtils.ensurePropertiesHaveNames;
import static org.gradle.util.GUtil.uncheckedCall;

@NonNullApi
public class DefaultInputPropertyRegistration implements InputPropertyRegistrationInternal {

    private final List<DeclaredTaskInputFileProperty> declaredFileProperties = Lists.newArrayList();
    private ImmutableSortedSet<TaskInputFilePropertySpec> fileProperties;
    private final Map<String, PropertyValue> properties = new HashMap<String, PropertyValue>();
    private final String taskName;
    private final TaskInputs taskInputs;
    private final FileResolver resolver;
    private static final ValidationAction INPUT_FILE_VALIDATOR = new ValidationAction() {
        @Override
        public void validate(String propertyName, Object value, TaskValidationContext context, TaskValidationContext.Severity severity) {
            File file = toFile(context, value);
            if (!file.exists()) {
                context.recordValidationMessage(severity, String.format("File '%s' specified for property '%s' does not exist.", file, propertyName));
            } else if (!file.isFile()) {
                context.recordValidationMessage(severity, String.format("File '%s' specified for property '%s' is not a file.", file, propertyName));
            }
        }
    };

    private static final ValidationAction INPUT_DIRECTORY_VALIDATOR = new ValidationAction() {
        @Override
        public void validate(String propertyName, Object value, TaskValidationContext context, TaskValidationContext.Severity severity) {
            File directory = toDirectory(context, value);
            if (!directory.exists()) {
                context.recordValidationMessage(severity, String.format("Directory '%s' specified for property '%s' does not exist.", directory, propertyName));
            } else if (!directory.isDirectory()) {
                context.recordValidationMessage(severity, String.format("Directory '%s' specified for property '%s' is not a directory.", directory, propertyName));
            }
        }
    };

    public static final ValidationAction RUNTIME_INPUT_FILE_VALIDATOR = wrapRuntimeApiValidator("file", INPUT_FILE_VALIDATOR);

    public static final ValidationAction RUNTIME_INPUT_DIRECTORY_VALIDATOR = wrapRuntimeApiValidator("dir", INPUT_DIRECTORY_VALIDATOR);

    public DefaultInputPropertyRegistration(String taskName, TaskInputs taskInputs, FileResolver resolver) {
        this.taskName = taskName;
        this.taskInputs = taskInputs;
        this.resolver = resolver;
    }

    @Override
    public boolean getHasInputs() {
        return !declaredFileProperties.isEmpty() || !properties.isEmpty();
    }

    @Override
    public boolean getHasSourceFiles() {
        for (TaskInputFilePropertySpec propertySpec : declaredFileProperties) {
            if (propertySpec.isSkipWhenEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void validate(TaskValidationContext context) {
        TaskPropertyUtils.ensurePropertiesHaveNames(declaredFileProperties);
        for (PropertyValue propertyValue : properties.values()) {
            propertyValue.getPropertySpec().validate(context);
        }
        for (DeclaredTaskInputFileProperty property : declaredFileProperties) {
            property.validate(context);
        }
    }

    @Override
    public List<DeclaredTaskInputFileProperty> getFilePropertiesInternal() {
        return declaredFileProperties;
    }

    @Override
    public ImmutableSortedSet<TaskInputFilePropertySpec> getFileProperties() {
        if (fileProperties == null) {
            ensurePropertiesHaveNames(declaredFileProperties);
            this.fileProperties = TaskPropertyUtils.<TaskInputFilePropertySpec>collectFileProperties("input", declaredFileProperties.iterator());
        }
        return fileProperties;
    }

    @Override
    public Map<String, Object> getProperties() {
        Map<String, Object> actualProperties = new HashMap<String, Object>();
        for (PropertyValue property : properties.values()) {
            String propertyName = property.resolveName();
            try {
                Object value = prepareValue(property.resolveValue());
                actualProperties.put(propertyName, value);
            } catch (Exception ex) {
                throw new InvalidUserDataException(String.format("Error while evaluating property '%s' of %s", propertyName, taskName), ex);
            }
        }
        return actualProperties;
    }

    @Nullable
    private Object prepareValue(@Nullable Object value) {
        while (true) {
            if (value instanceof Callable) {
                Callable callable = (Callable) value;
                value = uncheckedCall(callable);
            } else if (value instanceof FileCollection) {
                FileCollection fileCollection = (FileCollection) value;
                return fileCollection.getFiles();
            } else {
                return avoidGString(value);
            }
        }
    }

    @Nullable
    private static Object avoidGString(@Nullable Object value) {
        return (value instanceof GString) ? value.toString() : value;
    }

    @Override
    public TaskInputFilePropertyBuilderInternal registerFiles(ValidatingValue paths) {
        return addSpec(paths, ValidationAction.NO_OP);
    }

    @Override
    public TaskInputFilePropertyBuilderInternal registerFile(ValidatingValue value) {
        return registerFile(value, INPUT_FILE_VALIDATOR);
    }

    @Override
    public TaskInputFilePropertyBuilderInternal registerDir(final ValidatingValue dirPath) {
        return registerDir(dirPath, INPUT_DIRECTORY_VALIDATOR);
    }

    @Override
    public TaskInputPropertyBuilder registerProperty(String name, ValidatingValue value) {
        PropertyValue propertyValue = properties.get(name);
        DeclaredTaskInputProperty spec;
        if (propertyValue instanceof SimplePropertyValue) {
            spec = propertyValue.getPropertySpec();
            propertyValue.setValue(value);
        } else {
            spec = new DefaultTaskInputPropertySpec(taskInputs, name, value);
            propertyValue = new SimplePropertyValue(spec, value);
            properties.put(name, propertyValue);
        }
        return spec;
    }

    @Override
    public TaskInputPropertyBuilder registerNested(String name, ValidatingValue value) {
        PropertyValue propertyValue = properties.get(name);
        DeclaredTaskInputProperty spec;
        if (propertyValue instanceof NestedBeanTypePropertyValue) {
            spec = propertyValue.getPropertySpec();
            propertyValue.setValue(value);
        } else {
            spec = new DefaultTaskInputPropertySpec(taskInputs, name, value);
            propertyValue = new NestedBeanTypePropertyValue(spec, value);
            properties.put(name, propertyValue);
        }
        return spec;
    }

    @Override
    public TaskInputFilePropertyBuilderInternal registerFile(ValidatingValue value, ValidationAction validator) {
        return addSpec(value, validator);
    }

    @Override
    public TaskInputFilePropertyBuilderInternal registerDir(ValidatingValue dirPath, ValidationAction validator) {
        FileTreeInternal fileTree = resolver.resolveFilesAsTree(dirPath);
        return addSpec(new FileTreeValue(dirPath, fileTree), validator);
    }

    private TaskInputFilePropertyBuilderInternal addSpec(ValidatingValue paths, ValidationAction validationAction) {
        DefaultTaskInputFilePropertySpec spec = new DefaultTaskInputFilePropertySpec(taskName, resolver, paths, validationAction);
        declaredFileProperties.add(spec);
        return spec;
    }

    private static ValidationAction wrapRuntimeApiValidator(final String method, final ValidationAction validator) {
        return new ValidationAction() {
            @Override
            public void validate(String propertyName, Object value, TaskValidationContext context, TaskValidationContext.Severity severity) {
                try {
                    validator.validate(propertyName, value, context, severity);
                } catch (UnsupportedNotationException ex) {
                    DeprecationLogger.nagUserOfDeprecated("Using TaskInputs." + method + "() with something that doesn't resolve to a File object", "Use TaskInputs.files() instead");
                }
            }
        };
    }


    private static File toFile(TaskValidationContext context, Object value) {
        return context.getResolver().resolve(value);
    }

    private static File toDirectory(TaskValidationContext context, Object value) {
        if (value instanceof ConfigurableFileTree) {
            return ((ConfigurableFileTree) value).getDir();
        }
        return toFile(context, value);
    }

    private static abstract class PropertyValue {
        protected final DeclaredTaskInputProperty propertySpec;
        protected ValidatingValue value;

        public PropertyValue(DeclaredTaskInputProperty propertySpec, ValidatingValue value) {
            this.propertySpec = propertySpec;
            this.value = value;
        }

        public DeclaredTaskInputProperty getPropertySpec() {
            return propertySpec;
        }

        public abstract String resolveName();

        @Nullable
        public abstract Object resolveValue();

        public void setValue(ValidatingValue value) {
            this.value = value;
        }
    }

    private static class SimplePropertyValue extends PropertyValue {
        public SimplePropertyValue(DeclaredTaskInputProperty propertySpec, ValidatingValue value) {
            super(propertySpec, value);
        }

        @Override
        public String resolveName() {
            return propertySpec.getPropertyName();
        }

        @Override
        public Object resolveValue() {
            return value.call();
        }
    }

    private static class NestedBeanTypePropertyValue extends PropertyValue {
        public NestedBeanTypePropertyValue(DeclaredTaskInputProperty propertySpec, ValidatingValue value) {
            super(propertySpec, value);
        }

        @Override
        public String resolveName() {
            return propertySpec.getPropertyName() + ".class";
        }

        @Override
        public Object resolveValue() {
            Object value = this.value.call();
            return value == null ? null : value.getClass().getName();
        }
    }

    private static class FileTreeValue implements ValidatingValue {
        private final ValidatingValue delegate;
        private final FileTreeInternal fileTree;

        public FileTreeValue(ValidatingValue delegate, FileTreeInternal fileTree) {
            this.delegate = delegate;
            this.fileTree = fileTree;
        }

        @Nullable
        @Override
        public Object call() {
            return fileTree;
        }

        @Override
        public void validate(String propertyName, boolean optional, ValidationAction valueValidator, TaskValidationContext context) {
            delegate.validate(propertyName, optional, valueValidator, context);
        }
    }

}
