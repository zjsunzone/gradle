/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.language.nativeplatform.internal.incremental;

import org.gradle.api.NonNullApi;
import org.gradle.api.internal.Stats;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.cache.PersistentStateCache;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.tasks.SimpleStaleClassCleaner;
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec;

@NonNullApi
public class IncrementalNativeCompiler<T extends NativeCompileSpec> implements Compiler<T> {
    private final IncrementalCompilation incrementalCompilation;
    private final PersistentStateCache<CompilationState> stateCache;
    private final Compiler<T> delegateCompiler;
    private final TaskInternal task;

    public IncrementalNativeCompiler(TaskInternal task, IncrementalCompilation incrementalCompilation, PersistentStateCache<CompilationState> stateCache, Compiler<T> delegateCompiler) {
        this.task = task;
        this.incrementalCompilation = incrementalCompilation;
        this.stateCache = stateCache;
        this.delegateCompiler = delegateCompiler;
    }

    @Override
    public WorkResult execute(final T spec) {
        spec.setSourceFileIncludeDirectives(incrementalCompilation.getSourceFileIncludeDirectives());

        long startNs = System.nanoTime();

        WorkResult workResult;
        if (spec.isIncrementalCompile()) {
            workResult = doIncrementalCompile(incrementalCompilation, spec);
        } else {
            workResult = doCleanIncrementalCompile(spec);
        }

        long endNs = System.nanoTime();
        Stats.compileProcessing(null, startNs, endNs);

        stateCache.set(incrementalCompilation.getFinalState());

        return workResult;
    }

    protected WorkResult doIncrementalCompile(IncrementalCompilation compilation, T spec) {
        // Determine the actual sources to clean/compile
        spec.setSourceFiles(compilation.getRecompile());
        spec.setRemovedSourceFiles(compilation.getRemoved());
        return delegateCompiler.execute(spec);
    }

    protected WorkResult doCleanIncrementalCompile(T spec) {
        boolean deleted = cleanPreviousOutputs(spec);
        WorkResult compileResult = delegateCompiler.execute(spec);
        if (deleted && !compileResult.getDidWork()) {
            return WorkResults.didWork(true);
        }
        return compileResult;
    }

    private boolean cleanPreviousOutputs(NativeCompileSpec spec) {
        SimpleStaleClassCleaner cleaner = new SimpleStaleClassCleaner(getTask().getOutputs());
        cleaner.setDestinationDir(spec.getObjectFileDir());
        cleaner.execute();
        return cleaner.getDidWork();
    }

    protected TaskInternal getTask() {
        return task;
    }
}
