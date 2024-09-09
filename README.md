# ⚠️ Still in early Development

![GitHub last commit](https://img.shields.io/github/last-commit/Blis204/WebDavBackup)

![a-cute-cartoon-furry-wolf-with-a-green-hat-is-stan-fTmkbn_1QjCo_DUbPYsLrw-HTQGY0TKQkKdFQ277HeafA](https://github.com/user-attachments/assets/c0d3e8ed-a578-44b9-9f4c-f961ad2e146c)

# WebDavBackup

**WebDavBackup** is a Minecraft plugin with which you can easily create backups and upload them directly to your server trough WebDAV

## Features

- [x] Backup Creating
- [x] Backup Uploading (Pre-Alpha)
- [x] Custom Folders
- [x] Progress bar
- [ ] Upload progress bar

- [ ] Scheduled uploads

## Installation

1. Download the plugin jar file.
2. Place it in your server's `plugins` directory.
3. Restart your server to load the plugin.
4. Edit the `config.yml` file to set up your WebDAV server and backup preferences.
5. Restart your server
6. Execute `/backup`
## Configuration

```yaml
backup-directories:
- world
- plugins
enable-webdav: false
webdav-url: https://your-webdav-server.com/path/
webdav-username: your_username
webdav-password: your_password



