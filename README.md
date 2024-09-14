![WebDAVBackup](https://github.com/user-attachments/assets/49586ea5-1b74-490f-ad6a-045f7fbdc150)
![GitHub last commit](https://img.shields.io/github/last-commit/Blis204/WebDavBackup)
# WebDavBackup

**WebDavBackup** is a Minecraft plugin with which you can easily create backups and upload them directly to your server trough WebDAV

## Current and Upcoming Features

- [x] Backup Creation 📦
- [x] Backup Uploading 📤
- [x] Folder Config 📁
- [x] Realtime Progress Bar ⏳
- [x] Delete Local Backups 🗑️
- [x] Scheduled Backups 🕒
- [ ] Set How Many Backups to Keep on Server and Client Side ⚙️
- [ ] Reload Command 🔄
- [ ] Discord Integration 🛡️

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
delete-local-backup: false
auto-backup: false
only-backup-when-players-online: false
backup-interval-minutes: 60
enable-webdav: false
webdav-url: https://your-webdav-server.com/path/
webdav-username: your_username
webdav-password: your_password
```
