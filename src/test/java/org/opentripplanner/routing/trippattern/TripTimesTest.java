package org.opentripplanner.routing.trippattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.junit.Test;
import org.opentripplanner.model.FeedScopedId;
import org.opentripplanner.model.Route;
import org.opentripplanner.model.Stop;
import org.opentripplanner.model.StopTime;
import org.opentripplanner.model.Trip;
import org.opentripplanner.gtfs.BikeAccess;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graph.SimpleConcreteVertex;
import org.opentripplanner.routing.graph.Vertex;

public class TripTimesTest {
    private static final FeedScopedId tripId = new FeedScopedId("agency", "testtrip");

    private static final FeedScopedId stop_a = new FeedScopedId("agency", "A"); // 0
    private static final FeedScopedId stop_b = new FeedScopedId("agency", "B"); // 1
    private static final FeedScopedId stop_c = new FeedScopedId("agency", "C"); // 2
    private static final FeedScopedId stop_d = new FeedScopedId("agency", "D"); // 3
    private static final FeedScopedId stop_e = new FeedScopedId("agency", "E"); // 4
    private static final FeedScopedId stop_f = new FeedScopedId("agency", "F"); // 5
    private static final FeedScopedId stop_g = new FeedScopedId("agency", "G"); // 6
    private static final FeedScopedId stop_h = new FeedScopedId("agency", "H"); // 7

    private static final FeedScopedId[] stops =
        {stop_a, stop_b, stop_c, stop_d, stop_e, stop_f, stop_g, stop_h};

    private static final TripTimes originalTripTimes;

    static {
        Trip trip = new Trip();
        trip.setId(tripId);

        List<StopTime> stopTimes = new LinkedList<StopTime>();

        for(int i =  0; i < stops.length; ++i) {
            StopTime stopTime = new StopTime();

            Stop stop = new Stop();
            stop.setId(stops[i]);
            stopTime.setStop(stop);
            stopTime.setArrivalTime(i * 60);
            stopTime.setDepartureTime(i * 60);
            stopTime.setStopSequence(i);
            stopTimes.add(stopTime);
        }

        originalTripTimes = new TripTimes(trip, stopTimes, new Deduplicator());
    }

    @Test
    public void testBikesAllowed() {
        Graph graph = new Graph();
        Trip trip = new Trip();
        Route route = new Route();
        trip.setRoute(route);
        List<StopTime> stopTimes = Arrays.asList(new StopTime(), new StopTime());
        TripTimes s = new TripTimes(trip, stopTimes, new Deduplicator());

        RoutingRequest request = new RoutingRequest(TraverseMode.BICYCLE);
        Vertex v = new SimpleConcreteVertex(graph, "", 0.0, 0.0);
        request.setRoutingContext(graph, v, v);
        State s0 = new State(request);

        assertFalse(s.tripAcceptable(s0, 0));

        BikeAccess.setForTrip(trip, BikeAccess.ALLOWED);
        assertTrue(s.tripAcceptable(s0, 0));

        BikeAccess.setForTrip(trip, BikeAccess.NOT_ALLOWED);
        assertFalse(s.tripAcceptable(s0, 0));
    }

    @Test
    public void testStopUpdate() {
        TripTimes updatedTripTimesA = new TripTimes(originalTripTimes);

        updatedTripTimesA.updateArrivalTime(3, 190);
        updatedTripTimesA.updateDepartureTime(3, 190);
        updatedTripTimesA.updateArrivalTime(5, 311);
        updatedTripTimesA.updateDepartureTime(5, 312);

        assertEquals(3 * 60 + 10, updatedTripTimesA.getArrivalTime(3));
        assertEquals(3 * 60 + 10, updatedTripTimesA.getDepartureTime(3));
        assertEquals(5 * 60 + 11, updatedTripTimesA.getArrivalTime(5));
        assertEquals(5 * 60 + 12, updatedTripTimesA.getDepartureTime(5));
    }

    @Test
    public void testPassedUpdate() {
        TripTimes updatedTripTimesA = new TripTimes(originalTripTimes);

        updatedTripTimesA.updateDepartureTime(0, TripTimes.UNAVAILABLE);

        assertEquals(TripTimes.UNAVAILABLE, updatedTripTimesA.getDepartureTime(0));
        assertEquals(60, updatedTripTimesA.getArrivalTime(1));
    }

