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

import com.claimpilot.user.Custody;

/**
 * Which plan pays first when a person is covered by two group plans. Pure code, no AI, following
 * the coordination of benefits guidelines Canadian group insurers apply (CLHIA):
 * <ol>
 *   <li>For the member or the spouse: the plan where the patient is the plan member (their own
 *       employer's plan) pays first; the plan where they are covered as a dependent pays second.</li>
 *   <li>For a child: the plan of the parent whose birthday (month and day) comes first in the
 *       calendar year pays first; if both parents share a birthday, the parent whose first name
 *       comes first alphabetically.</li>
 *   <li>For a child of separated or divorced parents, custody decides instead: the plan of the
 *       parent with custody, then that parent's spouse (the step-parent), then the parent without
 *       custody, then their spouse. With joint custody, the birthday rule applies between the two
 *       parents, and step-parents come after them.</li>
 * </ol>
 * A person with two plans of their own is reported as undecided rather than guessed, and so is a
 * household whose profile does not say enough.
 */
public final class CoordinationOfBenefits {

    /** Who received the care, seen from the signed-in member. */
    public enum Patient { ME, SPOUSE, CHILD }

    enum Holder { ME, SPOUSE, OTHER_PARENT, OTHER }

    /** A ready policy, the plan member named in it and its group policy number. */
    public record Plan(UUID policyId, String label, String memberName, String policyNumber) {
    }

    /**
     * The member's household, from the profile. With separated parents, the spouse is the member's
     * current spouse (the child's step-parent) and the other parent is the child's other parent.
     */
    public record Household(String myName, LocalDate myBirthday, String spouseName, LocalDate spouseBirthday,
                            Custody custody, String otherParentName, LocalDate otherParentBirthday) {

        public Household {
            custody = custody == null ? Custody.TOGETHER : custody;
        }

        public Household(String myName, LocalDate myBirthday, String spouseName, LocalDate spouseBirthday) {
            this(myName, myBirthday, spouseName, spouseBirthday, Custody.TOGETHER, null, null);
        }
    }

    /**
     * @param firstPolicyId   the plan that pays first
     * @param secondPolicyId  the plan to claim the balance on
     * @param relationshipOnSecond the patient's relationship to the plan member of the second plan
     * @param order           every plan that can pay, in the order they pay (two or more)
     */
    public record Decision(boolean decided, UUID firstPolicyId, UUID secondPolicyId,
                           Relationship relationshipOnSecond, String rule, String explanation, List<UUID> order) {

        Decision(boolean decided, UUID firstPolicyId, UUID secondPolicyId, Relationship relationshipOnSecond,
                 String rule, String explanation) {
            this(decided, firstPolicyId, secondPolicyId, relationshipOnSecond, rule, explanation,
                    firstPolicyId == null ? List.of() : List.of(firstPolicyId, secondPolicyId));
        }

        static Decision undecided(String explanation) {
            return new Decision(false, null, null, null, null, explanation, List.of());
        }
    }

    private static final String CAVEAT = " If you and your spouse are separated, set the custody in your profile: "
            + "custody rules apply instead.";
    private static final String COURT_ORDER = " A court order that makes one parent responsible for the child's "
            + "health expenses overrides these rules.";

    private CoordinationOfBenefits() {
    }

