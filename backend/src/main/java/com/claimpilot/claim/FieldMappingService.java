package com.claimpilot.claim;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import com.claimpilot.extraction.JsonReply;

/**
 * Works out which data item each form field holds. PDF field names are often meaningless
 * ("txtField_07"), so the model reads each field's label once and maps it to a {@link DataKey}.
 * The mapping is stored per form version and reused, so later claims on the same form need no
 * model call at all. Forms shipped with the app carry a reviewed mapping and never need the model.
 */
@Service
public class FieldMappingService {

    /**
     * Fields whose label mentions these are forced to stay blank, whatever the model says. "certify"
     * rather than "certif", so that "Certificate number" can still be filled.
     */
    static final Pattern PERSONAL_ATTESTATION = Pattern.compile(
            "(?i)sign|declar|certify|attest|authori[sz]|consent|j'atteste|je certifie");

    private static final Logger log = LoggerFactory.getLogger(FieldMappingService.class);

    private final ChatClient chatClient;
    private final FormFieldMappingRepository repository;
    /** A form version's mapping never changes, so it is kept in memory after the first read. */
    private final Map<String, Map<String, DataKey>> byVersion = new ConcurrentHashMap<>();

    public FieldMappingService(ChatClient.Builder builder, FormFieldMappingRepository repository) {
        this.chatClient = builder.build();
        this.repository = repository;
    }

    /** PDF field name to data key, in form order. Calls the model only the first time a form is seen. */
    @Transactional
    public Map<String, DataKey> mappingFor(FormTemplate template) {
        Map<String, DataKey> known = byVersion.get(template.sha256());
        if (known != null) {
            return known;
        }
        Map<String, DataKey> mapping = load(template);
        byVersion.put(template.sha256(), mapping);
        return mapping;
    }

    private Map<String, DataKey> load(FormTemplate template) {
        if (template.presetMapping().isPresent()) {
            // A built-in form ships with a reviewed mapping; it goes through the same checks as the model's.
            return parse(template.presetMapping().get(), template);
        }
        List<FormFieldMapping> stored = repository.findByTemplateSha256(template.sha256());
        if (!stored.isEmpty()) {
            Map<String, DataKey> cached = new LinkedHashMap<>();
            stored.forEach(m -> cached.put(m.getPdfFieldName(), m.getDataKey()));
            return inFormOrder(template, cached);
        }
        String reply = chatClient.prompt()
                .system("You map the fields of an insurance claim form to data items. Reply with one JSON object and nothing else.")
                .user(prompt(template))
                .call()
                .content();
        Map<String, DataKey> mapping = parse(reply, template);
        mapping.forEach((field, key) -> repository.save(new FormFieldMapping(template.sha256(), field, key,
                labelOf(template, field))));
        log.info("Mapped {} fields of {} with the model and stored the mapping", mapping.size(), template.name());
        return mapping;
    }

    static String prompt(FormTemplate template) {
        StringBuilder sb = new StringBuilder("Data items:\n");
        for (DataKey key : DataKey.values()) {
            sb.append("- ").append(key.name()).append(": ").append(key.description()).append('\n');
        }
        sb.append("\nForm fields (name: label):\n");
        for (FormTemplate.Field field : template.fields()) {
            sb.append("- ").append(field.name()).append(": ").append(field.label())
                    .append(field.checkbox() ? " (checkbox)" : "").append('\n');
        }
        sb.append("""

                For every form field, choose the one data item it holds, using the label. Use NONE when no item fits.
                Return {"<field name>": "<DATA_ITEM>", ...} with every field name exactly as given.
                """);
        return sb.toString();
    }

    /**
     * Validates the model's mapping: unknown fields are ignored, unknown or missing items become
     * NONE, and signature or declaration fields are forced to stay blank.
     */
    static Map<String, DataKey> parse(String reply, FormTemplate template) {
        JsonNode root = JsonReply.parse(reply);
        Map<String, DataKey> mapping = new LinkedHashMap<>();
        for (FormTemplate.Field field : template.fields()) {
            DataKey key = toKey(JsonReply.text(root, field.name()));
            if (field.checkbox() && !key.neverFill()) {
                key = DataKey.NONE;  // every data item is text; a checkbox is never ticked for the member
            }
            if (PERSONAL_ATTESTATION.matcher(field.label()).find() && !key.neverFill()) {
                key = DataKey.NONE;
            }
            mapping.put(field.name(), key);
        }
        return mapping;
    }

    private static DataKey toKey(String value) {
        if (value == null) {
            return DataKey.NONE;
        }
        try {
            return DataKey.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return DataKey.NONE;
        }
    }

    private static Map<String, DataKey> inFormOrder(FormTemplate template, Map<String, DataKey> mapping) {
        Map<String, DataKey> ordered = new LinkedHashMap<>();
        for (FormTemplate.Field field : template.fields()) {
            ordered.put(field.name(), mapping.getOrDefault(field.name(), DataKey.NONE));
        }
        return ordered;
    }

    private static String labelOf(FormTemplate template, String fieldName) {
        return template.fields().stream().filter(f -> f.name().equals(fieldName)).map(FormTemplate.Field::label)
                .findFirst().orElse(null);
    }
}
