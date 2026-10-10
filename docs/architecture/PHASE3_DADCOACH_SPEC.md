# Phase 3.4 (truthful conversation timeline) - Dad Coach spec

Branch `unified/phase3` (= origin/main 8fbd63d). Decision entry: D-039 in `docs/implementation/DECISIONS.md`.
Platform contract: ai-workflow-platform `docs/architecture/UNIFIED_WORKFLOW_IMPLEMENTATION.md` §3.2, §3.4, §3.8
(branch `unified/phase3`). Dad Coach tells the platform what it actually delivered, so the platform's conversation (the
model's history, the admin view, reviews) shows what the father saw, not what the model drafted.

## Switch

`workflow.platform.delivery-reports` (env `PLATFORM_DELIVERY_REPORTS`), default **false**. Off = exactly today: the same
calls, the same bodies (no `internal` field on `/execute`, no new fields in the scheduled-callback answer), the
`:corrected` record and every `SentMessageRecorder` record as before. On = the table below; the old post-send records
are replaced, never doubled. Order of rollout (platform §3.7): platform deployed first; then this switch.

## Platform calls used

| Call | Body (Dad Coach fills) |
|---|---|
| (1) `POST /api/v1/worker/messages/inbound` | workerKey, workflowKey, userId `whatsapp:+972…`, channelId `whatsapp`, correlationId = Meta's message id (the platform stores `inbound:{id}`), content, messageType (`text` / `button_reply` / `audio` / `image` / …), structured `{buttonId, title}` for a tap, metadata `{timezone}`, tenantId, personRef, personName |
| (2) `POST /api/v1/worker/messages/outbound` (extended) | the pre-Phase-3 fields + turnCorrelationId, replacesDraft, providerMessageId, deliveryStatus (`ACCEPTED`, or `HELD` for a `held:<n>` id), template `{name, params}`, buttons, kind, supersededTurns |
| (3) `POST /api/v1/worker/messages/turn-outcome` | workerKey, workflowKey, userId, channelId, tenantId, turnCorrelationId, outcome `AS_IS` / `DROPPED` / `FAILED`, providerMessageId, deliveryStatus, reason, supersededTurns |
| (4) `/execute` `internal: true` | only on the Hebrew rewrite turn `{id}:he` |
| (5) scheduled-callback 2xx body | today's `{status, detail}` + `outcome` / `deliveredContent` / providerMessageId / deliveryStatus / reason / template / buttons / kind |

Content rule for every report: what was sent, without the identity line ("❤️ דאד קואץ׳:"), which the platform strips from
history anyway (P-B4). A voice note's "🎙️ שמעתי: …" line stays in a replacement (it was sent); it is ignored when deciding
AS_IS. A dashboard link is never reported with its URL.

## Path -> platform call

