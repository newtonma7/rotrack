package com.rotrack.richtext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The single rich-text trust boundary. It turns an untrusted tree into the compact value used by
 * persistence, replay fingerprints, previews, and API responses; PostgreSQL only checks its shape.
 */
public final class RichTextDocumentValidator {

    public static final int MAX_BYTES = 262_144;
    public static final int MAX_DEPTH = 32;
    public static final int MAX_NODES = 10_000;

    private final ObjectMapper mapper;

    public RichTextDocumentValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public RichTextValue validate(JsonNode input) {
        if (input == null || !input.isObject()) {
            throw invalid("contentJson must be an object");
        }
        ObjectNode source = (ObjectNode) input;
        requireKeys(source, Set.of("schemaVersion", "document"), "contentJson contains unknown fields");
        JsonNode schemaVersion = source.get("schemaVersion");
        if (schemaVersion == null || !schemaVersion.isInt() || schemaVersion.intValue() != 1) {
            throw invalid("contentJson.schemaVersion must be 1");
        }
        JsonNode document = source.get("document");
        if (document == null || !document.isObject()) {
            throw invalid("contentJson.document must be an object");
        }

        State state = new State();
        ObjectNode canonicalDocument = parseNode((ObjectNode) document, NodeKind.DOC, 1, state);
        ObjectNode canonical = mapper.createObjectNode();
        canonical.put("schemaVersion", 1);
        canonical.set("document", canonicalDocument);
        String serialized;
        try {
            serialized = mapper.writeValueAsString(canonical);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize rich text", exception);
        }
        if (serialized.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw invalid("contentJson exceeds the maximum size");
        }
        String contentText = String.join("\n", state.blocks);
        return new RichTextValue(canonical, serialized, contentText);
    }

    private ObjectNode parseNode(ObjectNode source, NodeKind kind, int depth, State state) {
        state.visit(depth);
        String type = text(source, "type", "contentJson contains an invalid node");
        if (!kind.type.equals(type)) {
            throw invalid("contentJson contains an invalid node");
        }
        return switch (kind) {
            case DOC -> parseDoc(source, depth, state);
            case PARAGRAPH -> parseParagraph(source, depth, state, false);
            case HEADING -> parseParagraph(source, depth, state, true);
            case BULLET_LIST -> parseList(source, depth, state, false);
            case ORDERED_LIST -> parseList(source, depth, state, true);
            case LIST_ITEM -> parseListItem(source, depth, state);
            case BLOCKQUOTE -> parseBlockquote(source, depth, state);
            case TEXT -> parseText(source, state);
        };
    }

    private ObjectNode parseDoc(ObjectNode source, int depth, State state) {
        requireKeys(source, Set.of("type", "content"), "contentJson contains an invalid node");
        ObjectNode result = mapper.createObjectNode();
        result.put("type", "doc");
        ArrayNode content = optionalChildren(source, "content");
        ArrayNode canonical = mapper.createArrayNode();
        for (JsonNode child : content) {
            if (!child.isObject()) {
                throw invalid("contentJson contains an invalid node");
            }
            String type = text((ObjectNode) child, "type", "contentJson contains an invalid node");
            NodeKind childKind = switch (type) {
                case "paragraph" -> NodeKind.PARAGRAPH;
                case "heading" -> NodeKind.HEADING;
                case "bulletList" -> NodeKind.BULLET_LIST;
                case "orderedList" -> NodeKind.ORDERED_LIST;
                case "blockquote" -> NodeKind.BLOCKQUOTE;
                default -> throw invalid("contentJson contains an invalid node");
            };
            canonical.add(parseNode((ObjectNode) child, childKind, depth + 1, state));
        }
        result.set("content", canonical);
        return result;
    }

    private ObjectNode parseParagraph(ObjectNode source, int depth, State state, boolean heading) {
        requireKeys(source, heading ? Set.of("type", "attrs", "content") : Set.of("type", "content"),
                "contentJson contains an invalid node");
        ObjectNode result = mapper.createObjectNode();
        result.put("type", heading ? "heading" : "paragraph");
        if (heading) {
            ObjectNode attrs = object(source, "attrs");
            requireKeys(attrs, Set.of("level"), "contentJson contains an invalid heading");
            JsonNode level = attrs.get("level");
            if (level == null || !level.isInt() || (level.intValue() != 2 && level.intValue() != 3)) {
                throw invalid("contentJson heading level must be 2 or 3");
            }
            ObjectNode canonicalAttrs = mapper.createObjectNode();
            canonicalAttrs.put("level", level.intValue());
            result.set("attrs", canonicalAttrs);
        }
        ArrayNode content = optionalChildren(source, "content");
        ArrayNode canonical = mapper.createArrayNode();
        StringBuilder block = new StringBuilder();
        for (JsonNode child : content) {
            if (!child.isObject() || !"text".equals(child.path("type").asText())) {
                throw invalid("contentJson contains an invalid inline child");
            }
            ObjectNode text = parseNode((ObjectNode) child, NodeKind.TEXT, depth + 1, state);
            block.append(text.path("text").asText());
            canonical.add(text);
        }
        state.blocks.add(block.toString());
        if (!canonical.isEmpty()) {
            result.set("content", canonical);
        }
        return result;
    }

