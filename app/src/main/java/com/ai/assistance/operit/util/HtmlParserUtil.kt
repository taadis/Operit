package com.ai.assistance.operit.util

import com.ai.assistance.operit.core.tools.ComputerPageInfoNode
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object HtmlParserUtil {

    fun getExtractionScript(): String {
        return """
            (function() {
                const IGNORE_TAGS = new Set(['SCRIPT', 'STYLE', 'META', 'LINK', 'HEAD', 'NOSCRIPT']);
                const INTERACTIVE_TAGS = new Set(['A', 'BUTTON', 'INPUT', 'TEXTAREA', 'SELECT', 'OPTION']);
                const IMPORTANT_ATTRIBUTES = new Set([
                    'id', 'class', 'href', 'src', 'alt', 'title', 'type', 'value',
                    'placeholder', 'name', 'role', 'aria-label', 'onclick'
                ]);
                const MAX_DEPTH = 20;
                
                let interactionIdCounter = 1;
                const interactionMap = {};

                function getCssSelector(el) {
                    if (!(el instanceof Element)) return '';
                    let path = [];
                    while (el.nodeType === Node.ELEMENT_NODE) {
                        let selector = el.nodeName.toLowerCase();
                        if (el.id) {
                            selector += '#' + el.id.trim();
                            path.unshift(selector);
                            break;
                        } else {
                            let sib = el, nth = 1;
                            while (sib = sib.previousElementSibling) {
                                if (sib.nodeName.toLowerCase() === selector) nth++;
                            }
                            if (nth !== 1) selector += `:nth-of-type(${'$'}{nth})`;
                        }
                        path.unshift(selector);
                        el = el.parentNode;
                    }
                    return path.join(' > ');
                }
                
                function isVisible(elem) {
                     if (!(elem instanceof Element)) return false;
                     const style = window.getComputedStyle(elem);
                     if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return false;
                     const rect = elem.getBoundingClientRect();
                     return rect.width > 0 && rect.height > 0;
                }

                function isInteractive(elem) {
                    if (!(elem instanceof Element)) return false;
                    const tagName = elem.tagName.toUpperCase();
                    const style = window.getComputedStyle(elem);
                    const inputType = elem.getAttribute('type')?.toLowerCase();

                    return INTERACTIVE_TAGS.has(tagName) ||
                           elem.hasAttribute('onclick') ||
                           style.cursor === 'pointer' ||
                           (tagName === 'INPUT' && ['button', 'submit', 'reset', 'image'].includes(inputType));
                }

                function getNodeDescription(element) {
                    const attrs = ['aria-label', 'alt', 'title', 'placeholder', 'name'];
                    for (const attr of attrs) {
                        if (element.hasAttribute(attr) && element.getAttribute(attr)) return element.getAttribute(attr);
                    }
                    
                    // For input fields, look for an associated label
                    if (element.tagName.toUpperCase() === 'INPUT' && element.id) {
                        const label = document.querySelector(`label[for="${'$'}{element.id}"]`);
                        if (label && label.textContent) return label.textContent.trim();
                    }

                    let ownText = '';
                    if (element.childNodes) {
                        for (const child of element.childNodes) {
                            if (child.nodeType === Node.TEXT_NODE) {
                                const text = child.textContent.trim();
                                if (text) ownText += text + ' ';
                            }
                        }
                    }
                    ownText = ownText.trim();
                    
                    if (!ownText && isInteractive(element)) {
                        ownText = (element.textContent || "").replace(/\s+/g, ' ').trim();
                    }

                    return ownText.length > 150 ? ownText.substring(0, 147) + '...' : ownText;
                }

                function simplifyNode(element, depth) {
                    if (depth > MAX_DEPTH || !element.tagName || IGNORE_TAGS.has(element.tagName.toUpperCase()) || !isVisible(element)) {
                        return null;
                    }

                    let hasInteractiveChild = false;
                    const rawChildren = [];
                    if (element.childNodes) {
                        element.childNodes.forEach(child => {
                            if (child.nodeType === Node.ELEMENT_NODE) {
                                const simplifiedChildResult = simplifyNode(child, depth + 1);
                                if (simplifiedChildResult) {
                                    rawChildren.push(simplifiedChildResult.node);
                                    if (simplifiedChildResult.hasInteractiveChild) {
                                        hasInteractiveChild = true;
                                    }
                                }
                            }
                        });
                    }
                    
                    const isItselfInteractive = isInteractive(element);
                    const ownDescription = getNodeDescription(element);

                    if (!isItselfInteractive && !hasInteractiveChild && !ownDescription) {
                        return null;
                    }
                    
                    const finalChildren = [];
                    let lastTextNode = null;
                    rawChildren.forEach(child => {
                         const isSimpleText = (child.type === 'container' || child.type === 'text') && !child.interactionId && child.children.length === 0;
                         if (isSimpleText && child.description) {
                            if (lastTextNode) {
                                lastTextNode.description = (lastTextNode.description + ' ' + child.description).trim();
                            } else {
                                lastTextNode = { type: 'text', description: child.description, children: [], interactionId: null };
                                finalChildren.push(lastTextNode);
                            }
                         } else {
                            lastTextNode = null;
                            finalChildren.push(child);
                         }
                    });
                    
                    const tagName = element.tagName.toUpperCase();
                    let type = "container";
                    if (isItselfInteractive) {
                         if (tagName === 'A') type = 'link';
                         else if (tagName === 'BUTTON') type = 'button';
                         else if (tagName === 'INPUT') {
                            const inputType = element.getAttribute('type')?.toLowerCase();
                            switch (inputType) {
                                case 'button':
                                case 'submit':
                                case 'reset':
                                case 'image':
                                    type = 'input_button';
                                    break;
                                default:
                                    type = 'input_text';
                                    break;
                            }
                         }
                         else if (tagName === 'TEXTAREA') type = 'input_text';
                         else type = 'interactive';
                    }

                    let interactionId = null;
                    if (isItselfInteractive) {
                        interactionId = interactionIdCounter++;
                        interactionMap[interactionId] = getCssSelector(element);
                    }

                    const finalNode = {
                        interactionId: interactionId,
                        type: type,
                        description: ownDescription || (finalChildren.length === 1 ? finalChildren[0].description : ''),
                        children: finalChildren.length > 1 ? finalChildren : (finalChildren.length === 1 && finalChildren[0].children.length > 0 ? finalChildren[0].children : [])
                    };

                    if (!finalNode.description && finalNode.children.length === 0 && !finalNode.interactionId) return null;

                    return { node: finalNode, hasInteractiveChild: isItselfInteractive || hasInteractiveChild };
                }
                
                const result = simplifyNode(document.body, 0);
                
                return JSON.stringify({
                    tree: result ? result.node : null,
                    interactionMap: interactionMap
                });
            })();
        """.trimIndent()
    }

    fun parseAndSimplify(
        jsonString: String,
        updateInteractionMap: (Map<Int, String>) -> Unit
    ): ComputerPageInfoNode? {
        try {
            val result = Json.decodeFromString<PageExtractionResult>(jsonString)
            val intKeyMap = result.interactionMap.mapKeys { it.key.toInt() }
            updateInteractionMap(intKeyMap)
            return result.tree
        } catch (e: Exception) {
            android.util.Log.e("HtmlParserUtil", "Failed to parse page extraction result. JSON length: ${jsonString.length}", e)
            return null
        }
    }

    @Serializable
    private data class PageExtractionResult(
        val tree: ComputerPageInfoNode?,
        val interactionMap: Map<String, String>
    )
} 