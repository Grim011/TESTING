# GrimeSMP (Paper/Purpur 26.2, Java 25)

## Build
Needs JDK 25 and Maven:

    mvn clean package

The jar is `target/GrimeSMP.jar`. Drop it in your server's `plugins/` folder and restart.

## Using it
- `/grime` opens the admin menu (ops only): pick a team, click a tier slot, pick an online player.
  Left-click a slot to assign/replace, right-click to remove.
- Offline players: `/grime set <team> <tier> <player>`
- `/grime event start` when the week begins (kills only count while the event is LIVE).
- Daily event prizes: `/grime points add <player> <n>` or `/grime bonus add <team> <n>`.
- Everyone: `/grime standings`, `/grime balance [player]`, `/grime roster`.

Players can't change teams themselves: only the admin menu/commands do it.
Tier values, the 15 minute per-victim kill cooldown and friendly fire are in `plugins/GrimeSMP/config.yml`.
Data is saved in `plugins/GrimeSMP/data.yml`.
