# Independent MBH client plugin

## Scope

Apply to `workspaces/bbs-mbh-plugin` and its contract with the isolated
`workspaces/fsr-mbh-host` and `workspaces/mbh-fsr-metadata` repositories.
The integration is a separate Java 21 hot SPI plugin, ID `mbh`, client-side.
Do not place MBH business logic in the FSR composition root or modify `old/`.

## Entry points and dependencies

`studio.twiap.mbh.MbhPlugin` registers an account entry, plugin catalog and
resource source during prepare. Start network work only after host activation.
The manifest declares `taskbar_account`, `plugin_catalog`, `resource_sources`
and `client_services`; unload closes the owned client and its operations.

Compile against the actual host SDK, including host-supported Gson 2.10.1.
Do not package host classes, Minecraft, NeoForge, Gson, loader metadata or native
libraries. The host's `PluginArtifactValidator` and generation classloader are
the authoritative package checks. A matching FSR version string alone does not
prove that the generic client extension APIs are present.

## API and account rules

- Default API origin is `https://mbh-api.twiap.studio`, from MBH's public
  frontend API priority configuration. `https://mbh.twiap.studio` is the website.
  An HTML website response is never an API success. The configurable origin is
  pinned; do not discover other backends by retrying around a cooldown.
- Read validated native-client config and OIDC discovery. Use the registered
  literal-loopback callback and PKCE S256, never invent a URI or client secret.
  Access tokens obtain identity from MBH, not unverified JWT claims.
- Browser binding handoff has stricter path validation than normal same-website
  navigation. Use the corresponding `MbhApi` method for each purpose; a
  `/passport/bind` allowlist cannot validate ordinary product purchase links.
- Logout advances account ownership, cancels account operations and clears
  secure storage. Plugin unload closes runtime work but preserves secure session
  storage. Offline, 429 and 503 do not erase the account or same-account content.
- Session-only secure-store fallback still requires explicit host deletion on
  logout. A reported lack of persistent storage does not mean its memory is empty.
  Auth snapshot and owner reads must not acquire locks in the reverse order of
  queue cleanup invoked by auth transition callbacks.
- Mutate request queues, budget counters and auth state under their own locks,
  then invoke suppliers and complete/cancel/discard futures outside those locks.
  CompletableFuture continuations may run inline and enter another subsystem.
  A scheduler completing an auth callback while holding its lock can deadlock
  against auth submitting a file request. Never rely on the fake transport being
  synchronous or on snapshot getters alone being lock-free.
- Authorization continuations must close late `AutoCloseable` values and forward
  cancellation to the owned HTTP operation. Cancelling one consumer must not
  cancel shared token renewal for other requests.
- Repeated login closes the previous loopback listener before binding the same
  registered port. Listener construction and disposal stay outside auth locks.
  Deferred notifications recheck the snapshot, session and subscription before
  dispatch; queued native UI work checks snapshot identity again on execution.
- Catalog `client_version` is the actual FSR host version, not plugin version.
  The current host has no separately declared base BBS dependency, so its
  `base_bbs_version` is absent and matches only absent-base server tuples.
  Unknown host versions fail closed rather than inventing compatibility.

## Requests and metadata

All pages, visible artwork, auth API reads, manifests and file starts share
`RequestScheduler`. Initial bounds are 30 requests/minute per origin, 10 per
strict family and a burst of at most two. Server hints can lower these values.
The concrete strict families are `fsr_me`, `fsr_catalog`, `fsr_library`,
`fsr_manifest` and `fsr_download`. Unknown/legacy 429 scope pauses the origin;
known strict scope pauses that family. Respect Retry-After and gradual recovery.
Configuration retry on the same origin reuses the scheduler and its cooldown;
recreating request budgets when the user clicks Retry would bypass this contract.

`features.works_library_metadata` must be present and true before enabling
whole-library `q`/`sort` or lightweight selected-model readback. Older contract
version `1` servers may silently ignore these parameters. Do not filter only a
loaded page while presenting it as a library-wide search.

The MBH metadata follow-up normalizes and validates `q` before repository access:
at most 120 Unicode characters, literal title matching and no NUL or invalid
UTF-8. Escape `%`, `_` and backslash in bound PostgreSQL ILIKE parameters; apply
the same filter before count and page selection. Sort is an allowlist of newest,
oldest, title, views, favorites, downloads and likes, with stable ID tie-breaking.
Minimize batch author projections to public identity/name/avatar fields.

