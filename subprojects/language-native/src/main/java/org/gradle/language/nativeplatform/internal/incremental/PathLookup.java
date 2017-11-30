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

package org.gradle.language.nativeplatform.internal.incremental;

import net.jcip.annotations.ThreadSafe;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.tasks.execution.TaskOutputsGenerationListener;
import org.gradle.cache.internal.ProducerGuard;
import org.gradle.internal.Factory;
import org.gradle.internal.event.ListenerManager;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// TODO - merge into FileSystemSnapshotter, reuse the information already present
@ThreadSafe
public class PathLookup implements TaskOutputsGenerationListener {
    private final StringInterner interner;
    private final Map<String, Boolean> found = new ConcurrentHashMap<String, Boolean>();
    private final ProducerGuard<String> lookupGuard = ProducerGuard.striped();

    public PathLookup(StringInterner interner, ListenerManager listenerManager) {
        this.interner = interner;
        listenerManager.addListener(this);
    }

    @Override
    public void beforeTaskOutputsGenerated() {
        // TODO - only if not in the cache, reuse logic in FileSystemMirror
        found.clear();
    }

    public boolean isFile(final File file) {
        final String path = file.getAbsolutePath();
        return lookupGuard.guardByKey(path, new Factory<Boolean>() {
            @Override
            public Boolean create() {
                Boolean isFile = found.get(path);
                if (isFile == null) {
                    isFile = file.isFile();
                    found.put(interner.intern(path), isFile);
                }
                return isFile;
            }
        });
    }
}
