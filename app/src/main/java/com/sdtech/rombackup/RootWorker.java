package com.sdtech.rombackup;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class RootWorker {

    private static boolean rootFound = false;
    private static boolean rootGranted = false;

    public static boolean rootFound() throws IOException {
        rootFound = false;
        //paths for searching su binary.
        String[] paths = "/debug_ramdisk/su /sbin/su /system/sbin/su /system/bin/su /system/xbin/su /su/bin/su /magisk/.core/bin/su".split(" ");

        for(String path : paths) {              
            BufferedReader reader = new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec("ls " + path).getInputStream()));
            String line = reader.readLine();
            if(line != null && line.startsWith(path)) {
                rootFound = true;
            }
            reader.close();
        }
        /** trying second method */
        if(!rootFound) {
            for(String path : paths) {
                if(new File(path).exists()) {
                    rootFound = true;
                }
            }
        }
        return rootFound;
    }

    public static boolean rootGranted() throws IOException {
        if(!rootFound()) {
            return false;
        }
        rootGranted = false;
        final List<String> output = RootShell.command("id").getOutput();
        for(String out : output) {
            if(out.contains("uid=0")) {
                rootGranted = true;
            }
        }
        // request root permission
        if(!rootGranted) {
            closeShell();
            RootShell shell =  RootShell.command("su", "-c", "/system/bin/sh");
            shell.addCommand("echo ROOTGRANTED");
            List<String> shelloutput = shell.getOutput();
            for(String out : shelloutput) {
                if(out.matches("ROOTGRANTED")) {
                    rootGranted = true;
                }
            }
        }
        return rootGranted;
    }

    public static RootShell command(String... commands) throws IOException {
        return RootShell.command(commands);
    }

    public static void closeShell() throws IOException {
    	RootShell.closeShell();
    }

    public static class RootShell {
        private Process mProcess;
        private OutputStream cmdOutput;
        private InputStream  cmdInput;
        private InputStream  cmdError;

        private static RootShell mShell;

        private RootShell(Process prs) {
            mProcess  = prs; 
            cmdOutput = prs.getOutputStream();
            cmdInput  = prs.getInputStream();
            cmdError  = prs.getErrorStream();
        }

        private void wipeStream(InputStream in) throws IOException {
            int available;
            while((available = in.available()) != 0) {
                in.skip(available);
            }
        }

        public static RootShell command(String... commands) throws IOException {
            if(mShell == null) {
                Process prs = new ProcessBuilder(commands).start();
                mShell = new RootShell(prs);
            } else {
                mShell.addCommand(commands);
            }
            return mShell;
        }
        
        public void waitToFininsh() {
            try {
                    getOutput();
                } catch(IOException ignored) {}
        }
        
        public void addCommand(String... commands) throws IOException {
            if(cmdOutput == null) {
                command("/system/bin/sh");
            }
            wipeStream(cmdInput);
            wipeStream(cmdError);
            for(String cmd : commands) {
                cmdOutput.write((cmd + " \n").getBytes());
                cmdOutput.flush();
            }
            cmdOutput.write("echo ENDOFCOMMAND\n".getBytes());
            cmdOutput.flush();
        }

        private void close() throws IOException {
        	if(mShell != null) {
                mShell = null;
                mProcess.destroy();
                cmdOutput.close();
                cmdInput.close();
                cmdError.close();
            }
        }

        private static void closeShell() throws IOException {
        	mShell.close();
        }

        public List<String> getOutput() throws IOException {
            List<String> output = new ArrayList<>();
            BufferedReader outputReader = new BufferedReader(new InputStreamReader(cmdInput));
            String line;
            while((line = outputReader.readLine()) != null) {
                if(!line.matches("ENDOFCOMMAND")) {
                    output.add(line);
                } else {
                    break;
                }
            }
        	return output;
        }

        public List<String> getError() throws IOException {
            if(cmdError == null) {
            	return new ArrayList<String>();
            }
            List<String> errors = new ArrayList<>();
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(cmdError));
            String line;
            while((line = errorReader.readLine()) != null) {
                errors.add(line);
            }
        	return errors;
        }
    }
}
