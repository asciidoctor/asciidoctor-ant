/*
 * Copyright 2014 Benoît Prioux
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.asciidoctor.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class AntExecutor {

    private String buildXml;
    private Map<String, String> properties = new HashMap<String, String>();

    public AntExecutor(String buildxml) {
        this.buildXml = buildxml;
    }

    public void setProperty(String name, String value) {
        properties.put(name, value);
    }

    public void executeAntTask() {
        executeAntTask(null);
    }

    public void executeAntTask(String target) {
        DefaultLogger consoleLogger = getConsoleLogger();

        // Prepare Ant project
        Project project = new Project();
        File buildFile = new File(buildXml);
        project.setUserProperty("ant.file", buildFile.getAbsolutePath());
        project.addBuildListener(consoleLogger);

        // Capture event for Ant script build start / stop / failure
        try {
            project.fireBuildStarted();
            project.init();
            ProjectHelper projectHelper = ProjectHelper.getProjectHelper();
            project.addReference("ant.projectHelper", projectHelper);
            projectHelper.parse(project, buildFile);

            // If no target specified then default target will be executed.
            String targetToExecute = (target != null && target.trim().length() > 0) ? target.trim() : project.getDefaultTarget();
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                project.setProperty(entry.getKey(), entry.getValue());
            }
            project.executeTarget(targetToExecute);
            project.fireBuildFinished(null);
        } catch (BuildException buildException) {
            project.fireBuildFinished(buildException);
            throw new RuntimeException(buildException);
        }
    }

    private static DefaultLogger getConsoleLogger() {
        DefaultLogger consoleLogger = new DefaultLogger();
        consoleLogger.setErrorPrintStream(System.err);
        consoleLogger.setOutputPrintStream(System.out);
        consoleLogger.setMessageOutputLevel(Project.MSG_INFO);
        return consoleLogger;
    }
}
