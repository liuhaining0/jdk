/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.util;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import com.sun.source.doctree.AttributeTree;
import com.sun.source.doctree.AttributeTree.ValueKind;
import com.sun.source.doctree.AuthorTree;
import com.sun.source.doctree.BlockTagTree;
import com.sun.source.doctree.CommentTree;
import com.sun.source.doctree.DeprecatedTree;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.EndElementTree;
import com.sun.source.doctree.EntityTree;
import com.sun.source.doctree.IdentifierTree;
import com.sun.source.doctree.InlineTagTree;
import com.sun.source.doctree.LinkTree;
import com.sun.source.doctree.LiteralTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.doctree.ProvidesTree;
import com.sun.source.doctree.ReferenceTree;
import com.sun.source.doctree.ReturnTree;
import com.sun.source.doctree.SeeTree;
import com.sun.source.doctree.SerialDataTree;
import com.sun.source.doctree.SerialFieldTree;
import com.sun.source.doctree.SerialTree;
import com.sun.source.doctree.SinceTree;
import com.sun.source.doctree.StartElementTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.doctree.ThrowsTree;
import com.sun.source.doctree.UnknownBlockTagTree;
import com.sun.source.doctree.UsesTree;
import com.sun.source.doctree.ValueTree;
import com.sun.source.doctree.VersionTree;
import com.sun.source.util.DocTreePath;
import com.sun.source.util.DocTrees;
import com.sun.source.util.SimpleDocTreeVisitor;
import com.sun.source.util.TreePath;
import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;

import static com.sun.source.doctree.DocTree.Kind.*;

/**
 * A utility class.
 */
public class CommentHelper {
    private final BaseConfiguration configuration;
    public final TreePath path;
    public final DocCommentTree dcTree;
    public final Element element;

    public static final String SPACER = " ";

    /**
     * Creates a utility class to encapsulate the contextual information for a doc comment tree.
     *
     * @param configuration the configuration
     * @param element       the element for which this is a doc comment
     * @param path          the path for the element
     * @param dcTree        the doc comment
     */
    public CommentHelper(BaseConfiguration configuration, Element element, TreePath path, DocCommentTree dcTree) {
        this.configuration = configuration;
        this.element = element;
        this.path = path;
        this.dcTree = dcTree;
    }

    public String getTagName(DocTree dtree) {
        switch (dtree.getKind()) {
            case AUTHOR:
            case DEPRECATED:
            case PARAM:
            case PROVIDES:
            case RETURN:
            case SEE:
            case SERIAL_DATA:
            case SERIAL_FIELD:
            case THROWS:
            case UNKNOWN_BLOCK_TAG:
            case USES:
            case VERSION:
                return ((BlockTagTree) dtree).getTagName();
            case UNKNOWN_INLINE_TAG:
                return ((InlineTagTree) dtree).getTagName();
            case ERRONEOUS:
                return "erroneous";
            default:
                return dtree.getKind().tagName;
        }
    }

    public String getParameterName(ParamTree p) {
        return p.getName().getName().toString();
    }

    Element getElement(ReferenceTree rtree) {
        Utils utils = configuration.utils;
        // likely a synthesized tree
        if (path == null) {
            // NOTE: this code path only supports module/package/type signatures
            //       and not member signatures. For more complete support,
            //       set a suitable path and avoid this branch.
            TypeMirror symbol = utils.getSymbol(rtree.getSignature());
            if (symbol == null) {
                return null;
            }
            return configuration.docEnv.getTypeUtils().asElement(symbol);
        }
        DocTreePath docTreePath = getDocTreePath(rtree);
        if (docTreePath == null) {
            return null;
        }
        DocTrees doctrees = configuration.docEnv.getDocTrees();
        // Workaround for JDK-8284193
        // DocTrees.getElement(DocTreePath) returns javac-internal Symbols
        var e = doctrees.getElement(docTreePath);
        return e == null || e.getKind() == ElementKind.CLASS && e.asType().getKind() != TypeKind.DECLARED ? null : e;
    }

    public TypeMirror getType(ReferenceTree rtree) {
        DocTreePath docTreePath = getDocTreePath(rtree);
        if (docTreePath != null) {
            DocTrees docTrees = configuration.docEnv.getDocTrees();
            return docTrees.getType(docTreePath);
        }
        return null;
    }

    public Element getException(ThrowsTree tt) {
        return getElement(tt.getExceptionName());
    }

    public List<? extends DocTree> getDescription(DocTree dtree) {
        return getTags(dtree);
    }

    public TypeElement getReferencedClass(DocTree dtree) {
        Element e = getReferencedElement(dtree);
        return getReferencedClass(e);
    }

    public TypeElement getReferencedClass(Element e) {
        Utils utils = configuration.utils;
        if (e == null) {
            return null;
        } else if (utils.isTypeElement(e)) {
            return (TypeElement) e;
        } else if (!utils.isPackage(e) && !utils.isModule(e)) {
            return utils.getEnclosingTypeElement(e);
        }
        return null;
    }

