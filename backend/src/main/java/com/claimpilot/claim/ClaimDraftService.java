package com.claimpilot.claim;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.claimpilot.audit.AuditAction;
import com.claimpilot.audit.AuditService;
import com.claimpilot.common.NotFoundException;
import com.claimpilot.document.DocumentKind;
import com.claimpilot.document.DocumentService;
import com.claimpilot.document.DocumentStatus;
import com.claimpilot.document.UploadedDocument;
import com.claimpilot.extraction.DocumentFact;
import com.claimpilot.extraction.DocumentFactRepository;
import com.claimpilot.extraction.FactKey;
import com.claimpilot.user.AppUser;
import com.claimpilot.user.Profile;
import com.claimpilot.user.ProfileRepository;

/**
 * Module 3: pre-filled claim forms. A draft holds one value per field the form asks for, each with
 * its source. The member reviews every field, corrects what is wrong, and only then downloads
 * the filled PDF to sign and submit personally.
 */
@Service
public class ClaimDraftService {

    private final ClaimDraftRepository drafts;
    private final DocumentService documents;
    private final DocumentFactRepository facts;
    private final ProfileRepository profiles;
    private final FieldMappingService mappings;
    private final FormCatalog forms;
    private final AuditService audit;

    public ClaimDraftService(ClaimDraftRepository drafts, DocumentService documents, DocumentFactRepository facts,
                             ProfileRepository profiles, FieldMappingService mappings, FormCatalog forms,
                             AuditService audit) {
        this.audit = audit;
        this.drafts = drafts;
        this.documents = documents;
        this.facts = facts;
        this.profiles = profiles;
        this.mappings = mappings;
        this.forms = forms;
    }

    @Transactional
    public ClaimDtos.Draft create(AppUser user, ClaimDtos.CreateDraft request) {
        if (request.policyId().equals(request.otherPolicyId())) {
            throw new IllegalArgumentException("The plan you claim on and the plan that paid first must be different.");
        }
        UploadedDocument policy = ready(user, DocumentKind.POLICY, request.policyId());
        UploadedDocument other = request.otherPolicyId() == null ? null
                : ready(user, DocumentKind.POLICY, request.otherPolicyId());
        UploadedDocument receipt = request.receiptId() == null ? null
                : ready(user, DocumentKind.RECEIPT, request.receiptId());
        Profile profile = profiles.findById(user.getId()).orElse(null);
        String formKey = request.formKey() == null || request.formKey().isBlank()
                ? FormCatalog.DEFAULT_KEY : request.formKey();
        FormTemplate form = forms.resolve(user, formKey);

        Map<DataKey, DraftValue> values = ClaimValueAssembler.assemble(source(policy), source(other),
                source(receipt), profile, request.relationship());

        ClaimDraft draft = new ClaimDraft(user.getId(), request.claimType(), policy.getId(),
                other == null ? null : other.getId(), receipt == null ? null : receipt.getId(),
                request.relationship(), formKey);
        for (DataKey key : fieldsOnForm(form)) {
            draft.addField(key, values.getOrDefault(key, DraftValue.missing()));
        }
        ClaimDraft saved = drafts.save(draft);
        audit.record(user.getId(), AuditAction.CLAIM_CREATED, "CLAIM", saved.getId(),
                request.claimType().label() + " on " + forms.name(user, formKey));
        return toDto(user, saved);
    }

    /** Newest first, one page at a time; the form details are worked out once per form, not per claim. */
    public List<ClaimDtos.Draft> list(AppUser user, int page, int size) {
        Map<String, List<String>> leftForYouByForm = new HashMap<>();
        return drafts.findByOwnerIdOrderByUpdatedAtDesc(user.getId(), PageRequest.of(page, size)).stream()
                .map(d -> toDto(user, d, leftForYouByForm.computeIfAbsent(d.getFormKey(),
                        key -> leftForYou(user, d))))
                .toList();
    }

    public ClaimDtos.Draft get(AppUser user, UUID id) {
        return toDto(user, find(user, id));
    }

    @Transactional
    public ClaimDtos.Draft updateField(AppUser user, UUID id, DataKey key, ClaimDtos.UpdateField request) {
        ClaimDraft draft = find(user, id);
        ClaimDraftField field = draft.field(key)
                .orElseThrow(() -> new NotFoundException("This form has no field " + key + "."));
        if (request.value() != null) {
            field.correct(request.value());
            audit.record(user.getId(), AuditAction.CLAIM_FIELD_CORRECTED, "CLAIM", id, key.label());
        }
        if (request.reviewed() != null) {
            field.setReviewed(request.reviewed());
        }
        draft.touch();
        return toDto(user, draft);
    }

