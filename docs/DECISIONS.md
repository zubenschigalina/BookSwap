# BookSwap — Resolved Decisions & Build Plan

This project is built from two source documents (on the Desktop):

- **`BookSwap_Android_App_PRD.pdf`** — the finalized, "Approved" v2.0 spec. **Authoritative.**
- **`tasks.pdf`** — the earlier clarification thread + draft Asana tickets that *led to* the PRD.
  Historical; where it conflicts with the PRD, the PRD wins.

## Architecture decisions (chosen 2026-07-24)

| Area | Decision |
|------|----------|
| Backend | **Firebase** — Auth, Firestore, Storage, FCM, Cloud Functions |
| Build scope | **Follow PRD sprint roadmap in order.** Sprint 1 first: Auth + Profile & City Audit |
| App architecture | MVVM + Repository, Jetpack Compose, Navigation Compose, Coroutines/Flow |

## Resolved inconsistencies

1. **Favoriting notifies the owner.** (`tasks.pdf` contradicted itself; PRD resolves to *notify*.)
   A swipe-right = favorite = push notification to the owner. Not a silent bookmark.

2. **Proposal direction = requester-initiated.** (Diverges from PRD's owner-initiated favorites flow.)
   The interested user sends "my 1–3 books for your book"; the owner accepts/rejects.
   Favorites still notify the owner (per #1), but any user may also send a proposal directly.
   `Proposal(proposerId, recipientId, requestedBookId, offeredBookIds[])` — proposer offers their own books.

3. **A book may sit in many *pending* proposals, but only one *accepted* exchange.**
   It locks to `RESERVED` on **acceptance** (not on proposal, not on schedule), and is hidden from
   discovery from that moment. Competing pending proposals auto-expire on acceptance.

4. **Book leaves discovery on ACCEPT** (PRD state machine), correcting the Book Management ticket
   which said "on schedule."

5. **`Book.status` enum** (expanded from the PRD's 3 values to cover states the tickets imply):
   `AVAILABLE, RESERVED, EXCHANGED, ARCHIVED (owner-hidden), REMOVED (admin)`.

6. **Discovery matching = same city (string match) for MVP.** Distance radius filter is deferred to a
   later pass with geolocation; strict same-city is the MVP rule (per PRD discovery scope + City ticket).

7. **Ratings are mutual** — each party rates the other once, non-editable (per `tasks.pdf`; PRD was vague).

8. **Session/token storage = Firebase's own secure persistence** (Sprint 1). The PRD NFR mandates
   EncryptedSharedPreferences/KeyStore; Firebase Auth already persists the session and refreshes ID
   tokens in its own secure store, so Sprint 1 relies on that rather than hand-rolling token caching.
   Email verification gates access uniformly via `FirebaseUser.isEmailVerified` (Google sign-ins are
   pre-verified, so the same check onboards them without a second email step).

## Sprint roadmap (from PRD §6)

- **Sprint 1** — Auth, Profile & City Audit  ← *built (2026-07-24); assembleDebug passes*
- **Sprint 2** — Book Catalog & ISBN Lookup, Dual Swipe/Grid Discovery  ← *built (2026-07-24); assembleDebug passes*
- **Sprint 3** — Filtering & Undo, Favorites Alerts, Proposal Engine & Queue  ← *built (2026-07-24); assembleDebug passes*
- **Sprint 4** — Meeting Scheduling, In-App Messaging, Cancellation Logic  ← *built (2026-08-04); assembleDebug + unit tests pass; not yet exercised at runtime*
- **Sprint 5** — Ownership Transfer, Reputation & Trust, Blocking, Admin Portal (separate web app)

## Sprint 2 implementation notes (2026-07-24)

- **ISBN lookup = Open Library, keyless.** `data/remote/IsbnLookup.kt` GETs
  `openlibrary.org/api/books` with a plain `HttpURLConnection` (no Retrofit for one read-only
  endpoint). A miss just leaves the Title/Author fields for manual entry.
- **Discovery query needs a Firestore composite index** on `books(ownerCity ASC, status ASC)`.
  Firestore will surface a one-click "create index" link the first time `discover()` runs.
- **`Book.ownerCity` is denormalized** from the user's profile at save time so discovery can filter
  by city without a join. (Caveat: if a user changes city, their existing books keep the old
  `ownerCity` until re-saved — acceptable for MVP; revisit if it matters.)
- **Swipe semantics (Sprint 2 scope):** a like or reject only removes the card from the shared
  session set (cross-view synced between swipe + grid via `DiscoveryViewModel.swipedIds`). The
  favorite-notifies-owner behavior (decision #1) and Undo are **Sprint 3** — likes are recorded
  in-memory only for now.
- **Catalog caching:** the PRD NFR suggests Room for offline catalog. Sprint 2 relies on Firestore's
  built-in offline persistence instead; a Room cache layer is deferred unless perf requires it.

## Sprint 3 implementation notes (2026-07-24)

- **Epic 5 filtering is client-side.** `discover()` still fetches same-city AVAILABLE
  books once; category/language/condition/author/keyword are applied in
  `DiscoveryFilter.matches()` over that in-memory list, so no extra Firestore indexes
  are needed. **Distance radius stays deferred** (decision #6 — no geolocation yet); the
  filter sheet omits it.
- **Undo & Reset are session-only.** `DiscoveryViewModel` keeps an in-memory undo stack;
  Undo reverses the most recent swipe (and deletes its favorite if it was a Like). "Reset
  Discovery" (PRD puts it in Settings; there's no Settings screen yet, so it lives in the
  filter sheet) clears the session's swiped set. Rejected books already reappear next
  session because rejects are never persisted.
- **Favoriting = swipe-right/Like** → writes `favorites/{userId}_{bookId}` (deterministic
  id = idempotent, no duplicate owner alerts) **and** an `interest_alerts/{id}` doc for the
  owner, in one batch. The in-app alert (Inbox → Interest tab) works with **no server
  code**. A real **FCM push** is still a Cloud Function that fans the alert doc out to the
  device — deferred as external setup; `firebase-messaging` is intentionally not yet a
  dependency.
- **Proposals are requester-initiated** (decision #2): reachable from a favorited book or
  the discovery detail overlay. Denormalized proposer name + book titles avoid extra reads.
- **Accept state machine** (`ProposalRepository.accept`, decision #3): reads competing
  PENDING proposals via two queries (`whereIn(requestedBookId)` +
  `whereArrayContainsAny(offeredBookIds)`, each ≤10 values — an exchange has ≤4), then one
  batch flips this proposal → ACCEPTED, all its books → RESERVED (which removes them from
  discovery), and every competing PENDING proposal → EXPIRED. Read-then-batch, not a true
  transaction — fine for MVP single-owner accepts; a Cloud Function would harden the race.
- **Inbox queries filter by a single field and sort client-side**, so the queue's sort
  options (Newest / Oldest / Requester / Title) need no composite indexes.
- **5-tab bottom nav** now: Discover, Favorites, Inbox, Library, Profile. Interested-user
  profile/library view is read-only (owner doesn't initiate under decision #2).

## Sprint 4 implementation notes (2026-08-04)

- **A reschedule is a separate pending offer, not an in-place edit.** The PRD looks
  self-contradictory here: Epic 8 says editing a meeting sets it back to *Pending
  Agreement*, while Epic 10 says the prior schedule stays active until the new time is
  confirmed. Both hold only if the proposed change lives alongside the agreed one, so
  `Meeting` carries active `dateTime`/`locationAddress` **plus**
  `proposedDateTime`/`proposedLocationAddress`/`proposedById`. `confirm()` promotes
  proposed → active only when both sides have agreed. The 2-hour cancellation window is
  always measured against the **active** schedule.
- **One meeting per proposal, keyed by the proposal id** (`meetings/{proposalId}`), same
  deterministic-id trick as Sprint 3's favorites: `schedule()` can use `.set()` and is
  idempotent, and the exchange screen reads the meeting without a query.
- **The 2-hour rule is pure, injected-clock logic.** `MeetingRules.requiresEmergencyReason(
  meetingTime, now)` takes `now` as a parameter so the PRD's "2-Hour Time Boundary Test" is
  a plain JVM unit test (`MeetingRulesTest`, 8 cases incl. exactly-2h and already-passed).
  The boundary is *strictly less than* 2 hours = emergency. The dialog decides which mode
  to show when it opens, but `ExchangeDetailViewModel.cancel` **re-derives** it at tap
  time, so a dialog left open across the boundary can't slip a reasonless cancel through.
- **Cancelling releases the books.** One batch sets the meeting → CANCELLED, the proposal →
  CANCELLED (a new `ProposalStatus`), and every book in the swap → AVAILABLE, which puts
  them straight back into discovery. Same read-then-batch caveat as accept().
- **Chat is a subcollection**, `proposals/{proposalId}/messages/{id}`. This is what lets
  `firestore.rules` `get()` the parent proposal and enforce Epic 9's "unlocked ONLY after
  acceptance" server-side rather than trusting the client; it also gives Epic 9's
  "transcript permanently linked to the exchange record" for free. Reads stay allowed after
  cancellation (history is readable), only `create` is gated on ACCEPTED.
- **Location pins are labels, not map coordinates.** No Maps SDK yet (deferred), so a
  location message carries the agreed meeting-place text and optional lat/lng, and "Open in
  Maps" hands off via a `geo:` intent. `Message` already has the lat/lng fields so adding
  the map later doesn't migrate data.
- **Exchanges are a third Inbox tab.** Firestore can't OR across fields, so the tab merges
  `observeIncoming` + `observeOutgoing` + `observeForUser(meetings)` client-side — no new
  composite indexes.
- **Deferred from Sprint 4:** the PRD's 24-hour and 2-hour meeting **reminders** need a
  scheduled Cloud Function + FCM — the same server-side piece already deferred from
  Sprint 3, and the only genuinely un-buildable part of these epics on-device. The PRD's
  `SCHEDULED`/`COMPLETED` book states aren't modelled: RESERVED already covers the whole
  scheduled window, and COMPLETED belongs to Sprint 5's ownership transfer.

## Known external setup (status as of 2026-08-04)

- **Firebase project `bookswap-c1c8f` is live** and `app/google-services.json` is the real
  config (no longer a placeholder), including the type-1 Android OAuth client for the debug
  keystore's SHA-1. To register another machine's debug cert:
  `firebase apps:android:sha:create <appId> <sha1>` then delete and re-pull with
  `firebase apps:sdkconfig ANDROID <appId> --out app/google-services.json`.
- **Rules + indexes are deployed** (`firebase deploy --only firestore,storage`), so the
  project is out of test mode. Cloud Storage needed its bucket created in the console once
  before `--only storage` would deploy. ⚠️ Two rules stay deliberately permissive so the
  client-side accept state machine works without a Cloud Function: any signed-in user may
  flip *only* a book's or proposal's `status`, which means a malicious client could reserve
  or expire someone else's books. Moving accept() into a Cloud Function closes this, and
  pairs with the FCM function already deferred.
- **Google Sign-In is configured but never verified end-to-end** — Play Services accepts the
  OAuth config, but the emulator has no Google account, so the token exchange and
  first-time user-doc creation in `AuthViewModel.signInWithGoogle` are untested. (There is
  no `secrets.xml` step: `default_web_client_id` comes from google-services.json.)
- **Firestore composite index** `books(ownerCity, status)` for discovery (see Sprint 2 notes).
- Still outstanding: **Maps SDK key** (real map pins, Sprint 4) and **FCM + Cloud
  Functions** (favorite/proposal push from Sprint 3, meeting reminders from Sprint 4).
