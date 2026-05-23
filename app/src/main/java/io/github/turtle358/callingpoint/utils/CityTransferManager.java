package io.github.turtle358.callingpoint.utils;

import java.util.HashMap;
import java.util.Map;

public class CityTransferManager {

    private static final Map<String, Map<String, Integer>> londonLinks = new HashMap<>();

    static {
        // Example London Terminal Links (Expand this as needed!)
        addLink("LBG", "EUS", 40);
        addLink("LBG", "KGX", 35);
        addLink("CHX", "EUS", 25);
        addLink("CHX", "STP", 30);
        addLink("CHX", "PAD", 30);
        addLink("VIC", "EUS", 25);
        addLink("VIC", "STP", 30);
        addLink("WAE", "PAD", 35);
    }

    private static void addLink(String stn1, String stn2, int timeMins) {
        londonLinks.computeIfAbsent(stn1, k -> new HashMap<>()).put(stn2, timeMins);
        londonLinks.computeIfAbsent(stn2, k -> new HashMap<>()).put(stn1, timeMins);
    }

    public static Map<String, Integer> getTransfersFrom(String crs) {
        return londonLinks.getOrDefault(crs, new HashMap<>());
    }
}