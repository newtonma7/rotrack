package com.rotrack.richtext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class RichTextDocumentValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final RichTextDocumentValidator validator = new RichTextDocumentValidator(mapper);

    @Test
    void canonicalizesAllowedDocumentAndDerivesBlockText() throws Exception {
        var input = mapper.readTree("""
                {"schemaVersion":1,"document":{"type":"doc","content":[
                  {"type":"heading","attrs":{"level":2},"content":[{"type":"text","text":"Read","marks":[{"type":"italic"},{"type":"bold"}]}]},
                  {"type":"paragraph","content":[{"type":"text","text":"the docs"}]}
                ]}}
                """);

        RichTextValue value = validator.validate(input);

        assertThat(value.serialized()).isEqualTo("{\"schemaVersion\":1,\"document\":{\"type\":\"doc\",\"content\":[{\"type\":\"heading\",\"attrs\":{\"level\":2},\"content\":[{\"type\":\"text\",\"marks\":[{\"type\":\"bold\"},{\"type\":\"italic\"}],\"text\":\"Read\"}]},{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"the docs\"}]}]}}");
        assertThat(value.contentText()).isEqualTo("Read\nthe docs");
    }

    @Test
    void canonicalizesOrderedListAttrsAndRejectsInvalidAttrs() throws Exception {
        var omitted = mapper.readTree("""
                {"schemaVersion":1,"document":{"type":"doc","content":[
                  {"type":"orderedList","content":[{"type":"listItem","content":[
                    {"type":"paragraph","content":[{"type":"text","text":"one"}]}
                  ]}]}
                ]}}
                """);
        assertThat(validator.validate(omitted).serialized())
                .isEqualTo("{\"schemaVersion\":1,\"document\":{\"type\":\"doc\",\"content\":[{\"type\":\"orderedList\",\"attrs\":{\"start\":1},\"content\":[{\"type\":\"listItem\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"one\"}]}]}]}]}}");

        var explicit = mapper.readTree("""
                {"schemaVersion":1,"document":{"type":"doc","content":[
                  {"type":"orderedList","attrs":{"start":3},"content":[{"type":"listItem","content":[{"type":"paragraph"}]}]}
                ]}}
                """);
        assertThat(validator.validate(explicit).contentJson()
                .path("document").path("content").get(0).path("attrs").path("start").intValue())
                .isEqualTo(3);

        for (String invalid : new String[]{"{\"start\":0}", "{\"start\":-1}", "{\"start\":1.5}", "{\"start\":\"1\"}", "{\"extra\":1}"}) {
            var input = mapper.readTree("""
                    {"schemaVersion":1,"document":{"type":"doc","content":[
                      {"type":"orderedList","attrs":%s,"content":[{"type":"listItem","content":[{"type":"paragraph"}]}]}
                    ]}}
                    """.formatted(invalid));
            assertThatThrownBy(() -> validator.validate(input))
                    .isInstanceOf(RichTextValidationException.class);
        }
    }

    @Test
    void canonicalizesTaskListsAtRootAndNestedBlockPositions() throws Exception {
        var input = mapper.readTree("""
                {"schemaVersion":1,"document":{"type":"doc","content":[
                  {"type":"taskList","content":[
                    {"type":"taskItem","attrs":{"checked":false},"content":[
                      {"type":"paragraph","content":[{"type":"text","text":"todo"}]},
                      {"type":"blockquote","content":[{"type":"paragraph","content":[{"type":"text","text":"quote"}]}]},
                      {"type":"taskList","content":[
                        {"type":"taskItem","attrs":{"checked":true},"content":[{"type":"paragraph","content":[{"type":"text","text":"nested"}]}]}
                      ]}
                    ]}
                  ]},
                  {"type":"blockquote","content":[
                    {"type":"taskList","content":[
                      {"type":"taskItem","attrs":{"checked":true},"content":[{"type":"paragraph","content":[{"type":"text","text":"inside"}]}]}
                    ]}
                  ]}
                ]}}
                """);

        RichTextValue value = validator.validate(input);

        assertThat(value.contentText()).isEqualTo("todo\nquote\nnested\ninside");
        assertThat(value.serialized()).contains(
                "\"type\":\"taskList\"",
                "\"type\":\"taskItem\"",
                "\"attrs\":{\"checked\":true}");
        assertThat(value.contentJson().at("/document/content/0/content/0/attrs/checked").booleanValue()).isFalse();
        assertThat(value.contentJson().at("/document/content/0/content/0/content/2/content/0/attrs/checked").booleanValue()).isTrue();
    }

    @Test
    void rejectsInvalidTaskListGrammarAndKeepsCheckboxStateOutOfContentText() throws Exception {
        JsonNode[] invalidDocuments = {
                mapper.readTree("""
                        {"type":"taskList","content":[]}
                        """),
                mapper.readTree("""
                        {"type":"taskList","content":[{"type":"listItem","content":[{"type":"paragraph"}]}]}
                        """),
                mapper.readTree("""
                        {"type":"taskList","content":[{"type":"taskItem","attrs":{},"content":[{"type":"paragraph"}]}]}
                        """),
                mapper.readTree("""
                        {"type":"taskList","content":[{"type":"taskItem","attrs":{"checked":"true"},"content":[{"type":"paragraph"}]}]}
                        """),
                mapper.readTree("""
                        {"type":"taskList","content":[{"type":"taskItem","attrs":{"checked":true,"extra":false},"content":[{"type":"paragraph"}]}]}
                        """),
                mapper.readTree("""
                        {"type":"taskList","content":[{"type":"taskItem","attrs":{"checked":true},"content":[{"type":"blockquote"}]}]}
                        """)
        };
        for (JsonNode invalid : invalidDocuments) {
            ObjectNode root = mapper.createObjectNode();
            root.put("schemaVersion", 1);
            ObjectNode document = root.putObject("document");
            document.put("type", "doc");
            document.putArray("content").add(invalid);
            assertThatThrownBy(() -> validator.validate(root))
                    .isInstanceOf(RichTextValidationException.class);
        }

        var checkboxOnly = mapper.readTree("""
                {"schemaVersion":1,"document":{"type":"doc","content":[
                  {"type":"taskList","content":[
                    {"type":"taskItem","attrs":{"checked":true},"content":[{"type":"paragraph"}]}
                  ]}
                ]}}
                """);
        RichTextValue value = validator.validate(checkboxOnly);
        assertThat(value.contentText()).isEmpty();
        assertThat(value.meaningful()).isFalse();
    }

    @Test
    void rejectsUnknownGrammarAndUnsafeLinks() throws Exception {
        var input = mapper.readTree("""
                {"schemaVersion":1,"document":{"type":"doc","content":[
                  {"type":"paragraph","extra":true,"content":[{"type":"text","text":"x"}]}
                ]}}
                """);
        assertThatThrownBy(() -> validator.validate(input)).isInstanceOf(RichTextValidationException.class);

        var link = mapper.readTree("""
                {"schemaVersion":1,"document":{"type":"doc","content":[
                  {"type":"paragraph","content":[{"type":"text","text":"x","marks":[{"type":"link","attrs":{"href":"javascript:alert(1)"},"extra":true}]}]}
                ]}}
                """);
        assertThatThrownBy(() -> validator.validate(link))
                .isInstanceOf(RichTextValidationException.class)
                .hasMessage("contentJson contains an invalid link");

        var linkAttrs = mapper.readTree("""
                {"schemaVersion":1,"document":{"type":"doc","content":[
                  {"type":"paragraph","content":[{"type":"text","text":"x","marks":[{"type":"link","attrs":{"href":"https://example.test","extra":true}}]}]}
                ]}}
                """);
        assertThatThrownBy(() -> validator.validate(linkAttrs)).hasMessage("contentJson contains an invalid link");
    }

    @Test
    void enforcesDepthNodeAndSerializedByteCeilings() throws Exception {
        ObjectNode maxDepth = mapper.createObjectNode();
        maxDepth.put("type", "doc");
        ArrayNode root = maxDepth.putArray("content");
        ObjectNode current = root.addObject();
        current.put("type", "blockquote");
        for (int i = 0; i < 29; i++) {
            current = current.putArray("content").addObject();
            current.put("type", "blockquote");
        }
        current.putArray("content").addObject().put("type", "paragraph");
        assertThat(validator.validate(envelope(maxDepth)).serialized()).isNotBlank();

        ObjectNode deep = mapper.createObjectNode();
        deep.put("type", "doc");
        current = deep.putArray("content").addObject();
        current.put("type", "blockquote");
        for (int i = 0; i < 30; i++) {
            current = current.putArray("content").addObject();
            current.put("type", "blockquote");
        }
        current.putArray("content").addObject().put("type", "paragraph");
        assertThatThrownBy(() -> validator.validate(envelope(deep)))
                .hasMessage("contentJson exceeds the maximum depth");

        ObjectNode many = mapper.createObjectNode();
        many.put("type", "doc");
        ArrayNode paragraphs = many.putArray("content");
        for (int i = 0; i < 9_999; i++) paragraphs.addObject().put("type", "paragraph");
        assertThat(validator.validate(envelope(many)).serialized()).isNotBlank();
        paragraphs.addObject().put("type", "paragraph");
        assertThatThrownBy(() -> validator.validate(envelope(many)))
                .hasMessage("contentJson exceeds the maximum node count");

        ObjectNode emptyText = textDocument("");
        int overhead = mapper.writeValueAsBytes(envelope(emptyText)).length;
        String atLimit = "x".repeat(RichTextDocumentValidator.MAX_BYTES - overhead);
        assertThat(mapper.writeValueAsBytes(envelope(textDocument(atLimit))).length)
                .isEqualTo(RichTextDocumentValidator.MAX_BYTES);
        assertThat(validator.validate(envelope(textDocument(atLimit))).serialized().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .hasSize(RichTextDocumentValidator.MAX_BYTES);
        assertThatThrownBy(() -> validator.validate(envelope(textDocument(atLimit + "x"))))
                .hasMessage("contentJson exceeds the maximum size");
    }

    @Test
    void acceptsOnlyHttpHttpsWithHostsAndValidMailtoLinks() throws Exception {
        for (String href : new String[]{"http://example.test", "https://example.test/path", "mailto:person@example.test"}) {
            assertThat(validator.validate(linkedDocument(href)).contentText()).isEqualTo(href.startsWith("mailto:") ? "person@example.test" : href);
        }
        for (String href : new String[]{
                "http:///missing-host", "https:///missing-host", "ftp://example.test", "javascript:alert(1)",
                "mailto:missing-at.example", "mailto:person@example.test?subject=private"}) {
            assertThatThrownBy(() -> validator.validate(linkedDocument(href)))
                    .hasMessage("contentJson contains an invalid link");
        }
    }

    @Test
    void rejectsDuplicateMarksAndCanonicalizesMarkOrder() throws Exception {
        JsonNode duplicate = mapper.readTree("""
                {"schemaVersion":1,"document":{"type":"doc","content":[
                  {"type":"paragraph","content":[{"type":"text","text":"x","marks":[{"type":"bold"},{"type":"bold"}]}]}
                ]}}
                """);
        assertThatThrownBy(() -> validator.validate(duplicate))
                .hasMessage("contentJson contains an invalid mark");

        JsonNode reordered = mapper.readTree("""
                {"schemaVersion":1,"document":{"type":"doc","content":[
                  {"type":"paragraph","content":[{"type":"text","text":"x","marks":[{"type":"italic"},{"type":"bold"}]}]}
                ]}}
                """);
        assertThat(validator.validate(reordered).serialized())
                .contains("\"marks\":[{\"type\":\"bold\"},{\"type\":\"italic\"}]");
    }

    @Test
    void previewCollapsesUnicodeWhitespace() throws Exception {
        var input = mapper.readTree("""
                {"schemaVersion":1,"document":{"type":"doc","content":[
                  {"type":"paragraph","content":[{"type":"text","text":"a\u00a0\u2003\u202f b"}]}
                ]}}
                """);
        assertThat(preview(input)).isEqualTo("a b");
    }

    private ObjectNode textDocument(String text) {
        ObjectNode document = mapper.createObjectNode();
        document.put("type", "doc");
        ArrayNode paragraphs = document.putArray("content");
        ObjectNode paragraph = paragraphs.addObject();
        paragraph.put("type", "paragraph");
        paragraph.putArray("content").addObject().put("type", "text").put("text", text);
        return document;
    }

    private JsonNode linkedDocument(String href) {
        ObjectNode document = textDocument(href.startsWith("mailto:") ? "person@example.test" : href).deepCopy();
        ObjectNode text = (ObjectNode) document.withArray("content").get(0).withArray("content").get(0);
        text.putArray("marks").addObject().put("type", "link").putObject("attrs").put("href", href);
        return envelope(document);
    }

    private String preview(JsonNode document) {
        String text = validator.validate(document).contentText();
        String collapsed = text.replaceAll("[\\p{javaWhitespace}\\p{Z}]+", " ").strip();
        int end = collapsed.offsetByCodePoints(0, Math.min(160, collapsed.codePointCount(0, collapsed.length())));
        return collapsed.substring(0, end);
    }

    private JsonNode envelope(ObjectNode document) {
        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("schemaVersion", 1);
        envelope.set("document", document);
        return envelope;
    }
}
