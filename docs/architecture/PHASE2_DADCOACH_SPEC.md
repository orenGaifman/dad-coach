# Phase 2 (delivery reliability) - Dad Coach spec

Branch `unified/phase2` (origin/main fe3378b + baseline c8701e5). Decision entry: D-038 in
`docs/implementation/DECISIONS.md`. The known-bug tests in `backend/src/test/java/com/dadcoach/baseline/` state the
target behaviour; this spec says how the code gets there. Line numbers are as of c8701e5.

## Summary

| Fix | What changes | DB | Flag (default) |
|---|---|---|---|
| DC-B1 | the wamid of every scheduled message and every dashboard-link message is stored; a gateway hold is stored apart and never used as a wamid | V45 (columns + partial indexes) | none (always on) |
| DC-B2 | Meta's `sent` / `delivered` / `read` / `failed` receipts update the matching row (forward only); `scheduled_response_delivery` gets ACCEPTED / HELD / SENT / READ so DELIVERED means a delivery receipt; `WhatsAppAdapter.getDeliveryStatus` answers from the rows | V45 | `dad-coach.whatsapp.receipts.enabled` (true) |
| DC-B3 | an inbound message is claimed IN_PROGRESS (with a lease) and marked done only after it was answered; on failure the claim is released so Meta's redelivery is processed | none (uses `tool_idempotency`, status IN_PROGRESS already allowed by its CHECK) | `dad-coach.whatsapp.inbound.retry-unanswered` (true), `dad-coach.whatsapp.inbound.claim-lease` (PT10M) |
| DC-B4 | `isDuplicate=true` with `responseContent` is sent once, guarded by the durable key WHATSAPP_REPLY/<wamid> | none | none (the guard is the safety) |
| in passing | `ScheduledResponseDelivery` takes its timestamps from the injected clock, not `Instant.now()` | none | none |

## DC-B1 - persist the Meta message id (wamid)

**Today.** `WhatsAppApiClient.sendMessage` (57-59) returns `SendResponse(true, "held:<n>", null)` when the shared-number
gateway holds the message, otherwise Meta's wamid (`extractMessageId`, 135/155-160). `WhatsAppAdapter.sendMessage`
(131-133) returns `DeliveryResult.sent(messageId)`. Neither recorded path keeps it:
- `ScheduledResponseDeliveryService.deliver` (95-96) calls `delivery.markDelivered(mode)` only;
  `scheduled_response_delivery` (V21) has no column for it.
- `LoginLinkService.issueAndSend` (~134-140) calls `link.recordDelivery(result.isSuccessful(), result.failureReason())`
  only; `login_link` (V30, V33) has no column for it.

**Change.**
- `DeliveryResult`: `isHeld()` (provider id starts with `SharedNumberGate.HELD_PREFIX`) and `metaMessageId()` (the
  provider id unless held, else null). Nothing else in the result changes.
- `ScheduledResponseDelivery`: new fields `providerMessageId`, `gatewayHoldId`, `statusAt`; `markDelivered(mode)` is
  replaced by `markAccepted(mode, DeliveryResult, now)`: a held send -> status HELD + `gateway_hold_id`; a Meta send ->
  status ACCEPTED + `provider_message_id`. `markFailed(reason, now)`; the constructor takes `now` (clock fix).
- `LoginLink.recordDelivery(sent, error, providerMessageId)`: `delivery_status` keeps its vocabulary (SENT / FAILED /
  ISSUED); a held send stores `gateway_hold_id` and `receipt_status = 'HELD'` (logical HELD: handed over, not yet sent
  to the father); a Meta send stores `provider_message_id`.
- `ScheduledResponseDeliveryService` and `LoginLinkService.issueAndSend` pass the result's id through.

**Other send paths - no outbound ledger now (decision).** The other sends are: the conversational replies and fixed
lines of `InboundMessageHandler.send`, the button-tap replies, `BeltPromotionNotifier` (image + ProactiveSender), and the
staff link. None of them keeps a row today: `CoachMentions.sent` stores what the coach mentioned (text features for the
next message), not a delivery; `SentMessageRecorder` writes into the platform's conversation. No reader needs their
receipts yet, and a ledger would add a DB write per outbound message plus a second source of truth for the two recorded
paths. Receipts for these wamids are therefore unknown wamids (ignored), and `getDeliveryStatus` answers PENDING for
them as before. When a reader appears (e.g. "did the father get the coach's reply"), the ledger is one table keyed by
wamid written in `WhatsAppAdapter.sendMessage`, and `DeliveryReceipts` gains one more UPDATE.

