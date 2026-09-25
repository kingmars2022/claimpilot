package com.claimpilot.claim;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.claimpilot.document.DocumentKind;
import com.claimpilot.document.DocumentResponse;
import com.claimpilot.document.DocumentService;
import com.claimpilot.document.DocumentStatus;
import com.claimpilot.extraction.FactKey;
import com.claimpilot.user.AppUser;
import com.claimpilot.user.Profile;
import com.claimpilot.user.ProfileRepository;

/** Applies {@link CoordinationOfBenefits} to the member's ready policies and profile. */
@Service
public class CoordinationService {

    private final DocumentService documents;
    private final ProfileRepository profiles;

    public CoordinationService(DocumentService documents, ProfileRepository profiles) {
        this.documents = documents;
        this.profiles = profiles;
    }

    public CoordinationOfBenefits.Decision decide(AppUser user, CoordinationOfBenefits.Patient patient) {
        return CoordinationOfBenefits.decide(patient, plans(user), household(user));
    }

    /**
     * For a balance claim about to be made: the decision, when the member is claiming on a plan other
     * than the one that pays second (the one that pays first, or a third one). Empty when the choice
     * is right or cannot be checked.
     */
    public Optional<CoordinationOfBenefits.Decision> conflictWith(AppUser user, UUID claimOn, Relationship relationship) {
        List<CoordinationOfBenefits.Plan> plans = plans(user);
        CoordinationOfBenefits.Household household = household(user);
        CoordinationOfBenefits.Plan target = plans.stream().filter(p -> p.policyId().equals(claimOn)).findFirst()
                .orElse(null);
        if (target == null || relationship == null) {
            return Optional.empty();
        }
        CoordinationOfBenefits.Holder holder = CoordinationOfBenefits.holder(target, household);
        CoordinationOfBenefits.Patient patient = switch (relationship) {
            case CHILD -> CoordinationOfBenefits.Patient.CHILD;
            case SELF -> holder == CoordinationOfBenefits.Holder.ME ? CoordinationOfBenefits.Patient.ME
                    : holder == CoordinationOfBenefits.Holder.SPOUSE ? CoordinationOfBenefits.Patient.SPOUSE : null;
            case SPOUSE -> holder == CoordinationOfBenefits.Holder.SPOUSE ? CoordinationOfBenefits.Patient.ME
                    : holder == CoordinationOfBenefits.Holder.ME ? CoordinationOfBenefits.Patient.SPOUSE : null;
        };
        if (patient == null) {
            return Optional.empty();
        }
        CoordinationOfBenefits.Decision decision = CoordinationOfBenefits.decide(patient, plans, household);
        if (!decision.decided()) {
            return Optional.empty();
        }
        // Compared by whose plan it is, so a second copy of the same policy counts as that plan. A
        // balance belongs on the plan that pays second: claiming it on the first, or skipping to a
        // third, is out of order.
        List<CoordinationOfBenefits.Holder> order = decision.order().stream()
                .map(id -> plans.stream().filter(p -> p.policyId().equals(id)).findFirst()
                        .map(p -> CoordinationOfBenefits.holder(p, household)).orElse(null))
                .toList();
        int position = order.indexOf(holder);
        return position >= 0 && position != 1 ? Optional.of(decision) : Optional.empty();
    }

    private List<CoordinationOfBenefits.Plan> plans(AppUser user) {
        return documents.list(user, DocumentKind.POLICY).stream()
                .filter(d -> d.status() == DocumentStatus.READY)
                .map(d -> new CoordinationOfBenefits.Plan(d.id(), label(d), fact(d, FactKey.PLAN_MEMBER_NAME),
                        fact(d, FactKey.POLICY_NUMBER)))
                .toList();
    }

    private CoordinationOfBenefits.Household household(AppUser user) {
        Profile profile = profiles.findById(user.getId()).orElse(null);
        return profile == null
                ? new CoordinationOfBenefits.Household(user.getDisplayName(), null, null, null)
                : new CoordinationOfBenefits.Household(
                        profile.getFullName() == null ? user.getDisplayName() : profile.getFullName(),
                        profile.getDateOfBirth(), profile.getSpouseName(), profile.getSpouseDateOfBirth(),
                        profile.getCustody(), profile.getOtherParentName(), profile.getOtherParentDateOfBirth());
    }

    private static String label(DocumentResponse d) {
        String insurer = fact(d, FactKey.INSURER_NAME);
        return insurer == null ? d.fileName() : insurer;
    }

    private static String fact(DocumentResponse d, FactKey key) {
        return d.facts().stream().filter(f -> f.key().equals(key.name())).map(DocumentResponse.Fact::value)
                .findFirst().orElse(null);
    }
}
