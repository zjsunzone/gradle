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

package org.gradle.api.internal;

import org.gradle.api.NonNullApi;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.taskfactory.TaskClassInfoStore;
import org.gradle.api.internal.project.taskfactory.TaskClassValidator;
import org.gradle.api.internal.tasks.DefaultInputPropertyRegistration;
import org.gradle.api.internal.tasks.DefaultTaskDestroyables;
import org.gradle.api.internal.tasks.DefaultTaskInputs;
import org.gradle.api.internal.tasks.DefaultTaskLocalState;
import org.gradle.api.internal.tasks.DefaultTaskOutputs;
import org.gradle.api.internal.tasks.TaskMutator;
import org.gradle.api.tasks.TaskDestroyables;
import org.gradle.api.tasks.TaskLocalState;

@NonNullApi
public class ChangeDetection implements Runnable {

    private final TaskInternal task;
    private final DefaultTaskInputs taskInputs;
    private final TaskOutputsInternal taskOutputs;
    private final TaskDestroyables taskDestroyables;
    private final TaskLocalState taskLocalState;
    private final TaskClassInfoStore taskClassInfoStore;
    private final FileResolver resolver;
    private boolean inputsAndOutputsDetected;

    public ChangeDetection(FileResolver resolver, TaskInternal task, TaskMutator taskMutator, TaskClassInfoStore taskClassInfoStore) {
        this.task = task;
        this.taskClassInfoStore = taskClassInfoStore;
        this.resolver = resolver;
        this.taskInputs = new DefaultTaskInputs(resolver, task, taskMutator, this);
        this.taskOutputs = new DefaultTaskOutputs(resolver, task, taskMutator, this);
        this.taskDestroyables = new DefaultTaskDestroyables(resolver, task, taskMutator, this);
        this.taskLocalState = new DefaultTaskLocalState(resolver, task, taskMutator, this);
    }

    public TaskInputsInternal getTaskInputs() {
        return taskInputs;
    }

    public TaskOutputsInternal getTaskOutputs() {
        return taskOutputs;
    }

    public TaskDestroyables getTaskDestroyables() {
        return taskDestroyables;
    }

    public TaskLocalState getTaskLocalState() {
        return taskLocalState;
    }

    @Override
    public void run() {
        if (!inputsAndOutputsDetected) {
            inputsAndOutputsDetected = true;
            TaskClassValidator validator = taskClassInfoStore.getTaskClassInfo(task.getClass()).getValidator();
            DefaultInputPropertyRegistration inputPropertyRegistration = new DefaultInputPropertyRegistration(task.getName(), taskInputs, resolver);
            validator.addInputsAndOutputs(task, inputPropertyRegistration);
            taskInputs.setInputPropertyRegistration(inputPropertyRegistration);
        }
    }
}