    public String getReferencedModuleName(DocTree dtree) {
        String s = getReferencedSignature(dtree);
        return getReferencedModuleName(s);
    }

    public String getReferencedModuleName(String signature) {
        if (signature == null || signature.contains("#") || signature.contains("(")) {
            return null;
        }
        int n = signature.indexOf("/");
        return (n == -1) ? signature : signature.substring(0, n);
    }

    public Element getReferencedMember(DocTree dtree) {
        Element e = getReferencedElement(dtree);
        return getReferencedMember(e);
    }

    public Element getReferencedMember(Element e) {
        Utils utils = configuration.utils;
        if (e == null) {
            return null;
        }
        return (utils.isExecutableElement(e) || utils.isVariableElement(e)) ? e : null;
    }

    public String getReferencedMemberName(String signature) {
        if (signature == null) {
            return null;
        }
        int n = signature.indexOf("#");
        return (n == -1) ? null : signature.substring(n + 1);
    }

    public PackageElement getReferencedPackage(DocTree dtree) {
        Element e = getReferencedElement(dtree);
        return getReferencedPackage(e);
    }

    public PackageElement getReferencedPackage(Element e) {
        if (e != null) {
            Utils utils = configuration.utils;
            return utils.containingPackage(e);
        }
        return null;
    }

    public ModuleElement getReferencedModule(DocTree dtree) {
        Element e = getReferencedElement(dtree);
        return getReferencedModule(e);
    }

    public ModuleElement getReferencedModule(Element e) {
        if (e != null && configuration.utils.isModule(e)) {
            return (ModuleElement) e;
        }
        return null;
    }

    public List<? extends DocTree> getFirstSentenceTrees(List<? extends DocTree> body) {
        return configuration.docEnv.getDocTrees().getFirstSentence(body);
    }

    public List<? extends DocTree> getFirstSentenceTrees(DocTree dtree) {
        return getFirstSentenceTrees(getBody(dtree));
    }

    public Element getReferencedElement(DocTree dtree) {
        return new ReferenceDocTreeVisitor<Element>() {
            @Override
            public Element visitReference(ReferenceTree node, Void p) {
                return getElement(node);
            }
        }.visit(dtree, null);
    }

    public TypeMirror getReferencedType(DocTree dtree) {
        return new ReferenceDocTreeVisitor<TypeMirror>() {
            @Override
            public TypeMirror visitReference(ReferenceTree node, Void p) {
                return getType(node);
            }
        }.visit(dtree, null);
    }

    public TypeElement getServiceType(DocTree dtree) {
        Element e = getReferencedElement(dtree);
        if (e != null) {
            Utils utils = configuration.utils;
            return utils.isTypeElement(e) ? (TypeElement) e : null;
        }
        return null;
    }

    /**
     * {@return the normalized signature from a {@code ReferenceTree}}
     */
    public String getReferencedSignature(DocTree dtree) {
        return new ReferenceDocTreeVisitor<String>() {
            @Override
            public String visitReference(ReferenceTree node, Void p) {
                return normalizeSignature(node.getSignature());
            }
        }.visit(dtree, null);
    }