**DB (V45, additive).**
```
scheduled_response_delivery + provider_message_id VARCHAR(128), gateway_hold_id VARCHAR(64), status_at TIMESTAMPTZ
login_link                  + provider_message_id VARCHAR(128), gateway_hold_id VARCHAR(64), receipt_status VARCHAR(20),
                              receipt_at TIMESTAMPTZ
partial indexes ON (provider_message_id) WHERE provider_message_id IS NOT NULL on both tables (receipt lookup)
```
Not UNIQUE: a wamid is unique at Meta, but a constraint would turn a replayed/odd provider answer into a failed
transaction after the message already went out. Neither table has a CHECK on its status columns (V21, V30 checked), so
the new status values need no constraint change. Existing rows keep NULL ids (they can never be matched by a receipt).
`MigrationTest` keeps passing: no table is added and the prod-upgrade path runs the same V45.

**Callers affected.** `ScheduledResponseDeliveryService.deliver`, `LoginLinkService.issueAndSend` (both `requestLink`
and `sendTo`). `ProactiveSender`, `DeliveryService`, `WhatsAppAdapter.sendMessage` unchanged.

## DC-B2 - process Meta status receipts; ACCEPTED vs DELIVERED

**Today.** `WhatsAppMessageParser.parseStatuses/parseStatus` (153-195) already yields `StatusUpdateDto(id, status,
recipient, timestamp, errorCode, errorTitle)`; `WhatsAppWebhookController.receive` (92-94) only logs
`whatsapp.receipts.ignored`. `WhatsAppAdapter.getDeliveryStatus` (165-171) is hard-coded PENDING.
`ScheduledResponseDelivery.Status` is `SENDING, DELIVERED, FAILED` and DELIVERED is written when Meta's API accepted the
send.

**Change.**
- `ScheduledResponseDelivery.Status` becomes `SENDING, ACCEPTED, HELD, SENT, DELIVERED, READ, FAILED`
  (`@Enumerated(STRING)`, VARCHAR(20), no CHECK). A successful send now writes ACCEPTED (or HELD); DELIVERED / READ /
  SENT come only from receipts. Legacy rows written DELIVERED before V45 keep DELIVERED (meaning "accepted"; they have no
  wamid). No data migration.
- New `com.dadcoach.whatsapp.DeliveryReceipts` (JdbcTemplate, one conditional UPDATE per table - atomic, so two
  concurrent webhooks cannot move a row backwards):
  - `apply(StatusUpdateDto)`: ignores a null id, a `held:` id, and any status other than sent / delivered / read /
    failed. Forward-only order ACCEPTED < SENT < DELIVERED < READ:
    - scheduled: `sent` from {ACCEPTED}; `delivered` from {ACCEPTED, SENT}; `read` from {ACCEPTED, SENT, DELIVERED};
      `failed` from {ACCEPTED, SENT} -> FAILED with `failure_reason = "META_<code>: <title>"`; `status_at` = the
      receipt's timestamp (clock when absent). FAILED is final. A `failed` after DELIVERED / READ is ignored.
    - login_link: the same order on `receipt_status` (NULL = accepted); `failed` (from NULL / SENT, and only while
      `delivery_status = 'SENT'`) also sets `delivery_status = 'FAILED'`, `delivery_error = "META_<code>: <title>"`
      (200 chars), so the admin's failed-links view and `LoginLinkService.sendTo` (no more ALREADY_SENT for a link
      that never arrived) see it. `delivery_status` is NOT moved to DELIVERED/READ: `sentToFatherSince` and ALREADY_SENT
      read `'SENT'` (LoginLinkRepository 31-33, LoginLinkService ~96-99).
    - unknown wamid: zero rows updated, nothing else happens.
  - `statusOf(wamid)`: scheduled row first (ACCEPTED/SENT -> SENT, DELIVERED, READ, FAILED, HELD/SENDING -> PENDING),
    then login_link (`receipt_status` NULL -> SENT, otherwise mapped; HELD -> PENDING), else PENDING.
- `WhatsAppWebhookController.receive`: with `dad-coach.whatsapp.receipts.enabled` (default true) every status update goes
  to `DeliveryReceipts.apply`, each in its own try/catch; Meta always gets 200. Off = today's log-and-ignore.
