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
package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

class StrictDependenciesIntegrationTest extends AbstractIntegrationSpec {
    def resolve = new ResolveTestFixture(buildFile, "conf")


    def setup() {
        settingsFile << "rootProject.name = 'test'"
        resolve.prepare()
    }


    void "can declare a strict dependency onto an external component"() {
        given:
        mavenRepo.module("org", "foo", '1.0').publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:foo:1.0') {
                    prefers '1.0'
                    accepts '1.0'
                }
            }           
        """

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:foo:1.0")
            }
        }

    }

    void "can avoid declaring preference is version is set"() {
        given:
        mavenRepo.module("org", "foo", '1.0').publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:foo:1.0') {
                    accepts '1.0'
                }
            }           
        """

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:foo:1.0")
            }
        }

    }

    void "can avoid declaring version is prefers is set"() {
        given:
        mavenRepo.module("org", "foo", '1.0').publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:foo') {
                    prefers '1.0'
                    accepts '1.0'
                }
            }           
        """

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:foo:1.0")
            }
        }

    }

    void "should fail if transitive dependency version doesn't match the strict dependency version"() {
        given:
        def foo10 = mavenRepo.module("org", "foo", '1.0').publish()
        def foo11 = mavenRepo.module("org", "foo", '1.1').publish()
        mavenRepo.module('org', 'bar', '1.0')
            .dependsOn(foo11)
            .publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:foo') {
                    prefers '1.0'
                    accepts '1.0'
                }
                conf('org:bar:1.0')
            }           
        """

        when:
        fails 'checkDeps'

        then:
        failure.assertHasCause('Bad luck')

    }

    void "should pass if transitive dependency version matches exactly the strict dependency version"() {
        given:
        def foo10 = mavenRepo.module("org", "foo", '1.0').publish()
        mavenRepo.module('org', 'bar', '1.0')
            .dependsOn(foo10)
            .publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:foo') {
                    prefers '1.0'
                    accepts '1.0'
                }
                conf('org:bar:1.0')
            }           
        """

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge "org:foo:1.0", "org:foo:1.0"
                edge ("org:bar:1.0", "org:bar:1.0") {
                    edge "org:foo:1.0", "org:foo:1.0"
                }
            }
        }
    }

    void "can upgrade a non-strict dependency"() {
        given:
        def foo10 = mavenRepo.module("org", "foo", '1.0').publish()
        def foo11 = mavenRepo.module("org", "foo", '1.1').publish()
        mavenRepo.module('org', 'bar', '1.0')
            .dependsOn(foo10)
            .publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:foo') {
                    prefers '1.1'
                    accepts '1.1'
                }
                conf('org:bar:1.0')
            }           
        """

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge "org:foo:1.1", "org:foo:1.1"
                edge ("org:bar:1.0", "org:bar:1.0") {
                    edge ("org:foo:1.0", "org:foo:1.1").byConflictResolution()
                }
            }
        }
    }

    void "should pass if transitive dependency version matches a strict dependency version range"() {
        given:
        def foo10 = mavenRepo.module("org", "foo", '1.0').publish()
        def foo11 = mavenRepo.module("org", "foo", '1.1').publish()
        def foo12 = mavenRepo.module("org", "foo", '1.2').publish()
        def foo13 = mavenRepo.module("org", "foo", '1.3').publish()
        mavenRepo.module('org', 'bar', '1.0')
            .dependsOn(foo12)
            .publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:foo') {
                    prefers '[1.0,1.3]'
                    accepts '[1.0,1.3]'
                }
                conf('org:bar:1.0')
            }
            
            task checkDeps {
                doLast {
                    def files = configurations.conf*.name.sort()
                    assert files == ['bar-1.0.jar', 'foo-1.2.jar']
                }
            }                  
        """

        when:
        run 'checkDeps'

        then:
        /*
        // wanted really this but the test fixture / ResolvedConfiguration#getFirstLevelModuleDependencies doesn't seem to handle the range
        resolve.expectGraph {
            root(":", ":test:") {
                edge "org:foo:[1.0,1.3]", "org:foo:1.2"
                edge ("org:bar:1.0", "org:bar:1.0") {
                    edge "org:foo:1.2", "org:foo:1.2"
                }
            }
        }*/
        noExceptionThrown()
    }

    void "can find a compromise when accepts is different from prefers"() {
        given:
        def foo10 = mavenRepo.module("org", "foo", '1.0').publish()
        def foo11 = mavenRepo.module("org", "foo", '1.1').publish()
        def foo12 = mavenRepo.module("org", "foo", '1.2').publish()
        def foo13 = mavenRepo.module("org", "foo", '1.3').publish()
        def foo20 = mavenRepo.module("org", "foo", '2.0').publish()
        mavenRepo.module('org', 'bar', '1.0')
            .dependsOn(mavenRepo.module("org", "foo", "[1.0,)"))
            .publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:foo') {
                    prefers '1.0'
                    accepts '[1.0,2.0)'
                }
                conf('org:bar:1.0')
            }
            
            task checkDeps {
                doLast {
                    def files = configurations.conf*.name.sort()
                    assert files == ['bar-1.0.jar', 'foo-1.3.jar']
                }
            }                  
        """

        when:
        run 'checkDeps'

        then:
        /*
        // wanted really this but the test fixture / ResolvedConfiguration#getFirstLevelModuleDependencies doesn't seem to handle the range
        resolve.expectGraph {
            root(":", ":test:") {
                edge "org:foo:[1.0,1.3]", "org:foo:1.2"
                edge ("org:bar:1.0", "org:bar:1.0") {
                    edge "org:foo:1.2", "org:foo:1.2"
                }
            }
        }*/
        noExceptionThrown()
    }

    def "should not downgrade dependency version when a transitive dependency has strict version"() {
        given:
        mavenRepo.module("org", "foo", '15').publish()
        mavenRepo.module("org", "foo", '17').publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:foo:17')
                conf project(path: 'other', configuration: 'conf')
            }                       
        """
        file("other/build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:foo') {
                    prefers '15'
                    accepts '15'
                }
            }       
        """
        settingsFile << "\ninclude 'other'"

        when:
        fails 'checkDeps'

        then:
        failure.assertHasCause('Bad luck')

    }

    def "should fail if 2 strict versions disagree"() {
        given:
        mavenRepo.module("org", "foo", '15').publish()
        mavenRepo.module("org", "foo", '17').publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:foo') {
                    prefers '17'
                    accepts '17'
                }
                conf project(path: 'other', configuration: 'conf')
            }                       
        """
        file("other/build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:foo:15') {
                    prefers '15'
                    accepts '15'
                }
            }       
        """
        settingsFile << "\ninclude 'other'"

        when:
        fails 'checkDeps'

        then:
        failure.assertHasCause('Bad luck')

    }

    void "should pass if strict version ranges overlap"() {
        given:
        def foo10 = mavenRepo.module("org", "foo", '1.0').publish()
        def foo11 = mavenRepo.module("org", "foo", '1.1').publish()
        def foo12 = mavenRepo.module("org", "foo", '1.2').publish()
        def foo13 = mavenRepo.module("org", "foo", '1.3').publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:foo') {
                    prefers '[1.0,1.2]'
                    accepts '[1.0,1.2]'
                }
                conf project(path:'other', configuration: 'conf')
            }
            
            task checkDeps {
                doLast {
                    def files = configurations.conf*.name.sort()
                    assert files == ['foo-1.2.jar']
                }
            }                  
        """
        file("other/build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:foo:[1.1,1.3]') {
                    prefers '[1.1,1.3]'
                    accepts '[1.1,1.3]'
                }
            }       
        """
        settingsFile << "\ninclude 'other'"

        when:
        run 'checkDeps'

        then:
        noExceptionThrown()
    }

}
