/*
 * Copyright 2013 Benoît Prioux
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
import org.apache.tools.ant.Task;
import org.asciidoctor.*;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class AsciidoctorAntTask extends Task {

    private String sourceDirectory;
    private String outputDirectory;

    public void setSourceDirectory(String sourceDirectory) {
        this.sourceDirectory = sourceDirectory;
    }

    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    @Override
    public void execute() throws BuildException {
        Asciidoctor asciidoctor = Asciidoctor.Factory.create();

        Attributes attributes = new Attributes();
        Options options = new Options();
        options.setAttributes(attributes);

        DirectoryWalker directoryWalker = new AsciiDocDirectoryWalker(sourceDirectory);
        List<File> asciidocFiles = directoryWalker.scan();

        for (File file : asciidocFiles) {
            try {
                FileWriter fileWriter = new FileWriter(new File(outputDirectory, getOutputFilename(file)));
                asciidoctor.render(new FileReader(file), fileWriter, options);
            } catch (IOException e) {
                throw new BuildException(e);
            }
        }
    }

    private String getOutputFilename(File file) {
        String name = file.getName();
        int indexOfPoint = name.lastIndexOf(".");
        return name.substring(0, indexOfPoint) + ".html";
    }
}
