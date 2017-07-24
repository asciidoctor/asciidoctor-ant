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
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(JUnitParamsRunner.class)
public class AsciidoctorAntTaskTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private AntExecutor antExecutor = new AntExecutor(buildXml("build-asciidoctor.xml"));

    @Test
    @Parameters({
            "docbook, simple.xml, <?xml version=\"1.0\" encoding=\"UTF-8\"?>",
            "html5, simple.html, <!DOCTYPE html>"
    })
    public void should_manage_backend(String backend, String outputFile, String expectedContent) throws IOException {
        String outputDirectory = outputDirectory("asciidoctor");
        String document = "simple.adoc";
        antExecutor.setProperties(initProperties(sourceDirectory(document), outputDirectory, backend, document));

        antExecutor.executeAntTask("asciidoctor");

        File out = new File(outputDirectory, outputFile);
        assertThat(out).exists();
        assertThat(IOUtils.toString(new FileInputStream(out))).startsWith(expectedContent);
    }

    @Test
    public void should_manage_backend_pdf() throws IOException {
        String outputDirectory = outputDirectory("asciidoctor");
        String document = "simple.adoc";
        antExecutor.setProperties(initProperties(sourceDirectory(document), outputDirectory, "pdf", document));

        antExecutor.executeAntTask("asciidoctor");

        File out = new File(outputDirectory, "simple.pdf");
        assertThat(out).exists();
    }

    @Test
    public void should_register_asciidoctor_extension() throws IOException {
        String outputDirectory = outputDirectory("asciidoctor-twitter");
        String document = "twitter.adoc";
        antExecutor.setProperties(initProperties(sourceDirectory(document), outputDirectory, "html5", document));

        antExecutor.executeAntTask("asciidoctor-twitter");

        File out = new File(outputDirectory, "twitter.html");
        assertThat(out).exists();
        assertThat(IOUtils.toString(new FileInputStream(out))).contains("<a href=\"http://www.twitter.com/@binout\">@binout</a>");
    }

    @Test
    public void should_use_asciidoctor_diagram() throws IOException {
        String outputDirectory = outputDirectory("asciidoctor-diagram");
        String document = "diagram.adoc";
        antExecutor.setProperties(initProperties(sourceDirectory(document), outputDirectory, "html5", document));

        antExecutor.executeAntTask("asciidoctor-diagram");

        File out = new File(outputDirectory, "diagram.html");
        assertThat(out).exists();
        assertThat(IOUtils.toString(new FileInputStream(out))).contains("width=\"550\" height=\"182\"");
    }

    @Test
    public void should_manage_template_dir_and_require() throws IOException {
        String outputDirectory = outputDirectory("asciidoctor-slide");
        String document = "slide.adoc";
        antExecutor.setProperties(initProperties(sourceDirectory(document), outputDirectory, "deckjs", document));

        antExecutor.executeAntTask("asciidoctor-slide");

        File out = new File(outputDirectory, "slide.html");
        assertThat(out).exists();
        assertThat(IOUtils.toString(new FileInputStream(out))).contains("deck.js");
    }

    @Test
    public void should_manage_extensions() throws IOException {
        String outputDirectory = outputDirectory("asciidoctor-ext");
        antExecutor.setProperties(initProperties(sourceDirectory("extensions/simple.txt"), outputDirectory, "docbook"));

        antExecutor.executeAntTask("asciidoctor-txt");

        assertThat(new File(outputDirectory, "simple.xml")).exists();
        assertThat(new File(outputDirectory, "_ignore.xml")).doesNotExist();
    }

    @Test
    public void should_manage_attributes() throws IOException {
        String outputDirectory = outputDirectory("asciidoctor-title");
        String document = "simple.adoc";
        antExecutor.setProperties(initProperties(sourceDirectory(document), outputDirectory, "docbook", document));

        antExecutor.executeAntTask("asciidoctor-title");

        File out = new File(outputDirectory, "simple.xml");
        assertThat(out).exists();
        assertThat(IOUtils.toString(new FileInputStream(out))).contains("<title>Title from build.xml</title>");
    }

    @Test
    public void should_manage_preserveDirectories() throws IOException {
        String outputDirectory = outputDirectory("asciidoctor-preserveDirectories");
        antExecutor.setProperties(initProperties(sourceDirectory("rootfolder/index.adoc"), outputDirectory, "html5"));

        antExecutor.executeAntTask("asciidoctor-preserveDirectories");

        assertThat(new File(outputDirectory, "index.html")).exists();
        assertThat(new File(outputDirectory, "subfolder/another.html")).exists();
    }

    @Test
    public void should_manage_resources() throws IOException {
        String outputDirectory = outputDirectory("asciidoctor-resources");
        String document = "simple.adoc";
        antExecutor.setProperties(initProperties(sourceDirectory(document), outputDirectory, "html5", document));

        antExecutor.executeAntTask("asciidoctor-resources");

        assertThat(new File(outputDirectory, "simple.html")).exists();
        assertThat(new File(outputDirectory, "images/ftw.jpg")).exists();
    }

    @Test
    public void should_manage_relativebasedir() throws IOException {
        String outputDirectory = outputDirectory("asciidoctor-relativebasedir");
        String document = "including.ad";
        antExecutor.setProperties(initProperties(sourceDirectory(document), outputDirectory, "html5", document));

        antExecutor.executeAntTask("asciidoctor-relativebasedir");

        // out is created in a new folder with basedir=sourceDir
        File out = new File(sourceDirectory(document) + outputDirectory, "including.html");
        assertThat(out).exists();
        assertThat(IOUtils.toString(new FileInputStream(out))).contains("More text");
    }

  @Test
    public void should_accept_safemode() throws IOException {
        String outputDirectory = outputDirectory("asciidoctor-safemode");
        String document = "simple.adoc";
        antExecutor.setProperties(initProperties(sourceDirectory(document), outputDirectory, "html5", document));
        
        antExecutor.executeAntTask("asciidoctor-safemode");

        assertThat(new File(outputDirectory, "simple.html")).exists();
    }
    
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

    private String outputDirectory(String dirName) throws IOException {
        File target = folder.newFolder(dirName);
        return target.getAbsolutePath();
    }

    private Map<String, String> initProperties(String sourceDirectory, String outputDirectory, String backend) {
        Map<String, String> properties = new HashMap<String, String>();
        properties.put("baseDir", "/");
        properties.put("sourceDirectory", sourceDirectory);
        properties.put("outputDirectory", outputDirectory);
        properties.put("backend", backend);
        return properties;
    }

    private Map<String, String> initProperties(String sourceDirectory, String outputDirectory, String backend, String sourceDocumentName) {
        Map<String, String> properties = initProperties(sourceDirectory,outputDirectory, backend);
        properties.put("sourceDocumentName", sourceDocumentName);
        return properties;
    }
}