    private ObjectNode parseList(ObjectNode source, int depth, State state, boolean ordered) {
        requireKeys(source, ordered ? Set.of("type", "attrs", "content") : Set.of("type", "content"),
                "contentJson contains an invalid list");
        ArrayNode content = requiredChildren(source, "content");
        if (content.isEmpty()) {
            throw invalid("contentJson lists cannot be empty");
        }
        ObjectNode result = mapper.createObjectNode();
        result.put("type", ordered ? "orderedList" : "bulletList");
        if (ordered) {
            int start = 1;
            if (source.has("attrs")) {
                ObjectNode attrs = object(source, "attrs");
                requireKeys(attrs, Set.of("start"), "contentJson contains invalid list attributes");
                JsonNode startNode = attrs.get("start");
                if (startNode == null || !startNode.isInt() || startNode.intValue() <= 0) {
                    throw invalid("contentJson ordered-list start must be positive");
                }
                start = startNode.intValue();
            }
            ObjectNode attrs = mapper.createObjectNode();
            attrs.put("start", start);
            result.set("attrs", attrs);
        }
        ArrayNode canonical = mapper.createArrayNode();
        for (JsonNode child : content) {
            if (!child.isObject() || !"listItem".equals(child.path("type").asText())) {
                throw invalid("contentJson lists may contain only list items");
            }
            canonical.add(parseNode((ObjectNode) child, NodeKind.LIST_ITEM, depth + 1, state));
        }
        result.set("content", canonical);
        return result;
    }

    private ObjectNode parseListItem(ObjectNode source, int depth, State state) {
        requireKeys(source, Set.of("type", "content"), "contentJson contains an invalid list item");
        ArrayNode content = requiredChildren(source, "content");
        if (content.isEmpty() || !content.get(0).isObject()
                || !"paragraph".equals(content.get(0).path("type").asText())) {
            throw invalid("contentJson list items must start with a paragraph");
        }
        ObjectNode result = mapper.createObjectNode();
        result.put("type", "listItem");
        ArrayNode canonical = mapper.createArrayNode();
        for (int i = 0; i < content.size(); i++) {
            JsonNode child = content.get(i);
            if (!child.isObject()) {
                throw invalid("contentJson contains an invalid list item child");
            }
            String type = child.path("type").asText();
            NodeKind childKind = switch (type) {
                case "paragraph" -> NodeKind.PARAGRAPH;
                case "bulletList" -> NodeKind.BULLET_LIST;
                case "orderedList" -> NodeKind.ORDERED_LIST;
                case "blockquote" -> NodeKind.BLOCKQUOTE;
                default -> throw invalid("contentJson contains an invalid list item child");
            };
            canonical.add(parseNode((ObjectNode) child, childKind, depth + 1, state));
        }
        result.set("content", canonical);
        return result;
    }

    private ObjectNode parseBlockquote(ObjectNode source, int depth, State state) {
        requireKeys(source, Set.of("type", "content"), "contentJson contains an invalid blockquote");
        ArrayNode content = requiredChildren(source, "content");
        if (content.isEmpty()) {
            throw invalid("contentJson blockquotes cannot be empty");
        }
        ObjectNode result = mapper.createObjectNode();
        result.put("type", "blockquote");
        ArrayNode canonical = mapper.createArrayNode();
        for (JsonNode child : content) {
            if (!child.isObject()) {
                throw invalid("contentJson contains an invalid blockquote child");
            }
            String type = child.path("type").asText();
            NodeKind childKind = switch (type) {
                case "paragraph" -> NodeKind.PARAGRAPH;
                case "bulletList" -> NodeKind.BULLET_LIST;
                case "orderedList" -> NodeKind.ORDERED_LIST;
                case "blockquote" -> NodeKind.BLOCKQUOTE;
                default -> throw invalid("contentJson contains an invalid blockquote child");
            };
            canonical.add(parseNode((ObjectNode) child, childKind, depth + 1, state));
        }
        result.set("content", canonical);
        return result;
    }

