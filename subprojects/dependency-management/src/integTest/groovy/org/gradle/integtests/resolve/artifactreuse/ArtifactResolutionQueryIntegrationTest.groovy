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

package org.gradle.integtests.resolve.artifactreuse

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import spock.lang.Issue
import spock.lang.Timeout

import java.util.concurrent.TimeUnit

class ArtifactResolutionQueryIntegrationTest extends AbstractHttpDependencyResolutionTest {

    /*
    This is an elaborate test case to reproduce https://github.com/gradle/gradle/issues/3579
    Previously, there's a potential dead lock issue in parallel mode.
    - One thread holds the artifact cache lock here: https://github.com/gradle/gradle/blob/43ca9a869d5e26c895c418af48ca049b1313c49b/subprojects/dependency-management/src/main/java/org/gradle/api/internal/artifacts/query/DefaultArtifactResolutionQuery.java#L116
    and then starts dependency resolution, waits for ProducerGuard's lock here: https://github.com/gradle/gradle/blob/b82c6e2bd7cbc712d56cfcb2fe41a61c20329510/subprojects/dependency-management/src/main/java/org/gradle/internal/resource/transfer/DefaultCacheAwareExternalResourceAccessor.java#L82
    - Another thread holds ProducerGuard's lock and try to access artifact cache and get the artifact cache lock here: https://github.com/gradle/gradle/blob/b82c6e2bd7cbc712d56cfcb2fe41a61c20329510/subprojects/dependency-management/src/main/java/org/gradle/internal/resource/transfer/DefaultCacheAwareExternalResourceAccessor.java#L86

    Here's how it works:

    - Thread 1, task resolve locks ProducerGuard key and requests POM, which will block for 1 second.
    - Thread 2, after 500ms sleep, task query locks artifact cache and want to lock ProducerGuard key
    - Thread 1, when POM request returns, it tries to lock artifact cache because it needs to write back to artifact cache
    - Dead lock happens
     */

    @Issue('https://github.com/gradle/gradle/issues/3579')
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    def 'dead lock should not happen'() {
        given:
        def module = mavenHttpRepo.module('group', "artifact", '1.0').publish()
        module.pom.expectGetBlocking(1)
        module.artifact.expectGet()
        settingsFile << 'include "query", "resolve"'
        buildFile << """ 
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier 

allprojects {
    apply plugin: 'java'
    repositories {
       maven { url '${mavenHttpRepo.uri}' }
    }
    
    dependencies {
        compile 'group:artifact:1.0'
    }
}

project('query') {
    task query {
        doLast {
            Thread.sleep(500)
            dependencies.createArtifactResolutionQuery()
                        .forComponents(new DefaultModuleComponentIdentifier('group','artifact','1.0'))
                        .withArtifacts(JvmLibrary)
                        .execute()
        }
    }    
}

project('resolve') {
    task resolve {
        doLast {
            configurations.compile.files.collect { it.file }
        }
    }  
}
"""
        executer.requireOwnGradleUserHomeDir().requireIsolatedDaemons()

        expect:
        succeeds('query:query', ':resolve:resolve', '--parallel')
    }
}
