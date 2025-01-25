package com.sdtech.rombackup;

import java.util.List;
import java.util.Arrays;

public final class RBConsonants {
    public static final String FILES_PATH = "/data/data/com.sdtech.rombackup/files";
    public static final String FIND_PARTITIONS =FILES_PATH + "/scripts/find-partitions.sh";
    public static final String FIND_PARTITION_SIZE = FILES_PATH + "/scripts/find-partition-size.sh";
    public static final String COMPRESS_TAR = FILES_PATH + "/scripts/compress-tar.sh";
    public static final String COMPRESS_ZIP = FILES_PATH + "/scripts/compress-zip.sh";
    /** well known unsupported partitions **/
    public static final List<String> UNSUPPORTEDS = (List<String>) Arrays.asList("system","vendor","cache","userdata");
    private static final String PREFS_KEY = "com.sdtech.rombackup.prefs_";

    public static final String PREFS_SHOW_SIZE=PREFS_KEY + "show_partition_size";
    public static final String PREFS_READABLE_SIZE=PREFS_KEY + "human_readable_partition_size";
    public static final String PREFS_HIDE_UNSUPPORTED=PREFS_KEY + "hide_unsupported_partitions";
    public static final String PREFS_BYTES_SIZE=PREFS_KEY + "bytes_per_kb";
    public static final String PREFS_AUTO_COMPRESS =PREFS_KEY + "auto_compress_backup";
    public static final String PREFS_TAR_GZ=PREFS_KEY + "tar_gz_format";
    public static final String PREFS_DELETE_FOLDER=PREFS_KEY + "delete_folder_after_compress";
}