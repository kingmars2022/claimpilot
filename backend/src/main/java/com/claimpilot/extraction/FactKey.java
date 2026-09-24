package com.claimpilot.extraction;

import java.util.Arrays;
import java.util.List;

import com.claimpilot.document.DocumentKind;

/** The facts read from each kind of document. The descriptions are shown to the model. */
public enum FactKey {

    INSURER_NAME(DocumentKind.POLICY, FactType.TEXT, "name of the insurance company that issues the plan"),
    INSURER_PHONE(DocumentKind.POLICY, FactType.PHONE, "customer service or claims telephone number for plan members"),
    INSURER_HOURS(DocumentKind.POLICY, FactType.TEXT, "days and hours when that telephone line is open"),
    POLICY_NUMBER(DocumentKind.POLICY, FactType.TEXT, "group policy number, contract number or plan number"),
    CERTIFICATE_NUMBER(DocumentKind.POLICY, FactType.TEXT, "the plan member's certificate number or member ID"),
    PLAN_MEMBER_NAME(DocumentKind.POLICY, FactType.TEXT, "full name of the plan member (the employee who holds the coverage)"),
    PLAN_SPONSOR(DocumentKind.POLICY, FactType.TEXT, "employer or plan sponsor that provides the plan"),
    CLAIMS_ADDRESS(DocumentKind.POLICY, FactType.TEXT, "mailing address where paper claims are sent"),

    PROVIDER_NAME(DocumentKind.RECEIPT, FactType.TEXT, "name of the clinic or health professional who provided the service"),
    PATIENT_NAME(DocumentKind.RECEIPT, FactType.TEXT, "name of the patient who received the service"),
    SERVICE_DATE(DocumentKind.RECEIPT, FactType.DATE, "date the service was provided"),
    SERVICE_TYPE(DocumentKind.RECEIPT, FactType.TEXT, "type of service, for example physiotherapy or dental cleaning"),
    AMOUNT_CHARGED(DocumentKind.RECEIPT, FactType.AMOUNT, "total amount charged for the service"),
    AMOUNT_PAID_BY_OTHER_PLAN(DocumentKind.RECEIPT, FactType.AMOUNT,
            "amount already paid by an insurance plan (direct billing), not by the patient"),
    RECEIPT_NUMBER(DocumentKind.RECEIPT, FactType.TEXT, "receipt or invoice number");

    private final DocumentKind kind;
    private final FactType type;
    private final String description;

    FactKey(DocumentKind kind, FactType type, String description) {
        this.kind = kind;
        this.type = type;
        this.description = description;
    }

    public static List<FactKey> forKind(DocumentKind kind) {
        return Arrays.stream(values()).filter(k -> k.kind == kind).toList();
    }

    public DocumentKind kind() {
        return kind;
    }

    public FactType type() {
        return type;
    }

    public String description() {
        return description;
    }
}
