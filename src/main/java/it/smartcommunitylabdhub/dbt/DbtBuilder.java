package it.smartcommunitylabdhub.dbt;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import it.smartcommunitylabdhub.core.annotations.BuilderComponent;
import it.smartcommunitylabdhub.core.components.infrastructure.builders.BaseBuilder;
import it.smartcommunitylabdhub.core.components.infrastructure.factories.builders.Builder;
import it.smartcommunitylabdhub.core.models.accessors.utils.RunUtils;
import it.smartcommunitylabdhub.core.models.builders.entities.FunctionEntityBuilder;
import it.smartcommunitylabdhub.core.models.dtos.FunctionDTO;
import it.smartcommunitylabdhub.core.models.dtos.RunDTO;
import it.smartcommunitylabdhub.core.models.dtos.TaskDTO;
import it.smartcommunitylabdhub.core.models.dtos.custom.ExecutionDTO;
import it.smartcommunitylabdhub.core.repositories.TaskRepository;
import it.smartcommunitylabdhub.core.services.interfaces.FunctionService;
import it.smartcommunitylabdhub.core.services.interfaces.TaskService;
import it.smartcommunitylabdhub.core.utils.MapUtils;

@BuilderComponent(runtime = "dbt", task = "job")
public class DbtBuilder extends BaseBuilder implements Builder {

	@Autowired
	TaskRepository taskRepository;

	@Autowired
	TaskService taskService;

	@Autowired
	FunctionService functionService;

	@Autowired
	FunctionEntityBuilder functionEntityBuilder;

	@Override
	public RunDTO build(FunctionDTO functionDTO, TaskDTO taskDTO, ExecutionDTO executionDTO) {
		// 1. Merge Task spec with function spec
		Map<String, Object> mergedSpec =
				MapUtils.mergeMaps(functionDTO.getSpec(), taskDTO.getSpec(),
						(oldValue, newValue) -> newValue);

		// 2. produce a run object and store it
		RunDTO runDTO = RunDTO.builder()
				.kind("run")
				.taskId(taskDTO.getId())
				.project(taskDTO.getProject())
				.task(RunUtils.buildRunString(
						functionDTO,
						taskDTO))
				.spec(mergedSpec)
				.build();

		// 3. Merge the rest of the spec from executionDTO and the current RunDTO
		return mergeSpec(executionDTO, runDTO);

	}



}
