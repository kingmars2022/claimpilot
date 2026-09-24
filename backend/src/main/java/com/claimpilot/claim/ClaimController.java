package com.claimpilot.claim;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.claimpilot.user.CurrentUserService;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {

    private final ClaimGuideService guides;
    private final ClaimDraftService drafts;
    private final CurrentUserService currentUser;

    public ClaimController(ClaimGuideService guides, ClaimDraftService drafts, CurrentUserService currentUser) {
        this.guides = guides;
        this.drafts = drafts;
        this.currentUser = currentUser;
    }

    @GetMapping("/types")
    public List<ClaimDtos.ClaimTypeOption> types() {
        return Arrays.stream(ClaimType.values()).map(t -> new ClaimDtos.ClaimTypeOption(t, t.label())).toList();
    }

    /** Module 2: steps, deadlines and documents for this claim type under this policy. */
    @GetMapping("/guide")
    public ClaimGuide guide(@RequestParam UUID policyId, @RequestParam ClaimType type) {
        return guides.guide(currentUser.get(), policyId, type);
    }

    /** Module 3: starts a claim and pre-fills the form from the policies, the receipt and the profile. */
    @PostMapping
    public ResponseEntity<ClaimDtos.Draft> create(@Valid @RequestBody ClaimDtos.CreateDraft request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(drafts.create(currentUser.get(), request));
    }

    @GetMapping
    public List<ClaimDtos.Draft> list() {
        return drafts.list(currentUser.get());
    }

    @GetMapping("/{id}")
    public ClaimDtos.Draft get(@PathVariable UUID id) {
        return drafts.get(currentUser.get(), id);
    }

    @PatchMapping("/{id}/fields/{key}")
    public ClaimDtos.Draft updateField(@PathVariable UUID id, @PathVariable DataKey key,
                                       @Valid @RequestBody ClaimDtos.UpdateField request) {
        return drafts.updateField(currentUser.get(), id, key, request);
    }

    /** The filled, still editable PDF, for the member to check, sign and submit. */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        var user = currentUser.get();
        byte[] pdf = drafts.pdf(user, id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(drafts.fileName(user, id)).build().toString())
                .body(pdf);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        drafts.delete(currentUser.get(), id);
        return ResponseEntity.noContent().build();
    }
}
