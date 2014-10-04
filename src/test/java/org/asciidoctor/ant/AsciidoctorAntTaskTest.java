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

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(JUnitParamsRunner.class)
public class AsciidoctorAntTaskTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private String buildXml(String fileName) {
        URL resource = Thread.currentThread().getContextClassLoader().getResource(fileName);
        if (resource == null) {
            throw new IllegalStateException(fileName + " can not be found in classpath");
        }
        return resource.getFile();
    }

    private String sourceDirectory(String file) {
        URL resource = Thread.currentThread().getContextClassLoader().getResource(file);
        if (resource == null) {
            throw new IllegalStateException("Can not find source directory for document : " + file);
        }
        return new File(resource.getFile()).getParent();
    }

    @Test
    @Parameters({
            "docbook, simple.xml, <?xml version=\"1.0\" encoding=\"UTF-8\"?>",
            "html5, simple.html, <!DOCTYPE html>"
    })
    public void should_manage_backend(String backend, String outputFile, String expectedContent) throws IOException {
        AntExecutor antExecutor = new AntExecutor(buildXml("build-asciidoctor.xml"));
        File target = folder.newFolder("asciidoctor");
        String outputDirectory = target.getAbsolutePath();
        String document = "simple.adoc";
        antExecutor.setProperty("sourceDirectory", sourceDirectory(document));
        antExecutor.setProperty("outputDirectory", outputDirectory);
        antExecutor.setProperty("sourceDocumentName", document);
        antExecutor.setProperty("backend", backend);

        antExecutor.executeAntTask();

        File out = new File(outputDirectory, outputFile);
        assertThat(out).exists();
        assertThat(IOUtils.toString(new FileInputStream(out))).startsWith(expectedContent);
    }
}