    @Test
    public void testNonIncreasingUpdate() {
        TripTimes updatedTripTimesA = new TripTimes(originalTripTimes);

        updatedTripTimesA.updateArrivalTime(1, 60);
        updatedTripTimesA.updateDepartureTime(1, 59);

        assertFalse(updatedTripTimesA.timesIncreasing());

        TripTimes updatedTripTimesB = new TripTimes(originalTripTimes);

        updatedTripTimesB.updateDepartureTime(6, 421);
        updatedTripTimesB.updateArrivalTime(7, 420);

        assertFalse(updatedTripTimesB.timesIncreasing());
    }

    @Test
    public void testDelay() {
        TripTimes updatedTripTimesA = new TripTimes(originalTripTimes);
        updatedTripTimesA.updateDepartureDelay(0, 10);
        updatedTripTimesA.updateArrivalDelay(6, 13);

        assertEquals(0 * 60 + 10, updatedTripTimesA.getDepartureTime(0));
        assertEquals(6 * 60 + 13, updatedTripTimesA.getArrivalTime(6));
    }

    @Test
    public void testCancel() {
        TripTimes updatedTripTimesA = new TripTimes(originalTripTimes);
        updatedTripTimesA.cancel();

        for (int i = 0; i < stops.length - 1; i++) {
            assertEquals(originalTripTimes.getDepartureTime(i),
                    updatedTripTimesA.getScheduledDepartureTime(i));
            assertEquals(originalTripTimes.getArrivalTime(i),
                    updatedTripTimesA.getScheduledArrivalTime(i));
            assertTrue(updatedTripTimesA.isCanceledDeparture(i));
            assertTrue(updatedTripTimesA.isCanceledArrival(i));
        }
    }

    @Test
    public void testApply() {
        Trip trip = new Trip();
        trip.setId(tripId);

        List<StopTime> stopTimes = new LinkedList<StopTime>();

        StopTime stopTime0 = new StopTime();
        StopTime stopTime1 = new StopTime();
        StopTime stopTime2 = new StopTime();

        Stop stop0 = new Stop();
        Stop stop1 = new Stop();
        Stop stop2 = new Stop();

        stop0.setId(stops[0]);
        stop1.setId(stops[1]);
        stop2.setId(stops[2]);

        stopTime0.setStop(stop0);
        stopTime0.setDepartureTime(0);
        stopTime0.setStopSequence(0);

        stopTime1.setStop(stop1);
        stopTime1.setArrivalTime(30);
        stopTime1.setDepartureTime(60);
        stopTime1.setStopSequence(1);

        stopTime2.setStop(stop2);
        stopTime2.setArrivalTime(90);
        stopTime2.setStopSequence(2);

        stopTimes.add(stopTime0);
        stopTimes.add(stopTime1);
        stopTimes.add(stopTime2);

        TripTimes differingTripTimes = new TripTimes(trip, stopTimes, new Deduplicator());

        TripTimes updatedTripTimesA = new TripTimes(differingTripTimes);

        updatedTripTimesA.updateArrivalTime(1, 89);
        updatedTripTimesA.updateDepartureTime(1, 98);

        assertFalse(updatedTripTimesA.timesIncreasing());
    }

    @Test
    public void testGetRunningTime() {
        for (int i = 0; i < stops.length - 1; i++) {
            assertEquals(60, originalTripTimes.getRunningTime(i));
        }

        TripTimes updatedTripTimes = new TripTimes(originalTripTimes);

        for (int i = 0; i < stops.length - 1; i++) {
            updatedTripTimes.updateDepartureDelay(i, i);
        }

        for (int i = 0; i < stops.length - 1; i++) {
            assertEquals(60 - i, updatedTripTimes.getRunningTime(i));
        }
    }

    @Test
    public void testGetDwellTime() {
        for (int i = 0; i < stops.length; i++) {
            assertEquals(0, originalTripTimes.getDwellTime(i));
        }

        TripTimes updatedTripTimes = new TripTimes(originalTripTimes);

        for (int i = 0; i < stops.length; i++) {
            updatedTripTimes.updateArrivalDelay(i, -i);
        }

        for (int i = 0; i < stops.length; i++) {
            assertEquals(i, updatedTripTimes.getDwellTime(i));
        }
    }
}
