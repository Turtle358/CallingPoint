package com.example.delaywatcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StationHelper {
    private static final Map<String, String> stationMap = new HashMap<>();

    static {
        stationMap.put("London Paddington", "PAD");
        stationMap.put("London Waterloo", "WAT");
        stationMap.put("London Liverpool Street", "LST");
        stationMap.put("London Victoria", "VIC");
        stationMap.put("London Bridge", "LBG");
        stationMap.put("London Euston", "EUS");
        stationMap.put("London King's Cross", "KGX");
        stationMap.put("London St Pancras Intl", "STP");
        stationMap.put("Stratford (London)", "SRA");
        stationMap.put("Shenfield", "SNF");
        stationMap.put("Reading", "RDG");
        stationMap.put("Bristol Temple Meads", "BRI");
        stationMap.put("Manchester Piccadilly", "MAN");
        stationMap.put("Birmingham New Street", "BHM");
        stationMap.put("Leeds", "LDS");
        stationMap.put("Edinburgh Waverley", "EDB");
        stationMap.put("Glasgow Central", "GLC");
        stationMap.put("Cardiff Central", "CDF");
        stationMap.put("Brighton", "BTN");
        stationMap.put("Gatwick Airport", "GTW");
        stationMap.put("Heathrow Airport T5", "HWV");
        stationMap.put("Abbey Wood", "ABW");
        stationMap.put("Farringdon", "ZFD");
        // TODO - ADD MORE STATIONS
    }

    public static List<String> getStationNames() {
        return new ArrayList<>(stationMap.keySet());
    }

    public static String getCrsFromName(String name) {
        return stationMap.get(name);
    }
}