    public static Decision decide(Patient patient, List<Plan> plans, Household household) {
        List<Plan> mine = distinct(plans, household, Holder.ME);
        List<Plan> spouses = distinct(plans, household, Holder.SPOUSE);
        List<Plan> otherParents = distinct(plans, household, Holder.OTHER_PARENT);
        if (mine.size() > 1 || spouses.size() > 1 || otherParents.size() > 1) {
            return Decision.undecided("One person holds two plans here. Between a person's own plans, the plan "
                    + "from active full-time employment usually pays first; confirm with the insurers.");
        }
        if (patient == Patient.CHILD && household.custody() != Custody.TOGETHER) {
            return separatedParents(first(mine), first(spouses), first(otherParents), household);
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

    /**
     * A child of separated or divorced parents. The other parent's new spouse is not known here: if
     * they have a plan, it pays after the other parent's when the other parent has custody.
     */
    private static Decision separatedParents(Plan myPlan, Plan stepParentPlan, Plan otherParentPlan,
                                             Household household) {
        List<Plan> order = new java.util.ArrayList<>();
        String rule;
        String why;
        switch (household.custody()) {
            case SOLE_ME -> {
                addIfPresent(order, myPlan, stepParentPlan, otherParentPlan);
                rule = "Custody rule";
                why = "You have custody, so your plan pays first, then your spouse's (the step-parent's), then the "
                        + "other parent's.";
            }
            case SOLE_OTHER_PARENT -> {
                addIfPresent(order, otherParentPlan, myPlan, stepParentPlan);
                rule = "Custody rule";
                why = "The other parent has custody, so their plan pays first (then their spouse's, if they have "
                        + "one), then yours, then your spouse's.";
            }
            default -> {
                if (myPlan != null && otherParentPlan != null) {
                    if (household.myBirthday() == null || household.otherParentBirthday() == null) {
                        return Decision.undecided("With joint custody, the plan of the parent whose birthday comes "
                                + "first in the year pays first. Add both parents' birthdays in your profile.");
                    }
                    boolean mineFirst = birthdayFirst(household.myBirthday(), household.myName(),
                            household.otherParentBirthday(), household.otherParentName());
                    addIfPresent(order, mineFirst ? myPlan : otherParentPlan, mineFirst ? otherParentPlan : myPlan,
                            stepParentPlan);
                    MonthDay day = MonthDay.from(mineFirst ? household.myBirthday() : household.otherParentBirthday());
                    why = MonthDay.from(household.myBirthday()).equals(MonthDay.from(household.otherParentBirthday()))
                            ? "With joint custody and the same birthday, the parent whose first name comes first "
                                    + "alphabetically pays first, then the other parent, then step-parents."
                            : "With joint custody, the parent whose birthday comes first in the year (" + format(day)
                                    + ") pays first, then the other parent, then step-parents.";
                } else {
                    addIfPresent(order, myPlan, otherParentPlan, stepParentPlan);
                    why = "With joint custody, the parents' plans pay before a step-parent's.";
                }
                rule = "Joint custody";
            }
        }
        if (order.size() < 2) {
            return Decision.undecided(household.otherParentName() == null
                    ? "Add the child's other parent in your profile so ClaimPilot can tell whose plan is whose."
                    : "Only one plan here covers the child, so there is nothing to coordinate. Add the other "
                            + "parent's policy if they have one.");
        }
        StringBuilder explanation = new StringBuilder(why).append(" Claim first on ").append(order.getFirst().label())
                .append(", then the balance on ").append(order.get(1).label());
        if (order.size() > 2) {
            explanation.append(", then on ").append(order.get(2).label());
        }
        explanation.append('.').append(COURT_ORDER);
        return new Decision(true, order.get(0).policyId(), order.get(1).policyId(), Relationship.CHILD, rule,
                explanation.toString(), order.stream().map(Plan::policyId).toList());
    }

    private static void addIfPresent(List<Plan> order, Plan... plans) {
        for (Plan plan : plans) {
            if (plan != null) {
                order.add(plan);
            }
        }
    }

    private static Plan first(List<Plan> plans) {
        return plans.isEmpty() ? null : plans.getFirst();
    }

    /** The birthday rule: month and day, then first names alphabetically. */
    private static boolean birthdayFirst(LocalDate a, String aName, LocalDate b, String bName) {
        MonthDay first = MonthDay.from(a);
        MonthDay second = MonthDay.from(b);
        return first.equals(second) ? firstName(aName).compareTo(firstName(bName)) <= 0 : first.isBefore(second);
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

    /** One person's plans; the same policy uploaded twice is one plan, and the first (newest) copy is kept. */
    private static List<Plan> distinct(List<Plan> plans, Household household, Holder holder) {
        Map<String, Plan> byNumber = new LinkedHashMap<>();
        for (Plan plan : plans.stream().filter(p -> holder(p, household) == holder).toList()) {
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
        if (member.equals(normalize(household.otherParentName()))) {
            return Holder.OTHER_PARENT;
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
