package io.github.turtle358.callingpoint.utils;

import java.util.HashMap;
import java.util.Map;

public class CityTransferManager {

    private static final Map<String, Map<String, Integer>> londonLinks = new HashMap<>();

    static {

        // 1. LONDON BRIDGE (LBG)
        addLink("LBG", "CHX", 20);
        addLink("LBG", "VIC", 30);
        addLink("LBG", "WAT", 15);
        addLink("LBG", "WAE", 15);
        addLink("LBG", "EUS", 25);
        addLink("LBG", "STP", 25);
        addLink("LBG", "KGX", 25);
        addLink("LBG", "PAD", 35);
        addLink("LBG", "LST", 15);

        // 2. CHARING CROSS (CHX)
        addLink("CHX", "VIC", 15);
        addLink("CHX", "WAT", 15);
        addLink("CHX", "WAE", 15);
        addLink("CHX", "EUS", 20);
        addLink("CHX", "STP", 25);
        addLink("CHX", "KGX", 25);
        addLink("CHX", "PAD", 20);
        addLink("CHX", "LST", 25);

        // 3. VICTORIA (VIC)
        addLink("VIC", "WAT", 20);
        addLink("VIC", "WAE", 20);
        addLink("VIC", "EUS", 15);
        addLink("VIC", "STP", 20);
        addLink("VIC", "KGX", 20);
        addLink("VIC", "PAD", 20);
        addLink("VIC", "LST", 30);

        // 4. WATERLOO (WAT)
        addLink("WAT", "WAE", 5);
        addLink("WAT", "EUS", 20);
        addLink("WAT", "STP", 25);
        addLink("WAT", "KGX", 25);
        addLink("WAT", "PAD", 25);
        addLink("WAT", "LST", 20);

        // 5. WATERLOO EAST (WAE)
        addLink("WAE", "EUS", 25);
        addLink("WAE", "STP", 30);
        addLink("WAE", "KGX", 30);
        addLink("WAE", "PAD", 30);
        addLink("WAE", "LST", 25);

        // 6. EUSTON (EUS)
        addLink("EUS", "STP", 15);
        addLink("EUS", "KGX", 15);
        addLink("EUS", "PAD", 20);
        addLink("EUS", "LST", 20);

        // 7. ST PANCRAS (STP)
        addLink("STP", "KGX", 5);
        addLink("STP", "PAD", 20);
        addLink("STP", "LST", 15);

        // 8. KINGS CROSS (KGX)
        addLink("KGX", "PAD", 20);
        addLink("KGX", "LST", 15);

        // 9. PADDINGTON (PAD)
        addLink("PAD", "LST", 25);

        // 10. FENCHURCH STREET (FST)
        addLink("FST", "LBG", 20);
        addLink("FST", "CHX", 20);
        addLink("FST", "VIC", 25);
        addLink("FST", "WAT", 25);
        addLink("FST", "WAE", 30);
        addLink("FST", "EUS", 25);
        addLink("FST", "STP", 20);
        addLink("FST", "KGX", 20);
        addLink("FST", "PAD", 30);
        addLink("FST", "LST", 15);
    }

    private static void addLink(String stn1, String stn2, int timeMins) {
        londonLinks.computeIfAbsent(stn1, k -> new HashMap<>()).put(stn2, timeMins);
        londonLinks.computeIfAbsent(stn2, k -> new HashMap<>()).put(stn1, timeMins);
    }

    public static Map<String, Integer> getTransfersFrom(String crs) {
        return londonLinks.getOrDefault(crs, new HashMap<>());
    }
}