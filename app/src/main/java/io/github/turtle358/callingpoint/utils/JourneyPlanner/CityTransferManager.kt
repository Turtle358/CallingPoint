package io.github.turtle358.callingpoint.utils.JourneyPlanner;

import java.util.HashMap;
import java.util.Map;

public class CityTransferManager {

    public static class TransferInfo {
        public final int timeMins;
        public final String operatorName;
        public final String serviceId;

        public TransferInfo(int timeMins, String operatorName, String serviceId) {
            this.timeMins = timeMins;
            this.operatorName = operatorName;
            this.serviceId = serviceId;
        }
    }

    private static final Map<String, Map<String, TransferInfo>> crossCityLinks = new HashMap<>();

    static {
        // ==========================================
        // LONDON (Tube / Walk)
        // ==========================================
        String lonOp = "London Underground / Walk";
        String lonId = "TUBE/WALK";
        addLink("LBG", "CHX", 20, lonOp, lonId);
        addLink("LBG", "VIC", 30, lonOp, lonId);
        addLink("LBG", "WAT", 15, lonOp, lonId);
        addLink("LBG", "WAE", 15, lonOp, lonId);
        addLink("LBG", "EUS", 25, lonOp, lonId);
        addLink("LBG", "STP", 25, lonOp, lonId);
        addLink("LBG", "KGX", 25, lonOp, lonId);
        addLink("LBG", "PAD", 35, lonOp, lonId);
        addLink("LBG", "LST", 15, lonOp, lonId);

        addLink("CHX", "VIC", 15, lonOp, lonId);
        addLink("CHX", "WAT", 15, lonOp, lonId);
        addLink("CHX", "WAE", 15, lonOp, lonId);
        addLink("CHX", "EUS", 20, lonOp, lonId);
        addLink("CHX", "STP", 25, lonOp, lonId);
        addLink("CHX", "KGX", 25, lonOp, lonId);
        addLink("CHX", "PAD", 20, lonOp, lonId);
        addLink("CHX", "LST", 25, lonOp, lonId);

        addLink("VIC", "WAT", 20, lonOp, lonId);
        addLink("VIC", "WAE", 20, lonOp, lonId);
        addLink("VIC", "EUS", 15, lonOp, lonId);
        addLink("VIC", "STP", 20, lonOp, lonId);
        addLink("VIC", "KGX", 20, lonOp, lonId);
        addLink("VIC", "PAD", 20, lonOp, lonId);
        addLink("VIC", "LST", 30, lonOp, lonId);

        addLink("WAT", "WAE", 5, "Walk", "WALK"); // Specifically just a walk
        addLink("WAT", "EUS", 20, lonOp, lonId);
        addLink("WAT", "STP", 25, lonOp, lonId);
        addLink("WAT", "KGX", 25, lonOp, lonId);
        addLink("WAT", "PAD", 25, lonOp, lonId);
        addLink("WAT", "LST", 20, lonOp, lonId);

        addLink("WAE", "EUS", 25, lonOp, lonId);
        addLink("WAE", "STP", 30, lonOp, lonId);
        addLink("WAE", "KGX", 30, lonOp, lonId);
        addLink("WAE", "PAD", 30, lonOp, lonId);
        addLink("WAE", "LST", 25, lonOp, lonId);

        addLink("EUS", "STP", 15, "Walk", "WALK"); // Euston to St Pancras is walkable
        addLink("EUS", "KGX", 15, "Walk", "WALK");
        addLink("EUS", "PAD", 20, lonOp, lonId);
        addLink("EUS", "LST", 20, lonOp, lonId);

        addLink("STP", "KGX", 5, "Walk", "WALK"); // Literally across the street
        addLink("STP", "PAD", 20, lonOp, lonId);
        addLink("STP", "LST", 15, lonOp, lonId);

        addLink("KGX", "PAD", 20, lonOp, lonId);
        addLink("KGX", "LST", 15, lonOp, lonId);

        addLink("PAD", "LST", 25, "Elizabeth Line / Tube", "ELIZABETH");

        addLink("FST", "LBG", 20, lonOp, lonId);
        addLink("FST", "CHX", 20, lonOp, lonId);
        addLink("FST", "VIC", 25, lonOp, lonId);
        addLink("FST", "WAT", 25, lonOp, lonId);
        addLink("FST", "WAE", 30, lonOp, lonId);
        addLink("FST", "EUS", 25, lonOp, lonId);
        addLink("FST", "STP", 20, lonOp, lonId);
        addLink("FST", "KGX", 20, lonOp, lonId);
        addLink("FST", "PAD", 30, lonOp, lonId);
        addLink("FST", "LST", 15, "Walk", "WALK"); // Fenchurch to Liverpool St is a short walk

        // ==========================================
        // MANCHESTER (Metrolink / Walk)
        // ==========================================
        String manOp = "Metrolink / Walk";
        String manId = "TRAM/WALK";
        addLink("MAN", "MCV", 20, manOp, manId);
        addLink("MAN", "MCO", 15, "Walk", "WALK");
        addLink("MCO", "MCV", 20, "Walk", "WALK");

        // ==========================================
        // BIRMINGHAM (Walk)
        // ==========================================
        addLink("BHM", "BMO", 15, "Walk", "WALK");
        addLink("BHM", "BSW", 15, "Walk / Metro", "TRAM/WALK");
        addLink("BMO", "BSW", 10, "Walk", "WALK");

        // ==========================================
        // GLASGOW (Walk / Subway)
        // ==========================================
        addLink("GLC", "GLQ", 15, "Walk / Bus", "WALK/BUS");

        // ==========================================
        // LIVERPOOL (Merseyrail / Walk)
        // ==========================================
        String livOp = "Merseyrail / Walk";
        String livId = "MERSEYRAIL";
        addLink("LIV", "LVC", 15, livOp, livId);
        addLink("LIV", "MRF", 15, livOp, livId);
        addLink("LIV", "LVJ", 20, livOp, livId);

        // ==========================================
        // BRADFORD (Walk)
        // ==========================================
        addLink("BDI", "BDQ", 15, "Walk", "WALK");

        // ==========================================
        // CARDIFF (Walk)
        // ==========================================
        addLink("CDF", "CDQ", 15, "Walk", "WALK");
    }

    private static void addLink(String stn1, String stn2, int timeMins, String op, String id) {
        TransferInfo info = new TransferInfo(timeMins, op, id);
        crossCityLinks.computeIfAbsent(stn1, k -> new HashMap<>()).put(stn2, info);
        crossCityLinks.computeIfAbsent(stn2, k -> new HashMap<>()).put(stn1, info);
    }

    public static Map<String, TransferInfo> getTransfersFrom(String crs) {
        return crossCityLinks.getOrDefault(crs, new HashMap<>());
    }
}