    /** The filled PDF. Refused until the member has reviewed every field. */
    public byte[] pdf(AppUser user, UUID id) {
        ClaimDraft draft = find(user, id);
        if (!draft.fullyReviewed()) {
            throw new IllegalArgumentException("Review every field before downloading the form.");
        }
        Map<DataKey, String> values = new EnumMap<>(DataKey.class);
        draft.getFields().forEach(f -> values.put(f.getDataKey(), f.getValue()));
        FormTemplate form = forms.resolve(user, draft.getFormKey());
        byte[] pdf = PdfFormFiller.fill(form, mappings.mappingFor(form), values);
        audit.record(user.getId(), AuditAction.CLAIM_DOWNLOADED, "CLAIM", id, form.name());
        return pdf;
    }

    @Transactional
    public void delete(AppUser user, UUID id) {
        ClaimDraft draft = find(user, id);
        drafts.delete(draft);
        audit.record(user.getId(), AuditAction.CLAIM_DELETED, "CLAIM", id, draft.getClaimType().label());
    }

    public String fileName(AppUser user, UUID id) {
        ClaimDraft draft = find(user, id);
        return "claim-" + draft.getClaimType().name().toLowerCase(java.util.Locale.ROOT).replace('_', '-') + "-"
                + draft.getId().toString().substring(0, 8) + ".pdf";
    }

    /** The data items this form asks for, in form order, excluding items left for the member. */
    private Set<DataKey> fieldsOnForm(FormTemplate form) {
        Set<DataKey> keys = new LinkedHashSet<>();
        mappings.mappingFor(form).values().stream().filter(k -> !k.neverFill()).forEach(keys::add);
        return keys;
    }

    /** Labels of the form fields the member must complete personally. */
    private List<String> leftForYou(AppUser user, ClaimDraft draft) {
        FormTemplate form;
        try {
            form = forms.resolve(user, draft.getFormKey());
        } catch (NotFoundException | IllegalArgumentException ex) {
            return List.of();
        }
        Map<String, DataKey> mapping = mappings.mappingFor(form);
        return form.fields().stream()
                .filter(f -> mapping.getOrDefault(f.name(), DataKey.NONE).neverFill())
                .map(f -> f.label().isBlank() ? f.name() : f.label())
                .toList();
    }

    private ClaimDraft find(AppUser user, UUID id) {
        return drafts.findByIdAndOwnerId(id, user.getId())
                .orElseThrow(() -> new NotFoundException("Claim " + id + " does not exist."));
    }

    private UploadedDocument ready(AppUser user, DocumentKind kind, UUID id) {
        UploadedDocument doc = documents.find(user, kind, id);
        if (doc.getStatus() != DocumentStatus.READY) {
            throw new IllegalArgumentException(doc.getFileName() + " is not ready yet. Try again in a moment.");
        }
        return doc;
    }

    private ClaimValueAssembler.Source source(UploadedDocument doc) {
        if (doc == null) {
            return ClaimValueAssembler.Source.none();
        }
        Map<FactKey, DocumentFact> byKey = facts.findByDocumentId(doc.getId()).stream()
                .collect(Collectors.toMap(DocumentFact::getKey, Function.identity(), (a, b) -> a));
        return new ClaimValueAssembler.Source(doc.getId(), doc.getFileName(), byKey);
    }

    private ClaimDtos.Draft toDto(AppUser user, ClaimDraft draft) {
        return toDto(user, draft, leftForYou(user, draft));
    }

    private ClaimDtos.Draft toDto(AppUser user, ClaimDraft draft, List<String> leftForYou) {
        return new ClaimDtos.Draft(draft.getId(), draft.getClaimType(), draft.getClaimType().label(),
                ref(draft.getPolicyId()), ref(draft.getOtherPolicyId()), ref(draft.getReceiptId()),
                new ClaimDtos.FormRef(draft.getFormKey(), forms.name(user, draft.getFormKey())),
                draft.getRelationship(),
                draft.getFields().stream().map(ClaimDtos.Field::from).toList(),
                leftForYou, draft.fullyReviewed(), draft.getCreatedAt(), draft.getUpdatedAt());
    }

    private ClaimDtos.DocumentRef ref(UUID id) {
        if (id == null) {
            return null;
        }
        return documents.fileNameOf(id).map(name -> new ClaimDtos.DocumentRef(id, name)).orElse(null);
    }
}
