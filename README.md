# Rom-Backup
This is an Android  app that will try to backup some partitions image on rooted Android phones.
> # Main Usage
> - [View Available Partitions](#view-available-partitions)
> - [View Partitions Size](#view-partitions-size)
> - [Backup Progress](#backup-progress)
> - [Backup Partitions](#backup-partitions)

# View Available Partitions
This app will try to find available partitions on devices and allow user to choose which he want backup.

# View Partitions Size
This app will try to find some partitions size in your device so that you can check if you have an available space to backup them in your sdcard.
<br/> not all partition size are available.
<br/> you can only view the size of the following partitions
* system
* vendor
* userdata
* cache
  
based on your device manufacturer.
# Backup Progress
you can see if the the backup is in progress or completed.
<br/> and you can stop the backup any time
# Backup Partitions
partitions are saved on your sdcard as binary image format.
<br/>so that image files like boot and recovery can be reflash again.
> Before flashing the backed up partition make sure it is not corrupted otherwise your phone may stuck at bootloop and i can not be responsible for any damage you made to your device.<br/>proceed at your own risk.
