"""Exception types that decide whether a failed job is retried."""


class PermanentJobError(Exception):
    """The job can never succeed (missing document, foreign file, nothing extractable).
    Retrying is pointless, so the job goes straight to FAILED. The message is stored on the job and
    shown to the user, so it must be a fixed, safe sentence: no paths, stack traces or file contents."""


class MappingError(ValueError):
    """A pipeline transaction could not be converted to the canonical model (e.g. NaN amount)."""


class LeaseLostError(Exception):
    """This worker no longer owns the job (lease expired and another worker took over)."""
