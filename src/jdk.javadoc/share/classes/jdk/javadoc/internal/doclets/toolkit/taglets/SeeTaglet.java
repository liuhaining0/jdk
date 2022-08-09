/*
 * Copyright (c) 2001, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.javadoc.internal.doclets.toolkit.taglets;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.SeeTree;
import jdk.javadoc.doclet.Taglet.Location;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocFinder;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

/**
 * A taglet that represents the {@code @see} tag.
 */
public class SeeTaglet extends BaseTaglet implements InheritableTaglet {

    public SeeTaglet() {
        super(DocTree.Kind.SEE, false, EnumSet.allOf(Location.class));
    }

    @Override
    public void inherit(DocFinder.Input input, DocFinder.Output output) {
        List<? extends SeeTree> tags = input.utils.getSeeTrees(input.element);
        if (!tags.isEmpty()) {
            CommentHelper ch = input.utils.getCommentHelper(input.element);
            output.holder = input.element;
            // TODO investigate: this seems to inherit the first @see tag only
            output.holderTag = tags.get(0);
            output.inlineTags = input.isFirstSentence
                    ? ch.getFirstSentenceTrees(output.holderTag)
                    : ch.getReference(output.holderTag);
        }
    }

    @Override
    public Content getAllBlockTagOutput(Element holder, TagletWriter writer) {
        Utils utils = writer.configuration().utils;
        List<? extends SeeTree> tags = utils.getSeeTrees(holder);
        Element e = holder;
        if (utils.isMethod(holder)) {
            Optional<Result> result = DocFinder.search(
                    (ExecutableElement) holder, m -> extract(utils, m), writer.configuration());
            if (result.isPresent()) {
                ExecutableElement m = result.get().method();
                tags = utils.getSeeTrees(m);
                e = m;
            }
        }
        return writer.seeTagOutput(e, tags);
    }

    private record Result(List<? extends SeeTree> seeTrees, ExecutableElement method) { }

    private static Optional<Result> extract(Utils utils, ExecutableElement method) {
        List<? extends SeeTree> tags = utils.getSeeTrees(method);
        if (tags.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Result(tags, method));
    }
}
