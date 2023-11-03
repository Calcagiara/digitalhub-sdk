package it.smartcommunitylabdhub.core.models.entities.run.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.smartcommunitylabdhub.core.models.base.specs.BaseSpec;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RunBaseSpec extends BaseSpec {

    @NotEmpty
    private String task;

    @NotEmpty
    @JsonProperty("task_id")
    private String taskId;

    private Map<String, Object> inputs = new HashMap<>();

    private Map<String, Object> outputs = new HashMap<>();

    private Map<String, Object> parameters = new HashMap<>();

    @JsonProperty("local_execution")
    private boolean localExecution = false;

    @Override
    protected <S extends T, T extends BaseSpec> void configure(S concreteSpec) {
    }
}