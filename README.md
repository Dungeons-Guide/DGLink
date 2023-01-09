# DGLink
Discord Github Link

This projects links discord forum channel to github issues

# Configuration
run the jar with following environment variables

```
WEBHOOK_SECRET=Github webhook secret
PRIVATE_KEY=Github private key, converted to pkcs#8, Base64 ONLY (no headers or footers)
APP_ID=Github App Id
CLIENT_ID=Github Client Id
BOT_TOKEN=Discord Bot Token
REDIS_SERVER=Redis server url

TARGET_CHANNEL_CONF=bug,suggestion,default
TARGET_CHANNEL_DEFAULT=default

GUILD_ID=ID of discord server

DSCD_bug_CHANNEL_ID=channel id
DSCD_bug_WEBHOOK_ID=webhook id
DSCD_bug_WEBHOOK_SECRET=webhook secret
DSCD_bug_TAGS=bug (tags to add, separated by comma)

DSCD_(confname)_.....
```
