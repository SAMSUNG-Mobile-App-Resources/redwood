/*
 * Copyright (C) 2022 Square, Inc.
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
package app.cash.redwood.gradle

import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor

@CacheableTask
internal abstract class RedwoodLintTask @Inject constructor(
  private val workerExecutor: WorkerExecutor,
) : DefaultTask() {
  @get:Classpath
  abstract val toolClasspath: ConfigurableFileCollection

  @get:Input
  abstract val projectDirectory: RegularFileProperty

  @get:Input
  abstract val sourceDirectories: Property<Collection<File>>

  @get:Classpath
  abstract val dependencies: ConfigurableFileCollection

  @TaskAction
  fun run() {
    val queue = workerExecutor.noIsolation()
    queue.submit(RedwoodLintWorker::class.java) {
      it.toolClasspath.from(toolClasspath)
      it.projectDirectory.set(projectDirectory)
      it.sourceDirectories.set(sourceDirectories)
      it.dependencies.setFrom(dependencies)
    }
  }
}

private interface RedwoodLintParameters : WorkParameters {
  val toolClasspath: ConfigurableFileCollection
  val projectDirectory: RegularFileProperty
  val sourceDirectories: Property<Collection<File>>
  val dependencies: ConfigurableFileCollection
}

private abstract class RedwoodLintWorker @Inject constructor(
  private val execOperations: ExecOperations,
) : WorkAction<RedwoodLintParameters> {
  override fun execute() {
    execOperations.javaexec { exec ->
      exec.classpath = parameters.toolClasspath
      exec.mainClass.set("app.cash.redwood.lint.Main")

      exec.args = mutableListOf<String>().apply {
        add(parameters.projectDirectory.get().asFile.absolutePath)

        for (file in parameters.sourceDirectories.get()) {
          add("-s")
          add(file.toString())
        }

        val dependencies = parameters.dependencies.files
        if (dependencies.isNotEmpty()) {
          add("-cp")
          add(dependencies.joinToString(File.pathSeparator))
        }
      }
    }
  }
}