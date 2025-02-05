//REPOS repsy-fluxtion=https://repo.repsy.io/mvn/gregv12/default
//DEPS com.fluxtion.dataflow:dataflow-builder:1.14
//COMPILE_OPTIONS -proc:full
package com.fluxtion.dataflow.examples.frontpage.triggering;

import com.fluxtion.dataflow.builder.DataFlowBuilder;
import com.fluxtion.dataflow.runtime.DataFlow;
import com.fluxtion.dataflow.runtime.flowfunction.helpers.Aggregates;

public class TriggerExample {
    public static void main(String[] args) {
        DataFlow sumDataFlow = DataFlowBuilder.subscribe(Integer.class)
                .aggregate(Aggregates.intSumFactory())
                .resetTrigger(DataFlowBuilder.subscribeToSignal("resetTrigger"))
                .filter(i -> i != 0)
                .publishTriggerOverride(DataFlowBuilder.subscribeToSignal("publishSumTrigger"))
                .console("Current sun:{}")
                .build();

        sumDataFlow.onEvent(10);
        sumDataFlow.onEvent(50);
        sumDataFlow.onEvent(32);
        //publish
        sumDataFlow.publishSignal("publishSumTrigger");

        //reset sum
        sumDataFlow.publishSignal("resetTrigger");

        //new sum
        sumDataFlow.onEvent(8);
        sumDataFlow.onEvent(17);
        //publish
        sumDataFlow.publishSignal("publishSumTrigger");
    }
}
