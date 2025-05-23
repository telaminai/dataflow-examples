/*
 * SPDX-File Copyright: © 2025.  Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.fluxtion.dataflow.reference.functional;

import com.fluxtion.dataflow.builder.DataFlowBuilder;
import com.fluxtion.dataflow.runtime.DataFlow;
import com.fluxtion.dataflow.runtime.flowfunction.helpers.Mappers;

public class GetFlowNodeByIdExample {
    public static void main(String[] args) throws NoSuchFieldException {
        DataFlow processor = DataFlowBuilder.subscribe(String.class)
                .filter(s -> s.equalsIgnoreCase("monday"))
                //ID START - this makes the wrapped value accessible via the id
                .mapToInt(Mappers.count()).id("MondayChecker")
                //ID END
                .console("Monday is triggered")
                .build();

        processor.onEvent("Monday");
        processor.onEvent("Tuesday");
        processor.onEvent("Wednesday");

        //ACCESS THE WRAPPED VALUE BY ITS ID
        Integer mondayCheckerCount = processor.getStreamed("MondayChecker");
        System.out.println("Monday count:" + mondayCheckerCount + "\n");

        //ACCESS THE WRAPPED VALUE BY ITS ID
        processor.onEvent("Monday");
        mondayCheckerCount = processor.getStreamed("MondayChecker");
        System.out.println("Monday count:" + mondayCheckerCount);
    }
}
