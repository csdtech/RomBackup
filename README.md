# Rom-Backup
This is an Android app that will allow you to backup some partitions image on rooted Android phones.

There are many partitions that are unsupported by Rom Backup App.

Common unsupported partitions are:

* userdata
* system
* vendor
* cache

and more.

Every mounted partition is unsupported by Rom Backup app, because cannot be reflashed later after backup. the backup of mounted partitions is usually corrupted.

## Main Usage
> - [Show Partitions](#show-partitions)
> - [Partitions Size](#partitions-size)
> - [Backup Partitions](#backup-partitions)
> - [Compress Partitions](#compress-partitions)

### Show Partitions
This app can find available partitions on devices and allow user to choose which one he want backup.

The available partitions found on device and thier size are rely manufacturer.

### Partitions Size
You can view the size of partition or in raw or formatted size.

this option can be turned off in app settings.

### Backup Partitions
partitions are saved on your sdcard as binary image format. so that image files like boot and recovery can be reflash again.

Not all visible partitions are supported.

> Before flashing the backed up partition make sure it is not corrupted otherwise you may break your device and i can not be responsible for any damage you made to your device. proceed at your own risk.

### Compress Partitions
You can turn on auto compress feature in app settings, to compress the saved partitions automatically to zip or tar archive.
reduce storage space and save safely.

### Binary Used
Rom Backup uses the following binaries for simple shell operations.

- Busybox

 Executable binary of busybox extracted from https://github.com/meefik/busybox
 
 Licensed under GPL-2.0 License.
 
- Zip
  
  Cross Compiled zip Binary for Android extracted from https://github.com/Zackptg5/Cross-Compiled-Binaries-Android
  
  Licensed under Other Open Source.