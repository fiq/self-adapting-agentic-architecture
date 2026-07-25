# Architecture

Use the smallest architecture that satisfies evidence and intent.

```
external world -> adapters -> use cases -> domain/application
                                    ^
                                    |
                         dependency direction
```

Add boundaries around volatile external APIs, databases, brokers, object
storage, payment providers, AI model providers and cloud-specific services when
they materially affect behaviour or tests.

Do not create one interface per class.

When a boundary matters, prefer an executable architecture fitness function
over a review-only reminder. Record the chosen check, or the reason it remains
manual, in `docs/validation.md` and `PROJECT_PROFILE.toon`.