`cid` = Meta's message id of the father's message (the turn's correlation id).

### Turn replies (`InboundMessageHandler.runTurn`)

| Path | Today | Flag on |
|---|---|---|
| Reply sent as the platform wrote it (identity line / heard line aside) | nothing | (3) `AS_IS`, providerMessageId + status of the send |
| Reply changed before sending: `ReplyLanguageGuard` removed an English note, `ClaimGuard`, `TimerClaims`, `ReplyStyleGuard` | (2) `{cid}:corrected` only when ClaimGuard/TimerClaims changed it, plain row | (2) `{cid}:delivered`, turnCorrelationId=cid, `replacesDraft=true`, content = exactly what was sent, kind `REPLY`, providerMessageId/status. No `:corrected` row |
| English reply, Hebrew rewrite `{cid}:he` succeeded | `/execute {cid}:he` | `/execute {cid}:he` with `internal: true`; then (2) `replacesDraft` on cid with what was sent + `supersededTurns:[{cid}:he]` (always a replacement: the text came from another turn) |
| English reply, rewrite still not Hebrew -> fixed line | the line | (2) `replacesDraft` on cid, content = the line, kind `FIXED_LINE`, `supersededTurns:[{cid}:he]` |
| Reply is only "I sent you the button" (`SENT_LINE_WORDS`), the card is the answer | the card (if not sent in the turn) | the card is reported by the card path below (linked to cid when sent here); then (3) `DROPPED`, reason `SENT_LINE_CARD` (+ superseded) |
| Same, a line goes instead of the card (already on screen, rate limited, not allowed, failed) | the line | (2) `replacesDraft` on cid with the line, kind `FIXED_LINE` |
| `ClaimGuard` needs the button -> card sent before the reply | the card | the card path (linked to cid) + the reply as above |
| Duplicate answer with the cached reply (DC-B4), first send | the reply | as the first two rows (AS_IS when unchanged) on that turn |
| Already replied (guard taken), SUPPRESSED, blank | nothing | nothing |
| Platform unavailable/refused on the first `/execute` | down line | nothing (no turn reply is known to exist; equal to today) |
| Platform refused (4xx) the `:he` call | down line | (2) `replacesDraft` on cid with the down line + `supersededTurns:[{cid}:he]` |
| Platform unavailable on the `:he` call | down line, released for the redelivery (DC-B3) | nothing: the redelivery runs both turns again (platform duplicates) and reports what it sends |
| Meta refused the reply send | released for the redelivery (DC-B3) | nothing: the redelivery runs the same turn id (platform duplicate) and reports then. A FAILED outcome now would be final on the platform (set once) and make that later report a 409 |

### Code-answered messages (no turn): (1) first, then (2)

| Path | Inbound content / type | Outbound (2) |
|---|---|---|
| Tap with a fixed reply (`SessionButtonTaps`, `dc:` ids) | the button title, `button_reply`, structured `{buttonId, title}` | correlationId = cid (today's id), kind `BUTTON_REPLY` (today: plain `recordSent`) |
| Tap that failed -> down line | as above | cid, kind `FIXED_LINE` |
| Ready question (`ReadyQuestions`, D-036) | the words (a heard note: the voice-note line + words, as a turn would get them), `text` / `audio` | cid (today's id), kind `READY_ANSWER` (today: plain `recordSent`) |
| Voice note not heard (too long, silent, failed) | `[voice note]`, `audio` | `{cid}:reply`, kind `FIXED_LINE` |
| Spoken deletion request (typed only) | the voice-note line + words, `audio` | `{cid}:reply`, kind `FIXED_LINE` |
| Picture / file / sticker without words, voice note with notes off | `[photo]` / `[video]` / `[file]` / `[voice note]` / `[sticker]`… | `{cid}:reply`, kind `FIXED_LINE` |
| Reaction | nothing (ignored today too) | nothing |
| Typed deletion phrase | nothing | nothing: the father is deleted at once and the platform deletes his conversations; a report could re-create one |
| Number with no father (any of the above) | nothing | nothing: never opens a conversation for a stranger |
| Rate limited, deleted sender | nothing | nothing |

### Other product sends

| Path | Today | Flag on |
|---|---|---|
| Dashboard card (`LoginLinkService` -> `LoginLinkDelivery`, father only; the coach's tool, the claim/line paths, the login page) | nothing | (2) `dashboard-link:{linkId}`, content = the card text (no URL), buttons `[{type:url, title:"כניסה לדף שלי"}]`, kind `DASHBOARD_LINK`, providerMessageId/status; turnCorrelationId when sent by the inbound handler for a turn. Outside the window (template): content = the template line without the link, template `{name}` (params omitted - they hold the link). Staff links: nothing (no conversation) |
| `DashboardNotes` (history only, never sent on WhatsApp) | plain (2) | (2) kind `DASHBOARD_NOTE`, no providerMessageId, no deliveryStatus (the platform stores a kind without status: `delivery_status` stays null) |
| `BeltPromotionNotifier` (unused since D-030) | plain (2) | (2) kind `BELT_PROMOTION`, providerMessageId/status, template `{name, params}` when sent as a template, content = the rendered template |

### Scheduled callbacks (`ScheduledResponseController` / `ScheduledResponseDeliveryService`), answer body (5)

| Path | Body added (flag on) |
|---|---|
| Timer state with no valid session (SKIPPED) | `outcome: DROPPED`, `reason: NO_VALID_SESSION` |
| Not Hebrew, blocked | `outcome: DROPPED`, `reason: BLOCKED_NOT_HEBREW` |
| Free-form, sent exactly as written, no buttons (the daily check) | `outcome: AS_IS`, providerMessageId, deliveryStatus |
| Free-form, text replaced (`ScheduledReplies`, `ReplyStyleGuard`, `ReplyLanguageGuard`) or sent with `dc:` buttons | `deliveredContent` = what was sent, buttons `[{id, title}]` when attached, kind `SCHEDULED`, providerMessageId, deliveryStatus |
| Template (own session template or the general one) | `deliveredContent` = the rendered template when the catalog has it, else the flattened `{{1}}`; template `{name, params}`; the own template's quick replies as buttons; kind `SCHEDULED`; providerMessageId, deliveryStatus |
| Send failed (window closed and no template, Meta refused, error) | `outcome: FAILED`, `reason` = the failure reason |
| Replay of a handled trigger (`X-Idempotency-Key` seen) | nothing added: the first answer carried the report; what was sent is not stored per trigger |

`status` / `detail` are unchanged in every case; the platform reads the new fields only from a 2xx JSON object.

## Reports

`TimelineReports` (new, `com.dadcoach.integration.platform`): one daemon thread, in submission order (inbound before
outbound, a card before its turn's outcome), so delivery never waits. Each report: up to 3 attempts (1 s, 2 s backoff;
retried on connection errors, timeouts, 5xx, 408, 429; a 4xx is final). After the last attempt:
`timeline.report_failed` (WARN: path, correlation id, status) - the platform then shows the draft, as today. Queue
bounded (1000); a report that does not fit is logged `timeline.report_failed` (reason QUEUE_FULL). Flag checked when a
report is made. Nothing is reported when the platform client is disabled.

## Tests

`PlatformDeliveryReportsTest` (contract tests against `FakeServers`; the fake platform answers the three endpoints
additively and records the calls): every row of the tables above that the fake can drive, plus flag off = no new calls,
no `internal`, the old `:corrected` row and the old callback body. The full suite runs with the flag off (unchanged).

## Not in this phase

- A FAILED outcome for a refused turn reply (see the table); receipts are applied by the platform gateway.
- Linking a card sent by the coach's tool inside a turn to that turn (the tool does not know the turn's id): it is
  recorded unlinked, in time order.
- The text fallback of a refused card button (WhatsAppAdapter) is reported as the card.
- Replayed callbacks carry no report.
