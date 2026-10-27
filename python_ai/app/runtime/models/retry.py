import httpx
from langgraph.types import RetryPolicy
from openai import APIConnectionError, APITimeoutError, RateLimitError


def transient(error):
    return isinstance(
        error,
        (
            ConnectionError,
            TimeoutError,
            httpx.TransportError,
            APIConnectionError,
            APITimeoutError,
            RateLimitError,
        ),
    ) or (isinstance(error, httpx.HTTPStatusError) and error.response.status_code == 429)


MODEL_RETRY = RetryPolicy(max_attempts=2, initial_interval=0.2, retry_on=transient)
