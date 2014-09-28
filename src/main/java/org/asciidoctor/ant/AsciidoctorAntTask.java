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
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.asciidoctor.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AsciidoctorAntTask extends Task {

    private String sourceDirectory;
    private String outputDirectory;

    private String sourceDocumentName;
    private String backend = "docbook";
    private String imagesDir = "images";
    private String doctype = "article";
    private boolean compact = false;
    private boolean headerFooter = true;
    private String sourceHighlighter;
    private boolean embedAssets = false;
    private String eruby = "";
    private String templateDir;
    private String templateEngine;
    private List<Attribute> attributes = new ArrayList<Attribute>();

    @Override
    public void execute() throws BuildException {
        checkMandatoryParameter("sourceDirectory", sourceDirectory);
        checkMandatoryParameter("outputDirectory", outputDirectory);

        ensureOutputExists();

        Asciidoctor asciidoctor = Asciidoctor.Factory.create(getClass().getClassLoader());

        AttributesBuilder attributesBuilder = buildAttributes();
        OptionsBuilder optionsBuilder = buildOptions();
        optionsBuilder.attributes(attributesBuilder.get());
        Options options = optionsBuilder.get();

        DirectoryWalker directoryWalker = new AsciiDocDirectoryWalker(sourceDirectory);
        List<File> asciidocFiles = directoryWalker.scan();

        if (sourceDocumentName == null) {
            log("Render asciidoc files from " + sourceDirectory + " to " + outputDirectory + " with backend=" + backend);
            for (File file : asciidocFiles) {
                asciidoctor.renderFile(file, options);
            }
        } else {
            log("Render "+ sourceDocumentName + " from " + sourceDirectory + " to " + outputDirectory + " with backend=" + backend);
            asciidoctor.renderFile(new File(sourceDirectory, sourceDocumentName), options);
        }
    }

    private OptionsBuilder buildOptions() {
        OptionsBuilder optionsBuilder = OptionsBuilder.options();
        optionsBuilder.safe(SafeMode.SAFE).eruby(eruby);
        optionsBuilder.baseDir(getProject().getBaseDir()).toDir(new File(outputDirectory));
        optionsBuilder.backend(backend).docType(doctype).compact(compact).headerFooter(headerFooter);
        if (templateEngine != null) {
            optionsBuilder.templateEngine(templateEngine);
        }
        if (templateDir != null) {
            optionsBuilder.templateDir(new File(templateDir));
        }
        return optionsBuilder;
    }

    private AttributesBuilder buildAttributes() {
        AttributesBuilder attributesBuilder = AttributesBuilder.attributes();
        attributesBuilder.imagesDir(imagesDir);
        if (sourceHighlighter != null) {
            attributesBuilder.sourceHighlighter(sourceHighlighter);
        }
        if (embedAssets) {
            attributesBuilder.linkCss(false);
            attributesBuilder.dataUri(true);
        }
        attributesBuilder.copyCss(false);
        // TODO Figure out how to reliably set other values (like boolean values, dates, times, etc)
        for (Attribute attribute : attributes) {
            if ("true".equals(attribute.getValue()) || "false".equals(attribute.getValue())) {
                attributesBuilder.attribute(attribute.getKey(), Attributes.toAsciidoctorFlag(Boolean.valueOf(attribute.getValue())));
                continue;
            }
            // Can't do anything about dates and times because all that logic is private in Attributes
            attributesBuilder.attribute(attribute.getKey(), attribute.getValue());
        }
        return attributesBuilder;
    }

    private void checkMandatoryParameter(String name, Object value) {
        if (value == null) {
            throw new BuildException(name + " is mandatory");
        }
    }

    private void ensureOutputExists() {
        File outputFile = new File(outputDirectory);
        if (!outputFile.exists()) {
            if (!outputFile.mkdirs()) {
                log("Can't create " + outputFile.getPath(), Project.MSG_ERR);
            }
        }
    }

    // Setters for Ant Task

    @SuppressWarnings("UnusedDeclaration")
    public void setSourceDirectory(String sourceDirectory) {
        this.sourceDirectory = sourceDirectory;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setSourceDocumentName(String sourceDocumentName) {
        this.sourceDocumentName = sourceDocumentName;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setBackend(String backend) {
        this.backend = backend;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setImagesDir(String imagesDir) {
        this.imagesDir = imagesDir;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setDoctype(String doctype) {
        this.doctype = doctype;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setCompact(boolean compact) {
        this.compact = compact;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setHeaderFooter(boolean headerFooter) {
        this.headerFooter = headerFooter;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setSourceHighlighter(String sourceHighlighter) {
        this.sourceHighlighter = sourceHighlighter;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setEmbedAssets(boolean embedAssets) {
        this.embedAssets = embedAssets;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setEruby(String eruby) {
        this.eruby = eruby;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setTemplateDir(String templateDir) {
        this.templateDir = templateDir;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setTemplateEngine(String templateEngine) {
        this.templateEngine = templateEngine;
    }

    @SuppressWarnings("UnusedDeclaration")
    public Attribute createAttribute() {
        Attribute attribute = new Attribute();
        attributes.add(attribute);
        return attribute;
    }

    public class Attribute {
        private String key;
        private String value;

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setKey(String key) {
            this.key = key;
        }
        @SuppressWarnings("UnusedDeclaration")
        public void setValue(String value) {
            this.value = value;
        }
    }

}
