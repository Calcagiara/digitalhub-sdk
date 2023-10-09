"""
Run module.
"""
from __future__ import annotations

import typing
from collections import namedtuple

from sdk.context.builder import get_context
from sdk.entities.artifacts.crud import get_artifact_from_key
from sdk.entities.base.entity import Entity
from sdk.entities.base.status import State
from sdk.entities.builders.kinds import build_kind
from sdk.entities.builders.metadata import build_metadata
from sdk.entities.builders.spec import build_spec
from sdk.entities.builders.status import build_status
from sdk.entities.dataitems.crud import get_dataitem_from_key
from sdk.runtimes.builder import build_runtime
from sdk.utils.api import api_base_create, api_base_read, api_base_update, api_ctx_read
from sdk.utils.commons import FUNC, RUNS, TASK
from sdk.utils.exceptions import EntityError
from sdk.utils.generic_utils import build_uuid

if typing.TYPE_CHECKING:
    from sdk.context.context import Context
    from sdk.entities.artifacts.entity import Artifact
    from sdk.entities.dataitems.entity import Dataitem
    from sdk.entities.runs.metadata import RunMetadata
    from sdk.entities.runs.spec.objects.base import RunSpec
    from sdk.entities.runs.status import RunStatus
    from sdk.runtimes.objects.base import Runtime


TaskString = namedtuple(
    "TaskString",
    ["function_kind", "task_kind", "function_name", "function_id", "task_id"],
)