Visible list items carry optional author/cover/statistics in the same response.
Missing counters remain missing; visible zero stays zero. `views` means browsing,
not video playback. Hidden pack members remain `{kind,id}`. Reading metadata
does not record views, favorites or downloads, and counters do not change the
pack pagination revision. No per-card website or metadata requests.
Selected-model readback requires ordinary individual ownership/author rights
again at projection time; it does not invoke artifact preparation or tracking.
Pack revisions include ordered identity, rights, visibility and stable metadata,
while excluding counters, general timestamps and author presentation. Hidden
members contribute no undisclosed metadata. A changed opaque revision returns
409 so enumeration can restart within its bound. Real PostgreSQL tests require
the explicit disposable test DSN; DryRun SQL is not live database evidence.

## Native UI and local resources

Use existing FSR controls, icon atlas, themes and scale behavior. The account
belongs at the far right of the global bottom taskbar, outside resource footers.
The existing plugin panel hosts discovery. Existing model selectors host a
Local/MBH source switch; MBH contains models, packs and transfers.

Preserve query, sort, selection and scroll for same-view refresh or narrow Back.
Clear account-owned content on account switch and reject stale view generations.
Rendering and snapshot access use memory only: no filesystem, HTTP, archive or
blocking future work on the Minecraft thread. Background completion marshals
through the active owner and view revision before touching UI or GPU textures.

Selection alone never downloads. Import publishes local files without selecting
a scene form. A local selection must resolve and match the presented model key
before the host accepts an explicit Use/Open action.

## Installation and downloads

Legacy catalog `runtime.package_id=null` / `install_kind=unknown` does not prove
or disprove hot loading. Download a verified authorized artifact, inspect it
with the host and bind the MBH project to the confirmed package ID. Later updates
must preserve that ID and use the actual installed version. The host owns
installation, rollback, dependencies, local watching and restart policy.

`TransferQueue` shares at most two transfers and 4 MiB/s aggregate, reduced by
server limits. Prepare signed files only as transfers start. Resume requires
consistent length, strong identity, Range and immutable revision; never mix
different revisions. Verify SHA-256 and byte size before bounded extraction.
Reject unsafe paths, links and archive expansion. Recovery contains no bearer
or signed URL and does not revive an old account operation.

Private-cache accounting and cleanup are background work and share a storage
gate without nesting the queue monitor. Enforce the 4 GiB cap and bound traversal;
Files.walk is weakly consistent and can throw UncheckedIOException when another
task deletes a directory. Skip only a NoSuchFileException whose path is confirmed
absent without following links. Propagate access-denied and other IO errors;
never treat failed scans as zero usage. Snapshot/enqueue/pause must not wait for
the disk gate. Cover resume while sibling tasks are cancelled, known file sizes,
traversal limits and error propagation with executing regressions.

Pack enumeration pins a consistent revision and restarts boundedly on change.
Only individually entitled members enter the queue. Future pack additions remain
ungranted. Deduplicate model/format intentions across model and pack views.

BBS/GEO/VOX import is gated by complete native parsing fixtures, including actual
JPEG-to-PNG conversion, animations and palette values. Unsupported companion or
scene data must not be silently discarded; save the original package instead.
OBJ and unproven formats remain save-only.

Use stable host model keys `mbh/<canonical-origin-hash>/<model-id>/<format>`.
The publication ledger protects textures, configs and additional local files.
Forward cancellation to the host publication future before final commit.
Conflict choices are cancel, keep both, or replace with backup. Already
published local models survive logout and plugin uninstall.

## Executing checks

Run `testCore`, `testTransfers`, `testNativeUi` and package validation through
`check jar -PfsrSdkClasspathFile=<actual classpath file>`.
Exercise suppliers and completion/cancellation callbacks that re-enter from
another thread using bounded latches/futures. Keep an outer test-task timeout;
a timeout requires a thread dump/root-cause fix, not an increased wait allowance.
Native UI fixtures include host validation, actual generation classloading and
contribution cleanup; they do not prove rendered theme/scale behavior.
Record live provider, PostgreSQL and actual NeoForge UI acceptance separately.
No real purchases or production configuration changes are part of fixtures.
