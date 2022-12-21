# Download

A download, update checking and security checking service.

## Goals
 - Fast
 - Flexible

## Usage

View API endpoints on [Swagger](https://download.discordsrv.com/swagger-ui/index.html)

### Download DiscordSRV

```
# Latest release
https://download.discordsrv.com/v2/DiscordSRV/DiscordSRV/release/download/latest/jar

# Latest snapshot
https://download.discordsrv.com/v2/DiscordSRV/DiscordSRV/snapshot/download/latest/jar
```

#### Use your favorite command line tool
```
# wget (--content-disposition = use the correct file name)
wget --content-disposition https://download.discordsrv.com/v2/DiscordSRV/DiscordSRV/release/download/latest/jar

# curl (--remote-name = download file, --remote-header-name = use the correct file name)
curl --remote-name --remote-header-name https://download.discordsrv.com/v2/DiscordSRV/DiscordSRV/release/download/latest/jar?preferRedirect=false

# httpie
http --download https://download.discordsrv.com/v2/DiscordSRV/DiscordSRV/release/download/latest/jar
```