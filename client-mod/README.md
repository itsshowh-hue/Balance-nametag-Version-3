# Balance Nametag (Fabric, Minecraft Java 1.21.4)

Draws a second, green line under a player's vanilla nametag showing their balance, e.g.

    Steve
    $12,500

**Build status: NOT verified.** This source was written without network access, so it has never been compiled
against the real 1.21.4 Yarn mappings or launched in-game. Treat the first `gradlew build` as the real test
(see "If the build fails").

## Build
Requirements: JDK 21, internet access (Loom downloads Minecraft + mappings).

    # one-time, if gradle/wrapper/gradle-wrapper.jar is missing (needs any Gradle >= 8.11 installed):
    gradle wrapper --gradle-version 8.12
    ./gradlew build            # Windows: gradlew.bat build

Output: `build/libs/balance-nametag-1.0.0.jar` (ignore the `-sources` jar).
Versions in `gradle.properties` should be checked against https://fabricmc.net/develop/ .

## Install
1. Install Fabric Loader for 1.21.4 and put Fabric API (1.21.4 build) in `.minecraft/mods`.
2. Copy `balance-nametag-1.0.0.jar` into `.minecraft/mods`. Do **not** install it on the server.

## Use
- Config: `config/balancenametag.json` (created on first launch). `/balancenametag reload` re-reads it.
- Toggle: `/balancenametag toggle`, or bind "Toggle Balance Display" under Controls (unbound by default).
- Options: `enabled`, `showForSelf`, `scoreboardObjectives` (checked in order, case-insensitive),
  `currencySymbol`, `placeholder` (empty = hide line), `textColor`, `refreshTicks`, `maxDistance`,
  `distanceScaling`, `maxScale`.

## Where balances come from (and the hard limit)
A client mod can only show data the server sends to the client. Vanilla/ViaVersion never send economy
balances (Vault, EssentialsX, CMI etc. keep them server-side). Two sources are supported, in this order:

1. **Server plugin payload** (`balancenametag:balances`) - a plugin you control or ask the server owner to run
   pushes balances. This is the only reliable way to see *other* players' balances on most servers.
2. **Scoreboard objectives** named `money`, `balance`, `bal`, `coins` or `cash`. Works only if the server has
   created such an objective AND the client receives it. Vanilla only syncs objectives that are shown in a
   display slot (tab list, below name, sidebar), so the server must display it somewhere. If it does not,
   nothing is shown. Other objectives (kills, health, playtime...) are never displayed.

If neither provides a value for a player, the line is hidden (or `placeholder` is shown). The mod never guesses,
never queries chat commands (e.g. `/bal <player>`), and never tries to read data the server did not send.

### ViaVersion
ViaVersion only translates protocol versions; it adds no economy data. Scoreboard sync and custom payloads
normally pass through it unchanged, but this is untested here.

### Payload format (big-endian, matches `java.io.DataOutputStream`)
    byte   version = 1
    byte   flags   (bit 0 = replace all previously known balances)
    int    count   (<= 2048)
    count x { long uuidMost; long uuidLeast; double balance }   // NaN = forget that player
Malformed packets are ignored, never fatal. Send updates on join and when a balance changes, not every tick.

Sketch of the server side (Paper, **untested**; getting the balance from Vault is up to you):

    // onEnable: getServer().getMessenger().registerOutgoingPluginChannel(this, "balancenametag:balances");
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(bos);
    out.writeByte(1); out.writeByte(1);
    out.writeInt(players.size());
    for (Player p : players) {
        out.writeLong(p.getUniqueId().getMostSignificantBits());
        out.writeLong(p.getUniqueId().getLeastSignificantBits());
        out.writeDouble(economy.getBalance(p));
    }
    viewer.sendPluginMessage(this, "balancenametag:balances", bos.toByteArray());

Only send balances that server policy allows every player to see.

## Finding out what a server really sends: `/balancenametag debug`
Run it while connected. It lists the scoreboard objectives the server synced to you (name and title) and, for
nearby players, the scores in them. If it says "0 objectives", the server does not expose balances through the
scoreboard. If one holds money under another name (e.g. `eco`), add that name to `scoreboardObjectives` in
`config/balancenametag.json` and run `/balancenametag reload`.

## Optional HTTPS API provider (off by default)
Only usable if the server operator publishes an API for balances. Config keys: `httpApiEnabled`, `httpApiUrl`
(https only; may contain `{name}` / `{uuid}`), `httpApiKeyHeader`, `httpApiKey`, `httpApiBalanceField`
(dot path in the JSON, e.g. `data.balance`), `httpApiRefreshSeconds`, `httpApiMinIntervalMs`.
Requests are asynchronous, cached, rate limited, never follow redirects, and back off on HTTP 429. This sends the
usernames of players near you to that API, so only enable it for a service you trust and are allowed to use.
It is untested against any real service.

## Adding another provider
Implement `provider.BalanceProvider` (`id`, `isEnabled`, `getBalance`) and add it to the list in
`BalanceNametagClient#onInitializeClient`; list order is priority. Results are cached per player for
`refreshTicks`, so providers are not hit every frame.

## How rendering works
- `PlayerEntityRendererMixin#updateRenderState` (TAIL): resolves the balance string from the entity and stores it
  on the render state (1.21.2+ render states carry no UUID).
- `PlayerEntityRendererMixin#renderLabelIfPresent` (TAIL): draws one row below the vanilla label with the same
  camera-facing transform, see-through layer and background as vanilla. Anchoring below the base line means it
  also clears the vanilla below-name score.

## If the build fails
The most likely trouble spots are Yarn names that differ slightly in your exact mappings build: the two
`@Inject` method descriptors in `PlayerEntityRendererMixin`, `PacketCodec.of` in `BalanceSyncPayload`, and
`TextRenderer#draw` / `Camera#getRotation` in `BalanceLabelRenderer`. Send me the compiler output and I'll fix them.
