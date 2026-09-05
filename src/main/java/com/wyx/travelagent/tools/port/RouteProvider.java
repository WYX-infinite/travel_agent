package com.wyx.travelagent.tools.port;

import com.wyx.travelagent.tools.model.PlaceCandidate;
import com.wyx.travelagent.tools.model.ToolResult;
import com.wyx.travelagent.tools.model.TravelMode;
import com.wyx.travelagent.tools.model.TravelSegment;

public interface RouteProvider {
    ToolResult<TravelSegment> route(
            String city,
            PlaceCandidate from,
            PlaceCandidate to,
            TravelMode mode
    );
}
