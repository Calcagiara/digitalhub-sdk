"""
Nefertem Function specification module.
"""
from sdk.entities.functions.spec.objects.base import FunctionParams, FunctionSpec
from sdk.utils.exceptions import EntityError
from sdk.utils.generic_utils import decode_string, encode_string


class FunctionSpecNefertem(FunctionSpec):
    """
    Specification for a Function Nefertem.
    """

    def __init__(
        self,
        source: str | None = None,
        **kwargs,
    ) -> None:
        """
        Constructor.

        Parameters
        ----------
        """
        super().__init__(source, **kwargs)


class FunctionParamsNefertem(FunctionParams):
    """
    Function Nefertem parameters model.
    """
