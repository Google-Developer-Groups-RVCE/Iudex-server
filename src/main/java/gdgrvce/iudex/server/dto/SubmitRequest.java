package gdgrvce.iudex.server.dto;

import java.util.List;

/** The outputs the user's program produced, one per testcase, in order. */
public record SubmitRequest(List<String> outputs) {
}
