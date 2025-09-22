/*
 * SPDX-File Copyright: © 2025.  Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */

//REPOS mavencentral,repsy-fluxtion=https://repo.repsy.io/mvn/fluxtion/fluxtion-public
//DEPS com.fluxtion.dataflow:dataflow-builder:1.0.0
//DEPS org.projectlombok:lombok:1.18.36
//COMPILE_OPTIONS -proc:full
//SOURCES userfunc/Helpers.java
//SOURCES userfunc/AlarmDeltaFilter.java
//SOURCES userfunc/LocationCode.java
//SOURCES userfunc/MachineProfileEvent.java
//SOURCES userfunc/MachineReadingEvent.java
//SOURCES userfunc/MachineState.java
//SOURCES userfunc/SupportContactEvent.java
package com.fluxtion.dataflow.examples.frontpage.multijoin;

import com.fluxtion.dataflow.examples.frontpage.multijoin.userfunc.*;
import com.telamin.fluxtion.builder.DataFlowBuilder;
import com.telamin.fluxtion.runtime.DataFlow;
import com.telamin.fluxtion.runtime.flowfunction.aggregate.function.primitive.DoubleAverageFlowFunction;
import com.telamin.fluxtion.runtime.flowfunction.groupby.GroupBy;
import com.telamin.fluxtion.runtime.time.FixedRateTrigger;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


/**
 * Build a rela time event processor that consumes event streams to monitor and alarm machines in a data centre. </br>
 * <br>
 * Monitors each machine for an average or current temperature breach in a sliding window of 4 seconds with a bucket size of 1 second
 * readings are produced randomly every mircosecond the aggregation handles all combining values within a window and dropping
 * values that have expired.<br>
 * <br>
 * Alarm status is published on any change to the alarm state, i.e. new alarms or cleared old alarms<br>
 * <br>
 * Each machine can have its own temperature alarm profile updated by event MachineProfile<br>
 * <br>
 * Notifies a support contact in the correct location where the breach has occurred. The contact lookup is built up
 * through events:
 * <ul>
 *     <li>MachineLocation = Machine id -> location</li>
 *     <li>SupportContact = location -> contact details</li>
 * </ul>
 * <br>
 * <br>
 * <p>
 * A sink is available for the host application to consume the alarm output, in this case a pretty print consumer<br>
 * <br>
 * Running the app should produce an output similar to below:
 *
 * <pre>
 *  Application started - wait four seconds for first machine readings
 *
 *  ALARM UPDATE 14:31:30.785
 *  New alarms: ['server_GOOG@USA_EAST_1',  temp:'49.16', avgTemp:'52.05' SupportContact[name=Jean, locationCode=USA_EAST_1, contactDetails=jean@fluxtion.com], 'server_TKM@USA_EAST_2',  temp:'86.47', avgTemp:'52.37' SupportContact[name=Tandy, locationCode=USA_EAST_2, contactDetails=tandy@fluxtion.com], 'server_AMZN@USA_EAST_1',  temp:'71.48', avgTemp:'54.25' SupportContact[name=Jean, locationCode=USA_EAST_1, contactDetails=jean@fluxtion.com], 'server_MSFT@USA_EAST_2',  temp:'31.70', avgTemp:'52.53' SupportContact[name=Tandy, locationCode=USA_EAST_2, contactDetails=tandy@fluxtion.com]]
 *  Alarms to clear[]
 *  Current alarms[server_GOOG, server_TKM, server_AMZN, server_MSFT]
 *  ------------------------------------
 *
 *  ALARM UPDATE 14:31:32.778
 *  New alarms: []
 *  Alarms to clear[server_TKM]
 *  Current alarms[server_GOOG, server_AMZN, server_MSFT]
 *  ------------------------------------
 *
 *  ALARM UPDATE 14:31:33.768
 *  New alarms: ['server_TKM@USA_EAST_2',  temp:'98.33', avgTemp:'49.95' SupportContact[name=Tandy, locationCode=USA_EAST_2, contactDetails=tandy@fluxtion.com]]
 *  Alarms to clear[]
 *  Current alarms[server_GOOG, server_TKM, server_AMZN, server_MSFT]
 *  ------------------------------------
 *
 *  ALARM UPDATE 14:31:37.777
 *  New alarms: []
 *  Alarms to clear[server_AMZN]
 *  Current alarms[server_GOOG, server_TKM, server_MSFT]
 *  ------------------------------------
 * </pre>
 **/

public class MultiFeedJoinExample {

    public static void main(String[] args) {
        //build realtime machine monitor
        var currentMachineTemp = DataFlowBuilder.groupBy(
                MachineReadingEvent::id, MachineReadingEvent::temp);

        //create a stream of averaged machine sliding temps,
        //with a 4-second window and 1 second buckets grouped by machine id
        var avgMachineTemp = DataFlowBuilder.subscribe(MachineReadingEvent.class)
                .groupBySliding(
                        MachineReadingEvent::id,
                        MachineReadingEvent::temp,
                        DoubleAverageFlowFunction::new,
                        1000,
                        4);

        //join machine profiles with contacts and then with readings.
        //Publish alarms with stateful user function
        var tempMonitor = DataFlowBuilder.groupBy(MachineProfileEvent::id)
                .mapValues(MachineState::new)
                .mapBi(
                        DataFlowBuilder.groupBy(SupportContactEvent::locationCode),
                        Helpers::addContact)
                .innerJoin(currentMachineTemp, MachineState::setCurrentTemperature)
                .innerJoin(avgMachineTemp, MachineState::setAvgTemperature)
                .publishTriggerOverride(FixedRateTrigger.atMillis(1_000))
                .filterValues(MachineState::outsideOperatingTemp)
                .map(GroupBy::toMap)
                .map(new AlarmDeltaFilter()::updateActiveAlarms)
                .filter(AlarmDeltaFilter::isChanged)
                .sink("alarmPublisher")
                .build();

        runSimulation(tempMonitor);
    }

    private static void runSimulation(DataFlow tempMonitor) {
        tempMonitor.addSink("alarmPublisher", Helpers::prettyPrintAlarms);

        final String[] MACHINE_IDS = new String[]{"server_GOOG", "server_AMZN", "server_MSFT", "server_TKM"};

        //set up machine locations
        tempMonitor.onEvent(new MachineProfileEvent("server_GOOG", LocationCode.USA_EAST_1, 70, 48));
        tempMonitor.onEvent(new MachineProfileEvent("server_AMZN", LocationCode.USA_EAST_1, 99.999, 65));
        tempMonitor.onEvent(new MachineProfileEvent("server_MSFT", LocationCode.USA_EAST_2, 92, 49.99));
        tempMonitor.onEvent(new MachineProfileEvent("server_TKM", LocationCode.USA_EAST_2, 102, 50.0001));

        //set up support contacts
        tempMonitor.onEvent(new SupportContactEvent("Jean", LocationCode.USA_EAST_1, "jean@fluxtion.com"));
        tempMonitor.onEvent(new SupportContactEvent("Tandy", LocationCode.USA_EAST_2, "tandy@fluxtion.com"));

        Random random = new Random();

        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
                    String machineId = MACHINE_IDS[random.nextInt(MACHINE_IDS.length)];
                    double temperatureReading = random.nextDouble() * 100;
                    tempMonitor.onEvent(new MachineReadingEvent(machineId, temperatureReading));
                },
                10_000, 10, TimeUnit.MICROSECONDS);

        System.out.println("Application started - wait four seconds for first machine readings\n");
    }
}
