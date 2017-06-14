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

package org.gradle.workers.internal

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter

import static org.gradle.util.TextUtil.normaliseFileSeparators

class WorkerDaemonIntegrationTest extends AbstractWorkerExecutorIntegrationTest {
    def "sets the working directory to the project directory by default during worker execution"() {
        withRunnableClassInBuildScript()
        buildFile << """
            import org.gradle.workers.IsolationMode

            $runnableThatPrintsWorkingDirectory

            task runInWorker(type: WorkerTask) {
                isolationMode = IsolationMode.PROCESS
                runnableClass = WorkingDirRunnable.class
            }
        """

        when:
        args("--info")
        def gradle = executer.withTasks("runInWorker").start()

        then:
        gradle.waitForFinish()

        and:
        gradle.standardOutput.readLines().find { normaliseFileSeparators(it).matches "Starting process 'Gradle Worker Daemon \\d+'. Working directory: " + normaliseFileSeparators(executer.gradleUserHomeDir.file("workers").getAbsolutePath()) + ".*" }

        and:
        gradle.standardOutput.contains("Execution working dir: " + testDirectory.getAbsolutePath())

        and:
        GradleContextualExecuter.daemon || gradle.standardOutput.contains("Shutdown working dir: " + executer.gradleUserHomeDir.file("workers").getAbsolutePath())
    }

    def "sets the working directory to the specified directory during worker execution"() {
        withRunnableClassInBuildScript()
        buildFile << """
            import org.gradle.workers.IsolationMode

            $runnableThatPrintsWorkingDirectory

            task runInWorker(type: WorkerTask) {
                isolationMode = IsolationMode.PROCESS
                runnableClass = WorkingDirRunnable.class
                additionalForkOptions = { it.workingDir = project.file("workerDir") }
            }
        """
        testDirectory.file("workerDir").createDir()

        when:
        args("--info")
        def gradle = executer.withTasks("runInWorker").start()

        then:
        gradle.waitForFinish()

        and:
        gradle.standardOutput.readLines().find { normaliseFileSeparators(it).matches "Starting process 'Gradle Worker Daemon \\d+'. Working directory: " + normaliseFileSeparators(executer.gradleUserHomeDir.file("workers").getAbsolutePath()) + ".*" }

        and:
        gradle.standardOutput.contains("Execution working dir: " + testDirectory.file("workerDir").getAbsolutePath())

        and:
        GradleContextualExecuter.daemon || gradle.standardOutput.contains("Shutdown working dir: " + executer.gradleUserHomeDir.file("workers").getAbsolutePath())
    }

    def getRunnableThatPrintsWorkingDirectory() {
        return """
            class WorkingDirRunnable extends TestRunnable {
                @Inject
                public WorkingDirRunnable(List<String> files, File outputDir, Foo foo) {
                    super(files, outputDir, foo);
                    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                        @Override
                        public void run() {
                            printWorkingDirectory("Shutdown")
                        }
                    }));
                }
                
                public void run() {
                    super.run()
                    printWorkingDirectory("Execution")
                }
                
                void printWorkingDirectory(String phase) {
                    println phase + " working dir: " + System.getProperty("user.dir")
                }
            }
        """
    }
}
