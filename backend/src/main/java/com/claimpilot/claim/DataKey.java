package com.claimpilot.claim;

/**
 * The canonical data items a claim form can ask for. Form fields are mapped onto these keys
 * (by the model, once per form version), and values are filled from the user's documents.
 */
public enum DataKey {

    MEMBER_NAME("Plan member name", "full name of the plan member of the plan being claimed on"),
    POLICY_NUMBER("Group policy number", "group policy / contract number of the plan being claimed on"),
    CERTIFICATE_NUMBER("Certificate number", "certificate or member ID of the plan being claimed on"),
    PLAN_SPONSOR("Plan sponsor (employer)", "employer that provides the plan being claimed on"),
    PATIENT_NAME("Patient name", "full name of the patient who received the care"),
    PATIENT_DOB("Patient date of birth", "patient's date of birth"),
    PATIENT_ADDRESS("Patient address", "patient's mailing address"),
    PATIENT_PHONE("Patient phone", "patient's telephone number"),
    PATIENT_RELATIONSHIP("Relationship to plan member", "patient's relationship to the plan member (self, spouse, child)"),
    OTHER_INSURER("Other plan: insurer", "insurance company of the patient's other plan, which paid first"),
    OTHER_POLICY_NUMBER("Other plan: policy number", "group policy number of the other plan"),
    OTHER_CERTIFICATE_NUMBER("Other plan: certificate number", "certificate or member ID on the other plan"),
    PROVIDER_NAME("Provider", "clinic or practitioner who provided the care"),
    SERVICE_DATE("Date of service", "date the care was provided"),
    SERVICE_TYPE("Type of service", "type of care, for example physiotherapy"),
    RECEIPT_NUMBER("Receipt number", "receipt or invoice number"),
    AMOUNT_CHARGED("Amount charged", "total amount charged by the provider"),
    AMOUNT_PAID_BY_OTHER_PLAN("Paid by other plan", "amount already paid by the other plan"),
    AMOUNT_CLAIMED("Amount claimed", "remaining amount claimed from this plan"),

    // Never filled automatically: the member must complete these personally.
    DECLARATION("Declaration", "checkbox where the member certifies the claim is true", true),
    SIGNATURE("Signature", "plan member's signature", true),
    SIGNATURE_DATE("Date signed", "date the member signs the form", true),

    NONE("Not used", "anything else, or a field that should be left blank", true);

    private final String label;
    private final String description;
    private final boolean neverFill;

    DataKey(String label, String description) {
        this(label, description, false);
    }

    DataKey(String label, String description, boolean neverFill) {
        this.label = label;
        this.description = description;
        this.neverFill = neverFill;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    /** Legal declarations and signatures are left for the member, whatever the mapping says. */
    public boolean neverFill() {
        return neverFill;
    }
}