    @SuppressWarnings("fallthrough")
    private static String normalizeSignature(String sig) {
        if (sig == null
                || (!sig.contains(" ") && !sig.contains("\n")
                 && !sig.contains("\r") && !sig.endsWith("/"))) {
            return sig;
        }
        StringBuilder sb = new StringBuilder();
        char lastChar = 0;
        for (int i = 0; i < sig.length(); i++) {
            char ch = sig.charAt(i);
            switch (ch) {
                case '\n':
                case '\r':
                case '\f':
                case '\t':
                case ' ':
                    // Add at most one space char, or none if it isn't needed
                    switch (lastChar) {
                        case 0:
                        case'(':
                        case'<':
                        case ' ':
                        case '.':
                            break;
                        default:
                            sb.append(' ');
                            lastChar = ' ';
                            break;
                    }
                    break;
                case ',':
                case '>':
                case ')':
                case '.':
                    // Remove preceding space character
                    if (lastChar == ' ') {
                        sb.setLength(sb.length() - 1);
                    }
                    // fallthrough
                default:
                    sb.append(ch);
                    lastChar = ch;
            }
        }
        // Delete trailing slash
        if (lastChar == '/') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    private static class ReferenceDocTreeVisitor<R> extends SimpleDocTreeVisitor<R, Void> {
        @Override
        public R visitSee(SeeTree node, Void p) {
            for (DocTree dt : node.getReference()) {
                return visit(dt, null);
            }
            return null;
        }

        @Override
        public R visitLink(LinkTree node, Void p) {
            return visit(node.getReference(), null);
        }

        @Override
        public R visitProvides(ProvidesTree node, Void p) {
            return visit(node.getServiceType(), null);
        }

        @Override
        public R visitValue(ValueTree node, Void p) {
            return visit(node.getReference(), null);
        }

        @Override
        public R visitSerialField(SerialFieldTree node, Void p) {
            return visit(node.getType(), null);
        }

        @Override
        public R visitUses(UsesTree node, Void p) {
            return visit(node.getServiceType(), null);
        }

        @Override
        protected R defaultAction(DocTree node, Void p) {
            return null;
        }
    }

    public List<? extends DocTree> getReference(DocTree dtree) {
        return dtree.getKind() == SEE ? ((SeeTree)dtree).getReference() : null;
    }

    public IdentifierTree getName(DocTree dtree) {
        switch (dtree.getKind()) {
            case PARAM:
                return ((ParamTree)dtree).getName();
            case SERIAL_FIELD:
                return ((SerialFieldTree)dtree).getName();
            default:
                return null;
            }
    }

    public List<? extends DocTree> getTags(DocTree dtree) {
        return new SimpleDocTreeVisitor<List<? extends DocTree>, Void>() {

            private List<DocTree> asList(String content) {
                return List.of(configuration.cmtUtils.makeTextTree(content));
            }

            @Override
            public List<? extends DocTree> visitAuthor(AuthorTree node, Void p) {
                return node.getName();
            }

            @Override
            public List<? extends DocTree> visitComment(CommentTree node, Void p) {
                return asList(node.getBody());
            }

            @Override
            public List<? extends DocTree> visitDeprecated(DeprecatedTree node, Void p) {
                return node.getBody();
            }

            @Override
            public List<? extends DocTree> visitDocComment(DocCommentTree node, Void p) {
                return node.getBody();
            }

            @Override
            public List<? extends DocTree> visitLiteral(LiteralTree node, Void p) {
                return asList(node.getBody().getBody());
            }

            @Override
            public List<? extends DocTree> visitProvides(ProvidesTree node, Void p) {
                return node.getDescription();
            }

            @Override
            public List<? extends DocTree> visitSince(SinceTree node, Void p) {
                return node.getBody();
            }

            @Override
            public List<? extends DocTree> visitText(TextTree node, Void p) {
                return asList(node.getBody());
            }

            @Override
            public List<? extends DocTree> visitVersion(VersionTree node, Void p) {
                return node.getBody();
            }

            @Override
            public List<? extends DocTree> visitParam(ParamTree node, Void p) {
                return node.getDescription();
            }

            @Override
            public List<? extends DocTree> visitReturn(ReturnTree node, Void p) {
                return node.getDescription();
            }

            @Override
            public List<? extends DocTree> visitSee(SeeTree node, Void p) {
                return node.getReference();
            }

            @Override
            public List<? extends DocTree> visitSerial(SerialTree node, Void p) {
                return node.getDescription();
            }

            @Override
            public List<? extends DocTree> visitSerialData(SerialDataTree node, Void p) {
                return node.getDescription();
            }

            @Override
            public List<? extends DocTree> visitSerialField(SerialFieldTree node, Void p) {
                return node.getDescription();
            }

            @Override
            public List<? extends DocTree> visitThrows(ThrowsTree node, Void p) {
                return node.getDescription();
            }

            @Override
            public List<? extends DocTree> visitUnknownBlockTag(UnknownBlockTagTree node, Void p) {
                return node.getContent();
            }

            @Override
            public List<? extends DocTree> visitUses(UsesTree node, Void p) {
                return node.getDescription();
            }

            @Override
            protected List<? extends DocTree> defaultAction(DocTree node, Void p) {
                return List.of();
            }
        }.visit(dtree, null);
    }

    public List<? extends DocTree> getBody(DocTree dtree) {
        return getTags(dtree);
    }

    public ReferenceTree getType(DocTree dtree) {
        if (dtree.getKind() == SERIAL_FIELD) {
            return ((SerialFieldTree) dtree).getType();
        } else {
            return null;
        }
    }

    public DocTreePath getDocTreePath(DocTree dtree) {
        if (dcTree == null && element instanceof ExecutableElement ee) {
            return getInheritedDocTreePath(dtree, ee);
        }
        if (path == null || dcTree == null || dtree == null) {
            return null;
        }
        DocTreePath dtPath = DocTreePath.getPath(path, dcTree, dtree);
        if (dtPath == null && element instanceof ExecutableElement ee) {
            // The overriding element has a doc tree, but it doesn't contain what we're looking for.
            return getInheritedDocTreePath(dtree, ee);
        }
        return dtPath;
    }

    private DocTreePath getInheritedDocTreePath(DocTree dtree, ExecutableElement ee) {
        Utils utils = configuration.utils;
        var docFinder = utils.docFinder();
        Optional<ExecutableElement> inheritedDoc = docFinder.search(ee,
                (m -> utils.getFullBody(m).isEmpty() ? Optional.empty() : Optional.of(m)));
        return inheritedDoc.isEmpty() || inheritedDoc.get().equals(ee)
                ? null
                : utils.getCommentHelper(inheritedDoc.get()).getDocTreePath(dtree);
    }

    /**
     * For debugging purposes only. Do not rely on this for other things.
     * @return a string representation.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("CommentHelper{" + "path=" + path + ", dcTree=" + dcTree);
        sb.append(", element=");
        sb.append(element.getEnclosingElement());
        sb.append("::");
        sb.append(element);
        sb.append('}');
        return sb.toString();
    }
}
