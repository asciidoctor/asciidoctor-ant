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

import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;
import org.asciidoctor.*;
import org.asciidoctor.internal.JRubyRuntimeContext;
import org.asciidoctor.internal.RubyUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class AsciidoctorAntTask extends Task {

    private String sourceDirectory;
    private String outputDirectory;
    private boolean preserveDirectories = false;

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

    private List<FileSet> resources = new ArrayList<FileSet>();
    private List<Attribute> attributes = new ArrayList<Attribute>();

    private List<Extension> preProcessors = new ArrayList<Extension>();
    private List<Extension> treeProcessors = new ArrayList<Extension>();
    private List<Extension> postProcessors = new ArrayList<Extension>();
    private List<Extension> blockProcessors = new ArrayList<Extension>();
    private List<Extension> blockMacroProcessors = new ArrayList<Extension>();
    private List<Extension> inlineMacroProcessors = new ArrayList<Extension>();
    private List<Extension> includeProcessors = new ArrayList<Extension>();

    private List<RubyLibrary> requires = new ArrayList<RubyLibrary>();
    private String gemPath;

    @Override
    public void execute() throws BuildException {
        checkMandatoryParameter("sourceDirectory", sourceDirectory);
        checkMandatoryParameter("outputDirectory", outputDirectory);

        ensureOutputExists();

        Asciidoctor asciidoctor = createAsciidoctor(gemPath);
        registerAdditionalRubyLibraries();
        registerExtensions(asciidoctor);

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

        try {
            for (FileSet resource : resources) {
                File resourceDir = resource.getDir();
                String destPath = resourceDir.getCanonicalPath().substring(new File(sourceDirectory).getCanonicalPath().length());
                File destResourceDir = new File(outputDirectory, destPath);
                destResourceDir.mkdirs();
                String[] includedFiles = resource.getDirectoryScanner(getProject()).getIncludedFiles();
                FileUtils.copyDirectory(resourceDir, destResourceDir, new ResourceFileFilter(includedFiles), false);
            }
        } catch (IOException e) {
            throw new BuildException("Error copying resources", e);
        }
    }

    private void registerAdditionalRubyLibraries() {
        for (RubyLibrary require : requires) {
            // FIXME AsciidoctorJ should provide a public API for requiring paths in the Ruby runtime
            RubyUtils.requireLibrary(JRubyRuntimeContext.get(), require.getName());
        }
    }

    private void registerExtensions(Asciidoctor asciidoctor) {
        for (Extension preProcessor : preProcessors) {
            asciidoctor.javaExtensionRegistry().preprocessor(preProcessor.getClassName());
        }
        for (Extension treeProcessor : treeProcessors) {
            asciidoctor.javaExtensionRegistry().treeprocessor(treeProcessor.getClassName());
        }
        for (Extension postProcessor : postProcessors) {
            asciidoctor.javaExtensionRegistry().postprocessor(postProcessor.getClassName());
        }
        for (Extension blockProcessor : blockProcessors) {
            asciidoctor.javaExtensionRegistry().block(blockProcessor.getBlockName(), blockProcessor.getClassName());
        }
        for (Extension blockMacroProcessor : blockMacroProcessors) {
            asciidoctor.javaExtensionRegistry().blockMacro(blockMacroProcessor.getBlockName(), blockMacroProcessor.getClassName());
        }
        for (Extension inlineMacroProcessor : inlineMacroProcessors) {
            asciidoctor.javaExtensionRegistry().inlineMacro(inlineMacroProcessor.getBlockName(), inlineMacroProcessor.getClassName());
        }
        for (Extension includeProcessor : includeProcessors) {
            asciidoctor.javaExtensionRegistry().includeProcessor(includeProcessor.getClassName());
        }
    }

    private Asciidoctor createAsciidoctor(String gemPath) {
        ClassLoader oldTCCL = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            return getAsciidoctorInstance(gemPath);
        } finally {
            Thread.currentThread().setContextClassLoader(oldTCCL);
        }
    }

    private Asciidoctor getAsciidoctorInstance(String gemPath) {
        Asciidoctor asciidoctor;
        if (gemPath == null) {
            asciidoctor = Asciidoctor.Factory.create();
        }
        else {
            // Replace Windows path separator to avoid paths with mixed \ and /.
            // This happens for instance when setting: <gemPath>${project.build.directory}/gems-provided</gemPath>
            // because the project's path is converted to string.
            String normalizedGemPath = (File.separatorChar == '\\') ? gemPath.replaceAll("\\\\", "/") : gemPath;
            asciidoctor = Asciidoctor.Factory.create(normalizedGemPath);
        }

        String gemHome = JRubyRuntimeContext.get().evalScriptlet("ENV['GEM_HOME']").toString();
        String gemHomeExpected = (gemPath == null || "".equals(gemPath)) ? "" : gemPath.split(java.io.File.pathSeparator)[0];

        if (!"".equals(gemHome) && !gemHomeExpected.equals(gemHome)) {
            log("Using inherited external environment to resolve gems (" + gemHome + "), i.e. build is platform dependent!");
        }

        return asciidoctor;
    }

    private OptionsBuilder buildOptions() {
        OptionsBuilder optionsBuilder = OptionsBuilder.options();
        optionsBuilder.safe(SafeMode.SAFE).eruby(eruby);
        optionsBuilder.backend(backend).docType(doctype).compact(compact).headerFooter(headerFooter).mkDirs(true);
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
        try {
            if (preserveDirectories) {
                String proposalPath = sourceFile.getParentFile().getCanonicalPath().substring(new File(sourceDirectory).getCanonicalPath().length());
                File relativePath = new File(outputDirectory, proposalPath);
                relativePath.mkdirs();
                optionsBuilder.toDir(relativePath).destinationDir(relativePath);
            } else {
                File destinationDir = new File(outputDirectory);
                optionsBuilder.toDir(destinationDir).destinationDir(destinationDir);
            }
        } catch (IOException e) {
            throw new BuildException("Unable to locate output directory", e);
        }
    }

    private File computeBaseDir(File sourceFile) {
        File baseDirFile;
        if (baseDir != null) {
            baseDirFile = new File(baseDir);
        } else {
            // when preserveDirectories == false, parent and sourceDirectory are the same
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
    public void setPreserveDirectories(boolean preserveDirectories) {
        this.preserveDirectories = preserveDirectories;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void addResource(FileSet fileSet) {
        resources.add(fileSet);
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

    private static class ResourceFileFilter implements FileFilter {
        private final List<String> includedFiles;

        public ResourceFileFilter(String[] includedFiles) {
            this.includedFiles = Arrays.asList(includedFiles);
        }

        @Override
        public boolean accept(File pathname) {
           return includedFiles.contains(pathname.getName());
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public Extension createPreProcessor() {
        Extension extension = new Extension();
        preProcessors.add(extension);
        return extension;
    }

    @SuppressWarnings("UnusedDeclaration")
    public Extension createTreeProcessor() {
        Extension extension = new Extension();
        treeProcessors.add(extension);
        return extension;
    }

    @SuppressWarnings("UnusedDeclaration")
    public Extension createPostProcessor() {
        Extension extension = new Extension();
        postProcessors.add(extension);
        return extension;
    }

    @SuppressWarnings("UnusedDeclaration")
    public Extension createBlockProcessor() {
        Extension extension = new Extension();
        blockProcessors.add(extension);
        return extension;
    }

    @SuppressWarnings("UnusedDeclaration")
    public Extension createBlockMacroProcessor() {
        Extension extension = new Extension();
        blockMacroProcessors.add(extension);
        return extension;
    }

    @SuppressWarnings("UnusedDeclaration")
    public Extension createInlineMacroProcessor() {
        Extension extension = new Extension();
        inlineMacroProcessors.add(extension);
        return extension;
    }

    @SuppressWarnings("UnusedDeclaration")
    public Extension createIncludeProcessor() {
        Extension extension = new Extension();
        includeProcessors.add(extension);
        return extension;
    }

    public class Extension {
        private String className;
        private String blockName;

        public String getClassName() {
            return className;
        }

        public String getBlockName() {
            return blockName;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setClassName(String className) {
            this.className = className;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setBlockName(String blockName) {
            this.blockName = blockName;
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setGemPath(String gemPath) {
        this.gemPath = gemPath;
    }

    @SuppressWarnings("UnusedDeclaration")
    public RubyLibrary createRequire() {
        RubyLibrary rubyLibrary = new RubyLibrary();
        requires.add(rubyLibrary);
        return rubyLibrary;
    }

    public class RubyLibrary {
        private String name;

        public String getName() {
            return name;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setName(String name) {
            this.name = name;
        }
    }
}
