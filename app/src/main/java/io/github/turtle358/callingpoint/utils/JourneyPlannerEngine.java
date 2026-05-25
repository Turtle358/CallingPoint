package io.github.turtle358.callingpoint.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.turtle358.callingpoint.api.APIClient;
import io.github.turtle358.callingpoint.api.NationalRailAPI;
import io.github.turtle358.callingpoint.models.JourneyLeg;
import io.github.turtle358.callingpoint.models.JourneyOption;
import io.github.turtle358.callingpoint.models.ServiceDetailsResponse;
import io.github.turtle358.callingpoint.models.StationDepartureResponse;
import retrofit2.Response;

public class JourneyPlannerEngine {

    private static final String TAG = "JourneyPlanner";
    private static Map<String, String> crsToNameMap = null;

    public interface JourneyCallback {
        void onJourneysFound(List<JourneyOption> journeys);
        void onError(String message);
        void onStatusUpdate(String message);
    }

    private final NationalRailAPI api;
    private final GraphManager graphManager;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final String ldbwsApiKey;
    private final String serviceDetailsApiKey;

    private Map<String, StationDepartureResponse> boardCache;
    private Map<String, ServiceDetailsResponse> detailCache;

    public JourneyPlannerEngine(GraphManager graphManager, String ldbwsApiKey, String serviceDetailsApiKey) {
        this.api = APIClient.getClient().create(NationalRailAPI.class);
        this.graphManager = graphManager;
        this.ldbwsApiKey = ldbwsApiKey;
        this.serviceDetailsApiKey = serviceDetailsApiKey;
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void findRoutes(String originCrs, String destCrs, JourneyCallback callback) {
        executor.execute(() -> {
            boardCache = new HashMap<>();
            detailCache = new HashMap<>();
            List<JourneyOption> foundJourneys = new ArrayList<>();

            try {
                mainHandler.post(() -> callback.onStatusUpdate("Checking for direct trains..."));
                findDirect(originCrs, destCrs, foundJourneys);

                if (foundJourneys.size() < 5) {
                    mainHandler.post(() -> callback.onStatusUpdate("Checking 1-change connections..."));
                    findOneChange(originCrs, destCrs, foundJourneys);
                }

                if (foundJourneys.size() < 3) {
                    mainHandler.post(() -> callback.onStatusUpdate("Checking cross-city Tube links..."));
                    findCrossCity(originCrs, destCrs, foundJourneys);
                }

                if (foundJourneys.size() < 3) {
                    mainHandler.post(() -> callback.onStatusUpdate("Deep searching 2-change routes..."));
                    findTwoChange(originCrs, destCrs, foundJourneys);
                }

                foundJourneys.sort((j1, j2) -> {
                    String arr1 = j1.arriveTime != null ? j1.arriveTime : "23:59";
                    String arr2 = j2.arriveTime != null ? j2.arriveTime : "23:59";
                    return arr1.compareTo(arr2);
                });
                mainHandler.post(() -> callback.onJourneysFound(foundJourneys));

            } catch (Exception e) {
                Log.e(TAG, "Network error during search", e);
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }
        });
    }

    private void findDirect(String originCrs, String destCrs, List<JourneyOption> results) {
        StationDepartureResponse directRes = getBoard(originCrs, 25, destCrs);
        if (directRes != null && directRes.trainServices != null) {
            for (StationDepartureResponse.TrainService service : directRes.trainServices) {
                String arrTime = getPreciseArrivalTime(service.serviceID, destCrs);
                if (arrTime == null) arrTime = "00:00";

                String destName = (service.destination != null && !service.destination.isEmpty()) ? service.destination.get(0).locationName : destCrs;
                List<JourneyLeg.Stop> intermediateStops = extractIntermediateStops(service.serviceID, originCrs, destCrs);

                List<JourneyLeg> legs = new ArrayList<>();
                legs.add(new JourneyLeg(originCrs, getStationName(originCrs), destCrs, getStationName(destCrs), destName, service.std, arrTime, service.operatorName, service.serviceID, "TRAIN", intermediateStops, getLiveLocationStatus(service.serviceID), service.etd));
                results.add(new JourneyOption(legs));

                graphManager.addConnection(originCrs, destCrs);
            }
        }
    }

    private void findOneChange(String originCrs, String destCrs, List<JourneyOption> results) {
        StationDepartureResponse allDepRes = getBoard(originCrs, 20, null);
        if (allDepRes == null || allDepRes.trainServices == null) return;

        List<String> hubs = getPrioritizedHubs(allDepRes.trainServices, originCrs);
        int hubCount = 0;

        for (String hubCrs : hubs) {
            if (hubCrs.equals(destCrs) || hubCrs.equals(originCrs)) continue;
            if (hubCount++ >= 8) break;

            StationDepartureResponse leg2Res = getBoard(hubCrs, 50, destCrs);
            if (leg2Res != null && leg2Res.trainServices != null) {

                int leg1Count = 0;
                for (StationDepartureResponse.TrainService t1 : allDepRes.trainServices) {
                    if (leg1Count++ >= 6) break;

                    String t1ArrTime = getPreciseArrivalTime(t1.serviceID, hubCrs);
                    if (t1ArrTime == null) continue;

                    for (StationDepartureResponse.TrainService t2 : leg2Res.trainServices) {
                        if (hasValidTransferTime(t1ArrTime, t2.std, 5, 180)) {
                            String t2ArrTime = getPreciseArrivalTime(t2.serviceID, destCrs);
                            if (t2ArrTime == null) t2ArrTime = "00:00";

                            String t1Dest = (t1.destination != null && !t1.destination.isEmpty()) ? t1.destination.get(0).locationName : hubCrs;
                            String t2Dest = (t2.destination != null && !t2.destination.isEmpty()) ? t2.destination.get(0).locationName : destCrs;

                            List<JourneyLeg.Stop> stops1 = extractIntermediateStops(t1.serviceID, originCrs, hubCrs);
                            List<JourneyLeg.Stop> stops2 = extractIntermediateStops(t2.serviceID, hubCrs, destCrs);

                            List<JourneyLeg> legs = new ArrayList<>();
                            legs.add(new JourneyLeg(originCrs, getStationName(originCrs), hubCrs, getStationName(hubCrs), t1Dest, t1.std, t1ArrTime, t1.operatorName, t1.serviceID, "TRAIN", stops1, getLiveLocationStatus(t1.serviceID), t1.etd));
                            legs.add(new JourneyLeg(hubCrs, getStationName(hubCrs), destCrs, getStationName(destCrs), t2Dest, t2.std, t2ArrTime, t2.operatorName, t2.serviceID, "TRAIN", stops2, getLiveLocationStatus(t2.serviceID), t2.etd));
                            results.add(new JourneyOption(legs));

                            graphManager.addConnection(originCrs, hubCrs);
                            graphManager.addConnection(hubCrs, destCrs);
                            break;
                        }
                    }
                    if (results.size() >= 10) return;
                }
            }
        }
    }

    private void findTwoChange(String originCrs, String destCrs, List<JourneyOption> results) {
        StationDepartureResponse allOrigin = getBoard(originCrs, 15, null);
        if (allOrigin == null || allOrigin.trainServices == null) return;

        List<String> hub1s = getPrioritizedHubs(allOrigin.trainServices, originCrs);
        int h1Count = 0;

        for (String hub1 : hub1s) {
            if (hub1.equals(destCrs) || hub1.equals(originCrs)) continue;
            if (h1Count++ >= 5) break;

            StationDepartureResponse allHub1 = getBoard(hub1, 20, null);
            if (allHub1 == null || allHub1.trainServices == null) continue;

            List<String> hub2s = getPrioritizedHubs(allHub1.trainServices, hub1);
            int h2Count = 0;

            for (String hub2 : hub2s) {
                if (hub2.equals(destCrs) || hub2.equals(originCrs) || hub2.equals(hub1)) continue;
                if (h2Count++ >= 10) break;

                StationDepartureResponse hub2ToDest = getBoard(hub2, 30, destCrs);
                if (hub2ToDest == null || hub2ToDest.trainServices == null || hub2ToDest.trainServices.isEmpty()) continue;

                for (StationDepartureResponse.TrainService t1 : allOrigin.trainServices) {
                    String t1ArrTime = getPreciseArrivalTime(t1.serviceID, hub1);
                    if (t1ArrTime == null) continue;

                    for (StationDepartureResponse.TrainService t2 : allHub1.trainServices) {
                        if (hasValidTransferTime(t1ArrTime, t2.std, 5, 120)) {
                            String t2ArrTime = getPreciseArrivalTime(t2.serviceID, hub2);
                            if (t2ArrTime == null) continue;

                            for (StationDepartureResponse.TrainService t3 : hub2ToDest.trainServices) {
                                if (hasValidTransferTime(t2ArrTime, t3.std, 5, 120)) {

                                    String t3ArrTime = getPreciseArrivalTime(t3.serviceID, destCrs);
                                    if (t3ArrTime == null) t3ArrTime = "00:00";

                                    String t1Dest = (t1.destination != null && !t1.destination.isEmpty()) ? t1.destination.get(0).locationName : hub1;
                                    String t2Dest = (t2.destination != null && !t2.destination.isEmpty()) ? t2.destination.get(0).locationName : hub2;
                                    String t3Dest = (t3.destination != null && !t3.destination.isEmpty()) ? t3.destination.get(0).locationName : destCrs;

                                    List<JourneyLeg.Stop> stops1 = extractIntermediateStops(t1.serviceID, originCrs, hub1);
                                    List<JourneyLeg.Stop> stops2 = extractIntermediateStops(t2.serviceID, hub1, hub2);
                                    List<JourneyLeg.Stop> stops3 = extractIntermediateStops(t3.serviceID, hub2, destCrs);

                                    List<JourneyLeg> legs = new ArrayList<>();
                                    legs.add(new JourneyLeg(originCrs, getStationName(originCrs), hub1, getStationName(hub1), t1Dest, t1.std, t1ArrTime, t1.operatorName, t1.serviceID, "TRAIN", stops1, getLiveLocationStatus(t1.serviceID), t1.etd));
                                    legs.add(new JourneyLeg(hub1, getStationName(hub1), hub2, getStationName(hub2), t2Dest, t2.std, t2ArrTime, t2.operatorName, t2.serviceID, "TRAIN", stops2, getLiveLocationStatus(t2.serviceID), t2.etd));
                                    legs.add(new JourneyLeg(hub2, getStationName(hub2), destCrs, getStationName(destCrs), t3Dest, t3.std, t3ArrTime, t3.operatorName, t3.serviceID, "TRAIN", stops3, getLiveLocationStatus(t3.serviceID), t3.etd));

                                    results.add(new JourneyOption(legs));

                                    graphManager.addConnection(originCrs, hub1);
                                    graphManager.addConnection(hub1, hub2);
                                    graphManager.addConnection(hub2, destCrs);
                                    if (results.size() >= 10) return;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void findCrossCity(String originCrs, String destCrs, List<JourneyOption> results) {
        StationDepartureResponse allOrigin = getBoard(originCrs, 20, null);
        if (allOrigin == null || allOrigin.trainServices == null) return;

        Set<String> londonTerminalsReached = new HashSet<>();

        for (StationDepartureResponse.TrainService t1 : allOrigin.trainServices) {
            String destCode = (t1.destination != null && !t1.destination.isEmpty()) ? t1.destination.get(0).crs : null;
            if (destCode != null && !CityTransferManager.getTransfersFrom(destCode).isEmpty()) {
                londonTerminalsReached.add(destCode);
            }
        }

        for (String terminal1 : londonTerminalsReached) {
            Map<String, Integer> possibleTubeTransfers = CityTransferManager.getTransfersFrom(terminal1);

            for (Map.Entry<String, Integer> transfer : possibleTubeTransfers.entrySet()) {
                String terminal2 = transfer.getKey();
                int tubeTimeMins = transfer.getValue();

                StationDepartureResponse term2Board = getBoard(terminal2, 30, destCrs);
                if (term2Board == null || term2Board.trainServices == null) continue;

                for (StationDepartureResponse.TrainService t1 : allOrigin.trainServices) {
                    String t1ArrTime = getPreciseArrivalTime(t1.serviceID, terminal1);
                    if (t1ArrTime == null) continue;

                    String arrivedAtTerm2Time = addMinutesToTime(t1ArrTime, tubeTimeMins);

                    for (StationDepartureResponse.TrainService t2 : term2Board.trainServices) {
                        if (hasValidTransferTime(arrivedAtTerm2Time, t2.std, 10, 120)) {

                            String t2ArrTime = getPreciseArrivalTime(t2.serviceID, destCrs);
                            if (t2ArrTime == null) t2ArrTime = "00:00";

                            String t1TrainDest = (t1.destination != null && !t1.destination.isEmpty()) ? t1.destination.get(0).locationName : terminal1;
                            String t2TrainDest = (t2.destination != null && !t2.destination.isEmpty()) ? t2.destination.get(0).locationName : destCrs;

                            List<JourneyLeg.Stop> stops1 = extractIntermediateStops(t1.serviceID, originCrs, terminal1);
                            List<JourneyLeg.Stop> stops2 = extractIntermediateStops(t2.serviceID, terminal2, destCrs);

                            List<JourneyLeg> legs = new ArrayList<>();
                            legs.add(new JourneyLeg(originCrs, getStationName(originCrs), terminal1, getStationName(terminal1), t1TrainDest, t1.std, t1ArrTime, t1.operatorName, t1.serviceID, "TRAIN", stops1, getLiveLocationStatus(t1.serviceID), t1.etd));
                            legs.add(new JourneyLeg(terminal1, getStationName(terminal1), terminal2, getStationName(terminal2), getStationName(terminal2), t1ArrTime, arrivedAtTerm2Time, "London Underground", "TUBE", "TUBE/WALK", new ArrayList<>(), null, null));
                            legs.add(new JourneyLeg(terminal2, getStationName(terminal2), destCrs, getStationName(destCrs), t2TrainDest, t2.std, t2ArrTime, t2.operatorName, t2.serviceID, "TRAIN", stops2, getLiveLocationStatus(t2.serviceID), t2.etd));

                            results.add(new JourneyOption(legs));
                            if (results.size() >= 10) return;
                            break;
                        }
                    }
                }
            }
        }
    }

    private StationDepartureResponse getBoard(String crs, int rows, String filter) {
        String cacheKey = crs + "_" + rows + "_" + (filter != null ? filter : "ALL");
        if (boardCache.containsKey(cacheKey)) return boardCache.get(cacheKey);

        try {
            Response<StationDepartureResponse> res = api.getStationDepartures(ldbwsApiKey, crs, rows, 120, filter).execute();
            if (res.isSuccessful() && res.body() != null) {
                boardCache.put(cacheKey, res.body());
                return res.body();
            }
        } catch (Exception e) {
            Log.e(TAG, "Cache miss/fail for board: " + cacheKey);
        }
        return null;
    }

    private String getPreciseArrivalTime(String serviceId, String targetCrs) {
        ServiceDetailsResponse details = null;

        if (detailCache.containsKey(serviceId)) {
            details = detailCache.get(serviceId);
        } else {
            try {
                Response<ServiceDetailsResponse> res = api.getServiceDetails(serviceDetailsApiKey, serviceId).execute();
                if (res.isSuccessful() && res.body() != null) {
                    details = res.body();
                    detailCache.put(serviceId, details);
                }
            } catch (Exception e) { Log.e(TAG, "Failed details for " + serviceId); }
        }

        if (details != null) {
            if (details.subsequentCallingPoints != null && !details.subsequentCallingPoints.isEmpty()) {
                for (ServiceDetailsResponse.TimelinePoint cp : details.subsequentCallingPoints.get(0).callingPoint) {
                    String cpCrs = cp.crs != null ? cp.crs : StationHelper.getCrsFromName(cp.locationName);
                    if (targetCrs.equalsIgnoreCase(cpCrs)) {
                        return (cp.scheduledTime != null && !cp.scheduledTime.isEmpty()) ? cp.scheduledTime : cp.estimatedTime;
                    }
                }
            }
            String destCrs = details.crs != null ? details.crs : StationHelper.getCrsFromName(details.locationName);
            if (targetCrs.equalsIgnoreCase(destCrs)) {
                return (details.sta != null && !details.sta.isEmpty()) ? details.sta : details.eta;
            }
        }
        return null;
    }

    private List<JourneyLeg.Stop> extractIntermediateStops(String serviceId, String fromCrs, String toCrs) {
        List<JourneyLeg.Stop> stops = new ArrayList<>();
        ServiceDetailsResponse details = detailCache.get(serviceId);
        if (details == null) return stops;

        List<ServiceDetailsResponse.TimelinePoint> allPoints = new ArrayList<>();

        if (details.previousCallingPoints != null && !details.previousCallingPoints.isEmpty()) {
            allPoints.addAll(details.previousCallingPoints.get(0).callingPoint);
        }

        ServiceDetailsResponse.TimelinePoint currentStation = new ServiceDetailsResponse.TimelinePoint();
        currentStation.locationName = details.locationName;
        currentStation.crs = details.crs != null ? details.crs : StationHelper.getCrsFromName(details.locationName);
        currentStation.scheduledTime = details.std != null ? details.std : details.sta;
        allPoints.add(currentStation);

        if (details.subsequentCallingPoints != null && !details.subsequentCallingPoints.isEmpty()) {
            allPoints.addAll(details.subsequentCallingPoints.get(0).callingPoint);
        }

        boolean isRecording = false;
        for (ServiceDetailsResponse.TimelinePoint cp : allPoints) {
            String cpCrs = cp.crs != null ? cp.crs : StationHelper.getCrsFromName(cp.locationName);
            if (cpCrs == null) continue;

            if (cpCrs.equalsIgnoreCase(toCrs)) {
                break;
            }

            if (isRecording) {
                String time = cp.scheduledTime;
                if (time == null || time.isEmpty()) time = cp.estimatedTime;
                if (time == null || time.equalsIgnoreCase("On time")) time = "00:00";
                stops.add(new JourneyLeg.Stop(cp.locationName, time));
            }

            if (cpCrs.equalsIgnoreCase(fromCrs)) {
                isRecording = true;
            }
        }
        return stops;
    }

    // =================================================================================
    // NEW: LIVE LOCATION HELPER
    // =================================================================================
    private String getLiveLocationStatus(String serviceId) {
        // Because getPreciseArrivalTime runs first, the data is guaranteed to be in the cache
        ServiceDetailsResponse details = detailCache.get(serviceId);
        if (details != null) {
            return LiveLocationEngine.getLiveLocation(details);
        }
        return null;
    }

    private List<String> getPrioritizedHubs(List<StationDepartureResponse.TrainService> services, String origin) {
        List<String> prioritizedHubs = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String knownHub : graphManager.getConnections(origin)) {
            if (seen.add(knownHub)) prioritizedHubs.add(knownHub);
        }

        if (services != null) {
            for (StationDepartureResponse.TrainService service : services) {
                if (service.destination != null && !service.destination.isEmpty()) {
                    String destCrsCode = service.destination.get(0).crs;
                    if (destCrsCode != null && seen.add(destCrsCode)) {
                        prioritizedHubs.add(destCrsCode);
                    }
                }
            }
        }
        return prioritizedHubs;
    }

    private boolean hasValidTransferTime(String arrival, String departure, int minMins, int maxMins) {
        if (arrival == null || departure == null || !arrival.contains(":") || !departure.contains(":")) return false;
        try {
            String[] arrParts = arrival.split(":");
            String[] depParts = departure.split(":");

            int arrMins = Integer.parseInt(arrParts[0].trim()) * 60 + Integer.parseInt(arrParts[1].trim());
            int depMins = Integer.parseInt(depParts[0].trim()) * 60 + Integer.parseInt(depParts[1].trim());

            int diff = depMins - arrMins;
            if (diff < 0) diff += 24 * 60;

            return diff >= minMins && diff <= maxMins;
        } catch (Exception e) {
            return false;
        }
    }

    private String addMinutesToTime(String time, int minsToAdd) {
        if (time == null || !time.contains(":")) return "00:00";
        try {
            String[] parts = time.split(":");
            int mins = Integer.parseInt(parts[0].trim()) * 60 + Integer.parseInt(parts[1].trim()) + minsToAdd;
            int h = (mins / 60) % 24;
            int m = mins % 60;
            return String.format("%02d:%02d", h, m);
        } catch (Exception e) { return time; }
    }

    public void shutdown() {
        executor.shutdown();
    }

    private String getStationName(String crs) {
        if (crs == null) return "";
        String upperCrs = crs.toUpperCase();

        if (crsToNameMap == null) {
            crsToNameMap = new HashMap<>();
            List<String> allNames = StationHelper.getStationNames();
            if (allNames != null) {
                for (String name : allNames) {
                    String code = StationHelper.getCrsFromName(name);
                    if (code != null) {
                        crsToNameMap.putIfAbsent(code.toUpperCase(), name);
                    }
                }
            }
        }
        return crsToNameMap.getOrDefault(upperCrs, upperCrs);
    }
}