/*
 * SPDX-File Copyright: © 2025.  Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.fluxtion.dataflow.reference.groupby;

import com.fluxtion.dataflow.builder.DataFlowBuilder;
import com.fluxtion.dataflow.runtime.DataFlow;
import com.fluxtion.dataflow.runtime.flowfunction.groupby.GroupBy;
import com.fluxtion.dataflow.runtime.flowfunction.helpers.Aggregates;

public class GroupBySample {
    public record ResetList() { }

    public static void main(String[] args) {
        var resetSignal = DataFlowBuilder.subscribe(ResetList.class).console("\n--- RESET ---");

        DataFlow processor = DataFlowBuilder.subscribe(Integer.class)
                .groupBy(i -> i % 2 == 0 ? "evens" : "odds", Aggregates.countFactory())
                .resetTrigger(resetSignal)
                .map(GroupBy::toMap)
                .console("ODD/EVEN map:{}")
                .build();

        processor.onEvent(1);
        processor.onEvent(2);

        processor.onEvent(new ResetList());
        processor.onEvent(5);
        processor.onEvent(7);

        processor.onEvent(new ResetList());
        processor.onEvent(2);
    }
}