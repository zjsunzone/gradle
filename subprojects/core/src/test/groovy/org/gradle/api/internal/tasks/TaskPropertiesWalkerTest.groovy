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

package org.gradle.api.internal.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.tasks.Destroys
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

class TaskPropertiesWalkerTest extends AbstractProjectBuilderSpec {

    def visitor = Mock(InputsOutputVisitor)

    def "visits inputs"() {
        def task = project.tasks.create("myTask", MyTask)

        when:
        visitInputs(task)

        then:
        1 * visitor.visitInputProperty({ it.propertyName == 'myProperty' && it.value == 'myValue' })
        1 * visitor.visitInputFileProperty({ it.propertyName == 'inputFile' })
        1 * visitor.visitInputFileProperty({ it.propertyName == 'inputFiles' })
        1 * visitor.visitInputProperty({ it.propertyName == 'bean.class' && it.value == NestedBean.name })
        1 * visitor.visitInputProperty({ it.propertyName == 'bean.nestedInput' && it.value == 'nested' })
        1 * visitor.visitInputFileProperty({ it.propertyName == 'bean.inputDir' })

        1 * visitor.visitOutputFileProperty({ it.propertyName == 'outputFile' && it.value.value.path == 'output' })
        1 * visitor.visitOutputFileProperty({ it.propertyName == 'bean.outputDir' && it.value.value.path == 'outputDir' })

        1 * visitor.visitDestroyable({ it.propertyName == 'destroyed' && it.value.path == 'destroyed' })

        0 * _
    }

    def "nested bean with null value is detected"() {
        def task = project.tasks.create("myTask", MyTask)
        task.bean = null

        when:
        visitInputs(task)

        then:
        1 * visitor.visitInputProperty({ it.propertyName == 'bean.class' && it.value == null })
    }

    private visitInputs(MyTask task) {
        def specFactory = new DefaultPropertySpecFactory(task, TestFiles.resolver())
        new TaskPropertiesWalker([]).visitInputs(specFactory, visitor, task)
    }

    static class MyTask extends DefaultTask {

        @Input
        String myProperty = "myValue"

        @InputFile
        File inputFile = new File("some-location")

        @InputFiles
        FileCollection inputFiles = new SimpleFileCollection([new File("files")])

        @OutputFile
        File outputFile = new File("output")

        @Nested
        Object bean = new NestedBean()

        @Destroys
        File destroyed = new File('destroyed')

    }

    static class NestedBean {
        @Input
        String nestedInput = 'nested'

        @InputDirectory
        File inputDir

        @OutputDirectory
        File outputDir = new File('outputDir')
    }

}
