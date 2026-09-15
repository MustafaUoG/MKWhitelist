# MKWhitelist ⛏️

A Minecraft plugin that requires players to link their Discord account before joining — and stay in your Discord server to keep playing.

> [!NOTE]
> This project is a personal/learning project built for a specific Discord community. It's fully functional, but setup currently requires editing a config file directly — see [Configuration](#configuration) below.

## Table of Contents
- [About](#about)
- [Features](#features)
- [How It Works](#how-it-works)
- [Requirements](#requirements)
- [Installation](#installation)
- [Discord Bot Setup](#discord-bot-setup)
- [Configuration](#configuration)
- [Linking Your Account (Players)](#linking-your-account-players)
- [Companion App](#companion-app)
- [Troubleshooting](#troubleshooting)
- [Known Limitations & Roadmap](#known-limitations--roadmap)

## About

MKWhitelist gates access to your Minecraft server behind your Discord server. Players must link their Minecraft account to a Discord account, and remain a member of your Discord server, in order to join.

**Key Features:**
- 🔗 Discord-based account linking via a `/link` slash command
- 🛡️ Blocks unlinked or non-member players *before* they connect (no join-then-kick)
- 💾 SQLite-backed storage for linked accounts
- ⚙️ Config-driven setup — no code changes needed to run your own instance
- 🖥️ Companion desktop app for managing linked accounts (see below)

## How It Works

1. A player tries to join your Minecraft server.
2. If they haven't linked an account yet, they're kicked with a one-time code and instructions.
3. They join your Discord server and run `/link <code>`.
4. Their Minecraft account is now linked to their Discord account.
5. On future joins, the plugin checks that they're still a member of your Discord server before letting them in.

## Requirements

- A [Paper](https://papermc.io/) server running Minecraft 26.2
- Java 25
- A Discord server and a Discord bot application (see below)

## Installation

1. Download `MKWhitelist.jar` from [Releases](../../releases) (or build it yourself — see below)
2. Drop it into your server's `plugins/` folder
3. Start the server once — this generates `plugins/MKWhitelist/config.yml`
4. Stop the server, edit the config (see [Configuration](#configuration)), then start it again

> [!TIP]
> Building from source: clone this repo, then run `mvn package`. The built jar will be in `target/MKWhitelist.jar`.

## Discord Bot Setup

1. Go to the [Discord Developer Portal](https://discord.com/developers/applications) and create a new application
2. Under the **Bot** tab, click **Reset Token** and copy it — you'll need this for `config.yml`
3. Still under **Bot**, scroll to **Privileged Gateway Intents** and enable **Server Members Intent**

> [!IMPORTANT]
> If this intent isn't enabled here, the bot will fail to connect. This is the most common setup mistake — see [Troubleshooting](#troubleshooting).

4. Under **OAuth2**, check the `bot` scope, then under permissions check **View Channels**, **Send Messages**, and **Use Slash Commands**
5. Copy the generated URL, open it in your browser, and invite the bot to your Discord server

## Configuration

Open `plugins/MKWhitelist/config.yml` and fill in:

```yaml
discord:
  bot-token: "YOUR_BOT_TOKEN_HERE"
  guild-id: "YOUR_DISCORD_SERVER_ID_HERE"
```

- **bot-token** — from the Bot tab in the Developer Portal (see above)
- **guild-id** — right-click your Discord server's icon (with [Developer Mode](https://support.discord.com/hc/en-us/articles/206346498) enabled) and choose **Copy Server ID**

Restart the server after editing.

## Linking Your Account (Players)

1. Try to join the Minecraft server — you'll be kicked with a short code
2. In the Discord server, run `/link <code>`
3. You're now linked — join the server again

Codes expire after 10 minutes and can only be used once.

## Companion App

**[MKWhitelistManager](https://github.com/MustafaUoG/MKWhitelist-Manager)** is a desktop app for viewing and managing linked accounts — search, view, and unlink players without touching the database directly.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| "Discord setup is incomplete" on startup | `bot-token` or `guild-id` is missing or still has placeholder text in `config.yml` |
| "does not appear to be a valid Discord guild ID" | `guild-id` isn't a numeric Discord server ID — re-copy it with Developer Mode enabled |
| "Connected to Discord, but this bot is not a member of the server" | The bot was never invited — use the OAuth2 URL from [Discord Bot Setup](#discord-bot-setup) |
| "Discord connection timed out after 20 seconds" | Usually means **Server Members Intent** is enabled in code but not toggled on in the Developer Portal, or there's no internet connection |
| Player kicked even though they're linked and in Discord | The bot's member cache may not have loaded them yet — try again in a few seconds after server startup |

## Known Limitations & Roadmap

- Unlinking a player (via the companion app) doesn't kick them if they're already connected — it only takes effect on their next join attempt
- Config is edited manually via YAML; a setup wizard in the companion app is planned
- No automatic re-check of currently-connected players' Discord membership

---

Built as a learning project — Paper API, JDA, SQLite, and JavaFX.
