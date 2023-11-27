"""
Job Function specification module.
"""
from digitalhub_core.entities.functions.spec import FunctionParams, FunctionSpec
from digitalhub_core.utils.exceptions import EntityError
from digitalhub_core.utils.file_utils import is_python_module
from digitalhub_core.utils.generic_utils import encode_source
from digitalhub_core.utils.uri_utils import get_name_from_uri


class FunctionSpecMLRun(FunctionSpec):
    """
    Specification for a Function job.
    """

    def __init__(
        self,
        source: str | None = None,
        image: str | None = None,
        tag: str | None = None,
        handler: str | None = None,
        command: str | None = None,
        requirements: list | None = None,
        **kwargs,
    ) -> None:
        """
        Constructor.

        Parameters
        ----------
        image : str
            Name of the Function's container image.
        tag : str
            Tag of the Function's container image.
        handler : str
            Function handler name.
        command : str
            Command to run inside the container.
        requirements : list
            List of requirements for the Function.
        """
        super().__init__(source, **kwargs)
        if source is None:
            raise EntityError("Source must be provided.")

        if not is_python_module(source):
            raise EntityError("Source is not a valid python file.")

        self.image = image
        self.tag = tag
        self.handler = handler
        self.command = command
        self.requirements = requirements if requirements is not None else []
        self.build = {
            "functionSourceCode": encode_source(source),
            "code_origin": source,
            "origin_filename": get_name_from_uri(source),
        }


class FunctionParamsMLRun(FunctionParams):
    """
    Function mlrun parameters model.
    """

    image: str | None = None
    """Name of the Function's container image."""

    tag: str | None = None
    """Tag of the Function's container image."""

    handler: str | None = None
    """Function handler name."""

    command: str | None = None
    """Command to run inside the container."""

    requirements: list | None = None
    """List of requirements for the Function."""
