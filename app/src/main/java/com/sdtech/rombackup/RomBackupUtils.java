package com.sdtech.rombackup;


import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.UnknownHostException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;

public final class RomBackupUtils {

    public final static void copyFile(File outFile, File inFile) throws IOException {
        FileInputStream in=new FileInputStream(inFile);
        FileOutputStream out=new FileOutputStream(outFile);
        byte[] buf=new byte[4096];
        int n;
        while((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        in.close();
        out.close();
    }
    
    public static void deCompressZip(InputStream zip, File dir) throws IOException {
        ZipInputStream zip_strm=new ZipInputStream(zip);
        
        ZipEntry entry;
        int n;
        final byte[] buf=new byte[4096*2];
        

        while((entry=zip_strm.getNextEntry())!=null){
            if(entry.getName().endsWith("/")){
                String name=entry.getName();
                name=name.substring(0,name.length()-1);
                new File(dir,name).mkdirs();   
                continue;
            }
            
            File out=new File(dir,entry.getName());
            out.getParentFile().mkdirs();
            
            FileOutputStream out_strm=new FileOutputStream(out);
           
            while((n=zip_strm.read(buf))!=-1)
                out_strm.write(buf,0,n);
                
            out_strm.close();
            zip_strm.closeEntry();
        }
    }

    public final static byte[] readFile(String path) throws IOException {
        FileInputStream in=new FileInputStream(path);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buf=new byte[4096];
        int n;
        while((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        in.close();
        return out.toByteArray();
    }

    public final static String readFileString(File path) throws IOException {
        FileInputStream in=new FileInputStream(path);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buf=new byte[4096];
        int n;
        while((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        in.close();
        return out.toString();
    }

    public final static void writeFile(String path, byte[] data) throws IOException {
        FileOutputStream out=new FileOutputStream(path);
        out.write(data, 0, data.length);
        out.close();
    }

    public final static void writeFile(String path, InputStream strm) throws IOException {
        FileOutputStream out=new FileOutputStream(path);
        final byte[] buf=new byte[4096];
        int n;
        while((n = strm.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        out.close();
    }

    public static String readStream(InputStream strm) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        final byte[] buf=new byte[4096];
        int n;
        while((n = strm.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toString();
    }

    public static void compressFolder(File sourceFolder, String zipFilePath) throws IOException {
        FileOutputStream fos = new FileOutputStream(zipFilePath);
        ZipOutputStream zos = new ZipOutputStream(fos);

        compressFiles(sourceFolder, sourceFolder.getName(), zos);

        zos.close();
        fos.close();
    }

    public static void compressFiles(File folder, String parentFolder, ZipOutputStream zos) throws IOException {
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                compressFiles(file, parentFolder + "/" + file.getName(), zos);
            } else {
                FileInputStream fis = new FileInputStream(file);
                ZipEntry zipEntry = new ZipEntry(parentFolder + "/" + file.getName());
                zos.putNextEntry(zipEntry);

                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }

                zos.closeEntry();
                fis.close();
            }
        }
    }

    
    public static String getStackTrace(Throwable tr) {
        if(tr == null) {
            return "";
        }

        Throwable t = tr;
        while(t != null) {
            if(t instanceof UnknownHostException) {
                return t.toString();
            }
            t = t.getCause();
        }

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        tr.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }
    
    /**
     * Get the device architecture for executable binary to work correctly.
     *
     * @return The detected architecture if found, null otherwise.
     */
    public static String getArchName() {
        for(String androidArch : Build.SUPPORTED_ABIS) {
            switch(androidArch) {
                case "x86_64":
                return "x86_64";
                case "x86":
                return "x86";
                case "armeabi-v7a":
                return "arm";
                case "arm64-v8a":
                return "arm64";
            }

        }
        return null;
    }

    /**
     * Format file size in to human readable .
     *
     * @param size the size of file to format. which is <code>file.length()</code>
     * @param kiloBytes is the bytes block per kilobyte(KB). which must be 1024 or 1000 for correct
     *     formation.
     * @return the string of formatted file size.
     */
    public static String readableFileSize(long size, long kiloBytes) {
        long mega = kiloBytes * kiloBytes;
        long giga = mega * kiloBytes;
        long tera = giga * kiloBytes;
        String s = "";
        String name = "";
        double kb = (double) size / kiloBytes;
        double mb = kb / kiloBytes;
        double gb = mb / kiloBytes;
        double tb = gb / kiloBytes;
        if(size < kiloBytes) {
            s = size + "";
            name = "B";
        } else if(size >= kiloBytes && size < mega) {
            s = String.format("%.2f", kb);
            name = "KB";
        } else if(size >= mega && size < giga) {
            s = String.format("%.2f", mb);
            name = "MB";
        } else if(size >= giga && size < tera) {
            s = String.format("%.2f", gb);
            name = "GB";
        } else if(size >= tera) {
            s = String.format("%.2f", tb);
            name = "TB";
        }
        return s + name;
    } 

    /**
     * save the stacktrace String to file which is caused by a {@link Throwable}.
     *
     * @param path the path to save the log.
     * @param cause the throwable to read the stacktrace from.
     * @throws IOException if cannot write to the file.
     */
    public static void saveLog(String path, Throwable cause) throws IOException {
        File LOG_FILE = new File(path, "rb-error-log_" + RomBackupUtils.getFormattedTime() + ".txt");
        RomBackupUtils.writeFile(LOG_FILE.getAbsolutePath(), RomBackupUtils.getStackTrace(cause).getBytes());
    }

    /**
     * Get current time in human readable string.
     *
     * @return Returns the string representing the current time in human readable format.
     */
    public static String getFormattedTime() {
        return android.text.format.DateFormat.format("dd-MM-yyyy_hh-mm-ss_a", System.currentTimeMillis()).toString().toLowerCase();
    }
    
    /**
     * Revert the formatted file size into long value (The default file size which is same as <code>file.length();</code>). The format must be 1M,1 M, 1MB or 1 MB.for
     * example.
     *
     * @param size The formatted string of file size to revert which is usually B,KB,MB,GB,or TB.
     * @param kiloBytes is the bytes block per kilobyte(KB). which must be 1024 or 1000 for correct
     *     convertion.
     * @return The reverted size as long.
     */
    public static long revertSize(String size, long kiloBytes) {
        size = size.replaceAll("\\s", "").toUpperCase();
        long mega = kiloBytes * kiloBytes;
        long giga = mega * kiloBytes;
        long tera = giga * kiloBytes;
        long sizeBytes = 0;
        try {
            if(size.matches("[0-9]{1,}")){
                sizeBytes =
                (long)
                (double)
                (Double.parseDouble(size));
            }else if(size.matches("([\\d+]{1,}|[\\d+]{1,}\\.[\\d+]{1,})K[B]{0,1}")) {
                sizeBytes =
                (long)
                (double)
                (Double.parseDouble(size.replaceAll("[a-zA-Z]", ""))
                * kiloBytes);
            } else if(size.matches("([\\d+]{1,}|[\\d+]{1,}\\.[\\d+]{1,})M[B]{0,1}")) {
                sizeBytes =
                (long)
                (double)
                (Double.parseDouble(size.replaceAll("[a-zA-Z]", ""))
                * mega);
            } else if(size.matches("([\\d+]{1,}|[\\d+]{1,}\\.[\\d+]{1,})G[B]{0,1}")) {
                sizeBytes =
                (long)
                (double)
                (Double.parseDouble(size.replaceAll("[a-zA-Z]", ""))
                * giga);
            } else if(size.matches("([\\d+]{1,}|[\\d+]{1,}\\.[\\d+]{1,})T[B]{0,1}")) {
                sizeBytes =
                (long)
                (double)
                (Double.parseDouble(size.replaceAll("[a-zA-Z]", ""))
                * tera);
            }
        } catch(NumberFormatException e) {
        }
        return sizeBytes;
    }
    
}
