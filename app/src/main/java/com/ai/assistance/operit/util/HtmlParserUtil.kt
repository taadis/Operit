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
                const MAX_ELEMENTS = 300; // Limit total elements to prevent performance issues on huge pages.
                
                let interactionIdCounter = 1;
                let processedElements = 0;
                const interactionMap = {};

                function findTopmostModal() {
                    let maxZ = -1;
                    let topmostModal = null;
                    const elements = document.querySelectorAll('div, section, aside, form');
                    const modalKeywords = ['modal', 'dialog', 'popup', 'overlay'];

                    for (const el of elements) {
                        try {
                            const style = window.getComputedStyle(el);
                            const zIndex = parseInt(style.zIndex, 10) || 0;
                            const position = style.position;
                            const classList = Array.from(el.classList);

                            // --- Enhanced Modal Detection Logic ---
                            let score = 0;
                            if (position === 'fixed' || position === 'absolute') score += 2;
                            if (zIndex > 100) score += 2;
                            if (el.getAttribute('role') === 'dialog') score += 3;
                            if (el.getAttribute('aria-modal') === 'true') score += 3;
                            if (modalKeywords.some(keyword => classList.some(cls => cls.includes(keyword)))) score += 2;

                            // A high score strongly indicates a modal. We also check visibility and compare z-index.
                            if (score >= 5 && isVisible(el) && zIndex >= maxZ) {
                                maxZ = zIndex;
                                topmostModal = el;
                            }
                        } catch (e) { /* Ignore elements that might cause errors */ }
                    }
                    return topmostModal;
                }

                function getCssSelector(el) {
                    if (!(el instanceof Element)) return '';
                    let path = [];
                    while (el.nodeType === Node.ELEMENT_NODE) {
                        let selector = el.nodeName.toLowerCase();
                        if (el.id) {
                            selector += '#' + el.id.trim().replace(/\s+/g, ' ');
                            path.unshift(selector);
                            break;
                        } else {
                            // Use all valid classes. Modern frameworks generate complex but often stable (within a session) class names.
                            // Filtering them with heuristics is more likely to break things than to help.
                            let classes = Array.from(el.classList || []).filter(c => /^[a-zA-Z0-9-_]+$/.test(c));
                            if (classes.length > 0) {
                                selector += '.' + classes.join('.');
                            } else {
                                // Fallback to nth-of-type if no classes are found at all.
                            let sib = el, nth = 1;
                            while (sib = sib.previousElementSibling) {
                                if (sib.nodeName.toLowerCase() === selector) nth++;
                            }
                            if (nth !== 1) selector += `:nth-of-type(${'$'}{nth})`;
                            }
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

                    // Any INPUT element is considered interactive.
                    // The specific type ('text', 'button', 'search', etc.) is determined later.
                    return INTERACTIVE_TAGS.has(tagName) ||
                           elem.hasAttribute('onclick') ||
                           style.cursor === 'pointer';
                }

                function getNodeDescription(element) {
                    const attrs = ['aria-label', 'alt', 'title', 'placeholder', 'name'];
                    for (const attr of attrs) {
                        if (element.hasAttribute(attr) && element.getAttribute(attr)) return element.getAttribute(attr);
                    }
                    
                    // --- New: Heuristic for icon buttons ---
                    const classList = Array.from(element.classList || []);
                    const iconKeywords = ['close', 'search', 'next', 'previous', 'back', 'menu', 'delete', 'remove', 'add', 'plus'];
                    for (const keyword of iconKeywords) {
                        if (classList.some(cls => cls.toLowerCase().includes(keyword))) {
                            return keyword; // Return the keyword as description
                        }
                    }

                    // --- New: Heuristic for SVG <use> tags ---
                    const useTag = element.querySelector('use');
                    if (useTag) {
                        const href = useTag.getAttribute('href') || useTag.getAttribute('xlink:href');
                        if (href && href.startsWith('#')) {
                            return href.substring(1); // Return the ID as description
                        }
                    }
                    // --- End New ---

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
                    if (depth > MAX_DEPTH || processedElements > MAX_ELEMENTS || !element.tagName || IGNORE_TAGS.has(element.tagName.toUpperCase()) || !isVisible(element)) {
                        return null;
                    }
                    processedElements++;

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
                
                const rootElement = findTopmostModal() || document.body;
                const result = simplifyNode(rootElement, 0);
                
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