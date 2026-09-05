package com.wyx.travelagent.tools.port;

import com.wyx.travelagent.tools.model.PlaceCandidate;
import com.wyx.travelagent.tools.model.ToolResult;

import java.util.List;

public interface PlaceProvider {
    ToolResult<List<PlaceCandidate>> search(String city, String keyword, int limit);

    ToolResult<PlaceCandidate> geocode(String city, String address);
}