    private ObjectNode parseText(ObjectNode source, State state) {
        requireKeys(source, Set.of("type", "marks", "text"), "contentJson contains an invalid text node");
        JsonNode textNode = source.get("text");
        if (textNode == null || !textNode.isTextual() || textNode.textValue().isEmpty()) {
            throw invalid("contentJson text nodes cannot be empty");
        }
        ObjectNode result = mapper.createObjectNode();
        result.put("type", "text");
        ArrayNode marks = optionalChildren(source, "marks");
        List<ObjectNode> canonicalMarks = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode mark : marks) {
            if (!mark.isObject()) {
                throw invalid("contentJson contains an invalid mark");
            }
            ObjectNode markObject = (ObjectNode) mark;
            String type = text(markObject, "type", "contentJson contains an invalid mark");
            if (!seen.add(type) || !(type.equals("bold") || type.equals("italic") || type.equals("link"))) {
                throw invalid("contentJson contains an invalid mark");
            }
            ObjectNode canonical = mapper.createObjectNode();
            canonical.put("type", type);
            if (type.equals("link")) {
                requireKeys(markObject, Set.of("type", "attrs"), "contentJson contains an invalid link");
                ObjectNode attrs = object(markObject, "attrs");
                requireKeys(attrs, Set.of("href"), "contentJson contains an invalid link");
                String href = text(attrs, "href", "contentJson contains an invalid link");
                if (!safeLink(href)) {
                    throw invalid("contentJson contains an invalid link");
                }
                ObjectNode canonicalAttrs = mapper.createObjectNode();
                canonicalAttrs.put("href", href);
                canonical.set("attrs", canonicalAttrs);
                canonicalMarks.add(canonical);
            } else {
                requireKeys(markObject, Set.of("type"), "contentJson contains an invalid mark");
                canonicalMarks.add(canonical);
            }
        }
        canonicalMarks.sort(Comparator.comparingInt(mark -> switch (mark.path("type").asText()) {
            case "bold" -> 0;
            case "italic" -> 1;
            default -> 2;
        }));
        if (!canonicalMarks.isEmpty()) {
            ArrayNode canonicalMarkArray = mapper.createArrayNode();
            canonicalMarks.forEach(canonicalMarkArray::add);
            result.set("marks", canonicalMarkArray);
        }
        result.put("text", textNode.textValue());
        return result;
    }

    private ArrayNode optionalChildren(ObjectNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null) {
            return mapper.createArrayNode();
        }
        if (!value.isArray()) {
            throw invalid("contentJson " + field + " must be an array");
        }
        return (ArrayNode) value;
    }

    private ArrayNode requiredChildren(ObjectNode object, String field) {
        if (!object.has(field)) {
            throw invalid("contentJson " + field + " is required");
        }
        return optionalChildren(object, field);
    }

    private ObjectNode object(ObjectNode source, String field) {
        JsonNode value = source.get(field);
        if (value == null || !value.isObject()) {
            throw invalid("contentJson " + field + " must be an object");
        }
        return (ObjectNode) value;
    }

    private String text(ObjectNode source, String field, String message) {
        JsonNode value = source.get(field);
        if (value == null || !value.isTextual()) {
            throw invalid(message);
        }
        return value.textValue();
    }

    private void requireKeys(ObjectNode source, Set<String> allowed, String message) {
        source.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) {
                throw invalid(message);
            }
        });
    }

    private boolean safeLink(String href) {
        if (href.startsWith("mailto:")) {
            String address = href.substring("mailto:".length());
            return address.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
                    && !address.contains("?") && !address.contains("#");
        }
        try {
            URI uri = URI.create(href);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }


    private RichTextValidationException invalid(String message) {
        return new RichTextValidationException(message);
    }

    private enum NodeKind {
        DOC("doc"), PARAGRAPH("paragraph"), HEADING("heading"), BULLET_LIST("bulletList"),
        ORDERED_LIST("orderedList"), LIST_ITEM("listItem"), BLOCKQUOTE("blockquote"), TEXT("text");

        private final String type;

        NodeKind(String type) {
            this.type = type;
        }
    }

    private static final class State {
        private int count;
        private final List<String> blocks = new ArrayList<>();

        void visit(int depth) {
            if (depth > MAX_DEPTH) {
                throw new RichTextValidationException("contentJson exceeds the maximum depth");
            }
            if (++count > MAX_NODES) {
                throw new RichTextValidationException("contentJson exceeds the maximum node count");
            }
        }
    }
}
