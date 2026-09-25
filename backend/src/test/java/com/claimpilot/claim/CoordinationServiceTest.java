package com.claimpilot.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.claimpilot.document.DocumentKind;
import com.claimpilot.document.DocumentResponse;
import com.claimpilot.document.DocumentService;
import com.claimpilot.document.DocumentStatus;
import com.claimpilot.extraction.FactKey;
import com.claimpilot.user.AppUser;
import com.claimpilot.user.Custody;
import com.claimpilot.user.Profile;
import com.claimpilot.user.ProfileDto;
import com.claimpilot.user.ProfileRepository;

/** Which balance claims are out of order, with three plans for a child of separated parents. */
class CoordinationServiceTest {

    private static final UUID FIONAS = UUID.randomUUID();
    private static final UUID MARCS = UUID.randomUUID();
    private static final UUID LUCS = UUID.randomUUID();

    private final CoordinationService service = service();

    @Test
    void theBalanceBelongsOnThePlanThatPaysSecond() {
        // Fiona has custody: Fiona's plan, then Marc's (step-parent), then Luc's.
        AppUser fiona = new AppUser("fiona", "Fiona Tremblay", "x");

        assertThat(service.conflictWith(fiona, MARCS, Relationship.CHILD)).as("second: right").isEmpty();
        assertThat(service.conflictWith(fiona, FIONAS, Relationship.CHILD)).as("first: out of order").isPresent();
        Optional<CoordinationOfBenefits.Decision> skipped = service.conflictWith(fiona, LUCS, Relationship.CHILD);
        assertThat(skipped).as("third: out of order").isPresent();
        assertThat(skipped.get().secondPolicyId()).isEqualTo(MARCS);
    }

    private static CoordinationService service() {
        DocumentService documents = mock(DocumentService.class);
        when(documents.list(any(), any())).thenReturn(List.of(
                policy(FIONAS, "Harbourline Vie", "Fiona Tremblay"),
                policy(MARCS, "Cedarview Assurance", "Marc Gagnon"),
                policy(LUCS, "Northgate Life", "Luc Bergeron")));
        Profile profile = new Profile(1L);
        profile.update(new ProfileDto("Fiona Tremblay", LocalDate.of(1991, 4, 17), null, null, null, null, null,
                "Marc Gagnon", LocalDate.of(1989, 11, 2), Custody.SOLE_ME, "Luc Bergeron", null));
        ProfileRepository profiles = mock(ProfileRepository.class);
        when(profiles.findById(any())).thenReturn(Optional.of(profile));
        return new CoordinationService(documents, profiles);
    }

    private static DocumentResponse policy(UUID id, String insurer, String member) {
        return new DocumentResponse(id, DocumentKind.POLICY, insurer + ".pdf", 1, DocumentStatus.READY, 3, null,
                null, null, List.of(
                        new DocumentResponse.Fact(FactKey.INSURER_NAME.name(), insurer, insurer, 1, true),
                        new DocumentResponse.Fact(FactKey.PLAN_MEMBER_NAME.name(), member, member, 1, true),
                        new DocumentResponse.Fact(FactKey.POLICY_NUMBER.name(), id.toString(), id.toString(), 1,
                                true)));
    }
}
