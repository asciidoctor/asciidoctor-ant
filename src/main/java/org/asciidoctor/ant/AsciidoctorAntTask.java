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
import java.util.Arrays;
import java.util.Iterator;
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
    private String baseDir;
    private boolean relativeBaseDir = false;
    private String extensions;
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

        if (sourceDocumentName == null) {
            log("Render asciidoc files from " + sourceDirectory + " to " + outputDirectory + " with backend=" + backend);
            for (File file : scanSourceFiles()) {
                setDestinationPaths(optionsBuilder, file);
                asciidoctor.renderFile(file, optionsBuilder.get());
            }
        } else {
            log("Render " + sourceDocumentName + " from " + sourceDirectory + " to " + outputDirectory + " with backend=" + backend);
            File file = new File(sourceDirectory, sourceDocumentName);
            setDestinationPaths(optionsBuilder, file);
            asciidoctor.renderFile(file, optionsBuilder.get());
        }
    }

    private OptionsBuilder buildOptions() {
        OptionsBuilder optionsBuilder = OptionsBuilder.options();
        optionsBuilder.safe(SafeMode.SAFE).eruby(eruby);
        optionsBuilder.backend(backend).docType(doctype).compact(compact).headerFooter(headerFooter);
        if (templateEngine != null) {
            optionsBuilder.templateEngine(templateEngine);
        }
        if (templateDir != null) {
            optionsBuilder.templateDir(new File(templateDir));
        }
        return optionsBuilder;
    }

    private void setDestinationPaths(OptionsBuilder optionsBuilder, final File sourceFile)  {
        optionsBuilder.baseDir(computeBaseDir(sourceFile));
        optionsBuilder.toDir(new File(outputDirectory));
    }

    private File computeBaseDir(File sourceFile) {
        File baseDirFile;
        if (baseDir != null) {
            baseDirFile = new File(baseDir);
        } else {
            if (relativeBaseDir) {
                baseDirFile = sourceFile.getParentFile();
            } else {
                baseDirFile = getProject().getBaseDir();
            }
        }
        return baseDirFile;
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

    private List<File> scanSourceFiles() {
        final List<File> asciidoctorFiles;
        String absoluteSourceDirectory = sourceDirectory;
        if (extensions == null || extensions.isEmpty()) {
            final DirectoryWalker directoryWalker = new AsciiDocDirectoryWalker(absoluteSourceDirectory);
            asciidoctorFiles = directoryWalker.scan();
        } else {
            final DirectoryWalker directoryWalker = new CustomExtensionDirectoryWalker(absoluteSourceDirectory, Arrays.asList(extensions.split(",")));
            asciidoctorFiles = directoryWalker.scan();
        }
        for (Iterator<File> iter = asciidoctorFiles.iterator(); iter.hasNext();) {
            File f = iter.next();
            do {
                // stop when we hit the source directory root
                if (absoluteSourceDirectory.equals(f.getAbsolutePath())) {
                    break;
                }
                // skip if the filename or directory begins with _
                if (f.getName().startsWith("_")) {
                    iter.remove();
                    break;
                }
            } while ((f = f.getParentFile()) != null);
        }
        return asciidoctorFiles;
    }

    private static class CustomExtensionDirectoryWalker extends AbstractDirectoryWalker {
        private final List<String> extensions;

        public CustomExtensionDirectoryWalker(final String absolutePath, final List<String> extensions) {
            super(absolutePath);
            this.extensions = extensions;
        }

        @Override
        protected boolean isAcceptedFile(final File filename) {
            final String name = filename.getName();
            for (final String extension : extensions) {
                if (name.endsWith(extension)) {
                    return true;
                }
            }
            return false;
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
    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setRelativeBaseDir(boolean relativeBaseDir) {
        this.relativeBaseDir = relativeBaseDir;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setExtensions(String extensions) {
        this.extensions = extensions;
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
