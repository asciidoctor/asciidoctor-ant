/*
 * Copyright 2015 Benoît Prioux
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
package org.asciidoctor.ant.extensions;

import org.asciidoctor.ast.AbstractBlock;
import org.asciidoctor.ast.Inline;
import org.asciidoctor.extension.InlineMacroProcessor;

import java.util.HashMap;
import java.util.Map;

public class TwitterMacro extends InlineMacroProcessor {

    public TwitterMacro(String macroName, Map<String, Object> config) {
        super(macroName, config);
    }

    @Override
    public Object process(AbstractBlock parent, String twitterHandle, Map<String, Object> attributes) {
        // Define options for an 'anchor' element.
        Map<String, Object> options = new HashMap<String, Object>();
        options.put("type",":link");
        options.put("target", "http://www.twitter.com/@"+twitterHandle);

        // Prepend twitterHandle with @ as text link.
        final Inline inlineTwitterLink = createInline(parent, "anchor", "@"+twitterHandle, attributes, options);

        // Convert to String value.
        return inlineTwitterLink.convert();
    }
}
