import asyncio

from evals.companion.select import parser, run

if __name__ == "__main__":
    raise SystemExit(asyncio.run(run(parser().parse_args())))
