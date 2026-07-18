---
name: runtime-python
description: Specialise Python runtime, package, framework and test conventions.
---

# Runtime: Python

Detect `pyproject.toml`, uv, Poetry, pip-tools, requirements, pytest, FastAPI,
Flask, Django, LangChain, LangGraph, direct SDK use, Pydantic, SQLAlchemy,
Alembic, Celery, Kafka clients and data or ML shape.

Preserve existing package management. Separate deterministic application logic,
provider contracts and opt-in live model tests.

## Language smells (for review-loop)

Mutable default arguments; bare `except`; broad `except Exception` swallowing
errors; import-time side effects; missing type hints on public boundaries;
`*args/**kwargs` hiding a real interface; god modules; overuse of dicts where a
dataclass fits; blocking calls in async code.
