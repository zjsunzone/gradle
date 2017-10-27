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

package org.gradle.nativeplatform.fixtures.app;

import org.gradle.integtests.fixtures.SourceFile;

import java.util.List;

public abstract class SwiftSourceElement extends SourceElement {
    public String getModuleName() {
        throw new IllegalStateException("This fixture needs a module name. Set it either by overriding "
            + "'getModuleName()' or by using 'inModule(String)' methods");
    }

    public SwiftSourceElement asModule(final String moduleName) {
        final SwiftSourceElement delegate = this;
        return new SwiftSourceElement() {
            @Override
            public List<SourceFile> getFiles() {
                return delegate.getFiles();
            }

            @Override
            public String getModuleName() {
                return moduleName;
            }
        };
    }
}