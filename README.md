# Balance Nametag - client mod + server plugin

- `client-mod/`    Fabric mod for Minecraft 1.21.4 (goes in each player's .minecraft/mods)
- `server-plugin/` Paper plugin (goes in the server's plugins/ folder; needs Vault + an economy plugin)
- `.github/workflows/build.yml` builds both jars on GitHub (Actions tab -> latest run -> Artifacts)

NOT compiled or tested by the author (no network in the sandbox). The first build is the real test.
Players with permission `balancenametag.hide` are never sent; players without `balancenametag.receive`
receive nothing. Anything sent can be read by every client that has the mod: only use it if your
server's rules allow showing balances publicly.