- `WhatsAppAdapter.getDeliveryStatus(id)` = `DeliveryReceipts.statusOf(id)`.
- **Early "failed" receipt (review follow-up).** Meta can answer a send with "failed" (131047 and similar) within
  milliseconds, before the row holding the wamid is committed (the send happens before `repository.save` /
  the end of `LoginLinkService.sendTo`'s transaction). Such a receipt matches no row. `DeliveryReceipts` keeps a "failed"
  receipt whose wamid is in no row in memory for 2 minutes (at most 500 at once) and re-applies it every 15 seconds
  (`retryPendingFailed`); it is dropped once its row exists or the time is up. Limits: only "failed" is kept (a lost
  early "sent"/"delivered" is corrected by the next receipt; a lost "failed" never would be), and the memory is lost on
  a restart or on another instance - an early "failed" can still be lost then.

**Readers of DELIVERED checked.**
- `ScheduledResponseResult.of` (the HTTP answer to the platform, also used for a replayed callback): ACCEPTED, HELD,
  SENT, DELIVERED, READ all answer `"DELIVERED"` (detail "Delivered" / "Already delivered"; HELD: "Held by the shared
  number gateway"). The platform ignores the body (ai-workflow-platform `ScheduledResponseNotifier` reads only the HTTP
  status), `provisioning/scripts/deployed_smoke_test.py:202` accepts DELIVERED/FAILED, and the baseline tests assert
  `"DELIVERED"` in the HTTP answer - so the wire contract is unchanged. A replay after a `failed` receipt answers FAILED
  with Meta's reason (true, and ignored by the platform).
- `ScheduledResponseController` (97: `"DELIVERED".equals(result.status())` -> `CoachMentions.sent`): unchanged
  behaviour through the mapping above (a held message is recorded as mentioned, as today).
- `AdminQueries` (failedDeliveriesSince, undelivered, lastFailure): read FAILED only - a receipt-failed row now shows up
  there, which is the point. `deliveriesOf` shows the raw status; `frontend/src/lib/labels.ts` DELIVERY_STATUS gains
  ACCEPTED / SENT / READ labels, and HELD (unused before D-038; it read "waiting for a suitable hour") now says what a
  gateway hold is: "ממתין — האב בשיחה עם מוצר אחר". `AdminQueries.loginLinksOf` also returns `receipt_status`, shown
  next to a link's status in the father page.
- `FatherDataPurger` deletes by father_id - unaffected.

**Existing tests changed (explicit, not weakened).** `ScheduledDeliveryBaselineTest` PASS tests
`insideTheWindowFreeFormAndTheRowIsDelivered`, `outsideTheWindowTheApprovedTemplateCarriesIt`,
`aReceiptForAnUnknownWamidChangesNothing` assert the ROW status `DELIVERED` right after Meta accepted the send; that is
exactly the conflation DC-B2 removes, so the row assertion becomes `ACCEPTED` (the HTTP-answer assertions stay
`DELIVERED`). Their display names say "DELIVERED" for the HTTP answer and are adjusted to name the row ACCEPTED.
`WhatsAppWebhookTest.statusReceiptsAreAcknowledgedAndIgnored` (213-220): its assertions (200, no turn, no send) stay
valid - a receipt for an unknown wamid is still acknowledged and changes nothing; it is renamed
`statusReceiptsAreAcknowledgedAndNeverRunATurn` because "ignored" is no longer true.

## DC-B3 - mark an inbound message handled only after it was answered

**Today.** `WhatsAppWebhookController.receive` (85-90) calls `IdempotencyService.firstTime` (84-97), which inserts the
WHATSAPP_INBOUND row already SUCCEEDED, then submits the turn to `InboundTurnExecutor`. If the turn fails (platform
down after retries -> `PLATFORM_DOWN_REPLY`) or the reply is refused by Meta (`InboundMessageHandler.send` 509-516 only
logs), the redelivered message is dropped as `whatsapp.inbound.duplicate`.

**Change.**
- `ToolIdempotencyRepository`: native `insertIfAbsent(id, scope, key, status, now, expires)` (`INSERT ... ON CONFLICT
  (scope, idempotency_key) DO NOTHING`, atomic, no constraint exception) and `takeOverStale(scope, key, staleBefore,
  now)` (`UPDATE ... SET created_at = now WHERE status = 'IN_PROGRESS' AND created_at < staleBefore`).
- `IdempotencyService`:
  - `claim(scope, key, lease)` -> `CLAIMED` (new row IN_PROGRESS, 7-day expiry; or a stale IN_PROGRESS row older than
    the lease taken over - the earlier worker died), `DONE` (row SUCCEEDED/FAILED), `BUSY` (IN_PROGRESS within the
    lease - being processed now). `created_at` of an IN_PROGRESS inbound row = when the current claim was taken.
  - `firstTime` now uses `insertIfAbsent` (same semantics, no exception path) - used when the flag is off.
  - `forget(scope, key)`: deletes the row whatever its status (the reply guard's release).
  - existing `complete` / `release` reused (release deletes only IN_PROGRESS rows).
- `InboundMessageHandler.handle` returns `Outcome { HANDLED, UNANSWERED }`. A per-call `Attempt` is passed to every
  `send`; UNANSWERED when a send was refused by Meta, or the platform was unavailable (`PlatformUnavailableException`:
  5xx after the client's retries, timeout, circuit open). When the processing throws, `handle` catches it and decides
  by what already happened (`Attempt.afterError`, review follow-up): something reached him (a fixed line, a ready
  answer, a button reply, the AI reply) and no send failed -> HANDLED, so a Meta redelivery can never send that line
  again; nothing reached him -> UNANSWERED. A ready answer (D-036) that went out before an error is the answer - it no
  longer falls through to an AI turn and a second reply. A definitive refusal (`PlatformRejectedException`, 4xx) is
  HANDLED - processing it again gets the same refusal. Deliberate non-answers (deleted father, reaction, rate limited,
  suppressed / blank) are HANDLED.
- `WhatsAppWebhookController`: with `dad-coach.whatsapp.inbound.retry-unanswered` (default true): `claim(...)`; DONE or
  BUSY -> `whatsapp.inbound.duplicate` (with `state`), dropped; CLAIMED -> submitted as today, and when the turn ends
  HANDLED -> `complete` (SUCCEEDED), UNANSWERED or an exception -> `release` (row deleted), logged
  `whatsapp.inbound.released`. Flag off -> today's `firstTime` path exactly.
- Ordering is unchanged: claims are taken on the webhook thread in arrival order, turns still run one at a time per
  sender on `InboundTurnExecutor`.
- An answered message's redelivery is still dropped (row SUCCEEDED): `WhatsAppWebhookTest.aMetaRetryOfTheSameMessage...`
  (103-111, one row) and `InboundDedupBaselineTest` PASS (c) keep passing unchanged.

**Double booking on a released platform timeout.** A turn whose platform call timed out is released and re-run on the
redelivery with the same correlation id (Meta's message id). If the first call did run on the platform (its tools
booked a session), the re-run must not book again: that relies on the platform's duplicate-request check
(`workflow.duplicate-request-check-enabled`, env `WORKFLOW_DUPLICATE_CHECK_ENABLED`, default true, also switchable in the
platform admin's Settings), which answers the repeated correlation id as a duplicate with the cached reply (sent once,
DC-B4) instead of running the tools again. With that check turned off, a released timeout can repeat the turn's tools.

**Limit (documented, not fixed here).** Meta redelivers a webhook only when it did not get a 2xx (or by its own
at-least-once duplicates); Dad Coach always answers 200 at once. DC-B3 makes a redelivery processable; it does not
create one. Actively re-processing unanswered messages (a sweeper over released / stale claims) would be a separate
change.

## DC-B4 - a duplicate answer that carries the reply is sent once

**Today.** `InboundMessageHandler.runTurn` (229-231) returns on any `isDuplicate`, outcome DUPLICATE: when the client's
retry (WorkflowPlatformClient MAX_RETRIES=2 on 5xx) is answered by the platform as a duplicate WITH the cached reply,
the father never gets it. The class Javadoc (64) says "a ... duplicate ... reply sends nothing".

**Change.** In `runTurn`, after a non-blank, non-suppressed reply (duplicate or not): claim the durable guard
`tool_idempotency(scope WHATSAPP_REPLY, key = Meta's inbound wamid)` with `firstTime`. Already present -> outcome
DUPLICATE (or ALREADY_REPLIED for a non-duplicate answer), nothing sent. Claimed -> the reply goes through the normal
pipeline (Hebrew guard, claim checks, style guard), outcome `DUPLICATE_REPLAYED` for a duplicate. If the reply then
could not be sent (Attempt UNANSWERED), or the turn threw before anything reached him, the guard is released with
`forget`, so the redelivery DC-B3 lets through can send it. An exception AFTER a send keeps the guard (the webhook still
releases the inbound claim, so a redelivery re-runs the turn; the cached AI reply is not sent twice, a fixed line could
be - accepted for that edge). A duplicate with no content still sends nothing (PASS test `aDuplicateWithNoContentSendsNothing`). Javadoc
item 8 updated.

**Known limitation.** When the cached reply arrives on a later redelivery (not the in-call retry), the turn ledger
snapshot is taken after the first attempt's tool effects, so `ClaimGuard` may strip a true confirmation from it
(conservative: it never adds a false claim).

## Tests

Enabled (the `@Disabled` annotations removed, nothing else in them changed):
- DC-B1: `ScheduledDeliveryBaselineTest.theWamidIsPersistedOnTheDeliveryRow`, `LoginLinkDeliveryBaselineTest.theLinkRowKeepsTheWamid`
- DC-B2: `ScheduledDeliveryBaselineTest.aFailedReceiptMarksTheDeliveryFailed`, `theAdapterReportsFailedAfterAFailedReceipt`,
  `deliveredAndReadReceiptsUpdateTheDelivery`; `LoginLinkDeliveryBaselineTest.aFailedReceiptMarksTheLinkFailed`
- DC-B3: `InboundDedupBaselineTest.aRedeliveryAfterAFailedTurnIsProcessedAgain`, `aRedeliveryAfterTheReplyFailedToSendIsProcessedAgain`
- DC-B4: `InboundDedupBaselineTest.aDuplicateAnswerWithTheCachedReplyIsSentOnce`

Changed: the three row-status assertions listed under DC-B2 (with their display names, and
`insideTheWindowFreeFormAndTheRowIsDelivered` renamed `...IsAccepted`), and the WhatsAppWebhookTest name. The baseline
class Javadocs now say the KNOWN-BUG tests are fixed by D-038. Added: `DeliveryReceiptsTest` (forward only: a late
delivered / failed after READ changes nothing; sent then failed is final and a replayed callback answers FAILED; a held
message is HELD with `held:<n>` kept apart and never matched; a link's receipts keep it SENT for the coach, a failed
link is sent again when asked; unknown wamid PENDING; an early "failed" is applied when its row appears, and dropped
after 2 minutes or when its row already exists), `InboundAttemptTest` (after an error: HANDLED once something reached him and nothing failed, else UNANSWERED),
`InboundClaimTest` (an error before any reply releases the message and its redelivery is answered once, a claim being
processed is not started twice,
a stale claim is taken over, answered = SUCCEEDED and a failed turn leaves no row, a 4xx refusal is an answer, a cached
reply whose guard exists is never sent again, a cached reply is sent once and its guard stays), `DeliveryFlagsOffTest`
(both switches off: receipts ignored, the message marked on arrival - the rollback behaviour).

## Rollback

- Behaviour without a deploy: `DAD_COACH_WHATSAPP_RECEIPTS_ENABLED=false` (receipts ignored again; wamids still
  stored) and `DAD_COACH_WHATSAPP_INBOUND_RETRY_UNANSWERED=false` (inbound marked handled on arrival again). A restart
  picks them up (Render env change).
- Code: revert the commit. V45 stays applied (additive, never edited); the old code ignores the new columns. Rows
  written ACCEPTED / HELD / SENT / READ would not load into the old `Status` enum (JPA would fail on a replayed
  callback of such a trigger), so once the revert deploy is LIVE (the new code no longer writes them) run
  `UPDATE scheduled_response_delivery SET status = 'DELIVERED' WHERE status IN ('ACCEPTED','HELD','SENT','READ');`
  and run it once more a few minutes later, to catch rows the old instance wrote while the deploy switched over (data
  only, no schema change). Until it has run, only a replayed callback of such a trigger fails (the platform's retry);
  new callbacks are unaffected. Login links need nothing (`delivery_status` vocabulary unchanged).
- Inbound IN_PROGRESS rows left by the new code are harmless to the old code (`firstTime` treats any row as seen).
