# Local Hybrid GraphRAG and Evolutionary Memory

## Why

SAAA can now run a live mutation proposer and preserve prompt/response evidence,
but the proposer receives only the whole target file and allowed mutation scopes.
It cannot deliberately recover structurally connected tests or constraints, and
observable outcomes from prior deterministic evaluations do not inform future
search.

The original retrieval/container deferral was correct. Its recorded semantic
search revisit condition has now been reached. This change tests, rather than
assumes, whether bounded structural, semantic and historical evidence improves
accepted deterministic fitness per mutation cost.

## Intent

Build the smallest local implementation that can compare `NONE`, `VECTOR`,
`GRAPH` and `HYBRID` against the same tasks. Project canonical repository facts
into Neo4j Community, compile selected evidence into low-noise Evidence Capsules,
memoise derived projections separately in SQLite, make the retrieval treatment
explicit before LangChain4j invocation, and project observable evaluation
outcomes back as bounded future evidence.

Persist compact observable experiment envelopes in Git so the efficient SQLite
ledger and generated wiki experiment view can be rebuilt. Keep Neo4j efficient
through a versioned evolutionary-value policy with explicit historic
reinflation. Relate the subject implementation revision to the SAAA process
revision in one partitioned graph. Use the `saaa-` namespace for public command
tokens and prefer embedded Java APIs over subprocesses where comparable APIs
exist.

## Non-goals

- generic RAG platform or background indexing service
- OpenSearch, Elasticsearch or another vector database
- broad AST/call-graph framework or LSP integration
- Kubernetes, remote deployment or an always-running Neo4j daemon
- LLM reranking or model-authored retrieval approval
- automatic merge/deployment or historical-winner auto-promotion
- changing deterministic fitness semantics in this change
