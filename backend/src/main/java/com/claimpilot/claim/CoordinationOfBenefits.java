package com.claimpilot.claim;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Which plan pays first when a person is covered by two group plans. Pure code, no AI, following
 * the coordination of benefits guidelines Canadian group insurers apply (CLHIA):
 * <ol>
 *   <li>For the member or the spouse: the plan where the patient is the plan member (their own
 *       employer's plan) pays first; the plan where they are covered as a dependent pays second.</li>
 *   <li>For a child: the plan of the parent whose birthday (month and day) comes first in the
 *       calendar year pays first; if both parents share a birthday, the parent whose first name
 *       comes first alphabetically.</li>
 * </ol>
 * Separated or divorced parents, and a person with two plans of their own, follow further rules;
 * those cases are reported as undecided rather than guessed.
 */
public final class CoordinationOfBenefits {

    /** Who received the care, seen from the signed-in member. */
    public enum Patient { ME, SPOUSE, CHILD }

    enum Holder { ME, SPOUSE, OTHER }

    /** A ready policy, the plan member named in it and its group policy number. */
    public record Plan(UUID policyId, String label, String memberName, String policyNumber) {
    }

    /** The member's household, from the profile. */
    public record Household(String myName, LocalDate myBirthday, String spouseName, LocalDate spouseBirthday) {
    }

    /**
     * @param firstPolicyId   the plan that pays first
     * @param secondPolicyId  the plan to claim the balance on
     * @param relationshipOnSecond the patient's relationship to the plan member of the second plan
     */
    public record Decision(boolean decided, UUID firstPolicyId, UUID secondPolicyId,
                           Relationship relationshipOnSecond, String rule, String explanation) {

        static Decision undecided(String explanation) {
            return new Decision(false, null, null, null, null, explanation);
        }
    }

    private static final String CAVEAT = " Separated or divorced parents follow custody rules instead; if that "
            + "applies, confirm with the insurer.";

    private CoordinationOfBenefits() {
    }

    public static Decision decide(Patient patient, List<Plan> plans, Household household) {
        List<Plan> mine = distinct(plans.stream().filter(p -> holder(p, household) == Holder.ME).toList());
        List<Plan> spouses = distinct(plans.stream().filter(p -> holder(p, household) == Holder.SPOUSE).toList());
        if (mine.size() > 1 || spouses.size() > 1) {
            return Decision.undecided("One person holds two plans here. Between a person's own plans, the plan "
                    + "from active full-time employment usually pays first; confirm with the insurers.");
        }
        if (mine.isEmpty() || spouses.isEmpty()) {
            return Decision.undecided(missingPlanMessage(mine.isEmpty(), spouses.isEmpty(), household));
        }
        Plan myPlan = mine.getFirst();
        Plan spousePlan = spouses.getFirst();
        return switch (patient) {
            case ME -> new Decision(true, myPlan.policyId(), spousePlan.policyId(), Relationship.SPOUSE,
                    "Own plan first",
                    "Your own plan (" + myPlan.label() + ") pays first. Claim the balance on your spouse's plan ("
                            + spousePlan.label() + ") with the statement from your plan.");
            case SPOUSE -> new Decision(true, spousePlan.policyId(), myPlan.policyId(), Relationship.SPOUSE,
                    "Own plan first",
                    "Your spouse's own plan (" + spousePlan.label() + ") pays first. Claim the balance on your plan ("
                            + myPlan.label() + ") with the statement from their plan.");
            case CHILD -> child(myPlan, spousePlan, household);
        };
    }

    private static Decision child(Plan myPlan, Plan spousePlan, Household household) {
        if (household.myBirthday() == null || household.spouseBirthday() == null) {
            return Decision.undecided("For a child, the plan of the parent whose birthday comes first in the year "
                    + "pays first. Add both birthdays in your profile to decide it.");
        }
        MonthDay mine = MonthDay.from(household.myBirthday());
        MonthDay theirs = MonthDay.from(household.spouseBirthday());
        boolean myPlanFirst;
        String why;
        if (!mine.equals(theirs)) {
            myPlanFirst = mine.isBefore(theirs);
            why = "the birthday that comes first in the year (" + format(myPlanFirst ? mine : theirs) + ")";
        } else {
            myPlanFirst = firstName(household.myName()).compareTo(firstName(household.spouseName())) <= 0;
            why = "the same birthday, so the first name that comes first alphabetically";
        }
        Plan first = myPlanFirst ? myPlan : spousePlan;
        Plan second = myPlanFirst ? spousePlan : myPlan;
        return new Decision(true, first.policyId(), second.policyId(), Relationship.CHILD, "Birthday rule",
                "For a child, the plan of the parent with " + why + " pays first: " + first.label()
                        + ". Claim the balance on " + second.label() + "." + CAVEAT);
    }

    /** The same policy uploaded twice is one plan: the first (newest) copy is kept. */
    private static List<Plan> distinct(List<Plan> plans) {
        Map<String, Plan> byNumber = new LinkedHashMap<>();
        for (Plan plan : plans) {
            String key = plan.policyNumber() == null ? plan.policyId().toString()
                    : plan.policyNumber().replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
            byNumber.putIfAbsent(key, plan);
        }
        return List.copyOf(byNumber.values());
    }

    static Holder holder(Plan plan, Household household) {
        String member = normalize(plan.memberName());
        if (member.isEmpty()) {
            return Holder.OTHER;
        }
        if (member.equals(normalize(household.myName()))) {
            return Holder.ME;
        }
        if (member.equals(normalize(household.spouseName()))) {
            return Holder.SPOUSE;
        }
        return Holder.OTHER;
    }

    private static String missingPlanMessage(boolean noneOfMine, boolean noneOfSpouse, Household household) {
        if (household.spouseName() == null && noneOfSpouse) {
            return "Add your spouse's name in your profile so ClaimPilot can tell whose plan is whose.";
        }
        if (noneOfMine && noneOfSpouse) {
            return "No policy names you or your spouse as plan member. Check the names in your profile.";
        }
        return noneOfMine ? "Add the policy that names you as plan member to coordinate two plans."
                : "Add your spouse's policy to coordinate two plans.";
    }

    private static String format(MonthDay day) {
        return day.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + day.getDayOfMonth();
    }

    private static String firstName(String name) {
        String n = name == null ? "" : name.strip();
        int space = n.indexOf(' ');
        return normalize(space < 0 ? n : n.substring(0, space));
    }

    private static String normalize(String name) {
        if (name == null) {
            return "";
        }
        return Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replaceAll("[^\\p{L}]", "").toLowerCase(Locale.ROOT);
    }
}
