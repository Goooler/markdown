package org.intellij.markdown.html

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.ast.impl.ListItemCompositeNode

internal class ATXGeneratingProvider(tagName: String) : HtmlGenerator.SimpleInlineTagProvider(tagName) {
    override fun childrenToRender(node: ASTNode): List<ASTNode> {
        val L = node.children.size()

        var from = 1
        if (L >= 2 && node.children.get(1).type == MarkdownTokenTypes.WHITE_SPACE) {
            from++
        }

        var to = L
        if (L > 1 && node.children.get(L - 1).type == MarkdownTokenTypes.ATX_HEADER) {
            to--
            if (L > 3 && node.children.get(L - 2).type == MarkdownTokenTypes.WHITE_SPACE) {
                to--
            }
        }

        return node.children.subList(from, to)
    }
}

internal class ListItemGeneratingProvider : HtmlGenerator.SimpleTagProvider("li") {
    override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
        assert(node is ListItemCompositeNode)

        visitor.consumeHtml(openTag(text, node))
        if (node.children.size() == 2 // Bullet + content
                && node.children.last().type == MarkdownElementTypes.PARAGRAPH
                && !(node as ListItemCompositeNode).parent!!.loose) {
            SilentParagraphGeneratingProvider.processNode(visitor, text, node.children.last())
        } else {
            node.acceptChildren(visitor)
        }
        visitor.consumeHtml(closeTag(text, node))
    }

    object SilentParagraphGeneratingProvider : HtmlGenerator.InlineHolderGeneratingProvider() {
        override fun openTag(text: String, node: ASTNode): String {
            return ""
        }

        override fun closeTag(text: String, node: ASTNode): String {
            return ""
        }
    }
}

internal class CodeFenceGeneratingProvider : HtmlGenerator.GeneratingProvider {
    override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
        val indentBefore = node.getTextInNode(text).commonPrefixWith(" ".repeat(10)).length()

        visitor.consumeHtml("<pre><code")
        var state = 0

        var childrenToConsider = node.children
        if (childrenToConsider.last().type == MarkdownTokenTypes.CODE_FENCE_END) {
            childrenToConsider = childrenToConsider.subList(0, childrenToConsider.size() - 1)
        }

        var lastChildWasEol = false;

        for (child in childrenToConsider) {
            if (state == 1 && child.type in listOf(MarkdownTokenTypes.CODE_FENCE_CONTENT,
                    MarkdownTokenTypes.EOL)) {
                visitor.consumeHtml(HtmlGenerator.trimIndents(HtmlGenerator.leafText(text, child), indentBefore))
                lastChildWasEol = child.type == MarkdownTokenTypes.EOL
            }
            if (state == 0 && child.type == MarkdownTokenTypes.FENCE_LANG) {
                visitor.consumeHtml(" class=\"language-${
                HtmlGenerator.leafText(text, child).toString().trim().split(' ')[0]
                }\"");
            }
            if (state == 0 && child.type == MarkdownTokenTypes.EOL) {
                state = 1
                visitor.consumeHtml(">")
            }
        }
        if (state == 0) {
            visitor.consumeHtml(">")
        }
        if (!lastChildWasEol) {
            visitor.consumeHtml("\n")
        }
        visitor.consumeHtml("</code></pre>")
    }
}