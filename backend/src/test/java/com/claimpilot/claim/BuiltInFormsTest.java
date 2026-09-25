package com.claimpilot.claim;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

/** Every form shipped with the app has a reviewed mapping that matches the PDF field for field. */
class BuiltInFormsTest {

    static Set<String> keys() {
        return FormCatalog.BUILT_IN_FORMS.keySet();
    }

    @ParameterizedTest
    @MethodSource("keys")
    void theReviewedMappingCoversEveryFieldAndOnlyThoseFields(String key) {
        FormTemplate form = FormTemplate.builtIn(FormCatalog.BUILT_IN_FORMS.get(key).path());
        Map<String, String> reviewed = reviewed(form);

        assertThat(reviewed.keySet()).as(key)
                .containsExactlyInAnyOrderElementsOf(form.fields().stream().map(FormTemplate.Field::name).toList());
        Set<String> dataKeys = Arrays.stream(DataKey.values()).map(Enum::name).collect(Collectors.toSet());
        assertThat(reviewed.values()).as(key).allMatch(dataKeys::contains);
    }

    @ParameterizedTest
    @MethodSource("keys")
    void theReviewedMappingPassesTheSafetyChecksUnchanged(String key) {
        FormTemplate form = FormTemplate.builtIn(FormCatalog.BUILT_IN_FORMS.get(key).path());
        Map<String, String> reviewed = reviewed(form);

        Map<String, DataKey> checked = FieldMappingService.parse(form.presetMapping().orElseThrow(), form);

        // Nothing had to be overridden: signatures, declarations and checkboxes are never filled.
        checked.forEach((field, dataKey) -> assertThat(dataKey.name()).as(key + " " + field)
                .isEqualTo(reviewed.get(field)));
        assertThat(checked.values()).as(key).contains(DataKey.SIGNATURE, DataKey.PATIENT_NAME, DataKey.MEMBER_NAME);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> reviewed(FormTemplate form) {
        return JsonMapper.builder().build().readValue(form.presetMapping().orElseThrow(), LinkedHashMap.class);
    }
}