class Run(Entity):
    """
    A class representing a run.
    """

    def __init__(
        self,
        uuid: str,
        kind: str,
        metadata: RunMetadata,
        spec: RunSpec,
        status: RunStatus,
        local: bool = False,
    ) -> None:
        """
        Initialize the Run instance.

        Parameters
        ----------
        uuid : str
            UUID.
        kind : str
            The type of the run.
        metadata : RunMetadata
            Metadata of the object.
        spec : RunSpec
            Specification of the object.
        status : RunStatus
            State of the object.
        local: bool
            If True, export locally.
        """
        super().__init__()

        self.id = uuid
        self.kind = kind
        self.metadata = metadata
        self.spec = spec
        self.status = status

        # Private attributes
        self._local = local

    #############################
    #  Save / Export
    #############################

    def save(self, uuid: str | None = None) -> dict:
        """
        Save run into backend.

        Parameters
        ----------
        uuid : str
            Ignore this parameter.

        Returns
        -------
        dict
            Mapping representation of Run from backend.
        """
        if self._local:
            raise EntityError("Use .export() for local execution.")

        obj = self.to_dict(include_all_non_private=True)

        # TODO: Remove this when backend is fixed
        obj["project"] = self.metadata.project
        obj["task_id"] = self.spec.task_id
        obj["task"] = self.spec.task
        obj["local_execution"] = self.spec.local_execution

        if uuid is None:
            api = api_base_create(RUNS)
            response = self._context().create_object(obj, api)
            id_ = response.get("id")
            if id_ is not None:
                self.id = id_
            return response

        self.id = uuid
        api = api_base_update(RUNS, uuid)
        return self._context().update_object(obj, api)

    def export(self, filename: str | None = None) -> None:
        """
        Export object as a YAML file.

        Parameters
        ----------
        filename : str
            Name of the export YAML file. If not specified, the default value is used.

        Returns
        -------
        None
        """
        obj = self.to_dict()
        filename = (
            filename
            if filename is not None
            else f"run_{self.metadata.project}_{self.spec.task_id}_{self.id}.yaml"
        )
        self._export_object(filename, obj)

    #############################
    #  Run Methods
    #############################

    def build(self, local: bool = True):
        """
        Build run.

        Parameters
        ----------
        local : bool
            If True, build locally, otherwise build on backend.

        Returns
        -------
        None
        """
        function = self._get_function()
        task = self._get_task()
        runtime = self._get_runtime()
        new_spec = runtime.build(function, task, self.to_dict())
        # inserire validazione per task string
        self.spec = build_spec(RUNS, self.kind, ignore_validation=True, **new_spec)
        self._set_status({"state": State.PENDING.value})
        self.save(self.id)

    def run(self, local: bool = True) -> Run:
        """
        Run run.

        Parameters
        ----------
        local : bool
            If True, run locally, otherwise run from backend.

        Returns
        -------
        Run
            Run object.
        """
        runtime = self._get_runtime()
        try:
            # inserire await, wrappare runtime.run in un async
            status = runtime.run(self.to_dict(include_all_non_private=True))
        except Exception as e:
            status = {"state": State.ERROR.value, "message": str(e)}
        self._set_status(status)
        self.save(self.id)
        return self

    def refresh(self) -> dict:
        """
        Get run from backend.

        Returns
        -------
        dict
            Mapping representation of Run from backend.
        """
        if self._local:
            raise EntityError("Cannot refresh local run.")

        api = api_base_read(RUNS, self.id)
        return self._context().read_object(api)

    def stop(self) -> dict:
        """
        Not implemented yet.
        """
        raise NotImplementedError

    def logs(self) -> dict:
        """
        Get run's logs from backend.

        Returns
        -------
        dict
            Logs from backend.
        """
        api = api_ctx_read(
            self.metadata.project,
        )
        return self._context().read_object(api)

    def _set_status(self, status: dict) -> None:
        """
        Set run status.

        Parameters
        ----------
        status : dict
            Status to set.

        Returns
        -------
        None

        Raises
        ------
        EntityError
            If status is not a dictionary or a Status object.
        """
        if not isinstance(status, dict):
            raise EntityError("Status must be a dictionary.")
        self.status = build_status(RUNS, **status)

    #############################
    #  Artifacts and Dataitems
    #############################

    def get_artifacts(self, output_key: str | None = None) -> Artifact | list[Artifact]:
        """
        Get artifact(s) from backend produced by the run through its key.

        Parameters
        ----------
        output_key : str, optional
            Key of the artifact to get. If not provided, returns all artifacts.

        Returns
        -------
        Artifact | list[Artifact]
            Artifact(s) from backend.
        """
        resp = self.refresh()
        result = resp.get("status", {}).get("artifacts")
        if result is None:
            raise EntityError("Run has no result (maybe try when it finishes).")
        if output_key is not None:
            key = next(
                (r.get("id") for r in result if r.get("key") == output_key), None
            )
            if key is None:
                raise EntityError(f"No artifact found with key '{output_key}'.")
            return get_artifact_from_key(key)
        return [get_artifact_from_key(r.get("id")) for r in result]

    def get_dataitem(self, output_key: str | None = None) -> Dataitem | list[Dataitem]:
        """
        Get dataitem(s) from backend produced by the run through its key.

        Parameters
        ----------
        output_key : str, optional
            Key of the dataitem to get. If not provided, returns all dataitems.

        Returns
        -------
        Dataitem | list[Dataitem]
            Dataitem(s) from backend.
        """
        resp = self.refresh()
        result = resp.get("status", {}).get("dataitems")
        if result is None:
            raise EntityError("Run has no result (maybe try when it finishes).")
        if output_key is not None:
            key = next(
                (r.get("id") for r in result if r.get("key") == output_key), None
            )
            if key is None:
                raise EntityError(f"No dataitem found with key '{output_key}'.")
            return get_dataitem_from_key(key)
        return [get_dataitem_from_key(r.get("id")) for r in result]

    #############################
    #  Functions and Tasks
    #############################

    def _parse_task_string(self) -> TaskString:
        """
        Parse task string.

        Returns
        -------
        None
        """
        kinds, func = self.spec.task.split("://")
        fnc_kind, tsk_kind = kinds.split("+")
        fnc_name, fnc_id = func.split("/")[1].split(":")
        return TaskString(fnc_kind, tsk_kind, fnc_name, fnc_id, self.spec.task_id)

    def _get_function(self) -> dict:
        """
        Get function from backend. Reimplemented to avoid circular imports.

        Returns
        -------
        dict
            Function from backend.
        """
        parsed = self._parse_task_string()
        api = api_ctx_read(
            self.metadata.project, FUNC, parsed.function_name, parsed.function_id
        )
        return self._context().read_object(api)

    def _get_task(self) -> dict:
        """
        Get task from backend. Reimplemented to avoid circular imports.

        Returns
        -------
        dict
            Task from backend.
        """
        parsed = self._parse_task_string()
        api = api_base_read(TASK, parsed.task_id)
        return self._context().read_object(api)

    #############################
    # Runtimes
    #############################

    def _get_runtime(self) -> Runtime:
        """
        Build runtime to build run or execute it.

        Returns
        -------
        Runtime
            Runtime object.

        """
        fnc_kind = self._parse_task_string().function_kind
        return build_runtime(fnc_kind)

    #############################
    #  Context
    #############################

    def _context(self) -> Context:
        """
        Get context.

        Returns
        -------
        Context
            Context.
        """
        return get_context(self.metadata.project)

    #############################
    #  Getters and Setters
    #############################

    @property
    def local(self) -> bool:
        """
        Get local flag.

        Returns
        -------
        bool
            Local flag.
        """
        return self._local

    #############################
    #  Generic Methods
    #############################

    @classmethod
    def from_dict(cls, obj: dict) -> "Run":
        """
        Create object instance from a dictionary.

        Parameters
        ----------
        obj : dict
            Dictionary to create object from.

        Returns
        -------
        Run
            Self instance.
        """
        parsed_dict = cls._parse_dict(obj)
        _obj = cls(**parsed_dict)
        _obj._local = _obj._context().local
        return _obj

    @staticmethod
    def _parse_dict(obj: dict) -> dict:
        """
        Parse dictionary.

        Parameters
        ----------
        obj : dict
            Dictionary to parse.

        Returns
        -------
        dict
            Parsed dictionary.
        """

        # Mandatory fields
        project = obj.get("project")
        task_id = obj.get("task_id")
        task = obj.get("task")
        if project is None or task_id is None or task is None:
            raise EntityError("Project, task or task_id are not specified.")

        # Build UUID, kind, metadata, spec and status
        uuid = obj.get("id")
        uuid = build_uuid(uuid)

        kind = obj.get("kind")
        kind = build_kind(RUNS, kind)

        metadata = obj.get("metadata")
        metadata = metadata if metadata is not None else {"project": project}
        metadata = build_metadata(RUNS, **metadata)

        spec = obj.get("spec")
        spec = spec if spec is not None else {"task": task, "task_id": task_id}
        spec = build_spec(RUNS, kind, **spec)

        status = obj.get("status")
        status = status if status is not None else {}
        status = build_status(RUNS, **status)

        return {
            "uuid": uuid,
            "kind": kind,
            "metadata": metadata,
            "spec": spec,
            "status": status,
            "project": project,
            "task_id": task_id,
            "task": task,
        }


