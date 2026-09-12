# MagicCrates

MagicSMP digital-key crates for Paper 1.21.4. Keys exist only in `playerdata.yml`; no physical key item is created. The five physical crate displays use colored shulker boxes, but their storage inventory never opens.

## Build

Run `mvn clean package`, or upload the project to GitHub and run the included Build workflow. The JAR appears in `target/` or in the workflow artifact.

## Setup

1. Put the JAR in `plugins/` and restart.
2. Stand where a crate should be and run `/magiccrates set common`. The plugin places the matching colored shulker box. Repeat for `uncommon`, `rare`, `amethyst`, and `elite`.
3. Give a digital key with `/magiccrates keys give <player> <crate> <amount>`.
4. Left-click a crate to preview; right-click to open it.

Players can run `/keys`. Edit `config.yml`, then use `/magiccrates reload`.
