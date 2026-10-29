package io.github.turtle358.callingpoint.utils.JourneyPlanner;

import androidx.annotation.Nullable;
import io.github.turtle358.callingpoint.models.JourneyLeg;
import io.github.turtle358.callingpoint.models.JourneyOption;

public final class JourneyChangeSummary {
    private JourneyChangeSummary() { }

    public static boolean isWalking(@Nullable JourneyLeg leg) {
        if (leg == null) return false;
        String mode = leg.transportMode == null ? "" : leg.transportMode.trim();
        if (mode.equalsIgnoreCase("TUBE/WALK") || mode.equalsIgnoreCase("WALK")
                || mode.equalsIgnoreCase("WALKING") || mode.equalsIgnoreCase("FOOT")) return true;
        String operator = leg.operatorName == null ? "" : leg.operatorName.trim();
        return operator.equalsIgnoreCase("Walk") || operator.equalsIgnoreCase("Walking");
    }

    public static int count(@Nullable JourneyOption journey) {
        if (journey == null || journey.legs == null) return 0;
        int riddenLegs = 0;
        for (JourneyLeg leg : journey.legs) {
            if (leg != null && !isWalking(leg)) riddenLegs++;
        }
        return Math.max(0, riddenLegs - 1);
    }

    public static String label(@Nullable JourneyOption journey) {
        if (journey == null || journey.legs == null || journey.legs.isEmpty()) return "Direct";
        int riddenLegs = 0;
        for (JourneyLeg leg : journey.legs) {
            if (leg != null && !isWalking(leg)) riddenLegs++;
        }
        if (riddenLegs == 0) return "Walking only";
        int changes = riddenLegs - 1;
        return changes == 0 ? "Direct" : changes + (changes == 1 ? " Change" : " Changes");
    }
}