def run_from_parameters(
    project: str,
    task: str,
    task_id: str,
    uuid: str | None = None,
    kind: str | None = None,
    inputs: dict | None = None,
    outputs: list | None = None,
    parameters: dict | None = None,
    local_execution: bool = False,
    local: bool = False,
    **kwargs,
) -> Run:
    """
    Create run.

    Parameters
    ----------
    project : str
        Name of the project.
    task_id : str
        Identifier of the task associated with the run.
    task : str
        Name of the task associated with the run.
    uuid : str
        UUID.
    kind : str
        The type of the run.
    inputs : dict
        Inputs of the run.
    outputs : list
        Outputs of the run.
    parameters : dict
        Parameters of the run.
    local_execution : bool
        Flag to determine if object has local execution.
    local : bool
        Flag to determine if object will be exported to backend.
    embedded : bool
        Flag to determine if object must be embedded in project.
    **kwargs
        Keyword arguments.

    Returns
    -------
    Run
        Run object.
    """
    uuid: str = build_uuid(uuid)
    kind: str = build_kind(RUNS, kind)
    metadata: RunMetadata = build_metadata(
        RUNS,
        project=project,
        name=uuid,
    )
    spec = build_spec(
        RUNS,
        kind,
        task=task,
        task_id=task_id,
        inputs=inputs,
        outputs=outputs,
        parameters=parameters,
        local_execution=local_execution,
        **kwargs,
    )
    status = build_status(RUNS)
    return Run(
        uuid=uuid,
        kind=kind,
        metadata=metadata,
        spec=spec,
        local=local,
        status=status,
    )


def run_from_dict(obj: dict) -> Run:
    """
    Create run from dictionary.

    Parameters
    ----------
    obj : dict
        Dictionary to create run from.

    Returns
    -------
    Run
        Run object.
    """
    return Run.from_dict(obj)