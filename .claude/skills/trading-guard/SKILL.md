---
name: trading-guard
description: Background knowledge for the trading module. Enforces human-in-the-loop approval constraint — never scaffold auto-execution paths for TradeOrder.
user-invocable: false
---

# Trading Module Guard

## Hard Constraint

Every `TradeOrder` **requires explicit human approval before execution**. This is a non-negotiable architectural rule, not a preference.

## What this means when writing trading code

- **Always** create a `TradeOrder` in `PENDING_APPROVAL` status — never `APPROVED` or `EXECUTING` directly
- **Never** wire up a method that calls an execution endpoint without passing through an approval step
- **Always** expose a dedicated approval endpoint (e.g. `POST /trading/orders/{id}/approve`) that a human must call explicitly
- **Never** auto-approve orders based on AI confidence scores, thresholds, or time delays
- The approval endpoint must require an explicit request body confirming intent (e.g. `{ "confirmed": true }`)

## Required TradeOrder lifecycle

```
PENDING_APPROVAL → APPROVED (human action) → EXECUTING → COMPLETED | FAILED
                ↘ REJECTED (human action)
```

## Package structure reminder

```
trading/
  TradingFacade.java          ← public API only
  domain/
    repository/
      TradeOrderRepository.java
    TradeOrder.java            ← entity, status field is the gate
  rest/
    controller/
      TradingController.java   ← must include /approve endpoint
      model/
        ApproveOrderRequest.java
  service/
    TradingService.java        ← orchestrates, never skips approval gate
```

## Review checklist before completing any trading task

- [ ] `TradeOrder` initial status is `PENDING_APPROVAL`
- [ ] No code path transitions directly from `PENDING_APPROVAL` to `EXECUTING`
- [ ] An explicit `/approve` (or equivalent) controller endpoint exists
- [ ] Approval endpoint validates the confirmation payload
- [ ] No scheduled tasks or event listeners auto-approve orders
