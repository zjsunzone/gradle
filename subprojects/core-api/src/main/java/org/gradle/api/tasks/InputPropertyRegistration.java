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

package org.gradle.api.tasks;

import java.util.Map;

/**
 * Registered input properties
 *
 * @since 4.5
 */
public interface InputPropertyRegistration {
    /**
     * Returns the set of input properties for this task.
     *
     * @return The properties.
     *
     * @since 1.0
     */
    Map<String, Object> getProperties();

    /**
     * Returns true if this task has declared that it accepts source files.
     *
     * @return true if this task has source files, false if not.
     *
     * @since 1.0
     */
    boolean getHasSourceFiles();

    /**
     * Returns true if this task has declared the inputs that it consumes.
     *
     * @return true if this task has declared any inputs.
     *
     * @since 1.0
     */
    boolean getHasInputs();
}
